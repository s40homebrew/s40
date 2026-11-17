/* SPDX-License-Identifier: MIT */
/**
 * Descriptor-free ComMgr discovery. Recipes retain object fields and control flow;
 * only decoded address-dependent calls and literal loads are masked.
 */
final class AudioLegacyBindings {
    private AudioLegacyBindings() {}

    private static final int REGISTER_OBJECT = 0;
    private static final int READY = 1;
    private static final int UNREGISTER_OBJECT = 2;
    private static final int SEND = 3;
    private static final int SOURCE_CREATE = 4;
    private static final int SOURCE_SHARE = 5;
    private static final int SOURCE_ID = 6;
    private static final int SOURCE_FORGET = 7;
    private static final int SOURCE_EOF = 8;
    private static final int FRAGMENT_INIT = 9;
    private static final int SOURCE_WRITE = 10;
    private static final int VOLUME_SCALE = 11;

    private static final RomRecipe[] RECIPES = {
            // Registration checks resource routing and reads the legacy object ID at +4.
            AudioLegacyPatterns.REGISTER_OBJECT(),
            // Ready uses registration index +8 and a 16-byte per-task object slot.
            AudioLegacyPatterns.READY(),
            // Unregister validates that the indexed slot belongs to this object.
            AudioLegacyPatterns.UNREGISTER_OBJECT(),
            // SendToServer deletes the owned message if no server is registered.
            AudioLegacyPatterns.SEND(),
            // Active_Source construction followed by its +96/+100 watermarks setter.
            AudioLegacyPatterns.SOURCE_CREATE(),
            // A CM_Source_Producer is constructed and stored at source +44.
            AudioLegacyPatterns.SOURCE_SHARE(),
            // Factory records hold type 19; publication unregisters the original source.
            AudioLegacyPatterns.SOURCE_ID(),
            // Forget consumes types 5/19 and destroys the untaken source with C005.
            AudioLegacyPatterns.SOURCE_FORGET(),
            // Sets EOF state 3/5 at source +55 and clears source field +32 before notification.
            AudioLegacyPatterns.SOURCE_EOF(),
            // The seven-word fragment owns data and uses a default freeing destructor.
            AudioLegacyPatterns.FRAGMENT_INIT(),
            // Queue bytes +48, fragments +52 and status +55; cancellation consumes data.
            AudioLegacyPatterns.SOURCE_WRITE(),
            // Map percent through native range bytes +17/+18 and round the result.
            AudioLegacyPatterns.VOLUME_SCALE(),
    };
    private static final RomSearch SEARCH = new RomSearch(RECIPES);

    static void prepare() { FirmwareClasses.prepare(); }

    static boolean bind(FirmwareResolver r) {
        if (!"RM-217".equals(r.familyId()) || r.hasExports() || !SEARCH.resolve(r))
            return false;
        int[] api = r.api;
        int create = SEARCH.address(SOURCE_CREATE), share = SEARCH.address(SOURCE_SHARE);
        int activeClass = r.literal(create + 4), producer = r.literal(share + 10);
        if (!FirmwareClasses.classAt(r, activeClass, 108, "Active_Source")
                || !FirmwareClasses.classAt(r, producer, 20, "CM_Source_Producer"))
            return false;
        int source = r.word(activeClass + 4), parent = r.word(producer + 4);
        if (!FirmwareClasses.classAt(r, source, 96, "CM_Source") || r.word(source + 4) != parent
                || !FirmwareClasses.classAt(r, parent, 12, "ConnLayer"))
            return false;
        int classCall = r.thumbCall(create + 6);
        if (r.literal(create + 2) != 0xc003 || r.literal(share + 2) != 0xc006
                || !r.matches(classCall & ~1, CLASS_CALL)
                || r.literal(SEARCH.address(SOURCE_FORGET) + 20) != 0xc005
                || r.thumbCall(SEARCH.address(SOURCE_FORGET) + 22) != classCall)
            return false;
        int register = SEARCH.address(REGISTER_OBJECT), ready = SEARCH.address(READY);
        int unregister = SEARCH.address(UNREGISTER_OBJECT);
        int task = api[FirmwareApi.FW_OS_TASK_OWN_ID_READ];
        if (r.thumbCall(register + 42) != task || r.thumbCall(ready + 4) != task
                || r.thumbCall(unregister + 6) != task
                || r.literal(register + 46) != r.literal(ready + 8)
                || r.literal(ready + 8) != r.literal(unregister + 10)
                || !r.ram(r.literal(ready + 8)))
            return false;
        int messageDelete = r.thumbCall(SEARCH.address(SEND) + 32);
        if (!r.matches(messageDelete & ~1, DELETE_MESSAGE)
                || r.thumbCall((messageDelete & ~1) + 20) != api[FirmwareApi.FW_OS_BLOCK_DEALLOC]
                || r.thumbCall(SEARCH.address(SOURCE_ID) + 70) != (unregister | 1)
                || !r.ram(r.literal(SEARCH.address(VOLUME_SCALE) + 4)))
            return false;
        int fragment = SEARCH.address(FRAGMENT_INIT), destructor = r.literal(fragment + 42);
        if (!r.matches(destructor & ~1, FRAGMENT_DESTRUCTOR)
                || r.thumbCall((destructor & ~1) + 6) != api[FirmwareApi.FW_OS_BLOCK_DEALLOC]
                || r.thumbCall((destructor & ~1) + 12) != api[FirmwareApi.FW_OS_BLOCK_DEALLOC])
            return false;
        api[FirmwareApi.AUDIO_PARENT_CLASS] = parent;
        api[FirmwareApi.AUDIO_REGISTER_OBJECT] = register | 1;
        api[FirmwareApi.AUDIO_READY] = ready | 1;
        api[FirmwareApi.AUDIO_UNREGISTER_OBJECT] = unregister | 1;
        api[FirmwareApi.AUDIO_SEND] = SEARCH.address(SEND) | 1;
        api[FirmwareApi.AUDIO_DELETE_MESSAGE] = messageDelete;
        api[FirmwareApi.AUDIO_SOURCE_CREATE] = create | 1;
        api[FirmwareApi.AUDIO_SOURCE_SHARE] = share | 1;
        api[FirmwareApi.AUDIO_SOURCE_LIMITS] = (create + 12) | 1;
        api[FirmwareApi.AUDIO_SOURCE_ID] = SEARCH.address(SOURCE_ID) | 1;
        api[FirmwareApi.AUDIO_SOURCE_FORGET] = SEARCH.address(SOURCE_FORGET) | 1;
        api[FirmwareApi.AUDIO_SOURCE_EOF] = SEARCH.address(SOURCE_EOF) | 1;
        api[FirmwareApi.AUDIO_FRAGMENT_INIT] = fragment | 1;
        api[FirmwareApi.AUDIO_SOURCE_WRITE] = SEARCH.address(SOURCE_WRITE) | 1;
        api[FirmwareApi.AUDIO_CLASS_CALL] = classCall;
        api[FirmwareApi.AUDIO_VOLUME_SCALE] = SEARCH.address(VOLUME_SCALE) | 1;
        api[FirmwareApi.AUDIO_ABI] = FirmwareApi.AUDIO_ABI_LEGACY;
        return r.error == 0;
    }

    // Method dispatch preserves the original object and method, supplying zero arguments.
    private static final RomRecipe CLASS_CALL = AudioLegacyPatterns.CLASS_CALL();
    // Skips null and the task's current message; otherwise frees the allocation at message -8.
    // Its free call must match the resolved OS deallocator.
    private static final RomRecipe DELETE_MESSAGE =
            AudioLegacyPatterns.DELETE_MESSAGE();
    // Releases the data pointer at fragment +4, then releases the fragment itself.
    // Both calls must match the resolved OS deallocator.
    private static final RomRecipe FRAGMENT_DESTRUCTOR = AudioLegacyPatterns.FRAGMENT_DESTRUCTOR();
}
