/* SPDX-License-Identifier: MIT */
/** RAPU memory metadata for service discovery without a shared KVM launcher. */
final class Rm813Memory extends FirmwareMemory {
    private static final int PRIMARY = 0xf0100400, SECONDARY = 0xf3000400;

    // Adjacent RAM and ROM tables consumed by the firmware range checker.
    // Ends are image metadata; family starts and zero terminators are structural.
    private static final RomRecipe RANGES = Rm813MemoryPatterns.RAPU_MEMORY_RANGES();

    Rm813Memory() {
        super("RM-813", PRIMARY, 0xf1000000, 128 * 1024, SECONDARY, 0xf4000000, 0xd8000000,
                0xe0000000, true);
    }

    boolean discover(FirmwareResolver r) {
        int table = r.findUnique(RANGES, romStart, bootBytes);
        if (table == 0)
            return false;
        int ramEnd = r.word(table + 4), firstEnd = r.word(table + 20);
        int secondEnd = r.word(table + 28);
        if ((ramEnd & 4095) != 0 || (firstEnd & 3) != 0 || (secondEnd & 3) != 0)
            return false;
        return r.setRamLimit(ramEnd) && r.setRomRegions(firstEnd - PRIMARY, secondEnd - SECONDARY);
    }
}
