#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
set -euo pipefail
A=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
P=$(CDPATH='' cd -- "$A/../.." && pwd)
R=$(CDPATH='' cd -- "$P/.." && pwd)
APP=heap_check
ARTIFACT=S40HeapCheck
# shellcheck source=homebrew/tools/midlet_build.sh
source "$P/tools/midlet_build.sh"
SOURCES=("$A"/src/*.java)
compile_midlet
python3 "$P/tools/package_midlet.py" "$P" "$A" "$B" "$D" "$ARTIFACT"
