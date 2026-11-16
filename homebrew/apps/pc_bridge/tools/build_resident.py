#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Build/relocate the retained bridge independently of the transient installer."""

import importlib.util
import os
import shutil
import struct
import subprocess
import sys
from pathlib import Path

APP = Path(__file__).resolve().parents[1]
HOMEBREW = APP.parents[1]
ROOT = HOMEBREW.parent
BUILD = Path(os.environ.get("S40_BUILD_DIR", HOMEBREW / "build/pc_bridge"))
RESIDENT = BUILD / "resident"


def run(*args):
    subprocess.run([str(x) for x in args], check=True)


def main():
    RESIDENT.mkdir(parents=True, exist_ok=True)
    found_llvm = shutil.which("llvm-objcopy")
    default_llvm = str(Path(found_llvm).parent) if found_llvm else "/opt/homebrew/opt/llvm/bin"
    llvm = Path(os.environ.get("LLVM", default_llvm))
    cc = os.environ.get("CC", str(llvm / "clang"))
    ld = os.environ.get("LD", shutil.which("ld.lld") or str(llvm / "ld.lld"))
    gcc = os.environ.get("ARM_GCC", shutil.which("arm-none-eabi-gcc") or "arm-none-eabi-gcc")
    libgcc = subprocess.check_output([gcc, "-print-libgcc-file-name"], text=True).strip()
    flags = [
        "-target",
        "arm-none-eabi",
        "-march=armv5te",
        "-marm",
        "-mfloat-abi=soft",
        "-O2",
        "-fPIC",
        "-fvisibility=hidden",
        "-ffreestanding",
        "-fno-builtin",
        "-fno-stack-protector",
        "-fno-unwind-tables",
        "-fno-asynchronous-unwind-tables",
        "-fno-strict-aliasing",
        "-fwrapv",
        "-ffunction-sections",
        "-fdata-sections",
        "-Wall",
        "-Wextra",
        "-Werror",
        f"-ffile-prefix-map={ROOT}=.",
        f"-I{APP / 'native'}",
    ]
    objects = []
    for name in ("resident.c", "call.S"):
        obj = RESIDENT / (Path(name).stem + ".o")
        run(cc, *flags, "-c", APP / "native" / name, "-o", obj)
        objects.append(obj)
    elf_path = RESIDENT / "resident.elf"
    run(
        ld,
        "-shared",
        "-Bsymbolic",
        "--no-undefined",
        "--gc-sections",
        "--exclude-libs=ALL",
        "-e",
        "resident_entry",
        "--hash-style=sysv",
        "-z",
        "max-page-size=4096",
        f"-Map={RESIDENT / 'resident.map'}",
        "-o",
        elf_path,
        *objects,
        libgcc,
        "-z",
        "notext",
    )
    spec = importlib.util.spec_from_file_location("s40pack", HOMEBREW / "tools/pack.py")
    pack = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = pack
    spec.loader.exec_module(pack)
    elf = elf_path.read_bytes()
    header = pack.ElfHeader(*struct.unpack_from("<16sHHIIIIIHHHHHH", elf))
    if header.kind != 3 or header.machine != 40 or header.ident[:7] != pack.ELF_IDENT:
        raise ValueError("Expected an ARM shared image")
    sections = [
        pack.Section(
            *struct.unpack_from("<10I", elf, header.section_offset + i * header.section_entry_bytes)
        )
        for i in range(header.section_count)
    ]
    image, memory = pack.load_image(elf, header)
    relocs = pack.relative_relocations(elf, sections, image, memory)
    if not 0 < header.entry < len(image) or memory > 1024 * 1024:
        raise ValueError("Invalid retained image bounds/relocations")
    output = [
        "/* Generated own-code retained bridge; no firmware data. */",
        f"#define PB_IMAGE_MEMORY {memory}u",
        f"#define PB_IMAGE_ENTRY {header.entry}u",
        "static const unsigned char pb_image[] = {",
    ]
    output.extend(
        "    " + ",".join(f"0x{x:02x}" for x in image[i : i + 16]) + ","
        for i in range(0, len(image), 16)
    )
    output += [
        "};",
        "static const unsigned pb_relocations[] = {",
        "    " + ",".join(str(x) for x in relocs),
        "};",
        "",
    ]
    (BUILD / "resident_image.h").write_text("\n".join(output))
    (RESIDENT / "resident.bin").write_bytes(image)
    (RESIDENT / "layout.txt").write_text(
        f"image={len(image)} memory={memory} entry={header.entry} relocations={len(relocs)}\n"
    )
    print(
        f"PC bridge resident: {len(image)} file bytes, {memory} RAM bytes, "
        f"{len(relocs)} relative relocations"
    )


if __name__ == "__main__":
    main()
