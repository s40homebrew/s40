/* SPDX-License-Identifier: MIT */
#ifndef S40_AUDIO_INTERNAL_H
#define S40_AUDIO_INTERNAL_H
#include <stdint.h>

/* Private session hook. Consumes only messages directed to an adapter-owned
 * object whose callback cannot touch firmware UI or the display transport.
 * All other messages retain ordinary ComMgr dispatch and video suspension. */
int s40_audio_dispatch_message(uintptr_t message);
void s40_audio_set_message_dispatch(int (*dispatch)(uintptr_t));
#endif
