/* SPDX-License-Identifier: MIT */
#include "commgr/driver.h"
#include "firmware/bindings.h"
#include "internal.h"
#include "s40/audio.h"
#include "s40/runtime.h"
#include "services.h"

static audio_abi selected;
static int device_open(const s40_audio_format* format) { return audio_cm_open(&selected, format); }
static const s40_audio_driver device_driver = {device_open, audio_cm_write, audio_cm_queued,
                                               audio_cm_status, audio_cm_close};

int s40_audio_bind_device(void) {
    s40_audio_bind(NULL);
    if (!audio_cm_available() || !s40_result ||
        !audio_abi_from_services(s40_result + FW_API_DESCRIPTOR, &selected))
        return 0;
    s40_audio_bind(&device_driver);
    s40_audio_set_message_dispatch(audio_cm_dispatch);
    return 1;
}
