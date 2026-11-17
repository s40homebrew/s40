/* SPDX-License-Identifier: MIT */
/** C3-00 family policy. Symbol addresses are always discovered from live ROM. */
final class Rm614Profile {
    private static final int HALFWORD_MASK = 0xFFFF;

    private Rm614Profile() {}

    static FirmwareProfile create() {
        FirmwareProfile.Layout layout = new FirmwareProfile.Layout();
        layout.useKvmDiscovery();
        layout.useLibraryDescriptors();
        layout.romStart = 0x80130400;
        layout.romLimit = 0x80F00000;
        layout.ramStart = 0x80F00000;
        layout.ramLimit = 0x84000000;
        // Conservative page-aligned prefixes wholly present in the audited
        // 04.60, 08.63 and 08.70 images. Do not scan RAM, the bank gap, or assume that
        // a flash container's padded EOF is a runtime memory-map boundary.
        // A future firmware with services outside these windows fails closed.
        layout.scanWindows = new int[] {0x80130400, 0x80EC0000, 0x85000400, 0x85900000};
        layout.kniReturnSpLdrOffset = 4;
        layout.taskReceiveCallOffset = 0;
        layout.taskVmTimerCallOffset = 24;
        layout.taskTimerTimeoutCallOffset = 36;
        layout.taskPhonetCallOffset = 62;
        layout.taskInternalQueueCallOffset = 66;
        layout.taskMsgCountCallOffset = 70;
        layout.taskSubscriptionsCallOffset = 90;
        layout.lcdReserveOwnerLdrOffset = 16;
        layout.lcdUpdateOwnerLdrOffset = 2;
        layout.lcdOwnerLiteralBias = 4;
        layout.lcdAcquireIdOffset = 36;
        layout.lcdReleaseIdOffset = 132;
        layout.lcdAcquireCallOffset = 38;
        layout.lcdReleaseCallOffset = 134;
        layout.displayWidth = 320;
        layout.displayHeight = 240;
        layout.canvasLayout = FirmwareApi.CANVAS_CURRENT;
        layout.inputLayout = FirmwareApi.INPUT_QWERTY;
        layout.timerMessageLast = 450;
        layout.ignoredTimer = -1;
        layout.specialTimer = 76;
        layout.taskSpecialCallOffset = 50;
        return new FirmwareProfile("RM-614", FirmwareProfiles.CARRIER_BOOTSTRAP,
                new String[] {"NokiaC3-00"}, createPatterns(), layout);
    }

    /**
     * C3-00 recipes are generated from firmware/signatures/profiles/Rm614Patterns.S.
     * Shared KNI/dispatcher recipes retain the same offsets and are checked
     * independently by FirmwareResolver.
     */
    private static FirmwareResolver.Pattern[] createPatterns() {
        // These families share the audited KNI/app/dispatcher recipes. Start
        // with a fresh array so C3 overrides cannot change the RM-443 profile.
        FirmwareResolver.Pattern[] shared = Rm443Profile.createPatterns();
        FirmwareResolver.Pattern[] patterns = new FirmwareResolver.Pattern[ResolverPatterns.COUNT];
        System.arraycopy(shared, 0, patterns, 0, shared.length);
        // ARM exception-vector prologue in the initial mapped page. This is
        // an identity check, not RM-443's ROM-range walker.
        patterns[ResolverPatterns.RANGE] = Rm614Patterns.ROM_VECTORS();
        patterns[ResolverPatterns.CACHE] = Rm614Patterns.CACHE();
        patterns[ResolverPatterns.TASK_LOOP] = Rm614Patterns.TASK_LOOP();
        patterns[ResolverPatterns.EVENT_POP] = Rm614Patterns.EVENT_POP();
        patterns[ResolverPatterns.MSG_COUNT] = Rm614Patterns.MSG_COUNT();
        patterns[ResolverPatterns.VM_TIMER] = Rm614Patterns.VM_TIMER();
        patterns[ResolverPatterns.LCD_RESERVE] = Rm614Patterns.LCD_RESERVE();
        patterns[ResolverPatterns.LCD_UPDATE] = Rm614Patterns.LCD_UPDATE();
        // The special timer builds a native message; it does not enter the Java VM.
        patterns[ResolverPatterns.SPECIAL_TIMER] = specialTimer();
        patterns[ResolverPatterns.BYTECODE_SYNC] = Rm614Patterns.BYTECODE_SYNC();
        patterns[ResolverPatterns.DCACHE_VENEER] = Rm614Patterns.DCACHE_VENEER();
        patterns[ResolverPatterns.ICACHE_VENEER] = Rm614Patterns.ICACHE_VENEER();
        patterns[ResolverPatterns.DCACHE_RANGE] = Rm614Patterns.DCACHE_RANGE();
        patterns[ResolverPatterns.ICACHE_RANGE] = Rm614Patterns.ICACHE_RANGE();
        patterns[ResolverPatterns.WRITE_BUFFER] = Rm614Patterns.WRITE_BUFFER();
        patterns[ResolverPatterns.DCACHE_ALL] = Rm614Patterns.DCACHE_ALL();
        // Foreground Java UI refresh: validated source-copy and task-16 signal path.
        patterns[ResolverPatterns.UI_REFRESH] = Rm614Patterns.UI_REFRESH();
        patterns[ResolverPatterns.UI_COPY] = Rm614Patterns.UI_COPY();
        patterns[ResolverPatterns.UI_SIGNAL] = Rm614Patterns.UI_SIGNAL();
        return patterns;
    }

    private static FirmwareResolver.Pattern specialTimer() {
        FirmwareResolver.Pattern pattern = Rm614Patterns.SPECIAL_TIMER();
        // 04.60 uses SUBS r0,r0,#2 after MOVS r0,#0, whereas 08.63
        // loads its message destination from a literal. Preserve the complete
        // handler recipe and permit only that audited instruction alternative.
        int[] words = new int[pattern.words.length];
        int[] masks = new int[pattern.masks.length];
        System.arraycopy(pattern.words, 0, words, 0, words.length);
        System.arraycopy(pattern.masks, 0, masks, 0, masks.length);
        words[76 / 2] = 0x1e80;
        masks[76 / 2] = HALFWORD_MASK;
        pattern.alternative = new FirmwareResolver.Pattern(pattern.name, pattern.alignment,
                pattern.anchorOffset, pattern.anchor, words, masks);
        return pattern;
    }

}
