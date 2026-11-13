/* SPDX-License-Identifier: MIT */
/**
 * Allocation-free discovery shared by the standalone heap booster profiles.
 * No firmware function/global address is used as a search seed.
 */
final class HeapPatchResolver {
    // Shared VM diagnostic, not a firmware identifier. It corroborates the code
    // recipe within the local initializer; the string alone never selects a site.
    private static final String FAILURE = "Failed to load main class";
    private static final String SYSTEM = "libsystem.so", ALLOC = "os_block_alloc_no_wait",
                                FREE = "os_block_dealloc";
    // 15 Thumb-1 halfwords, not ARM words. STACK reloads the app from an SP slot;
    // FRAME reloads it from [r3+0x40]. Variant 2 differs at cfg+0x20 -> app+0x6c.
    // Keep observed address assertions in tests, and variant semantics here.
    private static final int[] STACK = {0x9900, 0x68e0, 0x6588, 0x9900, 0x6960, 0x65c8, 0x9a00,
            0x69a1, 0x2002, 0x6691, 0x9900, 0x66c8, 0x9900, 0x6a60, 0x6708};
    private static final int[] FRAME = {0x68e0, 0x6c19, 0x6588, 0x6960, 0x6c19, 0x65c8, 0x69a0,
            0x6c19, 0x6688, 0x6c19, 0x2002, 0x66c8, 0x6a60, 0x6c19, 0x6708};
    // Same fields as form 2, but the compiler reloads the app before cfg.
    // The preceding +18 store leaves R1=app, so the shared patch is valid.
    private static final int[] FRAME_APP_FIRST = {0x6c19, 0x68e0, 0x6588, 0x6c19, 0x6960, 0x65c8,
            0x6c19, 0x69a0, 0x6688, 0x6c19, 0x6a20, 0x66c8, 0x6c19, 0x6a60, 0x6708};
    // App-first with a constant +6c store through R0 (302). Its preceding
    // +18 store leaves R0=app, so the patch must reload R1 before using it.
    private static final int[] FRAME_APP_CONSTANT = {0x6c19, 0x68e0, 0x6588, 0x6c19, 0x6960, 0x65c8,
            0x6c19, 0x69a0, 0x6688, 0x6c18, 0x2102, 0x66c1, 0x6c19, 0x6a60, 0x6708};
    private static final int[] PATCH_APP_CONSTANT = {0x6c19, 0x68e0, 0x6588, 0x2805, 0x6960, 0xd101,
            0x2000, 0x0500, 0x65c8, 0x69a0, 0x6688, 0x2002, 0x66c8, 0x6a60, 0x6708};
    // Replacement: app+0x58=type; app+0x5c=(type==5 ? MiB<<20 : cfg+0x14);
    // then preserve app+0x68/+0x6c/+0x70. R1 already holds app at entry. In the
    // 301 STACK context R2 already holds app too; the removed R2 reload is dead.
    // Compare/shift flags are overwritten before use after this block. A new
    // recipe must check live registers, flags and incoming branches in context;
    // matching the stores alone is insufficient. See apps/ram_booster/docs/porting.md.
    private static final int[] PATCH = {0x68e0, 0x6588, 0x2805, 0xd102, 0x2000, 0x0500, 0xe000,
            0x6960, 0x65c8, 0x69a0, 0x6688, 0x2002, 0x66c8, 0x6a60, 0x6708};
    // Older frame initializer (6303 08.90): the tail initializes +60/+64,
    // not +6c/+70. Loading the default quota between CMP and BNE preserves
    // flags and saves a branch, leaving room for those stores in 30 bytes.
    private static final int[] FRAME_ZERO = {0x68e0, 0x6c19, 0x6588, 0x6960, 0x6c19, 0x65c8, 0x69a0,
            0x6c19, 0x6688, 0x6c18, 0x4900, 0x6606, 0x6c18, 0x1d09, 0x6646};
    private static final int[] PATCH_ZERO = {0x68e0, 0x6588, 0x2805, 0x6960, 0xd101, 0x2000, 0x0500,
            0x65c8, 0x69a0, 0x6688, 0x0008, 0x4900, 0x6606, 0x1d09, 0x6646};
    final HeapBoosterProfile profile;
    final int[] original = new int[15];
    final int[] replacement = new int[15];
    int site, variant, currentMiB, poolBytes, poolBase, cacheSync;
    int sites, pools, caches, error;
    int alloc, free, allocators;
    int romEnd, ramEnd;
    private int[] view;
    private int base;

    HeapPatchResolver(HeapBoosterProfile p) {
        profile = p;
        // Ensure classes/constants are initialized outside the forged interval.
        if (FAILURE.length() == 0 || STACK.length != PATCH.length)
            throw new Error();
    }
    int word(int address) {
        return view[(address - base) >>> 2];
    }
    private boolean rom(int address, int bytes) {
        return bytes >= 0 && address - profile.romStart >= 0
                && address - profile.romStart <= romEnd - profile.romStart - bytes;
    }
    private boolean discoverBounds() {
        if (profile.mapPrefixBytes == 0)
            return true;
        // RAPU combined RAM + two-code-bank table, found only in the known
        // readable kernel prefix. The second bank is corroboration, not a
        // request to read it. A shorter build narrows every later code read.
        int count = 0;
        for (int p = profile.romStart; p - profile.romStart <= profile.mapPrefixBytes - 40;
                p += 4) {
            if (word(p) != profile.ramStart || word(p + 8) != 0 || word(p + 12) != 0
                    || word(p + 16) != (profile.romStart & ~4095) || word(p + 24) != 0xF3000000
                    || word(p + 32) != 0 || word(p + 36) != 0)
                continue;
            int ram = word(p + 4), primary = word(p + 20), secondary = word(p + 28);
            if ((ram & 4095) != 0 || ram - profile.ramStart <= 0
                    || ram - profile.ramStart > 0x10000000 || (primary & 4095) != 0
                    || primary - profile.romStart < profile.mapPrefixBytes
                    || primary - profile.romStart > 0x02000000 || (secondary & 3) != 0
                    || secondary - 0xF3000000 <= 0 || secondary - 0xF3000000 > 0x02000000)
                continue;
            count++;
            romEnd = primary < profile.romEnd ? primary : profile.romEnd;
            ramEnd = ram < profile.ramEnd ? ram : profile.ramEnd;
        }
        return count == 1;
    }
    int half(int address) {
        return (word(address & ~3) >>> ((address & 2) * 8)) & 65535;
    }
    private int literal(int instruction) {
        int h = half(instruction);
        if ((h & 0xF800) != 0x4800)
            return 0;
        int p = ((instruction + 4) & ~3) + ((h & 255) << 2);
        return rom(p, 4) ? word(p) : 0;
    }
    private boolean textAt(int p, String text) {
        if (!rom(p, text.length() + 1))
            return false;
        for (int i = 0; i <= text.length(); i++) {
            int a = p + i, b = (word(a & ~3) >>> ((a & 3) * 8)) & 255;
            if (b != (i == text.length() ? 0 : text.charAt(i)))
                return false;
        }
        return true;
    }
    private void allocator(int p) {
        // Same 36-byte Nokia descriptor / ELF32 symbol format used by the full
        // homebrew resolver. Only OS allocations are wanted: NW_Mem_Malloc is
        // app-owned and must not back a code page surviving MIDlet exit.
        if (!rom(p, 36) || !textAt(word(p), SYSTEM))
            return;
        int hash = word(p + 24), strings = word(p + 28), symbols = word(p + 32);
        if ((hash & 3) != 0 || (symbols & 3) != 0 || !rom(hash, 8))
            return;
        int count = word(hash + 4);
        if (count < 2 || count > 8192 || !rom(symbols, count * 16))
            return;
        int a = 0, f = 0;
        for (int i = 1; i < count; i++) {
            int s = symbols + 16 * i, off = word(s), value = word(s + 4);
            if (off < 0 || off > romEnd - profile.romStart || !rom(value & ~1, 4)
                    || (word(s + 12) & 255) != 0x12)
                continue;
            if (textAt(strings + off, ALLOC)) {
                if (a != 0)
                    return;
                a = value;
            }
            if (textAt(strings + off, FREE)) {
                if (f != 0)
                    return;
                f = value;
            }
        }
        if (a != 0 && f != 0) {
            allocators++;
            alloc = a;
            free = f;
        }
    }
    private boolean failureString(int p) {
        int end = p + 1536;
        if (!rom(p, 1536 + 32))
            return false;
        for (int at = (p + 32) & ~3; at < end; at += 4) {
            if (word(at) != 0x6C696146)
                continue;
            int n = 0;
            while (n < FAILURE.length()) {
                int a = at + n;
                if (((word(a & ~3) >>> ((a & 3) * 8)) & 255) != FAILURE.charAt(n))
                    break;
                n++;
            }
            if (n == FAILURE.length())
                return true;
        }
        return false;
    }
    private void candidate(int p) {
        if (!rom(p - 6, 40))
            return;
        int form = -1, slot = 0;
        if ((half(p - 6) & 0xFF00) == 0x9900 && half(p - 4) == 0x68A0 && half(p - 2) == 0x6188) {
            form = 0;
            slot = half(p - 6) & 255;
        } else if (half(p - 6) == 0x68A0 && half(p - 4) == 0x6C19 && half(p - 2) == 0x6188)
            form = 1;
        else if (half(p - 6) == 0x6C19 && half(p - 4) == 0x68A0 && half(p - 2) == 0x6188)
            form = 4;
        else if (half(p - 6) == 0x6C18 && half(p - 4) == 0x68A1 && half(p - 2) == 0x6181)
            form = 5;
        if (form < 0)
            return;
        if (form == 1
                && ((half(p + 18) == 0x6c18 && half(p + 22) == 0x6606)
                        || (half(p + 20) == 0x0008 && half(p + 24) == 0x6606))) {
            candidateZero(p);
            return;
        }
        if (form == 1 && (half(p + 18) == 0x6A20 || half(p + 22) == 0x6A20))
            form = 2;
        int quota = 0;
        boolean stock = true, patched = true;
        for (int i = 0; i < 15; i++) {
            int expected = form == 0 ? STACK[i]
                    : form == 4      ? FRAME_APP_FIRST[i]
                    : form == 5      ? FRAME_APP_CONSTANT[i]
                                     : FRAME[i];
            if (form == 0 && (expected == 0x9900 || expected == 0x9A00))
                expected |= slot;
            if (form == 2 && i == 9)
                expected = 0x6A20;
            if (form == 2 && i == 10)
                expected = 0x6C19;
            int h = half(p + 2 * i);
            if (h != expected)
                stock = false;
            int patch = form == 5 ? PATCH_APP_CONSTANT[i] : PATCH[i];
            if ((form == 2 || form == 4) && i == 11)
                patch = 0x6A20;
            if (i == (form == 5 ? 6 : 4)) {
                quota = h & 255;
                if ((h & 0xFF00) != 0x2000 || !choice(quota))
                    patched = false;
            } else if (h != patch)
                patched = false;
        }
        if ((!stock && !patched) || !failureString(p))
            return;
        sites++;
        site = p;
        variant = form;
        currentMiB = stock ? 0 : quota;
        for (int i = 0; i < 15; i++) {
            int h = form == 0   ? STACK[i]
                    : form == 4 ? FRAME_APP_FIRST[i]
                    : form == 5 ? FRAME_APP_CONSTANT[i]
                                : FRAME[i];
            if (form == 0 && (h == 0x9900 || h == 0x9A00))
                h |= slot;
            if (form == 2 && i == 9)
                h = 0x6A20;
            if (form == 2 && i == 10)
                h = 0x6C19;
            original[i] = h;
        }
    }
    static boolean choice(int mib) {
        return mib == 3 || mib == 4 || mib == 6 || mib == 8;
    }
    private int moveLiteral(int h, int from, int to) {
        int target = ((from + 4) & ~3) + ((h & 255) << 2), delta = target - ((to + 4) & ~3);
        return delta >= 0 && delta <= 1020 && rom(target, 4) ? 0x4900 | (delta >>> 2) : 0;
    }
    private void candidateZero(int p) {
        boolean stock = true, patched = true;
        for (int i = 0; i < 15; i++) {
            int h = half(p + 2 * i);
            if (i == 10 ? (h & 0xff00) != 0x4900 : h != FRAME_ZERO[i])
                stock = false;
            if (i == 11 ? (h & 0xff00) != 0x4900
                            : i == 5 ? (h & 0xff00) != 0x2000 || !choice(h & 255)
                                     : h != PATCH_ZERO[i])
                patched = false;
        }
        if ((!stock && !patched) || !failureString(p))
            return;
        int h = stock ? half(p + 20) : moveLiteral(half(p + 22), p + 22, p + 20);
        if (h == 0 || moveLiteral(h, p + 20, p + 22) == 0)
            return;
        sites++;
        site = p;
        variant = 3;
        currentMiB = stock ? 0 : half(p + 10) & 255;
        for (int i = 0; i < 15; i++)
            original[i] = FRAME_ZERO[i];
        original[10] = h;
    }
    void makePatch(int mib) {
        if (variant == 5) {
            for (int i = 0; i < 15; i++)
                replacement[i] = PATCH_APP_CONSTANT[i];
            replacement[6] |= mib;
            return;
        }
        if (variant == 3) {
            for (int i = 0; i < 15; i++)
                replacement[i] = PATCH_ZERO[i];
            replacement[5] |= mib;
            replacement[11] = moveLiteral(original[10], site + 20, site + 22);
            return;
        }
        for (int i = 0; i < 15; i++)
            replacement[i] = PATCH[i];
        replacement[4] |= mib;
        if (variant == 2 || variant == 4)
            replacement[11] = 0x6A20;
    }
    // Interpret only the tiny constant-building vocabulary used by these fixed
    // pool suppliers. Register allocation/order and BL versus BLX may differ;
    // an unrelated instruction stops recognition rather than being skipped.
    private void pool(int p) {
        if (!rom(p, 48) || half(p) != 0xB510 || (half(p + 2) != 0x000C && half(p + 2) != 0x460C))
            return;
        int r0 = 0, r1 = 0, known = 0, at = p + 4;
        for (int i = 0; i < 6; i++, at += 2) {
            int h = half(at), reg = (h >>> 8) & 7;
            if ((h & 0xF800) == 0x4800 && reg <= 1) {
                int value = literal(at);
                if (reg == 0)
                    r0 = value;
                else
                    r1 = value;
                known |= 1 << reg;
            } else if ((h & 0xF800) == 0x2000 && reg <= 1) {
                if (reg == 0)
                    r0 = h & 255;
                else
                    r1 = h & 255;
                known |= 1 << reg;
            } else if ((h & 0xF800) == 0 && (h & 7) <= 1 && ((h >>> 3) & 7) == (h & 7)) {
                reg = h & 7;
                if ((known & (1 << reg)) == 0)
                    return;
                int shift = (h >>> 6) & 31;
                if (reg == 0)
                    r0 <<= shift;
                else
                    r1 <<= shift;
            } else if (h == 0x46C0) {
                // Explicit Thumb NOP, not arbitrary compiler instructions.
            } else if ((h & 0xF800) == 0xF000) {
                int low = half(at + 2), mode = low & 0xF800;
                if (known != 3 || (mode != 0xF800 && mode != 0xE800))
                    return;
                if (mode == 0xE800 && (low & 1) != 0)
                    return;
                int delta = ((h & 2047) << 12) | ((low & 2047) << 1);
                if ((delta & 0x400000) != 0)
                    delta -= 0x800000;
                int called = at + 4 + delta;
                if (mode == 0xE800)
                    called &= ~3;
                if (!rom(called, 4) || (half(at + 4) & 0xFF00) != 0x4800 || literal(at + 4) != r0
                        || half(at + 6) != 0x6020 || half(at + 8) != 0xBD10)
                    return;
                if (r1 < 2 * 1024 * 1024 || r1 > 32 * 1024 * 1024 || (r1 & 4095) != 0
                        || !(profile.ram(r0, r1)
                                && r0 - profile.ramStart <= ramEnd - profile.ramStart - r1))
                    return;
                pools++;
                poolBytes = r1;
                poolBase = r0;
                return;
            } else
                return;
        }
    }
    private static final int[] DIRECT_CACHE = {0xE92D4070, 0xE0805001, 0xE3C0001F, 0xEE070F3E,
            0xEE070F35, 0xE2800020, 0xEE070F3E, 0xEE070F35, 0xE2800020, 0xE1500005, 0x3AFFFFF7,
            0xEE075F9A, 0xE8BD8070};
    // RAPU's ARM I-cache range helper, including the outer-cache tail call.
    // Only that call's relocation varies. The other RAPU form enters Thumb.
    private static final int[] RAPU_ARM_ICACHE = {0xE92D4001, 0xE3A0E000, 0xE1A02000, 0xE0813000,
            0xE2433001, 0xEC423F05, 0xEE07EF95, 0xEC423F05, 0xEE07EF95, 0xEE072FF5, 0xEE07EF9A,
            0xEE07EF95};
    private boolean rapuInstructionCache(int function) {
        int p = function & ~1;
        if ((function & 1) != 0)
            return rom(p, 2);
        if ((p & 3) != 0 || !rom(p, 56))
            return false;
        for (int i = 0; i < RAPU_ARM_ICACHE.length; i++)
            if (word(p + 4 * i) != RAPU_ARM_ICACHE[i])
                return false;
        int call = word(p + 48), delta = (call << 8) >> 6;
        return (call & 0xFF000000) == 0xEB000000 && word(p + 52) == 0xE8BD8001
                && rom(p + 56 + delta, 4);
    }
    private void cache(int p) {
        if (!rom(p, 52))
            return;
        // Positive-length wrapper used by RAPU: preserve start and length,
        // call D-cache + outer-cache maintenance, then I-cache + outer cache,
        // and flush the prefetch buffer. Do not replace it with ARM926 loops.
        if ((word(p + 4) & 0xFFFFF000) == 0xE59FC000 && word(p + 8) == 0xE1A04000
                && word(p + 12) == 0xE1A05001 && word(p + 16) == 0xE12FFF3C
                && (word(p + 20) & 0xFFFFF000) == 0xE59FC000 && word(p + 24) == 0xE1A00004
                && word(p + 28) == 0xE1A01005 && word(p + 32) == 0xE12FFF3C
                && word(p + 36) == 0xE3A0C000 && word(p + 40) == 0xEE07CF95
                && word(p + 44) == 0xE8BD8070) {
            int l1 = p + 12 + (word(p + 4) & 4095), l2 = p + 28 + (word(p + 20) & 4095);
            if (rom(l1, 4) && rom(l2, 4) && (word(l1) & 3) == 0 && rom(word(l1), 4)
                    && rapuInstructionCache(word(l2))) {
                caches++;
                cacheSync = p;
            }
            return;
        }
        // Direct two-line ARM926 loop (6303); same (start,length) entry ABI.
        if (word(p + 4) == 0xE0805001 || word(p + 4) == 0xE0815000) {
            int i;
            for (i = 2; i < DIRECT_CACHE.length; i++)
                if (word(p + 4 * i) != DIRECT_CACHE[i])
                    break;
            if (i == DIRECT_CACHE.length) {
                caches++;
                cacheSync = p;
            }
            return;
        }
        // Shared ARM wrapper: preserve start, calculate end, call D then I
        // helpers. Normalize addition order and the optional end-1 instruction.
        if (word(p + 4) != 0xE1A04000 || (word(p + 8) != 0xE0815000 && word(p + 8) != 0xE0805001))
            return;
        int q = p + 12;
        if (word(q) == 0xE2455001)
            q += 4;
        if ((word(q) & 0xFFFFF000) != 0xE59FC000 || word(q + 4) != 0xE1A01005
                || word(q + 8) != 0xE12FFF3C || (word(q + 12) & 0xFFFFF000) != 0xE59FC000
                || word(q + 16) != 0xE1A00004 || word(q + 20) != 0xE1A01005
                || word(q + 24) != 0xE12FFF3C || word(q + 28) != 0xE8BD8070)
            return;
        int l1 = q + 8 + (word(q) & 4095), l2 = q + 20 + (word(q + 12) & 4095);
        if (rom(l1, 4) && rom(l2, 4) && (word(l1) & 1) == 1 && (word(l2) & 1) == 1
                && rom(word(l1) & ~1, 2) && rom(word(l2) & ~1, 2)) {
            caches++;
            cacheSync = p;
        }
    }
    void reset() {
        sites = pools = caches = error = 0;
        alloc = free = allocators = 0;
        site = variant = poolBytes = poolBase = cacheSync = currentMiB = 0;
        romEnd = profile.romEnd;
        ramEnd = profile.ramEnd;
    }
    boolean resolve(int[] memory, int viewBase) {
        view = memory;
        base = viewBase;
        reset();
        if (!discoverBounds()) {
            error = 105;
            return false;
        }
        // Discovery does not depend on model capability flags or CPU backend.
        // Every mapped profile receives every applicable content recipe.
        for (int p = profile.romStart; p - profile.romStart < romEnd - profile.romStart - 64;
                p += 4) {
            int w = word(p);
            if ((w & 65535) == 0x68E0) {
                candidate(p);
                candidate(p - 2);
            }
            if ((w >>> 16) == 0x68E0) {
                candidate(p + 2);
                candidate(p);
            }
            if ((w & 65535) == 0xB510)
                pool(p);
            if ((w >>> 16) == 0xB510)
                pool(p + 2);
            if (w == 0xE92D4070)
                cache(p);
            if (rom(w, 13) && ((word(w & ~3) >>> ((w & 3) * 8)) & 255) == 108)
                allocator(p);
        }
        if (sites != 1)
            error = 101;
        else if (pools != 1)
            error = 102;
        else if (caches != 1)
            error = 103;
        else if ((site & 1023) > 1024 - 30)
            error = 104;
        if (allocators != 1)
            alloc = free = 0;
        return error == 0;
    }
}
