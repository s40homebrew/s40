#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
set -euo pipefail
ROOT=$(CDPATH='' cd -- "$(dirname -- "$0")/../.." && pwd)
BUILD=$(mktemp -d "${TMPDIR:-/tmp}/s40-video.XXXXXX")
trap 'rm -rf "$BUILD"' EXIT
python3 "$ROOT/tools/gen_imports.py" "$BUILD"
clang -std=c99 -Wall -Wextra -Werror -fsanitize=address,undefined -I"$ROOT/include" -I"$ROOT/src/native" -I"$BUILD" "$ROOT/tests/video/test_video.c" "$ROOT/src/native/display/video.c" -o "$BUILD/test_video"
"$BUILD/test_video"
clang -std=c99 -Wall -Wextra -Werror -fsanitize=address,undefined -I"$ROOT/include" -I"$ROOT/src/native" -I"$BUILD" "$ROOT/tests/video/test_display.c" "$ROOT/src/native/display/canvas.c" -o "$BUILD/test_display"
"$BUILD/test_display"
clang -std=c99 -Wall -Wextra -Werror -fsanitize=address,undefined -I"$ROOT/include" -I"$ROOT/src/native" -I"$BUILD" "$ROOT/tests/video/test_awake.c" "$ROOT/src/native/display/awake.c" -o "$BUILD/test_awake"
"$BUILD/test_awake"
clang -std=c99 -Wall -Wextra -Werror -fsanitize=address,undefined -I"$ROOT/include" -I"$ROOT/src/native" -I"$BUILD" "$ROOT/tests/video/test_pump.c" "$ROOT/src/native/core/session.c" -o "$BUILD/test_pump"
"$BUILD/test_pump"
printf 'Video scaling, DMA buffer ownership, dispatch and fallback checks passed.\n'
