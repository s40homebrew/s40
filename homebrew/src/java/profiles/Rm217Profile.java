/* SPDX-License-Identifier: MIT */
/** Descriptor-free S40 mapping: bindings are discovered from checked code and call edges. */
final class Rm217Profile {
    private Rm217Profile() {}
    static final int FILE_ADAPTER_OPEN = 24;
    static final int FILE_ADAPTER_UNLINK = 25;
    static final int FILE_ADAPTER_MKDIR = 26;
    static final int OS_CURRENT_TIME_READ_IN_MS = 27;
    static final int OS_BLOCK_ALLOC_NO_WAIT = 28;
    static final int OS_BLOCK_DEALLOC = 29;
    static final int KVM_CURRENT_TIME_MILLIS = 30;
    static final int KVM_TIME_ACCUMULATOR = 31;
    static final int KVM_TIME_DELTA = 32;
    static final int FILE_READ_NATIVE = 33;
    static final int TASK_OWN_ID = 34;
    static final int FOREGROUND_RENDER = 35;
    static final int FOREGROUND_PRESENT = 36;
    static final int FORMAT_TO_HAL = 37;
    static final int RECEIVE = 38;
    static final int INTERNAL = 39;
    static final int PHONET = 40;
    static final int TIMEOUT = 41;
    static final int SUBSCRIPTIONS = 42;
    static FirmwareProfile create() {
        FirmwareProfile.Layout l = new FirmwareProfile.Layout();
        l.useKvmDiscovery();
        l.useRomRangeTable();
        l.displayWidth = 240;
        l.displayHeight = 320;
        l.inputLayout = FirmwareApi.INPUT_KEYPAD;
        l.canvasLayout = FirmwareApi.CANVAS_LEGACY;
        l.timerMessageLast = 478;
        l.ignoredTimer = 127;
        l.romStart = 0xF0100400;
        l.romLimit = 0xF2000000;
        l.ramStart = 0xD8000000;
        l.ramLimit = 0xD9000000;
        // descriptorBytes == 0 selects checked code discovery, not ELF exports.
        l.kniReturnSpLdrOffset = 2;
        l.taskReceiveCallOffset = 8;
        l.taskVmTimerCallOffset = 28;
        l.taskTimerTimeoutCallOffset = 40;
        l.taskPhonetCallOffset = 52;
        l.taskInternalQueueCallOffset = 56;
        l.taskMsgCountCallOffset = 60;
        l.taskSubscriptionsCallOffset = 80;
        return new FirmwareProfile("RM-217", FirmwareProfiles.RM443_BOOTSTRAP,
                new String[] {"Nokia6300"}, createPatterns(), l);
    }
    private static FirmwareResolver.Pattern[] createPatterns() {
        FirmwareResolver.Pattern[] p = new FirmwareResolver.Pattern[SUBSCRIPTIONS + 1];
        FirmwareResolver.Pattern[] common = Rm443Profile.createPatterns();
        p[ResolverPatterns.CACHE] = common[ResolverPatterns.CACHE];
        p[ResolverPatterns.KNI_PARAM] = common[ResolverPatterns.KNI_PARAM];
        p[ResolverPatterns.APP_ID] = common[ResolverPatterns.APP_ID];
        p[ResolverPatterns.DISPATCH] = common[ResolverPatterns.DISPATCH];
        p[ResolverPatterns.INVOKE] = common[ResolverPatterns.INVOKE];
        p[ResolverPatterns.RANGE] = new FirmwareResolver.Pattern(Rm217Memory.RANGE);
        p[ResolverPatterns.EVENT_POP] = Rm217Patterns.EVENT_POP();
        p[ResolverPatterns.KNI_RETURN] = Rm217Patterns.KNI_RETURN();
        p[ResolverPatterns.TASK_LOOP] = Rm217Patterns.TASK_LOOP();
        p[ResolverPatterns.MSG_COUNT] = Rm217Patterns.MSG_COUNT();
        p[ResolverPatterns.VM_TIMER] = Rm217Patterns.VM_TIMER();
        p[ResolverPatterns.UI_REFRESH] = Rm217Patterns.UI_REFRESH();
        p[FILE_ADAPTER_OPEN] = Rm217Patterns.FILE_ADAPTER_OPEN();
        p[FILE_ADAPTER_UNLINK] =
                Rm217Patterns.FILE_ADAPTER_UNLINK();
        p[FILE_ADAPTER_MKDIR] = Rm217Patterns.FILE_ADAPTER_MKDIR();
        p[OS_CURRENT_TIME_READ_IN_MS] = Rm217Patterns.OS_CURRENT_TIME_READ_IN_MS();
        p[OS_BLOCK_ALLOC_NO_WAIT] = new FirmwareResolver.Pattern(Rm217Memory.ALLOC);
        p[OS_BLOCK_DEALLOC] = new FirmwareResolver.Pattern(Rm217Memory.FREE);
        p[KVM_CURRENT_TIME_MILLIS] =
                Rm217Patterns.KVM_CURRENT_TIME_MILLIS();
        p[KVM_TIME_ACCUMULATOR] = Rm217Patterns.KVM_TIME_ACCUMULATOR();
        p[KVM_TIME_DELTA] = Rm217Patterns.KVM_TIME_DELTA();
        p[FILE_READ_NATIVE] = new FirmwareResolver.Pattern(Rm217Memory.FILE_READ);
        p[TASK_OWN_ID] = Rm217Patterns.TASK_OWN_ID();
        p[FOREGROUND_RENDER] = Rm217Patterns.FOREGROUND_RENDER();
        p[FOREGROUND_PRESENT] = Rm217Patterns.FOREGROUND_PRESENT();
        p[FORMAT_TO_HAL] = Rm217Patterns.FORMAT_TO_HAL();
        p[RECEIVE] = Rm217Patterns.RECEIVE();
        p[INTERNAL] = Rm217Patterns.INTERNAL();
        p[PHONET] = Rm217Patterns.PHONET();
        p[TIMEOUT] = Rm217Patterns.TIMEOUT();
        p[SUBSCRIPTIONS] = Rm217Patterns.SUBSCRIPTIONS();
        return p;
    }
}
