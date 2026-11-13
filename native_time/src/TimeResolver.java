/* SPDX-License-Identifier: MIT */
/** Read-only, allocation-free discovery, reduced from homebrew's RM-443 resolver. */
final class TimeResolver {
    // Family memory-map bounds, NOT individual firmware function addresses.
    static final int ROM_START = 0xF0100400, ROM_LIMIT = 0xF2000000;
    static final int RAM_START = 0xD8000000, RAM_LIMIT = 0xDA000000;
    private static final String LIBRARY = "libsystem.so";
    private static final String SYMBOL = "os_current_time_read_in_ms";

    // Checked boot range walker: two variable BL halves and one LDR literal.
    private static final int[] RANGE = {
        0xb570, 0x0004, 0x000d, 0xf000, 0xf800, 0x07c0, 0xd00c, 0x4800,
        0x1962, 0xe00c, 0x42a1, 0xd802, 0x6843, 0x42a3, 0xd804, 0x428a,
        0xd304, 0x6841, 0x428a, 0xd201, 0x2001, 0xbd70, 0x3008, 0x6801,
        0x2900, 0xd1ef, 0x2000, 0xbd70
    };
    private static final int[] CACHE = {
        0xe92d4070, 0xe0805001, 0xe3c0001f, 0xee070f3e, 0xee070f35,
        0xe2800020, 0xee070f3e, 0xee070f35, 0xe2800020, 0xe1500005,
        0x3afffff7, 0xee075f9a, 0xe8bd8070
    };
    // The branch destinations vary; every other instruction must match exactly.
    private static final int[] DISPATCH = {
        0xe5de2001, 0xe5de3002, 0xe1a0a00e, 0xe28ae003, 0xe1831402,
        0xe7981101, 0xe1d141be, 0xe5910014, 0xe0464104, 0xe5942000,
        0xe3520000, 0x0a000000, 0xe5912004, 0xe2443004, 0xe3520000,
        0x0a000000, 0xe5959438, 0xe3100008, 0x02833004, 0xe3590000,
        0x0a000000, 0xe5893020, 0xe5894024, 0xe5859468, 0xe5851448,
        0xe285ae42, 0xe88a49c0, 0xe12fff32
    };
    private static final int[] INVOKE = {
        0xe1a01004, 0xe1d141be, 0xe5910014, 0xe28ae003,
        0xe0464104, 0xe3100c01, 0x1a000000
    };

    private int[] view;
    private int base, end, range, dispatch, invoke, library;
    int cache, time;
    boolean rapuMode;
    final RapuTimeResolver rapu = new RapuTimeResolver();

    boolean ram(int a, int bytes) {
        if (rapuMode) return rapu.ram(a, bytes);
        return bytes >= 0 && a - RAM_START >= 0
                && a - RAM_START <= RAM_LIMIT - RAM_START - bytes;
    }

    int resolve(int[] memory, int origin) {
        if (rapuMode) {
            int error = rapu.resolve(memory, origin);
            time = rapu.time;
            cache = rapu.cache;
            return error;
        }
        view = memory;
        base = origin;
        try {
            // Only same-session ROM discoveries are cached; never heap pointers.
            // A changed image or failed recheck falls back to a full scan.
            if (time != 0 && rangeAt(range) && bounds(range) && armAt(cache, CACHE)
                    && abi() && descriptor(library) && symbol(library) == time) return 0;
            time = cache = dispatch = invoke = library = range = 0;
            end = ROM_START + 65536;
            int hits = 0;
            for (int a = ROM_START; a <= end - RANGE.length * 2; a += 2) {
                if (half(a) == RANGE[0] && rangeAt(a)) { range = a; hits++; }
            }
            if (hits != 1) return 101;
            if (!bounds(range)) return 102;

            int cacheHits = 0, dispatchHits = 0, invokeHits = 0, libraryHits = 0;
            // One linear ROM pass, with inline common-path loads. No function
            // address, string location, or export-table address is baked in.
            int[] data = view;
            int first = (ROM_START - base) >>> 2, limit = first + ((end - ROM_START) >>> 2);
            for (int i = first; i < limit; i++) {
                int v = data[i];
                if (v == CACHE[0] && armAt(base + (i << 2), CACHE)) {
                    cache = base + (i << 2); cacheHits++;
                }
                if (v == DISPATCH[0] && armAt(base + (i << 2), DISPATCH)) {
                    dispatch = base + (i << 2); dispatchHits++;
                }
                if (v == INVOKE[0] && armAt(base + (i << 2), INVOKE)) {
                    invoke = base + (i << 2); invokeHits++;
                }
                if (v >= ROM_START && v < end
                        && ((data[(v - base) >>> 2] >>> ((v & 3) * 8)) & 255) == 'l'
                        && text(v, LIBRARY) && descriptor(base + (i << 2))) {
                    library = base + (i << 2); libraryHits++;
                }
            }
            if (cacheHits != 1) return 103;
            if (dispatchHits != 1 || invokeHits != 1 || !abi()) return 104;
            if (libraryHits != 1) return 105;
            time = symbol(library);
            return time == 0 ? 106 : 0;
        } finally {
            view = null;
        }
    }

    private boolean contains(int a, int size) {
        return size >= 0 && a - ROM_START >= 0 && a - ROM_START <= end - ROM_START - size;
    }
    // Callers validate a pointer/range before following it.
    private int word(int a) { return view[(a - base) >>> 2]; }
    private int half(int a) { return (word(a & ~3) >>> ((a & 2) * 8)) & 65535; }
    private boolean text(int a, String s) {
        if (!contains(a, s.length() + 1)) return false;
        for (int i = 0; i <= s.length(); i++) {
            int c = (word((a + i) & ~3) >>> (((a + i) & 3) * 8)) & 255;
            if (c != (i == s.length() ? 0 : s.charAt(i))) return false;
        }
        return true;
    }
    private boolean rangeAt(int a) {
        if ((a & 1) != 0 || !contains(a, RANGE.length * 2)) return false;
        for (int i = 0; i < RANGE.length; i++) {
            int mask = i == 3 || i == 4 ? 0xf800 : i == 7 ? 0xff00 : 0xffff;
            if ((half(a + 2 * i) & mask) != RANGE[i]) return false;
        }
        return true;
    }
    private boolean armAt(int a, int[] pattern) {
        if ((a & 3) != 0 || !contains(a, pattern.length * 4)) return false;
        for (int i = 0; i < pattern.length; i++) {
            int want = pattern[i];
            int mask = want == 0x0a000000 || want == 0x1a000000 ? 0xff000000 : -1;
            if ((word(a + 4 * i) & mask) != want) return false;
        }
        return true;
    }
    private boolean bounds(int a) {
        // Follow the boot walker's PC-relative LDR to its zero-terminated table.
        // Restrict all table reads to the known mapped boot prefix.
        end = ROM_START + 65536;
        if (!rangeAt(a)) return false;
        int instruction = a + 14;
        int literal = ((instruction + 4) & ~3) + 4 * (half(instruction) & 255);
        if (!contains(literal, 4)) return false;
        int table = word(literal);
        if ((table & 3) != 0 || !contains(table, 24)) return false;
        int high = word(table + 4);
        if (word(table) != (ROM_START & ~4095) || (high & 3) != 0
                || high < end || high > ROM_LIMIT) return false;
        boolean terminated = false;
        for (int i = 1; i < 8; i++) {
            int entry = table + i * 8;
            if (!contains(entry, 8)) return false;
            int low = word(entry), limit = word(entry + 4);
            if (low == 0 && limit == 0) { terminated = true; break; }
            if ((low & 3) != 0 || (low & 0xf0000000) != 0xf0000000
                    || limit - low <= 0 || limit - low > ROM_LIMIT - ROM_START) return false;
        }
        if (!terminated) return false;
        end = high;
        int first = (ROM_START - base) >>> 2;
        return first <= view.length && (end - ROM_START) / 4 <= view.length - first;
    }
    private boolean abi() {
        if (!armAt(dispatch, DISPATCH) || !armAt(invoke, INVOKE)) return false;
        int branch = invoke + 24;
        return branch + 8 + ((word(branch) << 8) >> 6) == dispatch + 48;
    }
    private boolean descriptor(int a) {
        if ((a & 3) != 0 || !contains(a, 36) || !text(word(a), LIBRARY)) return false;
        int hash = word(a + 24), strings = word(a + 28), symbols = word(a + 32);
        if ((hash & 3) != 0 || (symbols & 3) != 0 || !contains(hash, 8)
                || !contains(strings, 1)) return false;
        int buckets = word(hash), count = word(hash + 4);
        return buckets > 0 && buckets <= 8192 && count > 1 && count <= 8192
                && contains(hash, 8 + 4 * (buckets + count)) && contains(symbols, 16 * count);
    }
    private int symbol(int a) {
        // Only ~600 exports: a bounded linear search is simpler than hashing.
        int count = word(word(a + 24) + 4), strings = word(a + 28), symbols = word(a + 32);
        int found = 0;
        for (int i = 1; i < count; i++) {
            int record = symbols + 16 * i, offset = word(record);
            if (offset < 0 || offset >= end - ROM_START || !contains(strings + offset, 1)) return 0;
            if (text(strings + offset, SYMBOL)) {
                int value = word(record + 4);
                // ELF32 absolute global function; retain the Thumb state bit.
                if (found != 0 || word(record + 12) != 0xfff10012
                        || !contains(value & ~1, 4) || (value & 3) == 2) return 0;
                found = value;
            }
        }
        return found;
    }
}
