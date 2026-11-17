/* SPDX-License-Identifier: MIT */
#include "s40/runtime.h"
#include "core/time.h"
#include "video_internal.h"
#include "display/rm443/driver.h"

static unsigned enabled;
static uint32_t next_refresh;
enum { LIGHT_LEASE_SECONDS = 30, LIGHT_RENEW_MS = 10000 };

int s40_display_keep_awake(int wanted) {
    if (!wanted) {
        if (!enabled)
            return 0;
        enabled = 0;
        return s40_rm443_video_backlight(0, 1);
    }
    if (!s40_rm443_video_supported() || !s40_rm443_video_foreground())
        return -1;
    int result = s40_rm443_video_backlight(LIGHT_LEASE_SECONDS, 0);
    enabled = result == 0;
    next_refresh = s40_uptime_ms() + LIGHT_RENEW_MS;
    return result;
}

void s40_display_service_awake(void) {
    if (!enabled)
        return;
    uint32_t now = s40_uptime_ms();
    if (s40_time_until(next_refresh, now))
        return;
    if (!s40_rm443_video_foreground()) {
        (void)s40_display_keep_awake(0);
        return;
    }
    (void)s40_rm443_video_backlight(LIGHT_LEASE_SECONDS, 0);
    next_refresh = now + LIGHT_RENEW_MS;
}
