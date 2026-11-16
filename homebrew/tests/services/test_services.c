/* SPDX-License-Identifier: MIT */
#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include "libc/path.h"
#include "s40/frame_clock.h"
#include "s40/input.h"

enum { MOVE = 1, FIRE = 2, MENU = 4 };
static const s40_input_binding bindings[] = {{S40_KEYCODE_UP, MOVE},
                                             {'w', MOVE},
                                             {'1', FIRE},
                                             {S40_KEYCODE_SELECT, FIRE},
                                             {' ', FIRE | MENU}};

static void event(s40_input_state* state, int code, int type, unsigned metadata) {
    s40_event e = {S40_EVENT_KEYBOARD, type, (int)((uint16_t)code | metadata)};
    s40_input_handle(state, &e);
}

static void input_check(void) {
    s40_input_state state;
    s40_input_state_init(&state, S40_INPUT_KEYPAD, bindings, sizeof(bindings) / sizeof(*bindings));
    event(&state, S40_KEYCODE_UP, S40_KEY_DOWN, 0x1F0000);
    event(&state, 'W', S40_KEY_DOWN, 0);
    event(&state, '1', S40_KEY_DOWN, 0);
    assert(state.held == (MOVE | FIRE) && state.pressed == (MOVE | FIRE));
    s40_input_begin_frame(&state);
    event(&state, S40_KEYCODE_UP, S40_KEY_UP, 0x70000);
    assert(state.held == (MOVE | FIRE) && state.released == 0);
    event(&state, 'w', S40_KEY_UP, 0);
    event(&state, '1', S40_KEY_UP, 0);
    assert(state.held == 0 && state.released == (MOVE | FIRE));
    s40_input_begin_frame(&state);
    event(&state, '1', S40_KEY_REPEAT, 0);
    assert(state.held == 0 && state.pressed == 0);
    event(&state, ' ', S40_KEY_DOWN, 0);
    event(&state, ' ', S40_KEY_UP, 0);
    assert(state.held == 0 && state.pressed == (FIRE | MENU) && state.released == (FIRE | MENU));

    s40_input_state_init(&state, S40_INPUT_QWERTY, bindings, sizeof(bindings) / sizeof(*bindings));
    event(&state, S40_KEYCODE_UP, S40_KEY_DOWN, 0);
    event(&state, '1', S40_KEY_DOWN, 0);
    s40_input_begin_frame(&state);
    event(&state, S40_KEYCODE_FN, S40_KEY_DOWN, 0x02000000);
    assert(state.held == MOVE && state.released == FIRE);
    s40_event lost = {S40_EVENT_FOCUS, S40_FOCUS_LOST, 0};
    s40_input_handle(&state, &lost);
    assert(state.held == 0 && state.released == (MOVE | FIRE));
}

static void clock_check(void) {
    s40_frame_clock clock;
    assert(s40_frame_clock_init(&clock, 0, 4194304, 70224));
    for (unsigned i = 0; i < 100000; ++i)
        s40_frame_clock_step(&clock);
    assert(clock.deadline == (uint64_t)100000 * 70224 * 1000 / 4194304);
    assert(s40_frame_clock_delay(&clock, clock.deadline + 5000) == 0);
    s40_frame_clock_reset(&clock, UINT32_MAX - 3);
    s40_frame_clock_step(&clock);
    assert(s40_frame_clock_delay(&clock, UINT32_MAX - 3) == 16);
    assert(s40_frame_clock_delay(&clock, 13) == 0);
    assert(!s40_frame_clock_init(&clock, 0, 0, 1));
    assert(!s40_frame_clock_init(&clock, 0, 1001, 1));
    assert(!s40_frame_clock_init(&clock, 0, 1, UINT32_MAX));
}

static void path_check(void) {
    uint16_t out[256];
    assert(s40_path16("C:/predefgallery/", "./caf\xC3\xA9.gb", out, 256));
    assert(out[2] == '\\' && out[20] == 0xE9 && out[24] == 0);
    assert(s40_path16("C:/ignored/", "E:/\xF0\x9F\x8E\xAE.gb", out, 256));
    assert(out[0] == 'E' && out[3] == 0xD83C && out[4] == 0xDFAE);
    assert(!s40_path16("C:/", "x\xE2\x82", out, 256));
    assert(!s40_path16("C:/", "\xC0\xAF", out, 256));
    assert(!s40_path16("C:/", "\xED\xA0\x80", out, 256));
    assert(!s40_path16("C:/", "\xF4\x90\x80\x80", out, 256));
    assert(!s40_path16("C:/", "game.gb", out, 10));
    assert(s40_path16("C:/", "game.gb", out, 11));
}

int main(void) {
    input_check();
    clock_check();
    path_check();
    puts("PASS input aliases/edges/focus, rational timing, UTF-8 native paths");
    return 0;
}
