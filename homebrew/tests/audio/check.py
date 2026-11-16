#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Exercise maintained PCM ownership, pacing and profile gates without firmware."""

import os
import subprocess
import sys
import tempfile
from pathlib import Path

sdk = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix="s40-audio-") as temporary:
    build = Path(temporary)
    subprocess.run([sys.executable, str(sdk / "tools/gen_imports.py"), str(build)], check=True)
    for name, source, definitions in [
        ("stream-rm839", "test_stream.c", []),
        ("stream-rm443", "test_stream.c", ["-DTEST_RM443"]),
        ("stream-legacy", "test_stream.c", ["-DTEST_RM443", "-DTEST_LEGACY"]),
        ("clock", "test_clock.c", []),
        ("device", "test_device.c", []),
    ]:
        binary = build / name
        subprocess.run(
            [
                os.environ.get("CC", "clang"),
                "-std=c99",
                "-Wall",
                "-Wextra",
                "-Werror",
                "-fsanitize=address,undefined",
                "-I" + str(sdk / "include"),
                "-I" + str(sdk / "src/native"),
                "-I" + str(build),
                *definitions,
                str(Path(__file__).with_name(source)),
                *(
                    [
                        str(path)
                        for path in (sdk / "src/native/audio/devices").glob("rm[0-9][0-9][0-9].c")
                    ]
                    if source != "test_clock.c"
                    else []
                ),
                *(
                    [
                        str(sdk / "src/native/audio" / path)
                        for path in ("core.c", "device.c", "commgr/driver.c", "devices/registry.c")
                    ]
                    if source == "test_device.c"
                    else []
                ),
                "-o",
                str(binary),
            ],
            check=True,
        )
        subprocess.run([str(binary)], check=True)
