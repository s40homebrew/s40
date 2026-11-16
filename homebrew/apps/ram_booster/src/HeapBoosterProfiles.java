/* SPDX-License-Identifier: MIT */
/**
 * Mapping policy only: patch sites, sizes and helpers are discovered from code.
 * Firmware suffixes are deliberately ignored. Fixture versions belong in tests
 * and porting notes, not in a runtime allowlist. See apps/ram_booster/docs/porting.md.
 */
final class HeapBoosterProfiles {
    static HeapBoosterProfile select(String platform) {
        if (platform == null)
            return null;
        String text = platform.trim().toLowerCase();
        if (model(text, "nokia301"))
            return new HeapBoosterProfile("RM-839", 0x62E00000, 0x63D00000, 0x60000000, 0x64000000,
                    HeapBoosterProfile.ARMV6_IDENTITY, 0x60000000, 0x64000000);
        if (model(text, "nokia302") || model(text, "nokia3020") || model(text, "nokiaasha302"))
            // The stable kernel prefix supplies the firmware's own range
            // table. Discovery clamps these ceilings before scanning code.
            // RM-813 product-info field 5 supplies Nokia302 or Nokia3020.
            return new HeapBoosterProfile("RM-813", 0xF0100400, 0xF0D20000, 0xD8000000, 0xDCC75000,
                    HeapBoosterProfile.ARMV6_RAPU, 0, 0, 0x40000);
        if (model(text, "nokia306") || model(text, "nokia3060") || model(text, "nokiaasha306"))
            // X-GOLD keeps OS allocations in identity DDR, but reserves Java
            // objects in two VMM arenas. Do not admit the unmapped gap.
            return new HeapBoosterProfile("RM-767", 0x40120000, 0x41D24000, 0x60000000, 0x62000000,
                    HeapBoosterProfile.ARMV6_XGOLD, 0x60000000, 0x62000000, 0, 0x90000000,
                    0xC0000000);
        if (model(text, "nokiac2-01"))
            return new HeapBoosterProfile("RM-721 / RM-722", 0xF0100400, 0xF0B30000, 0xD8000000,
                    0xDC000000, HeapBoosterProfile.ARM926_RAP, 0x10000000, 0x14000000);
        if (model(text, "nokia6300"))
            // Original RAP 6300 only. The firmware's range walker narrows the
            // ROM ceiling before scanning; no firmware suffix selects code.
            return new HeapBoosterProfile("RM-217", 0xF0100400, 0xF2000000, 0xD8000000, 0xD9000000,
                    HeapBoosterProfile.ARM926_RAP, 0, 0, 65536);
        if (model(text, "nokia6303c") || model(text, "nokia6303classic"))
            return new HeapBoosterProfile("RM-443", 0xF0100400, 0xF14C0000, 0xD8000000, 0xDA000000,
                    HeapBoosterProfile.ARM926_RAP, 0x10000000, 0x12000000);
        if (model(text, "nokia6700c") || model(text, "nokia6700classic"))
            // BSE supplies the live physical RAM regions. The ordinary VM RAM
            // window includes static data and the final kernel-global page.
            return new HeapBoosterProfile("RM-470", 0xF0100400, 0xF14D8000, 0xD8000000, 0xDC954000,
                    HeapBoosterProfile.ARMV6_RAPU, 0, 0);
        if (model(text, "nokiac3-00"))
            return new HeapBoosterProfile("RM-614", 0x80130400, 0x80EC0000, 0x80F00000, 0x84000000,
                    HeapBoosterProfile.ARM926_BCM, 0x80000000, 0x84000000);
        return null;
    }
    private static boolean model(String text, String name) {
        return PlatformIdentity.matches(text, name);
    }
}
