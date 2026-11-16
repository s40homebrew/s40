/* SPDX-License-Identifier: MIT */
/**
 * Runtime-selected ABI assumptions, allocated before any firmware access.
 * Model identity selects a candidate; only ROM validation establishes a match.
 * Construction copies the named Layout fields so later edits to a builder
 * cannot change an active resolver's memory map or invalidate its cache.
 */
final class FirmwareProfile {
    final String id, bootstrapId;
    final String[] platformModels;
    final FirmwareResolver.Pattern[] patterns;
    // Model-family mappings and bounded discovery windows.
    // Null selects the ROM-declared RM-443 range. Other profiles can use
    // conservative mapped prefixes: pairs of [start, exclusive limit]. These
    // are discovery windows, not claims about a flash image's exact length.
    final int[] scanWindows;
    final int displayWidth, displayHeight, timerMessageLast, ignoredTimer;
    final int inputLayout, canvasLayout;
    final int specialTimer, taskSpecialCallOffset;
    final int romStart;
    final int romLimit;
    final int ramStart;
    final int ramLimit;
    final int bootBytes;
    final int romPageMask;
    // Range-walker recipe and its zero-terminated pairs of exclusive bounds.
    final int rangeTableLdrOffset;
    final int rangeEntryBytes;
    final int rangeEndOffset;
    final int rangeMinTableBytes;
    final int rangeMaxEntries;
    final int rangeRegionMask;
    final int rangeRomRegion;
    // Nokia built-in library descriptor; the five intervening words are ignored.
    final int descriptorBytes;
    final int descriptorHashOffset;
    final int descriptorStringsOffset;
    final int descriptorSymbolsOffset;
    // KNI helper relationships; offsets are relative to checked recipe starts.
    final int kniReturnSearchRadius;
    final int kniParamReturnOffset;
    final int kniReturnSpLdrOffset;
    // Checked Java task-loop calls, not absolute firmware addresses.
    final int taskReceiveCallOffset;
    final int taskVmTimerCallOffset;
    final int taskTimerTimeoutCallOffset;
    final int taskPhonetCallOffset;
    final int taskInternalQueueCallOffset;
    final int taskMsgCountCallOffset;
    final int taskSubscriptionsCallOffset;
    final int invokeBranchOffset;
    final int dispatchNativeEntryOffset;
    // Checked LCD ownership and resource acquire/release relationships.
    final int lcdReserveOwnerLdrOffset;
    final int lcdUpdateOwnerLdrOffset;
    final int lcdOwnerLiteralBias;
    final int lcdAcquireIdOffset;
    final int lcdReleaseIdOffset;
    final int lcdAcquireCallOffset;
    final int lcdReleaseCallOffset;

    FirmwareProfile(String id, String bootstrapId, String[] platformModels,
            FirmwareResolver.Pattern[] patterns, Layout layout) {
        this.id = id;
        this.bootstrapId = bootstrapId;
        this.platformModels = platformModels;
        this.patterns = patterns;
        this.scanWindows = layout.scanWindows == null ? null : new int[layout.scanWindows.length];
        if (scanWindows != null)
            System.arraycopy(layout.scanWindows, 0, scanWindows, 0, scanWindows.length);
        this.displayWidth = layout.displayWidth;
        this.displayHeight = layout.displayHeight;
        this.inputLayout = layout.inputLayout;
        this.canvasLayout = layout.canvasLayout;
        this.timerMessageLast = layout.timerMessageLast;
        this.ignoredTimer = layout.ignoredTimer;
        this.specialTimer = layout.specialTimer;
        this.taskSpecialCallOffset = layout.taskSpecialCallOffset;
        this.romStart = layout.romStart;
        this.romLimit = layout.romLimit;
        this.ramStart = layout.ramStart;
        this.ramLimit = layout.ramLimit;
        this.bootBytes = layout.bootBytes;
        this.romPageMask = layout.romPageMask;
        this.rangeTableLdrOffset = layout.rangeTableLdrOffset;
        this.rangeEntryBytes = layout.rangeEntryBytes;
        this.rangeEndOffset = layout.rangeEndOffset;
        this.rangeMinTableBytes = layout.rangeMinTableBytes;
        this.rangeMaxEntries = layout.rangeMaxEntries;
        this.rangeRegionMask = layout.rangeRegionMask;
        this.rangeRomRegion = layout.rangeRomRegion;
        this.descriptorBytes = layout.descriptorBytes;
        this.descriptorHashOffset = layout.descriptorHashOffset;
        this.descriptorStringsOffset = layout.descriptorStringsOffset;
        this.descriptorSymbolsOffset = layout.descriptorSymbolsOffset;
        this.kniReturnSearchRadius = layout.kniReturnSearchRadius;
        this.kniParamReturnOffset = layout.kniParamReturnOffset;
        this.kniReturnSpLdrOffset = layout.kniReturnSpLdrOffset;
        this.taskReceiveCallOffset = layout.taskReceiveCallOffset;
        this.taskVmTimerCallOffset = layout.taskVmTimerCallOffset;
        this.taskTimerTimeoutCallOffset = layout.taskTimerTimeoutCallOffset;
        this.taskPhonetCallOffset = layout.taskPhonetCallOffset;
        this.taskInternalQueueCallOffset = layout.taskInternalQueueCallOffset;
        this.taskMsgCountCallOffset = layout.taskMsgCountCallOffset;
        this.taskSubscriptionsCallOffset = layout.taskSubscriptionsCallOffset;
        this.invokeBranchOffset = layout.invokeBranchOffset;
        this.dispatchNativeEntryOffset = layout.dispatchNativeEntryOffset;
        this.lcdReserveOwnerLdrOffset = layout.lcdReserveOwnerLdrOffset;
        this.lcdUpdateOwnerLdrOffset = layout.lcdUpdateOwnerLdrOffset;
        this.lcdOwnerLiteralBias = layout.lcdOwnerLiteralBias;
        this.lcdAcquireIdOffset = layout.lcdAcquireIdOffset;
        this.lcdReleaseIdOffset = layout.lcdReleaseIdOffset;
        this.lcdAcquireCallOffset = layout.lcdAcquireCallOffset;
        this.lcdReleaseCallOffset = layout.lcdReleaseCallOffset;
    }

    /** Named construction fields. A profile factory fills every group explicitly. */
    static final class Layout {
        int[] scanWindows;
        int displayWidth, displayHeight, timerMessageLast, ignoredTimer;
        int inputLayout, canvasLayout;
        int specialTimer = -1, taskSpecialCallOffset = -1;
        int romStart;
        int romLimit;
        int ramStart;
        int ramLimit;
        int bootBytes;
        int romPageMask;
        int rangeTableLdrOffset;
        int rangeEntryBytes;
        int rangeEndOffset;
        int rangeMinTableBytes;
        int rangeMaxEntries;
        int rangeRegionMask;
        int rangeRomRegion;
        int descriptorBytes;
        int descriptorHashOffset;
        int descriptorStringsOffset;
        int descriptorSymbolsOffset;
        int kniReturnSearchRadius;
        int kniParamReturnOffset;
        int kniReturnSpLdrOffset;
        int taskReceiveCallOffset;
        int taskVmTimerCallOffset;
        int taskTimerTimeoutCallOffset;
        int taskPhonetCallOffset;
        int taskInternalQueueCallOffset;
        int taskMsgCountCallOffset;
        int taskSubscriptionsCallOffset;
        int invokeBranchOffset;
        int dispatchNativeEntryOffset;
        int lcdReserveOwnerLdrOffset;
        int lcdUpdateOwnerLdrOffset;
        int lcdOwnerLiteralBias;
        int lcdAcquireIdOffset;
        int lcdReleaseIdOffset;
        int lcdAcquireCallOffset;
        int lcdReleaseCallOffset;
    }
}
