#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Format owned source files, excluding generated output and caches.

Run with --check to validate style without changing files. Assembly, linker
scripts, manifests and prose keep their intentional/manual formatting.
"""

import argparse
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    excluded = {"build", "dist", "__pycache__", ".ruff_cache"}
    files = sorted(
        p
        for p in root.rglob("*")
        if p.is_file() and not excluded.intersection(p.relative_to(root).parts)
    )
    native = [str(p) for p in files if p.suffix in {".c", ".h", ".java"}]
    subprocess.run(
        ["clang-format", *(["--dry-run", "--Werror"] if args.check else ["-i"]), *native],
        check=True,
    )
    for path in files:
        if not path.name.endswith(".java.in"):
            continue
        original = path.read_text()
        formatted = subprocess.run(
            ["clang-format", "--assume-filename=" + str(path.with_suffix(""))],
            input=original,
            capture_output=True,
            text=True,
            check=True,
        ).stdout
        if args.check and original != formatted:
            raise SystemExit("Needs formatting: " + str(path))
        if not args.check:
            path.write_text(formatted)
    python = [str(p) for p in files if p.suffix == ".py"]
    subprocess.run(
        ["ruff", "check", "--select", "I", *([] if args.check else ["--fix"]), *python], check=True
    )
    subprocess.run(["ruff", "format", *(["--check"] if args.check else []), *python], check=True)


if __name__ == "__main__":
    main()
