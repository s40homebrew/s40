/* SPDX-License-Identifier: MIT */
#ifndef S40_VIDEO_INTERNAL_H
#define S40_VIDEO_INTERNAL_H
#include <stdint.h>

/* Session dispatch only. Returns one for an audited message consumed while
 * video owns GDI. Otherwise restores normal output before returning zero.
 */
int s40_video_dispatch_message(uintptr_t message);
int s40_video_transport_active(void);
/* Release ordinary master-display ownership without invalidating its pixels.
 * Call only after video is suspended. Ordinary open/present reacquires it.
 */
void s40_display_yield(void);
int s40_video_prepare_services(void);
void s40_display_service_awake(void);
#endif
