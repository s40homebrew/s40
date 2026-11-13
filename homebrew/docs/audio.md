# Native PCM audio

`s40/audio.h` provides PCM output. The current backend,
`runtime/audio/rm839_0233.c`, supports **Nokia 301 firmware 02.33** and is used
by TyrQuake. The 6303/C3 launchers have no audio binding.

Use signed 16-bit little-endian PCM, mono or interleaved stereo, at 11025 or
22050 Hz. Counts are frames, including both samples of a stereo pair.

```c
#include "s40/audio.h"

s40_audio_format format = {11025, 2, 60, 160};
s40_audio_bind(&s40_audio_rm839_0233);
if (s40_audio_open(&format) > 0) {
    int accepted = s40_audio_write(samples, frames);
    /* Advance by accepted frames; retry the rest on a later update.
       Zero means full; negative means failure. */
}
```

Call from the application's native task and pump firmware messages regularly.
`write` copies up to 512 frames per call, so the caller can reuse its buffer.
`buffer_ms` sets a 100–500 ms queue cap; zero selects 500 ms. The initial
4096-byte decoder probe can exceed a small mono queue cap.

`s40_audio_queued()` counts frames still in the source queue. The decoder may
have already read ahead. Use `s40/audio_clock.h` to pace the mixer by elapsed
time and consumed frames. TyrQuake uses a 160 ms queue and 120 ms mix-ahead.

`s40_audio_status()` returns 1 when open, 0 when closed, or a negative error.
`s40_audio_close()` is idempotent. Close before unloading the native payload;
the shared runtime does this on exit, including focus loss. Open/close requests
have a 1.5-second timeout and pump messages while waiting.

The backend uses firmware-owned PCM fragments and destructors. Its application
callback runs through the message pump and is removed before native return.
Exact firmware addresses and instruction prefixes are in
`runtime/audio/rm839_0233_abi.json`.
