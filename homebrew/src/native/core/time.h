/* SPDX-License-Identifier: MIT */
#ifndef S40_TIME_INTERNAL_H
#define S40_TIME_INTERNAL_H
#include <stdint.h>

/* Wrapping millisecond deadline comparison. A caller must keep samples and
 * deadlines within half the uint32_t range, resetting after longer pauses. */
static inline uint32_t s40_time_until(uint32_t deadline, uint32_t now) {
    uint32_t remaining = deadline - now;
    return remaining <= INT32_MAX ? remaining : 0;
}
#endif
