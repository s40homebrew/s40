#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Exercise packaged method lookup, temporary payload writes and restoration."""

import json
import os
import shutil
import struct
import subprocess
import zipfile
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[3]
BUILD = PROJECT / "build/ram_booster"
JAR = PROJECT / "dist/ram_booster/S40RamBooster.jar"


def main():
    javac = (
        str(Path(os.environ["JAVA8_HOME"]) / "bin/javac")
        if "JAVA8_HOME" in os.environ
        else shutil.which("javac")
    )
    if not javac:
        raise SystemExit("Set JAVA8_HOME or put a JDK on PATH")
    java = str(Path(javac).with_name("java"))
    sdk = os.environ.get(
        "SDK_CLASSES", str(PROJECT.parent / "tools/.deps/S40-SDK-Classes/S40v6/classes/classes.zip")
    )
    output = BUILD / "host-check"
    output.mkdir(parents=True, exist_ok=True)
    layout = json.loads((BUILD / "carrier_layout.json").read_text())
    carrier = bytes.fromhex("1aab0000") + struct.pack(">II", 111, 10)
    for i, word in enumerate(layout["words"]):
        carrier += struct.pack("<I", word) + struct.pack(">I", 91 + i * 2)
    carrier += bytes.fromhex("03ac") * 10 + bytes.fromhex("04ac")
    storage = bytes.fromhex("1aab0000") + struct.pack(">II", 12301, 1536)
    storage += b"".join(struct.pack(">II", i << 16, 12299) for i in range(1, 1537))
    storage += bytes.fromhex("03ac04ac")
    with zipfile.ZipFile(JAR) as archive:
        packaged = archive.read("HeapBoosterSession.class")
    for name, body in (("carrier.bin", carrier), ("payload_space.bin", storage)):
        assert packaged.count(body) == 1, name
        # Feed the installed bytes to the Java harness, including compiler output.
        start = packaged.index(body)
        (output / name).write_bytes(packaged[start : start + len(body)])
    classpath = os.pathsep.join((str(JAR), sdk, str(output)))
    subprocess.run(
        [
            javac,
            "-cp",
            classpath,
            "-d",
            str(output),
            str(Path(__file__).with_name("CheckHeapBootstrap.java")),
        ],
        check=True,
    )
    subprocess.run(
        [
            java,
            "-cp",
            classpath,
            "CheckHeapBootstrap",
            str(output / "carrier.bin"),
            str(output / "payload_space.bin"),
        ],
        check=True,
    )


if __name__ == "__main__":
    main()
