# S40 PC Bridge

Install a native PC Suite debugging bridge on Nokia 6303 classic RM-443 firmware
10.12. It supports memory inspection, tracked native allocations and short
ARM/Thumb calls from a computer. The separate host client lives under
`$S40_RESEARCH_ROOT/tools/pcsuite/`; follow its README for connection and command
usage. The phone installer builds without that tool.

## Build and install

Follow [build setup](../../../docs/building.md), then run from the SDK root:

```sh
bash homebrew/build.sh pc_bridge
```

Install `homebrew/dist/pc_bridge/S40PcBridge.jar` and its matching JAD.
Launching validates the firmware and installs the bridge. Success closes the
MIDlet automatically; failure leaves diagnostics on screen. Exit closes that
error screen.

The bridge remains resident after the MIDlet exits. Reboot removes it and its
allocations. Original PC Suite messages continue to use their normal handler.
Native calls execute synchronously on a firmware task: payloads must return
promptly and preserve the calling convention. The bridge provides no exception
recovery, preemption or isolated task for a payload.

## Returning payload examples

```sh
bash homebrew/apps/pc_bridge/payloads/build.sh
"$S40_RESEARCH_ROOT/tools/pcsuite/bin/pcsuite" native exec homebrew/build/pc_bridge/payloads/smoke_arm.bin 17 25 3 0
"$S40_RESEARCH_ROOT/tools/pcsuite/bin/pcsuite" native exec homebrew/build/pc_bridge/payloads/smoke_thumb.bin 100 23 3 0 --thumb
```

`scan_words.bin` searches up to 1 MiB of mapped memory without downloading it.
Pass start, byte length, needle and mask in r0–r3; r0 returns the first match
address (zero if absent) and r1 the count. Invalid alignment or length returns
`0xffffffff` in r0 and r1.

These examples are returning code with register arguments. Their linker rejects
data/global pointers; raw `native exec` does not load general ELF files or apply
arbitrary relocations. The host client's protocol reference documents the full
command set.

The [SDK test guide](../../tests/README.md) lists installer and resident-code
checks. Original code is MIT licensed under [COPYING](../../COPYING); shared
library notices are included in the JAR.
