#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
set -euo pipefail
APP=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
WORKSPACE=$(CDPATH='' cd -- "$APP/../../.." && pwd)
BUILD="${S40_BUILD_DIR:-$WORKSPACE/homebrew/build/pc_bridge}/payloads"
source "$WORKSPACE/tools/env.sh"
mkdir -p "$BUILD"
for name in smoke_arm smoke_thumb scan_words; do
    source="$APP/payloads/$name.S"
    if [[ $name == scan_words ]]; then source="$APP/payloads/$name.c"; fi
    "$CC" -target arm-none-eabi -march=armv5te -marm -mfloat-abi=soft -Os -ffreestanding -fPIC -fvisibility=hidden -fno-builtin -fno-stack-protector -fno-unwind-tables -fno-asynchronous-unwind-tables "-ffile-prefix-map=$WORKSPACE=." -c "$source" -o "$BUILD/$name.o"
    "$LD" -T "$APP/payloads/payload.ld" --no-undefined -o "$BUILD/$name.elf" "$BUILD/$name.o"
    "$LLVM/llvm-objcopy" -O binary "$BUILD/$name.elf" "$BUILD/$name.bin"
done
printf 'Returning native payloads: %s\n' "$BUILD"
