# S40 PC Bridge

A one-shot Java ME installer for a native PC Suite debugging bridge. The first
adapter targets Nokia 6303 classic RM-443 firmware 10.12. The host client is a
separate tool in `$S40_RESEARCH_ROOT/tools/pcsuite/`; its README and PROTOCOL
describe the commands and wire format. The installer builds without that tool.

Build from the SDK repository root:

```sh
bash homebrew/build.sh pc_bridge
```

Outputs are `homebrew/dist/pc_bridge/S40PcBridge.jar` and its matching `.jad`.
The shared homebrew build dependencies and overrides apply. `build.sh` respects
`S40_BUILD_DIR` and `S40_DIST_DIR` overrides as well.

Launching the installer requires an RM-443 10.12 platform identity, discovers
and validates the RM-443 bootstrap, then loads the transient native installer
and installs the protocol hook.
Success closes the MIDlet automatically. A failed installation shows its check
code and native diagnostics on the handset; Exit closes that error screen.
There are no normal-operation menus or Java request handlers.

The installer copies the separate position-independent resident image into an
OS allocation, applies any relative relocations and synchronizes its caches
before publishing either pointer. It replaces both Panel's class lookup and
the communication manager's cached receive callback. Original protocol messages
continue through the original callback with all five arguments preserved. The
native code, state and callbacks remain allocated after the Java task exits.
Reboot removes the runtime hook and its allocations.

The resident handler supports liveness/capability queries, bounded memory
reads/writes, tracked allocation/free/listing, cache synchronization, detach and
ARM/Thumb calls that capture returned `r0`–`r3`. Native calls run synchronously on the firmware
protocol task. Payloads must follow the calling convention and return promptly;
this bridge does not provide exception recovery, preemption or isolated tasks.

Host checks:

```sh
python3 homebrew/apps/pc_bridge/tests/test_resident.py
python3 homebrew/apps/pc_bridge/tests/test_install.py
```

These execute the compiled ARM code in Unicorn with firmware services stubbed.
They check protocol framing, allocation ownership, calls, detach, publication
order and repeat installation without firmware dumps.

Returning payload examples:

```sh
bash homebrew/apps/pc_bridge/payloads/build.sh
"$S40_RESEARCH_ROOT/tools/pcsuite/bin/pcsuite" native exec homebrew/build/pc_bridge/payloads/smoke_arm.bin 17 25 3 0
"$S40_RESEARCH_ROOT/tools/pcsuite/bin/pcsuite" native exec homebrew/build/pc_bridge/payloads/smoke_thumb.bin 100 23 3 0 --thumb
```

`scan_words.bin` performs a masked word search in up to 1 MiB of mapped memory
without downloading the region. Pass start, byte length, needle and mask in
r0–r3; r0 returns the first match address (zero if absent) and r1 the match count.
Invalid alignment/length returns `0xffffffff` in r0 and r1. The example linker
requires returning code with register arguments and rejects data/global pointers;
general ELF loading and arbitrary relocations are not part of raw `native exec`.

Original code is MIT licensed under `homebrew/COPYING`. Shared library notices
are included in the JAR.
