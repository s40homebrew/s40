#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Emit C constants from the Java/native wire contract."""

import re
import sys
from pathlib import Path


def main():
    source, destination = map(Path, sys.argv[1:])
    text = re.sub(r"/\*.*?\*/|//[^\n]*", "", source.read_text(), flags=re.S)
    declarations = re.findall(r"\bint\s+(\w+)\s*=\s*([^;]+);", text)
    assert declarations and len({name for name, _ in declarations}) == len(declarations)
    lines = ["/* Generated from HeapBoosterProtocol.java. Do not edit. */", "#pragma once"]
    for name, value in declarations:
        assert re.fullmatch(r"(?:0x[0-9a-fA-F]+|[0-9]+)", value.strip()), (name, value)
        lines.append(f"#define {name} {value.strip()}u")
    destination.write_text("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
