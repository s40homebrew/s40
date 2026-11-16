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
while IFS= read -r source; do
    SOURCES+=("$P/$source")
done < "$A/shared-java.txt"
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
"$CC" "${FLAGS[@]}" -march=armv5tej -Oz -c "$N/patch_fixed_growth.c" -o "$B/patch_fixed_growth.o"
"$LD" -T "$N/payload.ld" --no-undefined -o "$B/payload_fixed.elf" "$B/entry_arm926.o" "$B/patch_fixed_growth.o"
"$LLVM/llvm-objcopy" -O binary "$B/payload_fixed.elf" "$B/ram_booster_fixed.bin"
"$LLVM/llvm-objdump" -d "$B/payload_fixed.elf" > "$B/payload_fixed.disasm.txt"
"$LLVM/llvm-readobj" --relocations "$B/payload_fixed.elf" > "$B/payload_fixed.relocations.txt"
# X-GOLD ARM1176 executes its Java policy from flash. Its legacy identity MMU
# uses a section-to-coarse RAM shadow, separate from the RAPU RAM-code writer.
"$CC" "${FLAGS[@]}" -c "$N/patch_xgold.c" -o "$B/patch_xgold.o"
"$LD" -T "$N/payload.ld" --no-undefined -o "$B/payload_xgold.elf" "$B/entry.o" "$B/patch_xgold.o"
"$LLVM/llvm-objcopy" -O binary "$B/payload_xgold.elf" "$B/ram_booster_xgold.bin"
"$LLVM/llvm-objdump" -d "$B/payload_xgold.elf" > "$B/payload_xgold.disasm.txt"
"$LLVM/llvm-readobj" --relocations "$B/payload_xgold.elf" > "$B/payload_xgold.relocations.txt"
"$CC" "${FLAGS[@]}" -c "$N/growth_entry.S" -o "$B/growth_entry.o"
"$CC" "${FLAGS[@]}" -Oz -flto -c "$N/patch_growth.c" -o "$B/patch_growth.o"
"$LD" -T "$N/payload.ld" --no-undefined -o "$B/payload_growth.elf" "$B/entry.o" "$B/patch_growth.o" "$B/growth_entry.o"
"$LLVM/llvm-objcopy" -O binary "$B/payload_growth.elf" "$B/ram_booster_growth.bin"
"$LLVM/llvm-objdump" -d "$B/payload_growth.elf" > "$B/payload_growth.disasm.txt"
"$LLVM/llvm-readobj" --relocations "$B/payload_growth.elf" > "$B/payload_growth.relocations.txt"
python3 - "$B" <<'PY'
import re
import sys
from pathlib import Path
build = Path(sys.argv[1])
limit = int(re.search(r"#define PAYLOAD_CAPACITY (\d+)u", (build / "heap_booster_protocol.h").read_text()).group(1))
for name in ("ram_booster.bin", "ram_booster_arm926.bin", "ram_booster_xgold.bin", "ram_booster_growth.bin", "ram_booster_fixed.bin"):
    payload = (build / name).read_bytes()
    if not payload or len(payload) > limit or len(payload) % 4:
        raise SystemExit("Native payload exceeds its reserved method: " + name)
    if payload.count(bytes.fromhex("017e05b0")) != 1:
        raise SystemExit("Native result binding is not unique: " + name)
PY
python3 "$A/tools/generate_carrier.py" "$A/src/HeapBoosterSession.java.in" "$B/HeapBoosterSession.java" "$B/carrier_layout.json"
SOURCES+=("$B/HeapBoosterSession.java")
compile_midlet
cp "$B/ram_booster.bin" "$B/preverified/ram_booster.bin"
cp "$B/ram_booster_arm926.bin" "$B/preverified/ram_booster_arm926.bin"
cp "$B/ram_booster_xgold.bin" "$B/preverified/ram_booster_xgold.bin"
cp "$B/ram_booster_growth.bin" "$B/preverified/ram_booster_growth.bin"
cp "$B/ram_booster_fixed.bin" "$B/preverified/ram_booster_fixed.bin"
"$JAVA8_HOME/bin/javap" -classpath "$B/preverified" -c -p -v HeapBoosterSession > "$B/HeapBoosterSession.javap.txt"
python3 "$P/tools/package_midlet.py" "$P" "$A" "$B" "$D" "$ARTIFACT"
