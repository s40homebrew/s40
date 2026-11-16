#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
set -euo pipefail
PROJECT=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
ROOT=$(CDPATH='' cd -- "$PROJECT/.." && pwd)
source "$ROOT/tools/env.sh"
JAVA="$JAVA8_HOME/bin/java"
BUILD="$PROJECT/build"
DIST="$PROJECT/dist"
for dependency in "$LEGACY_JAVAC" "$PROGUARD_JAR" "$SDK_CLASSES"; do
    test -f "$dependency" || { printf 'Missing dependency: %s\n' "$dependency" >&2; exit 1; }
done
python3 "$ROOT/tools/clean_build_outputs.py" java "$PROJECT" "$BUILD" build "$PROJECT"
mkdir -p "$BUILD/classes" "$BUILD/preverified" "$DIST"
"$JAVA" -cp "$LEGACY_JAVAC" j2me.thirdparty.legacyjavac.Main -source 1.3 -target 1.1 -g:none -bootclasspath "$SDK_CLASSES" -d "$BUILD/classes" "$PROJECT"/src/*.java
"$JAVA" -jar "$PROGUARD_JAR" -injars "$BUILD/classes" -outjars "$BUILD/preverified" -libraryjars "$SDK_CLASSES" -dontshrink -dontoptimize -dontobfuscate -microedition -forceprocessing
python3 - "$PROJECT" <<'PY'
import sys
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

project = Path(sys.argv[1])
manifest = (project / 'src/MANIFEST.MF').read_text().strip() + '\n\n'
jar = project / 'dist/NativeTime.jar'
with ZipFile(jar, 'w', ZIP_DEFLATED) as out:
    out.writestr('META-INF/MANIFEST.MF', manifest.replace('\n', '\r\n'))
    out.write(project / 'COPYING', 'META-INF/LICENSE.txt')
    for path in sorted((project / 'build/preverified').rglob('*.class')):
        out.write(path, path.relative_to(project / 'build/preverified').as_posix())
jad = '\n'.join(manifest.splitlines()[1:]).strip() + '\n'
jad += 'MIDlet-Jar-URL: NativeTime.jar\nMIDlet-Jar-Size: %d\n' % jar.stat().st_size
(project / 'dist/NativeTime.jad').write_bytes(jad.replace('\n', '\r\n').encode('ascii'))
print('Built %s (%d bytes)' % (jar, jar.stat().st_size))
PY
