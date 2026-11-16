#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Copy unchanged booster classes and payloads into the separate diagnostic."""

import sys
import zipfile
from pathlib import Path


def main():
    jar, destination = map(Path, sys.argv[1:])
    with zipfile.ZipFile(jar) as archive:
        for entry in archive.infolist():
            if not entry.filename.endswith((".class", ".bin")):
                continue
            relative = Path(entry.filename)
            if relative.is_absolute() or ".." in relative.parts:
                raise ValueError("Archive entry is not a relative resource path")
            target = destination / relative
            if target.exists():
                raise ValueError("Diagnostic class collides with booster: " + entry.filename)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(entry))


if __name__ == "__main__":
    main()
