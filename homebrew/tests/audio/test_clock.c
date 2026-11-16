/* SPDX-License-Identifier: MIT */
#include <assert.h>
#include <stdio.h>

#include "s40/audio_clock.h"

int main(void) {
    s40_audio_clock c;
    /* A greedy decoder removes every submitted frame immediately. Calling
     * the mixer frequently must not fill its hidden buffers faster than real
     * time, even though the source queue always reads as empty. */
    s40_audio_clock_reset(&c, 0, 0);
    unsigned submitted = 1323;
    for (unsigned ms = 0; ms <= 60000; ms++) {
        unsigned played = s40_audio_clock_update(&c, ms, 11025, submitted, 0);
        assert(played == ms * 11025 / 1000);
        submitted = played + 1323;
        assert(submitted - ms * 11025 / 1000 == 1323);
        assert(s40_audio_clock_update(&c, ms, 11025, submitted, 0) == played);
    }
    /* No dequeue means no playback. A stall doesn't create a large credit
     * that can be spent on future samples once audio resumes. */
    s40_audio_clock_reset(&c, 0, 0);
    assert(s40_audio_clock_update(&c, 5000, 11025, 1323, 1323) == 0);
    assert(s40_audio_clock_update(&c, 5010, 11025, 1323, 0) == 110);
    assert(s40_audio_clock_update(&c, 15010, 11025, 1323, 0) == 1323);
    assert(s40_audio_clock_update(&c, 15010, 11025, 2646, 0) == 1323);
    /* Firmware uptime wraps at 2^32 ms. Long-session mixer resets can use a
     * nonzero sample origin without resetting the whole audio stream. */
    s40_audio_clock_reset(&c, 0xfffffff0u, 8192);
    assert(s40_audio_clock_update(&c, 0x10, 11025, 10000, 0) == 8192 + 352);
    puts("PASS: decoder read-ahead pacing, repeated polls, fractional rate, stalls, uptime wrap "
         "and sample-origin reset");
}
