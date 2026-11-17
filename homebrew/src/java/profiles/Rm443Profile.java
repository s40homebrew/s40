/* SPDX-License-Identifier: MIT */
/**
 * RM-443 mapping and instruction-layout assumptions, not firmware addresses.
 * Both 08.90 and 10.12 use this profile; the resolver discovers their bindings.
 * A different bootstrap/VM ABI needs an audited profile and launcher path.
 */
final class Rm443Profile {
    private Rm443Profile() {}

    static FirmwareProfile create() {
        FirmwareProfile.Layout layout = new FirmwareProfile.Layout();
        layout.useKvmDiscovery();
        layout.useRomRangeTable();
        layout.useLibraryDescriptors();

        layout.displayWidth = 240;
        layout.displayHeight = 320;
        layout.canvasLayout = FirmwareApi.CANVAS_CURRENT;
        layout.inputLayout = FirmwareApi.INPUT_KEYPAD;
        layout.timerMessageLast = 488;
        layout.ignoredTimer = 127;

        // Virtual mapping and initial boot-window probe.
        layout.romStart = 0xF0100400;
        layout.romLimit = 0xF2000000;
        layout.ramStart = 0xD8000000;
        layout.ramLimit = 0xDA000000;

        // KNI helper cluster and return/stack relationships.
        layout.kniReturnSpLdrOffset = 4;

        // Calls relative to checked task-loop and dispatcher recipes.
        layout.taskReceiveCallOffset = 0;
        layout.taskVmTimerCallOffset = 32;
        layout.taskTimerTimeoutCallOffset = 42;
        layout.taskPhonetCallOffset = 54;
        layout.taskInternalQueueCallOffset = 58;
        layout.taskMsgCountCallOffset = 62;
        layout.taskSubscriptionsCallOffset = 82;

        // LCD ownership and paired resource acquire/release operations.
        layout.lcdReserveOwnerLdrOffset = 12;
        layout.lcdUpdateOwnerLdrOffset = 8;
        layout.lcdOwnerLiteralBias = 28;
        layout.lcdAcquireIdOffset = 32;
        layout.lcdReleaseIdOffset = 102;
        layout.lcdAcquireCallOffset = 34;
        layout.lcdReleaseCallOffset = 104;

        return new FirmwareProfile("RM-443", FirmwareProfiles.RM443_BOOTSTRAP,
                new String[] {"Nokia6303c", "Nokia6303classic"}, createPatterns(), layout);
    }

    /**
     * Recipes generated from firmware/signatures/profiles/Rm443Patterns.S,
     * shared by 08.90 and 10.12. Exact anchors filter candidates;
     * FirmwareResolver always checks each complete instruction recipe.
     *
     * RANGE checks the boot range walker. KNI_PARAM excludes its return instruction
     * because both BX LR and B-to-BX-LR are validated separately. LCD_RESERVE masks
     * only the two resource-ID immediates; matching IDs and named calls are required.
     * Package-visible because RM-614 reuses the audited common recipes.
     */
    static FirmwareResolver.Pattern[] createPatterns() {
        return new FirmwareResolver.Pattern[] {
                Rm443Patterns.RANGE(),
                Rm443Patterns.CACHE(),
                Rm443Patterns.KNI_PARAM(),
                Rm443Patterns.TASK_LOOP(),
                Rm443Patterns.APP_ID(),
                Rm443Patterns.EVENT_POP(),
                Rm443Patterns.DISPATCH(),
                Rm443Patterns.INVOKE(),
                Rm443Patterns.KNI_RETURN(),
                Rm443Patterns.MSG_COUNT(),
                Rm443Patterns.VM_TIMER(),
                Rm443Patterns.LCD_RESERVE(),
                Rm443Patterns.LCD_UPDATE(),
        };
    }
}
