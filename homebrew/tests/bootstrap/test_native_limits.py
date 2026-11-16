#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Execute Java and ARM allocation guards, including payloads above the old cap."""

import importlib.util
import os
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from unicorn import UC_ARCH_ARM, UC_HOOK_CODE, UC_MODE_ARM, Uc
from unicorn.arm_const import (
    UC_ARM_REG_LR,
    UC_ARM_REG_PC,
    UC_ARM_REG_R0,
    UC_ARM_REG_R1,
    UC_ARM_REG_SP,
)

ROOT = Path(__file__).resolve().parents[2]
GENERATOR = ROOT / "tools/gen_native_limits.py"
SPEC = importlib.util.spec_from_file_location("gen_native_limits", GENERATOR)
GEN = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GEN)


def payload(memory=5 * 1024**2):
    return struct.pack("<8I", 0x30443453, 2, 4, memory, 0, 0, 196608, 1) + bytes(12)


class NativeLimitsTest(unittest.TestCase):
    def test_budget_and_malformed_headers(self):
        self.assertEqual(GEN.limits(payload(1024)), (3145728, 4194304))
        with self.assertRaises(ValueError):
            GEN.limits(payload())
        self.assertEqual(GEN.limits(payload(), True), (5242880, 5439496))
        for broken in [b"", payload()[:-1], payload() + b"x", payload(0x7FFFFFFC)]:
            with self.assertRaises(ValueError):
                GEN.limits(broken, True)

    def test_actual_java_guard(self):
        java_home = Path(
            os.environ.get(
                "JAVA8_HOME", "/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home"
            )
        )
        javac = (
            str(java_home / "bin/javac")
            if (java_home / "bin/javac").exists()
            else shutil.which("javac")
        )
        java = str(Path(javac).with_name("java"))
        with tempfile.TemporaryDirectory() as tmp:
            work = Path(tmp)
            (work / "application.bin").write_bytes(payload())
            subprocess.run(
                ["python3", str(GENERATOR), tmp, "--from-payload"], check=True, capture_output=True
            )
            (work / "BoundsTest.java").write_text("""
public class BoundsTest {
    static void check(boolean b) { if (!b) throw new AssertionError(); }
    public static void main(String[] args) {
        check(NativeImageBounds.allocationBytes(4, 5242880, 0, 0, 196608, 1) == 5439496);
        check(NativeImageBounds.allocationBytes(4, 5242884, 0, 0, 196608, 1) == 0);
        check(NativeImageBounds.allocationBytes(4, 5242880, 0, 1, 196608, 1) == 0);
        check(NativeImageBounds.allocationBytes(4, 5242880, 4, 0, 196608, 1) == 0);
        check(NativeImageBounds.allocationBytes(-1, 5242880, 0, 0, 196608, 1) == 0);
        check(NativeImageBounds.allocationBytes(4, -4, 0, 0, 196608, 1) == 0);
        check(NativeImageBounds.allocationBytes(4, 2147483644, 0, 65536, 196608, 256) == 0);
    }
}
""")
            subprocess.run(
                [
                    javac,
                    "-d",
                    tmp,
                    str(work / "NativeBuildConfig.java"),
                    str(ROOT / "src/java/launcher/NativeImageBounds.java"),
                    str(work / "BoundsTest.java"),
                ],
                check=True,
            )
            subprocess.run([java, "-cp", tmp, "BoundsTest"], check=True)

    def test_actual_arm_allocation_guard(self):
        llvm = Path(os.environ.get("LLVM", "/opt/homebrew/opt/llvm/bin"))

        def tool(name):
            candidate = llvm / name
            return str(candidate) if candidate.is_file() else shutil.which(name)

        with tempfile.TemporaryDirectory() as tmp:
            work = Path(tmp)
            # Compile against the production ABI, including optional legacy
            # display bindings, rather than a hand-maintained partial header.
            subprocess.run([sys.executable, str(ROOT / "tools/gen_imports.py"), tmp], check=True)
            (work / "stub.S").write_text(
                '.syntax unified\n.arm\n.global on_native_stack\non_native_stack: bx lr\n.section .note.GNU-stack,"",%progbits\n'
            )
            for budget in [4194304, 5439496]:
                (work / "native_limits.h").write_text(
                    f"#define S40_NATIVE_ALLOCATION_LIMIT {budget}u\n"
                )
                subprocess.run(
                    [
                        tool("clang"),
                        "-target",
                        "arm-none-eabi",
                        "-march=armv5te",
                        "-marm",
                        "-Os",
                        "-ffreestanding",
                        "-fno-builtin",
                        "-fno-unwind-tables",
                        "-nostdlib",
                        "-Wl,-Ttext=0x10000",
                        "-Wl,-e,loader_command",
                        "-I",
                        tmp,
                        str(ROOT / "src/bootstrap/loader.c"),
                        str(work / "stub.S"),
                        "-o",
                        str(work / "loader.elf"),
                    ],
                    check=True,
                )
                subprocess.run(
                    [
                        tool("llvm-objcopy"),
                        "-O",
                        "binary",
                        str(work / "loader.elf"),
                        str(work / "loader.bin"),
                    ],
                    check=True,
                )
                symbols = subprocess.check_output(
                    [tool("llvm-nm"), str(work / "loader.elf")], text=True
                )
                entry = next(
                    int(line.split()[0], 16)
                    for line in symbols.splitlines()
                    if line.endswith(" loader_command")
                )
                for requested, expected in [
                    (0, 21),
                    (budget, 22),
                    (budget + 1, 21),
                    (0xFFFFFFFF, 21),
                ]:
                    uc = Uc(UC_ARCH_ARM, UC_MODE_ARM)
                    uc.mem_map(0x10000, 0x40000)
                    uc.mem_write(0x10000, (work / "loader.bin").read_bytes())
                    descriptor, api, allocator, stop = 0x20000, 0x22000, 0x30000, 0x31000
                    uc.mem_write(descriptor + 4, struct.pack("<I", 1))
                    uc.mem_write(descriptor + 44, struct.pack("<I", requested))
                    uc.mem_write(api + 8, struct.pack("<I", allocator))
                    calls = []

                    def hook(machine, address, size, user):
                        if address == allocator:
                            calls.append(machine.reg_read(UC_ARM_REG_R0))
                            machine.reg_write(UC_ARM_REG_R0, 0)  # allocator reports failure
                            machine.reg_write(UC_ARM_REG_PC, machine.reg_read(UC_ARM_REG_LR))

                    uc.hook_add(UC_HOOK_CODE, hook)
                    for reg, value in [
                        (UC_ARM_REG_R0, descriptor),
                        (UC_ARM_REG_R1, api),
                        (UC_ARM_REG_SP, 0x4F000),
                        (UC_ARM_REG_LR, stop),
                    ]:
                        uc.reg_write(reg, value)
                    uc.emu_start(entry, stop, count=10000)
                    self.assertEqual(
                        struct.unpack("<I", uc.mem_read(descriptor + 12, 4))[0], expected
                    )
                    self.assertEqual(calls, [requested] if expected == 22 else [])


if __name__ == "__main__":
    unittest.main()
