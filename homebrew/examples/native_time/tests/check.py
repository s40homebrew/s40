#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Check the built Java package and actual binding/restore code without firmware."""

import os
import struct
import subprocess
import sys
from pathlib import Path
from zipfile import ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "tests"))
from s40_test_paths import SDK_CLASSES, java_tool

project = Path(__file__).resolve().parents[1]
sdk = project.parents[1]
build = Path(os.environ.get("S40_BUILD_DIR", sdk / "build/native_time"))
dist = Path(os.environ.get("S40_DIST_DIR", sdk / "dist/native_time"))
jar = dist / "NativeTime.jar"
with ZipFile(jar) as archive:
    assert archive.testzip() is None
    for name in archive.namelist():
        if name.endswith(".class"):
            data = archive.read(name)
            assert data == (build / "preverified" / name).read_bytes()
            assert struct.unpack_from(">IHH", data) == (0xCAFEBABE, 3, 45)
            assert b"StackMap" in data
jad = (dist / "NativeTime.jad").read_text()
assert f"MIDlet-Jar-Size: {jar.stat().st_size}\n" in jad
assert "MIDlet-Jar-URL: NativeTime.jar\n" in jad

host = build / "host"
host.mkdir(exist_ok=True)
classpath = os.pathsep.join(map(str, (build / "preverified", host, SDK_CLASSES)))
subprocess.run(
    [java_tool("javac"), "-cp", classpath, "-d", str(host), str(project / "tests/CheckCarrier.java")],
    check=True,
)
subprocess.run(
    [java_tool("java"), "-Xverify:all", "-cp", classpath, "CheckCarrier", str(jar)],
    check=True,
)
print("PASS: CLDC package, JAD size and carrier binding/restoration")
