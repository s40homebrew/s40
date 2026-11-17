/* SPDX-License-Identifier: MIT */
/** Firmware relationships for resident protocol hooks. */
final class BridgeBindings {
    private BridgeBindings() {}

    // PANEL matches the constructor's superclass and registration sequence.
    // These positions are inside that checked sequence, not ROM displacements.
    private static final int CLASS_LITERAL = 8, GLOBAL_LITERAL = 24, REGISTER_CALL = 40;
    private static final int CONSTRUCTOR_PREFIX_BYTES = 168, GLOBAL_POINTER_BIAS = -12;
    private static final int PANEL_OBJECT_BYTES = 284, PANEL_CLASS_LAYOUT = 0x011c000d;
    private static final RomRecipe PANEL = BridgePatterns.PANEL();
    private static final RomRecipe RECEIVE = BridgePatterns.RECEIVE();
    private static final RomSearch SEARCH = new RomSearch(new RomRecipe[] {PANEL});

    static void prepare() {
        FirmwareClasses.prepare();
        ComMgrBindings.prepare();
    }

    static boolean bind(FirmwareResolver r) {
        if (!"RM-443".equals(r.familyId()) || !SEARCH.resolve(r))
            return false;
        int registration = SEARCH.address(0);
        int klass = r.literal(registration + CLASS_LITERAL);
        if (!FirmwareClasses.classAt(r, klass, PANEL_OBJECT_BYTES, "MessageParser")
                || r.word(klass + 12) != PANEL_CLASS_LAYOUT
                || FirmwareClasses.method(r, klass, 0xc004) != ((registration - CONSTRUCTOR_PREFIX_BYTES) | 1)
                || r.thumbCall(registration + REGISTER_CALL) != r.export("CM_ComMgr_Register"))
            return false;
        int receive = FirmwareClasses.method(r, klass, 0xc009);
        if (!r.matches(receive & ~1, RECEIVE))
            return false;
        int pointer = r.literal(registration + GLOBAL_LITERAL) + GLOBAL_POINTER_BIAS;
        int contexts = ComMgrBindings.taskContexts(r), objectTask = ComMgrBindings.objectTask(r);
        if (!r.ram(pointer, 4) || contexts == 0 || objectTask == 0)
            return false;
        int messageNew = r.export("CM_ISIMes_New_Zero"),
            messageDelete = r.export("CM_ISIMes_Delete");
        int call = r.export("_CallClass3");
        if (messageNew == 0 || messageDelete == 0 || call == 0 || r.error != 0)
            return false;
        int[] api = r.api;
        api[FirmwareApi.BRIDGE_PANEL_POINTER] = pointer;
        api[FirmwareApi.BRIDGE_ORIGINAL_CLASS] = klass;
        api[FirmwareApi.BRIDGE_ORIGINAL_RX] = receive;
        api[FirmwareApi.BRIDGE_CLASS_SIZES] = r.word(klass + 12);
        api[FirmwareApi.BRIDGE_TASK_CONTEXTS] = contexts;
        api[FirmwareApi.BRIDGE_DEVICE] = ComMgrBindings.deviceAddress(r, objectTask);
        api[FirmwareApi.BRIDGE_OBJECT_TASK] = objectTask;
        api[FirmwareApi.BRIDGE_MESSAGE_NEW] = messageNew;
        api[FirmwareApi.BRIDGE_MESSAGE_DELETE] = messageDelete;
        api[FirmwareApi.BRIDGE_CALL_CLASS3] = call;
        api[FirmwareApi.BRIDGE_RAM_START] = r.ramStart;
        api[FirmwareApi.BRIDGE_RAM_END] = r.ramLimit;
        api[FirmwareApi.BRIDGE_ROM_START] = r.romStart;
        api[FirmwareApi.BRIDGE_ROM_END] = r.romStart + r.romBytes;
        api[FirmwareApi.BRIDGE_ABI] = FirmwareApi.BRIDGE_ABI_RM443;
        return r.error == 0;
    }
}
