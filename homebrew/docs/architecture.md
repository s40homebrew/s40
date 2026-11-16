# Architecture and contracts

The application runtime runs native code within a Java launch session. The
standalone [RAM Booster](../apps/ram_booster/README.md) uses a separate, smaller
session to change Java heap policy. It is not needed by native applications,
which allocate from the firmware's OS heap.

## Native execution

The application image is ARM machine code. Import veneers branch directly to
resolved firmware functions: allocations use the OS heap, files use `filesys_*`,
display updates use GDI, and event/message handling uses native services. These
operations do not pass through Java ME APIs. Java performs discovery, bootstrapping
and resource delivery, and receives control again after native teardown.

Execution belongs to the Java host's OS task and native application ID, on a
private native stack. The runtime handles that host's messages while the Java
call remains outstanding and exits on focus loss. It does not create a task
or provide a background lifetime.

Bundled newlib/libm and compiler helpers provide formatting, parsing and arithmetic;
allocation and file wrappers use firmware services. Firmware ELF loading and a
general native library lookup API are not implemented.

## Responsibilities

`resolver/` finds exports and checked instruction relationships within the selected
profile's ROM windows. `profiles/` supplies model-family assumptions: ROM/RAM maps,
masked recipes, KVM/native relationships, display dimensions, input layout and
message/timer policy. Model-name matching selects a candidate, not proof of support.
ROM validation must succeed before a native application can start. Each device
profile keeps its policy and pattern factory in one file.

The application runtime and RAM Booster use the same `PlatformIdentity` model
boundaries, `RomRecipe` masked matcher and `RomRanges` ROM-table validator.
`Rm217Memory` supplies their shared descriptor-free range and allocator recipes.
These helpers allocate recipes before opening the temporary Java memory view.
The booster's `shared-java.txt` lists its shared source dependencies.

`HomebrewMIDlet` handles UI, runtime profile selection and a worker thread.
Applications subclass it to supply a title and instructions. `NativeSession` owns
getRGB, the reversible heap view, native bindings, carrier and resource streaming.
Its native methods must remain on its concrete class: the bootstrap inspects that
class's KVM method table. Do not allocate or call UI code while the view is forged.

`bootstrap/` owns the native loader and KNI entry/return mechanics. `runtime/native/`
owns `native_main`, session lifetime, message dispatch, the master display, native
clock, event polling, tracked allocations and file handles. Its public entry points
are in `runtime/include/s40/`; `firmware.h` and `fw_*` veneers are implementation
details.

An application provides `int s40_app_main(void)` and owns its controls and assets.

## Session flow

1. Read `microedition.platform`, choose a unique registered device profile and
   prepare the session before firmware access.
2. Establish the reversible Java memory view. Resolve and validate ROM
   exports, masked code patterns, native calling relationships and bootstrap ABI.
3. For **Check firmware**, restore the view and return diagnostics. A successful
   same-session cache is locally revalidated on later calls; nothing is persisted.
4. For **Start**, install a resident native loader using the selected bootstrap.
   C3's switch carrier is materialized by a Java call before discovery. KVM can
   rewrite the executed `ireturn` byte from `AC` to `D5`/`D6`, so live validation
   accepts those three return encodings while checking all other bytes exactly.
   Restore temporary Java state before reading or streaming application resources.
5. Stream the native image, relocate it, bind imports and synchronize caches.
   Doom uses `/application.bin.parts` and 64-KiB resources so older VMs need not
   inflate the whole image into Java memory. Other apps can use `/application.bin`.
   The loader clears its Java method binding and enters `native_main` on a private
   native stack. No KNI/Java callbacks occur in the application loop.
6. The runtime invokes `s40_app_main`. Returning or calling `s40_finish` runs exit
   callbacks, releases the display, closes tracked files and frees tracked memory.
   The loader frees its application allocation and returns to Java diagnostics.

Focus loss ends the native session so the application does not paint over phone UI.
Applications must pump messages regularly: call `s40_pump` before draining a
batch of events. `s40_sleep_ms` also pumps while waiting. Cleanup callbacks run
in reverse registration order; a callback that exits through `s40_finish` is not
run again.

## Wire formats

The native resource uses S4D0 version 2: eight little-endian
32-bit header words (magic, version, initialized bytes, memory bytes, entry offset,
relative relocation count, stack bytes, binding count), then initialized image,
`R_ARM_RELATIVE` offsets and `(image offset, API index)` binding pairs. The current
stack contract is 192 KiB. The loader and Java side impose matching size limits.

The pinned Java `int[]` is a command/result descriptor. Important word groups:

| Words | Meaning |
|---|---|
| 0–3 | Magic `0x444F4F4D`, command, allocation, status |
| 4–10 | Native diagnostics; 5/8/9 may carry application detail |
| 11–15 | Image size, transfer/relocation metadata, entry and binding count |
| 16–79 | Error text storage |
| 80–84, 86 | Temporary bootstrap/loader state |
| 85 | Optional native foreground-refresh callback |
| 87–88 | Display width/height |
| 89–92 | Timer policy and discovered special handler |
| 93 | Input layout |
| 94 | Application launch options |
| 95 | GDI canvas layout: 0 current, 1 legacy |
| 96–1119 | 4 KiB transfer buffer |
| 1120 onward | Discovered firmware service table |

`tools/gen_imports.py` owns shared import/config indices. A format
change must update Java, C, assembly and the packer together. Application code
should use the public API instead of treating spare descriptor words as storage.

## Runtime details

- RM-443 and RM-614 require the full 27-entry core contract. RM-217 resolves
  23 entries plus a mandatory foreground-refresh callback. Its three unused
  master-display functions and LCD-owner slot stay zero; the loader permits
  exactly these four missing imports only with the legacy canvas policy and
  a nonzero refresh callback. Other imports remain mandatory.
- Display uses GDI format 12 / HAL format 9, opaque 32-bit pixels and at most
  76,800 pixels. See [device profiles](devices.md) for the foreground-refresh and
  legacy canvas backends.
- Key events expose translated character codes. On C3, the upper half also contains
  modifier flags and a rotating history index; it is not a physical scan code.
- The C layer is a deliberately limited compatibility subset, with private `s40_`
  file symbols and bundled soft-float/newlib support. Relative paths use an explicit
  application root. `rename` is copy/close/unlink, not atomic; `fflush` is unbuffered.
  Filename conversion is byte-to-UTF-16, not a complete Unicode filesystem API.
- Audio has an optional shared PCM API and an experimental exact-firmware
  RM-839 02.33 adapter, used by the separate Quake port. RM-443/RM-614 audio
  bindings are not implemented. See [audio.md](audio.md).
- Networking, threads and C++ unwinding are unsupported. The binary ABI may change.
