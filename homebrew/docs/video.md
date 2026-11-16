# Video output

Use [s40/video.h](../include/s40/video.h) for an emulator or game with its own
framebuffer. It accepts packed, host-endian RGB565 pixels and handles scaling,
centering and presentation. For full-screen menus and simple drawing, use the
ordinary ARGB API in [s40/display.h](../include/s40/display.h).

```c
#include "s40/runtime.h"
#include "s40/video.h"

int s40_app_main(void) {
    uint16_t* pixels = s40_video_open(160, 144);
    if (!pixels)
        s40_error("Cannot allocate video");
    for (unsigned i = 0; i < 160 * 144; ++i)
        pixels[i] = 0x07e0;
    s40_video_present();
    s40_sleep_ms(1000);
    return 0;
}
```

The runtime owns the returned buffer. It remains valid until close, a change of
source dimensions or session exit. Reopening with the same dimensions preserves
its contents. The API header defines the supported dimensions and return values.

## Scaling and menus

The default `S40_VIDEO_FIT` preserves aspect ratio with nearest-neighbour scaling
and centers the image against black. A 160×144 source becomes 240×216 on a
240×320 screen. `s40_video_set_scale(S40_VIDEO_NATIVE)` selects centered 1:1 pixels
when they fit; oversized sources still scale down. Changing scale preserves the
source pointer.

Call `s40_video_suspend()` before drawing a menu with the ordinary display API.
This retains the game framebuffer; the next `s40_video_present()` resumes video.
Opening or presenting the ordinary display also suspends video automatically.

`s40_display_keep_awake(1)` requests foreground illumination where supported.
Keep pumping messages to renew the request. Disabling it or ending the session
restores the phone's normal lighting behavior. It does not change a stored setting.
See [device compatibility](devices.md) for optional display features.

## Timing and diagnostics

After `s40_video_present()` returns, the application may reuse its source pixels.
Presentation can wait for an earlier transfer or the panel's next refresh.
Use `s40_video_wait()` when a measurement must include completion of the last
transfer. Close and session cleanup also finish outstanding display work.

Emulators should advance their guest clock independently of presentation. A
phone panel can refresh more slowly than the emulated system; skip presentations
when needed to preserve gameplay speed. The [frame clock](../include/s40/frame_clock.h)
helps maintain that timing without choosing a rendering policy for the app.

Keep calling `s40_pump()` and consuming events throughout rendering. The runtime
yields display ownership when phone services need it; focus loss ends the session.
Optional fast drivers fall back to ordinary output when unavailable.

`s40_video_backend()` and `s40_video_fallback_reason()` identify the selected
output path. `s40_video_get_stats()` reports presentation and message-handling
counts and time spent yielding the display. These are software diagnostics,
not counts of distinct frames visible on the panel.
