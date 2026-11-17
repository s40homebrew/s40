/* SPDX-License-Identifier: MIT */
/**
 * Bounded Nokia library-descriptor and ELF32 export decoding.
 * The owning resolver supplies its validated memory view. This helper performs
 * table lookups only; discovery and device-specific relationships stay there.
 */
final class RomSymbols {
    static final int DESCRIPTOR_BYTES = 36, HASH_OFFSET = 24, STRINGS_OFFSET = 28,
                     SYMBOLS_OFFSET = 32;
    static final int FUNCTION = 0xFFF10012, OBJECT = 0xFFF10011;
    private static final int SYMBOL_BYTES = 16, MAX_ENTRIES = 8192;

    interface Reader {
        boolean containsRom(int address, int bytes);
        int readWord(int address);
        boolean matchesText(int address, String text);
    }

    private final Reader reader;
    private final int descriptorBytes, hashOffset, stringsOffset, symbolsOffset;
    private int hash, strings, symbols, buckets, count;
    boolean malformed;

    RomSymbols(Reader source) {
        this(source, DESCRIPTOR_BYTES, HASH_OFFSET, STRINGS_OFFSET, SYMBOLS_OFFSET);
    }

    RomSymbols(Reader source, int bytes, int hashAt, int stringsAt, int symbolsAt) {
        reader = source;
        descriptorBytes = bytes;
        hashOffset = hashAt;
        stringsOffset = stringsAt;
        symbolsOffset = symbolsAt;
    }

    private boolean table(int descriptor) {
        malformed = false;
        if ((descriptor & 3) != 0 || descriptorBytes < 4
                || !reader.containsRom(descriptor, descriptorBytes))
            return invalid();
        hash = reader.readWord(descriptor + hashOffset);
        strings = reader.readWord(descriptor + stringsOffset);
        symbols = reader.readWord(descriptor + symbolsOffset);
        if ((hash & 3) != 0 || (symbols & 3) != 0 || !reader.containsRom(hash, 8)
                || !reader.containsRom(strings, 1))
            return invalid();
        buckets = reader.readWord(hash);
        count = reader.readWord(hash + 4);
        if (buckets < 1 || buckets > MAX_ENTRIES || count < 2 || count > MAX_ENTRIES
                || !reader.containsRom(hash, 8 + 4 * (buckets + count))
                || !reader.containsRom(symbols, SYMBOL_BYTES * count))
            return invalid();
        return true;
    }

    boolean validate(int descriptor, int maxNameOffset, boolean allEntries) {
        if (!table(descriptor))
            return false;
        if (!allEntries)
            return true;
        for (int i = 0; i < buckets + count; i++) {
            int index = reader.readWord(hash + 8 + 4 * i);
            if (index < 0 || index >= count)
                return invalid();
        }
        for (int i = 1; i < count; i++) {
            int record = symbols + SYMBOL_BYTES * i;
            int offset = reader.readWord(record), type = reader.readWord(record + 12);
            if (!nameOffset(offset, maxNameOffset) || (type != FUNCTION && type != OBJECT))
                return invalid();
            if (type == FUNCTION && !callable(reader.readWord(record + 4)))
                return invalid();
        }
        return true;
    }

    /** A missing export returns zero; malformed tables also set malformed. */
    int find(int descriptor, String name, int type, int maxNameOffset, boolean hashed) {
        if (!table(descriptor) || (type != FUNCTION && type != OBJECT))
            return 0;
        int index = 1;
        if (hashed) {
            int value = 0;
            for (int i = 0; i < name.length(); i++) {
                value = (value << 4) + name.charAt(i);
                int high = value & 0xF0000000;
                value ^= high >>> 24;
                value &= ~high;
            }
            index = reader.readWord(hash + 8 + 4 * (value % buckets));
        }
        int result = 0, steps = 0;
        while (index != 0 && (hashed || index < count)) {
            if (index < 0 || index >= count || ++steps >= count) {
                invalid();
                return 0;
            }
            int record = symbols + SYMBOL_BYTES * index;
            int offset = reader.readWord(record);
            if (!nameOffset(offset, maxNameOffset)) {
                invalid();
                return 0;
            }
            if (reader.matchesText(strings + offset, name)) {
                int value = reader.readWord(record + 4);
                if (result != 0 || reader.readWord(record + 12) != type
                        || (type == FUNCTION && !callable(value))) {
                    invalid();
                    return 0;
                }
                result = value;
            }
            index = hashed ? reader.readWord(hash + 8 + 4 * (buckets + index)) : index + 1;
        }
        return result;
    }

    private boolean nameOffset(int offset, int limit) {
        return offset >= 0 && offset < limit && reader.containsRom(strings + offset, 1);
    }

    private boolean callable(int value) {
        return reader.containsRom(value & ~1, 2) && ((value & 1) != 0 || (value & 3) == 0);
    }

    private boolean invalid() {
        malformed = true;
        return false;
    }
}
