#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
set -euo pipefail
A=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
P=$(CDPATH='' cd -- "$A/../.." && pwd)
R=$(CDPATH='' cd -- "$P/.." && pwd)
APP=ram_booster
ARTIFACT=S40RamBooster
# shellcheck source=homebrew/tools/midlet_build.sh
source "$P/tools/midlet_build.sh"
SOURCES=("$A"/src/*.java)
if [[ -z ${LLVM:-} ]]; then
    if command -v llvm-objcopy >/dev/null 2>&1; then
        LLVM=$(dirname "$(command -v llvm-objcopy)")
    else
        LLVM=/opt/homebrew/opt/llvm/bin
    fi
fi
CC=${CC:-$LLVM/clang}
LD=${LD:-$(command -v ld.lld)}
N="$A/native"
python3 "$A/tools/generate_protocol.py" "$A/src/HeapBoosterProtocol.java" "$B/heap_booster_protocol.h"
# Both current backends enter ARM state through the shared Thumb carrier. Future
# architectures need a matched payload + carrier selection; changing -march
# alone cannot port the CP15 code or VM object/method layouts.
FLAGS=(-target arm-none-eabi -march=armv6 -marm -mfloat-abi=soft -Os
    -ffreestanding -fno-builtin -fPIC -fno-stack-protector -fno-unwind-tables
    -fno-asynchronous-unwind-tables -Wall -Wextra -Werror "-ffile-prefix-map=$R=." "-I$B")
"$CC" "${FLAGS[@]}" -c "$N/entry.S" -o "$B/entry.o"
"$CC" "${FLAGS[@]}" -c "$N/patch_armv6.c" -o "$B/patch.o"
"$LD" -T "$N/payload.ld" --no-undefined -o "$B/payload.elf" "$B/entry.o" "$B/patch.o"
"$LLVM/llvm-objcopy" -O binary "$B/payload.elf" "$B/ram_booster.bin"
"$LLVM/llvm-objdump" -d "$B/payload.elf" > "$B/payload.disasm.txt"
"$LLVM/llvm-readobj" --relocations "$B/payload.elf" > "$B/payload.relocations.txt"
# ARMv5 uses a separate table decoder/cache writer, not the ARMv6 object.
"$CC" "${FLAGS[@]}" -march=armv5tej -c "$N/entry.S" -o "$B/entry_arm926.o"
"$CC" "${FLAGS[@]}" -march=armv5tej -c "$N/patch_arm926.c" -o "$B/patch_arm926.o"
"$LD" -T "$N/payload.ld" --no-undefined -o "$B/payload_arm926.elf" "$B/entry_arm926.o" "$B/patch_arm926.o"
"$LLVM/llvm-objcopy" -O binary "$B/payload_arm926.elf" "$B/ram_booster_arm926.bin"
"$LLVM/llvm-objdump" -d "$B/payload_arm926.elf" > "$B/payload_arm926.disasm.txt"
"$LLVM/llvm-readobj" --relocations "$B/payload_arm926.elf" > "$B/payload_arm926.relocations.txt"
python3 "$A/tools/generate_carrier.py" "$A/src/HeapBoosterSession.java.in" "$B/HeapBoosterSession.java" "$B/carrier_layout.json"
SOURCES+=("$B/HeapBoosterSession.java")
compile_midlet
cp "$B/ram_booster.bin" "$B/preverified/ram_booster.bin"
cp "$B/ram_booster_arm926.bin" "$B/preverified/ram_booster_arm926.bin"
"$JAVA8_HOME/bin/javap" -classpath "$B/preverified" -c -p -v HeapBoosterSession > "$B/HeapBoosterSession.javap.txt"
python3 "$P/tools/package_midlet.py" "$P" "$A" "$B" "$D" "$ARTIFACT"
