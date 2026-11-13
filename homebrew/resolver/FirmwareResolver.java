/* SPDX-License-Identifier: MIT */
/**
 * Allocation-free firmware discovery for a runtime-selected, validated ABI profile.
 * Construct the profile and resolver before forging the heap.
 * The mapping window and VM ABI are model-family assumptions. No function or
 * global address from the reference firmware is present in this class.
 */
final class FirmwareResolver {
    // Stable diagnostic codes shown by the MIDlet and checked by host tests.
    static final int ERROR_NONE = 0;
    static final int ERROR_MEMORY_VIEW = 101;
    static final int ERROR_LITERAL_LOAD = 102;
    static final int ERROR_THUMB_CALL = 103;
    static final int ERROR_ARM_BRANCH = 104;
    static final int ERROR_RANGE_PATTERN = 110;
    static final int ERROR_ROM_BOUNDS = 111;
    static final int ERROR_PATTERN_COUNT = 112;
    static final int ERROR_DESCRIPTOR_COUNT = 114;
    static final int ERROR_IMPORT = 115;
    static final int ERROR_ABI = 116;
    static final int ERROR_UNBOUND_API = 117;
    static final int ERROR_SYMBOL_CHAIN = 118;

    private static final int PHASE_BOUNDS = 1;
    private static final int PHASE_SCAN = 2;
    private static final int PHASE_DESCRIPTORS = 3;
    private static final int PHASE_IMPORTS = 4;
    private static final int PHASE_ABI = 5;
    private static final int PHASE_READY = 6;
    static final int ABI_TASK_HELPERS = 1;
    static final int ABI_TASK_CALLS = 2;
    static final int ABI_NATIVE_DISPATCH = 3;
    static final int ABI_KNI_GLOBALS = 4;
    static final int ABI_KNI_RETURN = 5;
    static final int ABI_LCD_CODE = 6;
    static final int ABI_LCD_OWNER = 7;
    static final int ABI_APP_GLOBAL = 8;
    // Preserve the existing on-device diagnostic number.
    static final int ABI_LCD_RESOURCE = 9;

    // Shared ELF32/SysV formats. Nokia descriptor offsets live in the profile.
    private static final int ELF_HASH_HEADER_BYTES = 8;
    private static final int ELF_HASH_CHAIN_COUNT_OFFSET = 4;
    private static final int ELF_SYMBOL_BYTES = 16;
    private static final int ELF_SYMBOL_VALUE_OFFSET = 4;
    private static final int ELF_SYMBOL_TYPE_OFFSET = 12;
    private static final int ELF_ABSOLUTE_FUNCTION = 0xFFF10012;
    private static final int ELF_ABSOLUTE_OBJECT = 0xFFF10011;
    private static final int ELF_FUNCTION_INFO = 0x12;
    private static final int MAX_HASH_ENTRIES = 8192;
    private static final int SYSV_HASH_HIGH_BITS = 0xF0000000;

    // ARMv5/Thumb encodings: operands below are decoded, never called here.
    private static final int THUMB_OPCODE_MASK = 0xF800;
    private static final int THUMB_LITERAL_LOAD = 0x4800;
    private static final int THUMB_CALL_PREFIX = 0xF000;
    private static final int THUMB_CALL_SUFFIX = 0xF800;
    private static final int THUMB_EXCHANGE_SUFFIX = 0xE800;
    private static final int THUMB_BRANCH = 0xE000;
    private static final int THUMB_RETURN = 0x4770; // BX LR
    private static final int THUMB_CALL_IMMEDIATE_MASK = 0x7FF;
    private static final int THUMB_CALL_SIGN_BIT = 0x400000;
    private static final int THUMB_CALL_SIGN_EXTENSION = 0xFF800000;
    private static final int ARM_BRANCH_MASK = 0x0E000000;
    private static final int ARM_BRANCH = 0x0A000000;
    private static final int ARM_LITERAL_JUMP = 0xE51FF004; // LDR pc,[pc,#-4]
    private static final int MAX_VENEER_DEPTH = 4;
    private static final int THUMB_PC_BIAS = 4;
    private static final int ARM_PC_BIAS = 8;
    private static final int ANCHOR_BUCKETS = 256;
    private static final int LIBRARY_NAME_INITIAL = 'l';

    final FirmwareProfile profile;
    final int romStart;
    private final int bootBytes, maxBytes;
    private static final String SYSTEM = "libsystem.so";
    private static final String DL_SYSTEM = "libdl_system.so";
    final Pattern[] patterns;
    final int[] api = new int[FirmwareApi.COUNT];
    final int[] found;
    final int[] matches;
    private final int[] buckets = new int[ANCHOR_BUCKETS];
    private final int[] next;
    private final int[] anchors;
    // Touch generated strings before the caller's no-allocation interval.
    private final String[] imports = FirmwareApi.NAMES;
    private final String[] labels = FirmwareApi.LABELS;
    private int[] view;
    private int viewBase;
    private boolean cacheReady;
    boolean reused;
    int scanPasses;
    int romBytes, systemDescriptor, dlDescriptor, exportCount;
    int secondaryStart, secondaryBytes, specialTimerHandler;
    private int scanStart, scanBytes;
    int phase, detail, error, scanned;
    private int systemMatches, dlMatches;

    static final class Pattern {
        final String name;
        final int alignment, anchorOffset, anchor, size;
        final int[] words, masks;
        Pattern(String patternName, int instructionAlignment, int offset, int anchorWord,
                int[] data, int[] mask) {
            name = patternName;
            alignment = instructionAlignment;
            anchorOffset = offset;
            anchor = anchorWord;
            words = data;
            masks = mask;
            size = data.length * 2;
        }
    }

    FirmwareResolver(FirmwareProfile selectedProfile) {
        if (selectedProfile == null) {
            throw new IllegalArgumentException("A selected firmware profile is required");
        }
        profile = selectedProfile;
        romStart = profile.romStart;
        bootBytes = profile.bootBytes;
        maxBytes = profile.romLimit - romStart;
        // Signed scan comparisons need an ordered range within one sign half.
        if ((romStart & 3) != 0 || maxBytes < bootBytes
                || (romStart < 0) != (profile.romLimit < 0)) {
            throw new IllegalArgumentException("Unsupported ROM mapping");
        }
        patterns = profile.patterns;
        found = new int[patterns.length];
        matches = new int[patterns.length];
        next = new int[patterns.length];
        anchors = new int[patterns.length];
        // Search IDs start at 1; zero is the empty/end sentinel. This lets the
        // scan use single-operand ifeq/ifne rather than pushing -1 each time.
        for (int i = 1; i < patterns.length; i++) {
            if (!searched(i))
                continue;
            int bucket = patterns[i].anchor & 255;
            anchors[i] = patterns[i].anchor;
            next[i] = buckets[bucket];
            buckets[bucket] = i;
        }
    }

    private boolean searched(int index) {
        return index > 0
                && (index < ResolverPatterns.SEARCH_COUNT
                        || index == ResolverPatterns.BYTECODE_SYNC);
    }

    private boolean rom(int address, int size) {
        int offset = address - romStart;
        return size >= 0
                && ((offset >= 0 && offset <= romBytes - size)
                        || (secondaryBytes != 0 && address - secondaryStart >= 0
                                && address - secondaryStart <= secondaryBytes - size));
    }
    private boolean ram(int address) {
        return (address & 3) == 0 && address - profile.ramStart >= 0
                && address - profile.ramStart < profile.ramLimit - profile.ramStart;
    }
    private int word(int address) {
        if ((address & 3) != 0 || !rom(address, 4)) {
            error = ERROR_MEMORY_VIEW;
            return 0;
        }
        return view[(address - viewBase) >>> 2];
    }
    private int half(int address) {
        return (word(address & ~3) >>> ((address & 2) * 8)) & 65535;
    }
    private int octet(int address) {
        return (word(address & ~3) >>> ((address & 3) * 8)) & 255;
    }
    private boolean textAt(int address, String text) {
        if (!rom(address, text.length() + 1))
            return false;
        for (int i = 0; i < text.length(); i++)
            if (octet(address + i) != text.charAt(i))
                return false;
        return octet(address + text.length()) == 0;
    }
    boolean patternAt(int index, int address) {
        Pattern p = patterns[index];
        if ((address & (p.alignment - 1)) != 0 || !rom(address, p.size))
            return false;
        for (int i = 0; i < p.words.length; i++) {
            if ((half(address + 2 * i) & p.masks[i]) != p.words[i])
                return false;
        }
        return error == 0;
    }
    private int literal(int instruction) {
        int h = half(instruction);
        if ((h & THUMB_OPCODE_MASK) != THUMB_LITERAL_LOAD) {
            error = ERROR_LITERAL_LOAD;
            return 0;
        }
        return word(((instruction + THUMB_PC_BIAS) & ~3) + ((h & 255) << 2));
    }
    private int thumbCall(int address) {
        int first = half(address), second = half(address + 2);
        if ((first & THUMB_OPCODE_MASK) != THUMB_CALL_PREFIX
                || ((second & THUMB_OPCODE_MASK) != THUMB_CALL_SUFFIX
                        && (second & THUMB_OPCODE_MASK) != THUMB_EXCHANGE_SUFFIX)) {
            error = ERROR_THUMB_CALL;
            return 0;
        }
        int displacement = ((first & THUMB_CALL_IMMEDIATE_MASK) << 12)
                | ((second & THUMB_CALL_IMMEDIATE_MASK) << 1);
        if ((displacement & THUMB_CALL_SIGN_BIT) != 0)
            displacement |= THUMB_CALL_SIGN_EXTENSION;
        int target = address + THUMB_PC_BIAS + displacement;
        target = (second & THUMB_OPCODE_MASK) == THUMB_EXCHANGE_SUFFIX ? target & ~3 : target | 1;
        // Stock ARM interworking veneers are LDR pc,[pc,#-4]; .word target.
        for (int depth = 0; depth < MAX_VENEER_DEPTH; depth++) {
            if (!rom(target & ~1, 8)) {
                error = ERROR_THUMB_CALL;
                return 0;
            }
            if ((target & 3) != 0 || word(target) != ARM_LITERAL_JUMP)
                return target;
            target = word(target + 4);
        }
        error = ERROR_THUMB_CALL;
        return 0;
    }
    private int armBranch(int address) {
        int instruction = word(address);
        if ((instruction & ARM_BRANCH_MASK) != ARM_BRANCH) {
            error = ERROR_ARM_BRANCH;
            return 0;
        }
        return address + ARM_PC_BIAS + ((instruction << 8) >> 6);
    }
    private boolean fail(int code, int which) {
        error = code;
        detail = which;
        return false;
    }

    /**
     * A fresh live view is supplied every time; never retain Java heap pointers.
     * Only reads firmware. All state needed during the scan was preallocated.
     */
    boolean resolve(int[] source, int origin) {
        view = source;
        viewBase = origin;
        reused = false;
        scanned = 0;
        scanPasses = 0;
        error = 0;
        detail = 0;
        phase = PHASE_BOUNDS;
        try {
            if ((origin & 3) != 0 || !viewContains(bootBytes))
                return fail(ERROR_MEMORY_VIEW, 0);
            if (cacheReady) {
                cacheReady = false;
                if (revalidate()) {
                    cacheReady = true;
                    reused = true;
                    return true;
                }
            }
            cacheReady = false;
            if (!discover())
                return false;
            cacheReady = true;
            return true;
        } finally {
            view = null;
            viewBase = 0;
        }
    }

    private boolean viewContains(int bytes) {
        return viewContains(romStart, bytes);
    }

    private boolean viewContains(int start, int bytes) {
        int index = (start - viewBase) >>> 2;
        return bytes >= 0 && index <= view.length && (bytes >>> 2) <= view.length - index;
    }

    private boolean revalidate() {
        int previousBytes = romBytes;
        romBytes = bootBytes;
        secondaryBytes = 0;
        if (!patternAt(ResolverPatterns.RANGE, found[ResolverPatterns.RANGE]) || !readBounds()
                || romBytes != previousBytes)
            return false;
        for (int i = 1; i < patterns.length; i++)
            if (searched(i) && !patternAt(i, found[i]))
                return false;
        phase = PHASE_DESCRIPTORS;
        if (!rom(systemDescriptor, profile.descriptorBytes)
                || !rom(dlDescriptor, profile.descriptorBytes)
                || !textAt(word(systemDescriptor), SYSTEM) || !textAt(word(dlDescriptor), DL_SYSTEM)
                || !descriptorAt(systemDescriptor) || !descriptorAt(dlDescriptor))
            return false;
        // Resolve names again and repeat all ABI/reference checks. Only global
        // searches are skipped: this in-session cache was established against
        // the handset's immutable ROM, never loaded from an external address list.
        return bindAndValidate(true);
    }

    private boolean discover() {
        romBytes = bootBytes;
        secondaryBytes = 0;
        phase = PHASE_BOUNDS;
        detail = 0;
        error = 0;
        systemMatches = dlMatches = systemDescriptor = dlDescriptor = exportCount = 0;
        for (int i = 0; i < api.length; i++)
            api[i] = 0;
        for (int i = 0; i < found.length; i++) {
            found[i] = 0;
            matches[i] = 0;
        }
        // Locate the checked range-walking routine in the model's first 64 KiB.
        Pattern range = patterns[ResolverPatterns.RANGE];
        for (int a = romStart; a <= romStart + bootBytes - range.size; a += 2) {
            int anchor = a + range.anchorOffset;
            if (half(anchor) == (range.anchor & 65535) && half(anchor + 2) == (range.anchor >>> 16)
                    && patternAt(ResolverPatterns.RANGE, a)) {
                found[ResolverPatterns.RANGE] = a;
                ++matches[ResolverPatterns.RANGE];
            }
        }
        if (matches[ResolverPatterns.RANGE] != 1)
            return fail(ERROR_RANGE_PATTERN, 0);
        if (!readBounds())
            return false;
        phase = PHASE_SCAN;
        scanStart = romStart;
        scanBytes = romBytes;
        ++scanPasses;
        scanRom();
        if (secondaryBytes != 0 && error == 0) {
            scanStart = secondaryStart;
            scanBytes = secondaryBytes;
            scanRom();
        }
        if (error != 0)
            return false;
        for (int i = 1; i < patterns.length; i++)
            if (searched(i) && matches[i] != 1)
                return fail(ERROR_PATTERN_COUNT, i);
        phase = PHASE_DESCRIPTORS;
        if (systemMatches != 1 || dlMatches != 1)
            return fail(ERROR_DESCRIPTOR_COUNT, 0);
        return bindAndValidate(false);
    }

    private boolean readBounds() {
        if (profile.scanWindows != null) {
            // C3-00 has no RM-443 range walker. Probe only the two audited,
            // mapped ROM prefixes; never infer that the intervening addresses
            // are readable. Window limits are family policy, not ROM symbols.
            int[] windows = profile.scanWindows;
            if (windows.length != 4 || windows[0] != romStart || windows[1] <= romStart
                    || windows[1] > profile.romLimit || windows[2] <= windows[1]
                    || windows[3] <= windows[2] || (windows[0] & 3) != 0 || (windows[1] & 3) != 0
                    || (windows[2] & 3) != 0 || (windows[3] & 3) != 0 || windows[3] >= 0)
                return fail(ERROR_ROM_BOUNDS, 0);
            romBytes = windows[1] - windows[0];
            secondaryStart = windows[2];
            secondaryBytes = windows[3] - windows[2];
            if (!viewContains(romStart, romBytes) || !viewContains(secondaryStart, secondaryBytes))
                return fail(ERROR_MEMORY_VIEW, 0);
            return true;
        }
        int table = literal(found[ResolverPatterns.RANGE] + profile.rangeTableLdrOffset);
        if (!rom(table, profile.rangeMinTableBytes) || (table & 3) != 0)
            return fail(ERROR_ROM_BOUNDS, 0);
        int low = word(table), high = word(table + profile.rangeEndOffset), bytes = high - romStart;
        // The range table describes bytes, not MMU pages. 08.90 has a
        // word-aligned end before the flash container's trailing padding.
        if (low != (romStart & ~profile.romPageMask) || bytes < bootBytes || bytes > maxBytes
                || (high & 3) != 0)
            return fail(ERROR_ROM_BOUNDS, 0);
        // Validate the terminator without trusting a pointer outside the boot window.
        boolean terminated = false;
        for (int i = 1; i < profile.rangeMaxEntries; i++) {
            if (!rom(table + profile.rangeEntryBytes * i, profile.rangeEntryBytes))
                return fail(ERROR_ROM_BOUNDS, 0);
            int l = word(table + profile.rangeEntryBytes * i),
                h = word(table + profile.rangeEntryBytes * i + profile.rangeEndOffset);
            if (l == 0 && h == 0) {
                terminated = true;
                break;
            }
            if ((l & 3) != 0 || (l & profile.rangeRegionMask) != profile.rangeRomRegion
                    || h - l <= 0 || h - l > maxBytes)
                return fail(ERROR_ROM_BOUNDS, 0);
        }
        if (!terminated || error != 0)
            return fail(ERROR_ROM_BOUNDS, 0);
        romBytes = bytes;
        if (!viewContains(bytes))
            return fail(ERROR_MEMORY_VIEW, 0);
        return true;
    }

    private void scanRom() {
        // All common-path loads and anchor filtering stay in this method. Do not
        // reintroduce per-word interface calls or helpers for empty buckets.
        int[] data = view, heads = buckets;
        // Declaration order is intentional: the legacy compiler puts previous
        // in local slot 3, giving the loop compact iload_3/istore_3 bytecodes.
        int previous;
        int[] links = next, exact = anchors;
        int origin = viewBase, bytes = scanBytes, start = scanStart;
        // Built-in library names are in the primary bank on both audited ABIs.
        int namesStart = romStart, namesEnd = romStart + romBytes;
        int index = (start - origin) >>> 2, limit = index + (bytes >>> 2);
        previous = data[index++];
        for (; index < limit; index++) {
            int following = data[index];
            for (int i = heads[previous & 255]; i != 0; i = links[i])
                if (previous == exact[i])
                    recordMatch(i, origin + ((index - 1) << 2));
            // The low byte of a cross-word anchor is already in previous.
            // Build its full value only if that bucket can contain a match.
            int bucket = heads[(previous >>> 16) & 255];
            if (bucket != 0) {
                int cross = (previous >>> 16) | (following << 16);
                for (int i = bucket; i != 0; i = links[i])
                    if (cross == exact[i])
                        recordMatch(i, origin + ((index - 1) << 2) + 2);
            }
            // A descriptor begins with a ROM pointer to its library name.
            // Check that pointer's first byte inline. Only rare 'l' candidates
            // enter the complete string/descriptor validator. No assumption is
            // made about the five currently-zero descriptor fields.
            // Both bounds are in the same negative-int ROM window, so signed
            // comparisons are sufficient. Reconstruct addresses only on hits.
            if (previous >= namesStart && previous < namesEnd
                    && ((data[(previous - origin) >>> 2] >>> ((previous & 3) << 3)) & 255)
                            == LIBRARY_NAME_INITIAL)
                descriptorCandidate(origin + ((index - 1) << 2), previous);
            previous = following;
        }
        // An anchor can occupy the last complete word, but cannot straddle EOF.
        for (int i = heads[previous & 255]; i != 0; i = links[i])
            if (previous == exact[i])
                recordMatch(i, origin + ((index - 1) << 2));
        scanned += bytes;
    }

    private void recordMatch(int index, int address) {
        int start = address - patterns[index].anchorOffset;
        if (patternAt(index, start)) {
            found[index] = start;
            ++matches[index];
        }
    }

    private void descriptorCandidate(int address, int name) {
        if (!rom(address, profile.descriptorBytes))
            return;
        if (textAt(name, SYSTEM) && descriptorAt(address)) {
            systemDescriptor = address;
            ++systemMatches;
        } else if (textAt(name, DL_SYSTEM) && descriptorAt(address)) {
            dlDescriptor = address;
            ++dlMatches;
        }
    }

    private boolean bindAndValidate(boolean cached) {
        exportCount = word(word(systemDescriptor + profile.descriptorHashOffset)
                              + ELF_HASH_CHAIN_COUNT_OFFSET)
                - 1;
        phase = PHASE_IMPORTS;
        for (int i = 0; i < api.length; i++) {
            if (imports[i].length() == 0)
                continue;
            detail = i;
            api[i] = symbol(systemDescriptor, imports[i]);
            if (api[i] == 0 || error != 0)
                return fail(ERROR_IMPORT, i);
        }
        phase = PHASE_ABI;
        detail = 0;
        // KNI return stubs are duplicated throughout the ROM. Search the KNI
        // helper cluster and require the same CurrentThread global, rather than
        // picking an arbitrary global byte-pattern match.
        int param = found[ResolverPatterns.KNI_PARAM];
        int thread = literal(param), ret = found[ResolverPatterns.KNI_RETURN], returnMatches = 0;
        if (!ram(thread))
            return fail(ERROR_ABI, ABI_KNI_GLOBALS);
        if (cached) {
            if (patternAt(ResolverPatterns.KNI_RETURN, ret) && literal(ret) == thread)
                returnMatches = 1;
        } else {
            int first = param - profile.kniReturnSearchRadius,
                last = param + profile.kniReturnSearchRadius;
            if (first < romStart)
                first = romStart;
            if (last > romStart + romBytes - patterns[ResolverPatterns.KNI_RETURN].size)
                last = romStart + romBytes - patterns[ResolverPatterns.KNI_RETURN].size;
            for (int a = first; a <= last; a += 2) {
                if (patternAt(ResolverPatterns.KNI_RETURN, a) && literal(a) == thread) {
                    ret = a;
                    ++returnMatches;
                }
            }
        }
        found[ResolverPatterns.KNI_RETURN] = ret;
        matches[ResolverPatterns.KNI_RETURN] = returnMatches;
        if (returnMatches != 1)
            return fail(ERROR_PATTERN_COUNT, ResolverPatterns.KNI_RETURN);
        api[FirmwareApi.CACHE_SYNC] = found[ResolverPatterns.CACHE];
        api[FirmwareApi.KNI_PARAM] = found[ResolverPatterns.KNI_PARAM] | 1;
        api[FirmwareApi.KNI_RETURN] = found[ResolverPatterns.KNI_RETURN] | 1;
        api[FirmwareApi.FW_APP_ID] = found[ResolverPatterns.APP_ID] | 1;
        api[FirmwareApi.FW_EVENT_POP] = found[ResolverPatterns.EVENT_POP] | 1;
        int loop = found[ResolverPatterns.TASK_LOOP];
        api[FirmwareApi.FW_VM_TIMER] = thumbCall(loop + profile.taskVmTimerCallOffset);
        api[FirmwareApi.FW_MSG_COUNT] = thumbCall(loop + profile.taskMsgCountCallOffset);
        if (!patternAt(ResolverPatterns.VM_TIMER, api[FirmwareApi.FW_VM_TIMER] & ~1)
                || !patternAt(ResolverPatterns.MSG_COUNT, api[FirmwareApi.FW_MSG_COUNT] & ~1))
            return fail(ERROR_ABI, ABI_TASK_HELPERS);
        found[ResolverPatterns.VM_TIMER] = api[FirmwareApi.FW_VM_TIMER] & ~1;
        found[ResolverPatterns.MSG_COUNT] = api[FirmwareApi.FW_MSG_COUNT] & ~1;
        // Independent named-call relationships anchor this native task loop.
        if (thumbCall(loop + profile.taskReceiveCallOffset) != api[FirmwareApi.FW_OS_MSG_RECEIVE]
                || thumbCall(loop + profile.taskTimerTimeoutCallOffset)
                        != api[FirmwareApi.FW_CM_TIMER_TIMEOUT]
                || thumbCall(loop + profile.taskPhonetCallOffset)
                        != api[FirmwareApi.FW_CM_COMMGR_PROCESSPHONETMESSAGE]
                || thumbCall(loop + profile.taskInternalQueueCallOffset)
                        != api[FirmwareApi.FW_CM_COMMGR_PROCESSINTERNALQUEUE]
                || thumbCall(loop + profile.taskSubscriptionsCallOffset)
                        != api[FirmwareApi.FW_CM_COMMGR_SCANISISUBSCRIPTIONS])
            return fail(ERROR_ABI, ABI_TASK_CALLS);
        if (armBranch(found[ResolverPatterns.INVOKE] + profile.invokeBranchOffset)
                != found[ResolverPatterns.DISPATCH] + profile.dispatchNativeEntryOffset)
            return fail(ERROR_ABI, ABI_NATIVE_DISPATCH);
        if (FirmwareProfiles.CARRIER_BOOTSTRAP.equals(profile.bootstrapId)
                && !validateCarrierCache())
            return fail(ERROR_ABI, ABI_NATIVE_DISPATCH);
        specialTimerHandler = 0;
        if (profile.taskSpecialCallOffset >= 0) {
            specialTimerHandler = thumbCall(loop + profile.taskSpecialCallOffset);
            found[ResolverPatterns.SPECIAL_TIMER] = specialTimerHandler & ~1;
            if (!patternAt(ResolverPatterns.SPECIAL_TIMER, specialTimerHandler & ~1))
                return fail(ERROR_ABI, ABI_TASK_HELPERS);
        }
        int returnThread = literal(ret), operandSP = literal(ret + profile.kniReturnSpLdrOffset);
        if (!ram(thread) || thread != returnThread || !ram(operandSP) || thread == operandSP)
            return fail(ERROR_ABI, ABI_KNI_GLOBALS);
        // Both an inline BX LR and a branch to BX LR preserve this ABI.
        int branch = half(param + profile.kniParamReturnOffset);
        int tail = param + profile.kniParamReturnOffset + THUMB_PC_BIAS + ((branch << 21) >> 20);
        if (branch != THUMB_RETURN
                && ((branch & THUMB_OPCODE_MASK) != THUMB_BRANCH || !rom(tail, 2)
                        || half(tail) != THUMB_RETURN))
            return fail(ERROR_ABI, ABI_KNI_RETURN);
        int reserve = api[FirmwareApi.FW_GDI_DISPLAY_MASTER_RESERVE_ENABLE] & ~1;
        int update = api[FirmwareApi.FW_GDI_DISPLAY_MASTER_UPDATE] & ~1;
        if (!patternAt(ResolverPatterns.LCD_RESERVE, reserve)
                || !patternAt(ResolverPatterns.LCD_UPDATE, update))
            return fail(ERROR_ABI, ABI_LCD_CODE);
        found[ResolverPatterns.LCD_RESERVE] = reserve;
        found[ResolverPatterns.LCD_UPDATE] = update;
        // The internal resource number changes between releases. Require the
        // same number for acquire/release, and verify both named operations.
        if (half(reserve + profile.lcdAcquireIdOffset) != half(reserve + profile.lcdReleaseIdOffset)
                || thumbCall(reserve + profile.lcdAcquireCallOffset)
                        != symbol(systemDescriptor, "os_resource_reserve")
                || thumbCall(reserve + profile.lcdReleaseCallOffset)
                        != symbol(systemDescriptor, "os_resource_release"))
            return fail(ERROR_ABI, ABI_LCD_RESOURCE);
        // Both instruction recipes compute display-1 ownership as literal-28.
        int owner =
                literal(reserve + profile.lcdReserveOwnerLdrOffset) - profile.lcdOwnerLiteralBias;
        if (!ram(owner)
                || owner
                        != literal(update + profile.lcdUpdateOwnerLdrOffset)
                                - profile.lcdOwnerLiteralBias)
            return fail(ERROR_ABI, ABI_LCD_OWNER);
        api[FirmwareApi.FW_LCD_OWNER] = owner;
        if (!ram(literal(found[ResolverPatterns.APP_ID])))
            return fail(ERROR_ABI, ABI_APP_GLOBAL);
        if (error != 0)
            return false;
        for (int i = 0; i < api.length; i++)
            if (api[i] == 0)
                return fail(ERROR_UNBOUND_API, i);
        phase = PHASE_READY;
        detail = 0;
        return true;
    }

    private boolean validateCarrierCache() {
        int cache = api[FirmwareApi.CACHE_SYNC];
        // The checked bytecode-loader tail synchronizes method.code for its
        // real code length. This is what makes the Java switch executable.
        if (armBranch(found[ResolverPatterns.BYTECODE_SYNC] + 8) != cache || !rom(cache, 52))
            return false;
        int dataVeneer = word(cache + 44), instructionVeneer = word(cache + 48);
        if ((dataVeneer & 1) == 0 || (instructionVeneer & 1) == 0
                || !patternAt(ResolverPatterns.DCACHE_VENEER, dataVeneer & ~1)
                || !patternAt(ResolverPatterns.ICACHE_VENEER, instructionVeneer & ~1))
            return false;
        int dataRange = thumbCall((dataVeneer & ~1) + 2);
        int instructionRange = thumbCall((instructionVeneer & ~1) + 2);
        int drain = thumbCall((dataVeneer & ~1) + 6);
        if (!patternAt(ResolverPatterns.DCACHE_RANGE, dataRange)
                || !patternAt(ResolverPatterns.ICACHE_RANGE, instructionRange)
                || !patternAt(ResolverPatterns.WRITE_BUFFER, drain))
            return false;
        // Large images take the whole-D-cache path; validate that too, rather
        // than checking only the short loader's range-cleaning loop.
        found[ResolverPatterns.DCACHE_VENEER] = dataVeneer & ~1;
        found[ResolverPatterns.ICACHE_VENEER] = instructionVeneer & ~1;
        found[ResolverPatterns.DCACHE_RANGE] = dataRange;
        found[ResolverPatterns.ICACHE_RANGE] = instructionRange;
        found[ResolverPatterns.WRITE_BUFFER] = drain;
        found[ResolverPatterns.DCACHE_ALL] = armBranch(dataRange + 12);
        int threshold = word(dataRange + 8 + (word(dataRange) & 4095));
        return threshold == 32767
                && patternAt(ResolverPatterns.DCACHE_ALL, found[ResolverPatterns.DCACHE_ALL])
                && error == 0;
    }

    private boolean descriptorAt(int address) {
        int hash = word(address + profile.descriptorHashOffset),
            strings = word(address + profile.descriptorStringsOffset),
            symbols = word(address + profile.descriptorSymbolsOffset);
        if ((hash & 3) != 0 || (symbols & 3) != 0 || !rom(hash, ELF_HASH_HEADER_BYTES)
                || !rom(strings, 1))
            return false;
        int buckets = word(hash), count = word(hash + ELF_HASH_CHAIN_COUNT_OFFSET);
        if (buckets < 1 || buckets > MAX_HASH_ENTRIES || count < 2 || count > MAX_HASH_ENTRIES
                || !rom(hash, ELF_HASH_HEADER_BYTES + 4 * (buckets + count))
                || !rom(symbols, ELF_SYMBOL_BYTES * count))
            return false;
        for (int i = 0; i < buckets + count; i++) {
            int index = word(hash + ELF_HASH_HEADER_BYTES + 4 * i);
            if (index < 0 || index >= count)
                return false;
        }
        for (int i = 1; i < count; i++) {
            int record = symbols + ELF_SYMBOL_BYTES * i, offset = word(record),
                value = word(record + ELF_SYMBOL_VALUE_OFFSET),
                type = word(record + ELF_SYMBOL_TYPE_OFFSET);
            if (offset < 0 || offset >= romBytes || !rom(strings + offset, 1)
                    || (type != ELF_ABSOLUTE_FUNCTION && type != ELF_ABSOLUTE_OBJECT))
                return false;
            if ((type & 255) == ELF_FUNCTION_INFO && !rom(value & ~1, 2))
                return false;
        }
        return error == 0;
    }

    private int symbol(int descriptor, String name) {
        int hash = word(descriptor + profile.descriptorHashOffset),
            strings = word(descriptor + profile.descriptorStringsOffset),
            symbols = word(descriptor + profile.descriptorSymbolsOffset);
        int buckets = word(hash), count = word(hash + ELF_HASH_CHAIN_COUNT_OFFSET), h = 0;
        for (int i = 0; i < name.length(); i++) {
            h = (h << 4) + name.charAt(i);
            int high = h & SYSV_HASH_HIGH_BITS;
            h ^= high >>> 24;
            h &= ~high;
        }
        int index = word(hash + ELF_HASH_HEADER_BYTES + 4 * (h % buckets)), result = 0, steps = 0;
        while (index != 0) {
            if (index < 0 || index >= count || ++steps >= count) {
                error = ERROR_SYMBOL_CHAIN;
                return 0;
            }
            int record = symbols + ELF_SYMBOL_BYTES * index;
            if (textAt(strings + word(record), name)) {
                if (result != 0 || word(record + ELF_SYMBOL_TYPE_OFFSET) != ELF_ABSOLUTE_FUNCTION) {
                    error = ERROR_SYMBOL_CHAIN;
                    return 0;
                }
                result = word(record + ELF_SYMBOL_VALUE_OFFSET);
                if ((result & 1) == 0 && (result & 3) != 0) {
                    error = ERROR_SYMBOL_CHAIN;
                    return 0;
                }
            }
            index = word(hash + ELF_HASH_HEADER_BYTES + 4 * (buckets + index));
        }
        return result;
    }

    String failureName() {
        if (error == ERROR_RANGE_PATTERN || error == ERROR_PATTERN_COUNT)
            return patterns[detail].name;
        if (error == ERROR_IMPORT || error == ERROR_UNBOUND_API)
            return labels[detail];
        return "phase " + phase + " detail " + detail;
    }
}
