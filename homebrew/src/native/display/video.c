/* SPDX-License-Identifier: MIT */
#include "s40/video.h"

#include <stdlib.h>
#include <string.h>

#include "audio/internal.h"
#include "internal.h"
#include "firmware/message.h"
#include "s40/runtime.h"
#include "video_internal.h"
#include "display/rm443/driver.h"

static uint16_t *source, *packed[2];
static unsigned source_width, source_height, screen_width, screen_height;
static unsigned scale, left, top, width, height, next_buffer, fast_enabled;
static uint16_t columns[S40_DISPLAY_MAX_DIMENSION];
static unsigned rows[S40_DISPLAY_MAX_DIMENSION];
static const char* fallback_reason = "video not open";
static char failure_detail[64];
static s40_video_stats statistics;
enum { VIDEO_DRAIN_MS = 100 };

static void transport_failed(const char* operation, int result) {
    snprintf(failure_detail, sizeof(failure_detail), "RM443 %s error %d", operation, result);
    fallback_reason = failure_detail;
    fast_enabled = 0;
}

static void geometry(void) {
    width = source_width;
    height = source_height;
    if (scale != S40_VIDEO_NATIVE || width > screen_width || height > screen_height) {
        if (source_width * screen_height > source_height * screen_width) {
            width = screen_width;
            height = source_height * width / source_width;
        } else {
            height = screen_height;
            width = source_width * height / source_height;
        }
    }
    if (!width)
        width = 1;
    if (!height)
        height = 1;
    left = (screen_width - width) / 2;
    top = (screen_height - height) / 2;
    for (unsigned x = 0; x < width; ++x)
        columns[x] = x * source_width / width;
    for (unsigned y = 0; y < height; ++y)
        rows[y] = (y * source_height / height) * source_width;
}

int s40_video_transport_active(void) { return s40_rm443_video_active(); }

void s40_video_suspend(void) {
    /* An uncertain DMA or failed format restore must never return to Java or
     * release callback/source memory. IRQs remain enabled during each bounded
     * drain/restore attempt, allowing the stock watchdog to complete cleanup.
     */
    if (!s40_rm443_video_active())
        return;
    uint32_t start = s40_uptime_ms();
    while (s40_rm443_video_active())
        (void)s40_rm443_video_restore(VIDEO_DRAIN_MS, NULL);
    uint32_t elapsed = s40_uptime_ms() - start;
    statistics.suspend_ms += elapsed;
    if (elapsed > statistics.longest_suspend_ms)
        statistics.longest_suspend_ms = elapsed;
}

void s40_video_close(void) {
    s40_video_suspend();
    free(source);
    free(packed[0]);
    free(packed[1]);
    source = packed[0] = packed[1] = NULL;
    source_width = source_height = fast_enabled = next_buffer = 0;
    fallback_reason = "video not open";
}

uint16_t* s40_video_open(unsigned w, unsigned h) {
    if (!w || !h || w > S40_DISPLAY_MAX_DIMENSION || h > S40_DISPLAY_MAX_DIMENSION)
        return NULL;
    if (source && source_width == w && source_height == h)
        return source;
    s40_video_close();
    screen_width = s40_display_width();
    screen_height = s40_display_height();
    if (!screen_width || !screen_height || screen_width > S40_DISPLAY_MAX_DIMENSION ||
        screen_height > S40_DISPLAY_MAX_DIMENSION)
        return NULL;
    source = calloc(w * h, sizeof(*source));
    if (!source)
        return NULL;
    source_width = w;
    source_height = h;
    geometry();
    if (s40_rm443_video_supported()) {
        packed[0] = calloc(screen_width * screen_height, sizeof(**packed));
        packed[1] = calloc(screen_width * screen_height, sizeof(**packed));
        if (packed[0] && packed[1]) {
            fast_enabled = 1;
            fallback_reason = "none";
        } else {
            free(packed[0]);
            free(packed[1]);
            packed[0] = packed[1] = NULL;
            fallback_reason = "presentation buffer allocation failed";
        }
    } else {
        fallback_reason = s40_rm443_video_support_reason();
    }
    return source;
}

void s40_video_set_scale(unsigned mode) {
    mode = mode == S40_VIDEO_NATIVE ? S40_VIDEO_NATIVE : S40_VIDEO_FIT;
    if (scale == mode)
        return;
    s40_video_suspend();
    scale = mode;
    if (source) {
        geometry();
        for (unsigned i = 0; i < 2; ++i)
            if (packed[i])
                memset(packed[i], 0, screen_width * screen_height * sizeof(**packed));
    }
}

static void pack_frame(uint16_t* output) {
    for (unsigned y = 0; y < height; ++y) {
        uint16_t* line = output + (top + y) * screen_width + left;
        const uint16_t* input = source + rows[y];
        for (unsigned x = 0; x < width; ++x) {
            unsigned p = input[columns[x]];
            /* ARMv5 little-endian storage to high-byte-first LCD wire order. */
            line[x] = (p >> 8) | (p << 8);
        }
    }
}

static void ordinary_frame(void) {
    uint32_t* output = s40_display_open();
    for (unsigned i = 0; i < screen_width * screen_height; ++i)
        output[i] = S40_ARGB_BLACK;
    for (unsigned y = 0; y < height; ++y) {
        uint32_t* line = output + (top + y) * screen_width + left;
        const uint16_t* input = source + rows[y];
        for (unsigned x = 0; x < width; ++x) {
            line[x] = s40_rgb565_to_argb(input[columns[x]]);
        }
    }
    s40_display_present();
}

int s40_video_present(void) {
    if (!source)
        return -1;
    ++statistics.presents;
    if (fast_enabled) {
        if (!s40_rm443_video_foreground())
            s40_finish(0);
        /* Pack into the free buffer while the previous DMA is still running. */
        pack_frame(packed[next_buffer]);
        int result = s40_rm443_video_drain(VIDEO_DRAIN_MS);
        const char* operation = "drain";
        if (!result && !s40_rm443_video_active()) {
            /* Ordinary menus can own master display even after they stop
             * drawing. Release that reservation before taking the transport's
             * scratchpad lock; retain menu pixels for later redraw.
             */
            s40_display_yield();
            operation = "init";
            result = s40_rm443_video_init(NULL);
            /* A waiting system dialog may take focus while lock acquisition
             * blocks. Recheck before drawing over the new foreground UI.
             */
            if (!s40_rm443_video_foreground())
                s40_finish(0);
        }
        if (!result) {
            uint32_t edge;
            operation = "submit";
            result = s40_rm443_video_submit_synced(packed[next_buffer], NULL, &edge);
        }
        if (!result) {
            next_buffer ^= 1;
            return 0;
        }
        s40_video_suspend();
        transport_failed(operation, result);
    }
    ordinary_frame();
    return 0;
}

const char* s40_video_backend(void) { return fast_enabled ? "RGB565 TE" : "ARGB8888"; }
const char* s40_video_fallback_reason(void) { return fallback_reason; }
void s40_video_get_stats(s40_video_stats* out) {
    if (out)
        *out = statistics;
}

int s40_video_wait(void) {
    if (!s40_rm443_video_active())
        return 0;
    int result;
    do {
        result = s40_rm443_video_drain(VIDEO_DRAIN_MS);
    } while (s40_rm443_video_in_flight());
    return result;
}

int s40_video_dispatch_message(uintptr_t message) {
    if (!s40_rm443_video_active())
        return 0;
    int timer = fw_message_is_timer(message, s40_result[FW_TIMER_MESSAGE_LAST_WORD]);
    if (timer && fw_message_timer_id(message) == s40_result[FW_IGNORED_TIMER_WORD])
        return 1;
    /* Owned PCM replies only update the stream's scalar state and release
     * their message. They neither enter GDI nor touch presentation buffers,
     * so the preceding frame can continue transferring during delivery.
     */
    if (!timer && s40_audio_dispatch_message(message)) {
        ++statistics.fast_audio_messages;
        return 1;
    }
    int result = s40_video_wait();
    if (!result && !timer && s40_rm443_video_keyboard(message)) {
        ++statistics.fast_key_messages;
        return 1;
    }
    if (result) {
        transport_failed("dispatch drain", result);
    }
    ++statistics.message_yields;
    s40_video_suspend();
    return 0;
}

int s40_video_prepare_services(void) {
    if (!s40_rm443_video_active())
        return 1;
    int pending = s40_rm443_video_services_pending();
    if (!pending)
        return 0;
    if (pending < 0) {
        fast_enabled = 0;
        fallback_reason = "unsupported ComMgr task context";
    }
    ++statistics.service_yields;
    s40_video_suspend();
    return 1;
}
