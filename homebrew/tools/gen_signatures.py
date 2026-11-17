#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Assemble annotated firmware signatures into Java factories or C arrays."""

import argparse
import os
import re
import shlex
import shutil
import struct
import subprocess
import tempfile
from dataclasses import dataclass, field
from pathlib import Path


@dataclass
class Statement:
    assembly: str
    mask: str
    location: str


@dataclass
class Signature:
    symbol: str
    kind: str
    mode: str
    name: str
    statements: list[Statement] = field(default_factory=list)
    anchor: int | None = None
    alignment: int = 2


def parse(path):
    """Metadata lives in ARM comments; instruction syntax belongs to LLVM MC."""
    signatures = []
    current = None
    for number, raw in enumerate(path.read_text().splitlines(), 1):
        line = raw.strip()
        location = f"{path.name}:{number}"
        if line.startswith("@ pattern "):
            if current is not None:
                raise ValueError(f"{location}: nested pattern")
            fields = shlex.split(line[len("@ pattern "):])
            if len(fields) != 4:
                raise ValueError(f"{location}: expected SYMBOL recipe|pattern|bytes|halfwords|words thumb|arm|data NAME")
            symbol, kind, mode, name = fields
            if not re.fullmatch(r"[A-Za-z_][A-Za-z_0-9]*", symbol):
                raise ValueError(f"{location}: invalid symbol name")
            if kind not in ("recipe", "pattern", "bytes", "halfwords", "words") or mode not in ("thumb", "arm", "data"):
                raise ValueError(f"{location}: invalid pattern type or instruction set")
            if any(s.symbol == symbol for s in signatures):
                raise ValueError(f"{location}: duplicate pattern {symbol}")
            if not re.fullmatch(r"[A-Za-z_][A-Za-z_0-9]*", name):
                raise ValueError(f"{location}: invalid diagnostic name")
            current = Signature(symbol, kind, mode, name, alignment=2 if mode == "thumb" else 4)
        elif line == "@ end":
            if current is None or not current.statements:
                raise ValueError(f"{location}: pattern needs instructions")
            if current.kind in ("recipe", "pattern") and current.anchor is None:
                raise ValueError(f"{location}: masked recipe needs one anchor")
            signatures.append(current)
            current = None
        elif line == "@ anchor":
            if current is None or current.anchor is not None:
                raise ValueError(f"{location}: anchor outside pattern or duplicate anchor")
            if current.kind in ("bytes", "halfwords", "words"):
                raise ValueError(f"{location}: plain arrays do not use anchors")
            current.anchor = len(current.statements)
        elif not line or line.startswith("@"):
            continue
        else:
            if current is None:
                raise ValueError(f"{location}: instruction outside pattern")
            assembly, _, comment = line.partition("@")
            match = re.search(r"\bmask=([^\s]+)", comment)
            # One instruction/data directive per statement makes mask widths explicit.
            if ";" in assembly or ":" in assembly:
                raise ValueError(f"{location}: use one instruction per line, without labels")
            if assembly.lstrip().startswith(".") and not re.match(r"\s*\.(?:byte|hword|word|space)\s", assembly):
                raise ValueError(f"{location}: unsupported assembly directive")
            current.statements.append(Statement(assembly.strip(), match[1] if match else "exact", location))
    if current is not None:
        raise ValueError(f"{path.name}: unterminated pattern")
    if not signatures:
        raise ValueError(f"{path.name}: no patterns")
    return signatures


def elf_contents(data):
    """Read the ELF32 sections/symbols we emit; reject unresolved relocations."""
    if data[:6] != b"\x7fELF\x01\x01":
        raise ValueError("assembler did not produce little-endian ELF32")
    section_offset = struct.unpack_from("<I", data, 32)[0]
    entry_size, count, strings_index = struct.unpack_from("<HHH", data, 46)
    headers = [struct.unpack_from("<10I", data, section_offset + i * entry_size) for i in range(count)]
    contents = [data[h[4]:h[4] + h[5]] for h in headers]
    def string(table, offset):
        return table[offset:table.index(0, offset)].decode()
    sections = {string(contents[strings_index], h[0]): contents[i] for i, h in enumerate(headers)}
    symbols = {}
    for i, h in enumerate(headers):
        if h[1] in (4, 9) and h[5]:
            raise ValueError("unresolved assembly relocation; use numeric PC-relative displacements")
        if h[1] == 2:
            for offset in range(0, h[5], h[9]):
                name, value, _, _, _, section = struct.unpack_from("<IIIBBH", contents[i], offset)
                if section:
                    symbols[string(contents[h[6]], name)] = value
    return sections, symbols


def mask_words(statement, words, mode):
    policy = statement.mask
    size = len(words)
    if policy == "exact":
        return [0xffff] * size
    if policy == "ignore":
        return [0] * size
    if policy == "call":
        # Thumb BL or BLX: keep the two-halfword call shape, ignore ISA choice.
        if mode != "thumb" or size != 2 or words[0] & 0xf800 != 0xf000 or words[1] & 0xe800 != 0xe800:
            raise ValueError(f"{statement.location}: call mask needs Thumb BL/BLX")
        return [0xf800, 0xe800]
    if policy == "target":
        if mode == "thumb":
            if size == 2 and words[0] & 0xf800 == 0xf000 and words[1] & 0xe800 == 0xe800:
                return [0xf800, 0xf800]
            if size == 1 and words[0] & 0xf800 == 0xe000:
                return [0xf800]
            if size == 1 and words[0] & 0xf000 == 0xd000 and words[0] & 0x0f00 < 0x0e00:
                return [0xff00]
        elif mode == "arm" and size == 2 and words[1] & 0x0e00 == 0x0a00:
            # BLX immediate encodes displacement bit 1 as H (bit 24).
            # B/BL keep their condition and link bit; BLX keeps its ISA change.
            return [0, 0xfe00 if words[1] & 0xfe00 == 0xfa00 else 0xff00]
        raise ValueError(f"{statement.location}: target mask needs a direct branch/call")
    if policy == "literal":
        if mode == "thumb" and size == 1 and words[0] & 0xf800 in (0x4800, 0xa000):
            return [0xff00]
        if mode == "arm" and size == 2 and words[1] & 0x0f7f == 0x051f:
            return [0xf000, 0xffff]
        raise ValueError(f"{statement.location}: literal mask needs a PC-relative LDR/ADR")
    if policy == "imm8":
        if mode == "thumb" and size == 1 and words[0] & 0xe000 == 0x2000:
            return [0xff00]
        raise ValueError(f"{statement.location}: imm8 mask needs Thumb MOV/CMP/ADD/SUB immediate")
    try:
        masks = [int(value, 0) for value in policy.split(",")]
    except ValueError as error:
        raise ValueError(f"{statement.location}: unknown mask policy {policy}") from error
    if len(masks) != size or any(m < 0 or m > 0xffff for m in masks):
        raise ValueError(f"{statement.location}: mask must contain one 16-bit value per halfword")
    return masks


def assemble(signatures, llvm):
    lines = [".syntax unified", ".fpu vfpv3"]
    for i, signature in enumerate(signatures):
        lines += [f'.section .s40_signature_{i},"a",%progbits', ".arm" if signature.mode == "arm" else ".thumb"]
        for j, statement in enumerate(signature.statements):
            lines += [f"s40_sig_{i}_{j}:", statement.assembly]
        lines.append(f"s40_sig_{i}_{len(signature.statements)}:")
    with tempfile.TemporaryDirectory(prefix="s40-signatures-") as temporary:
        output = Path(temporary) / "signatures.o"
        result = subprocess.run([str(llvm / "llvm-mc"), "-triple=armv6-none-eabi", "-filetype=obj", "-o", str(output)],
                                input="\n".join(lines) + "\n", text=True, capture_output=True)
        if result.returncode:
            # MC numbers the generated translation unit; also identify its source statement.
            line_sources = {}
            line_number = 2
            for signature in signatures:
                line_number += 2
                for statement in signature.statements:
                    line_number += 2
                    line_sources[line_number] = statement.location
                line_number += 1
            diagnostic = re.sub(r"<stdin>:(\d+)", lambda m: line_sources.get(int(m[1]), m[0]), result.stderr)
            raise ValueError(diagnostic.strip())
        sections, symbols = elf_contents(output.read_bytes())
    recipes = []
    for i, signature in enumerate(signatures):
        data = sections[f".s40_signature_{i}"]
        unit = 1 if signature.kind == "bytes" else 2
        if not data or len(data) % unit:
            raise ValueError(f"{signature.symbol}: pattern must contain whole halfwords")
        if signature.kind == "words" and len(data) % 4:
            raise ValueError(f"{signature.symbol}: word array must contain whole 32-bit words")
        words, masks = [], []
        for j, statement in enumerate(signature.statements):
            first, last = symbols[f"s40_sig_{i}_{j}"], symbols[f"s40_sig_{i}_{j+1}"]
            if first % unit or last <= first or (last - first) % unit:
                raise ValueError(f"{statement.location}: statement must emit whole halfwords")
            encoded = list(struct.unpack("<" + ("B" if unit == 1 else "H") * ((last-first)//unit), data[first:last]))
            if signature.kind in ("bytes", "halfwords", "words") and statement.mask != "exact":
                raise ValueError(f"{statement.location}: plain arrays require exact bytes, without masks")
            mask = mask_words(statement, encoded, signature.mode)
            words.extend(w & m for w, m in zip(encoded, mask))
            masks.extend(mask)
        anchor = None
        if signature.anchor is not None:
            anchor = symbols[f"s40_sig_{i}_{signature.anchor}"]
            if anchor + 4 > len(data) or anchor % 2 or masks[anchor//2:anchor//2+2] != [0xffff, 0xffff]:
                raise ValueError(f"{signature.symbol}: anchor must cover four exact bytes")
        recipes.append((signature, anchor, words, masks))
    return recipes


def raw_values(signature, units):
    """Convert the assembler's byte/halfword units to exact output elements."""
    if signature.kind == "words":
        return [units[i] | units[i+1] << 16 for i in range(0, len(units), 2)], 32
    return units, 8 if signature.kind == "bytes" else 16


def java_source(class_name, recipes):
    lines = ["/* SPDX-License-Identifier: MIT */", "/* Generated by gen_signatures.py; edit the assembly signature source. */",
             f"final class {class_name} {{", f"    private {class_name}() {{}}"]
    for signature, anchor, words, masks in recipes:
        if signature.kind in ("bytes", "halfwords", "words"):
            values, bits = raw_values(signature, words)
            width = bits // 4
            lines += [f"    static int[] {signature.symbol}() {{", "        return new int[] {"]
            for offset in range(0, len(values), 8):
                lines.append("            " + ", ".join(f"0x{v:0{width}x}" for v in values[offset:offset+8]) + ",")
            lines += ["        };", "    }"]
            continue
        kind = "RomRecipe" if signature.kind == "recipe" else "FirmwareResolver.Pattern"
        anchor_word = words[anchor//2] | words[anchor//2+1] << 16
        lines += [f"    static {kind} {signature.symbol}() {{",
                  f'        return new {kind}("{signature.name}", {signature.alignment}, {anchor}, 0x{anchor_word:08x},']
        for index, values in enumerate((words, masks)):
            lines.append("                new int[] {")
            for offset in range(0, len(values), 10):
                lines.append("                    " + ", ".join(f"0x{v:04x}" for v in values[offset:offset+10]) + ",")
            lines.append("                }" + ("," if index == 0 else ");"))
        lines += ["    }"]
    return "\n".join(lines + ["}", ""])


def c_source(header_name, recipes):
    guard = f"S40_SIGNATURES_{header_name}_H"
    lines = ["/* SPDX-License-Identifier: MIT */",
             "/* Generated by gen_signatures.py; edit the assembly signature source. */",
             f"#ifndef {guard}", f"#define {guard}", "", "#include <stdint.h>", ""]
    for signature, _, units, _ in recipes:
        if signature.kind not in ("bytes", "halfwords", "words"):
            raise ValueError(f"{signature.symbol}: C output requires bytes, halfwords or words")
        values, bits = raw_values(signature, units)
        lines.append(f"static const uint{bits}_t {signature.symbol}[] = {{")
        for offset in range(0, len(values), 8):
            lines.append("    " + ", ".join(f"0x{v:0{bits//4}x}u" for v in values[offset:offset+8]) + ",")
        lines += ["};", ""]
    return "\n".join(lines + [f"#endif /* {guard} */", ""])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("sources", type=Path, nargs="*")
    parser.add_argument("--llvm", type=Path, default=None, help="directory containing llvm-mc")
    parser.add_argument("--format", choices=("java", "c"), default="java", help="generated source language (default: java)")
    args = parser.parse_args()
    llvm = args.llvm or (Path(os.environ["LLVM"]) if "LLVM" in os.environ else None)
    if llvm is None:
        executable = shutil.which("llvm-mc")
        llvm = Path(executable).parent if executable else Path("/opt/homebrew/opt/llvm/bin")
    sources = args.sources or sorted((Path(__file__).resolve().parents[1] / "src/firmware/signatures").rglob("*.S"))
    if not sources:
        parser.error("no signature sources")
    generated = {}
    try:
        for path in sources:
            if not re.fullmatch(r"[A-Za-z_][A-Za-z_0-9]*", path.stem) or path.stem in generated:
                raise ValueError(f"{path.name}: invalid or duplicate output name")
            emit = java_source if args.format == "java" else c_source
            generated[path.stem] = emit(path.stem, assemble(parse(path), llvm))
    except (ValueError, OSError) as error:
        parser.exit(1, f"Signature generation failed: {error}\n")
    args.output.mkdir(parents=True, exist_ok=True)
    extension = ".java" if args.format == "java" else ".h"
    for old in args.output.glob("*" + extension):
        if old.stem not in generated:
            old.unlink()
    for name, contents in generated.items():
        (args.output / (name + extension)).write_text(contents)


if __name__ == "__main__":
    main()
