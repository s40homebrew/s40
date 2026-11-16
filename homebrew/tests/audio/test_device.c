/* SPDX-License-Identifier: MIT */
/* Unknown identities and incomplete bindings fail before native calls. */
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "audio/devices/registry.h"
#include "firmware/bindings.h"
#include "s40/audio.h"

uint32_t* s40_result;
void* fw_os_block_alloc_no_wait(unsigned bytes) {
    (void)bytes;
    abort();
}
void fw_os_block_dealloc(void* pointer) {
    (void)pointer;
    abort();
}
uint32_t fw_os_current_time_read_in_ms(void) { abort(); }
void s40_pump(void) { abort(); }

int main(void) {
    s40_audio_format format = {11025, 1, 60, 100};
    assert(!s40_audio_bind_device());
    /* A trusted identity is required even when every prefix matches. */
    uint32_t prefix[8] = {0};
    audio_rom_binding binding = {(uintptr_t)prefix, 0x0b2ae445u};
    audio_profile sample = {.clock_anchor = 0x12345679u,
                            .parent_class = {(uintptr_t)prefix, 0x0b2ae445u},
                            .configuration = {.volume_percent = 1},
                            .bindings = {
                                    .register_object = binding,
                                    .ready = binding,
                                    .unregister_object = binding,
                                    .send = binding,
                                    .delete_message = binding,
                                    .source_create = binding,
                                    .source_share = binding,
                                    .source_limits = binding,
                                    .source_id = binding,
                                    .source_forget = binding,
                                    .source_eof = binding,
                                    .fragment_init = binding,
                                    .source_write = binding,
                                    .class_call = binding,
                                    .volume_scale = binding,
                            }};
    audio_abi resolved, previous;
    assert(audio_profile_resolve(&sample, 0x12345679u, &resolved));
    assert((uintptr_t)resolved.imports.source_write == (uintptr_t)prefix);
    memcpy(&previous, &resolved, sizeof(previous));
    assert(!audio_profile_resolve(&sample, 0x12345678u, &resolved)); /* Thumb bit matters. */
    ((uint8_t*)prefix)[31] = 1;
    assert(!audio_profile_resolve(&sample, 0x12345679u, &resolved));
    ((uint8_t*)prefix)[31] = 0;
    sample.bindings.source_write.entry = 0;
    assert(!audio_profile_resolve(&sample, 0x12345679u, &resolved));
    sample.bindings.source_write = binding;
    sample.bindings.volume_scale.entry = 0;
    assert(!audio_profile_resolve(&sample, 0x12345679u, &resolved));
    assert(!memcmp(&resolved, &previous, sizeof(resolved))); /* Failure cannot partially bind. */
    sample.configuration.volume_percent = 0;
    assert(audio_profile_resolve(&sample, 0x12345679u, &resolved));
    sample.parent_class.address = 1; /* Unknown identity must not read this. */
    assert(!audio_profile_resolve(&sample, 0x98765431u, &resolved));
    uint32_t descriptor[4096] = {0};
    s40_result = descriptor;
    descriptor[FW_API_DESCRIPTOR + API_FW_OS_CURRENT_TIME_READ_IN_MS] = 0x12345679;
    assert(!s40_audio_bind_device());
    assert(!s40_audio_status());
    assert(s40_audio_open(&format) < 0);
    s40_audio_close();
    puts("PASS: profile identity, prefix and required-import gates; unresolved bindings stay unchanged");
    return 0;
}
