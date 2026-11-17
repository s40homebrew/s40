/* SPDX-License-Identifier: MIT */
/**
 * Optional RGB565 transport discovered from the stock LCD and Java task paths.
 * Recipes describe instruction layouts, never firmware addresses or versions.
 * Every search is bounded to a named export or already-resolved caller cluster.
 */
final class VideoBindings {
    private VideoBindings() {}
    static void prepare() {
        ComMgrBindings.prepare();
    }

    // Named public update -> panel wrapper -> RGB submit. Hooks and completion layouts must agree.
    private static final RomRecipe UPDATE = VideoPatterns.UPDATE();

    private static final RomRecipe WRAPPER = VideoPatterns.WRAPPER();

    private static final RomRecipe SET_COMPLETION = VideoPatterns.SET_COMPLETION();

    private static final RomRecipe GATE = VideoPatterns.GATE();

    // Bus clock 25, data pins 16..23/mode 2, and peripheral control writes.
    private static final RomRecipe SUBMIT_BUS = VideoPatterns.SUBMIT_BUS();

    // Panel revision/mode/power offsets and stock window/arm arguments.
    private static final RomRecipe SUBMIT_STATE = VideoPatterns.SUBMIT_STATE();

    // DMA request 18, state callbacks, watchdog 80000us and channel/armed fields.
    private static final RomRecipe SUBMIT_DMA = VideoPatterns.SUBMIT_DMA();

    private static final RomRecipe LCD_COMMAND = VideoPatterns.LCD_COMMAND();

    private static final RomRecipe LCD_WRITE = VideoPatterns.LCD_WRITE();

    private static final RomRecipe LCD_READ_BYTE = VideoPatterns.LCD_READ_BYTE();

    private static final RomRecipe LCD_ABORT =
            VideoPatterns.LCD_ABORT();

    private static final RomRecipe LCD_ARM = VideoPatterns.LCD_ARM();

    private static final RomRecipe LCD_READ_WORD = VideoPatterns.LCD_READ_WORD();

    private static final RomRecipe LCD_WINDOW = VideoPatterns.LCD_WINDOW();

    // Stock DMA completion owns IRQ, bus clocks, pins and the user completion callback.
    private static final RomRecipe DMA_COMPLETE = VideoPatterns.DMA_COMPLETE();

    private static final RomRecipe TIMEOUT = VideoPatterns.TIMEOUT();

    private static final RomRecipe GPIO_MODE = VideoPatterns.GPIO_MODE();

    private static final RomRecipe IRQ_LOCK = VideoPatterns.IRQ_LOCK();

    private static final RomRecipe IRQ_UNLOCK = VideoPatterns.IRQ_UNLOCK();

    private static final RomRecipe CLOCK_ENABLE = VideoPatterns.CLOCK_ENABLE();

    private static final RomRecipe CLOCK_RESET = VideoPatterns.CLOCK_RESET();

    // Only the watchdog pool capacity immediate varies; it is not consumed by the SDK.
    private static final RomRecipe WATCHDOG = VideoPatterns.WATCHDOG();

    private static final RomRecipe DMA_START = VideoPatterns.DMA_START();

    // Timer/message immediates vary. The task still receives/deallocates OS messages and restarts
    // 1000 ticks.
    private static final RomRecipe LCD_TASK = VideoPatterns.LCD_TASK();

    private static final RomRecipe TIMER_START = VideoPatterns.TIMER_START();

    private static final RomRecipe TIMER_STOP = VideoPatterns.TIMER_STOP();

    // Timer records have stride 28; the state byte is at record+18 after a 20-byte table prefix.
    private static final RomRecipe TIMER_TABLE = VideoPatterns.TIMER_TABLE();

    private static final RomRecipe TIMER_STOP_STATE = VideoPatterns.TIMER_STOP_STATE();

    // Scratchpad index 1 selects a resource byte; paired exports must reference the same array.
    private static final RomRecipe SCRATCHPAD = VideoPatterns.SCRATCHPAD();

    // Internal queues use a ROM descriptor pointing to a RAM circular-list tail; zero means empty.
    private static final RomRecipe CM_DISPATCH = VideoPatterns.CM_DISPATCH();

    private static final RomRecipe CM_POP = VideoPatterns.CM_POP();

    private static final RomRecipe CM_QUEUE = VideoPatterns.CM_QUEUE();

    // The ComMgr dirty flag is bit0 of queue byte60.
    private static final RomRecipe CM_SUBSCRIPTIONS = VideoPatterns.CM_SUBSCRIPTIONS();

    private static final RomRecipe TASK_ENTRY = VideoPatterns.TASK_ENTRY();

    // The Java foreground object stores state 3 at byte 20.
    private static final RomRecipe FOREGROUND = VideoPatterns.FOREGROUND();

    // Keyboard messages E104..E106 are decoded into the existing Java event queue.
    private static final RomRecipe KEYBOARD = VideoPatterns.KEYBOARD();

    private static final RomRecipe JAVA_SERVER = VideoPatterns.JAVA_SERVER();

    private static final RomRecipe RESOURCE_TABLE = VideoPatterns.RESOURCE_TABLE();

    // OS resources use an eight-byte record and UINT32_MAX empty waiter-head sentinel.
    private static final RomRecipe RESOURCE_HEAD = VideoPatterns.RESOURCE_HEAD();

    // Alternate stock reader derives bus timing from configuration; identical output and call ABI.
    private static final RomRecipe LCD_READ_BYTE_TIMED = VideoPatterns.LCD_READ_BYTE_TIMED();

    private static final RomRecipe LCD_READ_WORD_TIMED = VideoPatterns.LCD_READ_WORD_TIMED();

    private static boolean call(FirmwareResolver r, int slot, int instruction, RomRecipe recipe) {
        int target = r.thumbCall(instruction);
        if (!r.matches(target & ~1, recipe))
            return false;
        r.api[slot] = target;
        return true;
    }

    private static boolean callback(
            FirmwareResolver r, int slot, int instruction, RomRecipe recipe) {
        int target = r.literal(instruction);
        if ((target & 1) == 0 || !r.matches(target & ~1, recipe))
            return false;
        r.api[slot] = target;
        return true;
    }

    private static int readRoutine(
            FirmwareResolver r, RomRecipe fixed, RomRecipe timed, int start, int bytes) {
        int first = r.findUnique(fixed, start, bytes);
        int second = r.findUnique(timed, start, bytes);
        // Two valid alternatives in one driver are ambiguous, not a preference.
        return (first == 0) != (second == 0) ? (first | second) | 1 : 0;
    }

    static boolean bind(FirmwareResolver r) {
        if (!"RM-443".equals(r.familyId()))
            return false;
        int[] api = r.api;
        int update = r.export("hw_lcd_color_update") & ~1;
        if (!r.matches(update, UPDATE) || !call(r, FirmwareApi.VIDEO_LCD_GATE, update + 8, GATE))
            return false;
        api[FirmwareApi.VIDEO_DISPLAY_HOOKS] = r.literal(update + 12);
        int table = r.literal(update + 24);
        int wrapper = r.word(table) & ~1;
        if (!r.matches(wrapper, WRAPPER)
                || !call(r, FirmwareApi.VIDEO_LCD_SET_COMPLETION, wrapper + 16, SET_COMPLETION))
            return false;
        api[FirmwareApi.VIDEO_LCD_PENDING] = r.literal(wrapper + 2);
        int completion = api[FirmwareApi.VIDEO_LCD_SET_COMPLETION] & ~1;
        int publicComplete = r.literal(wrapper + 34);
        int publicCode = publicComplete & ~1;
        // The public callback is MOV r1,0; MOV r0,2; B set_completion.
        int branch = r.half(publicCode + 4);
        int displacement = (branch & 2047) << 1;
        if ((displacement & 2048) != 0)
            displacement -= 4096;
        if ((publicComplete & 1) == 0 || r.half(publicCode) != 0x2100
                || r.half(publicCode + 2) != 0x2002 || (branch & 0xf800) != 0xe000
                || publicCode + 8 + displacement != completion
                || r.literal(completion) != api[FirmwareApi.VIDEO_LCD_PENDING]
                || r.thumbCall(completion + 22) != api[FirmwareApi.VIDEO_LCD_GATE])
            return false;
        api[FirmwareApi.VIDEO_LCD_PUBLIC_COMPLETE] = publicComplete;
        int driverTable = r.literal(wrapper + 24);
        int submit = r.word(driverTable + 48) & ~1;
        if (!r.matches(submit, SUBMIT_BUS) || !r.matches(submit + 0xb6, SUBMIT_STATE)
                || !r.matches(submit + 0x150, SUBMIT_DMA))
            return false;
        api[FirmwareApi.VIDEO_LCD_CONTROL] = r.literal(submit + 10) + 8;
        api[FirmwareApi.VIDEO_GPIO] = r.literal(submit + 46);
        api[FirmwareApi.VIDEO_LCD_STATE] = r.literal(submit + 0x76);
        // These peripheral registers and pin/DMA assignments are part of this
        // transport ABI. Firmware globals themselves must remain in profile RAM.
        if (api[FirmwareApi.VIDEO_LCD_CONTROL] != 0xffd22208
                || api[FirmwareApi.VIDEO_GPIO] != 0xffd31c00
                || !call(r, FirmwareApi.VIDEO_IRQ_LOCK, submit + 6, IRQ_LOCK)
                || !call(r, FirmwareApi.VIDEO_IRQ_UNLOCK, submit + 20, IRQ_UNLOCK)
                || !call(r, FirmwareApi.VIDEO_CLOCK_ENABLE, submit + 30, CLOCK_ENABLE)
                || !call(r, FirmwareApi.VIDEO_CLOCK_RESET, submit + 40, CLOCK_RESET)
                || !call(r, FirmwareApi.VIDEO_GPIO_PIN_MODE, submit + 58, GPIO_MODE)
                || !call(r, FirmwareApi.VIDEO_LCD_COMMAND, submit + 0xa2, LCD_COMMAND)
                || !call(r, FirmwareApi.VIDEO_LCD_WRITE_BYTES, submit + 0xaa, LCD_WRITE)
                || !call(r, FirmwareApi.VIDEO_LCD_WINDOW, submit + 0x10e, LCD_WINDOW)
                || !call(r, FirmwareApi.VIDEO_LCD_ARM, submit + 0x118, LCD_ARM)
                || !call(r, FirmwareApi.VIDEO_DMA_START, submit + 0x158, DMA_START)
                || !call(r, FirmwareApi.VIDEO_WATCHDOG_START, submit + 0x1aa, WATCHDOG)
                || !callback(r, FirmwareApi.VIDEO_LCD_DMA_COMPLETE, submit + 0x150, DMA_COMPLETE)
                || !callback(r, FirmwareApi.VIDEO_LCD_TIMEOUT, submit + 0x1a6, TIMEOUT))
            return false;
        int gate = api[FirmwareApi.VIDEO_LCD_GATE] & ~1;
        int dmaComplete = api[FirmwareApi.VIDEO_LCD_DMA_COMPLETE] & ~1;
        if (r.literal(gate + 8) != api[FirmwareApi.VIDEO_DISPLAY_HOOKS]
                || r.thumbCall(gate + 4) != api[FirmwareApi.VIDEO_IRQ_LOCK]
                || r.thumbCall(gate + 16) != api[FirmwareApi.VIDEO_IRQ_UNLOCK]
                || r.literal(dmaComplete + 6) != api[FirmwareApi.VIDEO_LCD_STATE]
                || r.literal(submit + 0x1ae) != api[FirmwareApi.VIDEO_LCD_STATE])
            return false;
        int command = api[FirmwareApi.VIDEO_LCD_COMMAND] & ~1;
        int arm = api[FirmwareApi.VIDEO_LCD_ARM] & ~1;
        api[FirmwareApi.VIDEO_LCD_READ_BYTE] =
                readRoutine(r, LCD_READ_BYTE, LCD_READ_BYTE_TIMED, command, 0x100);
        api[FirmwareApi.VIDEO_LCD_READ_WORD] =
                readRoutine(r, LCD_READ_WORD, LCD_READ_WORD_TIMED, arm, 0x200);
        int abort = r.findUnique(LCD_ABORT, command, 0x100);
        if (abort == 0 || r.literal(abort) + 8 != api[FirmwareApi.VIDEO_LCD_CONTROL])
            return false;
        api[FirmwareApi.VIDEO_LCD_ABORT] = abort | 1;

        // The periodic LCD task reasserts the normal pixel format. Derive its
        // timer ID and state byte rather than assuming IDs survive a new build.
        int task = r.findUnique(LCD_TASK, update, 0x6000);
        if (task == 0 || r.thumbCall(task + 12) != api[FirmwareApi.FW_OS_MSG_RECEIVE]
                || r.thumbCall(task + 0x4c) != api[FirmwareApi.FW_OS_BLOCK_DEALLOC]
                || !call(r, FirmwareApi.VIDEO_TIMER_START, task + 0x46, TIMER_START))
            return false;
        int timer = 255 + (r.half(task + 0x44) & 255);
        int stop = r.findUnique(TIMER_STOP, api[FirmwareApi.VIDEO_TIMER_START] & ~1, 0x600);
        if (stop == 0 || !r.matches(stop + 0x7c, TIMER_TABLE)
                || !r.matches(stop + 0x108, TIMER_STOP_STATE)
                || timer >= (r.half(stop + 0x22) & 255) + (r.half(stop + 0x26) & 255))
            return false;
        api[FirmwareApi.VIDEO_TIMER_STOP] = stop | 1;
        api[FirmwareApi.VIDEO_PERIODIC_TIMER_ID] = timer;
        api[FirmwareApi.VIDEO_PERIODIC_TIMER_STATE] = r.literal(stop + 0x7e) + 20 + 28 * timer + 18;

        int take = r.export("gdi_display_scratchpad_lock_enable") & ~1;
        int give = r.export("gdi_display_scratchpad_lock_disable") & ~1;
        int release = r.export("os_resource_release") & ~1;
        if (!r.matches(take, SCRATCHPAD) || !r.matches(give, SCRATCHPAD)
                || r.thumbCall(take + 22) != r.export("os_resource_reserve")
                || r.thumbCall(give + 22) != (release | 1)
                || r.literal(take + 12) != r.literal(give + 12)
                || !r.matches(release + 0x68, RESOURCE_TABLE)
                || !r.matches(release + 0x108, RESOURCE_HEAD)
                || (r.half(release + 0x28) & 0xff00) != 0x2d00)
            return false;
        api[FirmwareApi.VIDEO_SCRATCHPAD_TAKE] = take | 1;
        api[FirmwareApi.VIDEO_SCRATCHPAD_GIVE] = give | 1;
        api[FirmwareApi.VIDEO_SCRATCHPAD_RESOURCES] = r.literal(take + 12) - 33;
        api[FirmwareApi.VIDEO_OS_RESOURCES] = r.literal(release + 0x68);
        api[FirmwareApi.VIDEO_RESOURCE_COUNT] = r.half(release + 0x28) & 255;

        int contexts = ComMgrBindings.taskContexts(r);
        int process = api[FirmwareApi.FW_CM_COMMGR_PROCESSINTERNALQUEUE] & ~1;
        int scan = api[FirmwareApi.FW_CM_COMMGR_SCANISISUBSCRIPTIONS] & ~1;
        if (!r.matches(process, CM_QUEUE) || !r.matches(scan + 0x34, CM_SUBSCRIPTIONS))
            return false;
        int dispatch = r.thumbCall(process + 0x34) & ~1;
        if (!r.matches(dispatch, CM_DISPATCH)
                || !r.matches(r.thumbCall(dispatch + 10) & ~1, CM_POP))
            return false;
        int entry = r.found[ResolverPatterns.TASK_LOOP] - 40;
        if (contexts == 0 || !r.matches(entry, TASK_ENTRY)
                || r.thumbCall(entry + 20) != r.export("CM_ComMgr_Init"))
            return false;
        int taskId = r.half(entry + 2) & 255;
        int descriptor = r.literal(entry + 18);
        if (!r.rom(descriptor, 24))
            return false;
        api[FirmwareApi.VIDEO_JAVA_TASK_ID] = taskId;
        api[FirmwareApi.VIDEO_JAVA_TASK_SLOT] = contexts + 4 * taskId;
        api[FirmwareApi.VIDEO_JAVA_TASK_DESCRIPTOR] = descriptor;
        api[FirmwareApi.VIDEO_JAVA_QUEUE] = r.word(descriptor);
        int appId = api[FirmwareApi.FW_APP_ID] & ~1;
        int foreground = r.findUnique(FOREGROUND, appId - 0x40000, 0x40000);
        int keyboard = r.findUnique(KEYBOARD, appId - 0x10000, 0x20000);
        if (foreground == 0 || keyboard == 0 || r.literal(keyboard + 2) != 0xffff1efc)
            return false;
        int server = r.findUnique(JAVA_SERVER, keyboard - 0x4000, 0x4000);
        if (server == 0 || r.thumbCall(server + 6) != r.export("CM_ISIMes_New_Zero"))
            return false;
        api[FirmwareApi.VIDEO_FOREGROUND_SLOT] = r.literal(foreground) + 4;
        api[FirmwareApi.VIDEO_KEYBOARD_DECODE] = keyboard | 1;
        api[FirmwareApi.VIDEO_JAVA_SERVER] = r.literal(server + 2);
        api[FirmwareApi.VIDEO_ISI_DELETE] = r.export("CM_ISIMes_Delete");
        api[FirmwareApi.VIDEO_CM_SEND_SERVER] = r.export("CM_ComMgr_SendToServer");
        if (!r.ram(api[FirmwareApi.VIDEO_LCD_STATE], 0x49)
                || !r.ram(api[FirmwareApi.VIDEO_LCD_PENDING], 8)
                || !r.ram(api[FirmwareApi.VIDEO_DISPLAY_HOOKS], 16)
                || !r.ram(api[FirmwareApi.VIDEO_FOREGROUND_SLOT], 4)
                || !r.ram(api[FirmwareApi.VIDEO_JAVA_SERVER], 12)
                || !r.ram(api[FirmwareApi.VIDEO_JAVA_TASK_SLOT], 4)
                || !r.ram(api[FirmwareApi.VIDEO_JAVA_QUEUE], 61)
                || !r.ramBytes(api[FirmwareApi.VIDEO_SCRATCHPAD_RESOURCES], 2)
                || !r.ram(api[FirmwareApi.VIDEO_OS_RESOURCES],
                        8 * api[FirmwareApi.VIDEO_RESOURCE_COUNT])
                || !r.ramBytes(api[FirmwareApi.VIDEO_PERIODIC_TIMER_STATE], 1))
            return false;
        api[FirmwareApi.VIDEO_RAM_START] = r.ramStart;
        api[FirmwareApi.VIDEO_RAM_END] = r.ramLimit;
        for (int i = FirmwareApi.VIDEO_FIRST + 1; i < FirmwareApi.VIDEO_LIMIT; i++)
            if (api[i] == 0)
                return false;
        api[FirmwareApi.VIDEO_ABI] = FirmwareApi.VIDEO_ABI_RM443;
        return r.error == 0;
    }
}
