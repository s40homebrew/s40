/* SPDX-License-Identifier: MIT */
#include <assert.h>
#include <stdint.h>

#include "s40/runtime.h"
#include "display/video_internal.h"

static uint32_t now;
static unsigned supported, foreground = 1, renewals, defaults;
uint32_t s40_uptime_ms(void) { return now; }
int s40_rm443_video_supported(void) { return supported; }
int s40_rm443_video_foreground(void) { return foreground; }
int s40_rm443_video_backlight(unsigned seconds, int normal) {
    if (normal) {
        assert(seconds == 0);
        ++defaults;
    } else {
        assert(seconds == 30);
        ++renewals;
    }
    return 0;
}

int main(void) {
    assert(s40_display_keep_awake(1) == -1);
    s40_display_service_awake();
    assert(!renewals && !defaults);
    supported = 1;
    now = 0xfffff000u;
    assert(s40_display_keep_awake(1) == 0 && renewals == 1);
    now += 9999;
    s40_display_service_awake();
    assert(renewals == 1);
    ++now;
    s40_display_service_awake();
    assert(renewals == 2);
    now += 10000;
    s40_display_service_awake();
    assert(renewals == 3);
    foreground = 0;
    now += 10000;
    s40_display_service_awake();
    assert(defaults == 1 && renewals == 3);
    now += 60000;
    s40_display_service_awake();
    assert(defaults == 1 && renewals == 3);
    assert(s40_display_keep_awake(0) == 0 && defaults == 1);
    foreground = 1;
    assert(s40_display_keep_awake(1) == 0 && renewals == 4);
    assert(s40_display_keep_awake(0) == 0 && defaults == 2);
    return 0;
}
