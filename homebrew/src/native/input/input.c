/* SPDX-License-Identifier: MIT */
#include "s40/input.h"

#include <string.h>

void s40_input_state_init(s40_input_state* state, unsigned layout,
                          const s40_input_binding* bindings, size_t count) {
    memset(state, 0, sizeof(*state));
    state->layout = layout;
    state->bindings = bindings;
    state->binding_count = bindings ? count : 0;
}

void s40_input_begin_frame(s40_input_state* state) {
    state->pressed = 0;
    state->released = 0;
}

static void update_held(s40_input_state* state) {
    uint32_t held = 0;
    for (unsigned i = 0; i < sizeof(state->sources) / sizeof(*state->sources); ++i)
        held |= state->sources[i];
    state->pressed |= held & ~state->held;
    state->released |= state->held & ~held;
    state->held = held;
}

void s40_input_release_all(s40_input_state* state) {
    memset(state->sources, 0, sizeof(state->sources));
    state->released |= state->held;
    state->held = 0;
}

void s40_input_handle(s40_input_state* state, const s40_event* event) {
    if (event->category == S40_EVENT_FOCUS && event->type == S40_FOCUS_LOST) {
        s40_input_release_all(state);
        return;
    }
    if (event->category != S40_EVENT_KEYBOARD ||
        (event->type != S40_KEY_DOWN && event->type != S40_KEY_UP))
        return;

    int code = s40_event_key(event);
    if (state->layout == S40_INPUT_QWERTY) {
        unsigned modifiers = s40_event_modifiers(event) & S40_MODIFIER_FN_MASK;
        if (modifiers != state->modifiers) {
            /* Fn changes translated characters without stable physical IDs.
             * End printable holds on a layer change; keep D-pad/Shift held.
             */
            for (int key = ' '; key <= S40_INPUT_LAST_SOURCE; ++key)
                state->sources[key - S40_INPUT_FIRST_SOURCE] = 0;
            update_held(state);
            state->modifiers = modifiers;
        }
        if (code == S40_KEYCODE_FN)
            return;
    }
    if (code >= 'A' && code <= 'Z')
        code += 'a' - 'A';
    if (code == S40_KEYCODE_LINE_FEED)
        code = S40_KEYCODE_RETURN;
    if (code == S40_KEYCODE_DELETE)
        code = S40_KEYCODE_BACKSPACE;
    if (code < S40_INPUT_FIRST_SOURCE || code > S40_INPUT_LAST_SOURCE)
        return;
    unsigned index = code - S40_INPUT_FIRST_SOURCE;
    if (event->type == S40_KEY_UP) {
        state->sources[index] = 0;
    } else if (!state->sources[index]) {
        for (size_t i = 0; i < state->binding_count; ++i)
            if (state->bindings[i].key == code)
                state->sources[index] |= state->bindings[i].actions;
    }
    update_held(state);
}
