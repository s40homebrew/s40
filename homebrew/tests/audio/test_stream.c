/* SPDX-License-Identifier: MIT
 * Deterministic firmware model. Verifies protocol/ownership, not handset sound.
 * Run with homebrew/tests/audio/check.py.
 */
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "audio/commgr/driver.c"
#include "audio/commgr/driver.h"
#include "audio/devices/rm839_0233_imports.h"
#ifdef TEST_RM443
#include "audio/devices/registry.h"
static audio_abi model_abi;
static int model_open(const s40_audio_format* format) { return audio_cm_open(&model_abi, format); }
static const s40_audio_driver model_driver = {model_open, audio_cm_write, audio_cm_queued,
                                              audio_cm_status, audio_cm_close};
#define S40_CM_DRIVER model_driver
#else
#include "audio/devices/rm839_0233.c"
#define S40_CM_DRIVER s40_audio_rm839_0233
#endif
#include "audio/core.c"

static unsigned allocations, fail_alloc, clock_ms, active_object, plays, closes;
static int failure; /* 1 policy rejection, 2 open rejection, 3 play timeout,
                     4 route registration rejected, 5 route control rejected,
                     6 wrong controller group, 7 route value clamped,
                     8 allocation failure after the route claim,
                     9 lost route deletion acknowledgment */
static unsigned model_policy, model_route_client, route_requested;
static unsigned char* model_source;
static int claimed, ended;
static uint8_t* pending;
static unsigned pending_code, pending_after;
typedef struct {
    void* data;
    unsigned bytes;
} block;
static block* blocks[256];
static unsigned block_count;
static unsigned requested_volume = 65;

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
    audio_cm_object* obj = p;
    assert(obj->classic.klass->self == obj->classic.klass);
    assert(obj->classic.klass->methods[0].method == 0xc009);
    if (abi->object_layout == AUDIO_CM_LEGACY)
        obj->legacy.object_id = 0x322;
    else
        obj->classic.object_id = 0x322;
    audio_cm_set_index(obj, abi, 0);
    active_object = 1;
    return audio_cm_id(obj, abi);
}
void fw_audio_cm_ready(void* p) { assert(p == &object && active_object); }
void fw_audio_cm_unregister(void* p) {
    assert(p == &object && active_object && !pending);
    active_object = 0;
}
void fw_audio_message_delete(void* p) { fw_os_block_dealloc((char*)p - 8); }
void s40_pump(void) {
    if (pending && clock_ms >= pending_after) {
        uint8_t* p = pending;
        pending = NULL;
        object.classic.klass->methods[0].function(&object, 0xc009, 0, p, pending_code);
    }
}
static void discard_blocks(void) {
    for (unsigned i = 0; i < block_count; i++) {
        fw_os_block_dealloc(blocks[i]->data);
        fw_os_block_dealloc(blocks[i]);
    }
    block_count = 0;
    if (model_source)
        *(uint32_t*)(model_source + (abi->source_layout == AUDIO_SOURCE_DEVICE
                                             ? offsetof(audio_source_device, queued_bytes)
                                             : offsetof(audio_source_classic, queued_bytes))) = 0;
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
        if (code == 0) {
            assert((abi->route == AUDIO_ROUTE_OWN_IHF) &&
                   (model_policy || abi->policy == AUDIO_POLICY_NONE) && !model_route_client);
            assert(n == 4 && p[2] == 8);
            response[1] = failure == 4 ? 2 : 1;
            response[2] = 27;
            model_route_client = failure != 4;
            length = 4;
        } else if (code == 0x14) {
            assert((abi->route == AUDIO_ROUTE_OWN_IHF) && model_route_client && !route_requested);
            assert(n == 16 && p[2] == 27 && p[3] == 6 && p[7] == 1);
            assert(p[8] == 3 && p[9] == 8 && p[10] == 1 && !p[11]);
            assert(audio_get_be16(p + 12) == 13);
            route_requested = 1;
            response[1] = failure == 5 ? 0x16 : 0x15;
            response[2] = failure == 6 ? 4 : 6;
            response[3] = failure == 7;
            if (requested_volume) {
                response[7] = 1;
                response[8] = 6;
                response[9] = 8;
                response[10] = 1;
                response[11] = failure == 7 ? 3 : 2;
                response[12] = 1;
                length = 16;
            }
        } else if (code == 4) {
            assert((abi->route == AUDIO_ROUTE_OWN_IHF) && model_route_client);
            assert(n == 4 && p[2] == 27);
            if (failure == 8) {
                /* The client allocator still fails: deletion must use its
                 * reserved packet. The server can then release resources. */
                assert(fail_alloc == 1);
                fail_alloc = 0;
            }
            model_route_client = route_requested = 0;
            if (failure == 9) {
                fw_audio_message_delete(data);
                return;
            }
            length = 4;
        } else if (n == 28 || (n == 12 && p[8] == 3)) {
            assert(code == 0xa0);
#ifdef TEST_RM443
            assert(n == 12 && !audio_get_be16(p + 2) && p[7] == 1 && p[8] == 3 && p[9] == 4 &&
                   p[10] == 1);
#else
            assert(p[7] == 2 && p[8] == 1 && p[9] == 16 && audio_get_be16(p + 12) == 4 &&
                   audio_get_be16(p + 14) == 4);
            assert(p[23] == 12 && p[24] == 3 && p[25] == 4 && p[26] == 1);
#endif
            audio_put_be16(response + 2, 42);
            response[6] = failure == 1;
            response[7] = 1;
            response[8] = 4;
            response[9] = 8;
            audio_put_be16(response + 12, 51);
            length = 16;
            model_policy = failure != 1;
        } else {
            assert(code == 0xa0 && !model_route_client && !route_requested);
            assert(n == 12 && audio_get_be16(p + 2) == 42 && p[8] == 5 &&
                   audio_get_be16(p + 10) == 42);
            assert(model_policy);
            model_policy = 0;
        }
    } else {
        assert(resource == 0x75);
        audio_put_be16(response + 2, 1);
        if (code == 18) {
            assert(p[7] == 22);
#ifdef TEST_RM443
            assert(p[21] == (requested_volume ? 4 : 1));
#else
            assert(p[21] == requested_volume);
#endif
            assert(p[25] == 20 && p[26] == 1 && p[35] == 1);
#ifndef TEST_LEGACY
            assert(p[44] == 22 && audio_get_be16(p + 46) == 51);
#endif
#ifdef TEST_RM443
#ifdef TEST_LEGACY
            assert(n == 56 && p[15] == 136 && p[23] == 2);
            assert(p[44] == 23 && p[45] == 12 && p[48] == 2 && p[49] == 16);
            assert(audio_get_be16(p + 50) == 256 && audio_get_be16(p + 54) == 11025);
#else
            assert(n == 60 && p[15] == 136 && p[23] == 3);
            assert(p[48] == 23 && p[49] == 12 && !p[50] && !p[51]);
            assert(p[52] == 2 && p[53] == 16 && audio_get_be16(p + 54) == 256);
            assert(!p[56] && !p[57] && audio_get_be16(p + 58) == 11025);
#endif
            assert(block_count == 2 && blocks[0]->bytes == 1024 && blocks[1]->bytes == 1024);
            for (unsigned b = 0; b < block_count; ++b)
                for (unsigned j = 0; j < blocks[b]->bytes; ++j)
                    assert(!((const uint8_t*)blocks[b]->data)[j]);
#else
            assert(n == 48 && p[15] == 36 && p[23] == 2);
            assert(block_count == 5 && blocks[0]->bytes == 44);
            uint8_t* h = blocks[0]->data;
            assert(!memcmp(h, "RIFF", 4) && !memcmp(h + 8, "WAVEfmt ", 8) && h[20] == 1 &&
                   h[34] == 16);
#endif
            claimed = 1;
            audio_put_be16(response + 4, 71);
            if (failure == 2) {
                audio_put_be16(response + 2, 2);
                claimed = 0;
            }
        } else if (code == 7) {
            assert(n == 4 && audio_get_be16(p + 2) == 71);
            plays++;
            if (failure == 3) {
                fw_audio_message_delete(data);
                return;
            }
        } else {
            assert(code == 2 && n == 4 && audio_get_be16(p + 2) == 71);
            closes++;
            destroy_source();
        }
    }
    fw_audio_message_delete(data);
    pending = message(length, response[1]);
    assert(pending);
    memcpy(pending, response, length);
    pending_code = (resource << 8) | response[1];
    pending_after = failure == 10 && code == 0 ? clock_ms + 1700 : clock_ms;
}
void* fw_audio_source_create(void) {
    if (failure == 8)
        fail_alloc = 1;
    model_source = fw_os_block_alloc_no_wait(132);
    if (failure == 8)
        fail_alloc = 1; /* Remains unavailable until the route is released. */
    ended = claimed = 0;
    if (model_source)
        model_source[(abi->source_layout == AUDIO_SOURCE_DEVICE
                              ? offsetof(audio_source_device, status)
                              : offsetof(audio_source_classic, status))] = 2;
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
unsigned fw_audio_volume_scale(unsigned level, unsigned minimum, unsigned maximum) {
    assert(level == requested_volume && minimum == 0 && maximum == 100);
    assert(!(abi->route == AUDIO_ROUTE_OWN_IHF) ||
           route_requested); /* Route setup precedes scaling. */
    /* RM-443's configured native range can differ from the Java percentages. */
    return 1 + (4 * level + 50) / 100;
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
    *(uint32_t*)(model_source + (abi->source_layout == AUDIO_SOURCE_DEVICE
                                         ? offsetof(audio_source_device, queued_bytes)
                                         : offsetof(audio_source_classic, queued_bytes))) +=
            b->bytes;
    return 1; /* accepted high-water */
}

int main(void) {
#ifdef TEST_RM443
    model_abi = audio_rm443_1012.configuration;
#ifdef TEST_LEGACY
    model_abi = audio_rm217_0721.configuration;
    assert(model_abi.object_layout == AUDIO_CM_LEGACY && model_abi.policy == AUDIO_POLICY_NONE);
#endif
    model_abi.imports = (audio_imports)AUDIO_LINKED_IMPORTS(fw_audio_volume_scale);
    abi = &model_abi;
#else
    abi = &rm839_0233_abi;
#endif

    s40_audio_format format = {11025, 2, 65, 0};
    int16_t samples[1024];
    for (unsigned i = 0; i < 1024; i++)
        samples[i] = (int16_t)(i * 17);
    assert(s40_audio_open(&format) == -1); /* unsupported profile */
    s40_audio_bind(&S40_CM_DRIVER);
#ifdef TEST_LEGACY
    assert(s40_audio_open(&format) == 1 && object.legacy.index == 0);
    discard_blocks();
    assert(s40_audio_write(samples, 512) == 512 && s40_audio_queued() == 512);
    assert(!memcmp(blocks[0]->data, samples, 1024));
    s40_audio_close();
    assert(!allocations && !active_object && !model_policy && !model_route_client);
    puts("PASS: legacy ComMgr object, RAW OPEN without policy, copied PCM and route release");
    return 0;
#endif
    for (failure = 0; failure < ((abi->route == AUDIO_ROUTE_OWN_IHF) ? 9 : 4); failure++) {
        int result = s40_audio_open(&format);
        if (!failure) {
            assert(result == 1 && s40_audio_status() == 1);
            /* Only this object's audio messages may bypass normal UI dispatch. */
            s40_audio_set_message_dispatch(audio_cm_dispatch);
            assert(!s40_audio_dispatch_message(192));
            uint8_t* response = message(8, 0x1c);
            response[-5] = 0x75;
            response[-7] = 0xfc | (audio_cm_id(&object, abi) >> 8);
            response[-2] = audio_cm_id(&object, abi) + 1;
            assert(!s40_audio_dispatch_message((uintptr_t)response - 8));
            response[-2] = audio_cm_id(&object, abi);
            response[-5] = 0x0e;
            assert(!s40_audio_dispatch_message((uintptr_t)response - 8));
            response[-5] = 0x75;
            response[-8] = 1;
            assert(!s40_audio_dispatch_message((uintptr_t)response - 8));
            response[-8] = 0;
            assert(s40_audio_dispatch_message((uintptr_t)response - 8));
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
        } else {
            assert(result < 0);
            if (failure >= 4 && failure <= 7)
                assert(result == -25);
            if (failure == 8)
                assert(result == -30);
        }
        s40_audio_close();
        s40_audio_close();
        assert(!allocations && !active_object && !model_source && !model_policy &&
               !model_route_client && !route_requested);
    }
    failure = 0;
    assert(s40_audio_open(&format) == 1);
    model_source[(abi->source_layout == AUDIO_SOURCE_DEVICE
                          ? offsetof(audio_source_device, status)
                          : offsetof(audio_source_classic, status))] =
            4; /* server cancelled while producer still owns stream */
    assert(s40_audio_write(samples, 1) < 0);
    s40_audio_close();
    assert(!allocations && !active_object);
#ifdef TEST_RM443
    requested_volume = format.volume = 0;
    assert(s40_audio_open(&format) == 1);
    discard_blocks();
    assert(s40_audio_write(samples, 512) == 512);
    for (unsigned i = 0; i < blocks[0]->bytes; ++i)
        assert(!((const uint8_t*)blocks[0]->data)[i]);
    assert(samples[1] == 17); /* Caller keeps its original samples. */
    s40_audio_close();
    assert(!allocations && !active_object);
#endif
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
#ifdef TEST_RM443
    failure = 10; /* CREATE acknowledgment arrives only during close's drain. */
    requested_volume = format.volume = 65;
    assert(s40_audio_open(&format) == -25);
    assert(!route_pending && !route_client && !route_delete && !model_route_client);
    assert(!allocations && !active_object && !model_policy);
    failure = 9;
    requested_volume = format.volume = 65;
    assert(s40_audio_open(&format) == 1);
    s40_audio_close();
    assert(route_client == 27 && !route_delete && !active_object && !model_route_client);
    assert(s40_audio_open(&format) == -25 && s40_audio_status() == -25);
    assert(!active_object && !allocations && !model_policy);
#endif
    s40_audio_bind(NULL);
    assert(!s40_audio_dispatch_message(4096)); /* binding clears adapter hook */
    printf("PASS: PCM copy/backpressure, policy/open failures, timeout, allocation failure, "
           "cancellation, repeated close; %u plays, %u closes\n",
           plays, closes);
}
