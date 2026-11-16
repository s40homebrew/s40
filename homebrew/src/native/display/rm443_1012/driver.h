/* SPDX-License-Identifier: MIT */
/* Private RM-443 10.12 adapter. No clock or bus timing changes. */
#ifndef S40_VIDEO_RM443_H
#define S40_VIDEO_RM443_H
#include <stdint.h>

int s40_rm443_video_supported(void);
const char* s40_rm443_video_support_reason(void);
int s40_rm443_video_foreground(void);
int s40_rm443_video_keyboard(uintptr_t message);
/* Pending bits: internal queue1, subscriptions2, display-lock waiters4.
 * Zero means idle; -1 means unsupported task/resource context. */
enum {
    RM443_SERVICE_QUEUE = 1,
    RM443_SERVICE_SUBSCRIPTIONS = 2,
    RM443_SERVICE_DISPLAY_WAITER = 4
};
int s40_rm443_video_services_pending(void);
int s40_rm443_video_backlight(unsigned seconds, int default_period);

enum {
    RM443_VIDEO_OK = 0,
    RM443_VIDEO_PLATFORM = -1,
    RM443_VIDEO_STATE = -2,
    RM443_VIDEO_FORMAT = -3,
    RM443_VIDEO_TRANSFER = -4,
    RM443_VIDEO_NO_DMA = -5,
    RM443_VIDEO_RETAINED = -6,
    RM443_VIDEO_TE_TIMEOUT = -7
};

/* Called from firmware completion context: memory writes only, no I/O, UI,
 * allocation, sleeps, reentry or exit. Argument0=success, negative=failed. */
typedef void (*s40_rm443_video_callback)(int result);

/* Acquires and retains GDI scratchpad lock1 until successful restore().
 * Requires normal powered DMA display and original COLMOD07. Programs the
 * 192-byte RGBSET LUT and COLMOD05, then reads back COLMOD via stock0C reader.
 * The LUT is565-specific; restoration of07 bypasses it, but does not recover
 * unknown original LUT contents. No other renderer may run during this session.
 * Suspends the stock5-second COLMOD07 refresh timer259 under its audited state
 * and ownership gate; restore() restarts its normal period. Every submit also
 * verifies the wire format before DMA, repairing an unexpected mismatch.
 * A negative return with active()!=0 means ownership is retained: restore first.
 * Do not invoke while caller already owns scratchpad lock1. */
int s40_rm443_video_init(uint8_t* readback);

/* Source: four-byte aligned, exactly153600 bytes, high byte then low byte for
 * each RGB565 pixel, 240x320 row-major. Source and callback code must remain
 * alive and immutable until in_flight()==0. Returns STATE if a frame is pending;
 * caller may compute/pack next private frame, then drain and submit it.
 * Uses stock DMA cache-clean, completion, pending count and80ms recovery.
 */
int s40_rm443_video_submit(const void* packed_rgb565, s40_rm443_video_callback callback);

/* Wait for a fresh GPIO25 TE rising edge before submitting, IRQs enabled.
 * Bounded to50ms (also a fixed iteration limit). On TE_TIMEOUT no DMA or
 * callback owns the source. te_ms records the observed edge in firmware ms.
 * Same panel/buffer/ownership requirements as ordinary submit(). */
int s40_rm443_video_submit_synced(const void* packed_rgb565, s40_rm443_video_callback callback,
                                  uint32_t* te_ms);

/* Bounded, IRQ-enabled wait. RETAINED means DMA/callback ownership is unknown:
 * keep code/source/session alive and retry draining; never exit/free on it.
 * A timeout recovered by the stock handler returns TRANSFER with no in-flight
 * frame, allowing restore(). Other firmware helpers have their original waits.
 */
int s40_rm443_video_drain(uint32_t timeout_ms);

/* Diagnostic RAMRD read of one actual panel-memory pixel, after drain().
 * Uses stock five-cycle reader (one dummy, four data bytes); returns the first
 * RGB triplet as 0x00RRGGBB. MC2PA8201 documents RGB24 RAMRD in every COLMOD.
 * This panel-family interpretation still requires hardware validation: inspect
 * primary-color patches first. It does not read the firmware RGB888 scratch.
 * Caller supplies x<240,y<320; no DMA may be pending. Untimed diagnostic only.
 */
int s40_rm443_video_read_pixel(unsigned x, unsigned y, uint32_t* rgb);

/* Read current panel COLMOD without changing it, after drain(). Uses the
 * existing session lock and acquires/releases driver gate and bus locally. */
int s40_rm443_video_read_format(uint8_t* format);

/* Drains first, restores COLMOD07 and verifies it, then releases lock1.
 * On RETAINED/FORMAT while active remains1, do not exit or call normal renderers.
 * After success, immediately redraw the screen using the normal RGB888 path.
 */
int s40_rm443_video_restore(uint32_t timeout_ms, uint8_t* readback);
int s40_rm443_video_active(void);
int s40_rm443_video_in_flight(void);
uint32_t s40_rm443_video_completed_ms(void);
int s40_rm443_video_last_result(void);
unsigned s40_rm443_video_format_repairs(void);
unsigned s40_rm443_video_first_repair(void);
#endif
