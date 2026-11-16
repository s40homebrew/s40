#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Source lists for the shared launcher. Standalone firmware adapters are selected
# by their own builds, not pulled in by recursively compiling every C file.
S40_SOURCE_ROOT=$(CDPATH='' cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../src" && pwd)
S40_JAVA_SOURCES=(
    "$S40_SOURCE_ROOT"/java/launcher/*.java
    "$S40_SOURCE_ROOT"/java/profiles/*.java
    "$S40_SOURCE_ROOT"/java/resolver/*.java
)
S40_NATIVE_SOURCES=(
    "$S40_SOURCE_ROOT"/native/core/*.c
    "$S40_SOURCE_ROOT"/native/display/*.c
    "$S40_SOURCE_ROOT"/native/display/rm443_1012/*.c
    "$S40_SOURCE_ROOT"/native/input/*.c
    "$S40_SOURCE_ROOT"/native/libc/*.c
    "$S40_SOURCE_ROOT"/native/audio/*.c
    "$S40_SOURCE_ROOT"/native/audio/commgr/*.c
    "$S40_SOURCE_ROOT/native/audio/devices/registry.c"
    "$S40_SOURCE_ROOT/native/audio/devices/rm217.c"
    "$S40_SOURCE_ROOT/native/audio/devices/rm443.c"
    "$S40_SOURCE_ROOT/native/audio/devices/rm614.c"
    "$S40_SOURCE_ROOT/native/audio/devices/rm813.c"
    "$S40_SOURCE_ROOT/native/audio/devices/rm839.c"
)
