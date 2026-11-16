#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Small host Java checks against a built application; no firmware inputs.
set -euo pipefail
PROJECT_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")/../.." && pwd)
WORKSPACE_DIR=$(CDPATH='' cd -- "$PROJECT_DIR/.." && pwd)
source "$WORKSPACE_DIR/tools/env.sh"
EXAMPLE=${1:-hello}
case "$EXAMPLE" in ''|*[!a-z0-9_-]*) exit 1 ;; esac
if [[ $EXAMPLE == doom ]]; then
    BUILD_DIR=${S40_BUILD_DIR:-$WORKSPACE_DIR/../doom/s40/shared/build}
else
    BUILD_DIR=${S40_BUILD_DIR:-$PROJECT_DIR/build/$EXAMPLE}
fi
test -f "$BUILD_DIR/preverified/NativeSession.class" || { printf 'Build %s first.\n' "$EXAMPLE" >&2; exit 1; }
mkdir -p "$BUILD_DIR/host-check"
"$JAVA8_HOME/bin/javac" -cp "$BUILD_DIR/preverified" -d "$BUILD_DIR/host-check" "$PROJECT_DIR"/tests/resolver/*.java "$PROJECT_DIR/tests/bootstrap/CheckStagePlacement.java" "$PROJECT_DIR/tests/bootstrap/CheckLaunchProgress.java"
"$JAVA8_HOME/bin/java" -cp "$BUILD_DIR/host-check:$BUILD_DIR/preverified:$SDK_CLASSES" CheckStagePlacement "$BUILD_DIR/native.bin"
"$JAVA8_HOME/bin/java" -cp "$BUILD_DIR/preverified:$BUILD_DIR/host-check" CheckProfiles
"$JAVA8_HOME/bin/java" -cp "$BUILD_DIR/preverified:$BUILD_DIR/host-check" CheckSharedDiscovery
"$JAVA8_HOME/bin/java" -cp "$BUILD_DIR/host-check:$BUILD_DIR/preverified:$SDK_CLASSES" CheckLaunchProgress
