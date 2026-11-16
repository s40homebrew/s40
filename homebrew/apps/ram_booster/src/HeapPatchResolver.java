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
                                FREE = "os_block_dealloc", MESSAGE_NEW = "CM_ISIMes_New_Zero",
                                MESSAGE_SEND = "CM_ComMgr_SendToObject";
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
    // Legacy zero-tail initializer. R7 was cleared just before this block;
    // preserve both zero stores and the following frame/literal loads. The
    // next LSLS overwrites our comparison flags before any conditional use.
    private static final int[] FRAME_LEGACY = {0x68e0, 0x6c19, 0x6588, 0x6960, 0x6c19, 0x65c8,
            0x69a0, 0x6c19, 0x6688, 0x6c18, 0x6607, 0x6c18, 0x6647, 0x6b98, 0x4900};
    private static final int[] PATCH_LEGACY = {0x68e0, 0x6588, 0x2805, 0x6960, 0xd101, 0x2000,
            0x0500, 0x65c8, 0x69a0, 0x6688, 0x660f, 0x664f, 0x46c0, 0x6b98, 0x4900};
    // Full-touch frame: cfg in R7, app in frame+44, tail fields at +74/+78.
    private static final int[] FRAME_TOUCH = {0x6c59, 0x68f8, 0x6588, 0x6c59, 0x6978, 0x65c8,
            0x6c59, 0x69b8, 0x6688, 0x6c59, 0x2002, 0x6748, 0x6c59, 0x6a78, 0x6788};
    private static final int[] PATCH_TOUCH = {0x68f8, 0x6588, 0x2805, 0xd102, 0x2000, 0x0500,
            0xe000, 0x6978, 0x65c8, 0x69b8, 0x6688, 0x2002, 0x6748, 0x6a78, 0x6788};
    final HeapBoosterProfile profile;
    final int[] original = new int[15];
    final int[] replacement = new int[15];
    int site, variant, currentMiB, poolBytes, poolBase, cacheSync;
    int poolProvider, poolClear, poolBaseLiteral, poolSizeInstruction;
    int fixedHeapState, fixedHeapConfig, fixedConfigSite, fixedResetHelper, fixedResetFlag;
    int fixedHeapBaseOffset, fixedHeapSizeOffset, fixedGrowthBytes, fixedQuotaLimit;
    private int fixedProvider, fixedInitializers, fixedConfigs, fixedResets, fixedConfigAddress;
    private int messageNew, messageSend;
    private int recipeAlloc, recipeFree, recipeReader, recipeAllocs, recipeFrees, recipeReaders;
    private int legacyScheduler, legacySchedulers, fixedInitializer;
    private static final int[] LEGACY_SCHEDULER = {0xb510, 0, 0, 0x2800, 0xd027, 0, 0, 0x2800,
            0xd124, 0, 0, 0x2800, 0xd105, 0x2001, 0, 0, 0, 0, 0xe011};
    private static final int[] LEGACY_RESET = {0xb510, 0, 0, 0x2800, 0xd00b, 0, 0, 0, 0, 0x1c43,
            0xd101, 0, 0, 0, 0, 0, 0, 0x2000, 0, 0, 0, 0, 0x2200, 0x2118, 0x2000, 0, 0, 0, 0,
            0xbd10};
    private static final int[] LEGACY_REINIT = {0xb510, 0, 0, 0x2800, 0xd10d, 0x2100, 0x207d,
            0x0380, 0, 0, 0x2005, 0, 0, 0, 0, 0x1c43, 0xd102, 0, 0, 0xbd10, 0x2800, 0xd1fc, 0, 0, 0,
            0, 0x2001, 0, 0, 0xbd10};
    private static final int[] LEGACY_VM_HEAD = {
            0xb5f0, 0x4e00, 0xb0ff, 0x2500, 0x1c30, 0xb0c6, 0x3050, 0x6035, 0x7005};
    private static final int[] LEGACY_VM_LINK = {0x6830, 0x2701, 0x4338, 0x6030, 0, 0, 0x95c4,
            0x4900, 0x4a00, 0x6808, 0x90c3, 0xa8c0, 0x6008, 0xa860, 0x90c0, 0x6810, 0x90c2, 0xa860,
            1, 1, 0x1c07, 0xd110, 0x4e00, 0x4668, 0x6030, 1, 1, 0x2800, 0xd106, 0, 0, 0, 0, 0x4900,
            0x6008};
    private static final int[] LEGACY_HEAP_WRAPPER = {
            0x4800, 0xb510, 0x2400, 0x6004, 0x4800, 0x6004, 0, 0, 0, 0, 0, 0};
    private static final int[] LEGACY_HEAP_INIT = {0xb570, 0x4d00, 0x2400, 0x606c, 1, 1, 0x4900,
            0x2000, 0x1c23, 0x00c2, 0x508b, 0x1852, 0x3001, 0x2874, 0x6053, 0xdbf8, 0x4a00, 0x2001,
            0x604a, 0x6028, 0xbd70};
    private static final int[] FIXED_RESET = {0xb570, 0x0005, 0x000e, 0x200c, 0, 0, 0x21db, 0x7041,
            0x0e29, 0x7101, 0x0c29, 0x7141, 0x0a29, 0x7181, 0x2100, 0x71c5, 0x7201, 0x7241, 0x0a31,
            0x7281, 0x0004, 0x72c6, 0, 0, 0x0001, 0x0023, 0x22e1, 0x207a, 0, 0, 0xbd70};
    int sites, pools, caches, error;
    int alloc, free, allocators;
    int romEnd, ramEnd;
    private int[] view;
    private int base;

    HeapPatchResolver(HeapBoosterProfile p) {
        profile = p;
        Rm217Memory.prepare();
        // Ensure classes/constants are initialized outside the forged interval.
        if (FAILURE.length() == 0 || SYSTEM.length() == 0 || ALLOC.length() == 0
                || FREE.length() == 0 || MESSAGE_NEW.length() == 0 || MESSAGE_SEND.length() == 0
                || STACK.length != PATCH.length)
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
        int count = 0;
        // Older RAP has no ELF descriptors. Its checked range walker points
        // into the readable boot prefix; the same recipe/table validator is
        // used by the Doom runtime.
        for (int p = profile.romStart;
                p - profile.romStart <= profile.mapPrefixBytes - Rm217Memory.RANGE.size; p += 2) {
            if (half(p) != 0xb570 || half(p + 2) != 0x1c04
                    || !Rm217Memory.RANGE.matches(view, base, p, profile.romStart,
                            profile.romStart + profile.mapPrefixBytes))
                continue;
            int at = p + 14;
            int tableAt = ((at + 4) & ~3) + ((half(at) & 255) << 2);
            if (tableAt - profile.romStart < 0
                    || tableAt - profile.romStart > profile.mapPrefixBytes - 4)
                return false;
            int end = RomRanges.readEnd(view, base, profile.romStart, profile.mapPrefixBytes,
                    profile.romEnd - profile.romStart, word(tableAt), 4095, 8, 4, 24, 8, 0xf0000000,
                    0xf0000000);
            if (end == 0)
                return false;
            romEnd = end;
            count++;
        }
        if (count != 0)
            return count == 1;
        // RAPU combined RAM + two-code-bank table, found only in the known
        // readable kernel prefix. The second bank is corroboration, not a
        // request to read it. A shorter build narrows every later code read.
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
        int a = 0, f = 0, newMessage = 0, sendMessage = 0;
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
            if (textAt(strings + off, MESSAGE_NEW)) {
                if (newMessage != 0)
                    return;
                newMessage = value;
            }
            if (textAt(strings + off, MESSAGE_SEND)) {
                if (sendMessage != 0)
                    return;
                sendMessage = value;
            }
        }
        if (a != 0 && f != 0) {
            allocators++;
            alloc = a;
            free = f;
            messageNew = newMessage;
            messageSend = sendMessage;
        }
    }
    private void memoryRecipe(int p, int first, int second) {
        if (first == 0xb5f0 && second == 0x1c06
                && Rm217Memory.ALLOC.matches(view, base, p, profile.romStart, romEnd)) {
            recipeAllocs++;
            recipeAlloc = p | 1;
        }
        if (first == 0xb5f0 && second == 0x1c05
                && Rm217Memory.FREE.matches(view, base, p, profile.romStart, romEnd)) {
            recipeFrees++;
            recipeFree = p | 1;
        }
        if (first == 0xb089 && second == 0x6819
                && Rm217Memory.FILE_READ.matches(view, base, p - 4, profile.romStart, romEnd)) {
            recipeReaders++;
            recipeReader = p - 4;
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
        if (form == 1 && half(p + 26) == 0x6b98) {
            candidateLegacy(p);
            return;
        }
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
    private void candidateTouch(int p) {
        if (!rom(p - 6, 40) || half(p - 6) != 0x6c59 || half(p - 4) != 0x68b8
                || half(p - 2) != 0x6188)
            return;
        boolean stock = true, patched = true;
        int quota = 0;
        for (int i = 0; i < 15; i++) {
            int h = half(p + 2 * i);
            if (h != FRAME_TOUCH[i])
                stock = false;
            if (i == 4) {
                quota = h & 255;
                if ((h & 0xff00) != 0x2000 || !choice(quota))
                    patched = false;
            } else if (h != PATCH_TOUCH[i])
                patched = false;
        }
        if ((!stock && !patched) || !failureString(p))
            return;
        sites++;
        site = p;
        variant = 6;
        currentMiB = stock ? 0 : quota;
        for (int i = 0; i < 15; i++)
            original[i] = FRAME_TOUCH[i];
    }
    static boolean choice(int mib) {
        return mib >= 3 && mib <= 8;
    }
    private void candidateLegacy(int p) {
        if (!rom(p - 16, 50) || half(p - 16) != 0x2700 || half(p - 14) != 0x6387
                || half(p + 30) != 0x0080 || half(p + 32) != 0x3104
                || (half(p + 28) & 0xff00) != 0x4900 || !profile.ram(literal(p + 28), 4))
            return;
        boolean stock = true, patched = true;
        for (int i = 0; i < 14; i++) {
            int h = half(p + 2 * i);
            if (h != FRAME_LEGACY[i])
                stock = false;
            if (i == 5 ? (h & 0xff00) != 0x2000 || !choice(h & 255) : h != PATCH_LEGACY[i])
                patched = false;
        }
        if ((!stock && !patched) || !failureString(p))
            return;
        sites++;
        site = p;
        variant = 7;
        currentMiB = stock ? 0 : half(p + 10) & 255;
        for (int i = 0; i < 15; i++)
            original[i] = FRAME_LEGACY[i];
        original[14] = half(p + 28);
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
        if (variant == 7) {
            for (int i = 0; i < 15; i++)
                replacement[i] = PATCH_LEGACY[i];
            replacement[5] |= mib;
            replacement[14] = original[14];
            return;
        }
        if (variant == 6) {
            for (int i = 0; i < 15; i++)
                replacement[i] = PATCH_TOUCH[i];
            replacement[4] |= mib;
            return;
        }
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
        if (!rom(p, 48) || half(p) != 0xB510
                || (half(p + 2) != 0x000C && half(p + 2) != 0x460C && half(p + 2) != 0x1c0c))
            return;
        int r0 = 0, r1 = 0, known = 0, at = p + 4;
        int baseLiteral = 0, sizeInstruction = 0;
        for (int i = 0; i < 6; i++, at += 2) {
            int h = half(at), reg = (h >>> 8) & 7;
            if ((h & 0xF800) == 0x4800 && reg <= 1) {
                int value = literal(at);
                if (reg == 0) {
                    r0 = value;
                    baseLiteral = ((at + 4) & ~3) + ((h & 255) << 2);
                } else
                    r1 = value;
                known |= 1 << reg;
            } else if ((h & 0xF800) == 0x2000 && reg <= 1) {
                if (reg == 0)
                    r0 = h & 255;
                else {
                    r1 = h & 255;
                    sizeInstruction = at;
                }
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
                poolProvider = p;
                poolClear = called | (mode == 0xF800 ? 1 : 0);
                poolBaseLiteral = baseLiteral;
                poolSizeInstruction = sizeInstruction;
                return;
            } else
                return;
        }
    }
    private int armLiteral(int p, int reg) {
        int w = word(p);
        if ((w & 0xff7ff000) != (0xe51f0000 | reg << 12))
            return 0;
        int at = p + 8 + ((w & 0x800000) != 0 ? w & 4095 : -(w & 4095));
        return rom(at, 4) ? word(at) : 0;
    }
    private void fixedHeapInitializer(int p) {
        // The ARM fixed-arena KVM initializer passes its size slot and output
        // base slot to the Thumb supplier. Bind the live state through that
        // call, rather than trusting a future supplier's advertised capacity.
        if (!rom(p, 40) || word(p) != 0xe92d4070 || word(p + 12) != 0xe5900000
                || word(p + 16) != 0xe2841010 || word(p + 20) != 0xe5840014
                || word(p + 24) != 0xe2840014 || word(p + 32) != 0xe5840018
                || word(p + 36) != 0xe5940010)
            return;
        int config = armLiteral(p + 4, 0), state = armLiteral(p + 8, 4);
        int call = word(p + 28);
        if (((config | state) & 3) != 0 || !profile.ram(config, 4) || !profile.ram(state, 64)
                || (call & 0xfe000000) != 0xfa000000)
            return;
        int supplier = p + 36 + ((call << 8) >> 6) + ((call >>> 23) & 2);
        if (!rom(supplier, 20) || half(supplier) != 0xb510
                || (half(supplier + 2) != 0x000c && half(supplier + 2) != 0x460c))
            return;
        fixedInitializers++;
        fixedInitializer = p;
        fixedProvider = supplier;
        fixedHeapState = state;
        fixedHeapConfig = config;
        fixedHeapBaseOffset = 24;
        fixedHeapSizeOffset = 20;
    }
    private void legacyFixedHeapInitializer(int p) {
        if (!rom(p, 80) || word(p + 4) != 0xe92d4010 || word(p + 12) != 0xe5900000
                || word(p + 16) != 0xe1a01004 || word(p + 20) != 0xe5840004
                || word(p + 24) != 0xe2840004 || word(p + 32) != 0xe5840008
                || word(p + 36) != 0xe5940000 || word(p + 40) != 0xe3500000
                || word(p + 64) != 0xe584000c || word(p + 68) != 0xe5941004
                || word(p + 72) != 0xe0801001 || word(p + 76) != 0xe5841010)
            return;
        int config = armLiteral(p, 0), state = armLiteral(p + 8, 4);
        int supplier = armCall(p + 28, true);
        if (((config | state) & 3) != 0 || !profile.ram(config, 4) || !profile.ram(state, 64)
                || !rom(supplier, 20) || half(supplier) != 0xb510 || half(supplier + 2) != 0x1c0c)
            return;
        fixedInitializers++;
        fixedInitializer = p;
        fixedProvider = supplier;
        fixedHeapState = state;
        fixedHeapConfig = config;
        fixedHeapBaseOffset = 8;
        fixedHeapSizeOffset = 4;
    }
    private void fixedConfig(int p) {
        if (!rom(p, 16) || (half(p) & 0xff00) != 0x4900 || half(p + 2) != 0x2000
                || !((half(p + 6) == 0x0492 && (half(p + 4) == 0x220f || half(p + 4) == 0x2224))
                        || (half(p + 6) == 0x04d2
                                && (half(p + 4) == 0x2205 || half(p + 4) == 0x220a)))
                || half(p + 8) != 0x7008 || half(p + 10) != 0x3914 || half(p + 12) != 0x600a)
            return;
        int address = literal(p) - 20;
        if ((address & 3) != 0 || !profile.ram(address, 24))
            return;
        fixedConfigs++;
        fixedConfigSite = p + 4;
        fixedConfigAddress = address;
    }
    private void fixedReset(int p) {
        if (!rom(p, FIXED_RESET.length * 2))
            return;
        for (int i = 0; i < FIXED_RESET.length; i++) {
            if (i == 4 || i == 22 || i == 28) {
                if (thumbTarget(p + i * 2) == 0)
                    return;
                i++;
            } else if (half(p + i * 2) != FIXED_RESET[i])
                return;
        }
        fixedResets++;
        fixedResetHelper = p | 1;
    }
    private boolean callPattern(int p, int[] code) {
        if (!rom(p, code.length * 2))
            return false;
        for (int i = 0; i < code.length; i++) {
            if (code[i] == 0 || code[i] == 1) {
                if ((code[i] == 0 ? thumbTarget(p + i * 2) : thumbArmCall(p + i * 2)) == 0)
                    return false;
                i++;
            } else if ((code[i] & 0xf8ff) == 0x4800) {
                if ((half(p + i * 2) & 0xff00) != code[i])
                    return false;
            } else if (half(p + i * 2) != code[i])
                return false;
        }
        return true;
    }
    private void legacyScheduler(int p) {
        if (!callPattern(p, LEGACY_SCHEDULER))
            return;
        legacySchedulers++;
        legacyScheduler = p;
    }
    private int byteGetter(int p, int opcode, int field) {
        if (!rom(p, 6) || (half(p) & 0xff00) != 0x4800 || half(p + 2) != opcode
                || half(p + 4) != 0x4770)
            return 0;
        int state = literal(p);
        return profile.ram(state, 16) ? state + field : 0;
    }
    private void legacyResetBinding() {
        if (legacySchedulers != 1 || fixedHeapBaseOffset != 8 || fixedInitializers != 1)
            return;
        int initialized = thumbTarget(legacyScheduler + 2);
        int getter = thumbTarget(legacyScheduler + 18);
        int reset = thumbTarget(legacyScheduler + 32);
        int initializedFlag = byteGetter(initialized, 0x7a00, 8);
        int resetFlag = byteGetter(getter, 0x7a40, 9);
        if (initializedFlag == 0 || resetFlag != initializedFlag + 1
                || !callPattern(reset, LEGACY_RESET) || thumbTarget(reset + 2) != initialized)
            return;
        int reinit = thumbTarget(reset + 54);
        if (!callPattern(reinit, LEGACY_REINIT) || thumbTarget(reinit + 2) != initialized)
            return;
        int vm = thumbTarget(reinit + 26);
        if (!callPattern(vm, LEGACY_VM_HEAD) || !callPattern(vm + 110, LEGACY_VM_LINK)
                || thumbTarget(vm + 118) != fixedConfigSite - 4
                || thumbArmCall(vm + 146) != thumbArmCall(vm + 160))
            return;
        int wrapper = thumbTarget(vm + 168);
        if (!callPattern(wrapper, LEGACY_HEAP_WRAPPER))
            return;
        int initialize = thumbTarget(wrapper + 20);
        if (!callPattern(initialize, LEGACY_HEAP_INIT)
                || thumbArmCall(initialize + 8) != fixedInitializer)
            return;
        fixedResetHelper = getter | 1;
        fixedResetFlag = resetFlag;
    }
    private int thumbTarget(int p) {
        int hi = half(p), lo = half(p + 2);
        if ((hi & 0xf800) != 0xf000 || (lo & 0xf800) != 0xf800)
            return 0;
        int delta = ((hi & 2047) << 12) | ((lo & 2047) << 1);
        if ((delta & 0x400000) != 0)
            delta -= 0x800000;
        int target = p + 4 + delta;
        return rom(target, 4) ? target : 0;
    }
    private int armCall(int p, boolean thumb) {
        int w = word(p);
        if (thumb ? (w & 0xfe000000) != 0xfa000000 : (w & 0xff000000) != 0xeb000000)
            return 0;
        int at = p + 8 + ((w << 8) >> 6) + (thumb ? (w >>> 23) & 2 : 0);
        return rom(at, 4) ? at : 0;
    }
    private int thumbArmCall(int p) {
        int hi = half(p), lo = half(p + 2);
        if ((hi & 0xf800) != 0xf000 || (lo & 0xf801) != 0xe800)
            return 0;
        int delta = ((hi & 2047) << 12) | ((lo & 2047) << 1);
        if ((delta & 0x400000) != 0)
            delta -= 0x800000;
        int at = (p + 4 + delta) & ~3;
        return rom(at, 4) ? at : 0;
    }
    private int armImmediate(int w) {
        int value = w & 255, shift = ((w >>> 8) & 15) * 2;
        return (value >>> shift) | (value << ((32 - shift) & 31));
    }
    private void growingPool(int p) {
        // KVM initialization -> pool wrapper -> VMM reservation. Unlike a
        // fixed arena, the committed extent grows. The reservation ceiling
        // minus its 16-byte header is the capacity, not the initial 16 KiB.
        if (!rom(p, 96) || word(p + 4) != 0xe92d4010 || armLiteral(p, 0) == 0
                || !profile.ram(armLiteral(p + 8, 4), 24) || word(p + 12) != 0xe5900000
                || word(p + 16) != 0xe2841000 || word(p + 20) != 0xe5840004
                || word(p + 24) != 0xe2840004 || word(p + 32) != 0xe5840008
                || word(p + 36) != 0xe5940000 || word(p + 40) != 0xe3500000
                || word(p + 44) != 0x1a000004 || word(p + 68) != 0xe5940008
                || word(p + 72) != 0xe584000c || word(p + 76) != 0xe5941004
                || word(p + 80) != 0xe0800001 || word(p + 84) != 0xe5840014
                || word(p + 88) != 0xe5840010)
            return;
        int wrapper = armCall(p + 28, true);
        if (!rom(wrapper, 28) || half(wrapper) != 0xb570 || half(wrapper + 2) != 0x4605
                || half(wrapper + 4) != 0x4602 || half(wrapper + 6) != 0x2001
                || half(wrapper + 8) != 0x0380 || (half(wrapper + 14) & 0xff00) != 0x4c00
                || half(wrapper + 16) != 0x6020 || half(wrapper + 18) != 0x6829
                || half(wrapper + 24) != 0x6820 || half(wrapper + 26) != 0xbd70)
            return;
        int supplier = thumbArmCall(wrapper + 10);
        if (!rom(supplier, 264) || word(supplier) != 0xe92d47f0 || word(supplier + 4) != 0xe1a05001
                || word(supplier + 8) != 0xe1a08002 || word(supplier + 12) != 0xe1a0a000
                || word(supplier + 16) != 0xe5914000 || word(supplier + 40) != 0xe3a02000
                || (word(supplier + 44) & 0xfffff000) != 0xe3a01000
                || word(supplier + 48) != 0xe1a00002 || armCall(supplier + 52, false) == 0
                || word(supplier + 64) != 0xe5854000 || word(supplier + 72) != 0xe28aa010
                || (word(supplier + 204) & 0xfffff000) != 0xe3a00000
                || word(supplier + 244) != 0x124a0010 || word(supplier + 252) != 0x12847010
                || word(supplier + 256) != 0x15880000)
            return;
        int capacity = armImmediate(word(supplier + 44));
        if (capacity != armImmediate(word(supplier + 204)) || capacity < 2 * 1048576
                || capacity > 32 * 1048576 || (capacity & 4095) != 0)
            return;
        pools++;
        poolBytes = capacity - 16;
        poolBase = 0; // Dynamically reserved virtual range, not a static base.
    }
    private void bareAllocators(int p) {
        // Some X-GOLD builds keep ELF absolute symbols/strings in ROM while
        // constructing library descriptors in RAM. Recognize the adjacent
        // named exports; never embed their values or their symbol-table size.
        if (!rom(p, 32) || word(p + 8) != 0 || word(p + 12) != 0xfff10012 || word(p + 24) != 0
                || word(p + 28) != 0xfff10012 || word(p) <= 0 || word(p) > 65535
                || word(p + 16) != word(p) + ALLOC.length() + 1 || !rom(word(p + 4) & ~1, 4)
                || !rom(word(p + 20) & ~1, 4))
            return;
        int limit = p + 65536;
        if (limit > romEnd)
            limit = romEnd;
        for (int a = p + 32; a < limit - ALLOC.length() - FREE.length() - 2; a++) {
            if (!textAt(a, ALLOC) || !textAt(a + ALLOC.length() + 1, FREE))
                continue;
            int strings = a - word(p);
            if (!rom(strings, 1) || ((word(strings & ~3) >>> ((strings & 3) * 8)) & 255) != 0)
                continue;
            allocators++;
            alloc = word(p + 4);
            free = word(p + 20);
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
    // Leaf ARM11 range synchronizer: clean D range, invalidate I range twice,
    // synchronize branch targets/prefetch, preserving incoming interrupt state.
    private static final int[] XGOLD_CACHE = {0xe0811000, 0xe2411001, 0xe1a02000, 0xe3a0c01f,
            0xe1c1100c, 0xe1c0000c, 0xe3a0c007, 0xe1c2200c, 0xe3a0c000, 0xee07cf9a, 0xe10f3000,
            0xf10c00c0, 0xec401f0c, 0xee07cf9a, 0xe12ff003, 0xee07cfba, 0xe10f3000, 0xf10c00c0,
            0xec401f05, 0xee07cf95, 0xec401f05, 0xee07cf95, 0xee072ff5, 0xee07cf9a, 0xee07cf95,
            0xe12ff003, 0xe12fff1e};
    private void cacheLeaf(int p) {
        if (!rom(p, XGOLD_CACHE.length * 4))
            return;
        for (int i = 0; i < XGOLD_CACHE.length; i++)
            if (word(p + i * 4) != XGOLD_CACHE[i])
                return;
        caches++;
        cacheSync = p;
    }
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
        poolProvider = poolClear = poolBaseLiteral = poolSizeInstruction = 0;
        fixedHeapState = fixedHeapConfig = fixedProvider = fixedInitializers = 0;
        fixedConfigSite = fixedResetHelper = fixedConfigs = fixedResets = fixedConfigAddress = 0;
        fixedHeapBaseOffset = fixedHeapSizeOffset = fixedGrowthBytes = fixedQuotaLimit = 0;
        fixedResetFlag = legacyScheduler = legacySchedulers = fixedInitializer = 0;
        messageNew = messageSend = 0;
        recipeAlloc = recipeFree = recipeReader = recipeAllocs = recipeFrees = recipeReaders = 0;
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
        // Keep the hot word pass in local variables. Masked index advancement
        // preserves word(address)'s unsigned subtraction at the view boundary.
        // Candidate helpers still validate their complete recipes.
        int start = profile.romStart, scanBytes = romEnd - start - 64;
        int pointerLimit = romEnd - start - 13, cacheSeed = XGOLD_CACHE[0];
        for (int offset = 0, index = (start - viewBase) >>> 2; offset < scanBytes;
                offset += 4, index = (index + 1) & 0x3fffffff) {
            int p = start + offset, w = memory[index], low = w & 65535, high = w >>> 16;
            // Group Thumb seeds by opcode byte. The adjacent instruction is
            // already in this word for aligned functions; reject unrelated
            // PUSH/LDR instructions before entering the full validators.
            switch (low >>> 8) {
                case 0x68:
                    if (low == 0x68e0) {
                        candidate(p);
                        candidate(p - 2);
                    } else if (low == 0x68f8) {
                        candidateTouch(p);
                        candidateTouch(p - 2);
                    }
                    break;
                case 0xb5:
                    if (low == 0xb510) {
                        if (high == 0x000c || high == 0x460c || high == 0x1c0c)
                            pool(p);
                        else if ((high & 0xf800) == 0xf000)
                            legacyScheduler(p);
                    } else if (low == 0xb570 && high == 0x0005)
                        fixedReset(p);
                    break;
                case 0x49:
                    if (high == 0x2000)
                        fixedConfig(p);
                    break;
            }
            // Thumb entry points may also start in the upper halfword. Read
            // the next word only for seeds that need that adjacent instruction.
            switch (high >>> 8) {
                case 0x68:
                    if (high == 0x68e0) {
                        candidate(p + 2);
                        candidate(p);
                    } else if (high == 0x68f8) {
                        candidateTouch(p + 2);
                        candidateTouch(p);
                    }
                    break;
                case 0xb5:
                    if (high == 0xb510) {
                        int next = memory[(index + 1) & 0x3fffffff] & 65535;
                        if (next == 0x000c || next == 0x460c || next == 0x1c0c)
                            pool(p + 2);
                        else if ((next & 0xf800) == 0xf000)
                            legacyScheduler(p + 2);
                    } else if (high == 0xb570
                            && (memory[(index + 1) & 0x3fffffff] & 65535) == 0x0005)
                        fixedReset(p + 2);
                    break;
                case 0x49:
                    if ((memory[(index + 1) & 0x3fffffff] & 65535) == 0x2000)
                        fixedConfig(p + 2);
                    break;
            }
            if (w == 0xE92D4070) {
                cache(p);
                fixedHeapInitializer(p);
            }
            if (w == cacheSeed)
                cacheLeaf(p);
            if (low == 0xb5f0 || low == 0xb089)
                memoryRecipe(p, low, high);
            if (high == 0xb5f0 || high == 0xb089)
                memoryRecipe(p + 2, high, memory[(index + 1) & 0x3fffffff] & 65535);
            if ((w & 0xff7ff000) == 0xe51f0000 && memory[(index + 1) & 0x3fffffff] == 0xe92d4010) {
                growingPool(p);
                legacyFixedHeapInitializer(p);
            }
            if (w == 0xfff10012)
                bareAllocators(p - 12);
            int pointerOffset = w - start;
            if (pointerOffset >= 0 && pointerOffset <= pointerLimit
                    && ((memory[((w & ~3) - viewBase) >>> 2] >>> ((w & 3) * 8)) & 255) == 108)
                allocator(p);
        }
        if (recipeAllocs == 1 && recipeFrees == 1 && recipeReaders == 1
                && thumbTarget(recipeReader + 168) == (recipeAlloc & ~1)
                && thumbTarget(recipeReader + 248) == (recipeFree & ~1)) {
            allocators++;
            alloc = recipeAlloc;
            free = recipeFree;
        }
        if (fixedInitializers != 1 || fixedProvider != poolProvider)
            fixedHeapState = fixedHeapConfig = 0;
        if (fixedConfigs != 1 || fixedConfigAddress != fixedHeapConfig)
            fixedConfigSite = 0;
        if (fixedHeapState != 0 && fixedConfigSite != 0 && poolBaseLiteral == poolProvider + 24
                && poolSizeInstruction == poolProvider + 4) {
            int shift = half(poolProvider + 8), configShift = half(fixedConfigSite + 2);
            if (fixedHeapBaseOffset == 8 && shift == 0x04c9 && configShift == 0x04d2
                    && (poolBytes == 0x280000 || poolBytes == 0x500000)) {
                fixedGrowthBytes = 0x500000;
                fixedQuotaLimit = 4;
            } else if (fixedHeapBaseOffset == 24 && shift == 0x0489 && configShift == 0x0492
                    && (poolBytes == 0x3c0000 || poolBytes == 0x900000)) {
                fixedGrowthBytes = 0x900000;
                fixedQuotaLimit = 8;
            }
        }
        int reset = fixedResetHelper & ~1;
        int getter = reset == 0 ? 0 : thumbTarget(reset + 44);
        if (fixedResets != 1 || thumbTarget(reset + 8) != (messageNew & ~1)
                || thumbTarget(reset + 56) != (messageSend & ~1) || !rom(getter, 4)
                || (half(getter) & 0xff00) != 0x4800 || half(getter + 2) != 0x4770
                || !profile.ram(literal(getter), 4))
            fixedResetHelper = 0;
        legacyResetBinding();
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
