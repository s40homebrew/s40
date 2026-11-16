/* SPDX-License-Identifier: MIT */
#ifndef S40_VIDEO_H
#define S40_VIDEO_H
#include <stdint.h>

enum { S40_VIDEO_FIT = 0, S40_VIDEO_NATIVE = 1 };

/* A packed, host-endian RGB565 source surface, owned by the runtime. Width
 * and height are 1..320. Returns NULL on invalid geometry/allocation failure.
 * Reopening with the same dimensions preserves pixels; changing dimensions
 * invalidates the old pointer. Close also happens at native session exit.
 */
uint16_t* s40_video_open(unsigned width, unsigned height);
/* Fit preserves aspect ratio using nearest-neighbour scaling. Native keeps
 * 1:1 pixels if the source fits; larger sources use fit. Both are centered
 * against black. Default is fit. Changing scale does not replace the source.
 */
void s40_video_set_scale(unsigned scale);
/* Copies the source into runtime-owned presentation buffers. The application
 * may immediately change its source pixels after return. Zero means success;
 * -1 means no surface is open. Firmware-specific fast output is optional and
 * failures restore normal output before falling back to the ordinary display.
 * This may wait for the preceding transfer and the panel's next refresh edge.
 * Pace emulation separately: the physical panel can be slower than the guest.
 */
int s40_video_present(void);
/* Wait for the last submitted transfer without leaving video mode. Returns
 * zero after completion, negative on a transport error. Useful when timing
 * completed frames. This does not count distinct optically visible frames.
 */
int s40_video_wait(void);
/* Restore ordinary phone rendering, keeping source pixels for later resume.
 * Next present resumes video. Opening/presenting the ordinary display also
 * suspends video. Safe to use for app menus, pause, and file dialogs.
 */
void s40_video_suspend(void);
void s40_video_close(void);
/* Diagnostic name, valid until the next open/present/close. */
const char* s40_video_backend(void);
/* "none" when fast output is selected; otherwise a diagnostic explanation. */
const char* s40_video_fallback_reason(void);
/* Session-lifetime diagnostics. These count software operations, not visible
 * optical frames or physical key presses. Counters wrap modulo 2^32.
 */
typedef struct {
    uint32_t presents, fast_key_messages, service_yields, message_yields;
    uint32_t suspend_ms, longest_suspend_ms;
    uint32_t fast_audio_messages;
} s40_video_stats;
void s40_video_get_stats(s40_video_stats* out);
#endif
