/* SPDX-License-Identifier: MIT */
/** C3-00 family policy. Symbol addresses are always discovered from live ROM. */
final class Rm614Profile {
    private static final int HALFWORD_MASK = 0xFFFF;
    private static final int THUMB_OPCODE_MASK = 0xF800;
    private static final int THUMB_CALL = 0xF000;
    private static final int THUMB_LITERAL = 0x4800;
    private static final int THUMB_ADR = 0xA000;

    private Rm614Profile() {}

    static FirmwareProfile create() {
        FirmwareProfile.Layout layout = new FirmwareProfile.Layout();
        layout.romStart = 0x80130400;
        layout.romLimit = 0x80F00000;
        layout.ramStart = 0x80F00000;
        layout.ramLimit = 0x84000000;
        layout.bootBytes = 65536;
        layout.romPageMask = 4095;
        // Conservative page-aligned prefixes wholly present in the audited
        // 08.63 and 08.70 images. Do not scan RAM, the bank gap, or assume that
        // a flash container's padded EOF is a runtime memory-map boundary.
        // A future firmware with services outside these windows fails closed.
        layout.scanWindows = new int[] {0x80130400, 0x80EC0000, 0x85000400, 0x85900000};
        layout.descriptorBytes = 36;
        layout.descriptorHashOffset = 24;
        layout.descriptorStringsOffset = 28;
        layout.descriptorSymbolsOffset = 32;
        layout.kniReturnSearchRadius = 4096;
        layout.kniParamReturnOffset = 12;
        layout.kniReturnSpLdrOffset = 4;
        layout.taskReceiveCallOffset = 0;
        layout.taskVmTimerCallOffset = 24;
        layout.taskTimerTimeoutCallOffset = 36;
        layout.taskPhonetCallOffset = 62;
        layout.taskInternalQueueCallOffset = 66;
        layout.taskMsgCountCallOffset = 70;
        layout.taskSubscriptionsCallOffset = 90;
        layout.invokeBranchOffset = 24;
        layout.dispatchNativeEntryOffset = 48;
        layout.lcdReserveOwnerLdrOffset = 16;
        layout.lcdUpdateOwnerLdrOffset = 2;
        layout.lcdOwnerLiteralBias = 4;
        layout.lcdAcquireIdOffset = 36;
        layout.lcdReleaseIdOffset = 132;
        layout.lcdAcquireCallOffset = 38;
        layout.lcdReleaseCallOffset = 134;
        layout.displayWidth = 320;
        layout.displayHeight = 240;
        layout.inputLayout = FirmwareApi.INPUT_QWERTY;
        layout.timerMessageLast = 450;
        layout.ignoredTimer = -1;
        layout.specialTimer = 76;
        layout.taskSpecialCallOffset = 50;
        return new FirmwareProfile("RM-614", FirmwareProfiles.CARRIER_BOOTSTRAP,
                new String[] {"NokiaC3-00"}, createPatterns(), layout);
    }

    /**
     * C3-00 instruction recipes audited against RM-614 08.63. All arrays describe
     * code, never reference-ROM locations. Shared KNI/dispatcher recipes retain
     * the same offsets and are checked independently by FirmwareResolver.
     */
    private static FirmwareResolver.Pattern[] createPatterns() {
        // These families share the audited KNI/app/dispatcher recipes. Start
        // with a fresh array so C3 overrides cannot change the RM-443 profile.
        FirmwareResolver.Pattern[] shared = Rm443Profile.createPatterns();
        FirmwareResolver.Pattern[] patterns = new FirmwareResolver.Pattern[ResolverPatterns.COUNT];
        System.arraycopy(shared, 0, patterns, 0, shared.length);
        // ARM exception-vector prologue in the initial mapped page. This is
        // an identity check, not RM-443's ROM-range walker.
        patterns[ResolverPatterns.RANGE] =
                new FirmwareResolver.Pattern("ROM_VECTORS", 4, 0, 0xE14FD000,
                        new int[] {0xd000, 0xe14f, 0x0020, 0xe31d, 0xe006, 0xe24e, 0xe002, 0x028e,
                                0xd000, 0xe59f, 0xd000, 0xe59d, 0x00ca, 0xe35d, 0x0000, 0x0a00},
                        new int[] {0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff,
                                0xf000, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0x0000, 0xff00});
        patterns[ResolverPatterns.CACHE] = exact("CACHE", 4, 4,
                new int[] {0x4070, 0xe92d, 0x4000, 0xe1a0, 0x5000, 0xe081, 0xc018, 0xe59f, 0x1005,
                        0xe1a0, 0xff3c, 0xe12f, 0xc010, 0xe59f, 0x0004, 0xe1a0, 0x1005, 0xe1a0,
                        0xff3c, 0xe12f, 0x8070, 0xe8bd});
        patterns[ResolverPatterns.TASK_LOOP] = thumb("TASK_LOOP", 4,
                new int[] {0xf630, 0xe82e, 0x4669, 0x2800, 0x6069, 0xd01a, 0x0004, 0x21ff, 0x3cc0,
                        0x3103, 0x428c, 0xd80f, 0xf7ff, 0xf833, 0x2800, 0xd104, 0x0420, 0x0c00,
                        0xf630, 0xe820, 0xe00b, 0x0420, 0x0c00, 0x284c, 0xd107, 0xf7c5, 0xfe70,
                        0xe004, 0x7801, 0x2901, 0xd001, 0xf630, 0xe816, 0xf630, 0xe818, 0xf47f,
                        0xea40, 0x2800, 0xd105, 0xf7ff, 0xf80c, 0xf630, 0xe810, 0x2800, 0xd1f5,
                        0xf630, 0xe810, 0x2000, 0x6068, 0xe7cd});
        patterns[ResolverPatterns.EVENT_POP] = thumb("EVENT_POP", 26,
                new int[] {0xb5f8, 0x000d, 0x0016, 0x001f, 0xf64d, 0xfbb4, 0x0004, 0x2000, 0x2c00,
                        0xd01a, 0x68a1, 0x2900, 0xd017, 0x1e49, 0x60a1, 0x6820, 0x213c, 0x0100,
                        0x1900, 0x68c0, 0x6028, 0x6820, 0x0100, 0x1900, 0x6900, 0x6030, 0x6820,
                        0x0100, 0x1900, 0x6940, 0x6038, 0x6820, 0x1c40, 0xf656, 0xe8a4, 0x2001,
                        0x6021, 0xbdf8});
        patterns[ResolverPatterns.MSG_COUNT] = thumb("MSG_COUNT", 20,
                new int[] {0xb5f8, 0x2500, 0xf615, 0xf90c, 0x3040, 0x7ec0, 0x4e22, 0x1e40, 0x0600,
                        0x0e00, 0x2324, 0x4358, 0x6931, 0x1840, 0x6887, 0x2401, 0x0621, 0x0e09,
                        0x0038, 0xf614, 0xfbd5, 0x6800, 0x1c64, 0x1945, 0x2c04, 0xdbf5, 0x68f0,
                        0x06c0, 0xd52a});
        patterns[ResolverPatterns.VM_TIMER] = thumb("VM_TIMER", 2,
                new int[] {0x0001, 0x39c0, 0x0409, 0x0c09, 0x000a, 0x2000, 0x3940, 0xb510, 0x290b,
                        0xd803, 0x0008, 0xf000, 0xfa94, 0xe009, 0x2a4d, 0xd108, 0xf64d, 0xfc4a,
                        0x2800, 0xd103, 0x4951, 0x204d, 0xf5c2, 0xed5c, 0x2001, 0xbd10});
        patterns[ResolverPatterns.LCD_RESERVE] = thumb("LCD_RESERVE", 48,
                new int[] {0xb5f8, 0x0004, 0x2000, 0x2c02, 0x9000, 0xd301, 0xf152, 0xedc8, 0x4830,
                        0x00a7, 0x1f00, 0x183d, 0x3d40, 0x6be8, 0x1c40, 0xd001, 0xf152, 0xedbe,
                        0x20b2, 0xf020, 0xff65, 0xf271, 0xff6d, 0x4e28, 0x6870, 0x63e8, 0xf271,
                        0xff7a, 0xf01d, 0xee8a, 0xf225, 0xef2e, 0xa231, 0xca0c, 0xf225, 0xef48,
                        0x221e, 0x43d2, 0xf77a, 0xea96, 0xf226, 0xe830, 0x6871, 0x1840, 0x1c40,
                        0x2101, 0x0409, 0x4288, 0x6070, 0xdd01, 0x2000, 0x6070, 0x2c01, 0xd001,
                        0xf152, 0xed98, 0x4827, 0x9900, 0x2201, 0xf7fe, 0xfe06, 0x4915, 0x3108,
                        0x1879, 0x3940, 0x63c8, 0x20b2, 0xf020, 0xfecb, 0x6be8});
        maskImmediate(patterns[ResolverPatterns.LCD_RESERVE], 36);
        maskImmediate(patterns[ResolverPatterns.LCD_RESERVE], 132);
        patterns[ResolverPatterns.LCD_UPDATE] = thumb("LCD_UPDATE", 4,
                new int[] {0xb5ff, 0x4856, 0x008c, 0x1f00, 0x1820, 0x3840, 0x6bc0, 0x000e, 0x0017,
                        0x1c40, 0xb085, 0xd101, 0xf152, 0xee08, 0x4850, 0x380c, 0x1985, 0x3d20,
                        0x7fe8, 0xf020, 0xffaa, 0x980e, 0x990f, 0x2200});
        // The special timer builds a native message; it does not enter the Java VM.
        patterns[ResolverPatterns.SPECIAL_TIMER] = specialTimer();
        patterns[ResolverPatterns.BYTECODE_SYNC] = arm("BYTECODE_SYNC",
                new int[] {0xE5990004, 0xE1A01005, 0xEB000000, 0xE1A00007, 0xE28DD014, 0xE8BD8FF0},
                new int[] {-1, -1, 0xFF000000, -1, -1, -1});
        patterns[ResolverPatterns.DCACHE_VENEER] = thumb("DCACHE_VENEER", 10,
                new int[] {0xb510, 0xf000, 0xe800, 0xf000, 0xe800, 0x2000, 0xbd10});
        patterns[ResolverPatterns.ICACHE_VENEER] =
                thumb("ICACHE_VENEER", 6, new int[] {0xb510, 0xf000, 0xe800, 0x2000, 0xbd10});
        patterns[ResolverPatterns.DCACHE_RANGE] = arm("DCACHE_RANGE",
                new int[] {0xE59F2000, 0xE0513000, 0xE1520003, 0x9A000000, 0xE10F3000, 0xE38320C0,
                        0xE121F002, 0xE3C0001F, 0xE3C1101F, 0xEE070F3E, 0xE2800020, 0xE3500000,
                        0x0A000002, 0xE1500001, 0x9AFFFFF9, 0xE3A00000, 0xEE070F9A, 0xE121F003,
                        0xE12FFF1E},
                new int[] {0xFFFFF000, -1, -1, 0xFF000000, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                        -1, -1, -1, -1, -1});
        patterns[ResolverPatterns.ICACHE_RANGE] = arm("ICACHE_RANGE",
                new int[] {0xE10F3000, 0xE38320C0, 0xE121F002, 0xE3C0001F, 0xE3C1101F, 0xEE070F35,
                        0xE2800020, 0xE3500000, 0x0A000001, 0xE1500001, 0x9AFFFFF9, 0xE121F003,
                        0xE12FFF1E},
                null);
        patterns[ResolverPatterns.WRITE_BUFFER] =
                arm("WRITE_BUFFER", new int[] {0xE3A00000, 0xEE070F9A, 0xE12FFF1E}, null);
        patterns[ResolverPatterns.DCACHE_ALL] = arm("DCACHE_ALL",
                new int[] {0xE10F3000, 0xE38320C0, 0xE121F002, 0xEE17FF7E, 0x1AFFFFFD, 0xE3A00000,
                        0xEE070F9A, 0xE121F003, 0xE12FFF1E},
                null);
        return patterns;
    }

    private static FirmwareResolver.Pattern arm(String name, int[] instructions, int[] mask) {
        int[] words = new int[instructions.length * 2];
        int[] masks = new int[words.length];
        for (int i = 0; i < instructions.length; i++) {
            int m = mask == null ? -1 : mask[i];
            words[2 * i] = (instructions[i] & m) & HALFWORD_MASK;
            words[2 * i + 1] = (instructions[i] & m) >>> 16;
            masks[2 * i] = m & HALFWORD_MASK;
            masks[2 * i + 1] = m >>> 16;
        }
        // Only BYTECODE_SYNC is globally searched; its first word is exact.
        return new FirmwareResolver.Pattern(name, 4, 0, instructions[0], words, masks);
    }

    private static FirmwareResolver.Pattern specialTimer() {
        return thumb("SPECIAL_TIMER", 22,
                new int[] {0xb53e, 0x4d15, 0x6868, 0x2800, 0xd025, 0x200c, 0xf64e, 0xef22, 0x2400,
                        0x2800, 0xd01e, 0x2202, 0x70c2, 0x2211, 0x7042, 0x2233, 0x7084, 0x7002,
                        0x2201, 0x7102, 0x2208, 0x7142, 0x686a, 0x1d01, 0x0a13, 0x7183, 0x71c2,
                        0x7204, 0x714c, 0x718c, 0x0002, 0x71cc, 0x210c, 0x2000, 0x466b, 0xc307,
                        0x0002, 0x0001, 0x4818, 0x2302, 0xf666, 0xfe03, 0x606c, 0xbd3e});
    }

    private static void maskImmediate(FirmwareResolver.Pattern pattern, int offset) {
        pattern.masks[offset / 2] = 0xFF00;
        pattern.words[offset / 2] &= 0xFF00;
    }

    /** Decode only relocation-bearing operands before the no-allocation phase. */
    private static FirmwareResolver.Pattern thumb(String name, int anchor, int[] words) {
        int[] masks = fullMasks(words.length);
        for (int i = 0; i < words.length; i++) {
            int opcode = words[i] & THUMB_OPCODE_MASK;
            if (opcode == THUMB_CALL && i + 1 < words.length) {
                masks[i] = masks[i + 1] = THUMB_OPCODE_MASK;
                ++i;
            } else if (opcode == THUMB_LITERAL || opcode == THUMB_ADR) {
                masks[i] = 0xFF00;
            }
        }
        int exact = words[anchor / 2] | (words[anchor / 2 + 1] << 16);
        if (masks[anchor / 2] != HALFWORD_MASK || masks[anchor / 2 + 1] != HALFWORD_MASK)
            throw new IllegalArgumentException("Masked anchor");
        for (int i = 0; i < words.length; i++)
            words[i] &= masks[i];
        return new FirmwareResolver.Pattern(name, 2, anchor, exact, words, masks);
    }

    private static FirmwareResolver.Pattern exact(
            String name, int alignment, int anchor, int[] words) {
        return new FirmwareResolver.Pattern(name, alignment, anchor,
                words[anchor / 2] | (words[anchor / 2 + 1] << 16), words, fullMasks(words.length));
    }

    private static int[] fullMasks(int length) {
        int[] masks = new int[length];
        for (int i = 0; i < length; i++)
            masks[i] = HALFWORD_MASK;
        return masks;
    }
}
