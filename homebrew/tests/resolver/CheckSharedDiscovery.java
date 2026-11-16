/* SPDX-License-Identifier: MIT */
/** Host regression checks for the contracts shared by Doom and RAM Booster. */
public final class CheckSharedDiscovery {
    private static void require(boolean value, String reason) {
        if (!value)
            throw new AssertionError(reason);
    }

    private static int end(int[] view, int base, int table) {
        return RomRanges.readEnd(
                view, base, base, 128, 4096, table, 4095, 8, 4, 24, 8, 0xf0000000, 0xf0000000);
    }

    public static void main(String[] args) {
        for (int base : new int[] {0xf0100400, 0xf1100400}) {
            int[] view = new int[32];
            int table = base + 16;
            view[4] = base & ~4095;
            view[5] = base + 2048;
            view[6] = 0xf8000000;
            view[7] = 0xf8001000;
            require(end(view, base, table) == base + 2048, "relocated first bank");
            view[5] = base + 4097;
            require(end(view, base, table) == 0, "unaligned/oversized end");
            view[5] = base + 64;
            require(end(view, base, table) == 0, "short first bank");
            view[5] = base + 2048;
            view[6] = 0xd8000000;
            require(end(view, base, table) == 0, "wrong additional region");
            view[6] = 0xf8000000;
            require(end(view, base, base + 120) == 0, "table outside readable prefix");
            for (int i = 8; i < 20; i += 2) {
                view[i] = 0xf8000000;
                view[i + 1] = 0xf8001000;
            }
            require(end(view, base, table) == 0, "missing bounded terminator");
        }
        RomRecipe recipe = new RomRecipe("test", 2, 0, 0, new int[] {0xb510, 0x4800, 0xbd10},
                new int[] {0xffff, 0xff00, 0xffff});
        int[] code = {0x48ffb510, 0x0000bd10};
        require(recipe.matches(code, 0xf0100400, 0xf0100400, 0xf0100400, 0xf0100408),
                "masked literal immediate");
        require(!recipe.matches(code, 0xf0100400, 0xf0100400, 0xf0100400, 0xf0100404),
                "truncated recipe");
        require(!recipe.matches(code, 0xf0100400, 0xf01003fe, 0xf0100400, 0xf0100408),
                "recipe before readable window");
        code[1] ^= 1;
        require(!recipe.matches(code, 0xf0100400, 0xf0100400, 0xf0100400, 0xf0100408),
                "changed return instruction");
        System.out.println("PASS: shared ROM recipes, relocation and bounded range metadata");
    }
}
