/* SPDX-License-Identifier: MIT */
/** Audio capability discovery through exports, native layouts and checked call relationships. */
final class AudioBindings {
    private AudioBindings() {}

    private static final String[] EXPORT_NAMES = {"CM_ComMgr_Register", "CM_ComMgr_SetReady",
            "CM_ComMgr_UnRegister", "CM_ComMgr_SendToServer", "CM_ISIMes_Delete",
            "CM_Source_Factory_Return", "CM_Source_Factory_Forget", "CM_Fragment_Init",
            "CM_Source_WriteData", "_CallClass0"};
    private static final int[] EXPORT_SLOTS = {FirmwareApi.AUDIO_REGISTER_OBJECT,
            FirmwareApi.AUDIO_READY, FirmwareApi.AUDIO_UNREGISTER_OBJECT, FirmwareApi.AUDIO_SEND,
            FirmwareApi.AUDIO_DELETE_MESSAGE, FirmwareApi.AUDIO_SOURCE_ID,
            FirmwareApi.AUDIO_SOURCE_FORGET, FirmwareApi.AUDIO_FRAGMENT_INIT,
            FirmwareApi.AUDIO_SOURCE_WRITE, FirmwareApi.AUDIO_CLASS_CALL};

    // Shared by RM-443 08.90/10.12 and RM-614 04.60/08.63. Masks omit only
    // literal-pool displacement and decoded BL/BLX targets; layout operations,
    // state transitions and internal control flow remain exact.

    // Constructs Active_Source, immediately followed by its +108/+112 watermarks setter.
    // Class literals and the constructor call must match the checked class graph and export.
    private static final RomRecipe CREATE_LIMITS =
            AudioPatterns.AUDIO_CREATE_LIMITS();

    // Constructs a CM_Source_Producer and stores it at source +44.
    // The producer class, common ConnLayer parent and _CallClass2 target are checked.
    private static final RomRecipe SHARE = AudioPatterns.AUDIO_SHARE();

    // Selects EOF state 3/5 at source +55, clears source field +32, then notifies.
    // The complete state transition is located beside the exported fragment initializer.
    private static final RomRecipe EOF = AudioPatterns.AUDIO_EOF();

    // Maps an input interval through native endpoint bytes +17/+18 and rounds.
    // The RAM global and repeated conversion/addition helper targets are checked.
    private static final RomRecipe VOLUME = AudioPatterns.AUDIO_VOLUME();

    // Reads the requested PCM block; a live short read enters the wait helper.
    // The read export, wait routine and shared end-of-source predicate are verified.
    private static final RomRecipe RAW_READ = AudioPatterns.AUDIO_RAW_READ();

    // Registers a firmware consumer callback and returns 0x100 while waiting.
    // Consumer/Source_Ok exports, end predicate and callback ROM bounds are checked.
    private static final RomRecipe RAW_WAIT = AudioPatterns.AUDIO_RAW_WAIT();

    // Uses registration index +10 to validate its 16-byte task slot, then marks it ready.
    // Its current-task call must match the resolved OS export.
    private static final RomRecipe READY = AudioPatterns.AUDIO_READY();

    // The same +10 registration-index check, with an expanded diagnostic failure path.
    // Both variants retain the owner comparison and ready-state write.
    private static final RomRecipe READY_TRACE = AudioPatterns.AUDIO_READY_TRACE();

    // Consumes cancelled writes; otherwise links the fragment and updates queue bytes +48.
    // Status +55, fragment count +52 and consumer notification establish the source layout.
    private static final RomRecipe SOURCE_WRITE = AudioPatterns.AUDIO_SOURCE_WRITE();

    // Only these two roots require a bank-wide search. Source helpers are
    // found in bounded neighborhoods of named exports; exported code is checked
    // in place. RomSearch shares one anchor pass and validates cached locations.
    private static final RomSearch ROOTS = new RomSearch(new RomRecipe[] {VOLUME, RAW_READ});

    static void prepare() {
        FirmwareClasses.prepare();
        AudioLegacyBindings.prepare();
        AudioDeviceBindings.prepare();
        AudioPolicyBindings.prepare();
    }

    static boolean bind(FirmwareResolver r) {
        if (!r.hasExports())
            return "RM-217".equals(r.familyId())
                    ? AudioLegacyBindings.bind(r)
                    : "RM-839".equals(r.familyId()) && AudioPolicyBindings.bind(r);
        int sourceClass = r.exportObject("CCM_Source");
        for (int i = 0; i < EXPORT_NAMES.length; i++) {
            int entry = r.export(EXPORT_NAMES[i]);
            if (entry == 0)
                return false;
            r.api[EXPORT_SLOTS[i]] = entry;
        }
        if (FirmwareClasses.classAt(r, sourceClass, 120, "CM_Source"))
            return AudioDeviceBindings.bind(r, sourceClass);
        if (!FirmwareClasses.classAt(r, sourceClass, 108, "CM_Source"))
            return false;
        int parent = r.word(sourceClass + 4);
        if (!FirmwareClasses.classAt(r, parent, 12, "ConnLayer"))
            return false;
        int ready = r.api[FirmwareApi.AUDIO_READY] & ~1;
        int write = r.api[FirmwareApi.AUDIO_SOURCE_WRITE] & ~1;
        if ((!r.matches(ready, READY) && !r.matches(ready, READY_TRACE))
                || !r.matches(write, SOURCE_WRITE)
                || r.thumbCall(ready + 4) != r.api[FirmwareApi.FW_OS_TASK_OWN_ID_READ])
            return false;

        int property = r.export("CM_Source_SetProperty");
        int classCall2 = r.export("_CallClass2");
        if (property == 0 || classCall2 == 0 || !r.rom(sourceClass, 512))
            return false;
        int activeClass = activeClass(r, sourceClass, 120);
        if (activeClass == 0)
            return false;
        // This class declares the normal C004 initializer first, followed by an
        // alternate initializer with the same selector. Preserve that ordering.
        int methods = r.word(activeClass + 8);
        if (r.word(methods) != 0xc004)
            return false;
        int initializer = r.word(methods + 4);
        if ((initializer & 1) == 0 || !r.rom(initializer & ~1, 1024))
            return false;
        int create = r.findUnique(CREATE_LIMITS, initializer & ~1, 1024);
        int share = r.findUnique(SHARE, (property & ~1) - 256, 256);
        int eof = r.findUnique(EOF, (r.api[FirmwareApi.AUDIO_FRAGMENT_INIT] & ~1) - 1024, 1024);
        if (create == 0 || share == 0 || eof == 0 || r.literal(create + 2) != 0xc003
                || r.thumbCall(create + 6) != r.api[FirmwareApi.AUDIO_CLASS_CALL]
                || r.literal(share + 0x36) != 0xc003 || r.thumbCall(share + 0x3e) != classCall2)
            return false;
        int producerClass = r.literal(share + 0x38);
        if (r.literal(create + 4) != activeClass
                || !FirmwareClasses.classAt(r, producerClass, 20, "CM_Source_Producer")
                || r.word(producerClass + 4) != parent || !ROOTS.resolve(r))
            return false;

        int volume = ROOTS.address(0), reader = ROOTS.address(1);
        // The three unsigned-to-float calls and paired additions are the same
        // helpers; the global is a RAM slot and may legitimately hold null.
        if (!r.ram(r.literal(volume + 4), 4)
                || r.thumbCall(volume + 0x14) != r.thumbCall(volume + 0x1c)
                || r.thumbCall(volume + 0x14) != r.thumbCall(volume + 0x3c)
                || r.thumbCall(volume + 0x42) != r.thumbCall(volume + 0x4a))
            return false;
        int wait = r.thumbCall(reader + 0x2c) & ~1;
        if (!r.matches(wait, RAW_WAIT) || r.thumbCall(reader + 0x10) != r.export("CM_Source_Read")
                || r.thumbCall(wait + 0x0a) != r.export("CM_Source_SetConsumer")
                || r.thumbCall(wait + 0x20) != r.export("CM_Source_Ok")
                || r.thumbCall(reader + 0x1c) != r.thumbCall(wait + 0x16)
                || !r.rom(r.literal(wait + 4) & ~1, 2))
            return false;
        r.api[FirmwareApi.AUDIO_PARENT_CLASS] = parent;
        r.api[FirmwareApi.AUDIO_SOURCE_CREATE] = create | 1;
        r.api[FirmwareApi.AUDIO_SOURCE_LIMITS] = (create + 12) | 1;
        r.api[FirmwareApi.AUDIO_SOURCE_SHARE] = share | 1;
        r.api[FirmwareApi.AUDIO_SOURCE_EOF] = eof | 1;
        r.api[FirmwareApi.AUDIO_VOLUME_SCALE] = volume | 1;
        r.api[FirmwareApi.AUDIO_ABI] = FirmwareApi.AUDIO_ABI_CLASSIC;
        return r.error == 0;
    }

    static int activeClass(FirmwareResolver r, int sourceClass, int objectBytes) {
        if (!r.rom(sourceClass, 512))
            return 0;
        int active = 0;
        for (int offset = 0; offset <= 512 - 20; offset += 4) {
            int candidate = sourceClass + offset;
            if (r.word(candidate) != candidate || r.word(candidate + 4) != sourceClass
                    || !FirmwareClasses.classAt(r, candidate, objectBytes, "Active_Source"))
                continue;
            if (active != 0)
                return 0;
            active = candidate;
        }
        return active;
    }
}
