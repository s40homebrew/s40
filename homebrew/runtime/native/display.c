/* SPDX-License-Identifier: MIT */
/* Audited S40 master-display ABI, shared by native applications. */
#include <stdlib.h>

#include "firmware.h"
#include "s40/runtime.h"

static int reserve_token = -1;
static struct {
    uint16_t format, width;
    uint32_t pixels[240 * 320];
}* lcd;
static struct fw_gdi_canvas canvas;
static struct fw_gdi_canvas_legacy legacy_canvas;
typedef int (*ui_refresh_fn)(int, int, int, int, void*, int, int);
static ui_refresh_fn ui_refresh;

unsigned s40_display_width(void) { return s40_result[FW_DISPLAY_WIDTH_WORD]; }
unsigned s40_display_height(void) { return s40_result[FW_DISPLAY_HEIGHT_WORD]; }
uint32_t* s40_display_open(void) {
    if (lcd)
        return lcd->pixels;
    unsigned width = s40_display_width(), height = s40_display_height();
    if (!width || !height || width > 320 || height > 320 || width * height > 240 * 320)
        s40_error("Unsupported display geometry");
    ui_refresh = (ui_refresh_fn)s40_result[FW_UI_REFRESH_WORD];
    if (s40_result[FW_CANVAS_LAYOUT_WORD] == FW_CANVAS_LEGACY && !ui_refresh)
        s40_error("Foreground display callback missing");
    if (!ui_refresh && *fw_lcd_owner != -1)
        s40_error("LCD already reserved");
    lcd = calloc(1, sizeof(*lcd));
    if (!lcd)
        s40_error("LCD allocation failed");
    // GDI type 12 / HAL type 9 expects opaque 0xFFRRGGBB pixels. Zero alpha
    // leaves old phone UI pixels visible on the audited master-display path.
    lcd->format = 9;
    lcd->width = width;
    canvas.pixels = lcd->pixels;
    canvas.width = width;
    canvas.height = height;
    canvas.format = 12;
    legacy_canvas.pixels = lcd->pixels;
    legacy_canvas.width = width;
    legacy_canvas.height = height;
    legacy_canvas.format = 12;
    if (!ui_refresh)
        reserve_token = fw_gdi_display_master_reserve_enable(1);
    for (unsigned i = 0; i < width * height; ++i)
        lcd->pixels[i] = 0xFF000000;
    return lcd->pixels;
}
void s40_display_present(void) {
    if (!lcd)
        return;
    if (ui_refresh) {
        // Follow the active Java UI's native presentation path. Older GDI
        // renders directly; newer firmware can schedule a painter worker.
        void* source = s40_result[FW_CANVAS_LAYOUT_WORD] == FW_CANVAS_LEGACY ? (void*)&legacy_canvas
                                                                             : (void*)&canvas;
        ui_refresh(0, 0, canvas.width, canvas.height, source, 0, 0);
    } else {
        fw_gdi_display_master_update(&canvas, 1, 0, 0, canvas.width, canvas.height, 0);
    }
}
void s40_display_close(void) {
    if (reserve_token != -1) {
        fw_gdi_display_master_reserve_disable(1, reserve_token);
        reserve_token = -1;
    }
    if (lcd) {
        free(lcd);
        lcd = NULL;
        canvas.pixels = NULL;
        legacy_canvas.pixels = NULL;
        ui_refresh = NULL;
    }
}
