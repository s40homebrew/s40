/* SPDX-License-Identifier: MIT */
/* Audited S40 master-display ABI, shared by native applications. */
#include <stdlib.h>

#include "internal.h"
#include "firmware/bindings.h"
#include "s40/runtime.h"
#include "s40/video.h"
#include "video_internal.h"

static int reserve_token = S40_DISPLAY_UNRESERVED;
static s40_display_buffer* lcd;
static struct fw_gdi_canvas canvas;
static struct fw_gdi_canvas_legacy legacy_canvas;
typedef int (*ui_refresh_fn)(int, int, int, int, void*, int, int);
static ui_refresh_fn ui_refresh;

static void reserve_display(void) {
    if (!ui_refresh && reserve_token == S40_DISPLAY_UNRESERVED) {
        if (*fw_lcd_owner != S40_DISPLAY_UNRESERVED)
            s40_error("LCD already reserved");
        reserve_token = fw_gdi_display_master_reserve_enable(S40_DISPLAY_MASTER);
    }
}

void s40_display_yield(void) {
    if (reserve_token != S40_DISPLAY_UNRESERVED) {
        fw_gdi_display_master_reserve_disable(S40_DISPLAY_MASTER, reserve_token);
        reserve_token = S40_DISPLAY_UNRESERVED;
    }
}

unsigned s40_display_width(void) { return s40_result[FW_DISPLAY_WIDTH_WORD]; }
unsigned s40_display_height(void) { return s40_result[FW_DISPLAY_HEIGHT_WORD]; }
uint32_t* s40_display_open(void) {
    s40_video_suspend();
    if (lcd) {
        reserve_display();
        return lcd->pixels;
    }
    unsigned width = s40_display_width(), height = s40_display_height();
    if (!width || !height || width > S40_DISPLAY_MAX_DIMENSION ||
        height > S40_DISPLAY_MAX_DIMENSION || width * height > S40_DISPLAY_MAX_PIXELS)
        s40_error("Unsupported display geometry");
    ui_refresh = (ui_refresh_fn)(uintptr_t)s40_result[FW_UI_REFRESH_WORD];
    if (s40_result[FW_CANVAS_LAYOUT_WORD] == FW_CANVAS_LEGACY && !ui_refresh)
        s40_error("Foreground display callback missing");
    lcd = calloc(1, sizeof(*lcd));
    if (!lcd)
        s40_error("LCD allocation failed");
    // GDI type 12 / HAL type 9 expects opaque 0xFFRRGGBB pixels. Zero alpha
    // leaves old phone UI pixels visible on the audited master-display path.
    lcd->format = S40_HAL_ARGB8888;
    lcd->width = width;
    canvas.pixels = lcd->pixels;
    canvas.width = width;
    canvas.height = height;
    canvas.format = S40_GDI_ARGB8888;
    legacy_canvas.pixels = lcd->pixels;
    legacy_canvas.width = width;
    legacy_canvas.height = height;
    legacy_canvas.format = S40_GDI_ARGB8888;
    reserve_display();
    for (unsigned i = 0; i < width * height; ++i)
        lcd->pixels[i] = S40_ARGB_BLACK;
    return lcd->pixels;
}
void s40_display_present(void) {
    s40_video_suspend();
    if (!lcd)
        return;
    reserve_display();
    if (ui_refresh) {
        // Follow the active Java UI's native presentation path. Older GDI
        // renders directly; newer firmware can schedule a painter worker.
        void* source = s40_result[FW_CANVAS_LAYOUT_WORD] == FW_CANVAS_LEGACY ? (void*)&legacy_canvas
                                                                             : (void*)&canvas;
        ui_refresh(0, 0, canvas.width, canvas.height, source, 0, 0);
    } else {
        fw_gdi_display_master_update(&canvas, S40_DISPLAY_MASTER, 0, 0, canvas.width, canvas.height,
                                     0);
    }
}
void s40_display_close(void) {
    s40_display_yield();
    if (lcd) {
        free(lcd);
        lcd = NULL;
        canvas.pixels = NULL;
        legacy_canvas.pixels = NULL;
        ui_refresh = NULL;
    }
}
