/* SPDX-License-Identifier: MIT */
/** Firmware relationships for ComMgr task routing. */
final class ComMgrBindings {
    private ComMgrBindings() {}

    // Positions within the matched SendToObject routing block and task lookup.
    private static final int SEND_ROUTING_OFFSET = 218, SEND_TASK_CALL = 234;
    private static final int TASK_DEVICE_LITERAL = 2, TASK_OWN_ID_CALL = 34;
    private static final RomRecipe SEND_OBJECT = ComMgrPatterns.SEND_OBJECT();
    private static final RomRecipe OBJECT_TASK = ComMgrPatterns.OBJECT_TASK();
    static void prepare() {}

    static int taskContexts(FirmwareResolver r) {
        int queue = r.api[FirmwareApi.FW_CM_COMMGR_PROCESSINTERNALQUEUE] & ~1;
        int scan = r.api[FirmwareApi.FW_CM_COMMGR_SCANISISUBSCRIPTIONS] & ~1;
        if (!r.rom(queue, 14) || !r.rom(scan, 14)
                || r.thumbCall(queue + 4) != r.api[FirmwareApi.FW_OS_TASK_OWN_ID_READ]
                || r.thumbCall(scan + 4) != r.api[FirmwareApi.FW_OS_TASK_OWN_ID_READ]
                || r.half(queue + 10) != 0x0080 || r.half(queue + 12) != 0x580d)
            return 0;
        int contexts = r.literal(queue + 8);
        return contexts == r.literal(scan + 8) && r.ram(contexts, 64 * 4) ? contexts : 0;
    }

    static int objectTask(FirmwareResolver r) {
        int send = r.export("CM_ComMgr_SendToObject") & ~1;
        if (!r.matches(send + SEND_ROUTING_OFFSET, SEND_OBJECT))
            return 0;
        int entry = r.thumbCall(send + SEND_TASK_CALL), function = entry & ~1;
        if (!r.matches(function, OBJECT_TASK)
                || r.thumbCall(function + TASK_OWN_ID_CALL)
                        != r.api[FirmwareApi.FW_OS_TASK_OWN_ID_READ]
                || !r.ramBytes(r.literal(function + TASK_DEVICE_LITERAL), 1))
            return 0;
        return entry;
    }

    static int deviceAddress(FirmwareResolver r, int objectTask) {
        return r.literal((objectTask & ~1) + TASK_DEVICE_LITERAL);
    }
}
