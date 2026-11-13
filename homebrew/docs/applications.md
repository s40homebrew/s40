# Writing an application

Start with `examples/hello/`: a C entry point, Java launcher, MIDlet manifest
and `build-config.sh`. The shared build supplies the runtime and device profiles.

## Native code

Include `s40/runtime.h` and implement `int s40_app_main(void)`:

```c
#include "s40/runtime.h"

int s40_app_main(void) {
    uint32_t* pixels = s40_display_open();
    unsigned count = s40_display_width() * s40_display_height();
    for (unsigned i = 0; i < count; ++i)
        pixels[i] = 0xFF204060;
    s40_display_present();
    s40_sleep_ms(1000);
    return 0;
}
```

The display buffer uses native dimensions and `0xFFRRGGBB` pixels. Call
`s40_display_present` after drawing. Pump messages with `s40_pump` before
reading events with `s40_poll_event`; event codes are in `s40/events.h`.
`s40_sleep_ms` also pumps messages.

Returning exits normally. `s40_finish(status)` exits from a nested call;
`s40_error(format, ...)` records an error for the launcher. The runtime releases
the display, tracked allocations and open files on exit.

`malloc`/`free` and the supported `stdio.h` functions use firmware services.
Set a file root with `s40_set_root` (include the trailing separator); the default
is `C:\`. `s40_set_log` directs stdout/stderr to an application log.
See [audio](audio.md) for the optional PCM interface.

## Java launcher

Subclass `HomebrewMIDlet` and implement `applicationName()` and
`instructions(int inputLayout)`. Optionally override `startLabel()`.
Name the class in `MANIFEST.MF`. The default native resource is `/application.bin`.

Use the default Java package and Java 1.3 syntax. The build emits CLDC class
version 45.3 with StackMaps.

## Build configuration

`build-config.sh` supplies:

- `ARTIFACT`: JAR/JAD filename stem.
- `APP_SOURCES`: array of C source paths, with unique basenames.
- `APP_FLAGS`: compiler flags and include paths for application sources.
- `NATIVE_PAYLOAD_BUDGET=1`: derive loading bounds from the packed image and
  stack instead of the default 3 MiB image / 4 MiB allocation limits.

The script can use `EXAMPLE_DIR`, `PROJECT_DIR`, `WORKSPACE_DIR` and `BUILD_DIR`.
Build with `bash homebrew/build.sh <name>`. Native output goes in
`homebrew/build/<name>/`; JAR/JAD files go in `homebrew/dist/<name>/`.

External apps can set `S40_APP_DIR`, `S40_BUILD_DIR` and `S40_DIST_DIR` to absolute
paths. Use a dedicated, initially empty build directory without `..` components.
The source layout is the same: `build-config.sh`, `MANIFEST.MF` and `src/*.java`.

Put application-specific license notices in `licenses/`. Packaging adds them
to the shared notices; filenames must be unique across both directories.
