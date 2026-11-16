#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Execute actual installer and resident ARM with explicit firmware stubs."""

import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from test_resident import APP, BUILD, HOMEBREW, ORIGINAL, SYNC, Firmware
from unicorn import UC_HOOK_MEM_WRITE
from unicorn.arm_const import UC_ARM_REG_LR, UC_ARM_REG_PC, UC_ARM_REG_R0

CODE, RESULT = 0xD8200000, 0xD8100000
PANEL, CONTEXT, RECORD = 0xD8010000, 0xD8020000, 0xD8030010
TASK, ERROR, TIME = 0xF0ADAAE0, 0xF0112000, 0xF0113000
ORIGINAL_CLASS = 0xF11717C0


def compile_installer(directory):
    directory = Path(directory)
    llvm = Path(os.environ.get("LLVM", "/opt/homebrew/opt/llvm/bin"))
    cc = os.environ.get("CC", str(llvm / "clang"))
    ld = os.environ.get("LD", shutil.which("ld.lld") or str(llvm / "ld.lld"))
    fixture = directory / "fixture.c"
    fixture.write_text("""
typedef unsigned u32;
u32* s40_result = (u32*)0xd8100000;
void* fw_os_block_alloc_no_wait(unsigned n) {
    return ((void* (*)(unsigned))0xf04f0341)(n);
}
void fw_os_block_dealloc(void* p) { ((void (*)(void*))0xf04efa6f)(p); }
u32 fw_os_current_time_read_in_ms(void) { return ((u32 (*)(void))0xf0113000)(); }
__attribute__((noreturn)) void s40_error(const char* text, ...) {
    ((void (*)(const char*))0xf0112000)(text);
    for (;;) {}
}
""")
    linker = directory / "fixture.ld"
    linker.write_text("""
ENTRY(s40_app_main)
SECTIONS {
  . = 0xd8200000;
  .text : { *(.text*) }
  .rodata : { *(.rodata*) }
  .data : { *(.data*) }
  .bss : { *(.bss*) *(COMMON) }
  /DISCARD/ : { *(.ARM.exidx*) *(.ARM.extab*) *(.comment*) *(.note*) }
}
""")
    objects = []
    for index, source in enumerate((APP / "native/install.c", fixture)):
        obj = directory / f"{index}.o"
        subprocess.run(
            [
                cc,
                "-target",
                "arm-none-eabi",
                "-march=armv5te",
                "-marm",
                "-mfloat-abi=soft",
                "-O2",
                "-ffreestanding",
                "-fno-builtin",
                "-fno-stack-protector",
                "-fno-unwind-tables",
                "-fno-asynchronous-unwind-tables",
                "-I" + str(APP / "native"),
                "-I" + str(BUILD),
                "-I" + str(HOMEBREW / "src/native"),
                "-I" + str(HOMEBREW / "include"),
                "-I" + str(HOMEBREW / "include/libc"),
                "-c",
                str(source),
                "-o",
                str(obj),
            ],
            check=True,
        )
        objects.append(str(obj))
    elf, binary = directory / "installer.elf", directory / "installer.bin"
    subprocess.run([ld, "-T", str(linker), "-o", str(elf), *objects], check=True)
    subprocess.run([str(llvm / "llvm-objcopy"), "-O", "binary", str(elf), str(binary)], check=True)
    names = subprocess.check_output([str(llvm / "llvm-nm"), str(elf)], text=True)
    entry = next(
        int(line.split()[0], 16)
        for line in names.splitlines()
        if len(line.split()) == 3 and line.split()[2] == "s40_app_main"
    )
    return binary.read_bytes(), entry


class InstallerError(Exception):
    pass


class Installer(Firmware):
    def __init__(self, binary, entry):
        super().__init__()
        self.uc.mem_write(CODE, binary)
        self.entry = entry
        self.publications = []
        self.allocations = []
        self.task_lookups = 0
        self.write(0xD80F0658, "<I", PANEL)
        self.write(PANEL, "<I", ORIGINAL_CLASS)
        self.write(PANEL + 8, "<HH", 0x600, 1)
        self.write(0xD80CC9F0, "<B", 0x10)
        self.write(0xD80CD36C + 5 * 4, "<I", CONTEXT)
        self.write(CONTEXT + 4, "<I", RECORD - 16)
        self.write(CONTEXT + 20, "<H", 2)
        self.write(RECORD, "<IIIHBB", PANEL, 0, ORIGINAL | 1, 0x600, 1, 0)
        self.write(ORIGINAL_CLASS, "<I", ORIGINAL_CLASS)
        self.write(ORIGINAL, "<I", 0xB0A9B5FF)
        self.write(RESULT + 4 * (1120 + 24), "<I", SYNC)
        self.uc.hook_add(UC_HOOK_MEM_WRITE, self.publication)

    def boundary(self, uc, address, size, data):
        if address == ERROR:
            pointer = uc.reg_read(UC_ARM_REG_R0)
            message = bytes(uc.mem_read(pointer, 128)).split(b"\0", 1)[0].decode()
            raise InstallerError(message)
        if address in (TASK, TIME):
            if address == TASK:
                self.task_lookups += 1
            uc.reg_write(UC_ARM_REG_R0, 5 if address == TASK else 0x12345678)
            uc.reg_write(UC_ARM_REG_PC, uc.reg_read(UC_ARM_REG_LR))
            return
        if address == 0xF04F0340:
            self.allocations.append(uc.reg_read(UC_ARM_REG_R0))
        super().boundary(uc, address, size, data)

    def publication(self, uc, access, address, size, value, data):
        if address not in (PANEL, RECORD + 8):
            return
        self.publications.append((address, value))
        if address == PANEL:
            # Memory hooks observe before the store: retained image and cache
            # synchronization must already be complete at first publication.
            assert self.word(PANEL) == ORIGINAL_CLASS
            assert self.word(RECORD + 8) == ORIGINAL | 1
            control = self.word(value + 24)
            assert self.word(control) == 0x53343042
            assert self.word(control + 20) == PANEL and self.word(control + 24) == RECORD
            assert self.word(control + 36) == SYNC
            assert self.synchronized == [(self.word(control + 8), self.word(control + 12))]
        else:
            assert self.word(PANEL) != ORIGINAL_CLASS
            control = self.word(self.word(PANEL) + 24)
            assert value == self.word(control + 16)

    def install(self):
        return self.call(self.entry)


class InstallerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory(prefix="s40-bridge-installer-")
        cls.binary, cls.entry = compile_installer(cls.directory.name)

    @classmethod
    def tearDownClass(cls):
        cls.directory.cleanup()

    def setUp(self):
        self.f = Installer(self.binary, self.entry)

    def test_install_publication_and_repeat(self):
        self.assertEqual(self.f.install(), 0)
        self.assertEqual(self.f.word(RESULT + 20), 1)
        self.assertEqual(self.f.word(RESULT + 32), 0x53343042)
        self.assertEqual([address for address, value in self.f.publications], [PANEL, RECORD + 8])
        self.assertEqual(len(self.f.allocations), 1)
        self.assertEqual(self.f.install(), 0)
        self.assertEqual(self.f.word(RESULT + 20), 2)
        self.assertEqual(len(self.f.allocations), 1)
        self.assertEqual(len(self.f.publications), 2)

    def reject_unchanged(self):
        original = self.f.word(PANEL), self.f.word(RECORD + 8)
        with self.assertRaises(InstallerError):
            self.f.install()
        self.assertEqual((self.f.word(PANEL), self.f.word(RECORD + 8)), original)
        self.assertEqual(self.f.publications, [])

    def test_unknown_class(self):
        self.f.write(PANEL, "<I", 0xD8040000)
        self.reject_unchanged()
        self.assertEqual(self.f.allocations, [])

    def test_foreign_cached_callback(self):
        self.f.write(RECORD + 8, "<I", 0xF0118001)
        self.reject_unchanged()
        self.assertEqual(self.f.allocations, [])

    def test_wrong_rom_before_task_lookup(self):
        for address in (ORIGINAL, ORIGINAL_CLASS):
            with self.subTest(address=hex(address)):
                self.f = Installer(self.binary, self.entry)
                self.f.write(address, "<I", 0xE12FFF1E)
                self.reject_unchanged()
                self.assertEqual(self.f.task_lookups, 0)
                self.assertEqual(self.f.allocations, [])

    def test_invalid_record_identity(self):
        self.f.write(RECORD + 12, "<H", 0x1234)
        self.reject_unchanged()
        self.assertEqual(self.f.allocations, [])

    def test_allocation_failure_does_not_publish(self):
        self.f.alloc_ok = False
        self.reject_unchanged()
        self.assertEqual(len(self.f.allocations), 1)
        self.assertEqual(self.f.synchronized, [])


if __name__ == "__main__":
    unittest.main()
