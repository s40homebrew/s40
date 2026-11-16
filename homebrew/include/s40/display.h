/* SPDX-License-Identifier: MIT */
#ifndef S40_DISPLAY_H
#define S40_DISPLAY_H
#include <stdint.h>

unsigned s40_display_width(void);
unsigned s40_display_height(void);
/* Native-sized 0xFFRRGGBB framebuffer owned by the runtime. Opening/presenting
 * suspends optional RGB565 video. Reopening preserves pixels; close or session
 * exit invalidates the pointer. Write opaque alpha, including for black pixels.
 */
uint32_t* s40_display_open(void);
void s40_display_present(void);
void s40_display_close(void);
/* Renew a finite foreground illumination lease while s40_pump runs. Disabling
 * resumes the normal light period; native exit disables automatically. Returns
 * -1 on unsupported firmware or request failure. No stored setting is changed.
 */
int s40_display_keep_awake(int enabled);
#endif
