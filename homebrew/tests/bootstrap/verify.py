#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Execute the compiled carrier and loader with modeled firmware services.

Checks self-copy, binding/relocation, private stack and cleanup. No firmware,
game data or research workspace is needed. Build an application first and pass
its build directory; the default is homebrew/build/hello.
"""

import json
import struct
import sys
from pathlib import Path

from unicorn import UC_ARCH_ARM, UC_HOOK_CODE, UC_MODE_ARM, Uc
from unicorn.arm_const import (
    UC_ARM_REG_LR,
    UC_ARM_REG_PC,
    UC_ARM_REG_R0,
    UC_ARM_REG_R1,
    UC_ARM_REG_R4,
    UC_ARM_REG_R11,
    UC_ARM_REG_SP,
)

BUILD_DIR = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / "build/hello"
META = None
IDS = None

# These are independently declared wire-format expectations, not values read
# back from the packer under test. Word indices match bootstrap/loader.c.
GAME_STACK_BYTES = 192 * 1024
LOADER_MAX_BYTES = 3072
PATCH_TAG_MASK = 0xFFFF0000
PATCH_TAG = 0xA40F0000
RESULT_MAGIC = 0x444F4F4D
ARRAY_HEADER_BYTES = 12
TRANSFER_WORD = 96
API_WORD = 1120
D_MAGIC, D_COMMAND, D_IMAGE, D_STATUS = 0, 1, 2, 3
D_ALLOCATION_BYTES, D_OFFSET, D_SIZE, D_ENTRY, D_BINDING_COUNT = 11, 12, 13, 14, 15
D_METHOD_SLOT, D_OBSERVED_SP, D_LOADER_SOURCE, D_LOADER_BYTES = 80, 81, 82, 83
D_RESIDENT_LOADER, D_CLOCK_RESULT = 84, 86
OP_INSTALL, OP_ALLOCATE, OP_COPY, OP_RUN, OP_ABORT = 0, 1, 2, 3, 4
STATUS_OK, STATUS_COPY_BOUNDS, STATUS_RELOCATION, STATUS_BINDING = 0, 23, 25, 27
MOCK_CLOCK_MS = 12345
FIXTURE_IMAGE_BYTES = 4096


def pack(*values):
    return struct.pack(f"<{len(values)}I", *(value & 0xFFFFFFFF for value in values))


def u32(data, offset=0):
    return struct.unpack_from("<I", data, offset)[0]


def carrier_body():
    """Check compiled carrier bytecode, independently of launcher constants."""
    words = [
        0xE0194280,
        0xE01D4790,
        0xE0154798,
        0xE0094800,
        0xE0014900,
        0xE7ED4A00,
        0xE7F51C04,
        0xE7EDB510,
        0x0000BD10,
        0xE7E11C23,
    ]
    expected = bytearray(bytes.fromhex("1aab0000") + struct.pack(">II", 111, 10))
    for i, word in enumerate(words):
        expected += pack(word) + struct.pack(">I", 91 + 2 * i)
    expected += bytes.fromhex("03ac") * 10 + bytes.fromhex("04ac")
    code = (BUILD_DIR / "preverified/NativeSession.class").read_bytes()
    assert len(expected) == 114 and code.count(expected) == 1, "Carrier bytecode changed"
    return bytes(expected)


def loader_tests():
    emulator = Uc(UC_ARCH_ARM, UC_MODE_ARM)
    emulator.mem_map(0x100000, 0x900000)
    # API values have no relationship to the actual ROM's addresses.
    targets = {name: 0x880001 + 16 * i for name, i in IDS.items()}
    targets["cache_sync"] &= ~3
    code = bytearray((BUILD_DIR / "native.bin").read_bytes())
    assert len(code) % 4 == 0 and len(code) <= LOADER_MAX_BYTES
    patched = []
    for off in range(0, len(code), 4):
        v = u32(code, off)
        if v & PATCH_TAG_MASK == PATCH_TAG:
            index = v & 65535
            assert index < len(IDS)
            name = META["entries"][index][0]
            struct.pack_into("<I", code, off, targets[name])
            patched.append(name)
    assert set(patched) == {
        "kni_param",
        "kni_return",
        "cache_sync",
        "fw_os_block_alloc_no_wait",
        "fw_os_block_dealloc",
    }
    ORIG = 0x100000
    OBJ = 0x200000
    DESC = OBJ + ARRAY_HEADER_BYTES
    SLOT = 0x210000
    STOP = 0x220000
    SP = 0x9FF000
    emulator.mem_write(ORIG, bytes(code))
    emulator.mem_write(SLOT, pack(ORIG))
    emulator.mem_write(STOP, pack(0xE1200070))

    def set_descriptor_word(i, v):
        emulator.mem_write(DESC + 4 * i, pack(v))

    def descriptor_word(i):
        return u32(emulator.mem_read(DESC + 4 * i, 4))

    for name, index in IDS.items():
        set_descriptor_word(API_WORD + index, targets[name])
    allocations = {}
    freed = []
    cache = []
    next_addr = 0x300000
    kni = []
    clock = []
    by_address = {address & ~1: name for name, address in targets.items()}

    def hook(emulator, a, size, user):
        nonlocal next_addr
        name = by_address.get(a)
        if name == "kni_param":
            assert emulator.reg_read(UC_ARM_REG_R0) == 1
            emulator.mem_write(emulator.reg_read(UC_ARM_REG_R1), pack(OBJ))
        elif name == "kni_return":
            kni.append(1)
        elif name == "fw_os_block_alloc_no_wait":
            n = emulator.reg_read(UC_ARM_REG_R0)
            addr = next_addr
            next_addr += (n + 4095) & ~4095
            assert next_addr < 0x800000
            allocations[addr] = n
            emulator.reg_write(UC_ARM_REG_R0, addr)
        elif name == "fw_os_block_dealloc":
            p = emulator.reg_read(UC_ARM_REG_R0)
            assert p in allocations
            freed.append(p)
            size = allocations.pop(p)
            emulator.mem_write(p, b"\xff" * size)  # poison the entire freed allocation
        elif name == "cache_sync":
            cache.append((emulator.reg_read(UC_ARM_REG_R0), emulator.reg_read(UC_ARM_REG_R1)))
        elif name == "fw_os_current_time_read_in_ms":
            clock.append(1)
            emulator.reg_write(UC_ARM_REG_R0, MOCK_CLOCK_MS)
        else:
            raise AssertionError("Unexpected mock call " + str(name))
        emulator.reg_write(UC_ARM_REG_PC, emulator.reg_read(UC_ARM_REG_LR))

    emulator.hook_add(UC_HOOK_CODE, hook, begin=0x880000, end=0x88FFFF)

    def call(addr, op):
        set_descriptor_word(D_COMMAND, op)
        emulator.reg_write(UC_ARM_REG_SP, SP)
        emulator.reg_write(UC_ARM_REG_LR, STOP)
        for r in range(UC_ARM_REG_R4, UC_ARM_REG_R11 + 1):
            emulator.reg_write(r, 0xAAAA0000 + r)
        emulator.emu_start(addr, STOP, count=10000000)
        assert emulator.reg_read(UC_ARM_REG_PC) == STOP and emulator.reg_read(UC_ARM_REG_SP) == SP
        assert all(
            emulator.reg_read(r) == 0xAAAA0000 + r for r in range(UC_ARM_REG_R4, UC_ARM_REG_R11 + 1)
        )
        assert descriptor_word(D_MAGIC) == RESULT_MAGIC

    def install(use_carrier=False):
        emulator.mem_write(ORIG, bytes(code))
        emulator.mem_write(SLOT, pack(ORIG))
        set_descriptor_word(D_METHOD_SLOT, SLOT)
        set_descriptor_word(D_LOADER_SOURCE, ORIG)
        set_descriptor_word(D_LOADER_BYTES, len(code))
        if use_carrier:
            # The exact Java switch is executable Thumb. Its literals are
            # data reads, so changing them does not require an I-cache flush.
            original = carrier_body()
            patched_carrier = bytearray(original)
            for offset, value in [(40, ORIG), (48, len(code)), (56, targets["cache_sync"])]:
                struct.pack_into("<I", patched_carrier, offset, value)
            carrier_address = 0x230000
            emulator.mem_write(carrier_address, bytes(patched_carrier))
            emulator.mem_write(SLOT, pack(carrier_address + 13))
            call(carrier_address + 13, OP_INSTALL)
            assert cache[0] == (ORIG, len(code)), "Carrier cache arguments"
            for offset in (40, 48, 56):
                emulator.mem_write(carrier_address + offset, original[offset : offset + 4])
            assert bytes(emulator.mem_read(carrier_address, len(original))) == original
        else:
            call(ORIG, OP_INSTALL)
        own = descriptor_word(D_RESIDENT_LOADER)
        assert own and u32(emulator.mem_read(SLOT, 4)) == own and (own, len(code)) in cache
        emulator.mem_write(ORIG, b"\xff" * len(code))
        set_descriptor_word(D_ALLOCATION_BYTES, FIXTURE_IMAGE_BYTES + 4 + 8 + GAME_STACK_BYTES)
        call(own, OP_ALLOCATE)
        base = descriptor_word(D_IMAGE)
        assert base
        return own, base

    own, base = install(use_carrier=True)
    # ARM callee records its stack, calls a bound Thumb clock and stores result.
    payload = pack(
        0xE1A0100D,
        0xE5801144,
        0xE92D4010,
        0xE1A04000,
        0xE59FC010,
        0xE12FFF3C,
        0xE5840158,
        0xE8BD8010,
        0,
        12,
        0,
    )
    emulator.mem_write(DESC + TRANSFER_WORD * 4, payload)
    set_descriptor_word(D_OFFSET, 0)
    set_descriptor_word(D_SIZE, len(payload))
    call(own, OP_COPY)
    assert descriptor_word(D_STATUS) == STATUS_OK
    set_descriptor_word(D_OFFSET, descriptor_word(D_ALLOCATION_BYTES) - 1)
    set_descriptor_word(D_SIZE, 4)
    call(own, OP_COPY)
    assert descriptor_word(D_STATUS) == STATUS_COPY_BOUNDS
    api = IDS["fw_os_current_time_read_in_ms"]
    set_descriptor_word(D_OFFSET, FIXTURE_IMAGE_BYTES)
    set_descriptor_word(D_SIZE, 12)
    emulator.mem_write(DESC + TRANSFER_WORD * 4, pack(36, 40, api))
    call(own, OP_COPY)
    set_descriptor_word(D_OFFSET, FIXTURE_IMAGE_BYTES)
    set_descriptor_word(D_SIZE, 1)
    set_descriptor_word(D_ENTRY, 0)
    set_descriptor_word(D_BINDING_COUNT, 1)
    call(own, OP_RUN)
    assert (
        descriptor_word(D_STATUS) == STATUS_OK
        and descriptor_word(D_IMAGE) == 0
        and descriptor_word(D_RESIDENT_LOADER) == 0
        and not allocations
    )
    assert u32(emulator.mem_read(SLOT, 4)) == 0 and descriptor_word(D_METHOD_SLOT) == 0
    assert (
        descriptor_word(D_OBSERVED_SP)
        == ((base + FIXTURE_IMAGE_BYTES + 4 + 8 + GAME_STACK_BYTES) & ~7)
        and descriptor_word(D_CLOCK_RESULT) == MOCK_CLOCK_MS
    )
    assert (
        clock == [1] and (base, FIXTURE_IMAGE_BYTES) in cache and len(freed) == 2 and len(kni) == 6
    )
    for name, rel, off, index, value, want in [
        ("bad relocation", 4097, 40, api, 0, 25),
        ("bad binding offset", 36, 4097, api, 0, 27),
        ("bad API index", 36, 40, len(IDS), 0, 27),
        ("nonzero binding slot", 36, 40, api, 123, 27),
    ]:
        own, base = install()
        emulator.mem_write(base, payload)
        emulator.mem_write(base + 40, pack(value))
        emulator.mem_write(base + FIXTURE_IMAGE_BYTES, pack(rel, off, index))
        set_descriptor_word(D_OFFSET, FIXTURE_IMAGE_BYTES)
        set_descriptor_word(D_SIZE, 1)
        set_descriptor_word(D_ENTRY, 0)
        set_descriptor_word(D_BINDING_COUNT, 1)
        call(own, OP_RUN)
        assert (
            descriptor_word(D_STATUS) == want
            and not allocations
            and not descriptor_word(D_RESIDENT_LOADER)
        ), name
        assert not u32(emulator.mem_read(SLOT, 4)) and clock == [1], name
    own, base = install()
    emulator.mem_write(base, payload)
    emulator.mem_write(base + FIXTURE_IMAGE_BYTES, pack(36, 40, api))
    set_descriptor_word(API_WORD + api, 0)
    set_descriptor_word(D_OFFSET, FIXTURE_IMAGE_BYTES)
    set_descriptor_word(D_SIZE, 1)
    set_descriptor_word(D_ENTRY, 0)
    set_descriptor_word(D_BINDING_COUNT, 1)
    call(own, OP_RUN)
    assert (
        descriptor_word(D_STATUS) == STATUS_BINDING
        and not allocations
        and not descriptor_word(D_RESIDENT_LOADER)
        and clock == [1]
    )
    set_descriptor_word(API_WORD + api, targets["fw_os_current_time_read_in_ms"])
    # RM-217 has a mandatory foreground callback and no master-display API.
    # Only those four uncalled slots may be zero, under the legacy policy.
    optional_display = [
        "fw_gdi_display_master_reserve_enable",
        "fw_gdi_display_master_reserve_disable",
        "fw_gdi_display_master_update",
        "fw_lcd_owner",
    ]
    cases = [(name, 1, 0x870001, STATUS_OK) for name in optional_display]
    cases += [
        (optional_display[0], 0, 0x870001, STATUS_BINDING),
        (optional_display[0], 1, 0, STATUS_BINDING),
        ("fw_os_current_time_read_in_ms", 1, 0x870001, STATUS_BINDING),
    ]
    for name, layout, refresh, expected_status in cases:
        own, base = install()
        index = IDS[name]
        set_descriptor_word(API_WORD + index, 0)
        set_descriptor_word(95, layout)
        set_descriptor_word(85, refresh)
        set_descriptor_word(4, 0)
        emulator.mem_write(base, pack(0xE3A0102A, 0xE5801010, 0xE12FFF1E))
        emulator.mem_write(base + FIXTURE_IMAGE_BYTES, pack(40, index))
        set_descriptor_word(D_OFFSET, FIXTURE_IMAGE_BYTES)
        set_descriptor_word(D_SIZE, 0)
        set_descriptor_word(D_ENTRY, 0)
        set_descriptor_word(D_BINDING_COUNT, 1)
        call(own, OP_RUN)
        assert descriptor_word(D_STATUS) == expected_status, (name, layout, refresh)
        assert descriptor_word(4) == (42 if expected_status == STATUS_OK else 0)
        assert not allocations and not descriptor_word(D_RESIDENT_LOADER)
        set_descriptor_word(API_WORD + index, targets[name])
    set_descriptor_word(95, 0)
    set_descriptor_word(85, 0)
    # A normal abort after installation frees the resident loader and image.
    own, base = install()
    call(own, OP_ABORT)
    assert (
        not allocations
        and not descriptor_word(D_RESIDENT_LOADER)
        and not u32(emulator.mem_read(SLOT, 4))
    )
    print(
        "PASS: compiled Thumb carrier and ARM loader at relocated mock APIs; "
        "positive cache arguments, carrier restoration, self-copy, KNI balance, private stack, "
        "bound Thumb call, chunk/import bounds, abort/error cleanup and tail-free"
    )


def main():
    global META, IDS
    META = json.loads((BUILD_DIR / "imports.json").read_text())
    IDS = {name: index for index, (name, _) in enumerate(META["entries"])}
    loader_tests()


if __name__ == "__main__":
    main()
