# SPDX-License-Identifier: MIT
"""SDK and Java locations for host tests."""

import os
import shutil
from pathlib import Path

SDK_ROOT = Path(os.environ.get("S40_SDK_ROOT", Path(__file__).resolve().parents[2])).resolve()
SDK_CLASSES = Path(
    os.environ.get(
        "SDK_CLASSES", SDK_ROOT / "tools/.deps/S40-SDK-Classes/S40v6/classes/classes.zip"
    )
)


def java_tool(name):
    """Respect the selected JDK, otherwise use the host Java tool on PATH."""
    if os.environ.get("JAVA8_HOME"):
        return str(Path(os.environ["JAVA8_HOME"]) / "bin" / name)
    command = shutil.which(name)
    if command:
        return command
    raise FileNotFoundError(f"Set JAVA8_HOME or put {name} on PATH")
