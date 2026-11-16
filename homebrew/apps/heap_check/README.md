# S40 Heap Check

A plain Java ME app that displays `Runtime.totalMemory()` / `freeMemory()` and
tries to retain 1, 1.5, 2, 3, 4, 4.5, 5, 6 or 7 MiB in 256 KiB chunks. It writes
sample data, requests garbage collection, verifies the data and releases the
arrays. If allocation runs out of memory, it still verifies the successfully
retained arrays and reports their total.

Run it before and after applying [RAM Booster](../ram_booster/README.md),
exiting between launches so a changed quota takes effect. Start one MiB below
the selected quota to leave room for the checker and object headers. For a
stock 2 MiB quota, use the 1 or 1.5 MiB test. Repeat a test after returning to
the menu to check that released space can be reused.

Use the 4.5 MiB test to check expansion beyond a 4 MiB arena, independently
of the reported heap size.

The latest displayed result replaces the UTF-8 record in the `HeapCheckResult`
RMS store, allowing collection through PC Suite. An RMS error leaves the
on-screen result available. This app uses no native code or memory patches.

Build from the SDK repo root with `bash homebrew/build.sh heap_check`.
Install the matching JAR/JAD from `homebrew/dist/heap_check/`.
