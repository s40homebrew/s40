/* SPDX-License-Identifier: MIT */
#ifndef S40_AUDIO_COMMGR_DRIVER_H
#define S40_AUDIO_COMMGR_DRIVER_H
#include "abi.h"
#include "s40/audio.h"

/* One active stream, shared by all firmware adapters. The immutable ABI must
 * outlive the stream; close before replacing it. No callbacks leave this task. */
int audio_cm_open(const audio_abi*, const s40_audio_format*);
int audio_cm_write(const int16_t*, unsigned frames);
unsigned audio_cm_queued(void);
int audio_cm_status(void);
void audio_cm_close(void);
int audio_cm_dispatch(uintptr_t message);
/* False after an unresolved output-client registration/deletion. */
int audio_cm_available(void);
#endif
