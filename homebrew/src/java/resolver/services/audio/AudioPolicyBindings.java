/* SPDX-License-Identifier: MIT */
/** Descriptor-free ComMgr with device addressing and media policy streams. */
final class AudioPolicyBindings {
    private AudioPolicyBindings() {}

    // These roots preserve native layout operations and control flow across
    // the two 301 builds. Calls and literals are checked through relationships
    // after the single batched search; no firmware address is a lookup key.
    // Registers the device-addressed object using its ID at +8.
    // Task/context references and the shared stack-release epilogue are checked.
    private static final RomRecipe CM_REGISTER =
            AudioPolicyPatterns.AUDIO_POLICY_CM_REGISTER();

    // Uses registration index +12 to verify its task-slot owner before setting state 3.
    // Register, Unregister and Send must use the same task accessor and context.
    private static final RomRecipe CM_READY = AudioPolicyPatterns.AUDIO_POLICY_CM_READY();

    // Validates the indexed object slot before unregistering it.
    // Factory publication must call this same routine; its task/context links are checked.
    private static final RomRecipe CM_UNREGISTER =
            AudioPolicyPatterns.AUDIO_POLICY_CM_UNREGISTER();

    // Selects the per-task server endpoint and deletes the message on failure.
    // The delete call, task/context references and shared return are followed and checked.
    private static final RomRecipe CM_SEND = AudioPolicyPatterns.AUDIO_POLICY_CM_SEND();

    // Constructs Active_Source, then sets its +120/+124 watermarks.
    // C003, the 132/120/16-byte class chain and the method dispatcher are checked.
    private static final RomRecipe SOURCE_CREATE =
            AudioPolicyPatterns.AUDIO_POLICY_SOURCE_CREATE();

    // Constructs CM_Source_Producer and stores it at source +48.
    // Its 24-byte class shares ConnLayer with the source; its dispatcher is validated.
    private static final RomRecipe SOURCE_SHARE = AudioPolicyPatterns.AUDIO_POLICY_SOURCE_SHARE();

    // Publishes a type-22 factory record and unregisters the original source.
    // Allocation, deallocation and Unregister calls must agree with other resolved routines.
    private static final RomRecipe SOURCE_ID = AudioPolicyPatterns.AUDIO_POLICY_SOURCE_ID();

    // Consumes type-5/type-22 IDs and destroys an untaken source through C005.
    // The adjusted selector literal and dispatcher must match source construction.
    private static final RomRecipe SOURCE_FORGET =
            AudioPolicyPatterns.AUDIO_POLICY_SOURCE_FORGET();

    // Sets EOF state 3/5 at source +64, clears source field +36 and notifies.
    // These field accesses establish the device-source EOF layout.
    private static final RomRecipe SOURCE_EOF = AudioPolicyPatterns.AUDIO_POLICY_SOURCE_EOF();

    // Initializes a 28-byte fragment with data, lengths and an optional destructor.
    // The default destructor, allocator and stack-release epilogue are checked separately.
    private static final RomRecipe FRAGMENT_INIT =
            AudioPolicyPatterns.AUDIO_POLICY_FRAGMENT_INIT();

    // Rejects terminal writes or links a fragment, updating queue +52 and end +76.
    // State +64 and consumer callbacks establish the active-source queue contract.
    private static final RomRecipe SOURCE_WRITE = AudioPolicyPatterns.AUDIO_POLICY_SOURCE_WRITE();

    // Maps an input interval through endpoint bytes +13/+14 and rounds to a native level.
    // The RAM global and repeated floating-point conversion/addition calls are checked.
    private static final RomRecipe VOLUME_SCALE =
            AudioPolicyPatterns.AUDIO_POLICY_VOLUME_SCALE();

    // Loads the raw context at player +276 and records the number of bytes read.
    // Its cross-bank call must lead to the checked PCM reader and consumer-wait helper.
    private static final RomRecipe RAW_READER =
            AudioPolicyPatterns.AUDIO_POLICY_RAW_READER();

    // Builds 0x0BA0 with an application descriptor, flags 12 and a stream subblock.
    // Existing applications use the shorter request; the default-preference getter is checked.
    private static final RomRecipe POLICY = AudioPolicyPatterns.AUDIO_POLICY_POLICY();

    // Resolves a method and invokes it with the original object/selector and no arguments.
    // Source creation and Forget share this dispatcher; CLASS_CALL2 shares its lookup helper.
    private static final RomRecipe CLASS_CALL =
            AudioPolicyPatterns.AUDIO_POLICY_CLASS_CALL();

    // Skips null and the task's current message; otherwise frees the allocation at message -8.
    // The deallocator must match factory-record and fragment cleanup.
    private static final RomRecipe DELETE = AudioPolicyPatterns.AUDIO_POLICY_DELETE();

    // Frees both the fragment data at +4 and the fragment itself.
    // Both free calls must use the same deallocator as message and factory cleanup.
    private static final RomRecipe DESTRUCTOR =
            AudioPolicyPatterns.AUDIO_POLICY_DESTRUCTOR();

    // Preserves two arguments across method lookup and invokes the resolved method.
    // The lookup helper matches CLASS_CALL; its shared return releases the expected stack.
    private static final RomRecipe CLASS_CALL2 = AudioPolicyPatterns.AUDIO_POLICY_CLASS_CALL2();

    // Defaults to preference 4, with manifest overrides for music 3 and navigation 28.
    // Both override comparisons must use the same string-comparison helper.
    private static final RomRecipe PREFERENCE = AudioPolicyPatterns.AUDIO_POLICY_PREFERENCE();

    private static final int REGISTER = 0, READY = 1, UNREGISTER = 2, SEND = 3, CREATE = 4,
                             SHARE = 5, ID = 6, FORGET = 7, EOF = 8, FRAGMENT = 9, WRITE = 10,
                             VOLUME = 11, RAW = 12, POLICY_CREATE = 13;
    private static final RomSearch SEARCH = new RomSearch(new RomRecipe[] {CM_REGISTER, CM_READY,
            CM_UNREGISTER, CM_SEND, SOURCE_CREATE, SOURCE_SHARE, SOURCE_ID, SOURCE_FORGET,
            SOURCE_EOF, FRAGMENT_INIT, SOURCE_WRITE, VOLUME_SCALE, RAW_READER, POLICY});

    static void prepare() {}

    private static boolean tail(FirmwareResolver r, int branch, int releaseStack) {
        int target = r.thumbBranch(branch);
        return target != 0 && r.half(target) == releaseStack && r.half(target + 2) == 0xbdf0;
    }

    static boolean bind(FirmwareResolver r) {
        if (!SEARCH.resolve(r))
            return false;
        int create = SEARCH.address(CREATE), share = SEARCH.address(SHARE);
        int active = r.literal(create + 2), producer = r.literal(share + 22);
        if (r.literal(create) != 0xc003 || r.literal(share + 20) != 0xc003
                || !FirmwareClasses.classAt(r, active, 132, "Active_Source")
                || !FirmwareClasses.classAt(r, producer, 24, "CM_Source_Producer"))
            return false;
        int source = r.word(active + 4), parent = r.word(producer + 4);
        if (!FirmwareClasses.classAt(r, source, 120, "CM_Source") || r.word(source + 4) != parent
                || !FirmwareClasses.classAt(r, parent, 16, "ConnLayer"))
            return false;
        int classCall = r.thumbCall(create + 6), classCall2 = r.thumbCall(share + 28);
        int forget = SEARCH.address(FORGET);
        if (!r.matches(classCall & ~1, CLASS_CALL) || !r.matches(classCall2 & ~1, CLASS_CALL2)
                || r.thumbCall((classCall & ~1) + 12) != r.thumbCall((classCall2 & ~1) + 16)
                || !tail(r, (classCall2 & ~1) + 32, 0xb005) || r.literal(forget + 32) != 0xc01d
                || r.thumbCall(forget + 36) != classCall)
            return false;
        int register = SEARCH.address(REGISTER), ready = SEARCH.address(READY);
        int unregister = SEARCH.address(UNREGISTER), send = SEARCH.address(SEND);
        int task = r.thumbCall(ready + 4), context = r.literal(ready + 8);
        if (r.thumbCall(register + 32) != task || r.thumbCall(unregister + 6) != task
                || r.thumbCall(send + 6) != task || !r.rom(task & ~1, 4)
                || r.literal(register + 36) != context || r.literal(unregister + 10) != context
                || r.literal(send + 10) != context || !r.ram(context, 4)
                || !tail(r, register + 138, 0xb017) || !tail(r, send + 134, 0xb01b))
            return false;
        int delete = r.thumbCall(send + 126), id = SEARCH.address(ID);
        if (!r.matches(delete & ~1, DELETE) || r.thumbCall(id + 146) != (unregister | 1))
            return false;
        int free = r.thumbCall((delete & ~1) + 18), fragment = SEARCH.address(FRAGMENT);
        int destructor = r.literal(fragment + 46);
        if (!r.matches(destructor & ~1, DESTRUCTOR) || r.half(fragment + 80) != 0xb005
                || r.half(fragment + 82) != 0xbdf0 || r.thumbCall(id + 94) != free
                || r.thumbCall((destructor & ~1) + 20) != free
                || r.thumbCall((destructor & ~1) + 26) != free
                || r.thumbCall(fragment + 16) != r.thumbCall(id + 104))
            return false;
        int volume = SEARCH.address(VOLUME);
        if (!r.ram(r.literal(volume + 6), 4) || r.thumbCall(volume + 16) != r.thumbCall(volume + 24)
                || r.thumbCall(volume + 16) != r.thumbCall(volume + 56)
                || r.thumbCall(volume + 62) != r.thumbCall(volume + 70))
            return false;
        // Stock media creation encodes app type/preference 4 and flags 12.
        int preference = r.thumbCall(SEARCH.address(POLICY_CREATE) + 150) & ~1;
        if (!r.matches(preference, PREFERENCE)
                || r.thumbCall(preference + 54) != r.thumbCall(preference + 66))
            return false;
        int reader = r.thumbCall(SEARCH.address(RAW) + 20) & ~1;
        if (!r.matches(reader, AudioDeviceBindings.READER))
            return false;
        int wait = r.thumbCall(reader + 44) & ~1;
        if (!r.matches(wait, AudioDeviceBindings.WAIT)
                || r.thumbCall(reader + 28) != r.thumbCall(wait + 22)
                || !r.rom(r.literal(wait + 4) & ~1, 4))
            return false;
        int[] api = r.api;
        api[FirmwareApi.AUDIO_PARENT_CLASS] = parent;
        api[FirmwareApi.AUDIO_REGISTER_OBJECT] = register | 1;
        api[FirmwareApi.AUDIO_READY] = ready | 1;
        api[FirmwareApi.AUDIO_UNREGISTER_OBJECT] = unregister | 1;
        api[FirmwareApi.AUDIO_SEND] = send | 1;
        api[FirmwareApi.AUDIO_DELETE_MESSAGE] = delete;
        api[FirmwareApi.AUDIO_SOURCE_CREATE] = create | 1;
        api[FirmwareApi.AUDIO_SOURCE_SHARE] = share | 1;
        api[FirmwareApi.AUDIO_SOURCE_LIMITS] = (create + 12) | 1;
        api[FirmwareApi.AUDIO_SOURCE_ID] = id | 1;
        api[FirmwareApi.AUDIO_SOURCE_FORGET] = forget | 1;
        api[FirmwareApi.AUDIO_SOURCE_EOF] = SEARCH.address(EOF) | 1;
        api[FirmwareApi.AUDIO_FRAGMENT_INIT] = fragment | 1;
        api[FirmwareApi.AUDIO_SOURCE_WRITE] = SEARCH.address(WRITE) | 1;
        api[FirmwareApi.AUDIO_CLASS_CALL] = classCall;
        api[FirmwareApi.AUDIO_VOLUME_SCALE] = volume | 1;
        api[FirmwareApi.AUDIO_ABI] = FirmwareApi.AUDIO_ABI_DEVICE_MEDIA;
        return r.error == 0;
    }
}
