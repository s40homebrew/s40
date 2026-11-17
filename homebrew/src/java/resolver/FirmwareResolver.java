/* SPDX-License-Identifier: MIT */
/**
 * Allocation-free firmware discovery for a runtime-selected, validated ABI profile.
 * Construct the profile and resolver before forging the heap.
 * The mapping window and VM ABI are model-family assumptions. No function or
 * global address from the reference firmware is present in this class.
 */
final class FirmwareResolver implements RomSymbols.Reader {
    static final int CAP_AUDIO = 1, CAP_VIDEO = 2, CAP_BATTERY = 4, CAP_BRIDGE = 8;
    static final int DEFAULT_CAPABILITIES = CAP_AUDIO | CAP_VIDEO;
    private final int requestedCapabilities;

    // Stable diagnostic codes shown by the MIDlet and checked by host tests.
    static final int ERROR_NONE = 0;
    static final int ERROR_MEMORY_VIEW = 101;
    static final int ERROR_LITERAL_LOAD = 102;
    static final int ERROR_THUMB_CALL = 103;
    static final int ERROR_ARM_BRANCH = 104;
    static final int ERROR_THUMB_BRANCH = 105;
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
    private final FirmwareMemory serviceMemory;
    private final String family;
    private final int descriptorBytes, descriptorHashOffset;
    final int ramStart;
    int ramLimit;
    private final RomSymbols exports;
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
    private boolean serviceRegionsReady;
    boolean reused;
    int scanPasses;
    int romBytes, systemDescriptor, dlDescriptor, exportCount;
    int secondaryStart, secondaryBytes, specialTimerHandler, uiRefresh;
    private int scanStart, scanBytes;
    int phase, detail, error, scanned;
    private int systemMatches, dlMatches;

    static final class Pattern extends RomRecipe {
        Pattern alternative;
        Pattern(String patternName, int instructionAlignment, int offset, int anchorWord,
                int[] data, int[] mask) {
            super(patternName, instructionAlignment, offset, anchorWord, data, mask);
        }
        Pattern(RomRecipe recipe) {
            this(recipe.name, recipe.alignment, recipe.anchorOffset, recipe.anchor, recipe.words,
                    recipe.masks);
        }
    }

    FirmwareResolver(FirmwareProfile selectedProfile) {
        this(selectedProfile, DEFAULT_CAPABILITIES);
    }

    FirmwareResolver(FirmwareProfile selectedProfile, int capabilities) {
        this(selectedProfile, null, capabilities);
    }

    /** A service-only context cannot be passed to NativeSession or resolve(). */
    FirmwareResolver(FirmwareMemory memory, int capabilities) {
        this(null, memory, capabilities);
    }

    private FirmwareResolver(
            FirmwareProfile selectedProfile, FirmwareMemory memory, int capabilities) {
        if ((selectedProfile == null) == (memory == null)
                || (memory != null && (capabilities & ~CAP_AUDIO) != 0))
            throw new IllegalArgumentException("A launch profile or service memory is required");
        profile = selectedProfile;
        serviceMemory = memory;
        requestedCapabilities = capabilities;
        family = profile != null ? profile.id : memory.familyId;
        ramStart = profile != null ? profile.ramStart : memory.ramStart;
        ramLimit = profile != null ? profile.ramLimit : memory.ramLimit;
        descriptorBytes = profile != null   ? profile.descriptorBytes
                : memory.libraryDescriptors ? RomSymbols.DESCRIPTOR_BYTES
                                            : 0;
        descriptorHashOffset =
                profile != null ? profile.descriptorHashOffset : RomSymbols.HASH_OFFSET;
        exports = new RomSymbols(this, descriptorBytes, descriptorHashOffset,
                profile != null ? profile.descriptorStringsOffset : RomSymbols.STRINGS_OFFSET,
                profile != null ? profile.descriptorSymbolsOffset : RomSymbols.SYMBOLS_OFFSET);
        RomRanges.prepare();
        if ((capabilities & CAP_AUDIO) != 0)
            AudioBindings.prepare();
        if ((capabilities & CAP_VIDEO) != 0)
            VideoBindings.prepare();
        if ((capabilities & CAP_BATTERY) != 0)
            BatteryBindings.prepare();
        if ((capabilities & CAP_BRIDGE) != 0)
            BridgeBindings.prepare();
        if (profile != null && descriptorBytes == 0)
            Rm217Bindings.prepare();
        romStart = profile != null ? profile.romStart : memory.romStart;
        bootBytes = profile != null ? profile.bootBytes : memory.bootBytes;
        int romLimit = profile != null ? profile.romLimit : memory.romLimit;
        maxBytes = romLimit - romStart;
        // Signed scan comparisons need an ordered range within one sign half.
        if ((romStart & 3) != 0 || maxBytes < bootBytes || (romStart < 0) != (romLimit < 0)) {
            throw new IllegalArgumentException("Unsupported ROM mapping");
        }
        patterns = profile != null ? profile.patterns : new Pattern[0];
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
        return index > 0 && patterns[index] != null
                && (profile.descriptorBytes == 0
                                ? index != ResolverPatterns.KNI_RETURN
                                        && index <= Rm217Profile.TASK_OWN_ID
                                : (index < ResolverPatterns.SEARCH_COUNT
                                          || index == ResolverPatterns.BYTECODE_SYNC
                                          || index == ResolverPatterns.UI_REFRESH));
    }

    boolean rom(int address, int size) {
        int offset = address - romStart;
        return size >= 0
                && ((offset >= 0 && offset <= romBytes - size)
                        || (secondaryBytes != 0 && address - secondaryStart >= 0
                                && address - secondaryStart <= secondaryBytes - size));
    }
    boolean ram(int address) {
        return ram(address, NativeProtocol.WORD_BYTES);
    }
    boolean ram(int address, int bytes) {
        return (address & 3) == 0 && ramBytes(address, bytes);
    }
    int ramBytes() {
        return ramLimit - ramStart;
    }
    boolean ramBytes(int address, int bytes) {
        int offset = address - ramStart;
        return bytes > 0 && offset >= 0 && offset <= ramLimit - ramStart - bytes;
    }
    String familyId() {
        return family;
    }
    boolean hasExports() {
        return descriptorBytes != 0;
    }
    boolean serviceOnly() {
        return serviceMemory != null;
    }

    int word(int address) {
        if ((address & 3) != 0 || !rom(address, 4)) {
            error = ERROR_MEMORY_VIEW;
            return 0;
        }
        return view[(address - viewBase) >>> 2];
    }
    int half(int address) {
        return (word(address & ~3) >>> ((address & 2) * 8)) & 65535;
    }
    int byteAt(int address) {
        return (word(address & ~3) >>> ((address & 3) * 8)) & 255;
    }
    boolean textAt(int address, String text) {
        if (!rom(address, text.length() + 1))
            return false;
        for (int i = 0; i < text.length(); i++)
            if (byteAt(address + i) != text.charAt(i))
                return false;
        return byteAt(address + text.length()) == 0;
    }
    boolean patternAt(int index, int address) {
        Pattern p = patterns[index];
        while (p != null) {
            if ((address & (p.alignment - 1)) != 0 || !rom(address, p.size)) {
                p = p.alternative;
                continue;
            }
            boolean matched = true;
            for (int i = 0; i < p.words.length; i++) {
                if ((half(address + 2 * i) & p.masks[i]) != p.words[i]) {
                    matched = false;
                    break;
                }
            }
            if (matched)
                return error == 0;
            p = p.alternative;
        }
        return false;
    }
    int literal(int instruction) {
        int h = half(instruction);
        if ((h & THUMB_OPCODE_MASK) != THUMB_LITERAL_LOAD) {
            error = ERROR_LITERAL_LOAD;
            return 0;
        }
        return word(((instruction + THUMB_PC_BIAS) & ~3) + ((h & 255) << 2));
    }
    int thumbCall(int address) {
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
            if (((target & 1) == 0 && (target & 3) != 0) || !rom(target & ~1, 8)) {
                error = ERROR_THUMB_CALL;
                return 0;
            }
            int entry = target & ~1;
            if ((target & 1) != 0 && (entry & 3) == 0 && rom(entry, 12) && word(entry) == 0x46C04778
                    && word(entry + 4) == ARM_LITERAL_JUMP) {
                target = word(entry + 8);
            } else if ((target & 3) == 0 && word(target) == ARM_LITERAL_JUMP) {
                target = word(target + 4);
            } else {
                return target;
            }
        }
        error = ERROR_THUMB_CALL;
        return 0;
    }
    /** Decode an unconditional Thumb B; returns its even instruction address. */
    int thumbBranch(int address) {
        int instruction = half(address);
        if ((address & 1) != 0 || (instruction & THUMB_OPCODE_MASK) != THUMB_BRANCH) {
            error = ERROR_THUMB_BRANCH;
            return 0;
        }
        int target = address + THUMB_PC_BIAS + ((instruction << 21) >> 20);
        if (!rom(target, 2)) {
            error = ERROR_THUMB_BRANCH;
            return 0;
        }
        return target;
    }

    int armBranch(int address) {
        int instruction = word(address);
        if ((instruction & ARM_BRANCH_MASK) != ARM_BRANCH) {
            error = ERROR_ARM_BRANCH;
            return 0;
        }
        return address + ARM_PC_BIAS + ((instruction << 8) >> 6);
    }
    /** Optional services share core discovery's bounds and instruction decoders. */
    boolean matches(int address, RomRecipe recipe) {
        if (recipe == null || (address & (recipe.alignment - 1)) != 0 || !rom(address, recipe.size))
            return false;
        for (int i = 0; i < recipe.words.length; i++)
            if ((half(address + 2 * i) & recipe.masks[i]) != recipe.words[i])
                return false;
        return error == 0;
    }

    /** Search only one already-validated ROM region; ambiguity is unavailable. */
    int findUnique(RomRecipe recipe, int start, int bytes) {
        if (recipe == null || !rom(start, bytes) || bytes < recipe.size
                || (recipe.alignment != 2 && recipe.alignment != 4) || recipe.anchorOffset < 0
                || (recipe.anchorOffset & 1) != 0 || recipe.anchorOffset > recipe.size - 4)
            return 0;
        int result = 0;
        int offset = (-start) & (recipe.alignment - 1);
        for (; offset <= bytes - recipe.size; offset += recipe.alignment) {
            int address = start + offset, anchor = address + recipe.anchorOffset;
            // The candidate span is checked above. Keep the scan's common path
            // as direct array reads, as in the core recipe scanner.
            int index = ((anchor & ~3) - viewBase) >>> 2;
            int value = view[index];
            if ((anchor & 2) != 0)
                value = (value >>> 16) | (view[index + 1] << 16);
            if (value == recipe.anchor && matches(address, recipe)) {
                if (result != 0)
                    return 0;
                result = address;
            }
        }
        return result;
    }

    /** Shared optional batch scanner; no interface calls for unmatched anchors. */
    void scan(RomSearch search, int start, int bytes) {
        if (!rom(start, bytes) || (start & 3) != 0 || (bytes & 3) != 0) {
            error = ERROR_MEMORY_VIEW;
            return;
        }
        int index = (start - viewBase) >>> 2;
        int limit = index + (bytes >>> 2);
        int[] heads = search.heads;
        for (int i = index; i < limit; i++) {
            int word = view[i], address = start + ((i - index) << 2);
            if (heads[word & 255] != 0)
                search.candidate(this, address, word);
            if (i + 1 < limit) {
                int shifted = (word >>> 16) | (view[i + 1] << 16);
                if (heads[shifted & 255] != 0)
                    search.candidate(this, address + 2, shifted);
            }
        }
        scanned += bytes;
        scanPasses++;
    }

    /** Decode a PC-relative ARM LDR of the expected destination register. */
    int armLiteral(int address, int register) {
        int instruction = word(address);
        if (register < 0 || register > 15
                || (instruction & 0xFF7FF000) != (0xE51F0000 | (register << 12))) {
            error = ERROR_LITERAL_LOAD;
            return 0;
        }
        int displacement = instruction & 4095;
        int slot = address + ARM_PC_BIAS
                + ((instruction & 0x00800000) != 0 ? displacement : -displacement);
        return word(slot);
    }

    int export(String name) {
        return systemDescriptor == 0 ? 0 : symbol(systemDescriptor, name);
    }

    int exportObject(String name) {
        return systemDescriptor == 0 ? 0 : symbol(systemDescriptor, name, RomSymbols.OBJECT);
    }

    boolean fail(int code, int which) {
        error = code;
        detail = which;
        return false;
    }

    /**
     * A fresh live view is supplied every time; never retain Java heap pointers.
     * Only reads firmware. All state needed during the scan was preallocated.
     */
    boolean resolve(int[] source, int origin) {
        clearBindings(0, api.length);
        if (profile == null)
            return fail(ERROR_ABI, 0);
        view = source;
        viewBase = origin;
        reused = false;
        scanned = 0;
        scanPasses = 0;
        error = 0;
        detail = 0;
        phase = PHASE_BOUNDS;
        boolean valid = false;
        try {
            if ((origin & 3) != 0 || !viewContains(bootBytes))
                return fail(ERROR_MEMORY_VIEW, 0);
            if (cacheReady) {
                cacheReady = false;
                if (revalidate()) {
                    cacheReady = true;
                    reused = true;
                    resolveOptional();
                    valid = true;
                    return true;
                }
            }
            cacheReady = false;
            if (!discover())
                return false;
            cacheReady = true;
            resolveOptional();
            valid = true;
            return true;
        } finally {
            if (!valid)
                clearBindings(0, api.length);
            view = null;
            viewBase = 0;
        }
    }

    /** A missing optional capability never changes a validated core result. */
    private void resolveOptional() {
        resolveOptional(CAP_AUDIO, FirmwareApi.AUDIO_FIRST, FirmwareApi.AUDIO_LIMIT);
        resolveOptional(CAP_VIDEO, FirmwareApi.VIDEO_FIRST, FirmwareApi.VIDEO_LIMIT);
        resolveOptional(CAP_BATTERY, FirmwareApi.BATTERY_FIRST, FirmwareApi.BATTERY_LIMIT);
        resolveOptional(CAP_BRIDGE, FirmwareApi.BRIDGE_FIRST, FirmwareApi.BRIDGE_LIMIT);
    }

    private void resolveOptional(int capability, int first, int limit) {
        clearBindings(first, limit);
        if ((requestedCapabilities & capability) == 0)
            return;
        int savedError = error, savedDetail = detail, savedPhase = phase;
        boolean valid = false;
        switch (capability) {
            case CAP_AUDIO:
                valid = AudioBindings.bind(this);
                break;
            case CAP_VIDEO:
                valid = VideoBindings.bind(this);
                break;
            case CAP_BATTERY:
                valid = BatteryBindings.bind(this);
                break;
            case CAP_BRIDGE:
                valid = BridgeBindings.bind(this);
                break;
        }
        if (!valid || error != 0)
            clearBindings(first, limit);
        error = savedError;
        detail = savedDetail;
        phase = savedPhase;
    }

    private void clearBindings(int first, int limit) {
        for (int i = first; i < limit; i++)
            api[i] = 0;
    }

    /** Publish discovered service regions only within their conservative windows. */
    boolean setRomRegions(int firstBytes, int secondBytes) {
        if (serviceMemory == null || firstBytes < bootBytes || firstBytes > maxBytes
                || (firstBytes & 3) != 0 || secondBytes < 0 || (secondBytes & 3) != 0
                || (secondBytes != 0
                        && (serviceMemory.secondaryStart == 0
                                || secondBytes > serviceMemory.secondaryLimit
                                                - serviceMemory.secondaryStart))
                || !viewContains(romStart, firstBytes)
                || (secondBytes != 0 && !viewContains(serviceMemory.secondaryStart, secondBytes)))
            return fail(ERROR_ROM_BOUNDS, 0);
        romBytes = firstBytes;
        secondaryStart = serviceMemory.secondaryStart;
        secondaryBytes = secondBytes;
        serviceRegionsReady = true;
        return true;
    }

    boolean setRamLimit(int limit) {
        if (serviceMemory == null || (limit & 3) != 0 || limit - ramStart <= 0
                || limit - ramStart > serviceMemory.ramLimit - ramStart)
            return fail(ERROR_ROM_BOUNDS, 0);
        ramLimit = limit;
        return true;
    }

    /** Discover optional services for a separately bootstrapped native port. */
    boolean resolveServices(int[] source, int origin) {
        clearBindings(0, api.length);
        if (serviceMemory == null)
            return fail(ERROR_ABI, 0);
        view = source;
        viewBase = origin;
        reused = false;
        scanned = scanPasses = error = detail = 0;
        phase = PHASE_BOUNDS;
        int oldBytes = romBytes, oldSecond = secondaryStart, oldSecondBytes = secondaryBytes;
        boolean valid = false;
        try {
            romBytes = bootBytes;
            secondaryBytes = 0;
            serviceRegionsReady = false;
            ramLimit = serviceMemory.ramLimit;
            if ((origin & 3) != 0 || !viewContains(bootBytes) || !serviceMemory.discover(this)
                    || !serviceRegionsReady || error != 0) {
                cacheReady = false;
                return fail(ERROR_ROM_BOUNDS, 0);
            }
            boolean same = cacheReady && oldBytes == romBytes && oldSecond == secondaryStart
                    && oldSecondBytes == secondaryBytes;
            if (hasExports()) {
                if (same && rom(systemDescriptor, descriptorBytes)
                        && textAt(word(systemDescriptor), SYSTEM)
                        && descriptorAt(systemDescriptor)) {
                    reused = true;
                } else {
                    error = 0;
                    systemMatches = dlMatches = systemDescriptor = dlDescriptor = 0;
                    scanStart = romStart;
                    scanBytes = romBytes;
                    ++scanPasses;
                    scanRom();
                    if (secondaryBytes != 0 && error == 0) {
                        scanStart = secondaryStart;
                        scanBytes = secondaryBytes;
                        ++scanPasses;
                        scanRom();
                    }
                    if (systemMatches != 1 || error != 0) {
                        cacheReady = false;
                        return fail(ERROR_DESCRIPTOR_COUNT, 0);
                    }
                }
                exportCount = word(word(systemDescriptor + descriptorHashOffset) + 4) - 1;
                for (int i = 0; i < FirmwareApi.EXPORT_COUNT; i++) {
                    api[i] = export(imports[i]);
                    if (error != 0) {
                        cacheReady = false;
                        return fail(ERROR_IMPORT, i);
                    }
                }
            } else {
                reused = same;
            }
            cacheReady = true;
            phase = PHASE_READY;
            resolveOptional();
            valid = true;
            return true;
        } finally {
            if (!valid)
                clearBindings(0, api.length);
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
        if (profile.descriptorBytes == 0)
            return bindAndValidate(true);
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
        if (profile.descriptorBytes != 0 && (systemMatches != 1 || dlMatches != 1))
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
        int high = RomRanges.readEnd(view, viewBase, romStart, bootBytes, maxBytes, table,
                profile.romPageMask, profile.rangeEntryBytes, profile.rangeEndOffset,
                profile.rangeMinTableBytes, profile.rangeMaxEntries, profile.rangeRegionMask,
                profile.rangeRomRegion);
        if (high == 0 || error != 0)
            return fail(ERROR_ROM_BOUNDS, 0);
        int bytes = high - romStart;
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
            if (descriptorBytes != 0 && previous >= namesStart && previous < namesEnd
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
        if (!rom(address, descriptorBytes))
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
        if (profile.descriptorBytes == 0) {
            phase = PHASE_ABI;
            if (!Rm217Bindings.bind(this, cached))
                return false;
            phase = PHASE_READY;
            detail = 0;
            return true;
        }
        exportCount = word(word(systemDescriptor + profile.descriptorHashOffset) + 4) - 1;
        phase = PHASE_IMPORTS;
        for (int i = 0; i < FirmwareApi.REQUIRED_COUNT; i++) {
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
        uiRefresh = 0;
        if (patterns.length > ResolverPatterns.UI_REFRESH) {
            int refresh = found[ResolverPatterns.UI_REFRESH];
            int copy = thumbCall(refresh + 102) & ~1;
            int signal = thumbCall(copy + 324) & ~1;
            found[ResolverPatterns.UI_COPY] = copy;
            found[ResolverPatterns.UI_SIGNAL] = signal;
            if (!patternAt(ResolverPatterns.UI_COPY, copy)
                    || !patternAt(ResolverPatterns.UI_SIGNAL, signal) || !ram(literal(refresh + 26))
                    || literal(refresh + 26) != literal(copy + 2)
                    || thumbCall(refresh + 14) != symbol(systemDescriptor, "os_resource_reserve")
                    || thumbCall(refresh + 112) != symbol(systemDescriptor, "os_resource_release")
                    || thumbCall(copy + 320) != symbol(systemDescriptor, "os_resource_release")
                    || thumbCall(copy + 206) != symbol(systemDescriptor, "gdi_copy_area"))
                return fail(ERROR_ABI, ABI_LCD_CODE);
            uiRefresh = refresh | 1;
        }
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
        for (int i = 0; i < FirmwareApi.REQUIRED_COUNT; i++)
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

    public boolean containsRom(int address, int bytes) {
        return rom(address, bytes);
    }

    public int readWord(int address) {
        return word(address);
    }

    public boolean matchesText(int address, String text) {
        return textAt(address, text);
    }

    private boolean descriptorAt(int address) {
        return exports.validate(address, romBytes, true) && error == 0;
    }

    private int symbol(int descriptor, String name) {
        return symbol(descriptor, name, RomSymbols.FUNCTION);
    }

    private int symbol(int descriptor, String name, int expectedType) {
        int result = exports.find(descriptor, name, expectedType, romBytes, true);
        if (exports.malformed)
            error = ERROR_SYMBOL_CHAIN;
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
