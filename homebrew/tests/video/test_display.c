/* SPDX-License-Identifier: MIT */
/* Ordinary display reservation can yield without invalidating its pixels. */
#include <assert.h>
#include <stdarg.h>
#include <stdlib.h>

#include "firmware/bindings.h"
#include "s40/runtime.h"
#include "display/video_internal.h"

static uint32_t result[1200];
uint32_t* s40_result = result;
static volatile int owner = -1;
volatile int* fw_lcd_owner = &owner;
static unsigned reserves, releases, updates, suspends;
static int token;

void s40_error(const char* format, ...) {
    (void)format;
    abort();
}
void s40_video_suspend(void) { ++suspends; }
int fw_gdi_display_master_reserve_enable(unsigned display) {
    assert(display == 1 && owner == -1);
    owner = 7;
    token = 40 + ++reserves;
    return token;
}
void fw_gdi_display_master_reserve_disable(unsigned display, int saved) {
    assert(display == 1 && owner == 7 && saved == token);
    owner = -1;
    ++releases;
}
void fw_gdi_display_master_update(struct fw_gdi_canvas* canvas, int display, int x, int y, int w, int h,
                                  int extra) {
    assert(owner == 7 && canvas && display == 1 && x == 0 && y == 0);
    assert(w == 240 && h == 320 && extra == 0);
    ++updates;
}

int main(void) {
    result[FW_DISPLAY_WIDTH_WORD] = 240;
    result[FW_DISPLAY_HEIGHT_WORD] = 320;
    uint32_t* pixels = s40_display_open();
    assert(pixels && reserves == 1 && releases == 0);
    pixels[0] = 0xff12ab34;
    s40_display_present();
    assert(updates == 1 && reserves == 1);
    s40_display_yield();
    s40_display_yield();
    assert(owner == -1 && releases == 1 && pixels[0] == 0xff12ab34);
    assert(s40_display_open() == pixels && reserves == 2);
    s40_display_yield();
    s40_display_present();
    assert(updates == 2 && reserves == 3 && pixels[0] == 0xff12ab34);
    s40_display_close();
    assert(owner == -1 && releases == 3 && suspends == 4);
    s40_display_close();
    assert(releases == 3);
    return 0;
}
