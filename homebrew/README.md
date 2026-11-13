# S40 homebrew SDK

The shared Java launcher, firmware resolver and ARM runtime for Nokia 6303
classic (RM-443) and C3-00 (RM-614). Each application includes both profiles
and chooses one on the phone. Services are discovered from the running firmware.

Start with [build setup](../docs/building.md), then:

```sh
bash homebrew/build.sh hello
```

The [Hello example](examples/hello/README.md) shows native graphics and input.
[RAM Booster](apps/ram_booster/README.md) and
[Heap Check](apps/heap_check/README.md) are separate utilities.
[Native Time](../native_time/README.md) is a smaller standalone native-call example.
Doom, TyrQuake and Linux live in separate upstream forks alongside this repo.

## Source

- `resolver/` and `profiles/`: firmware discovery and device assumptions.
- `bootstrap/`: native loader, relocation and Java return path.
- `runtime/java/`: MIDlet UI, reversible memory access and native entry.
- `runtime/include/s40/`: application API; `runtime/native/`: implementation.
- `runtime/audio/`: Nokia 301 02.33 PCM backend used by TyrQuake.
- `runtime/libc/include/`: the supported C/POSIX compatibility headers.
- `examples/hello/`: a small graphics/input example.
- `apps/`: RAM Booster and the ordinary Java heap checker.
- `tools/`: code generation, preverification and JAR packaging.

## Guides

- [Writing an application](docs/applications.md)
- [Runtime architecture](docs/architecture.md)
- [Device profiles and porting](docs/devices.md)
- [PCM audio](docs/audio.md)

Original code is MIT licensed: [COPYING](COPYING). Linked newlib/libgcc retain
the notices in `licenses/`, which are also packaged in the generated JARs.
