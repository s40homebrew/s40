#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Relocate the compiler from a user-supplied JDK 1.4.2 tools.jar.

Only package names change. A separate namespace prevents Java 8's compiler
classes from shadowing the compiler that emits the required CLDC bytecode.
The input compiler retains its original license; it is not distributed here.
"""

import argparse
import struct
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

OLD = b"com/sun/tools/javac"
NEW = b"j2me/thirdparty/legacyjavac"


def relocate(data):
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("Not a Java class")
    count = struct.unpack_from(">H", data, 8)[0]
    result = bytearray(data[:10])
    offset, index = 10, 1
    sizes = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4}
    while index < count:
        tag = data[offset]
        result.append(tag)
        offset += 1
        if tag == 1:
            size = struct.unpack_from(">H", data, offset)[0]
            offset += 2
            value = data[offset:offset + size]
            value = value.replace(OLD, NEW).replace(OLD.replace(b"/", b"."), NEW.replace(b"/", b"."))
            result.extend(struct.pack(">H", len(value)))
            result.extend(value)
        else:
            size = sizes[tag]
            result.extend(data[offset:offset + size])
            if tag in (5, 6):
                index += 1
        offset += size
        index += 1
    result.extend(data[offset:])
    return bytes(result)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tools_jar", type=Path)
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / ".deps/legacy-javac.jar")
    args = parser.parse_args()
    with ZipFile(args.tools_jar) as source:
        if "com/sun/tools/javac/v8/Main.class" not in source.namelist():
            parser.error("Supply lib/tools.jar from JDK 1.4.2, not a newer JDK")
        entries = {}
        for name in source.namelist():
            if name.startswith(OLD.decode() + "/") and not name.endswith("/"):
                data = source.read(name)
                entries[name.replace(OLD.decode(), NEW.decode())] = relocate(data) if name.endswith(".class") else data
            elif any(word in name.lower() for word in ("license", "copyright", "notice")) and not name.endswith("/"):
                entries[name] = source.read(name)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(args.output, "w", ZIP_DEFLATED) as output:
        for name, data in sorted(entries.items()):
            output.writestr(name, data)
    print("Prepared", args.output)


if __name__ == "__main__":
    main()
