#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Firmware-free checks of the packaged bootstrap and launcher.
set -euo pipefail
if [[ $# -ne 0 ]]; then
    printf '%s\n' 'Usage: check.sh (build ram_booster first)'
    [[ $# -eq 1 && ( $1 == --help || $1 == -h ) ]] && exit 0
    exit 2
fi
APP=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
SDK=$(CDPATH='' cd -- "$APP/../../.." && pwd)
source "$SDK/tools/env.sh"
if [[ ! -f "$SDK/homebrew/dist/ram_booster/S40RamBooster.jar" ]]; then
    printf '%s\n' 'Build first: bash homebrew/build.sh ram_booster' >&2
    exit 1
fi
python3 "$APP/tests/test_bootstrap.py"
python3 "$APP/tests/test_ui_flow.py"
