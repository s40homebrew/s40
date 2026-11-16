/* SPDX-License-Identifier: MIT */
/** Bounded, allocation-free RM-813 discovery; no firmware-version allowlist. */
final class RapuTimeResolver {
    // Family virtual-map bounds, never individual function or global addresses.
    static final int FIRST = 0xF0100400, SECOND = 0xF3000400;
    static final int FIRST_LIMIT = 0xF2000000, SECOND_LIMIT = 0xF4000000;
    static final int RAM_FIRST = 0xD8000000, RAM_LIMIT = 0xDD000000;
    private static final String LIBRARY = "libsystem.so", TIMER = "os_current_time_read_in_ms";
    private static final int[] DISPATCH = {
        0xe5de2001, 0xe5de3002, 0xe1a0a00e, 0xe28ae003, 0xe1831402,
        0xe7981101, 0xe1d141be, 0xe5910014, 0xe0464104, 0xe5942000,
        0xe3520000, 0x0a000000, 0xe5912004, 0xe2443004, 0xe3520000,
        0x0a000000, 0xe5959438, 0xe3100008, 0x02833004, 0xe3590000,
        0x0a000000, 0xe5893020, 0xe5894024, 0xe5859468, 0xe5851448,
        0xe59fc000, 0xe285ae42, 0xe88a49c0, 0xe12fff3c, 0xe89a49c0
    };
    private static final int[] INVOKE = {
        0xb570, 0x4900, 0x4d00, 0x6808, 0x682c, 0x2800, 0xd101,
        0x2010, 0x6008, 0x1e40, 0x6008, 0x4900, 0x0080, 0x500a,
        0x4790, 0x6828, 0x4284, 0xd007
    };
    private static final int[] CACHE = {
        0xe92d4070, 0xe59fc000, 0xe1a04000, 0xe1a05001, 0xe12fff3c,
        0xe59fc000, 0xe1a00004, 0xe1a01005, 0xe12fff3c, 0xe3a0c000,
        0xee07cf95, 0xe8bd8070
    };
    private static final int[] CLEAN = {
        0xe92d4001, 0xe1a02000, 0xe0813000, 0xe2433001, 0xe3a0e000,
        0xee07ef9a, 0xec423f0e, 0xee07ef9a, 0xe10f2000, 0xf10c00c0,
        0xe52d2004, 0xeb000000, 0xe49d2004, 0xe12ff002, 0xe8bd8001
    };
    private static final int[] INVALIDATE = {
        0xe92d4001, 0xe3a0e000, 0xe1a02000, 0xe0813000, 0xe2433001,
        0xec423f05, 0xee07ef95, 0xec423f05, 0xee07ef95, 0xee072ff5,
        0xee07ef9a, 0xee07ef95, 0xeb000000, 0xe8bd8001
    };
    private int[] view;
    private int base;
    int end, secondEnd, ramEnd, table, cache, time, dispatch, invoke, library;

    boolean ram(int p, int n) {
        int high = ramEnd == 0 ? RAM_LIMIT : ramEnd;
        return n >= 0 && p - RAM_FIRST >= 0 && p - RAM_FIRST <= high - RAM_FIRST - n;
    }
    private int word(int p) { return view[(p - base) >>> 2]; }
    private int half(int p) { return (word(p & ~3) >>> ((p & 2) * 8)) & 65535; }
    private boolean represented(int p, int n) {
        int index = (p - base) >>> 2;
        return n >= 0 && index <= view.length && (n + 3) / 4 <= view.length - index;
    }
    private boolean contains(int p, int n) {
        return n >= 0 && ((p - FIRST >= 0 && p - FIRST <= end - FIRST - n)
                || (p - SECOND >= 0 && p - SECOND <= secondEnd - SECOND - n));
    }
    private boolean text(int p, String wanted) {
        if (!contains(p, wanted.length() + 1)) return false;
        for (int i = 0; i <= wanted.length(); i++) {
            int a = p + i, c = (word(a & ~3) >>> ((a & 3) * 8)) & 255;
            if (c != (i == wanted.length() ? 0 : wanted.charAt(i))) return false;
        }
        return true;
    }
    private boolean pattern(int p, int[] values) {
        if ((p & 3) != 0 || !contains(p, values.length * 4)) return false;
        for (int i = 0; i < values.length; i++) {
            int v = values[i], mask = -1;
            if (v == 0x0a000000 || v == 0xeb000000) mask = 0xff000000;
            if (v == 0xe59fc000) mask = 0xfffff000;
            if ((word(p + i * 4) & mask) != v) return false;
        }
        return true;
    }
    private int armLiteral(int p) {
        if (!contains(p, 4) || (word(p) & 0xfffff000) != 0xe59fc000) return 0;
        int literal = p + 8 + (word(p) & 4095);
        return contains(literal, 4) ? word(literal) : 0;
    }
    private boolean cacheAt(int p) {
        if (!pattern(p, CACHE)) return false;
        int clean = armLiteral(p + 4), invalidate = armLiteral(p + 20);
        if (!pattern(clean, CLEAN) || !pattern(invalidate, INVALIDATE)) return false;
        int b1 = clean + 44, b2 = invalidate + 48;
        int t1 = b1 + 8 + ((word(b1) << 8) >> 6);
        int t2 = b2 + 8 + ((word(b2) << 8) >> 6);
        return contains(t1, 4) && contains(t2, 4) && t1 != t2;
    }
    private boolean dispatchAt(int p) {
        if (!pattern(p, DISPATCH)) return false;
        int target = armLiteral(p + 100);
        if ((target & 1) == 0 || !contains(target & ~1, 56)) return false;
        int at = target & ~1;
        for (int i = 0; i < INVOKE.length; i++) {
            int v = INVOKE[i], mask = (v == 0x4900 || v == 0x4d00) ? 0xff00 : 65535;
            if ((half(at + i * 2) & mask) != v) return false;
        }
        // Successful no-argument return must bypass the invariant-failure path.
        if (half(at + 52) != 0xbd70) return false;
        invoke = target;
        return true;
    }
    private boolean descriptor(int p) {
        if ((p & 3) != 0 || !contains(p, 36) || !text(word(p), LIBRARY)) return false;
        int hash = word(p + 24), strings = word(p + 28), symbols = word(p + 32);
        if ((hash & 3) != 0 || (symbols & 3) != 0 || !contains(hash, 8)
                || !contains(strings, 1)) return false;
        int buckets = word(hash), count = word(hash + 4);
        return buckets > 0 && buckets <= 8192 && count > 1 && count <= 8192
                && contains(hash, 8 + 4 * (buckets + count)) && contains(symbols, count * 16);
    }
    private int timer(int p) {
        int strings = word(p + 28), symbols = word(p + 32), count = word(word(p + 24) + 4);
        int found = 0;
        for (int i = 1; i < count; i++) {
            int record = symbols + i * 16, offset = word(record);
            if (offset < 0 || offset >= SECOND_LIMIT - FIRST || !contains(strings + offset, 1)) return 0;
            if (text(strings + offset, TIMER)) {
                int v = word(record + 4);
                if (found != 0 || word(record + 12) != 0xfff10012
                        || !contains(v & ~1, 4) || (v & 3) == 2) return 0;
                found = v;
            }
        }
        return found;
    }
    private boolean bounds() {
        int hits = 0;
        // Boot image metadata: a unique zero-terminated pair table. Its starts
        // are architectural mappings; both ends are discovered, not versioned.
        if (!represented(FIRST, 0x40000)) return false;
        for (int p = FIRST + 16; p < FIRST + 0x40000 - 24; p += 4) {
            if (word(p) != (FIRST & ~4095) || word(p + 8) != (SECOND & ~4095)
                    || word(p + 16) != 0 || word(p + 20) != 0) continue;
            int ramHigh = word(p - 12);
            if (word(p - 16) != RAM_FIRST || word(p - 8) != 0 || word(p - 4) != 0
                    || (ramHigh & 4095) != 0 || ramHigh <= RAM_FIRST || ramHigh > RAM_LIMIT) continue;
            int a = word(p + 4), b = word(p + 12);
            if ((a & 3) != 0 || (b & 3) != 0 || a < FIRST + 0x40000 || a > FIRST_LIMIT
                    || b < SECOND + 0x10000 || b > SECOND_LIMIT) continue;
            if (!represented(FIRST, a - FIRST) || !represented(SECOND, b - SECOND)) continue;
            end = a; secondEnd = b; ramEnd = ramHigh; table = p; hits++;
        }
        return hits == 1;
    }
    int resolve(int[] memory, int origin) {
        view = memory; base = origin;
        cache = time = dispatch = invoke = library = table = end = secondEnd = ramEnd = 0;
        try {
            if (!bounds()) { ramEnd = 0; return 102; }
            int caches = 0, dispatches = 0, libraries = 0, references = 0;
            for (int bank = 0; bank < 2; bank++) {
                int first = bank == 0 ? FIRST : SECOND, limit = bank == 0 ? end : secondEnd;
                for (int p = first; p <= limit - 4; p += 4) {
                    int v = word(p);
                    if (v == table) references++;
                    if (v == CACHE[0] && cacheAt(p)) { cache = p; caches++; }
                    if (v == DISPATCH[0] && dispatchAt(p)) { dispatch = p; dispatches++; }
                    if (contains(v, 13) && ((word(v & ~3) >>> ((v & 3) * 8)) & 255) == 'l'
                            && descriptor(p)) { library = p; libraries++; }
                }
            }
            if (references == 0) return 102;
            if (caches != 1) return 103;
            if (dispatches != 1) return 104;
            if (libraries != 1) return 105;
            time = timer(library);
            return time == 0 ? 106 : 0;
        } finally { view = null; }
    }
}
