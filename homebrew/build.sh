#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Build one example against the shared S40 runtime; firmware is selected on-device.
set -euo pipefail
PROJECT_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
WORKSPACE_DIR=$(CDPATH='' cd -- "$PROJECT_DIR/.." && pwd)
EXAMPLE=${1:-hello}
case "$EXAMPLE" in
    ''|*[!a-z0-9_-]*) printf 'Invalid example name: %s\n' "$EXAMPLE" >&2; exit 1 ;;
esac
if [[ -z ${S40_APP_DIR:-} && -f "$PROJECT_DIR/apps/$EXAMPLE/build.sh" ]]; then
    exec bash "$PROJECT_DIR/apps/$EXAMPLE/build.sh"
fi
EXAMPLE_DIR=${S40_APP_DIR:-$PROJECT_DIR/examples/$EXAMPLE}
BUILD_DIR=${S40_BUILD_DIR:-$PROJECT_DIR/build/$EXAMPLE}
DIST_DIR=${S40_DIST_DIR:-$PROJECT_DIR/dist/$EXAMPLE}
test -f "$EXAMPLE_DIR/build-config.sh" || { printf 'Unknown example: %s\n' "$EXAMPLE" >&2; exit 1; }
# Shared tool discovery works from both the main repo and external ports.
source "$WORKSPACE_DIR/tools/env.sh"
source "$PROJECT_DIR/tools/sources.sh"
for tool in "$ARM_GCC" "$CC" "$LD" "$LLVM/llvm-objcopy" "$JAVA8_HOME/bin/java"; do
    test -x "$tool" || { printf 'Missing tool: %s\n' "$tool" >&2; exit 1; }
done
for resource in "$PROGUARD_JAR" "$LEGACY_JAVAC" "$SDK_CLASSES"; do
    test -f "$resource" || { printf 'Missing build dependency: %s\n' "$resource" >&2; exit 1; }
done
APP_SOURCES=()
APP_FLAGS=()
ARTIFACT=
NATIVE_PAYLOAD_BUDGET=0
# Project-owned configuration supplies only this application's sources/options.
# shellcheck disable=SC1091
source "$EXAMPLE_DIR/build-config.sh"
test -n "$ARTIFACT" && test "${#APP_SOURCES[@]}" -gt 0
# Fresh Java output prevents removed classes or another app's resources from
# leaking into a JAR.
python3 "$WORKSPACE_DIR/tools/clean_build_outputs.py" java "$PROJECT_DIR" "$BUILD_DIR" "build/$EXAMPLE" "$EXAMPLE_DIR"
mkdir -p "$BUILD_DIR/obj" "$BUILD_DIR/classes" "$BUILD_DIR/preverified" "$DIST_DIR"
python3 "$PROJECT_DIR/tools/gen_imports.py" "$BUILD_DIR"
FLAGS=(-target arm-none-eabi -march=armv5te -marm -mfloat-abi=soft -O2
    -fPIC -fvisibility=hidden -ffreestanding -fno-builtin -fno-stack-protector
    -fno-unwind-tables -fno-asynchronous-unwind-tables -fno-strict-aliasing
    -fwrapv -fno-common -ffunction-sections -fdata-sections
    -Wno-incompatible-pointer-types-discards-qualifiers -Wno-pointer-sign
    -Wno-deprecated-non-prototype
    "-I$PROJECT_DIR/include/libc" "-I$PROJECT_DIR/include"
    "-I$PROJECT_DIR/src/native" "-I$BUILD_DIR"
    "-ffile-prefix-map=$WORKSPACE_DIR=.")
OBJECTS=()
printf 'Compiling shared S40 runtime and %s...\n' "$EXAMPLE"
for source in "${S40_NATIVE_SOURCES[@]}"; do
    relative=${source#"$S40_SOURCE_ROOT/native/"}
    object="$BUILD_DIR/obj/runtime_${relative%.c}.o"
    mkdir -p "$(dirname -- "$object")"
    "$CC" "${FLAGS[@]}" -c "$source" -o "$object"
    OBJECTS+=("$object")
done
for source in "${APP_SOURCES[@]}"; do
    object="$BUILD_DIR/obj/app_$(basename "${source%.c}").o"
    "$CC" "${FLAGS[@]}" "${APP_FLAGS[@]}" -c "$source" -o "$object"
    OBJECTS+=("$object")
done
"$CC" "${FLAGS[@]}" -c "$BUILD_DIR/imports.S" -o "$BUILD_DIR/obj/imports.o"
LIBC=$("$ARM_GCC" -print-file-name=libc.a)
LIBM=$("$ARM_GCC" -print-file-name=libm.a)
LIBGCC=$("$ARM_GCC" -print-file-name=libgcc.a)
"$LD" -shared -Bsymbolic --no-undefined --gc-sections --exclude-libs=ALL -e native_main --hash-style=sysv -z max-page-size=4096 -Map="$BUILD_DIR/application.map" -o "$BUILD_DIR/application.elf" "${OBJECTS[@]}" "$BUILD_DIR/obj/imports.o" --start-group "$LIBM" "$LIBC" "$LIBGCC" --end-group -z notext
python3 "$PROJECT_DIR/tools/pack.py" "$BUILD_DIR"
LIMIT_FLAGS=()
if [[ $NATIVE_PAYLOAD_BUDGET == 1 ]]; then LIMIT_FLAGS+=(--from-payload); fi
python3 "$PROJECT_DIR/tools/gen_native_limits.py" "$BUILD_DIR" "${LIMIT_FLAGS[@]}"
"$LLVM/llvm-readobj" --file-headers --program-headers --relocations "$BUILD_DIR/application.elf" > "$BUILD_DIR/application.elf.txt"
printf 'Building shared bootstrap and Java launcher...\n'
"$CC" "${FLAGS[@]}" -Os -c "$PROJECT_DIR/src/bootstrap/loader.c" -o "$BUILD_DIR/loader-c.o"
"$CC" "${FLAGS[@]}" -c "$PROJECT_DIR/src/bootstrap/loader.S" -o "$BUILD_DIR/loader-s.o"
"$LD" -T "$PROJECT_DIR/src/bootstrap/loader.ld" -o "$BUILD_DIR/loader.elf" "$BUILD_DIR/loader-s.o" "$BUILD_DIR/loader-c.o"
"$LLVM/llvm-objcopy" -O binary "$BUILD_DIR/loader.elf" "$BUILD_DIR/native.bin"
"$CC" "${FLAGS[@]}" -c "$PROJECT_DIR/src/bootstrap/rm443_stage.S" -o "$BUILD_DIR/stage.o"
"$LD" --image-base=0 -Ttext=0 -e stage_start -o "$BUILD_DIR/stage.elf" "$BUILD_DIR/stage.o"
"$LLVM/llvm-objcopy" -O binary "$BUILD_DIR/stage.elf" "$BUILD_DIR/stage.bin"
"$LLVM/llvm-objdump" -d "$BUILD_DIR/loader.elf" > "$BUILD_DIR/loader.disasm.txt"
"$JAVA8_HOME/bin/java" -cp "$LEGACY_JAVAC" j2me.thirdparty.legacyjavac.Main -source 1.3 -target 1.1 -g:none -bootclasspath "$SDK_CLASSES" -d "$BUILD_DIR/classes" "${S40_JAVA_SOURCES[@]}" "$EXAMPLE_DIR"/src/*.java "$BUILD_DIR/FirmwareApi.java" "$BUILD_DIR/NativeBuildConfig.java"
python3 "$PROJECT_DIR/tools/preverify.py" "$JAVA8_HOME/bin/java" "$PROGUARD_JAR" "$SDK_CLASSES" "$BUILD_DIR/classes" "$BUILD_DIR/preverified"
"$JAVA8_HOME/bin/javap" -classpath "$BUILD_DIR/preverified" -c -p -v NativeSession > "$BUILD_DIR/NativeSession.javap.txt"
"$JAVA8_HOME/bin/javap" -classpath "$BUILD_DIR/preverified" -c -p -v FirmwareResolver > "$BUILD_DIR/FirmwareResolver.javap.txt"
cp "$BUILD_DIR/stage.bin" "$BUILD_DIR/native.bin" "$BUILD_DIR/application.bin" "$BUILD_DIR/preverified/"
python3 "$PROJECT_DIR/tools/package_midlet.py" "$PROJECT_DIR" "$EXAMPLE_DIR" "$BUILD_DIR" "$DIST_DIR" "$ARTIFACT"
