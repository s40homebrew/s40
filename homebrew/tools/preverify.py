#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Add CLDC StackMap attributes using ProGuard's Java ME preverifier.

Only preverification is enabled. Native entrypoint names, class/member order,
and the deliberate scan-loop bytecode must not be optimized or obfuscated.
Use the S40 SDK as the library API, never the host Java runtime classes.
"""

import subprocess
import sys
from pathlib import Path


def main():
    java, proguard_jar, sdk, source, output = sys.argv[1:]
    if not list(Path(source).rglob("*.class")):
        raise ValueError("No compiled classes to preverify")
    subprocess.run(
        [
            java,
            "-jar",
            proguard_jar,
            "-injars",
            source,
            "-outjars",
            output,
            "-libraryjars",
            sdk,
            "-dontshrink",
            "-dontoptimize",
            "-dontobfuscate",
            "-microedition",
            "-forceprocessing",
        ],
        check=True,
    )


if __name__ == "__main__":
    main()
