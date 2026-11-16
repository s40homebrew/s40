/* SPDX-License-Identifier: MIT */
#ifndef S40_DISPLAY_INTERNAL_H
#define S40_DISPLAY_INTERNAL_H
#include <stddef.h>
#include <stdint.h>

/* Limits of the runtime-owned ordinary canvas and nearest-neighbour maps.
 * Panel dimensions still come from the selected firmware profile. */
enum {
    S40_DISPLAY_MAX_DIMENSION = 320,
    S40_DISPLAY_MAX_PIXELS = 240 * 320,
    S40_DISPLAY_MASTER = 1,
    S40_DISPLAY_UNRESERVED = -1,
    S40_GDI_ARGB8888 = 12,
    S40_HAL_ARGB8888 = 9
};
#define S40_ARGB_BLACK UINT32_C(0xff000000)

/* The firmware takes the canvas pixels, not this allocation header. Keep the
 * historical HAL metadata preceding the pixels for the ordinary display ABI. */
typedef struct {
    uint16_t format, width;
    uint32_t pixels[S40_DISPLAY_MAX_PIXELS];
} s40_display_buffer;
typedef char s40_display_pixels_offset[(offsetof(s40_display_buffer, pixels) == 4) ? 1 : -1];

static inline uint32_t s40_rgb565_to_argb(uint16_t pixel) {
    unsigned r = (pixel >> 11) & 31, g = (pixel >> 5) & 63, b = pixel & 31;
    return S40_ARGB_BLACK | (((r << 3) | (r >> 2)) << 16) | (((g << 2) | (g >> 4)) << 8) |
           (b << 3) | (b >> 2);
}
#endif
