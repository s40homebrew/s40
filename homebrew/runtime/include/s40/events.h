/* SPDX-License-Identifier: MIT */
#ifndef S40_EVENTS_H
#define S40_EVENTS_H
#include <stdint.h>

#include "firmware_api.h"
#define S40_INPUT_KEYPAD FW_INPUT_KEYPAD
#define S40_INPUT_QWERTY FW_INPUT_QWERTY

typedef struct {
    int category, type, value;
} s40_event;
/* value's low half is the signed translated key. Upper bits carry device
 * metadata, not a physical key identity. Consumers decide their own bindings.
 */
static inline int s40_event_key(const s40_event* event) { return (int16_t)event->value; }

enum {
    S40_EVENT_KEYBOARD = 1,
    S40_EVENT_FOCUS = 2,
    S40_KEY_DOWN = 1,
    S40_KEY_REPEAT = 2,
    S40_KEY_UP = 3,
    S40_FOCUS_LOST = 4
};

/* Values delivered by Nokia's native Java event queue. Shift/Fn are audited
 * C3 codes, not USB scan codes. Other vendor keys are deliberately unbound.
 */
enum {
    S40_KEYCODE_UP = -1,
    S40_KEYCODE_DOWN = -2,
    S40_KEYCODE_LEFT = -3,
    S40_KEYCODE_RIGHT = -4,
    S40_KEYCODE_SELECT = -5,
    S40_KEYCODE_SOFT_LEFT = -6,
    S40_KEYCODE_SOFT_RIGHT = -7,
    S40_KEYCODE_FN = -50,
    S40_KEYCODE_SHIFT = -55,
    S40_KEYCODE_BACKSPACE = 8,
    S40_KEYCODE_LINE_FEED = 10,
    S40_KEYCODE_RETURN = 13,
    S40_KEYCODE_DELETE = 127,
    S40_MODIFIER_FN_MASK = 6
};

#endif
