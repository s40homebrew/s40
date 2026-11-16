#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Actual ARM module vs explicit firmware-contract stubs. No handset access.

This verifies ABI, call ordering, counts, LUT, ownership and error paths.
It does NOT establish real panel compatibility or peripheral timing.
"""

import os
import struct
import subprocess
import tempfile
from pathlib import Path

from elftools.elf.elffile import ELFFile
from unicorn import UC_ARCH_ARM, UC_HOOK_CODE, UC_HOOK_MEM_READ, UC_HOOK_MEM_WRITE, UC_MODE_ARM, Uc
from unicorn.arm_const import *

ROOT = Path(__file__).resolve().parents[2]
BUILD = None
REGS = [UC_ARM_REG_R0, UC_ARM_REG_R1, UC_ARM_REG_R2, UC_ARM_REG_R3]
SAVED = [
    UC_ARM_REG_R4,
    UC_ARM_REG_R5,
    UC_ARM_REG_R6,
    UC_ARM_REG_R7,
    UC_ARM_REG_R8,
    UC_ARM_REG_R9,
    UC_ARM_REG_R10,
    UC_ARM_REG_R11,
]
STOP, STACK, SOURCE, RESULT = 0x300000, 0x400000, 0x500000, 0x600000
TARGETS = [
    0xF02D500A,
    0xF02A3234,
    0xF02A322C,
    0xF0250A38,
    0xF037CCFE,
    0xF037CD90,
    0xF02A30EA,
    0xF0250840,
    0xF02507A4,
    0xF0250868,
    0xF02509E6,
    0xF0D4A744,
    0xF0D4A760,
    0xF0254170,
    0xF025106A,
    0xF0250938,
    0xF037BF6A,
    0xF037AD86,
    0xF025087E,
    0xF0254192,
    0xF0253846,
    0xF0316BFE,
    0xF0316A94,
    0xF0CFE4BA,
    0xF0B9B868,
    0xF0B9CBC8,
]


class Model:
    def __init__(self, no_dma=False, timer_state=2, colmod=7, stop_fails=False):
        self.u = Uc(UC_ARCH_ARM, UC_MODE_ARM)
        self.symbols = {}
        self.events = []
        self.clock = 0
        self.colmod = colmod
        self.outer = 0
        self.gate = 0
        self.clocks = [0, 0]
        self.irq = 0
        self.callback = 0
        self.timer_callback = 0
        self.no_dma = no_dma
        self.command = 0
        self.lut = None
        self.mode_read = 0
        self.periodic_stops = 0
        self.periodic_starts = 0
        self.queued_expiry = timer_state == 3
        self.stop_fails = stop_fails
        self.reject_formats = set()
        self.dma_arms = 0
        self.clock_step = 1
        self.gpio_pattern = None
        self.gpio_reads = 0
        self.gpio_first_clock = None
        self.gpio_last_clock = None
        self.window_calls = 0
        with (BUILD / "video.elf").open("rb") as f:
            elf = ELFFile(f)
            ranges = []
            for s in elf.iter_segments():
                if s["p_type"] == "PT_LOAD":
                    ranges.append((s["p_vaddr"], s["p_memsz"], s.data()))
            start = min(a for a, n, b in ranges) & ~4095
            end = (max(a + n for a, n, b in ranges) + 4095) & ~4095
            self.code_range = (start, end)
            self.u.mem_map(start, end - start)
            for a, n, b in ranges:
                self.u.mem_write(a, b)
            for s in elf.get_section_by_name(".symtab").iter_symbols():
                if s["st_value"]:
                    self.symbols[s.name] = s["st_value"]
        for a, n in [
            (STOP, 4096),
            (STACK, 16384),
            (SOURCE, 155648),
            (RESULT, 4096),
            (0xD8000000, 0x200000),
            (0xFFD22000, 4096),
            (0xFFD31000, 4096),
        ]:
            self.u.mem_map(a, n)
        # Model only the documented memory contracts; no private firmware input.
        self.u.mem_map(0xF0100000, 0x1000000)
        self.u.mem_map(0xF1120000, 4096)
        for a, v in [
            (0xF0253928, 0xB5FE),
            (0xF025385C, 0xB570),
            (0xF037BF6A, 0xB5FF),
            (0xF0250938, 0xB5F8),
            (0xF0254170, 0x4AF8),
            (0xF02509E6, 0x490B),
        ]:
            self.u.mem_write(a, struct.pack("<H", v))
        self.bypass_identity = True
        self.identity = self.symbols["s40_rm443_video_supported"]
        self.u.hook_add(UC_HOOK_CODE, self.hook, begin=self.identity, end=self.identity)
        self.allocator = self.symbols["fw_os_block_alloc_no_wait"]
        self.allocate_ok = True
        self.light_messages = []
        self.u.hook_add(UC_HOOK_CODE, self.hook, begin=self.allocator, end=self.allocator)
        for a, v in [
            (0xD80BCC5B, 0x83),
            (0xD80BCC5C, 0x94),
            (0xD80BCC5D, 0x41),
            (0xD80BCC5E, 1),
            (0xD80BCC1C, 1),
        ]:
            self.u.mem_write(a, bytes([v]))
        self.w32(0xFFD22208, 8)
        self.u.mem_write(0xD80C33E2, bytes([timer_state]))
        for a in TARGETS:
            self.u.hook_add(UC_HOOK_CODE, self.hook, begin=a, end=a)
        self.u.hook_add(UC_HOOK_MEM_WRITE, self.write_check)
        self.u.hook_add(UC_HOOK_MEM_READ, self.gpio_read, begin=0xFFD31D48, end=0xFFD31D4B)

    def r32(self, a):
        return struct.unpack("<I", self.u.mem_read(a, 4))[0]

    def w32(self, a, v):
        self.u.mem_write(a, struct.pack("<I", v))

    def gpio_read(self, u, access, a, n, value, user):
        assert a == 0xFFD31D48 and n == 4 and self.gpio_pattern is not None
        assert self.irq == 0 and self.outer == self.gate == 1 and self.clocks == [1, 1]
        assert self.callback == self.dma_arms == self.r32(0xD80BD17C) == self.window_calls == 0
        assert self.r32(self.symbols["pending"]) == 0
        if self.gpio_first_clock is None:
            self.gpio_first_clock = self.clock
        high = self.gpio_pattern[min(self.gpio_reads, len(self.gpio_pattern) - 1)]
        # Unrelated input bits must not be confused with the TE bit.
        self.w32(a, 0x01000001 | (int(high) << 25))
        self.gpio_reads += 1
        self.gpio_last_clock = self.clock

    def write_check(self, u, access, a, n, value, user):
        lo, hi = self.code_range
        allowed = (
            (lo <= a and a + n <= hi)
            or STACK <= a < a + n <= STACK + 16384
            or RESULT <= a < a + n <= RESULT + 4096
            or 0xD8000000 <= a < a + n <= 0xD8100000
            or a in [0xFFD22208, 0xFFD31C24, 0xFFD31C28]
        )
        assert allowed, ("unexpected write", hex(a), n)

    def hook(self, u, a, size, user):
        if a == self.identity and not self.bypass_identity:
            return
        assert u.reg_read(UC_ARM_REG_SP) % 8 == 0, ("unaligned external call", hex(a))
        v = [u.reg_read(r) for r in REGS]
        self.events.append((hex(a), v))
        ret = 1
        tail = None
        if a == self.identity:
            ret = 1
        elif a == self.allocator:
            assert v[0] == 24
            ret = RESULT + 0x200 if self.allocate_ok else 0
        elif a == 0xF0B9CBC8:
            assert v[:3] == [14, 0xD8176D88, RESULT + 0x208]
            raw = bytes(u.mem_read(RESULT + 0x200, 24))
            assert raw[:8] == b"\0\0\0\0\x12\0\0\0"
            self.light_messages.append(raw[8:])
        elif a == 0xF0CFE4BA:
            assert v[:2] == [RESULT + 0x108, 0xE104]
        elif a == 0xF0B9B868:
            assert v[0] == RESULT + 0x108
        elif a == 0xF02D500A:
            self.clock = (self.clock + self.clock_step) & 0xFFFFFFFF
            ret = self.clock
        elif a == 0xF02A3234:
            self.irq += 1
        elif a == 0xF02A322C:
            self.irq -= 1
            assert self.irq >= 0
        elif a == 0xF0316BFE:
            assert v[0] == 259 and self.gate == 1 and self.outer == 1
            if self.periodic_stops == 0:
                assert self.irq > 0
            self.periodic_stops += 1
            if not self.stop_fails:
                self.u.mem_write(0xD80C33E2, b"\x01")
                self.queued_expiry = False
            ret = 500
        elif a == 0xF0316A94:
            assert v[:2] == [259, 1000] and self.outer == 1
            assert self.r32(0xD80BD17C) == 0 and self.clocks == [0, 0]
            self.periodic_starts += 1
            self.u.mem_write(0xD80C33E2, b"\x02")
        elif a == 0xF0D4A744:
            assert v[0] == 1 and self.outer == 0
            self.outer = 1
        elif a == 0xF0D4A760:
            assert v[0] == 1 and self.outer == 1 and self.gate == 0
            self.outer = 0
        elif a == 0xF0250A38:
            if v[0]:
                assert self.gate == 0
                self.gate = 1
            else:
                assert self.gate == 1
                self.gate = 0
        elif a in [0xF037CCFE, 0xF037CD90]:
            i = 0 if a == 0xF037CCFE else 1
            assert v[0] == 25 and v[1] in [0, 1] and v[2] == 0
            self.clocks[i] += 1 if v[1] else -1
            assert self.clocks[i] in [0, 1]
        elif a == 0xF02A30EA:
            assert 16 <= v[0] <= 23 and v[1] in [2, 0x20]
        elif a == 0xF0250840:
            assert self.gate == 1 and self.clocks == [1, 1]
            self.command = v[0]
        elif a == 0xF02507A4:
            b = bytes(u.mem_read(v[0], v[1]))
            if self.command == 0x2D:
                assert v[1] == 192
                self.lut = b
                assert b == bytes(
                    [((i << 3) | (i >> 2)) if i < 32 else 255 for i in range(64)]
                    + [(i << 2) | (i >> 4) for i in range(64)]
                    + [((i << 3) | (i >> 2)) if i < 32 else 255 for i in range(64)]
                )
            elif self.command == 0x3A:
                assert v[1] == 1 and b[0] in [5, 7]
                if b[0] not in self.reject_formats:
                    self.colmod = b[0]
            else:
                raise AssertionError(("unexpected data command", self.command))
        elif a == 0xF0250868:
            assert self.command == 0x0C
            u.mem_write(v[0], bytes([self.colmod]))
            self.mode_read += 1
        elif a == 0xF02509E6:
            assert self.command == 0x2E and self.gate == 1 and self.clocks == [1, 1]
            self.w32(v[0], 0x12345612)
        elif a == 0xF0254170:
            assert v[0] == 0 and self.colmod == 5
            assert self.u.mem_read(0xD80C33E2, 1) == b"\x01"
            self.callback = v[1]
        elif a == 0xF025106A:
            assert v in [[0, 319, 0, 239], [37, 37, 21, 21]]
            self.window_calls += 1
        elif a == 0xF0250938:
            assert v[:3] == [38400, 0, 0]
        elif a == 0xF037BF6A:
            assert self.irq > 0 and v == [SOURCE, 18, 38400, 0xF025385D]
            assert self.colmod == 5 and not self.queued_expiry
            self.dma_arms += 1
            ret = 255 if self.no_dma else 2
        elif a == 0xF037AD86:
            assert self.irq > 0 and v[1:3] == [0, 80000]
            self.timer_callback = v[0]
            ret = 4
        elif a == 0xF025087E:
            self.w32(0xFFD22208, 8)
        elif a == 0xF0254192:
            assert self.r32(0xD80BD17C) == 1
            self.w32(0xD80BD17C, 0)
            assert self.gate == 1
            self.gate = 0
            tail = self.callback
        elif a == 0xF0253846:
            self.stock_complete()
            tail = self.callback
        else:
            raise AssertionError(hex(a))
        for r in [UC_ARM_REG_R1, UC_ARM_REG_R2, UC_ARM_REG_R3, UC_ARM_REG_R12]:
            u.reg_write(r, 0xEEEEEEEE)
        u.reg_write(UC_ARM_REG_R0, ret)
        u.reg_write(UC_ARM_REG_PC, tail or u.reg_read(UC_ARM_REG_LR))

    def stock_complete(self):
        assert self.r32(0xD80BD17C) == 1 and self.gate == 1 and self.clocks == [1, 1]
        assert self.u.mem_read(0xD80BCC18, 2) == bytes([4, 1])
        assert self.u.mem_read(0xD80BCC1D, 1) == bytes([2])
        self.u.mem_write(0xD80BCC19, b"\x00\x00")
        self.u.mem_write(0xD80BCC34, b"\x00")
        self.w32(0xD80BD17C, 0)
        self.gate = 0
        self.clocks = [0, 0]

    def call(self, name, *args, instructions=1000000):
        address = self.symbols[name] if isinstance(name, str) else name
        sp = STACK + 16384
        self.u.reg_write(UC_ARM_REG_SP, sp)
        self.u.reg_write(UC_ARM_REG_LR, STOP)
        for r, v in zip(REGS, args):
            self.u.reg_write(r, v)
        saved = [0x45670000 + i for i in range(8)]
        for r, v in zip(SAVED, saved):
            self.u.reg_write(r, v)
        self.u.emu_start(address, STOP, count=instructions)
        assert self.u.reg_read(UC_ARM_REG_PC) == STOP
        assert self.u.reg_read(UC_ARM_REG_SP) == sp
        assert [self.u.reg_read(r) for r in SAVED] == saved
        assert self.irq == 0
        return self.u.reg_read(UC_ARM_REG_R0)


def exercise(no_dma=False, timeout=False, timer_state=2, repair=False):
    m = Model(no_dma, timer_state)
    assert m.call("s40_rm443_video_init", RESULT) == 0 and m.colmod == 5 and m.outer == 1
    assert m.call("s40_rm443_video_active") == 1
    assert m.periodic_stops == 1 and m.periodic_starts == 0 and not m.queued_expiry
    if repair:
        m.colmod = 7
    result = m.call("s40_rm443_video_submit", SOURCE, 0)
    assert m.call("s40_rm443_video_format_repairs") == int(repair)
    assert m.call("s40_rm443_video_first_repair") == int(repair)
    if no_dma:
        assert result == 0xFFFFFFFB and m.call("s40_rm443_video_in_flight") == 0
    else:
        assert result == 0 and m.call("s40_rm443_video_in_flight") == 1
        assert m.call("s40_rm443_video_read_pixel", 21, 37, RESULT + 4) == 0xFFFFFFFE
        assert m.call("s40_rm443_video_restore", 0, RESULT) == 0xFFFFFFFA
        assert m.outer == 1 and m.call("s40_rm443_video_active") == 1
        if timeout:
            m.call(m.timer_callback)
        else:
            m.stock_complete()
            m.call(m.callback, 1)
        assert m.call("s40_rm443_video_in_flight") == 0
        assert m.call("s40_rm443_video_drain", 100) == (0xFFFFFFFC if timeout else 0)
        if not timeout:
            assert m.call("s40_rm443_video_read_pixel", 21, 37, RESULT + 4) == 0
            assert m.r32(RESULT + 4) == 0x123456
            assert m.call("s40_rm443_video_read_pixel", 240, 37, RESULT + 4) == 0xFFFFFFFE
    assert m.call("s40_rm443_video_restore", 100, RESULT) == 0
    assert m.colmod == 7 and m.outer == 0 and m.gate == 0 and m.clocks == [0, 0]
    assert m.call("s40_rm443_video_active") == 0
    assert m.periodic_starts == 1 and m.u.mem_read(0xD80C33E2, 1) == b"\x02"
    return {
        "no_dma": no_dma,
        "stock_timeout": timeout,
        "initial_timer_state": timer_state,
        "repair": repair,
        "firmware_calls": len(m.events),
        "pass": True,
    }


def exercise_errors():
    cases = []
    m = Model()
    m.u.mem_write(0xD80BCC1C, b"\x00")  # Unpowered panel: no ownership acquired.
    assert m.call("s40_rm443_video_init", RESULT) == 0xFFFFFFFF
    assert m.outer == m.gate == m.periodic_stops == 0
    m.u.mem_write(0xD80BCC1C, b"\x01")
    m.u.mem_write(0xD80BCC19, b"\x01")  # A stock watchdog still owns the LCD.
    assert m.call("s40_rm443_video_init", RESULT) == 0xFFFFFFFE
    assert m.outer == m.gate == m.periodic_stops == 0
    cases.append({"case": "live_panel_and_dma_ownership_guards", "pass": True})
    for state in [0, 1, 4]:
        m = Model(timer_state=state)
        assert m.call("s40_rm443_video_init", RESULT) == 0xFFFFFFFE
        assert m.outer == m.gate == m.periodic_stops == m.periodic_starts == 0
        assert m.clocks == [0, 0] and m.lut is None and m.colmod == 7
        cases.append({"case": "ambiguous_timer_abort", "state": state, "pass": True})
    m = Model(stop_fails=True)
    assert m.call("s40_rm443_video_init", RESULT) == 0xFFFFFFFE
    assert m.outer == m.gate == 0 and m.lut is None and m.colmod == 7
    assert m.periodic_stops == m.periodic_starts == 1
    cases.append({"case": "timer_stop_verification_failure", "pass": True})
    m = Model(colmod=6)
    assert m.call("s40_rm443_video_init", RESULT) == 0xFFFFFFFD
    assert m.outer == m.gate == 0 and m.lut is None and m.colmod == 6
    assert m.periodic_stops == m.periodic_starts == 1
    cases.append({"case": "unexpected_initial_format", "pass": True})
    m = Model()
    m.reject_formats = {5}
    assert m.call("s40_rm443_video_init", RESULT) == 0xFFFFFFFD
    assert m.outer == 1 and m.periodic_starts == 0 and m.gate == 0
    assert m.call("s40_rm443_video_restore", 100, RESULT) == 0
    assert m.periodic_starts == 1 and m.outer == 0
    cases.append({"case": "initial_format_write_failure_then_restore", "pass": True})
    m = Model()
    assert m.call("s40_rm443_video_init", RESULT) == 0
    m.colmod = 7
    m.reject_formats = {5}
    assert m.call("s40_rm443_video_submit", SOURCE, 0) == 0xFFFFFFFD
    assert m.r32(0xD80BD17C) == 0 and m.callback == 0 and m.dma_arms == 0
    assert m.gate == 0 and m.clocks == [0, 0] and m.periodic_starts == 0
    assert m.call("s40_rm443_video_restore", 100, RESULT) == 0
    assert m.periodic_starts == 1 and m.outer == 0
    cases.append({"case": "repair_failure_before_dma_bookkeeping", "pass": True})
    m = Model()
    assert m.call("s40_rm443_video_init", RESULT) == 0
    m.reject_formats = {7}
    assert m.call("s40_rm443_video_restore", 100, RESULT) == 0xFFFFFFFD
    assert m.outer == 1 and m.periodic_starts == 0
    m.reject_formats = set()
    assert m.call("s40_rm443_video_restore", 100, RESULT) == 0
    assert m.periodic_starts == 1 and m.outer == 0
    cases.append({"case": "restore_failure_retains_timer_suppression_until_retry", "pass": True})
    return cases


def exercise_te(pattern, timeout=False, wrap=False, frozen=False):
    m = Model()
    assert m.call("s40_rm443_video_init", RESULT) == 0
    m.gpio_pattern = pattern
    if wrap:
        m.clock = 0xFFFFFFFD
    if frozen:
        m.clock_step = 0
    m.w32(RESULT + 4, 0xDEADBEEF)
    result = m.call(
        "s40_rm443_video_submit_synced",
        SOURCE,
        0,
        RESULT + 4,
        instructions=12000000 if frozen else 1000000,
    )
    if timeout:
        assert result == 0xFFFFFFF9 and m.r32(RESULT + 4) == 0
        assert m.gate == 0 and m.clocks == [0, 0] and m.outer == 1
        assert m.callback == m.dma_arms == m.window_calls == m.r32(0xD80BD17C) == 0
        assert m.call("s40_rm443_video_in_flight") == 0 and m.periodic_starts == 0
        assert m.u.mem_read(0xD80C33E2, 1) == b"\x01"
        assert m.gpio_reads == (262144 if frozen else 50)
        if not frozen:
            assert ((m.gpio_last_clock - m.gpio_first_clock) & 0xFFFFFFFF) == 49
        # An edge timeout leaves the session reusable, including ordinary submit.
        m.clock_step = 1
        assert m.call("s40_rm443_video_submit", SOURCE, 0) == 0
    else:
        assert result == 0 and m.call("s40_rm443_video_in_flight") == 1
        assert m.r32(RESULT + 4) == ((m.gpio_last_clock + 1) & 0xFFFFFFFF)
        assert m.gpio_reads == len(pattern) and m.dma_arms == m.window_calls == 1
        if wrap:
            assert m.r32(RESULT + 4) < 10
    edge_ms = m.r32(RESULT + 4)
    m.stock_complete()
    m.call(m.callback, 1)
    assert m.call("s40_rm443_video_restore", 100, RESULT) == 0
    assert m.outer == m.gate == 0 and m.clocks == [0, 0] and m.colmod == 7
    assert m.periodic_starts == 1
    return {
        "case": "te_sync",
        "pattern": pattern,
        "timeout": timeout,
        "clock_wrap": wrap,
        "frozen_clock": frozen,
        "gpio_reads": m.gpio_reads,
        "edge_ms": edge_ms,
        "pass": True,
    }


def exercise_te_null():
    m = Model()
    assert m.call("s40_rm443_video_init", RESULT) == 0
    calls = len(m.events)
    assert m.call("s40_rm443_video_submit_synced", SOURCE, 0, 0) == 0xFFFFFFFE
    assert len(m.events) == calls and m.gate == 0 and m.clocks == [0, 0]
    assert m.call("s40_rm443_video_restore", 100, RESULT) == 0
    return {"case": "te_null_timestamp_rejected_without_firmware_calls", "pass": True}


def check_identity():
    m = Model()
    m.bypass_identity = False
    m.u.mem_unmap(0xF0100000, 0x1000000)
    assert m.call("s40_rm443_video_supported") == 0
    m.u.mem_map(0xF0100000, 0x1000000)
    descriptor = m.r32(m.symbols["s40_result"])
    # The matching descriptor alone cannot enable the driver on a different ROM.
    for word, value in [(1122, 0xF02D500B), (87, 240), (88, 320), (89, 488), (85, 0), (95, 0)]:
        m.w32(descriptor + word * 4, value)
    assert m.call("s40_rm443_video_supported") == 0
    # RM-443 uses master display, so a zero UI-refresh callback must pass the
    # profile gate and reach ROM validation (the synthetic ROM then fails).
    reason = m.call("s40_rm443_video_support_reason")
    assert bytes(m.u.mem_read(reason, 25)).split(b"\0", 1)[0] == b"ROM fingerprint mismatch"
    assert not m.events and m.outer == m.gate == m.dma_arms == 0


def check_keyboard_and_focus():
    m = Model()
    message = bytearray(24)
    message[3] = 0xE1
    message[4] = 22
    message[6] = 42
    message[9] = 4
    m.u.mem_write(0xD8176D90, struct.pack("<H", 42))
    m.u.mem_write(RESULT + 0x100, bytes(message))
    assert m.call("s40_rm443_video_keyboard", RESULT + 0x100) == 1
    assert [int(a, 16) for a, args in m.events] == [0xF0CFE4BA, 0xF0B9B868]
    for offset, value in [(0, 1), (3, 0x11), (4, 21), (6, 43), (9, 7)]:
        rejected = bytearray(message)
        rejected[offset] = value
        m.u.mem_write(RESULT + 0x100, bytes(rejected))
        assert m.call("s40_rm443_video_keyboard", RESULT + 0x100) == 0
    assert len(m.events) == 2
    # Ten-bit object IDs include two receiver-device bits; unrelated device
    # flags must neither misroute nor reject an otherwise owned packet.
    message[1] = 0x82
    message[6] = 0xAB
    m.u.mem_write(0xD8176D90, struct.pack("<H", 0x2AB))
    m.u.mem_write(RESULT + 0x100, bytes(message))
    assert m.call("s40_rm443_video_keyboard", RESULT + 0x100) == 1
    assert len(m.events) == 4
    assert m.call("s40_rm443_video_foreground") == 0


def check_services_and_light():
    m = Model()
    descriptor = m.r32(m.symbols["s40_result"])
    m.w32(descriptor + 24, 16)
    assert m.call("s40_rm443_video_services_pending") == 0xFFFFFFFF
    # Physical CM_JAVA layout: immutable task descriptor in ROM, queue in RAM.
    m.w32(0xD80CD3AC, 0xF1120C34)
    m.w32(0xF1120C34, 0xD80CF2E4)
    m.u.mem_write(0xD803B25C, b"\x9b")
    m.w32(0xD80C5CB4, 0xFFFFFFFF)
    assert m.call("s40_rm443_video_services_pending") == 0
    m.w32(0xD80CF2E4, 0xD8180200)
    assert m.call("s40_rm443_video_services_pending") == 1
    m.w32(0xD80CF2E4, 0)
    m.u.mem_write(0xD80CF320, b"\x01")
    assert m.call("s40_rm443_video_services_pending") == 2
    m.u.mem_write(0xD80CF320, b"\x08")
    assert m.call("s40_rm443_video_services_pending") == 0
    # Panel can wait for the scratchpad before a focus event reaches Java.
    # Only read the head: a queued task does not need to be mapped/dereferenced.
    m.w32(0xD80C5CB4, 0xDEADBEEF)
    assert m.call("s40_rm443_video_services_pending") == 4
    m.w32(0xD80CF2E4, 0xD8180200)
    m.u.mem_write(0xD80CF320, b"\x01")
    assert m.call("s40_rm443_video_services_pending") == 7
    m.w32(0xD80CF2E4, 0)
    m.u.mem_write(0xD80CF320, b"\x08")
    m.w32(0xD80C5CB4, 0xFFFFFFFF)
    assert m.call("s40_rm443_video_services_pending") == 0
    m.u.mem_write(0xD803B25C, b"\xb2")
    assert m.call("s40_rm443_video_services_pending") == 0xFFFFFFFF
    m.u.mem_write(0xD803B25C, b"\x9b")
    m.w32(descriptor + 24, 37)
    assert m.call("s40_rm443_video_services_pending") == 0xFFFFFFFF
    m.u.mem_write(0xD8176D90, struct.pack("<H", 42))
    assert m.call("s40_rm443_video_backlight", 30, 0) == 0
    assert m.call("s40_rm443_video_backlight", 0, 1) == 0
    assert m.light_messages == [
        bytes([0, 0x90, 0, 1, 0, 12, 0, 30, 1, 1, 0, 0, 0, 100, 0, 0]),
        bytes([0, 0x90, 0, 1, 0, 12, 0, 0, 1, 1, 0, 0, 0, 100, 1, 0]),
    ]
    m.allocate_ok = False
    assert m.call("s40_rm443_video_backlight", 30, 0) == 0xFFFFFFFF
    assert m.call("s40_rm443_video_backlight", 121, 0) == 0xFFFFFFFF
    assert len(m.light_messages) == 2
    m.w32(0xD8176954, 0xD8180000)
    m.w32(0xD8180014, 3)
    assert m.call("s40_rm443_video_foreground") == 1
    m.w32(0xD8180014, 2)
    assert m.call("s40_rm443_video_foreground") == 0
    m.w32(0xD8176954, 0xFFFFFFFF)
    assert m.call("s40_rm443_video_foreground") == 0


def run():
    check_identity()
    check_keyboard_and_focus()
    check_services_and_light()
    cases = (
        [
            exercise(),
            exercise(timer_state=3),
            exercise(repair=True),
            exercise(no_dma=True),
            exercise(timeout=True),
        ]
        + exercise_errors()
        + [
            exercise_te([0, 0, 1]),
            exercise_te([1, 1, 0, 0, 1]),
            exercise_te([0, 0, 1], wrap=True),
            exercise_te([0], timeout=True),
            exercise_te([1], timeout=True),
            exercise_te([0], timeout=True, wrap=True),
            exercise_te([0], timeout=True, frozen=True),
            exercise_te_null(),
        ]
    )
    print(
        "RM-443 video: identity rejection and %d ARM ownership/format/timeout checks passed."
        % len(cases)
    )


if __name__ == "__main__":
    with tempfile.TemporaryDirectory(prefix="s40-video-arm-") as folder:
        BUILD = Path(folder)
        subprocess.run(["python3", str(ROOT / "tools/gen_imports.py"), str(BUILD)], check=True)
        fixture = BUILD / "fixture.c"
        fixture.write_text(
            "#include <stdint.h>\nuint32_t descriptor[1200];\nuint32_t *s40_result=descriptor;\n"
            "void* fw_os_block_alloc_no_wait(unsigned n) {(void)n; return 0;}\n"
        )
        cc = os.environ.get("CC", "clang")
        ld = os.environ.get("LD", "ld.lld")
        flags = [
            "-target",
            "arm-none-eabi",
            "-march=armv5te",
            "-marm",
            "-O2",
            "-ffreestanding",
            "-fno-builtin",
            "-I" + str(ROOT / "include"),
            "-I" + str(ROOT / "src/native"),
            "-I" + str(ROOT / "include/libc"),
            "-I" + str(BUILD),
        ]
        for src, out in [
            (ROOT / "src/native/display/rm443_1012/driver.c", BUILD / "video.o"),
            (fixture, BUILD / "fixture.o"),
        ]:
            subprocess.run([cc, *flags, "-c", str(src), "-o", str(out)], check=True)
        subprocess.run(
            [
                ld,
                "-Ttext=0x10000",
                "-e",
                "s40_rm443_video_init",
                str(BUILD / "video.o"),
                str(BUILD / "fixture.o"),
                "-o",
                str(BUILD / "video.elf"),
            ],
            check=True,
        )
        run()
