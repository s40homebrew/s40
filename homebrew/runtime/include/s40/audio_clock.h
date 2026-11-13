/* SPDX-License-Identifier: MIT */
#ifndef S40_AUDIO_CLOCK_H
#define S40_AUDIO_CLOCK_H
#include <stdint.h>

/* Conservative PCM playhead estimate for decoders that read ahead. A source
 * dequeue is NOT a hardware playback event. Advance no faster than the PCM
 * sample rate, and never beyond frames actually removed from the source.
 * This is a pacing clock, not a measured DAC position. Call regularly; the
 * outstanding PCM window must be less than a second. Counters share an origin.
 */
typedef struct {
    uint32_t last_ms, fraction, played;
} s40_audio_clock;

static inline void s40_audio_clock_reset(s40_audio_clock* c, uint32_t now, uint32_t origin) {
    c->last_ms = now;
    c->fraction = 0;
    c->played = origin;
}
static inline uint32_t s40_audio_clock_update(s40_audio_clock* c, uint32_t now, unsigned rate,
                                              uint32_t submitted, unsigned source_queued) {
    uint32_t elapsed = now - c->last_ms;
    c->last_ms = now;
    /* Don't accumulate catch-up credit while loading/stalled. The current
     * stream formats are <=22050 Hz, so this also bounds integer products. */
    if (elapsed > 1000)
        elapsed = 1000;
    uint32_t scaled = elapsed * rate + c->fraction;
    uint32_t advance = scaled / 1000;
    c->fraction = scaled % 1000;
    uint32_t removed = submitted > source_queued ? submitted - source_queued : 0;
    uint32_t available = removed > c->played ? removed - c->played : 0;
    if (advance > available)
        advance = available;
    c->played += advance;
    return c->played;
}
#endif
