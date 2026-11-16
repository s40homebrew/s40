#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Check install metadata, preverified classes and distribution privacy."""

import getpass
import re
import struct
import sys
from pathlib import Path
from zipfile import ZipFile


def main():
    build, dist = map(Path, sys.argv[1:])
    jar = dist / "S40PcBridge.jar"
    username = getpass.getuser().encode()
    # Do not flag innocent words containing the username, such as endianness.
    user_pattern = rb"(?<![A-Za-z0-9_])" + re.escape(username) + rb"(?![A-Za-z0-9_])"
    with ZipFile(jar) as archive:
        if archive.testzip() is not None:
            raise ValueError("Broken JAR archive")
        for name in archive.namelist():
            data = archive.read(name)
            if b"/Users/" in data or b"/home/" in data or re.search(user_pattern, data):
                raise ValueError("Builder identity leaked into JAR member " + name)
            if name.endswith(".class"):
                if struct.unpack_from(">IHH", data) != (0xCAFEBABE, 3, 45):
                    raise ValueError("Unexpected CLDC class version: " + name)
                if data != (build / "preverified" / name).read_bytes():
                    raise ValueError("Packaged class differs from preverifier output: " + name)
        manifest = archive.read("META-INF/MANIFEST.MF").decode("ascii")
        if "MIDlet-1: S40 PC Bridge,,PcBridgeInstaller\r\n" not in manifest:
            raise ValueError("Wrong bridge MIDlet entry point")
    jad = (dist / "S40PcBridge.jad").read_text()
    if "MIDlet-Jar-URL: S40PcBridge.jar\n" not in jad:
        raise ValueError("Wrong JAD JAR URL")
    if f"MIDlet-Jar-Size: {jar.stat().st_size}\n" not in jad:
        raise ValueError("JAD size differs from JAR")
    print("PC bridge package: CLDC classes, JAD and builder-identity checks passed")


if __name__ == "__main__":
    main()
