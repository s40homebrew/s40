/* SPDX-License-Identifier: MIT */
/**
 * Service discovery map for the two-bank X-GOLD 301 firmware family.
 * These conservative mapped prefixes are memory policy, not build lengths or
 * symbol addresses. The boot entry and all service relationships are checked
 * before publishing a capability. This does not provide a native launcher.
 */
final class Rm839Memory extends FirmwareMemory {
    private static final int FIRST = 0x62e00000, SECOND = 0x80000000;
    private static final int FIRST_BYTES = 0x00f00000, SECOND_BYTES = 0x00c00000;
    private static final int IMAGE_ENTRY_OFFSET = 0x400;

    Rm839Memory() {
        super("RM-839", FIRST, FIRST + FIRST_BYTES, 4096, SECOND, SECOND + SECOND_BYTES, 0x60000000,
                0x62000000, false);
    }

    boolean discover(FirmwareResolver r) {
        int entry = FIRST + IMAGE_ENTRY_OFFSET;
        if ((r.word(entry) & 0xff000000) != 0xea000000)
            return false;
        int dispatch = r.armBranch(entry);
        if (!r.rom(dispatch, 4) || (r.word(dispatch) & 0xff000000) != 0xea000000)
            return false;
        int veneer = r.armBranch(dispatch);
        if (!r.rom(veneer, 8) || r.word(veneer) != 0xe51ff004)
            return false;
        int start = r.word(veneer + 4);
        if ((start & 1) == 0 || !r.rom(start & ~1, 8) || r.half(start & ~1) != 0xb510)
            return false;
        return r.setRomRegions(FIRST_BYTES, SECOND_BYTES);
    }
}
