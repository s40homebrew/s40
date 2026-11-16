/* SPDX-License-Identifier: MIT */
import java.lang.reflect.Method;

/**
 * Host-only regression: calls the real compiled launcher selector, without
 * constructing a MIDlet, executing the bootstrap, or running an ARM emulator.
 * Run with build/preverified and the S40 SDK classes on the host classpath.
 */
public final class CheckStagePlacement {
    private static final int MAX_LOADER_BYTES = 3072;
    private static final int CARRIER_BYTES = 4096;
    private static final int CARRIER_WORDS = CARRIER_BYTES / 4;
    private static final int STAGE_BYTES = 44;
    private static final int CACHE_LINE_BYTES = 32;
    private static final int SYNCED_CACHE_BYTES = 2 * CACHE_LINE_BYTES;
    private static final int NATIVE_START_WORD = SYNCED_CACHE_BYTES / 4;
    private static final int ACC_STATIC = 0x0008;
    private static final int ACC_NATIVE = 0x0100;
    private static final int ACC_ABSTRACT = 0x0400;
    // Deliberately arbitrary addresses: placement depends on bits, not firmware.
    private static final int TEST_HEAP_BASE = 0xD1000000;
    private static final int TEST_METHOD = 0xD2000000;

    private static void require(boolean ok, String message) {
        if (!ok)
            throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Method select = Class.forName("NativeSession")
                                .getDeclaredMethod("findStageIndex", Integer.TYPE, Integer.TYPE,
                                        Integer.TYPE, Integer.TYPE, Integer.TYPE);
        select.setAccessible(true);
        long bytes = new java.io.File(args[0]).length();
        require(bytes > 0 && bytes <= MAX_LOADER_BYTES, "Missing/oversized native loader");
        int nativeLength = (int) bytes;
        int checked = 0;
        // Cover every aligned low-12-bit heap placement, including the old
        // failure at ...0508 (native+static+abstract, and stage fits cache).
        for (int low = 0; low < CARRIER_BYTES; low += 4) {
            int base = TEST_HEAP_BASE + low;
            int index = ((Integer) select.invoke(
                                 null, base, CARRIER_WORDS, TEST_METHOD, STAGE_BYTES, nativeLength))
                                .intValue();
            require(index >= 0, "No placement at " + Integer.toHexString(base));
            int stage = base + index * 4;
            require((stage & ACC_NATIVE) != 0 && (stage & ACC_STATIC) != 0, "Not native/static");
            require((stage & ACC_ABSTRACT) == 0, "Abstract method address selected");
            require((stage & 3) == 0, "Unaligned ARM stage");
            require((stage & (CACHE_LINE_BYTES - 1)) + STAGE_BYTES <= SYNCED_CACHE_BYTES,
                    "Stage escapes synced cache lines");
            require((stage & 0xffffffffL) + (TEST_METHOD & 0xffffffffL) > 0xffffffffL,
                    "Cache range did not wrap");
            require((index + NATIVE_START_WORD) * 4 + nativeLength <= CARRIER_BYTES,
                    "Loader escapes payload allocation");
            ++checked;
        }
        require(((Integer) select.invoke(
                         null, 0xD1000508, 16, TEST_METHOD, STAGE_BYTES, nativeLength))
                                .intValue()
                        == -1,
                "Accepted undersized carrier");
        require(((Integer) select.invoke(
                         null, 0x10000508, CARRIER_WORDS, 0x20000000, STAGE_BYTES, nativeLength))
                                .intValue()
                        == -1,
                "Accepted non-wrapping range");
        require(((Integer) select.invoke(null, 0xD1000508, CARRIER_WORDS, TEST_METHOD,
                         SYNCED_CACHE_BYTES + 1, nativeLength))
                                .intValue()
                        == -1,
                "Accepted oversized stage");
        System.out.println(
                "PASS: " + checked + " heap placements and invalid bounds; real launcher selector");
    }
}
