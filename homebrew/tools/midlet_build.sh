#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Sourced by an app's build.sh after setting P (homebrew), R (workspace),
# APP and ARTIFACT. Only compiler/preverification setup is shared here.
B=${S40_BUILD_DIR:-$P/build/$APP}
D=${S40_DIST_DIR:-$P/dist/$APP}
source "$R/tools/env.sh"
python3 "$R/tools/clean_build_outputs.py" java "$P" "$B" "build/$APP" "${A:-$P}"
mkdir -p "$B/classes" "$B/preverified" "$D"

compile_midlet() {
    "$JAVA8_HOME/bin/java" -cp "$LEGACY_JAVAC" j2me.thirdparty.legacyjavac.Main -source 1.3 -target 1.1 -g:none -bootclasspath "$SDK_CLASSES" -d "$B/classes" "${SOURCES[@]}"
    python3 "$P/tools/preverify.py" "$JAVA8_HOME/bin/java" "$PROGUARD_JAR" "$SDK_CLASSES" "$B/classes" "$B/preverified"
}
