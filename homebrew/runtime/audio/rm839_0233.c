/* SPDX-License-Identifier: MIT
 * Native PCM through the same ComMgr source/audio-policy/audio-server path
 * as RM-839 02.33 MMAPI. No Java calls or payload callbacks on audio threads.
 * ABI evidence and ownership contract: homebrew/docs/audio.md.
 */
#include <stddef.h>
#include <string.h>

#include "s40/audio.h"

void* fw_os_block_alloc_no_wait(unsigned);
void fw_os_block_dealloc(void*);
uint32_t fw_os_current_time_read_in_ms(void);
void s40_pump(void);
unsigned fw_audio_cm_register(void*, unsigned, unsigned);
void fw_audio_cm_ready(void*);
void fw_audio_cm_unregister(void*);
void fw_audio_cm_send(unsigned, void*, void*);
void fw_audio_message_delete(void*);
void* fw_audio_source_create(void);
void* fw_audio_source_share(void*);
void fw_audio_source_limits(void*, unsigned, unsigned);
const uint32_t* fw_audio_source_id(void*);
void fw_audio_source_forget(const uint32_t*);
void fw_audio_source_eof(void*);
void* fw_audio_fragment_init(void*, void*, unsigned, unsigned, void*);
int fw_audio_source_write(void*, void*);
void fw_audio_class_call(void*, unsigned);

typedef void (*receive_fn)(void*, unsigned, unsigned, uint8_t*, unsigned);
typedef struct {
    uintptr_t method;
    receive_fn function;
} method_entry;
typedef struct cm_class {
    const struct cm_class *self, *parent;
    const method_entry* methods;
    uint32_t size_flags;
    const char* name;
} cm_class;
typedef struct {
    const cm_class* klass;
    uint32_t resource;
    uint16_t object_id;
    uint8_t device, pad;
    uint16_t index, pad2;
} cm_object;

static cm_object object;
static void* source;
static uint32_t source_id[2];
static unsigned rate, stride, max_bytes, policy_id, stream_id, handle;
static unsigned serial, expected, expected_serial;
static int registered, state, reply, published, opened;

static unsigned be16(const uint8_t* p) { return (p[0] << 8) | p[1]; }
static void put16(uint8_t* p, unsigned v) {
    p[0] = v >> 8;
    p[1] = v;
}
static void put32(uint8_t* p, uint32_t v) {
    put16(p, v >> 16);
    put16(p + 2, v);
}
static void le32(uint8_t* p, uint32_t v) {
    p[0] = v;
    p[1] = v >> 8;
    p[2] = v >> 16;
    p[3] = v >> 24;
}

/* Firmware allocator used directly: ownership crosses into the audio server.
 * Its normal message allocator dereferences failed allocations; construct the
 * audited eight-byte header ourselves so low memory can fail cleanly. */
static uint8_t* message(unsigned bytes, unsigned id) {
    uint8_t* p = fw_os_block_alloc_no_wait(bytes + 8);
    if (!p)
        return NULL;
    memset(p, 0, bytes + 8);
    p[4] = (bytes + 2) & 255;
    p[5] = (bytes + 2) >> 8;
    p[8] = ++serial;
    p[9] = id;
    return p + 8;
}
static void send(unsigned resource, uint8_t* p, unsigned response) {
    expected = (resource << 8) | response;
    expected_serial = p[0];
    reply = 0;
    fw_audio_cm_send(resource, &object, p); /* consumes p, including failure */
}
static void receive(void* self, unsigned method, unsigned sender, uint8_t* p, unsigned code) {
    (void)self;
    (void)method;
    (void)sender;
    /* Phonet length is normalized to native endian by ComMgr. */
    unsigned bytes = (unsigned)p[-4] | ((unsigned)p[-3] << 8);
    bytes = bytes >= 2 ? bytes - 2 : 0;
    if (bytes >= 2 && code == expected && p[0] == expected_serial) {
        reply = -1;
        if (code == 0x0ba1 && bytes >= 8 && p[6] == 0) {
            unsigned id = be16(p + 2), stream = 0, off = 8;
            /* A policy application can exist even when stream creation fails. */
            if (!policy_id)
                policy_id = id;
            int valid = 1;
            for (unsigned i = 0; i < p[7]; i++) {
                if (off + 4 > bytes || p[off + 1] < 4 || off + p[off + 1] > bytes) {
                    valid = 0;
                    break;
                }
                if (p[off] == 4) {
                    if (p[off + 1] < 6 || p[off + 2]) {
                        valid = 0;
                        break;
                    }
                    stream = be16(p + off + 4);
                } else if ((p[off] == 2 || p[off] == 8) && p[off + 2]) {
                    valid = 0;
                    break;
                } else if (p[off] == 6 && (p[off + 1] < 5 || p[off + 4])) {
                    valid = 0;
                    break;
                }
                off += p[off + 1];
            }
            if (valid) {
                if (stream)
                    stream_id = stream;
                reply = 1;
            }
        } else if (code == 0x7513 && bytes >= 6 && be16(p + 2) == 1) {
            handle = be16(p + 4);
            opened = 1;
            reply = 1;
        } else if ((code == 0x7508 || code == 0x7503) && bytes >= 4 && be16(p + 2) == 1) {
            reply = 1;
        }
    }
    fw_audio_message_delete(p);
}
static const method_entry methods[] = {{0xc009, receive}, {0, NULL}};
static const cm_class klass = {&klass, (const cm_class*)0x63ab89a0u, methods, 16u << 16, "S40_PCM"};

static int wait_reply(void) {
    uint32_t start = fw_os_current_time_read_in_ms();
    for (unsigned budget = 0; !reply && budget < 20000; budget++) {
        s40_pump();
        if (fw_os_current_time_read_in_ms() - start >= 1500)
            break;
    }
    return reply == 1;
}
static int command(unsigned id, unsigned response) {
    uint8_t* p = message(4, id);
    if (!p)
        return 0;
    put16(p + 2, handle);
    send(0x75, p, response);
    return wait_reply();
}
static unsigned queued_bytes(void) {
    /* Source remains alive until producer EOF, even if the consumer closes:
     * C005 waits in status 4 for the producer. Atomic aligned snapshots only. */
    return source ? *(volatile uint32_t*)((uint8_t*)source + 52) : 0;
}
static int source_status(void) { return source ? *((volatile uint8_t*)source + 64) : 5; }
static int enqueue(const void* data, unsigned bytes) {
    void* pcm = fw_os_block_alloc_no_wait(bytes);
    void* fragment = fw_os_block_alloc_no_wait(28);
    if (!pcm || !fragment) {
        if (pcm)
            fw_os_block_dealloc(pcm);
        if (fragment)
            fw_os_block_dealloc(fragment);
        return -1;
    }
    memcpy(pcm, data, bytes);
    fw_audio_fragment_init(fragment, pcm, bytes, 0, NULL);
    /* The firmware default destructor frees BOTH allocations. Return 1 is
     * accepted high-water, not a retry request. Return 4 also consumes it. */
    return fw_audio_source_write(source, fragment) == 4 ? -1 : 1;
}
static int audio_status(void) {
    if (state == 1 && source_status() > 2)
        state = -2;
    return state;
}
static unsigned audio_queued(void) { return stride ? queued_bytes() / stride : 0; }
static int audio_write(const int16_t* pcm, unsigned frames) {
    if (audio_status() != 1)
        return -1;
    unsigned bytes = queued_bytes();
    unsigned available = bytes >= max_bytes ? 0 : (max_bytes - bytes) / stride;
    if (frames > available)
        frames = available;
    /* Small fragments limit how far decoder read-ahead can skew queue stats. */
    if (frames > 512)
        frames = 512;
    if (!frames)
        return 0;
    if (enqueue(pcm, frames * stride) < 0) {
        state = -2;
        return -1;
    }
    return frames;
}
static void audio_close(void) {
    state = 0;
    if (source) {
        void* s = source;
        source = NULL;
        fw_audio_source_eof(s);
        if (!published)
            fw_audio_class_call(s, 0xc005);
    }
    if (registered) {
        if (opened)
            command(2, 3);
        if (policy_id) {
            uint8_t* p = message(12, 0xa0);
            if (p) {
                put16(p + 2, policy_id);
                p[7] = 1;
                p[8] = 5;
                p[9] = 4;
                put16(p + 10, policy_id);
                send(0x0b, p, 0xa1);
                wait_reply();
            }
        }
    }
    /* Factory ID is single-use. Forget frees a source not taken by the server
     * (e.g. open failure); it is a no-op for one already consumed. */
    if (published)
        fw_audio_source_forget(source_id);
    if (registered) {
        s40_pump();
        fw_audio_cm_unregister(&object);
    }
    registered = published = opened = 0;
    policy_id = stream_id = handle = 0;
    stride = 0;
    expected = 0;
}
static int audio_open(const s40_audio_format* format) {
    int stage = 10;
    rate = format->rate;
    stride = format->channels * 2;
    unsigned buffer_ms = format->buffer_ms ? format->buffer_ms : 500;
    unsigned max_frames = rate * buffer_ms / 1000;
    max_bytes = max_frames * stride;
    memset(&object, 0, sizeof(object));
    object.klass = &klass;
    object.index = 0xffff;
    if (!fw_audio_cm_register(&object, 0, 0))
        goto fail;
    registered = 1;
    fw_audio_cm_ready(&object);

    /* Obtain normal media playback policy before opening the audio stream. */
    stage = 20;
    uint8_t* p = message(28, 0xa0);
    if (!p)
        goto fail;
    p[7] = 2;
    p[8] = 1;
    p[9] = 16;
    put16(p + 12, 4);
    put16(p + 14, 4);
    p[23] = 12;
    p[24] = 3;
    p[25] = 4;
    p[26] = 1;
    send(0x0b, p, 0xa1);
    if (!wait_reply() || !policy_id || !stream_id)
        goto fail;

    stage = 30;
    source = fw_audio_source_create();
    if (!source)
        goto fail;
    if (!fw_audio_source_share(source))
        goto fail;
    unsigned low_frames = max_frames / 8;
    if (low_frames > 512)
        low_frames = 512;
    fw_audio_source_limits(source, max_bytes, low_frames * stride);
    const uint32_t* id = fw_audio_source_id(source);
    if (!id)
        goto fail;
    source_id[0] = id[0];
    source_id[1] = id[1];
    published = 1;

    stage = 40;
    uint8_t header[44] = {0};
    memcpy(header, "RIFF", 4);
    le32(header + 4, 0x7ffff024);
    memcpy(header + 8, "WAVEfmt ", 8);
    le32(header + 16, 16);
    header[20] = 1;
    header[22] = format->channels;
    le32(header + 24, rate);
    le32(header + 28, rate * stride);
    header[32] = stride;
    header[34] = 16;
    memcpy(header + 36, "data", 4);
    le32(header + 40, 0x7ffff000);
    if (enqueue(header, 44) < 0)
        goto fail;
    /* Enough initial PCM for decoder probing without waiting on the mixer. */
    int16_t silence[512] = {0};
    for (unsigned n = 0; n < 4; n++)
        if (enqueue(silence, sizeof(silence)) < 0)
            goto fail;

    stage = 50;
    p = message(48, 18);
    if (!p)
        goto fail;
    put32(p + 4, source_id[1]);
    put32(p + 8, source_id[0]);
    put32(p + 12, 36); /* WAV */
    p[20] = 3;
    p[21] = format->volume;
    p[22] = 1;
    p[23] = 2;
    p[25] = 20;
    p[26] = 1;
    put32(p + 32, 1);
    p[44] = 22;
    p[45] = 4;
    put16(p + 46, stream_id);
    send(0x75, p, 19);
    if (!wait_reply())
        goto fail;
    stage = 60;
    if (!command(7, 8))
        goto fail;
    state = 1;
    return 1;
fail:
    audio_close();
    state = -stage;
    return state;
}
const s40_audio_driver s40_audio_rm839_0233 = {audio_open, audio_write, audio_queued, audio_status,
                                               audio_close};
