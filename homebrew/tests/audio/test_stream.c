/* SPDX-License-Identifier: MIT
 * Deterministic firmware model. Verifies protocol/ownership, not handset sound.
 * clang -std=c99 -Wall -Wextra -fsanitize=address,undefined
 *   -Ihomebrew/runtime/include homebrew/tests/audio/test_stream.c -o /tmp/s40-audio-test
 */
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "../../runtime/audio/rm839_0233.c"
#include "../../runtime/native/audio.c"

static unsigned allocations, fail_alloc, clock_ms, active_object, plays, closes;
static int failure; /* 1 policy rejection, 2 open rejection, 3 play timeout */
static unsigned char* model_source;
static int claimed, ended;
static uint8_t* pending;
static unsigned pending_code;
typedef struct {
    void* data;
    unsigned bytes;
} block;
static block* blocks[256];
static unsigned block_count;

void* fw_os_block_alloc_no_wait(unsigned n) {
    if (fail_alloc && --fail_alloc == 0)
        return NULL;
    void* p = calloc(1, n);
    assert(p);
    allocations++;
    return p;
}
void fw_os_block_dealloc(void* p) {
    if (p) {
        assert(allocations);
        allocations--;
        free(p);
    }
}
uint32_t fw_os_current_time_read_in_ms(void) { return clock_ms += 10; }
unsigned fw_audio_cm_register(void* p, unsigned id, unsigned resource) {
    assert(!active_object && !id && !resource);
    cm_object* obj = p;
    assert(obj->klass->self == obj->klass);
    assert(obj->klass->methods[0].method == 0xc009);
    obj->object_id = 34;
    obj->index = 0;
    active_object = 1;
    return 34;
}
void fw_audio_cm_ready(void* p) { assert(p == &object && active_object); }
void fw_audio_cm_unregister(void* p) {
    assert(p == &object && active_object && !pending);
    active_object = 0;
}
void fw_audio_message_delete(void* p) { fw_os_block_dealloc((char*)p - 8); }
void s40_pump(void) {
    if (pending) {
        uint8_t* p = pending;
        pending = NULL;
        object.klass->methods[0].function(&object, 0xc009, 0, p, pending_code);
    }
}
static void discard_blocks(void) {
    for (unsigned i = 0; i < block_count; i++) {
        fw_os_block_dealloc(blocks[i]->data);
        fw_os_block_dealloc(blocks[i]);
    }
    block_count = 0;
    if (model_source)
        *(uint32_t*)(model_source + 52) = 0;
}
static void destroy_source(void) {
    if (model_source) {
        assert(ended);
        discard_blocks();
        fw_os_block_dealloc(model_source);
        model_source = NULL;
    }
}
void fw_audio_cm_send(unsigned resource, void* obj, void* data) {
    uint8_t* p = data;
    assert(obj == &object && active_object && !pending);
    unsigned n = (unsigned)p[-4] | (unsigned)p[-3] << 8;
    n -= 2;
    unsigned code = p[1], transaction = p[0];
    uint8_t response[16] = {0};
    unsigned length = 8;
    response[0] = transaction;
    response[1] = code + 1;
    if (resource == 11) {
        assert(code == 0xa0);
        if (n == 28) {
            assert(p[7] == 2 && p[8] == 1 && p[9] == 16 && be16(p + 12) == 4 && be16(p + 14) == 4);
            assert(p[23] == 12 && p[24] == 3 && p[25] == 4 && p[26] == 1);
            put16(response + 2, 42);
            response[6] = failure == 1;
            response[7] = 1;
            response[8] = 4;
            response[9] = 8;
            put16(response + 12, 51);
            length = 16;
        } else {
            assert(n == 12 && be16(p + 2) == 42 && p[8] == 5 && be16(p + 10) == 42);
        }
    } else {
        assert(resource == 0x75);
        put16(response + 2, 1);
        if (code == 18) {
            assert(n == 48 && p[7] == 22 && p[15] == 36 && p[21] == 60 && p[23] == 2);
            assert(p[25] == 20 && p[26] == 1 && p[35] == 1 && p[44] == 22 && be16(p + 46) == 51);
            assert(block_count == 5 && blocks[0]->bytes == 44);
            uint8_t* h = blocks[0]->data;
            assert(!memcmp(h, "RIFF", 4) && !memcmp(h + 8, "WAVEfmt ", 8) && h[20] == 1 &&
                   h[34] == 16);
            claimed = 1;
            put16(response + 4, 71);
            if (failure == 2) {
                put16(response + 2, 2);
                claimed = 0;
            }
        } else if (code == 7) {
            assert(n == 4 && be16(p + 2) == 71);
            plays++;
            if (failure == 3) {
                fw_audio_message_delete(data);
                return;
            }
        } else {
            assert(code == 2 && n == 4 && be16(p + 2) == 71);
            closes++;
            destroy_source();
        }
    }
    fw_audio_message_delete(data);
    pending = message(length, code + 1);
    assert(pending);
    memcpy(pending, response, length);
    pending_code = (resource << 8) | (code + 1);
}
void* fw_audio_source_create(void) {
    model_source = fw_os_block_alloc_no_wait(132);
    ended = claimed = 0;
    if (model_source)
        model_source[64] = 2;
    return model_source;
}
void* fw_audio_source_share(void* p) {
    assert(p == model_source);
    return p;
}
void fw_audio_source_limits(void* p, unsigned high, unsigned low) {
    assert(p == model_source && high > low);
}
const uint32_t* fw_audio_source_id(void* p) {
    static const uint32_t id[2] = {0x80000052, 22};
    assert(p == model_source);
    return id;
}
void fw_audio_source_forget(const uint32_t* id) {
    assert(id[1] == 22);
    if (!claimed)
        destroy_source();
}
void fw_audio_source_eof(void* p) {
    assert(p == model_source && !ended);
    ended = 1;
}
void fw_audio_class_call(void* p, unsigned method) {
    assert(p == model_source && method == 0xc005);
    destroy_source();
}
void* fw_audio_fragment_init(void* fragment, void* data, unsigned bytes, unsigned flags,
                             void* callback) {
    assert(!flags && !callback);
    block* b = fragment;
    b->data = data;
    b->bytes = bytes;
    return b;
}
int fw_audio_source_write(void* p, void* fragment) {
    assert(p == model_source && !ended && block_count < 256);
    block* b = fragment;
    blocks[block_count++] = b;
    *(uint32_t*)(model_source + 52) += b->bytes;
    return 1; /* accepted high-water */
}

int main(void) {
    s40_audio_format format = {11025, 2, 60, 0};
    int16_t samples[1024];
    for (unsigned i = 0; i < 1024; i++)
        samples[i] = (int16_t)(i * 17);
    assert(s40_audio_open(&format) == -1); /* unsupported profile */
    s40_audio_bind(&s40_audio_rm839_0233);
    for (failure = 0; failure < 4; failure++) {
        int result = s40_audio_open(&format);
        if (!failure) {
            assert(result == 1 && s40_audio_status() == 1);
            discard_blocks();
            unsigned frames = 0;
            int n;
            while ((n = s40_audio_write(samples, 512)) > 0)
                frames += n;
            assert(n == 0 && frames == 5512 && s40_audio_queued() == frames);
            assert(!memcmp(blocks[0]->data, samples, 1024));
            discard_blocks();
            fail_alloc = 2;
            assert(s40_audio_write(samples, 1) < 0); /* PCM freed if fragment allocation fails */
        } else
            assert(result < 0);
        s40_audio_close();
        s40_audio_close();
        assert(!allocations && !active_object && !model_source);
    }
    failure = 0;
    assert(s40_audio_open(&format) == 1);
    model_source[64] = 4; /* server cancelled while producer still owns stream */
    assert(s40_audio_write(samples, 1) < 0);
    s40_audio_close();
    assert(!allocations && !active_object);
    format.buffer_ms = 160;
    assert(s40_audio_open(&format) == 1);
    discard_blocks();
    unsigned frames = 0;
    int n;
    while ((n = s40_audio_write(samples, 512)) > 0)
        frames += n;
    assert(n == 0 && frames == 1764 && s40_audio_queued() == 1764);
    s40_audio_close();
    assert(!allocations && !active_object);
    format.buffer_ms = 99;
    assert(s40_audio_open(&format) < 0);
    format.buffer_ms = 501;
    assert(s40_audio_open(&format) < 0);
    format.buffer_ms = 0;
    format.volume = 101;
    assert(s40_audio_open(&format) < 0);
    printf("PASS: PCM copy/backpressure, policy/open failures, timeout, allocation failure, "
           "cancellation, repeated close; %u plays, %u closes\n",
           plays, closes);
}
