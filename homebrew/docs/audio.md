# PCM audio

[s40/audio.h](../include/s40/audio.h) provides one native PCM stream per
application. The runtime copies submitted samples and closes the stream during
session cleanup. Audio is optional: check the binding and open results, and let
the application continue silently when unavailable.

```c
#include "s40/audio.h"

s40_audio_format format = {
    .rate = 11025,
    .channels = 2,
    .volume = 65,
    .buffer_ms = 160
};
if (s40_audio_bind_device() && s40_audio_open(&format) > 0) {
    int accepted = s40_audio_write(samples, frames);
    /* Advance by accepted frames. Retry any remainder on a later update. */
}
```

Supported formats are signed 16-bit little-endian PCM at 11025 or 22050 Hz,
mono or interleaved stereo. Counts are frames: one sample per channel. Volume is
0–100 and is selected when opening the stream. Reopen to change the format or
volume. Zero volume preserves timing while submitting silence.

## Feeding the stream

Call audio functions on the application's native task, outside message callbacks,
and pump firmware messages regularly. Open and close can wait for firmware replies.
Writes do not wait for playback: a positive result is the number of accepted
frames, zero means the source queue is full, and a negative result means failure.
The caller retains its buffer and may reuse accepted samples immediately.

Each write accepts at most 512 frames. Collect small mixer updates into
256–512-frame batches to reduce overhead. `buffer_ms` bounds the source queue
between 100 and 500 ms; zero selects 500 ms. Device startup may add silence before
the first application samples, so the cap is not an exact end-to-end latency.

`s40_audio_queued()` reports frames still in the source queue, excluding decoder
read-ahead. It is not a playback cursor. For a mixer that needs a conservative
playback estimate, use [s40/audio_clock.h](../include/s40/audio_clock.h), poll it
regularly and keep the outstanding sample window below one second. Games and
emulators can instead retain their own clock and generate samples as they advance.

`s40_audio_status()` reports open, closed or failed state. Stop feeding on failure
and call `s40_audio_close()`, which also handles partially completed startup.
Close before a long pause or unloading an independently managed native payload.
The shared runtime closes automatically before releasing application memory.

## Compatibility and other ports

[Device compatibility](devices.md) lists available audio bindings separately
from launcher support. An audio binding alone does not provide the rest of the
runtime for a phone.

Shared-launcher applications call `s40_audio_bind_device()`. Standalone ports may
use `s40_audio_bind_firmware()` with their resolved native clock and the required
heap, time and message-pump services. The explicit RM-839 02.33 adapter remains
available for the existing Quake integration. The public header documents these
entry points; private integration requirements are in
[src/native/audio/commgr/runtime_services.h](../src/native/audio/commgr/runtime_services.h).
