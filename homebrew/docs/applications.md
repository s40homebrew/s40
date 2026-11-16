# Writing an application

Use `examples/hello/` as the smallest complete template. Applications supply four
pieces: a native entry point, a small Java launcher, a MIDlet manifest and build
configuration. The shared build owns profiles, resolver, bootstrap, runtime and
packaging. Device selection always happens at runtime inside each application JAR.

## Native entry and services

Include `s40/runtime.h` and implement `int s40_app_main(void)`. Returning zero exits
normally. `s40_finish(status)` exits from nested calls; `s40_error(format, ...)`
records an error for the Java launcher. The runtime owns `native_main`, so do not
define it in an application.

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

The buffer has native display dimensions and `0xFFRRGGBB` pixels. Present after
writing a frame; do your own scaling if the application uses a different resolution.
The runtime releases the buffer/display on exit. `s40_poll_event` returns raw native
events with named types/codes in `s40/events.h`. Keep control bindings in the app;
the sibling Doom repository's `s40/shared/native/input.c` demonstrates Doom-specific held-key handling.

`malloc`/`free` and the supported `stdio.h` subset use the native OS, with tracked
cleanup at the end of the session. Call `s40_set_root` with a trailing separator
before opening relative file paths. The default root is `C:\`. Use `s40_set_log`
to route stdout/stderr to an opened application log. No Java calls service the
application loop. Pump messages before draining events; sleep also pumps while waiting.

`s40/audio.h` provides copied PCM writes, bounded queuing, status and cleanup.
Audio is an optional explicitly bound driver; unsupported profiles fail open
with -1. The first adapter targets RM-839 02.33 and is used by the separate
Quake port. The RM-443/RM-614 launchers do not yet have audio bindings. See
[Native PCM audio](audio.md) for format, ownership and firmware requirements.

## Java presentation

Subclass `HomebrewMIDlet` and implement `applicationName()` and
`instructions(int inputLayout)`. Optionally override `startLabel()`. The default
native resource is `/application.bin`, supplied by the build. Keep the MIDlet class
named in the manifest. No copied resolver or bootstrap is needed.

The CLDC build uses the default Java package, Java 1.3 source syntax and
class version 45.3 with CLDC StackMaps.

## Build configuration

Each `examples/<name>/build-config.sh` is sourced by the common build. It supplies:

- `ARTIFACT`: JAR/JAD filename stem.
- `APP_SOURCES`: an array of native C source paths.
- `APP_FLAGS`: optional compiler flags/include paths for application sources only.
- `NATIVE_PAYLOAD_BUDGET=1`: derive the Java/native allocation bounds from the
  checked packed image, relocation/import tables and stack. This removes the
  default 3 MiB image / 4 MiB allocation ceiling for applications such as Linux.
  Both guards receive generated matching constants; header arithmetic remains
  overflow-checked. Other applications keep the original limits. This controls
  loading bounds, not the amount of memory the handset allocator can supply.

External projects can set `S40_APP_DIR`, `S40_BUILD_DIR` and `S40_DIST_DIR` to
absolute paths and invoke `build.sh <name>`. Their source directory follows the
same layout, including `build-config.sh`, `MANIFEST.MF` and `src/*.java`.
Use a dedicated absolute `S40_BUILD_DIR` without `..` components. On first use,
a nondefault build directory must be empty (or absent); the build records its
ownership in `.s40-java-build`. Later builds remove only its `classes` and
`preverified` directories. Source, workspace and home roots are rejected, as are
symlinked cleanup targets. Existing default build directories need no marker.

`EXAMPLE_DIR`, `PROJECT_DIR`, `WORKSPACE_DIR` and `BUILD_DIR` are available to that
configuration. The runtime compiles separately without application flags or engine
include paths. Object filenames use each source's basename, so application source
basenames must be unique. Native assembly additions would need an explicit build
extension; the current app source contract is C.

Our foundation and original example code are MIT licensed, except for the
GPL-covered Doom backend noted in the project README. Keep any application-specific
dependency licenses and notices in `examples/<name>/licenses/`. Packaging includes
that directory alongside the shared MIT and newlib/GCC notices; filenames must be
unique across the two notice directories. The existing examples' license choices
do not prescribe a license for independently written new applications.

The shared build emits `build/<name>/application.elf`, `application.bin` and the
preverified classes, then `dist/<name>/<ARTIFACT>.jar` and `.jad`. It includes all
registered device profiles. Normal builds need no firmware images.
