#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Execute SDK input/timing/path and launcher argument code on the host."""

import os
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


with tempfile.TemporaryDirectory(prefix="s40-services-") as temporary:
    build = Path(temporary)
    run(sys.executable, ROOT / "tools/gen_imports.py", build)
    run(
        os.environ.get("HOST_CC", "clang"),
        "-std=c99",
        "-Wall",
        "-Wextra",
        "-Werror",
        "-fsanitize=address,undefined",
        "-I" + str(ROOT / "include"),
        "-I" + str(ROOT / "src/native"),
        "-I" + str(build),
        HERE / "test_services.c",
        ROOT / "src/native/input/input.c",
        ROOT / "src/native/core/frame_clock.c",
        ROOT / "src/native/libc/path.c",
        "-o",
        build / "services",
    )
    run(build / "services")
    # Only the production objects use SDK libc headers. The fixture retains
    # host libc for its fake OS heap, so sanitizer allocations cannot recurse.
    objects = []
    for name in ("memory", "files", "path"):
        obj = build / (name + ".o")
        run(os.environ.get("HOST_CC", "clang"), "-std=c99", "-Wall", "-Wextra", "-Werror",
            "-fsanitize=address,undefined", "-fno-builtin",
            "-Dmalloc=sdk_malloc", "-Dcalloc=sdk_calloc", "-Drealloc=sdk_realloc", "-Dfree=sdk_free",
            "-Derrno=sdk_errno", "-I" + str(ROOT / "include/libc"),
            "-I" + str(ROOT / "include"), "-I" + str(ROOT / "src/native"),
            "-I" + str(build), "-c", ROOT / ("src/native/libc/" + name + ".c"), "-o", obj)
        objects.append(obj)
    run(os.environ.get("HOST_CC", "clang"), "-std=c99", "-Wall", "-Wextra", "-Werror",
        "-fsanitize=address,undefined", HERE / "test_files.c", *objects, "-o", build / "files")
    run(build / "files")
    java_home = os.environ.get("JAVA8_HOME") or os.environ.get("JAVA_HOME")
    javac = Path(java_home) / "bin/javac" if java_home else "javac"
    java = Path(java_home) / "bin/java" if java_home else "java"
    run(
        javac,
        "-d",
        build,
        build / "FirmwareApi.java",
        ROOT / "src/java/launcher/NativeLaunchArgument.java",
        ROOT / "src/java/launcher/FilePickerPaths.java",
        HERE / "CheckLaunchArgument.java",
        HERE / "CheckFilePickerPaths.java",
    )
    run(java, "-cp", build, "CheckLaunchArgument")
    run(java, "-cp", build, "CheckFilePickerPaths")
