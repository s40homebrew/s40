#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
set -euo pipefail
A=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
P=$(CDPATH='' cd -- "$A/../../.." && pwd)
R=$(CDPATH='' cd -- "$P/.." && pwd)
APP=ram_booster_probe
ARTIFACT=S40BoosterProbe
BOOSTER_JAR=${BOOSTER_JAR:-$P/dist/ram_booster/S40RamBooster.jar}
if [[ ! -f "$BOOSTER_JAR" ]]; then
    echo "Build ram_booster first; this probe packages its exact class bytes." >&2
    exit 1
fi
# shellcheck source=homebrew/tools/midlet_build.sh
source "$P/tools/midlet_build.sh"
# Use the existing classes as compile/preverification libraries. Do not run
# the original booster classes through another transformation or rebuild.
SDK_CLASSES="$SDK_CLASSES:$BOOSTER_JAR"
SOURCES=("$A"/src/*.java)
"$JAVA8_HOME/bin/java" -cp "$LEGACY_JAVAC" j2me.thirdparty.legacyjavac.Main -source 1.3 -target 1.1 -g:none -bootclasspath "$SDK_CLASSES" -d "$B/classes" "${SOURCES[@]}"
# The diagnostic is deliberately in the original package and accesses its
# package-private members. ProGuard otherwise skips non-public library members.
"$JAVA8_HOME/bin/java" -jar "$PROGUARD_JAR" -injars "$B/classes" -outjars "$B/preverified" -libraryjars "$SDK_CLASSES" -dontskipnonpubliclibraryclasses -dontskipnonpubliclibraryclassmembers -dontshrink -dontoptimize -dontobfuscate -microedition -forceprocessing
python3 "$A/package_classes.py" "$BOOSTER_JAR" "$B/preverified"
python3 "$P/tools/package_midlet.py" "$P" "$A" "$B" "$D" "$ARTIFACT"
