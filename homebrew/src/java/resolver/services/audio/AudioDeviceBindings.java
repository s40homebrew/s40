/* SPDX-License-Identifier: MIT */
/** Exported ComMgr with device-addressed objects and active PCM sources. */
final class AudioDeviceBindings {
    private AudioDeviceBindings() {}

    // The paired constructor/limits helper and source state operations establish
    // the 16-byte ComMgr / 120-byte source layout. Calls and literal addresses
    // are decoded separately; native field accesses and control flow stay exact.
    // Constructs Active_Source, followed by watermarks at source +120/+124.
    // The checked class literal and exported dispatcher identify the constructor.
    private static final RomRecipe CREATE = AudioDevicePatterns.AUDIO_DEVICE_CREATE();

    // Constructs a 24-byte producer and stores it at source +48.
    // Its class parent, _CallClass2 target and shared return epilogue are checked.
    private static final RomRecipe SHARE = AudioDevicePatterns.AUDIO_DEVICE_SHARE();

    // Selects EOF state 3/5 at source +64 and clears source field +36 before notifying.
    // This layout is checked beside the exported fragment initializer.
    private static final RomRecipe EOF = AudioDevicePatterns.AUDIO_DEVICE_EOF();

    // Looks up registration index +12, validates the slot owner and writes ready state 3.
    // The task lookup must be the resolved current-task export.
    private static final RomRecipe READY = AudioDevicePatterns.AUDIO_DEVICE_READY();

    // Consumes cancelled fragments or queues data at source +52 and notifies the consumer.
    // Status +64 and end-position +76 distinguish this source layout from the classic one.
    private static final RomRecipe WRITE = AudioDevicePatterns.AUDIO_DEVICE_WRITE();

    // ARM/VFP mapping through native endpoint bytes +13/+14, preserving integer arguments.
    // The RAM-global literal and 0.5f rounding constant are verified; ARM state is retained.
    private static final RomRecipe VOLUME = AudioDevicePatterns.AUDIO_DEVICE_VOLUME();

    // Fills a requested PCM block and delegates live underflow to WAIT.
    // Both device backends check the wait/end links; the exported backend also checks Read.
    static final RomRecipe READER = AudioDevicePatterns.AUDIO_DEVICE_READER();

    // Installs a firmware consumer callback, returning 0x100 for pending input.
    // Callers check the shared end predicate and callback; exports add consumer/OK checks.
    static final RomRecipe WAIT = AudioDevicePatterns.AUDIO_DEVICE_WAIT();

    private static final RomSearch ROOTS = new RomSearch(new RomRecipe[] {VOLUME, READER});

    static void prepare() {}

    static boolean bind(FirmwareResolver r, int sourceClass) {
        int parent = r.word(sourceClass + 4);
        if (!FirmwareClasses.classAt(r, sourceClass, 120, "CM_Source")
                || !FirmwareClasses.classAt(r, parent, 16, "ConnLayer"))
            return false;
        int ready = r.api[FirmwareApi.AUDIO_READY] & ~1;
        if (!r.matches(ready, READY)
                || r.thumbCall(ready + 4) != r.api[FirmwareApi.FW_OS_TASK_OWN_ID_READ]
                || !r.matches(r.api[FirmwareApi.AUDIO_SOURCE_WRITE] & ~1, WRITE))
            return false;
        int active = AudioBindings.activeClass(r, sourceClass, 132);
        if (active == 0)
            return false;
        int methods = r.word(active + 8);
        if (r.word(methods) != 0xc004)
            return false;
        int initializer = r.word(methods + 4);
        if ((initializer & 1) == 0 || !r.rom(initializer & ~1, 1024))
            return false;
        int create = r.findUnique(CREATE, initializer & ~1, 1024);
        int property = r.export("CM_Source_SetProperty");
        int share = r.findUnique(SHARE, (property & ~1) - 256, 256);
        int eof = r.findUnique(EOF, (r.api[FirmwareApi.AUDIO_FRAGMENT_INIT] & ~1) - 1024, 1024);
        if (create == 0 || share == 0 || eof == 0 || property == 0 || r.literal(create) != 0xc003
                || r.literal(create + 2) != active
                || r.thumbCall(create + 6) != r.api[FirmwareApi.AUDIO_CLASS_CALL]
                || r.literal(share + 0x1e) != 0xc006
                || r.thumbCall(share + 0x28) != r.export("_CallClass2")
                || r.half(r.thumbBranch(share + 0x2e)) != 0xbd1c)
            return false;
        int producer = r.literal(share + 0x20);
        if (!FirmwareClasses.classAt(r, producer, 24, "CM_Source_Producer")
                || r.word(producer + 4) != parent || !ROOTS.resolve(r))
            return false;
        int volume = ROOTS.address(0), reader = ROOTS.address(1);
        // ARM/VFP implementation, with integer r0/r1/r2 arguments and r0 result.
        // Its lower/upper native endpoints are bytes 13/14, not classic 17/18.
        int rounding = volume + 0x54 + 8 + ((r.word(volume + 0x54) & 255) << 2);
        if (!r.ram(r.armLiteral(volume, 3), 4) || r.word(rounding) != 0x3f000000)
            return false;
        int wait = r.thumbCall(reader + 0x2c) & ~1;
        if (!r.matches(wait, WAIT) || r.thumbCall(reader + 0x10) != r.export("CM_Source_Read")
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
        r.api[FirmwareApi.AUDIO_VOLUME_SCALE] = volume;
        r.api[FirmwareApi.AUDIO_ABI] = FirmwareApi.AUDIO_ABI_DEVICE_IHF;
        return r.error == 0;
    }
}
