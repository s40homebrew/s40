#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Generate matching Java/C loader budgets from the actual packed application."""

import argparse
import struct
from pathlib import Path


def limits(payload, exact=False):
    if len(payload) < 32:
        raise ValueError("truncated native header")
    magic, version, size, memory, entry, relocations, stack, bindings = struct.unpack_from(
        "<8I", payload
    )
    allocation = memory + 4 * relocations + 8 * bindings + stack
    if (
        magic != 0x30443453
        or version != 2
        or not 0 < size <= memory
        or memory & 3
        or entry >= size
        or relocations > 65536
        or stack != 196608
        or not 1 <= bindings <= 256
        or allocation > 0x7FFFFFFF
        or len(payload) != 32 + size + 4 * relocations + 8 * bindings
    ):
        raise ValueError("invalid native image layout")
    memory_limit, allocation_limit = (memory, allocation) if exact else (3 * 1024**2, 4 * 1024**2)
    if memory > memory_limit or allocation > allocation_limit:
        raise ValueError("application exceeds default budget; opt in to NATIVE_PAYLOAD_BUDGET=1")
    return memory_limit, allocation_limit


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("build", type=Path)
    parser.add_argument("--from-payload", action="store_true")
    args = parser.parse_args()
    memory, allocation = limits((args.build / "application.bin").read_bytes(), args.from_payload)
    (args.build / "native_limits.h").write_text(
        "/* Generated from checked S4D0 payload; SPDX-License-Identifier: MIT */\n"
        f"#define S40_NATIVE_MEMORY_LIMIT {memory}u\n"
        f"#define S40_NATIVE_ALLOCATION_LIMIT {allocation}u\n"
    )
    (args.build / "NativeBuildConfig.java").write_text(
        "/* Generated from checked S4D0 payload; SPDX-License-Identifier: MIT */\n"
        "final class NativeBuildConfig {\n"
        f"    static final int MEMORY_LIMIT = {memory};\n"
        f"    static final int ALLOCATION_LIMIT = {allocation};\n"
        "}\n"
    )
    print(f"Native loader budget: {memory} image bytes, {allocation} total bytes")


if __name__ == "__main__":
    main()
