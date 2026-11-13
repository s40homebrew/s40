# Heap Check

Shows Java heap usage and tries to retain 2, 3, 5 or 7 MiB in 256 KiB chunks.
It writes sample data, runs `System.gc()`, verifies the data and releases it.
An out-of-memory result reports how much was retained.

Run it before and after applying [RAM Booster](../ram_booster/README.md),
exiting between launches so the new quota takes effect.

Build with `bash homebrew/build.sh heap_check`. Install the matching JAR/JAD
from `homebrew/dist/heap_check/`.
