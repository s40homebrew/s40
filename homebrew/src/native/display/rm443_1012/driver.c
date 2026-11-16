/* SPDX-License-Identifier: MIT */
/* RM-443 10.12 RGB565 transport. Keeps the stock DMA cache/IRQ/watchdog
 * and completion ownership. The generic video layer owns source buffers and
 * restores ordinary output before unrelated firmware message handling.
 */
#include "driver.h"

#include <stddef.h>

#include "firmware/bindings.h"
#include "firmware/guard.h"
#include "firmware/message.h"
#include "s40/runtime.h"
#include "bindings.h"
#include "layout.h"

#define BARRIER() __asm__ volatile("" ::: "memory")

typedef void (*void_fn)(void);

static volatile unsigned outer_owned, gate_owned, bus_owned, pending, configured;
static volatile int last_result;
static volatile uint32_t completion_ms;
static s40_rm443_video_callback volatile application_callback;
static unsigned periodic_stopped, format_repairs, submit_attempts, first_repair;
static const char* support_reason = "unprobed firmware";

const char* s40_rm443_video_support_reason(void) { return support_reason; }

__attribute__((noinline)) int s40_rm443_video_supported(void) {
    /* Check discovered identity before reading any device-specific address.
     * Other RM-443 firmware and other profiles keep the generic display path.
     * Fingerprints cover 32 bytes at every used ROM entry, including keyboard
     * routing and the stock completion wrapper. No firmware image is shipped.
     */
    support_reason = "unsupported firmware clock binding";
    if (!s40_result || s40_result[FW_API_DESCRIPTOR + API_FW_OS_CURRENT_TIME_READ_IN_MS] !=
                               (RM443_ROM_milliseconds | 1u))
        return 0;
    support_reason = "unsupported display profile";
    if (s40_result[FW_DISPLAY_WIDTH_WORD] != RM443_SCREEN_WIDTH ||
        s40_result[FW_DISPLAY_HEIGHT_WORD] != RM443_SCREEN_HEIGHT ||
        s40_result[FW_TIMER_MESSAGE_LAST_WORD] != RM443_TIMER_MESSAGE_LAST ||
        s40_result[FW_CANVAS_LAYOUT_WORD] != FW_CANVAS_CURRENT)
        return 0;
    static const fw_rom_fingerprint entries[] = {
#define RM443_SIGNATURE(name, address, hash) {address, hash},
            RM443_VIDEO_ROM_ENTRIES(RM443_SIGNATURE)
#undef RM443_SIGNATURE
    };
    for (unsigned i = 0; i < sizeof(entries) / sizeof(*entries); ++i) {
        if (!fw_rom_matches(&entries[i])) {
            support_reason = "ROM fingerprint mismatch";
            return 0;
        }
    }
    support_reason = "none";
    return 1;
}

int s40_rm443_video_foreground(void) {
    uintptr_t context = *RM443_FOREGROUND_SLOT;
    return rm443_ram_object(context, sizeof(rm443_java_foreground)) &&
           RM443_PTR(rm443_java_foreground, context)->state == RM443_JAVA_FOREGROUND;
}

int s40_rm443_video_services_pending(void) {
    /* ComMgr's internal queue is separate from the OS queue counted by
     * fw_msg_count. ProcessInternalQueue reads *task_context as its queue
     * owner; the first word is the circular-list tail (zero when empty).
     * ScanISISubscriptions tests bit0 of the owner's byte60. Inspect only;
     * any actual dispatch must occur after the video lock is released.
     */
    /* The task descriptor is immutable ROM, not a heap object. Its first
     * pointer addresses the mutable queue owner in RAM. Bind the audited Java
     * task explicitly; this adapter is not an arbitrary native-task renderer.
     */
    if (s40_result[FW_RESULT_TASK_ID_WORD] != RM443_JAVA_TASK ||
        *RM443_JAVA_TASK_SLOT != RM443_ROM_java_task_descriptor)
        return -1;
    const volatile rm443_cm_task* task = RM443_PTR(rm443_cm_task, *RM443_JAVA_TASK_SLOT);
    if (task->queue != RM443_JAVA_QUEUE)
        return -1;
    const volatile rm443_cm_queue* queue = RM443_PTR(rm443_cm_queue, task->queue);
    /* A system dialog can block on our scratchpad lock before publishing
     * focus loss or queuing a Java message. Yield as soon as any task waits
     * for that lock. OS resource records have an aligned waiter-list head;
     * UINT32_MAX is empty. Never dereference or modify the waiter itself.
     */
    unsigned resource = RM443_SCRATCHPAD_RESOURCES[RM443_SCRATCHPAD_DISPLAY];
    if (resource >= RM443_RESOURCE_COUNT)
        return -1;
    int display_waiting = RM443_OS_RESOURCES[resource].waiter_head != UINT32_MAX;
    return (queue->tail ? RM443_SERVICE_QUEUE : 0) |
           ((queue->flags & RM443_CM_SUBSCRIPTIONS_DIRTY) ? RM443_SERVICE_SUBSCRIPTIONS : 0) |
           (display_waiting ? RM443_SERVICE_DISPLAY_WAITER : 0);
}

/* Native Lights request. Fields not used by this request remain zero. */
typedef struct {
    uint8_t transaction, message, reserved02, subblocks;
    struct {
        uint8_t id, length, seconds_be[2], mode, enabled, reserved06[3];
        uint8_t brightness, default_period, reserved0b;
    } display;
} rm443_light_request;
typedef char rm443_light_request_size[(sizeof(rm443_light_request) == 16) ? 1 : -1];

int s40_rm443_video_backlight(unsigned seconds, int default_period) {
    if ((!default_period && (!seconds || seconds > RM443_LIGHT_MAX_SECONDS)) ||
        (default_period && seconds))
        return -1;
    /* The already registered Java server owns the reply, so no callbacks or
     * application memory outlive the native session. This is a finite Lights
     * server request, not a persistent brightness/idle-timeout setting.
     */
    if (!fw_isi_object_valid(RM443_JAVA_SERVER->object_id))
        return -1;
    unsigned allocation_size = sizeof(fw_isi_header) + sizeof(rm443_light_request);
    fw_isi_header* packet = fw_os_block_alloc_no_wait(allocation_size);
    if (!packet)
        return -1;
    for (unsigned i = 0; i < allocation_size; ++i)
        ((uint8_t*)packet)[i] = 0;
    fw_isi_set_body_size(packet, sizeof(rm443_light_request));
    rm443_light_request* body = (rm443_light_request*)fw_isi_body(packet);
    body->message = RM443_LIGHT_REQUEST;
    body->subblocks = 1;
    body->display.length = sizeof(body->display);
    body->display.seconds_be[0] = seconds >> 8;
    body->display.seconds_be[1] = seconds;
    body->display.mode = body->display.enabled = 1;
    body->display.brightness = 100;
    body->display.default_period = default_period != 0;
    rm443_cm_send_server(RM443_LIGHT_RESOURCE, (void*)RM443_JAVA_SERVER, body);
    return 0; /* SendToServer consumes the message, including its failure path. */
}

int s40_rm443_video_keyboard(uintptr_t message) {
    const fw_isi_header* packet = (const fw_isi_header*)message;
    if (packet->type == FW_MESSAGE_INTERNAL)
        return 0;
    const uint8_t* body = fw_isi_body_const(packet);
    unsigned target = RM443_JAVA_SERVER->object_id;
    if (packet->resource != RM443_KEYBOARD_RESOURCE ||
        fw_isi_length(packet) < RM443_KEY_MESSAGE_LENGTH || body[1] < RM443_KEY_EVENT_FIRST ||
        body[1] > RM443_KEY_EVENT_LAST || !fw_isi_object_valid(target) ||
        fw_isi_receiver(packet) != target)
        return 0;
    rm443_keyboard_decode(body, (RM443_KEYBOARD_RESOURCE << 8) | body[1]);
    rm443_isi_delete(body);
    return 1;
}

static uint32_t milliseconds(void) { return rm443_milliseconds(); }
static void stop_periodic_refresh(void) {
    /* LCD task message451 normally reapplies COLMOD07 every5s. Cancel its
     * timer while we own an alternate transport. A handler already dequeued
     * can still finish, so each submission also cancels/rechecks under152. */
    rm443_timer_stop(RM443_PERIODIC_TIMER);
    periodic_stopped = 1;
}
static void restart_periodic_refresh(void) {
    if (periodic_stopped) {
        rm443_timer_start(RM443_PERIODIC_TIMER, RM443_PERIODIC_TICKS); /* stock5-second period */
        periodic_stopped = 0;
    }
}
static void irq_lock(void) { rm443_irq_lock(); }
static void irq_unlock(void) { rm443_irq_unlock(); }
static void gate_take(void) {
    if (!gate_owned) {
        rm443_lcd_gate(1);
        gate_owned = 1;
    }
}
static void gate_give(void) {
    rm443_lcd_gate(0);
    gate_owned = 0;
}
/* Only release outer ownership after the bus and driver gate are recovered.
 * Failed format restoration keeps this ownership until a later retry. */
static void release_scratchpad(void) {
    rm443_scratchpad_give(RM443_SCRATCHPAD_DISPLAY);
    outer_owned = 0;
}

/* Waiting here concerns command readiness after prior callback completion,
 * never a substitute for DMA ownership/completion. */
static int command_ready(uint32_t timeout) {
    uint32_t start = milliseconds();
    for (unsigned i = 0; i < RM443_POLL_LIMIT; i++) {
        if (*RM443_LCD_CONTROL & RM443_COMMAND_READY)
            return 1;
        if ((uint32_t)(milliseconds() - start) >= timeout)
            return 0;
    }
    return 0;
}
static int settle_format(void) {
    /* Sharp family documents COLMOD taking effect at V-sync.40ms exceeds
     * two observed17–18ms TE intervals; configuration-only, never timed frames. */
    uint32_t start = milliseconds();
    for (unsigned i = 0; i < RM443_POLL_LIMIT; i++)
        if ((uint32_t)(milliseconds() - start) >= RM443_FORMAT_SETTLE_MS)
            return 1;
    return 0;
}

/* Mirror F0253928 bus activation; matching cleanup is F025385C. */
static void bus_enable(void) {
    irq_lock();
    *RM443_LCD_CONTROL &= ~RM443_BUS_IDLE;
    irq_unlock();
    rm443_clock_enable(RM443_BUS_CLOCK, 1, 0);
    rm443_clock_reset(RM443_BUS_CLOCK, 1, 0);
    RM443_GPIO->bus_config28 = 0xffff;
    RM443_GPIO->bus_config24 = 0x5555;
    for (unsigned pin = RM443_DATA_PIN_FIRST; pin <= RM443_DATA_PIN_LAST; pin++)
        rm443_gpio_pin_mode(pin, RM443_DATA_PIN_ACTIVE);
    bus_owned = 1;
}
static int bus_disable(void) {
    if (!command_ready(RM443_COMMAND_TIMEOUT_MS))
        return RM443_VIDEO_RETAINED;
    rm443_clock_enable(RM443_BUS_CLOCK, 0, 0);
    rm443_clock_reset(RM443_BUS_CLOCK, 0, 0);
    irq_lock();
    *RM443_LCD_CONTROL |= RM443_BUS_IDLE;
    irq_unlock();
    RM443_GPIO->bus_config28 = 0xffff;
    RM443_GPIO->bus_config24 = 0;
    for (unsigned pin = RM443_DATA_PIN_FIRST; pin <= RM443_DATA_PIN_LAST; pin++)
        rm443_gpio_pin_mode(pin, RM443_DATA_PIN_IDLE);
    bus_owned = 0;
    return RM443_VIDEO_OK;
}
static void command(unsigned value) { rm443_lcd_command(value); }
static void bytes(const void* value, unsigned count) { rm443_lcd_write_bytes(value, count); }
static uint8_t read_colmod(void) {
    uint8_t result = 0;
    command(RM443_LCD_READ_FORMAT);
    rm443_lcd_read_byte(&result); /* stock dummy+one-byte reader */
    return result;
}
static void set_colmod(unsigned format) {
    /* Stock byte writer reads four source bytes even for a one-byte transfer. */
    uint32_t padded = format;
    command(RM443_LCD_SET_FORMAT);
    bytes(&padded, 1);
}
static int platform_ok(void) {
    if (!s40_rm443_video_supported())
        return 0;
    /* Function entry bytes are already covered by the full ROM registry.
     * Live checks below guard operating mode, the panel identity and power.
     * The three zero guards are verified preconditions; their wider semantics
     * are deliberately not exposed as device controls. */
    return RM443_DISPLAY_HOOKS->update_guard == 0 && RM443_LCD_PENDING->update_guard == 0 &&
           RM443_LCD->panel_id[0] == 0x83 && RM443_LCD->panel_id[1] == 0x94 &&
           RM443_LCD->panel_id[2] == 0x41 && RM443_LCD->panel_revision == 1 &&
           RM443_LCD->powered != 0 && RM443_LCD->mode_guard == 0;
}

/* Invoked by F0254170 after stock DMA/clock/pin cleanup and driver gate release. */
static void completed(int ignored) {
    (void)ignored;
    completion_ms = milliseconds();
    bus_owned = 0;
    gate_owned = 0;
    s40_rm443_video_callback callback = application_callback;
    application_callback = NULL;
    /* Do not publish no-pending until callback has stopped using its context. */
    if (callback)
        callback(last_result);
    BARRIER();
    pending = 0;
}
static void timed_out(void) {
    last_result = RM443_VIDEO_TRANSFER;
    rm443_lcd_timeout(); /* stock timeout stop/cleanup/log/callback */
}

int s40_rm443_video_init(uint8_t* readback) {
    if (outer_owned || pending)
        return RM443_VIDEO_STATE;
    if (!platform_ok())
        return RM443_VIDEO_PLATFORM;
    rm443_scratchpad_take(RM443_SCRATCHPAD_DISPLAY);
    outer_owned = 1;
    gate_take();
    /* Acquiring the stock gate drains any preceding normal update. */
    if (RM443_LCD_PENDING->pending_updates != 0 || RM443_LCD->watchdog_armed != 0 ||
        RM443_LCD->dma_armed != 0) {
        gate_give();
        release_scratchpad();
        return RM443_VIDEO_STATE;
    }
    format_repairs = submit_attempts = first_repair = 0;
    /* Timer259 is one-shot and rearmed only after LCD control returns. With
     * resource152 held, active/queued means there is no already-dequeued
     * control handler waiting behind us. A stopped state is ambiguous: abort
     * without changing COLMOD instead of racing an outstanding refresh. */
    irq_lock();
    unsigned timer_state = *RM443_PERIODIC_TIMER_STATE;
    if (timer_state != RM443_TIMER_ACTIVE && timer_state != RM443_TIMER_QUEUED) {
        irq_unlock();
        gate_give();
        release_scratchpad();
        return RM443_VIDEO_STATE;
    }
    stop_periodic_refresh();
    unsigned stopped = *RM443_PERIODIC_TIMER_STATE == RM443_TIMER_STOPPED;
    irq_unlock();
    if (!stopped) {
        restart_periodic_refresh();
        gate_give();
        release_scratchpad();
        return RM443_VIDEO_STATE;
    }
    bus_enable();
    uint8_t original = read_colmod();
    if (readback)
        *readback = original;
    if ((original & RM443_FORMAT_MASK) != RM443_FORMAT_NORMAL) {
        int cleanup = bus_disable();
        if (cleanup)
            return cleanup;
        gate_give();
        restart_periodic_refresh();
        release_scratchpad();
        return RM443_VIDEO_FORMAT;
    }
    struct {
        uint8_t red[64], green[64], blue[64];
    } lut;
    for (unsigned i = 0; i < sizeof(lut.red); i++) {
        lut.red[i] = lut.blue[i] = (uint8_t)(i < 32 ? ((i << 3) | (i >> 2)) : 255);
        lut.green[i] = (uint8_t)((i << 2) | (i >> 4));
    }
    command(RM443_LCD_SET_LUT);
    bytes(&lut, sizeof(lut));
    set_colmod(RM443_FORMAT_RGB565);
    if (!settle_format())
        return RM443_VIDEO_RETAINED;
    uint8_t actual = read_colmod();
    if (readback)
        *readback = actual;
    int cleanup = bus_disable();
    if (cleanup)
        return cleanup;
    gate_give();
    configured = (actual & RM443_FORMAT_MASK) == RM443_FORMAT_RGB565;
    last_result = configured ? RM443_VIDEO_OK : RM443_VIDEO_FORMAT;
    completion_ms = 0;
    /* On format mismatch retain outer ownership so caller restores07. */
    return last_result;
}

static int wait_te(uint32_t* edge_ms) {
    uint32_t start = milliseconds();
    unsigned saw_low = 0;
    for (unsigned i = 0; i < RM443_POLL_LIMIT; i++) {
        unsigned high = RM443_GPIO->input & (1u << RM443_TE_PIN);
        uint32_t now = milliseconds();
        if ((uint32_t)(now - start) >= RM443_TE_TIMEOUT_MS)
            return RM443_VIDEO_TE_TIMEOUT;
        if (!high)
            saw_low = 1;
        else if (saw_low) {
            *edge_ms = now;
            return RM443_VIDEO_OK;
        }
    }
    return RM443_VIDEO_TE_TIMEOUT;
}

static int submit(const void* source, s40_rm443_video_callback callback, uint32_t* te_ms) {
    if (!outer_owned || !configured || pending || gate_owned || bus_owned)
        return RM443_VIDEO_STATE;
    if (!source || ((uintptr_t)source & 3u))
        return RM443_VIDEO_STATE;
    if (last_result)
        return last_result;
    gate_take();
    stop_periodic_refresh();
    void_fn before_update = (void_fn)(uintptr_t)RM443_DISPLAY_HOOKS->before_update;
    if (before_update)
        before_update(); /* same hook as public hw_update */
    bus_enable();
    ++submit_attempts;
    if ((read_colmod() & RM443_FORMAT_MASK) != RM443_FORMAT_RGB565) {
        if (!format_repairs)
            first_repair = submit_attempts;
        format_repairs++;
        set_colmod(RM443_FORMAT_RGB565);
        if (!settle_format())
            return RM443_VIDEO_RETAINED;
        if ((read_colmod() & RM443_FORMAT_MASK) != RM443_FORMAT_RGB565) {
            if (bus_disable() != RM443_VIDEO_OK)
                return RM443_VIDEO_RETAINED;
            gate_give();
            return RM443_VIDEO_FORMAT;
        }
    }
    if (te_ms) {
        *te_ms = 0;
        int sync_result = wait_te(te_ms);
        if (sync_result) {
            if (bus_disable() != RM443_VIDEO_OK)
                return RM443_VIDEO_RETAINED;
            gate_give();
            return sync_result;
        }
    }
    application_callback = callback;
    completion_ms = 0;
    last_result = RM443_VIDEO_OK;
    ++RM443_LCD_PENDING->pending_updates; /* exact32-bit increment in F0254246 */
    rm443_lcd_set_completion(0, completed);
    rm443_lcd_window(0, RM443_SCREEN_HEIGHT - 1, 0, RM443_SCREEN_WIDTH - 1);
    rm443_lcd_arm(RM443_FRAME_WORDS, 0, NULL);
    RM443_LCD->completion =
            (RM443_ROM_lcd_public_complete | 1u); /* ordinary public update completion wrapper */
    irq_lock();
    pending = 1;
    BARRIER();
    unsigned channel = rm443_dma_start(source, RM443_DMA_REQUEST, RM443_FRAME_WORDS,
                                       (void*)(uintptr_t)(RM443_ROM_lcd_dma_complete | 1u));
    RM443_LCD->dma_channel = (uint8_t)channel;
    if (channel >= RM443_DMA_CHANNEL_COUNT) {
        /* No DMA owns source. Stock abort resets the armed LCD transaction.
         * If readiness still fails, retain all state rather than fabricating
         * completion or releasing buffers which might still be referenced. */
        last_result = RM443_VIDEO_NO_DMA;
        irq_unlock();
        rm443_lcd_abort();
        if (bus_disable() != RM443_VIDEO_OK)
            return RM443_VIDEO_RETAINED;
        rm443_lcd_public_complete(); /* pending--,gate release,completed() */
        return RM443_VIDEO_NO_DMA;
    }
    RM443_LCD->dma_armed = 1;
    unsigned timer = rm443_watchdog_start(timed_out, 0, RM443_WATCHDOG_US);
    RM443_LCD->watchdog_timer = (uint8_t)timer;
    RM443_LCD->watchdog_armed = 1;
    irq_unlock();
    return RM443_VIDEO_OK;
}

int s40_rm443_video_submit(const void* source, s40_rm443_video_callback callback) {
    return submit(source, callback, NULL);
}
int s40_rm443_video_submit_synced(const void* source, s40_rm443_video_callback callback,
                                  uint32_t* te_ms) {
    if (!te_ms)
        return RM443_VIDEO_STATE;
    return submit(source, callback, te_ms);
}

int s40_rm443_video_drain(uint32_t timeout_ms) {
    uint32_t start = milliseconds();
    for (unsigned i = 0; i < RM443_POLL_LIMIT; i++) {
        if (!pending)
            return last_result;
        if ((uint32_t)(milliseconds() - start) >= timeout_ms)
            return RM443_VIDEO_RETAINED;
    }
    return pending ? RM443_VIDEO_RETAINED : last_result;
}

int s40_rm443_video_read_pixel(unsigned x, unsigned y, uint32_t* rgb) {
    if (!outer_owned || !configured || pending || gate_owned || bus_owned ||
        x >= RM443_SCREEN_WIDTH || y >= RM443_SCREEN_HEIGHT || !rgb)
        return RM443_VIDEO_STATE;
    if (last_result)
        return last_result;
    gate_take();
    bus_enable();
    /* Existing window setter sends 2B/2A then 2C, but writes no pixels.
     * RAMRD resets to that window's first coordinate. Stock read4 performs
     * five FIFO reads: dummy, R, G, B, then the next/wrapped R. */
    rm443_lcd_window(y, y, x, x);
    command(RM443_LCD_READ_PIXEL);
    uint32_t value = 0;
    rm443_lcd_read_word(&value);
    *rgb = value >> 8;
    int cleanup = bus_disable();
    if (cleanup)
        return cleanup;
    gate_give();
    return RM443_VIDEO_OK;
}

int s40_rm443_video_read_format(uint8_t* format) {
    if (!outer_owned || !configured || pending || gate_owned || bus_owned || !format)
        return RM443_VIDEO_STATE;
    gate_take();
    bus_enable();
    *format = read_colmod();
    int cleanup = bus_disable();
    if (cleanup)
        return cleanup;
    gate_give();
    return RM443_VIDEO_OK;
}

int s40_rm443_video_restore(uint32_t timeout_ms, uint8_t* readback) {
    if (!outer_owned)
        return RM443_VIDEO_STATE;
    (void)s40_rm443_video_drain(timeout_ms);
    if (pending)
        return RM443_VIDEO_RETAINED;
    gate_take();
    if (!bus_owned)
        bus_enable();
    set_colmod(RM443_FORMAT_NORMAL);
    if (!settle_format())
        return RM443_VIDEO_RETAINED;
    uint8_t actual = read_colmod();
    if (readback)
        *readback = actual;
    int cleanup = bus_disable();
    if (cleanup)
        return cleanup;
    gate_give();
    if ((actual & RM443_FORMAT_MASK) != RM443_FORMAT_NORMAL)
        return RM443_VIDEO_FORMAT; /* retain outer lock */
    restart_periodic_refresh();
    release_scratchpad();
    configured = 0;
    last_result = RM443_VIDEO_OK;
    return RM443_VIDEO_OK;
}
int s40_rm443_video_active(void) { return outer_owned != 0; }
int s40_rm443_video_in_flight(void) { return pending != 0; }
uint32_t s40_rm443_video_completed_ms(void) { return completion_ms; }
int s40_rm443_video_last_result(void) { return last_result; }
unsigned s40_rm443_video_format_repairs(void) { return format_repairs; }
unsigned s40_rm443_video_first_repair(void) { return first_repair; }
