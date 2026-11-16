/* SPDX-License-Identifier: MIT */
/** Bounded, allocation-free validation of the firmware's ROM range table. */
final class RomRanges {
    private RomRanges() {}

    // Loading this class must happen before opening a forged memory view.
    static void prepare() {}

    /**
     * Returns the first bank's exclusive end, or zero for malformed metadata.
     * Reads stay inside the caller's already-readable boot prefix. Additional
     * entries corroborate the table; they never enlarge the readable window.
     */
    static int readEnd(int[] view, int base, int start, int prefixBytes, int maxBytes, int table,
            int pageMask, int entryBytes, int endOffset, int minimumBytes, int maxEntries,
            int regionMask, int romRegion) {
        if ((table & 3) != 0 || table - start < 0 || minimumBytes < 8
                || table - start > prefixBytes - minimumBytes)
            return 0;
        int low = view[(table - base) >>> 2];
        int high = view[(table + endOffset - base) >>> 2];
        int bytes = high - start;
        if (low != (start & ~pageMask) || bytes < prefixBytes || bytes > maxBytes
                || (high & 3) != 0)
            return 0;
        for (int i = 1; i < maxEntries; i++) {
            int at = table + entryBytes * i;
            if (at - start < 0 || at - start > prefixBytes - entryBytes)
                return 0;
            int first = view[(at - base) >>> 2];
            int end = view[(at + endOffset - base) >>> 2];
            if (first == 0 && end == 0)
                return high;
            if ((first & 3) != 0 || (first & regionMask) != romRegion || end - first <= 0
                    || end - first > maxBytes)
                return 0;
        }
        return 0;
    }
}
