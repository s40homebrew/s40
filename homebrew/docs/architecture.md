# Runtime architecture

Java handles firmware discovery, native entry and loading. The ARM application
calls firmware services directly for memory, files, graphics, input and timing.
It runs in the Java host's OS task on a private native stack, then returns to
Java after cleanup.

## Source

- `resolver/`: exported symbols and instruction relationships in mapped ROM.
- `profiles/`: model selection, memory maps, VM layouts and device-specific recipes.
- `runtime/java/HomebrewMIDlet.java`: UI and worker thread.
- `runtime/java/NativeSession.java`: getRGB memory view, native bindings and loading.
- `bootstrap/`: loader, relocation and KNI entry/return.
- `runtime/native/`: application lifecycle and firmware service wrappers.

`NativeSession` inspects its own concrete class's KVM method table. Its native
methods must remain on that class. Do not allocate or call UI code while its
array length is forged.

[RAM Booster](../apps/ram_booster/README.md) uses a separate, smaller session
that patches the Java launch-quota policy and returns immediately.

## Launch and exit

1. Select a profile from `microedition.platform` and establish the memory view.
2. Resolve firmware services and check the bootstrap's instruction relationships.
   **Check firmware** stops here and restores the view.
3. **Start** installs the native loader and restores temporary Java state.
4. Stream `/application.bin`, relocate it, bind imports and synchronize caches.
5. Enter `native_main`, which calls the application's `s40_app_main`.
6. On return or `s40_finish`, run exit callbacks, release display/files/memory,
   free the application allocation and return to Java.

Call `s40_pump` before draining input events. `s40_sleep_ms` also pumps messages.
Focus loss exits the native session. Cleanup callbacks run in reverse order.

## Image format

S4D0 version 2 starts with eight little-endian 32-bit words: magic, version,
initialized size, memory size, entry offset, relocation count, stack size and
binding count. The initialized image follows, then `R_ARM_RELATIVE` offsets
and `(image offset, API index)` bindings. The native stack is 192 KiB.

`tools/gen_imports.py` defines the shared Java/C indices and generates import
veneers. Changes to the format must agree across Java, C, assembly and the packer.

## Runtime details

- Profiles resolve 27 core firmware bindings. The linker removes unused veneers.
- Display uses GDI format 12 / HAL format 9 and opaque `0xFFRRGGBB` pixels,
  up to 76,800 pixels.
- Input contains translated character codes. C3 adds modifiers and history
  bits in the upper half of the event word.
- C file operations use a private compatibility layer. `rename` copies then
  unlinks; `fflush` is unbuffered. Paths use byte-to-UTF-16 conversion.
- PCM audio currently has a Nokia 301 02.33 adapter; see [audio](audio.md).
