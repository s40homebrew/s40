/* SPDX-License-Identifier: MIT */
#include "s40/frame_clock.h"

#include "time.h"

int s40_frame_clock_init(s40_frame_clock* clock, uint32_t now, uint32_t frames_num,
                         uint32_t frames_den) {
    uint64_t period = (uint64_t)frames_den * 1000;
    if (!frames_num || !frames_den || period < frames_num ||
        period > (uint64_t)frames_num * INT32_MAX)
        return 0;
    clock->period_ms = period / frames_num;
    clock->remainder = period % frames_num;
    clock->divisor = frames_num;
    s40_frame_clock_reset(clock, now);
    return 1;
}

void s40_frame_clock_reset(s40_frame_clock* clock, uint32_t now) {
    clock->deadline = now;
    clock->fraction = 0;
}

uint32_t s40_frame_clock_step(s40_frame_clock* clock) {
    clock->deadline += clock->period_ms;
    /* Avoid overflow even when the rate numerator approaches UINT32_MAX. */
    if (clock->fraction >= clock->divisor - clock->remainder) {
        clock->fraction -= clock->divisor - clock->remainder;
        ++clock->deadline;
    } else {
        clock->fraction += clock->remainder;
    }
    return clock->deadline;
}

uint32_t s40_frame_clock_delay(const s40_frame_clock* clock, uint32_t now) {
    return s40_time_until(clock->deadline, now);
}
