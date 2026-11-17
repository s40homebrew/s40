#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
set -euo pipefail
A=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
P=$(CDPATH='' cd -- "$A/../.." && pwd)
R=$(CDPATH='' cd -- "$P/.." && pwd)
APP=native_time
ARTIFACT=NativeTime
source "$P/tools/midlet_build.sh"
source "$P/tools/sources.sh"
# Exact instruction templates retain this utility's existing decoding rules.
s40_generate_signatures "$B/signatures" "$A"/signatures/*.S
SOURCES=("$A"/src/*.java "$P/src/java/resolver/RomSymbols.java" "${S40_SIGNATURE_JAVA_SOURCES[@]}")
compile_midlet
python3 "$P/tools/package_midlet.py" "$P" "$A" "$B" "$D" "$ARTIFACT"
