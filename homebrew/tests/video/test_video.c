/* SPDX-License-Identifier: MIT */
/* Exercise production scaling, buffer ownership, fallback and dispatch. */
#include <assert.h>
#include <setjmp.h>
#include <stdlib.h>
#include <string.h>

#include "s40/runtime.h"
#include "s40/video.h"
#include "display/video_internal.h"
#include "display/rm443_1012/driver.h"

static uint32_t result[1200];
uint32_t* s40_result = result;
static uint32_t ordinary[320 * 320 + 2];
static uint16_t transferred[240 * 320];
static const uint16_t* pending_source;
static unsigned sw = 240, sh = 320, support, active, pending, fail_init, fail_submit;
static unsigned ordinary_count, fast_count, restored, keyboards;
static unsigned audio_messages;
static unsigned yields;
static int services_pending;
static int foreground = 1, lose_focus_on_init, finish_expected;
static jmp_buf finish_env;
uint32_t s40_uptime_ms(void) {
    static uint32_t ms;
    return ms++;
}

unsigned s40_display_width(void) { return sw; }
unsigned s40_display_height(void) { return sh; }
uint32_t* s40_display_open(void) {
    assert(!active);
    return ordinary + 1;
}
void s40_display_present(void) {
    assert(!active);
    ++ordinary_count;
}
void s40_display_yield(void) {
    assert(!active && !pending);
    ++yields;
}
void s40_finish(int status) {
    assert(finish_expected && status == 0);
    longjmp(finish_env, 1);
}
int s40_rm443_video_supported(void) { return support; }
int s40_rm443_video_services_pending(void) { return services_pending; }
const char* s40_rm443_video_support_reason(void) { return "unsupported fixture"; }
int s40_rm443_video_foreground(void) { return foreground; }
int s40_rm443_video_active(void) { return active; }
int s40_rm443_video_in_flight(void) { return pending; }
int s40_rm443_video_init(uint8_t* out) {
    (void)out;
    assert(!active);
    active = 1;
    if (lose_focus_on_init)
        foreground = 0;
    return fail_init ? -3 : 0;
}
int s40_rm443_video_drain(uint32_t ms) {
    (void)ms;
    if (pending)
        assert(!memcmp(pending_source, transferred, sw * sh * 2));
    pending = 0;
    return 0;
}
int s40_rm443_video_restore(uint32_t ms, uint8_t* out) {
    (void)out;
    assert(active);
    s40_rm443_video_drain(ms);
    active = 0;
    ++restored;
    return 0;
}
int s40_rm443_video_submit_synced(const void* pixels, s40_rm443_video_callback callback,
                                  uint32_t* edge) {
    assert(active && !pending && callback == NULL && edge != NULL);
    if (fail_submit)
        return -7;
    pending_source = pixels;
    memcpy(transferred, pixels, sw * sh * 2);
    pending = 1;
    ++fast_count;
    return 0;
}
int s40_rm443_video_keyboard(uintptr_t message) {
    assert(active && !pending);
    if (message != 0x1234)
        return 0;
    ++keyboards;
    return 1;
}
int s40_audio_dispatch_message(uintptr_t message) {
    if (message != 0x2345)
        return 0;
    assert(active && pending);
    ++audio_messages;
    return 1;
}

int main(void) {
    result[FW_TIMER_MESSAGE_LAST_WORD] = 488;
    result[FW_IGNORED_TIMER_WORD] = 127;
    ordinary[0] = ordinary[320 * 320 + 1] = 0xfeedbeef;
    assert(s40_video_present() == -1);
    assert(!s40_video_open(0, 144) && !s40_video_open(321, 144));
    sw = sh = 4;
    uint16_t* pixels = s40_video_open(2, 1);
    assert(pixels && s40_video_open(2, 1) == pixels);
    pixels[0] = 0xf800;
    pixels[1] = 0x07e0;
    assert(s40_video_present() == 0 && ordinary_count == 1);
    /* 2x1 fits as 4x2, centered vertically. */
    for (unsigned i = 0; i < 16; ++i) {
        uint32_t expected = i < 4 || i >= 12 ? 0xff000000 : (i % 4 < 2 ? 0xffff0000 : 0xff00ff00);
        assert(ordinary[i + 1] == expected);
    }
    s40_video_set_scale(S40_VIDEO_NATIVE);
    assert(s40_video_present() == 0);
    for (unsigned i = 0; i < 16; ++i)
        assert(ordinary[i + 1] == (i == 5 ? 0xffff0000 : i == 6 ? 0xff00ff00 : 0xff000000));
    s40_video_close();

    /* Actual GB dimensions: exact FIT 240x216 and NATIVE160x144 placement. */
    sw = 240;
    sh = 320;
    support = 1;
    s40_video_set_scale(S40_VIDEO_FIT);
    pixels = s40_video_open(160, 144);
    assert(pixels);
    for (unsigned y = 0; y < 144; ++y)
        for (unsigned x = 0; x < 160; ++x)
            pixels[y * 160 + x] = (uint16_t)(y * 173 + x * 47);
    for (unsigned frame = 0; frame < 3; ++frame) {
        pixels[0] = (uint16_t)(0x0123 + frame);
        assert(s40_video_present() == 0);
        for (unsigned y = 0; y < 320; ++y)
            for (unsigned x = 0; x < 240; ++x) {
                unsigned p = y < 52 || y >= 268
                                     ? 0
                                     : pixels[((y - 52) * 144 / 216) * 160 + x * 160 / 240];
                assert(transferred[y * 240 + x] == (uint16_t)((p >> 8) | (p << 8)));
            }
    }
    assert(fast_count == 3 && pending);
    assert(yields == 1 && !strcmp(s40_video_fallback_reason(), "none"));
    assert(s40_video_prepare_services() == 0 && pending && active);
    assert(s40_video_dispatch_message(319) == 1 && pending); /* ignored timer */
    assert(s40_video_dispatch_message(0x2345) == 1 && pending && active && audio_messages == 1);
    assert(s40_video_dispatch_message(0x1234) == 1 && active && !pending && keyboards == 1);
    assert(s40_video_present() == 0);
    assert(s40_video_dispatch_message(450) == 0 && !active && !pending && restored == 1);
    assert(s40_video_open(160, 144) == pixels);
    assert(s40_video_present() == 0);
    /* Unowned audio/phone traffic must still restore normal firmware output. */
    assert(s40_video_dispatch_message(0x2346) == 0 && !active && !pending && restored == 2);
    assert(s40_video_present() == 0 && active);
    services_pending = 1;
    assert(s40_video_prepare_services() == 1 && !active && !pending);
    services_pending = 0;
    assert(s40_video_present() == 0 && active);
    /* A system dialog can be waiting before it changes the focus event. */
    services_pending = 4;
    assert(foreground && s40_video_prepare_services() == 1 && !active && !pending);
    services_pending = 0;
    assert(s40_video_present() == 0 && active);
    s40_video_stats stats;
    s40_video_get_stats(&stats);
    assert(stats.fast_key_messages == 1 && stats.fast_audio_messages == 1);
    assert(stats.service_yields == 2 && stats.message_yields == 2);
    assert(stats.suspend_ms == 4 && stats.longest_suspend_ms == 1);
    s40_video_set_scale(S40_VIDEO_NATIVE);
    assert(!active && !pending);
    assert(s40_video_present() == 0);
    for (unsigned y = 0; y < 320; ++y)
        for (unsigned x = 0; x < 240; ++x) {
            unsigned p =
                    y < 88 || y >= 232 || x < 40 || x >= 200 ? 0 : pixels[(y - 88) * 160 + x - 40];
            assert(transferred[y * 240 + x] == (uint16_t)((p >> 8) | (p << 8)));
        }
    fail_submit = 1;
    unsigned before = ordinary_count;
    assert(s40_video_present() == 0 && !active && !pending && ordinary_count == before + 1);
    assert(!strcmp(s40_video_backend(), "ARGB8888"));
    assert(!strcmp(s40_video_fallback_reason(), "RM443 submit error -7"));
    s40_video_close();
    fail_submit = 0;
    fail_init = 1;
    pixels = s40_video_open(160, 144);
    assert(pixels && s40_video_present() == 0 && !active && !pending);
    assert(!strcmp(s40_video_fallback_reason(), "RM443 init error -3"));
    s40_video_close();
    fail_init = 0;
    pixels = s40_video_open(160, 144);
    assert(pixels && s40_video_present() == 0 && active);
    services_pending = -1;
    assert(s40_video_prepare_services() == 1 && !active && !pending);
    assert(!strcmp(s40_video_backend(), "ARGB8888"));
    assert(!strcmp(s40_video_fallback_reason(), "unsupported ComMgr task context"));
    unsigned prior_fast = fast_count;
    assert(s40_video_present() == 0 && fast_count == prior_fast && !active);
    s40_video_close();
    assert(s40_video_present() == -1);
    /* Foreground may change while a resumed presenter waits for the lock. */
    services_pending = 0;
    lose_focus_on_init = finish_expected = 1;
    assert(s40_video_open(160, 144));
    if (!setjmp(finish_env)) {
        s40_video_present();
        abort();
    }
    assert(active && !pending);
    s40_video_close();
    finish_expected = lose_focus_on_init = 0;
    foreground = 1;
    assert(ordinary[0] == 0xfeedbeef && ordinary[320 * 320 + 1] == 0xfeedbeef);
    return 0;
}
