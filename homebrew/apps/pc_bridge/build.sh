#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
set -euo pipefail
A=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
P=$(CDPATH='' cd -- "$A/../.." && pwd)
source "$P/../tools/env.sh"
export S40_APP_DIR="$A"
export S40_BUILD_DIR="${S40_BUILD_DIR:-$P/build/pc_bridge}"
export S40_DIST_DIR="${S40_DIST_DIR:-$P/dist/pc_bridge}"
# Validate/establish output ownership before generating retained-image output.
python3 "$P/../scripts/clean_build_outputs.py" java "$P" "$S40_BUILD_DIR" build/pc_bridge "$A"
python3 "$A/tools/build_resident.py"
bash "$P/build.sh" pc_bridge
python3 "$A/tools/check_package.py" "$S40_BUILD_DIR" "$S40_DIST_DIR"
