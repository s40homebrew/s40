# Native PCM audio

`runtime/include/s40/audio.h` is the shared, engine-independent PCM interface.
`runtime/native/audio.c` owns driver selection; runtime shutdown closes audio
before releasing application memory. Quake uses these same sources directly.

The first adapter is `runtime/audio/rm839_0233.c`, for **Nokia 301 RM-839,
firmware 02.33 only**. Speaker playback works on the handset; the current
buffering and pacing changes still need handset latency/underrun testing.
RM-443/RM-614 shared launchers do not bind this adapter. On those profiles,
`s40_audio_open` returns -1 until a compatible driver is added. Their existing
required firmware import contract is unchanged.

## Application contract

Use signed 16-bit little-endian PCM, mono or interleaved stereo, at 11025 or
22050 Hz. Counts are **frames**, not bytes or individual stereo samples.
Call from the application's native task, outside message callbacks. Pump
firmware messages regularly. Open/close also pump while waiting for replies;
each request has a 1.5-second deadline and an iteration bound.

For a port that has explicitly linked and validated the RM-839 adapter:

```c
#include "s40/audio.h"

s40_audio_format format = {11025, 2, 60, 160};
s40_audio_bind(&s40_audio_rm839_0233);
if (s40_audio_open(&format) > 0) {
    /* samples contains interleaved signed 16-bit left/right pairs. */
    int accepted = s40_audio_write(samples, frames);
    /* Advance by accepted frames; retry the remainder on a later update.
       Zero means full; negative means audio failed. */
}
```

`write` copies the accepted samples. The caller may immediately reuse its
buffer. Each call accepts at most 512 frames. `buffer_ms` sets the source queue
cap from 100 to 500 ms; zero retains the original 500 ms default. Quake uses
160 ms. The initial 4096-byte decoder probe may exceed a small mono queue cap;
subsequent writes obey the configured limit. The driver does not block waiting for
space. `s40_audio_queued()` reports source-queue frames, excluding data already
read ahead by the decoder. It is not an exact DAC playback cursor.

`s40/audio_clock.h` provides a conservative pacing clock for clients using
source-queue counts to drive a mixer. It limits advancement to elapsed time
at the sample rate and to frames removed from the queue. A decoder taking PCM
early therefore cannot make the client overproduce and fill hidden firmware
buffers. Call regularly; use a less-than-one-second outstanding PCM window.
This remains an estimate, not a hardware latency measurement. Quake combines
it with a 120 ms mix-ahead cap and an update before rendering.

`s40_audio_status()` returns 1 for an open stream, 0 when closed, and a negative
value on failure. `s40_audio_close()` is idempotent, including failed startup.
RM-839 initialization error stages are 10 (ComMgr registration), 20 (policy),
30 (source), 40 (initial data), 50 (player open), and 60 (play). Quake writes
the failed stage to `quake.log`.
It marks producer EOF, closes the player, releases audio policy, forgets an
unclaimed source ID and unregisters the application ComMgr object. Focus loss
must close audio before unloading the native payload; the shared runtime and
Quake entry point both do this. Timeouts are bounded but do not prove the
firmware has completed its own asynchronous cleanup.

The implementation uses firmware-allocated PCM fragments with firmware
destructors. No interrupt/audio-thread callback points into application code.
The sole application callback is a ComMgr message handler dispatched by the
application's message pump and removed before native return.

## RM-839 02.33 backend

`runtime/audio/rm839_0233_abi.json` records exact Thumb addresses and ROM
prefixes. The Quake import generator consumes it alongside the port's core ABI.
The bindings and class layout are specific to that firmware.

The driver sends the native MMAPI packets without calling Java/KNI wrappers.
Firmware owns fragment destruction; enqueue transfers ownership even when it
returns 1. The application message handler owns the messages it receives.

Source byte count is at +52, status at +64, and producer callbacks are firmware
owned. Consumer cancellation moves to status 4; the source stays alive until
producer EOF. Application reads are aligned snapshots; queue mutation is done
only by firmware routines using the source's mutex.

The stream carries a PCM WAV header, MIME enum 36 (the firmware table entry
for `audio/wav` / `audio/x-wav`), followed by small PCM fragments. Its declared
data length is large; actual termination uses source EOF. The initial 4096
silence bytes allow decoder probing before mixing starts.

## Checks

Fast host checks with memory/undefined-behavior sanitizers:

```sh
clang -std=c99 -Wall -Wextra -fsanitize=address,undefined -Ihomebrew/runtime/include homebrew/tests/audio/test_stream.c -o /tmp/s40-audio-test
/tmp/s40-audio-test
clang -std=c99 -Wall -Wextra -fsanitize=address,undefined -Ihomebrew/runtime/include homebrew/tests/audio/test_clock.c -o /tmp/s40-audio-clock-test
/tmp/s40-audio-clock-test
```

These exercise production stream ownership/cleanup and mixer pacing without
firmware. See [host checks](../tests/README.md) for dependencies and scope.
