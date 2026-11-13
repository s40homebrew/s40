#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Fetch build-only dependencies into tools/.deps (never into an app JAR)."""

import subprocess
import urllib.request
from pathlib import Path
from zipfile import ZipFile

DEPS = Path(__file__).resolve().parent / ".deps"
SDK_REVISION = "decec09b9479214ce790ed3ad2e9e07ef084bdc6"
PROGUARD_VERSION = "7.10.0"


def main():
    DEPS.mkdir(parents=True, exist_ok=True)
    sdk = DEPS / "S40-SDK-Classes"
    if not sdk.exists():
        subprocess.run(["git", "clone", "https://github.com/0x726564/S40-SDK-Classes.git", str(sdk)], check=True)
        subprocess.run(["git", "-C", str(sdk), "checkout", "--detach", SDK_REVISION], check=True)
    else:
        revision = subprocess.check_output(["git", "-C", str(sdk), "rev-parse", "HEAD"], text=True).strip()
        if revision != SDK_REVISION:
            raise SystemExit("SDK dependency is at another revision; use SDK_CLASSES or a fresh tools/.deps directory")
    directory = "proguard-" + PROGUARD_VERSION
    if not (DEPS / directory / "lib/proguard.jar").is_file():
        archive = DEPS / (directory + ".zip")
        url = f"https://github.com/Guardsquare/proguard/releases/download/v{PROGUARD_VERSION}/{directory}.zip"
        print("Downloading", url, flush=True)
        urllib.request.urlretrieve(url, archive)
        with ZipFile(archive) as source:
            for entry in source.infolist():
                path = Path(entry.filename)
                if path.is_absolute() or ".." in path.parts:
                    raise ValueError("Unsafe archive path")
            source.extractall(DEPS)
    print("SDK classes and ProGuard ready. See docs/building.md for JDK 8 and the legacy compiler.")


if __name__ == "__main__":
    main()
