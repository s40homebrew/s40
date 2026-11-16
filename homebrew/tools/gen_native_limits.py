#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Generate matching Java/C loader budgets from the actual packed application."""

import argparse
import struct
from pathlib import Path

from native_contract import (
    BINDING_BYTES,
    IMAGE_HEADER_BYTES,
    IMAGE_MAGIC,
    IMAGE_MAX_BINDINGS,
    IMAGE_MAX_RELOCATIONS,
    IMAGE_STACK_BYTES,
    IMAGE_VERSION,
    RELOCATION_BYTES,
    WORD_BYTES,
)


def limits(payload, exact=False):
    if len(payload) < IMAGE_HEADER_BYTES:
        raise ValueError("truncated native header")
    magic, version, size, memory, entry, relocations, stack, bindings = struct.unpack_from(
        "<8I", payload
    )
    allocation = memory + RELOCATION_BYTES * relocations + BINDING_BYTES * bindings + stack
    if (
        magic != IMAGE_MAGIC
        or version != IMAGE_VERSION
        or not 0 < size <= memory
        or memory & (WORD_BYTES - 1)
        or entry >= size
        or relocations > IMAGE_MAX_RELOCATIONS
        or stack != IMAGE_STACK_BYTES
        or not 1 <= bindings <= IMAGE_MAX_BINDINGS
        or allocation > 0x7FFFFFFF
        or len(payload)
        != IMAGE_HEADER_BYTES + size + RELOCATION_BYTES * relocations + BINDING_BYTES * bindings
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
