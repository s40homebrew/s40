# S40 homebrew SDK

Build native applications for Nokia Series 40 with a shared Java launcher,
firmware discovery and a small C runtime. Applications use the same graphics,
input, timing, file and audio APIs across the supported device profiles.
See [device compatibility](docs/devices.md) before installing on a phone.

Follow [build setup](../docs/building.md), then build the smallest example:

```sh
bash homebrew/build.sh hello
```

Install the matching JAR and JAD from `homebrew/dist/hello/`. Intermediate output
is under `homebrew/build/hello/`. Builds do not need firmware dumps.

## Examples and utilities

Pass the target below to `bash homebrew/build.sh <target>`.

| Application | Target | Purpose |
| --- | --- | --- |
| [Hello](examples/hello/README.md) | `hello` | Native graphics and input; start here for a new app |
| [Native Time](examples/native_time/README.md) | `native_time` | A minimal native call from Java |
| [RAM Booster](apps/ram_booster/README.md) | `ram_booster` | Increase the memory quota of Java apps |
| [Heap Check](apps/heap_check/README.md) | `heap_check` | Check Java memory allocation and retention |
| [Battery Info](apps/battery/README.md) | `battery` | Show battery capacity and charging information |
| [PC Bridge](apps/pc_bridge/README.md) | `pc_bridge` | Install a native debugging bridge for PC Suite |

Doom, TyrQuake, Gnuboy and Linux are separate projects that use this SDK. Their
own guides cover assets, controls and installation.

## Develop an application

- [Writing an application](docs/applications.md): entry point, launcher, events,
  files and build configuration.
- [Video](docs/video.md) and [audio](docs/audio.md): framebuffer and PCM output.
- [Architecture](docs/architecture.md): component boundaries and session lifetime.
- [Device compatibility and porting](docs/devices.md): profiles and optional features.
- [Host checks](tests/README.md): checks for each SDK component.

The public C API is in `include/s40/`; supported C library headers are in
`include/libc/`. Implementation lives in `src/java/`, `src/bootstrap/` and
`src/native/`. Apps, examples, build tools and tests have their own directories.

Run `python3 homebrew/tools/format.py` from the repository root to format C,
headers, Java and Python, or add `--check`. Generated files are excluded.

Original SDK code is MIT licensed: [COPYING](COPYING). Bundled newlib/libgcc
notices are in `licenses/` and included in generated JARs. Applications retain
their own licenses.
