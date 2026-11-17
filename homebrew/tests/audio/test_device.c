/* SPDX-License-Identifier: MIT */
/* Resolved capabilities are atomic; unavailable services never call firmware. */
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "audio/services.h"
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
    uint32_t descriptor[FW_API_DESCRIPTOR + FW_API_COUNT] = {0};
    uint32_t* services = descriptor + FW_API_DESCRIPTOR;
    s40_result = descriptor;
    audio_abi resolved, previous;
    assert(!audio_abi_from_services(services, &resolved));
    for (unsigned i = FW_AUDIO_FIRST + 1; i < FW_AUDIO_LIMIT; ++i)
        services[i] = 0x1000 + 4 * i + 1;
    services[API_AUDIO_ABI] = FW_AUDIO_ABI_CLASSIC;
    /* An ARM entry stays even; decoding must not invent a Thumb bit. */
    services[API_AUDIO_VOLUME_SCALE] = 0x2000;
    assert(audio_abi_from_services(services, &resolved));
    assert(resolved.object_layout == AUDIO_CM_CLASSIC);
    assert(resolved.transport == AUDIO_TRANSPORT_RAW_PCM);
    assert((uintptr_t)resolved.imports.volume_scale == 0x2000);
    assert((uintptr_t)resolved.imports.source_write == services[API_AUDIO_SOURCE_WRITE]);
    memcpy(&previous, &resolved, sizeof(previous));
    services[API_AUDIO_SOURCE_WRITE] = 0;
    assert(!audio_abi_from_services(services, &resolved));
    assert(!memcmp(&resolved, &previous, sizeof(resolved)));
    assert(!s40_audio_bind_device());
    assert(!s40_audio_status());
    assert(s40_audio_open(&format) < 0);
    services[API_AUDIO_SOURCE_WRITE] = 0x3001;
    services[API_AUDIO_ABI] = 99;
    assert(!audio_abi_from_services(services, &resolved));
    assert(!memcmp(&resolved, &previous, sizeof(resolved)));
    assert(audio_abi_configure(FW_AUDIO_ABI_LEGACY, &resolved));
    assert(resolved.object_layout == AUDIO_CM_LEGACY && resolved.policy == AUDIO_POLICY_NONE);
    assert(audio_abi_configure(FW_AUDIO_ABI_DEVICE_IHF, &resolved));
    assert(resolved.object_layout == AUDIO_CM_DEVICE && resolved.route == AUDIO_ROUTE_OWN_IHF);
    assert(audio_abi_configure(FW_AUDIO_ABI_DEVICE_MEDIA, &resolved));
    assert(resolved.policy == AUDIO_POLICY_EXPLICIT && resolved.route == AUDIO_ROUTE_EXISTING);
    s40_audio_close();
    puts("PASS: capability decoding, ARM/Thumb entries and incomplete-service rejection");
    return 0;
}
