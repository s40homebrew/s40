/* SPDX-License-Identifier: MIT */
/** Bounded readers for immutable ComMgr class and method records. */
final class FirmwareClasses {
    private FirmwareClasses() {}
    private static final int CLASS_BYTES = 20, METHOD_BYTES = 8, MAX_METHODS = 128;

    static void prepare() {}

    static boolean classAt(FirmwareResolver r, int address, int objectBytes, String name) {
        return (address & 3) == 0 && r.rom(address, CLASS_BYTES) && r.word(address) == address
                && (r.word(address + 12) >>> 16) == objectBytes && (r.word(address + 8) & 3) == 0
                && r.rom(r.word(address + 8), METHOD_BYTES) && r.textAt(r.word(address + 16), name);
    }

    /**
     * Resolve a method declared by this class; no implicit parent fallback.
     * Reject duplicate selectors, invalid code pointers and unterminated tables.
     */
    static int method(FirmwareResolver r, int klass, int selector) {
        if ((klass & 3) != 0 || !r.rom(klass, CLASS_BYTES) || r.word(klass) != klass)
            return 0;
        int table = r.word(klass + 8), result = 0;
        for (int i = 0; i < MAX_METHODS; i++) {
            int record = table + METHOD_BYTES * i;
            if ((record & 3) != 0 || !r.rom(record, METHOD_BYTES))
                return 0;
            int id = r.word(record), function = r.word(record + 4);
            if (id == 0)
                return function == 0 ? result : 0;
            if (!r.rom(function & ~1, 4) || ((function & 1) == 0 && (function & 3) != 0))
                return 0;
            if (id == selector) {
                if (result != 0)
                    return 0;
                result = function;
            }
        }
        return 0;
    }
}
