# S40 homebrew SDK

The shared Java launcher, firmware resolver and ARM runtime for Nokia 6300
(RM-217), 6303 classic (RM-443) and C3-00 (RM-614). Each application includes
all three profiles and chooses one on the phone. Services are discovered from
the running firmware.

Start with [build setup](../docs/building.md), then run from the repository root:

```sh
bash homebrew/build.sh hello
bash homebrew/build.sh ram_booster
bash homebrew/build.sh heap_check
bash homebrew/build.sh battery
bash homebrew/build.sh pc_bridge
```

Native build output goes in `homebrew/build/<app>/`; installable JARs and matching
JADs go in `homebrew/dist/<app>/`. Builds need no firmware dumps.

The [Hello example](examples/hello/README.md) shows native graphics and input.
[RAM Booster](apps/ram_booster/README.md), [Heap Check](apps/heap_check/README.md),
[Battery Info](apps/battery/README.md) and [PC Bridge](apps/pc_bridge/README.md)
are separate utilities. Battery Info and PC Bridge require RM-443 10.12.
[Native Time](../native_time/README.md) is a smaller standalone native-call example.
Doom, TyrQuake and Linux live in separate repositories and build against this SDK.

## Source

- `src/java/resolver/` and `src/java/profiles/`: firmware discovery, device assumptions and shared
  ROM-validation helpers. RM-217 uses instruction recipes without named exports.
- `src/bootstrap/`: native loader, relocation and Java return path.
- `src/java/launcher/`: MIDlet UI, reversible memory access and native entry.
- `include/s40/`: application API; `src/native/`: implementation.
- `src/native/audio/`: Nokia 301 02.33 PCM backend used by TyrQuake.
- `include/libc/`: the supported C/POSIX compatibility headers.
- `examples/hello/`: a small graphics/input example.
- `apps/`: standalone utilities and their app-specific code.
- `tools/`: code generation, preverification and JAR packaging.
- `tests/`: host and compiled-code checks without firmware dumps.

Device families keep their layouts and instruction recipes in `Rm*Profile.java`.
`gen_imports.py` generates shared Java/C constants and native veneers; it does
not read firmware or generate the resolver.

## Guides

- [Writing an application](docs/applications.md)
- [Runtime architecture](docs/architecture.md)
- [Device profiles and porting](docs/devices.md)
- [PCM audio](docs/audio.md)
- [Focused host checks](tests/README.md)

Run `python3 homebrew/tools/format.py` from the repository root to format owned
C, headers, Java and Python, or add `--check`. Generated files are excluded.
Shell scripts, assembly, manifests and Markdown use manual formatting.

Original code is MIT licensed: [COPYING](COPYING). Linked newlib/libgcc retain
the notices in `licenses/`, also packaged in generated JARs. Doom retains its
own GPL license and notices in the Doom repository.
