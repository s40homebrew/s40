/* SPDX-License-Identifier: MIT */
/* Small consumer of the S40 API; no Doom symbols, controls or assets. */
#include "s40/runtime.h"

static void draw(uint32_t* pixels, unsigned width, unsigned height, int x, int y, uint32_t color) {
    for (unsigned row = 0; row < height; ++row)
        for (unsigned column = 0; column < width; ++column)
            pixels[row * width + column] = 0xFF102030;
    for (int row = y; row < y + 16; ++row)
        for (int column = x; column < x + 16; ++column)
            pixels[row * width + column] = color;
    s40_display_present();
}

int s40_app_main(void) {
    uint32_t* pixels = s40_display_open();
    unsigned width = s40_display_width(), height = s40_display_height();
    int x = (width - 16) / 2, y = (height - 16) / 2;
    uint32_t color = 0xFFFFFFFF;
    draw(pixels, width, height, x, y, color);
    for (;;) {
        s40_event event;
        s40_pump();
        while (s40_poll_event(&event)) {
            if (event.category != S40_EVENT_KEYBOARD || event.type == S40_KEY_UP)
                continue;
            int key = s40_event_key(&event);
            switch (key) {
                case S40_KEYCODE_UP:
                    y -= 4;
                    break;
                case S40_KEYCODE_DOWN:
                    y += 4;
                    break;
                case S40_KEYCODE_LEFT:
                    x -= 4;
                    break;
                case S40_KEYCODE_RIGHT:
                    x += 4;
                    break;
                case S40_KEYCODE_SELECT:
                case ' ':
                    if (event.type == S40_KEY_DOWN)
                        color = color == 0xFFFFFFFF ? 0xFF00FF80 : 0xFFFFFFFF;
                    break;
                case S40_KEYCODE_SOFT_RIGHT:
                    return 0;
            }
            if (x < 0)
                x = 0;
            if (y < 0)
                y = 0;
            if (x > (int)width - 16)
                x = width - 16;
            if (y > (int)height - 16)
                y = height - 16;
            draw(pixels, width, height, x, y, color);
        }
        s40_sleep_ms(16);
    }
}
