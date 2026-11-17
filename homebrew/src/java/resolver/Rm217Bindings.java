/* SPDX-License-Identifier: MIT */
/** Relationships for descriptor-free firmware. Every address comes from the live ROM scan. */
final class Rm217Bindings {
    private Rm217Bindings() {}

    // Force KVM class loading before the forged memory-view interval.
    static void prepare() {}

    static boolean optional(int id) {
        return id == FirmwareApi.FW_GDI_DISPLAY_MASTER_RESERVE_ENABLE
                || id == FirmwareApi.FW_GDI_DISPLAY_MASTER_RESERVE_DISABLE
                || id == FirmwareApi.FW_GDI_DISPLAY_MASTER_UPDATE || id == FirmwareApi.FW_LCD_OWNER;
    }

    static boolean bind(FirmwareResolver r, boolean cached) {
        int[] f = r.found, api = r.api;
        FirmwareProfile p = r.profile;
        // A warm scan rechecks all globally found recipes and every relationship below.
        for (int i = 0; i < api.length; i++)
            api[i] = 0;
        int param = f[ResolverPatterns.KNI_PARAM], thread = r.literal(param);
        if (!r.ram(thread))
            return r.fail(FirmwareResolver.ERROR_ABI, FirmwareResolver.ABI_KNI_GLOBALS);
        int ret = f[ResolverPatterns.KNI_RETURN], count = 0;
        if (cached) {
            if (r.patternAt(ResolverPatterns.KNI_RETURN, ret) && r.literal(ret) == thread)
                count = 1;
        } else {
            int first = param - p.kniReturnSearchRadius;
            int last = param + p.kniReturnSearchRadius;
            if (first < r.romStart)
                first = r.romStart;
            if (last > r.romStart + r.romBytes - 12)
                last = r.romStart + r.romBytes - 12;
            for (int a = first; a <= last; a += 2) {
                if (r.patternAt(ResolverPatterns.KNI_RETURN, a) && r.literal(a) == thread) {
                    ret = a;
                    ++count;
                }
            }
        }
        f[ResolverPatterns.KNI_RETURN] = ret;
        r.matches[ResolverPatterns.KNI_RETURN] = count;
        if (count != 1)
            return r.fail(FirmwareResolver.ERROR_PATTERN_COUNT, ResolverPatterns.KNI_RETURN);
        int sp = r.literal(ret + p.kniReturnSpLdrOffset);
        if (!r.ram(sp) || sp == thread || r.half(param + p.kniParamReturnOffset) != 0x4770)
            return r.fail(FirmwareResolver.ERROR_ABI, FirmwareResolver.ABI_KNI_RETURN);
        if (r.armBranch(f[ResolverPatterns.INVOKE] + p.invokeBranchOffset)
                != f[ResolverPatterns.DISPATCH] + p.dispatchNativeEntryOffset)
            return r.fail(FirmwareResolver.ERROR_ABI, FirmwareResolver.ABI_NATIVE_DISPATCH);
        api[FirmwareApi.CACHE_SYNC] = f[ResolverPatterns.CACHE];
        api[FirmwareApi.KNI_PARAM] = param | 1;
        api[FirmwareApi.KNI_RETURN] = ret | 1;
        api[FirmwareApi.FW_APP_ID] = f[ResolverPatterns.APP_ID] | 1;
        api[FirmwareApi.FW_EVENT_POP] = f[ResolverPatterns.EVENT_POP] | 1;
        if (!r.ram(r.literal(f[ResolverPatterns.APP_ID])))
            return r.fail(FirmwareResolver.ERROR_ABI, FirmwareResolver.ABI_APP_GLOBAL);

        api[FirmwareApi.FW_OS_BLOCK_ALLOC_NO_WAIT] = f[Rm217Profile.OS_BLOCK_ALLOC_NO_WAIT] | 1;
        api[FirmwareApi.FW_OS_BLOCK_DEALLOC] = f[Rm217Profile.OS_BLOCK_DEALLOC] | 1;
        api[FirmwareApi.FW_OS_CURRENT_TIME_READ_IN_MS] =
                f[Rm217Profile.OS_CURRENT_TIME_READ_IN_MS] | 1;
        int open = f[Rm217Profile.FILE_ADAPTER_OPEN], unlink = f[Rm217Profile.FILE_ADAPTER_UNLINK];
        int mkdir = f[Rm217Profile.FILE_ADAPTER_MKDIR],
            readNative = f[Rm217Profile.FILE_READ_NATIVE];
        api[FirmwareApi.FW_FILESYS_OPEN] = open | 1;
        api[FirmwareApi.FW_FILESYS_CLOSE] = (open + 78) | 1;
        api[FirmwareApi.FW_FILESYS_READ] = (open + 86) | 1;
        api[FirmwareApi.FW_FILESYS_WRITE] = (open + 94) | 1;
        api[FirmwareApi.FW_FILESYS_LSEEK] = (open + 102) | 1;
        api[FirmwareApi.FW_FILESYS_UNLINK] = unlink | 1;
        api[FirmwareApi.FW_FILESYS_MKDIR] = mkdir | 1;
        for (int offset = 78; offset <= 102; offset += 8)
            if (!thunk(r, open + offset))
                return r.fail(FirmwareResolver.ERROR_ABI, FirmwareApi.FW_FILESYS_CLOSE);
        if (r.thumbCall(open + 70) != api[FirmwareApi.FW_OS_BLOCK_DEALLOC]
                || r.thumbCall(unlink + 66) != api[FirmwareApi.FW_OS_BLOCK_DEALLOC]
                || r.thumbCall(mkdir + 56) != api[FirmwareApi.FW_OS_BLOCK_DEALLOC]
                || r.thumbCall(open + 16) != r.thumbCall(unlink + 8)
                || r.thumbCall(open + 16) != r.thumbCall(mkdir + 8)
                || r.thumbCall(open + 22) != r.thumbCall(unlink + 14)
                || r.thumbCall(open + 22) != r.thumbCall(mkdir + 14)
                || r.thumbCall(readNative + 138) != api[FirmwareApi.FW_FILESYS_LSEEK]
                || r.thumbCall(readNative + 168) != api[FirmwareApi.FW_OS_BLOCK_ALLOC_NO_WAIT]
                || r.thumbCall(readNative + 192) != api[FirmwareApi.FW_FILESYS_READ]
                || r.thumbCall(readNative + 248) != api[FirmwareApi.FW_OS_BLOCK_DEALLOC])
            return r.fail(FirmwareResolver.ERROR_ABI, FirmwareApi.FW_FILESYS_OPEN);
        int delta = f[Rm217Profile.KVM_TIME_DELTA];
        int clockThunk = r.thumbCall(delta + 2) & ~1;
        if (r.thumbCall(f[Rm217Profile.KVM_CURRENT_TIME_MILLIS] + 2)
                        != (f[Rm217Profile.KVM_TIME_ACCUMULATOR] | 1)
                || r.thumbCall(f[Rm217Profile.KVM_TIME_ACCUMULATOR] + 16) != (delta | 1)
                || !thunk(r, clockThunk)
                || r.thumbCall(clockThunk + 2) != api[FirmwareApi.FW_OS_CURRENT_TIME_READ_IN_MS])
            return r.fail(FirmwareResolver.ERROR_ABI, FirmwareApi.FW_OS_CURRENT_TIME_READ_IN_MS);

        int loop = f[ResolverPatterns.TASK_LOOP];
        api[FirmwareApi.FW_OS_TASK_OWN_ID_READ] = f[Rm217Profile.TASK_OWN_ID] | 1;
        api[FirmwareApi.FW_OS_MSG_RECEIVE] = r.thumbCall(loop + p.taskReceiveCallOffset);
        api[FirmwareApi.FW_VM_TIMER] = r.thumbCall(loop + p.taskVmTimerCallOffset);
        api[FirmwareApi.FW_MSG_COUNT] = r.thumbCall(loop + p.taskMsgCountCallOffset);
        api[FirmwareApi.FW_CM_TIMER_TIMEOUT] = r.thumbCall(loop + p.taskTimerTimeoutCallOffset);
        api[FirmwareApi.FW_CM_COMMGR_PROCESSPHONETMESSAGE] =
                r.thumbCall(loop + p.taskPhonetCallOffset);
        api[FirmwareApi.FW_CM_COMMGR_PROCESSINTERNALQUEUE] =
                r.thumbCall(loop + p.taskInternalQueueCallOffset);
        api[FirmwareApi.FW_CM_COMMGR_SCANISISUBSCRIPTIONS] =
                r.thumbCall(loop + p.taskSubscriptionsCallOffset);
        int receive = api[FirmwareApi.FW_OS_MSG_RECEIVE] & ~1;
        int internal = api[FirmwareApi.FW_CM_COMMGR_PROCESSINTERNALQUEUE] & ~1;
        int phonet = api[FirmwareApi.FW_CM_COMMGR_PROCESSPHONETMESSAGE] & ~1;
        int timeout = api[FirmwareApi.FW_CM_TIMER_TIMEOUT] & ~1;
        int subscriptions = api[FirmwareApi.FW_CM_COMMGR_SCANISISUBSCRIPTIONS] & ~1;
        if (api[FirmwareApi.FW_VM_TIMER] != (f[ResolverPatterns.VM_TIMER] | 1)
                || api[FirmwareApi.FW_MSG_COUNT] != (f[ResolverPatterns.MSG_COUNT] | 1)
                || !derived(r, Rm217Profile.RECEIVE, receive)
                || !derived(r, Rm217Profile.INTERNAL, internal)
                || !derived(r, Rm217Profile.PHONET, phonet)
                || !derived(r, Rm217Profile.TIMEOUT, timeout)
                || !derived(r, Rm217Profile.SUBSCRIPTIONS, subscriptions)
                || r.thumbCall(loop + 72) != api[FirmwareApi.FW_CM_COMMGR_PROCESSINTERNALQUEUE]
                || r.thumbCall(internal + 2) != api[FirmwareApi.FW_OS_TASK_OWN_ID_READ]
                || r.thumbCall(phonet + 6) != api[FirmwareApi.FW_OS_TASK_OWN_ID_READ]
                || r.thumbCall(timeout + 4) != api[FirmwareApi.FW_OS_TASK_OWN_ID_READ]
                || r.thumbCall(subscriptions + 4) != api[FirmwareApi.FW_OS_TASK_OWN_ID_READ])
            return r.fail(FirmwareResolver.ERROR_ABI, FirmwareResolver.ABI_TASK_CALLS);
        int comm = r.literal(internal + 6), state = r.literal(f[Rm217Profile.TASK_OWN_ID]);
        if (!r.ram(comm) || !r.ram(state) || comm != r.literal(phonet + 10)
                || comm != r.literal(subscriptions + 8) || state != r.literal(receive + 94)
                || state != r.literal(f[ResolverPatterns.MSG_COUNT] + 86))
            return r.fail(FirmwareResolver.ERROR_ABI, FirmwareResolver.ABI_TASK_HELPERS);

        int refresh = f[ResolverPatterns.UI_REFRESH];
        int render = r.thumbCall(refresh + 74) & ~1;
        int present = r.thumbCall(render + 108) & ~1;
        int format = r.thumbCall(present + 16) & ~1;
        if (!derived(r, Rm217Profile.FOREGROUND_RENDER, render)
                || !derived(r, Rm217Profile.FOREGROUND_PRESENT, present)
                || !derived(r, Rm217Profile.FORMAT_TO_HAL, format) || !r.ram(r.literal(refresh + 2))
                || r.literal(refresh + 2) != r.literal(render + 20)
                || r.literal(refresh + 2) != r.literal(present + 72)
                || r.thumbCall(refresh + 40) != r.thumbCall(refresh + 92))
            return r.fail(FirmwareResolver.ERROR_ABI, FirmwareResolver.ABI_LCD_CODE);
        r.uiRefresh = refresh | 1;
        r.specialTimerHandler = 0;
        r.exportCount = 0;
        if (r.error != 0)
            return false;
        for (int i = 0; i < FirmwareApi.REQUIRED_COUNT; i++)
            if (!optional(i) && api[i] == 0)
                return r.fail(FirmwareResolver.ERROR_UNBOUND_API, i);
        return true;
    }

    private static boolean thunk(FirmwareResolver r, int address) {
        return r.rom(address, 8) && r.half(address) == 0xb510 && r.half(address + 6) == 0xbd10
                && r.thumbCall(address + 2) != 0;
    }
    private static boolean derived(FirmwareResolver r, int index, int address) {
        r.found[index] = address;
        return r.patternAt(index, address);
    }
}
