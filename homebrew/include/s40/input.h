/* SPDX-License-Identifier: MIT */
#ifndef S40_INPUT_H
#define S40_INPUT_H
#include <stddef.h>
#include <stdint.h>

#include "s40/events.h"

/* Applications choose their own action bits and bindings. Several keys may
 * hold the same action, and one key may hold several actions. Bind printable
 * letters in lower case; uppercase events are normalized before lookup.
 */
typedef struct {
    int key;
    uint32_t actions;
} s40_input_binding;

/* Storage range for translated keys, including the phone's negative keys.
 * This is an implementation capacity, not a promise that every code exists. */
enum {
    S40_INPUT_FIRST_SOURCE = -64,
    S40_INPUT_LAST_SOURCE = 127,
    S40_INPUT_SOURCE_COUNT = S40_INPUT_LAST_SOURCE - S40_INPUT_FIRST_SOURCE + 1
};

typedef struct {
    uint32_t held, pressed, released;
    /* Private state; keep the binding array alive until the next init. */
    const s40_input_binding* bindings;
    size_t binding_count;
    unsigned layout, modifiers;
    uint32_t sources[S40_INPUT_SOURCE_COUNT];
} s40_input_state;

void s40_input_state_init(s40_input_state* state, unsigned layout,
                          const s40_input_binding* bindings, size_t count);
/* Clear edge masks once per application frame, before pumping/draining events.
 * A press and release in one batch set both edges even when held ends at zero.
 */
void s40_input_begin_frame(s40_input_state* state);
void s40_input_handle(s40_input_state* state, const s40_event* event);
/* Release actions when changing menus/bindings; release edges are retained. */
void s40_input_release_all(s40_input_state* state);
#endif
