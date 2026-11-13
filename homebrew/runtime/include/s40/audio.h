/* SPDX-License-Identifier: MIT */
#ifndef S40_AUDIO_H
#define S40_AUDIO_H
#include <stdint.h>

/* One native PCM stream per application. All calls run on the application
 * task, outside message callbacks. Samples are signed 16-bit little endian,
 * interleaved for stereo. Counts are frames (one sample per channel).
 * write copies accepted samples; the caller always retains its buffer.
 * Zero means backpressure; negative results mean unavailable/error.
 */
typedef struct {
    unsigned rate;      /* 11025 or 22050 Hz */
    unsigned channels;  /* 1 or 2 */
    unsigned volume;    /* firmware volume, 0..100 */
    unsigned buffer_ms; /* source queue cap, 100..500 ms; 0 = 500 */
} s40_audio_format;

int s40_audio_open(const s40_audio_format* format);
int s40_audio_write(const int16_t* samples, unsigned frames);
unsigned s40_audio_queued(void);
int s40_audio_status(void); /* 1 open, 0 closed, negative error */
void s40_audio_close(void); /* idempotent, including partial startup */

/* Device adapters are explicit: unsupported profiles remain silent. Bind
 * before open; the descriptor must outlive the session. No OS audio thread
 * may call code in a payload after close returns. */
typedef struct {
    int (*open)(const s40_audio_format*);
    int (*write)(const int16_t*, unsigned);
    unsigned (*queued)(void);
    int (*status)(void);
    void (*close)(void);
} s40_audio_driver;
void s40_audio_bind(const s40_audio_driver* driver);

/* Exact firmware adapter, linked explicitly by RM-839 02.33 ports. */
extern const s40_audio_driver s40_audio_rm839_0233;
#endif
