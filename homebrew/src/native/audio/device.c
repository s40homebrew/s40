/* SPDX-License-Identifier: MIT */
#include "firmware/bindings.h"
#include "s40/audio.h"
#include "s40/runtime.h"

int s40_audio_bind_device(void) {
    uintptr_t clock =
            s40_result ? s40_result[FW_API_DESCRIPTOR + API_FW_OS_CURRENT_TIME_READ_IN_MS] : 0;
    return s40_audio_bind_firmware(clock);
}
