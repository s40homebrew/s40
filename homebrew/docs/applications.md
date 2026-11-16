# Writing an application

Start from [Hello](../examples/hello/README.md), the smallest complete native
application. An app supplies a C entry point, Java launcher, manifest and build
configuration. The SDK supplies firmware discovery, loading and runtime services.
Each shared-launcher JAR includes the registered device profiles and selects one
on the phone.

## Native entry point

Include `s40/runtime.h` and implement `int s40_app_main(void)`. Returning zero
exits normally. `s40_finish(status)` exits from a nested call; `s40_error(format,
...)` exits with a message for the launcher. Do not define `native_main`.

```c
#include "s40/runtime.h"

int s40_app_main(void) {
    uint32_t* pixels = s40_display_open();
    if (!pixels)
        s40_error("Cannot allocate display");
    unsigned count = s40_display_width() * s40_display_height();
    for (unsigned i = 0; i < count; ++i)
        pixels[i] = 0xFF204060;
    s40_display_present();
    s40_sleep_ms(1000);
    return 0;
}
```

The ordinary display uses native screen dimensions and `0xFFRRGGBB` pixels.
For a smaller RGB565 framebuffer with automatic scaling, follow the
[video guide](video.md). The runtime releases display memory on exit.

## Events and timing

Call `s40_pump()` before draining each batch with `s40_poll_event()`. Pumping keeps
input and phone services responsive; polling only reads queued events.
`s40_sleep_ms()` also pumps while waiting. Focus loss ends the native session.

Use [s40/events.h](../include/s40/events.h) for raw events and
[s40/input.h](../include/s40/input.h) to map keys to your own action bits:

1. Initialize `s40_input_state` with `s40_input_state_init`, the current input
   layout and a persistent array of `{key, actions}` bindings.
2. Call `s40_input_begin_frame` before pumping and draining events.
3. Feed each event to `s40_input_handle`, then read `held`, `pressed` and `released`.
4. Call `s40_input_release_all` when changing game/menu context.

Multiple keys can hold the same action independently. A quick press and release
in one frame sets both edge masks even when `held` is clear; use `pressed` when
a short tap must trigger an action. Bind printable letters in lowercase.

[s40/frame_clock.h](../include/s40/frame_clock.h) maintains rational frame
deadlines. Initialize a clock with the desired rate, advance once per simulated
frame, then query its remaining delay. Late frames return zero delay. Keep
simulation timing separate from presentation, and reset the clock after a pause.

## Files, memory and sound

The supported C library headers are in [include/libc/](../include/libc/).
`malloc`/`free` and file operations use native firmware services; the runtime
tracks allocations and open files for cleanup. Use `s40_on_exit` to register
application-specific saving and cleanup.

Set a root directory, including its trailing separator, with `s40_set_root`
before opening relative paths. The default is `C:\`. Native paths use UTF-8;
invalid text or paths exceeding 255 UTF-16 code units fail. File writes are
unbuffered. `rename` copies and then removes the old file, so it is not atomic;
check write/close results and preserve a backup when replacing important data.
`s40_set_log` directs stdout/stderr to an application log.

[PCM audio](audio.md) provides optional, copied sample output with bounded
queuing. Check support and startup results so the app can run silently on
unsupported firmware.

## Java launcher and file selection

Subclass `HomebrewMIDlet` and implement `applicationName()` and
`instructions(int inputLayout)`. Optionally override `startLabel()`. Name your
MIDlet in the manifest; the shared build packages its native resource.

Override `nativeOptions()` for application-defined flag bits and
`nativeArgument()` for a string such as a selected ROM path. Native code reads
these through `s40_launch_options()` and `s40_launch_argument()`. The UTF-8 string
is limited to 4095 bytes, defaults to empty and remains valid for the native
session. Do not modify or free it.

`HomebrewFilePicker.show(display, previous, root, title, suffixes, listener)`
provides a read-only selector. Supply a native directory ending in `/`, such as
`C:/predefgallery/`, and allowed suffixes. The picker lists matching files and
subdirectories, stays within its root, and returns a native path through
`listener.selected(path)`. It lists at most 128 entries per directory.

Include `javax.microedition.io.Connector.file.read` in `MIDlet-Permissions-Opt`
and allow the phone's file-access prompt. Some phones expose only predefined
Gallery subfolders to Java: Received files is
`C:/predefgallery/predeffilereceived/`. Native file access and Java's picker can
therefore expose different files.

Launcher sources use the default Java package and Java 1.3 syntax. The shared
build compiles and preverifies them for CLDC; use the compiler from
[build setup](../../docs/building.md).

## Build configuration

Each app has `build-config.sh`, `MANIFEST.MF` and `src/*.java`.
The configuration is sourced by the shared build and defines:

| Variable | Purpose |
| --- | --- |
| `ARTIFACT` | JAR/JAD filename stem |
| `APP_SOURCES` | Array of native C or assembly source paths |
| `APP_FLAGS` | Optional flags and include paths for app sources |
| `NATIVE_PAYLOAD_BUDGET=1` | Derive loading limits from the packed image for unusually large payloads |

Ordinary native apps retain the default 3 MiB image and 4 MiB allocation limits.
A derived budget changes the loader's bounds, not the RAM available on the phone.
Application source basenames must be unique. The shared runtime compiles
separately from app flags and engine include paths.

External projects set `S40_APP_DIR`, `S40_BUILD_DIR` and `S40_DIST_DIR` to absolute
paths before invoking `homebrew/build.sh <name>`. The app directory follows the
same layout. Use a dedicated build directory without `..` components; it must
initially be absent or empty. The build records ownership and cleans only its
class output on later runs. Workspace roots and symlinked cleanup targets are
rejected. `EXAMPLE_DIR`, `PROJECT_DIR`, `WORKSPACE_DIR` and `BUILD_DIR` are
available inside the configuration.

Output includes the native ELF/image under `homebrew/build/<name>/` and matching
JAR/JAD files under `homebrew/dist/<name>/`. Include dependency notices in the
app's `licenses/` directory; packaging combines them with SDK library notices,
so notice filenames must be unique. Independently written applications can
choose their own license.
