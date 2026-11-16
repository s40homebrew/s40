#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Execute retained ARM code, substituting only explicit firmware boundaries.

The real CM scheduler, allocator, transport and cache hardware are not emulated.
Run bash homebrew/build.sh pc_bridge before this offline check.
"""

import os
import struct
import sys
import unittest
from pathlib import Path

from unicorn import UC_ARCH_ARM, UC_HOOK_CODE, UC_MODE_ARM, Uc
from unicorn.arm_const import (
    UC_ARM_REG_LR,
    UC_ARM_REG_PC,
    UC_ARM_REG_R0,
    UC_ARM_REG_R1,
    UC_ARM_REG_R2,
    UC_ARM_REG_R3,
    UC_ARM_REG_R4,
    UC_ARM_REG_SP,
)

APP = Path(__file__).resolve().parents[1]
HOMEBREW = APP.parents[1]
BUILD = Path(os.environ.get("S40_BUILD_DIR", HOMEBREW / "build/pc_bridge"))
BASE, MESSAGE, REPLY, DATA, PAYLOAD = 0xD8400000, 0xD8600000, 0xD8610000, 0xD8620000, 0xD8630000
STOP, SYNC, STACK = 0xF0110000, 0xF0111000, 0xD8FF0000
ALLOC, FREE, NEW, DELETE, SEND, ORIGINAL = (
    0xF04F0340,
    0xF04EFA6E,
    0xF0B9B856,
    0xF0B9B868,
    0xF04ED2F2,
    0xF060FFC4,
)
REGS = (UC_ARM_REG_R0, UC_ARM_REG_R1, UC_ARM_REG_R2, UC_ARM_REG_R3)


def image():
    sys.path.insert(0, str(HOMEBREW / "tools"))
    import pack
    elf = (BUILD / "resident/resident.elf").read_bytes()
    header = pack.ElfHeader(*struct.unpack_from("<16sHHIIIIIHHHHHH", elf))
    sections = [
        pack.Section(
            *struct.unpack_from("<10I", elf, header.section_offset + i * header.section_entry_bytes)
        )
        for i in range(header.section_count)
    ]
    initialized, memory = pack.load_image(elf, header)
    result = bytearray(initialized) + bytearray(memory - len(initialized))
    for offset in pack.relative_relocations(elf, sections, initialized, memory):
        struct.pack_into("<I", result, offset, struct.unpack_from("<I", result, offset)[0] + BASE)
    return bytes(result), BASE + header.entry


class Firmware:
    def __init__(self):
        self.uc = Uc(UC_ARCH_ARM, UC_MODE_ARM)
        self.uc.mem_map(0xD8000000, 0x02000000)
        self.uc.mem_map(0xF0100000, 0x02000000)
        binary, entry = image()
        self.uc.mem_write(BASE, binary)
        self.sent, self.deleted, self.freed, self.forwarded, self.synchronized = [], [], [], [], []
        self.heap = 0xD8700000
        self.alloc_ok = self.message_ok = True
        self.uc.hook_add(UC_HOOK_CODE, self.boundary)
        self.control = self.call(entry)
        assert self.word(self.control) == 0x53343042
        self.write(self.control + 8, "<II", BASE, len(binary))
        self.write(self.control + 36, "<II", SYNC, 0x12345678)
        self.receiver = self.word(self.control + 16)
        # A transient loader/application region can disappear after installation.
        self.uc.mem_unmap(0xD8300000, 0x10000)

    def write(self, address, fmt, *values):
        self.uc.mem_write(address, struct.pack(fmt, *values))

    def word(self, address):
        return struct.unpack("<I", self.uc.mem_read(address, 4))[0]

    def args(self):
        return tuple(self.uc.reg_read(r) for r in REGS) + (
            self.word(self.uc.reg_read(UC_ARM_REG_SP)),
        )

    def boundary(self, uc, address, size, data):
        if address == STOP:
            uc.emu_stop()
            return
        result = 0
        if address == ALLOC:
            result = self.heap if self.alloc_ok else 0
            self.heap += (uc.reg_read(UC_ARM_REG_R0) + 15) & ~15
        elif address == FREE:
            self.freed.append(uc.reg_read(UC_ARM_REG_R0))
        elif address == NEW:
            requested = uc.reg_read(UC_ARM_REG_R0)
            assert requested <= 1042
            result = REPLY if self.message_ok else 0
            if result:
                self.uc.mem_write(REPLY - 4, bytes(2048))
        elif address == DELETE:
            self.deleted.append(uc.reg_read(UC_ARM_REG_R0))
        elif address == SEND:
            args = self.args()
            reply = args[4]
            length = struct.unpack("<H", uc.mem_read(reply - 4, 2))[0]
            self.sent.append((args[:4], bytes(uc.mem_read(reply, length - 2))))
        elif address == ORIGINAL:
            self.forwarded.append(self.args())
            result = 0xABCDEF01
        elif address == SYNC:
            self.synchronized.append(self.args()[:2])
        else:
            return
        uc.reg_write(UC_ARM_REG_R0, result)
        uc.reg_write(UC_ARM_REG_PC, uc.reg_read(UC_ARM_REG_LR))

    def call(self, address, *args):
        self.uc.reg_write(UC_ARM_REG_SP, STACK)
        self.uc.reg_write(UC_ARM_REG_LR, STOP)
        for reg, value in zip(REGS, args):
            self.uc.reg_write(reg, value)
        if len(args) > 4:
            self.write(STACK, "<I", args[4])
        self.uc.reg_write(UC_ARM_REG_R4, 0xA1B2C3D4)
        self.uc.emu_start(address, STOP + 4, count=250000)
        assert self.uc.reg_read(UC_ARM_REG_PC) == STOP, "ARM call exhausted instruction budget"
        assert self.uc.reg_read(UC_ARM_REG_SP) == STACK, "ARM call changed stack"
        assert self.uc.reg_read(UC_ARM_REG_R4) == 0xA1B2C3D4, (
            "ARM call changed callee-saved register"
        )
        return self.uc.reg_read(UC_ARM_REG_R0)

    def request(
        self, op, body=b"", version=1, declared=None, wire=None, magic=b"S40B", ident=0x0EE0
    ):
        count = len(body) if declared is None else declared
        message = bytes((0x75, 0xE0)) + magic + bytes((version, op))
        message += struct.pack(">H", count) + b"\x12\x34\x56\x78" + body
        self.write(MESSAGE - 4, "<H", len(message) + 2 if wire is None else wire)
        self.uc.mem_write(MESSAGE, message)
        before = len(self.sent)
        result = self.call(self.receiver, 0xD8010000, 0xC009, 0xD8020000, MESSAGE, ident)
        if len(self.sent) == before:
            return result, None
        args, reply = self.sent[-1]
        assert args == (0xD8020000, 0xC011, 0xD8010000, 14)
        assert reply[:8] == b"\x75\xe1S40B\x01" + bytes((op,))
        assert reply[10:14] == b"\x12\x34\x56\x78"
        assert struct.unpack(">H", reply[8:10])[0] == len(reply) - 14
        assert self.deleted[-1] == MESSAGE
        return struct.unpack_from("<I", reply, 14)[0], reply[18:]


class ResidentTests(unittest.TestCase):
    def setUp(self):
        self.f = Firmware()

    def test_ping_and_installer_lifetime(self):
        status, body = self.f.request(0)
        self.assertEqual(status, 0)
        self.assertEqual(
            struct.unpack("<8I", body),
            (
                1,
                0xFF,
                BASE,
                self.f.word(self.f.control + 12),
                0xD8000000,
                0xDA000000,
                1024,
                0x12345678,
            ),
        )
        self.assertEqual(self.f.word(self.f.control + 44), 1)

    def test_read_write_and_bounds(self):
        content = bytes(range(256)) * 4
        self.assertEqual(self.f.request(2, struct.pack("<II", DATA, 1024) + content), (0, b""))
        self.assertEqual(self.f.request(1, struct.pack("<II", DATA, 1024)), (0, content))
        for address, size in ((0xD7FFFFFF, 4), (0xD9FFFFFC, 8), (0xFFFFFFFC, 8)):
            self.assertEqual(self.f.request(1, struct.pack("<II", address, size))[0], 3)
        self.assertEqual(self.f.request(1, struct.pack("<II", DATA, 1025))[0], 1)
        self.assertEqual(self.f.request(2, struct.pack("<II", 0xF0110000, 1) + b"X")[0], 3)

    def test_arm_and_thumb_calls_capture_four_registers(self):
        self.f.write(PAYLOAD, "<5I", 0xE2800001, 0xE0211002, 0xE3A02055, 0xE3A030AA, 0xE12FFF1E)
        self.f.write(PAYLOAD + 32, "<5H", 0x3001, 0x4051, 0x2255, 0x23AA, 0x4770)
        for address in (PAYLOAD, (PAYLOAD + 32) | 1):
            status, body = self.f.request(3, struct.pack("<5I", address, 7, 0x12, 0x34, 0))
            self.assertEqual((status, struct.unpack("<4I", body)), (0, (8, 0x26, 0x55, 0xAA)))
        self.assertEqual(self.f.request(3, struct.pack("<5I", PAYLOAD + 2, 0, 0, 0, 0))[0], 3)

    def test_alloc_free_tracking_and_full_table(self):
        allocations = []
        for _ in range(16):
            status, body = self.f.request(4, struct.pack("<I", 32))
            self.assertEqual(status, 0)
            address = struct.unpack("<I", body)[0]
            allocations.append(address)
            self.assertEqual(bytes(self.f.uc.mem_read(address, 32)), bytes(32))
        self.assertEqual(self.f.request(4, struct.pack("<I", 32))[0], 5)
        self.assertEqual(self.f.request(5, struct.pack("<I", allocations[0])), (0, b""))
        self.assertEqual(self.f.request(5, struct.pack("<I", allocations[0]))[0], 5)
        self.assertEqual(self.f.request(5, struct.pack("<I", BASE))[0], 5)
        self.assertEqual(self.f.freed, allocations[:1])
        self.assertEqual(self.f.request(4, struct.pack("<I", 32))[0], 0)

    def test_alloc_failure_and_limits(self):
        self.f.alloc_ok = False
        self.assertEqual(self.f.request(4, struct.pack("<I", 32))[0], 4)
        for amount in (0, 0x100001):
            self.assertEqual(self.f.request(4, struct.pack("<I", amount))[0], 1)

    def test_allocations_list_tracks_live_owned_blocks(self):
        self.assertEqual(self.f.request(7), (0, b""))
        live = []
        for size in (32, 1024, 7):
            status, result = self.f.request(4, struct.pack("<I", size))
            self.assertEqual(status, 0)
            live.append((struct.unpack("<I", result)[0], size))
        expected = b"".join(struct.pack("<II", *block) for block in live)
        self.assertEqual(self.f.request(7), (0, expected))
        self.assertEqual(self.f.request(5, struct.pack("<I", live[1][0])), (0, b""))
        expected = b"".join(struct.pack("<II", *block) for block in (live[0], live[2]))
        self.assertEqual(self.f.request(7), (0, expected))
        self.assertEqual(self.f.request(7, b"X")[0], 1)

    def test_cache_and_invalid_requests(self):
        self.assertEqual(self.f.request(6, struct.pack("<II", DATA, 128)), (0, b""))
        self.assertEqual(self.f.synchronized, [(DATA, 128)])
        self.assertEqual(self.f.request(6, struct.pack("<II", DATA, 0))[0], 1)
        self.assertEqual(self.f.request(99)[0], 2)
        self.assertEqual(self.f.request(0, version=2)[0], 2)
        self.assertEqual(self.f.request(0, declared=1)[0], 1)
        self.assertEqual(self.f.request(1, struct.pack("<II", DATA, 4), wire=20)[0], 1)
        self.assertEqual(self.f.request(0, body=b"X")[0], 1)

    def test_foreign_message_forwards_five_arguments_and_return_value(self):
        for kwargs in ({"magic": b"TEST"}, {"ident": 0x0E03}, {"wire": 10}):
            self.assertEqual(self.f.request(0, **kwargs), (0xABCDEF01, None))
            self.assertEqual(
                self.f.forwarded[-1],
                (0xD8010000, 0xC009, 0xD8020000, MESSAGE, kwargs.get("ident", 0x0EE0)),
            )
        self.assertEqual(self.f.deleted, [])

    def test_reply_allocation_failure_deletes_request(self):
        self.f.message_ok = False
        self.assertEqual(self.f.request(0), (0, None))
        self.assertEqual(self.f.deleted, [MESSAGE])

    def test_detach_restores_both_bindings_without_freeing_callback(self):
        panel, record = 0xD8010000, 0xD8030000
        class_address = self.f.control + 48
        self.f.write(self.f.control + 20, "<II", panel, record)
        self.f.write(panel, "<I", class_address)
        self.f.write(record, "<III", panel, 0, self.f.receiver)
        status, result = self.f.request(4, struct.pack("<I", 64))
        self.assertEqual(status, 0)
        allocation = struct.unpack("<I", result)[0]
        self.assertEqual(self.f.request(8)[0], 6)
        self.assertEqual(self.f.word(panel), class_address)
        self.assertEqual(self.f.word(record + 8), self.f.receiver)
        self.assertEqual(self.f.request(5, struct.pack("<I", allocation)), (0, b""))
        replies = len(self.f.sent)
        self.assertEqual(self.f.request(8), (0, b""))
        self.assertEqual(len(self.f.sent), replies + 1)
        self.assertEqual(self.f.word(panel), 0xF11717C0)
        self.assertEqual(self.f.word(record + 8), ORIGINAL | 1)
        self.assertEqual(self.f.word(self.f.control), 0x53343042)
        self.assertEqual(self.f.freed, [allocation])


if __name__ == "__main__":
    unittest.main()
