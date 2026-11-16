#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Pack a position-independent ARM ELF as the loader's S4D0 v2 image.

The stream is: header, initialized image, R_ARM_RELATIVE offsets, import pairs.
Uninitialized memory is allocated by the native loader. Import slots stay zero
until the handset resolver supplies the selected firmware's bindings.
"""

import json
import struct
import sys
from pathlib import Path
from typing import NamedTuple

from native_contract import IMAGE_MAGIC, IMAGE_STACK_BYTES, IMAGE_VERSION, WORD_BYTES

ELF_IDENT = b"\x7fELF\x01\x01\x01"  # ELF32, little-endian, current version
ET_DYN = 3
EM_ARM = 40
PT_LOAD = 1
SHT_SYMTAB = 2
SHT_REL = 9
R_ARM_RELATIVE = 23
SHN_UNDEF = 0
# Public module aliases retained for callers that inspect packed images.
S4D0_MAGIC = IMAGE_MAGIC
S4D0_VERSION = IMAGE_VERSION
NATIVE_STACK_BYTES = IMAGE_STACK_BYTES


class ElfHeader(NamedTuple):
    ident: bytes
    kind: int
    machine: int
    version: int
    entry: int
    program_offset: int
    section_offset: int
    flags: int
    header_bytes: int
    program_entry_bytes: int
    program_count: int
    section_entry_bytes: int
    section_count: int
    string_section: int


class Section(NamedTuple):
    name: int
    kind: int
    flags: int
    address: int
    offset: int
    size: int
    link: int
    info: int
    alignment: int
    entry_bytes: int


def align_word(value):
    return (value + WORD_BYTES - 1) & ~(WORD_BYTES - 1)


def read_word(data, offset):
    return struct.unpack_from("<I", data, offset)[0]


def load_image(elf, header):
    loads = []
    for index in range(header.program_count):
        offset = header.program_offset + index * header.program_entry_bytes
        kind, file_offset, address, _, file_bytes, memory_bytes, _, _ = struct.unpack_from(
            "<8I", elf, offset
        )
        if kind == PT_LOAD:
            loads.append((file_offset, address, file_bytes, memory_bytes))
    assert min(address for _, address, _, _ in loads) == 0
    memory_bytes = align_word(max(address + size for _, address, _, size in loads))
    file_bytes = align_word(max(address + size for _, address, size, _ in loads))
    image = bytearray(file_bytes)
    for offset, address, size, _ in loads:
        image[address : address + size] = elf[offset : offset + size]
    return image, memory_bytes


def relative_relocations(elf, sections, image, memory_bytes):
    relocations = []
    for section in sections:
        if section.kind != SHT_REL:
            continue
        for position in range(section.offset, section.offset + section.size, section.entry_bytes):
            offset, info = struct.unpack_from("<II", elf, position)
            # r_info must contain ONLY R_ARM_RELATIVE, with symbol index zero.
            assert info == R_ARM_RELATIVE, f"unsupported relocation {info:#x} at {offset:#x}"
            assert offset % WORD_BYTES == 0 and offset + WORD_BYTES <= len(image)
            addend = read_word(image, offset)
            assert (addend & ~1) < memory_bytes, f"non-image relative addend {addend:#x}"
            relocations.append(offset)
    assert len(set(relocations)) == len(relocations)
    return relocations


def defined_symbols(elf, sections):
    symbols = {}
    for section in sections:
        if section.kind != SHT_SYMTAB:
            continue
        strings = sections[section.link]
        for position in range(section.offset, section.offset + section.size, section.entry_bytes):
            name, value, _, _, _, section_index = struct.unpack_from("<IIIBBH", elf, position)
            if section_index == SHN_UNDEF:
                continue
            start = strings.offset + name
            finish = elf.index(b"\0", start)
            symbols[elf[start:finish].decode()] = value
    return symbols


def import_bindings(imports, symbols, image, relocations):
    bindings = []
    for binding in imports["bindings"]:
        if binding["symbol"] not in symbols:
            continue  # The linker discards unused veneers.
        offset = symbols[binding["symbol"]]
        assert offset % WORD_BYTES == 0 and offset + WORD_BYTES <= len(image)
        assert offset not in relocations and read_word(image, offset) == 0
        bindings.append((offset, binding["api"]))
    assert bindings and len({offset for offset, _ in bindings}) == len(bindings)
    return bindings


def main():
    build_dir = Path(sys.argv[1])
    elf = (build_dir / "application.elf").read_bytes()
    header = ElfHeader(*struct.unpack_from("<16sHHIIIIIHHHHHH", elf))
    assert header.ident[:7] == ELF_IDENT and header.kind == ET_DYN and header.machine == EM_ARM
    sections = [
        Section(
            *struct.unpack_from(
                "<10I", elf, header.section_offset + index * header.section_entry_bytes
            )
        )
        for index in range(header.section_count)
    ]
    image, memory_bytes = load_image(elf, header)
    relocations = relative_relocations(elf, sections, image, memory_bytes)
    assert 0 < header.entry < len(image) and relocations
    imports = json.loads((build_dir / "imports.json").read_text())
    bindings = import_bindings(imports, defined_symbols(elf, sections), image, relocations)
    packed_header = struct.pack(
        "<8I",
        S4D0_MAGIC,
        S4D0_VERSION,
        len(image),
        memory_bytes,
        header.entry,
        len(relocations),
        NATIVE_STACK_BYTES,
        len(bindings),
    )
    binding_data = b"".join(struct.pack("<II", *pair) for pair in bindings)
    (build_dir / "application.bin").write_bytes(
        packed_header + image + struct.pack(f"<{len(relocations)}I", *relocations) + binding_data
    )
    (build_dir / "bindings.json").write_text(json.dumps(bindings, indent=2) + "\n")
    print(
        f"Native image: {len(image)} file bytes, {memory_bytes} memory bytes, "
        f"{len(relocations)} relative relocations, {len(bindings)} runtime bindings; "
        f"entry {header.entry:#x}"
    )


if __name__ == "__main__":
    main()
