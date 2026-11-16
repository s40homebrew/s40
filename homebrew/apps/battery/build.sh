#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
set -euo pipefail
APP_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH='' cd -- "$APP_DIR/../.." && pwd)
export S40_APP_DIR="$APP_DIR"
exec bash "$PROJECT_DIR/build.sh" battery
