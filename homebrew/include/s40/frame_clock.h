/* SPDX-License-Identifier: MIT */
#ifndef S40_FRAME_CLOCK_H
#define S40_FRAME_CLOCK_H
#include <stdint.h>

/* Rational frame deadlines in the same wrapping millisecond domain as
 * s40_uptime_ms(). Presentation and emulation may have different cadences.
 * This helper neither pumps events nor sleeps, and never discards late frames.
 */
typedef struct {
    uint32_t deadline;           /* Absolute wrapping milliseconds. */
    uint32_t period_ms;          /* Whole milliseconds per frame. */
    uint32_t remainder, divisor; /* Fractional milliseconds per frame. */
    uint32_t fraction;           /* Accumulated remainder, always less than divisor. */
} s40_frame_clock;

/* frames_num / frames_den frames per second. Returns 0 for an invalid rate.
 * Rates must be at most 1000 Hz, with a period shorter than 2^31 ms.
 */
int s40_frame_clock_init(s40_frame_clock* clock, uint32_t now, uint32_t frames_num,
                         uint32_t frames_den);
void s40_frame_clock_reset(s40_frame_clock* clock, uint32_t now);
/* Advance by one frame, carrying the fractional milliseconds exactly. */
uint32_t s40_frame_clock_step(s40_frame_clock* clock);
/* Milliseconds until the current deadline, or zero when already due.
 * Samples must remain less than 2^31 ms apart; reset after a long pause.
 */
uint32_t s40_frame_clock_delay(const s40_frame_clock* clock, uint32_t now);
#endif
