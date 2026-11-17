#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Source lists for the shared launcher. Standalone firmware adapters are selected
# by their own builds, not pulled in by recursively compiling every C file.
S40_SOURCE_ROOT=$(CDPATH='' cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../src" && pwd)
S40_RESOLVER_JAVA_SOURCES=(
    "$S40_SOURCE_ROOT"/java/profiles/*.java
    "$S40_SOURCE_ROOT"/java/resolver/*.java
    "$S40_SOURCE_ROOT"/java/resolver/services/*.java
    "$S40_SOURCE_ROOT"/java/resolver/services/audio/*.java
)
S40_JAVA_SOURCES=(
    "$S40_SOURCE_ROOT"/java/launcher/*.java
    "${S40_RESOLVER_JAVA_SOURCES[@]}"
)

# Keep generated Java factories in each build, never in the source tree.
# Supplying signature files limits generation for smaller standalone utilities.
s40_generate_signatures() {
    local signature_output=$1
    shift
    python3 "$S40_SOURCE_ROOT/../tools/gen_signatures.py" --llvm "$LLVM" "$signature_output" "$@" || return
    S40_SIGNATURE_JAVA_SOURCES=("$signature_output"/*.java)
    test -f "${S40_SIGNATURE_JAVA_SOURCES[0]}" || {
        printf 'No generated firmware signatures in %s\n' "$signature_output" >&2
        return 1
    }
}

S40_NATIVE_SOURCES=(
    "$S40_SOURCE_ROOT"/native/core/*.c
    "$S40_SOURCE_ROOT"/native/display/*.c
    "$S40_SOURCE_ROOT"/native/display/rm443/*.c
    "$S40_SOURCE_ROOT"/native/input/*.c
    "$S40_SOURCE_ROOT"/native/libc/*.c
    "$S40_SOURCE_ROOT"/native/audio/*.c
    "$S40_SOURCE_ROOT"/native/audio/commgr/*.c
)
