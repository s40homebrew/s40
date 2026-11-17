/* SPDX-License-Identifier: MIT */
import java.lang.reflect.Field;

/** Synthetic range metadata and class graphs; no firmware files are required. */
public final class CheckAudioMemory {
    private static final Rm813Memory MAP = new Rm813Memory();
    private static final int FIRST_BYTES = 128 * 1024, SECOND_BYTES = 4096;
    private static final int TABLE = 256, SOURCE = 1024, ACTIVE = SOURCE + 32;
    private static final int[] MEMORY =
            new int[(MAP.secondaryStart - MAP.romStart + SECOND_BYTES) / 4];

    private static FirmwareResolver reader() throws Exception {
        FirmwareResolver r = new FirmwareResolver(MAP, 0);
        Field view = FirmwareResolver.class.getDeclaredField("view");
        Field origin = FirmwareResolver.class.getDeclaredField("viewBase");
        view.setAccessible(true);
        origin.setAccessible(true);
        view.set(r, MEMORY);
        origin.setInt(r, MAP.romStart);
        r.romBytes = MAP.bootBytes;
        return r;
    }

    private static void table(int offset) {
        int[] values = {MAP.ramStart, MAP.ramStart + 16384, 0, 0, MAP.romStart - 1024,
                MAP.romStart + FIRST_BYTES, MAP.secondaryStart - 1024,
                MAP.secondaryStart + SECOND_BYTES, 0, 0};
        System.arraycopy(values, 0, MEMORY, offset / 4, values.length);
    }

    private static void ranges() throws Exception {
        table(TABLE);
        FirmwareResolver r = reader();
        require(MAP.discover(r) && r.romBytes == FIRST_BYTES && r.secondaryBytes == SECOND_BYTES
                        && r.ramBytes() == 16384,
                "variable firmware ends determine both ROM banks and RAM bound");
        MEMORY[(TABLE + 20) / 4] = MAP.romLimit + 4;
        require(!MAP.discover(reader()), "metadata cannot enlarge conservative readable window");
        table(TABLE);
        table(TABLE + 64);
        require(!MAP.discover(reader()), "ambiguous range tables rejected");
        MEMORY[(TABLE + 64) / 4] = 0;
        MEMORY[(TABLE + 32) / 4] = 1;
        require(!MAP.discover(reader()), "unterminated ROM metadata rejected");
        table(TABLE);
    }

    private static void active(int offset, int parent, int bytes) {
        MEMORY[offset / 4] = MAP.romStart + offset;
        MEMORY[offset / 4 + 1] = parent;
        MEMORY[offset / 4 + 2] = MAP.romStart + 2048;
        MEMORY[offset / 4 + 3] = bytes << 16;
        MEMORY[offset / 4 + 4] = MAP.romStart + 2080;
    }

    private static void classes() throws Exception {
        String name = "Active_Source";
        for (int i = 0; i < name.length(); i++)
            MEMORY[(2080 + i) / 4] |= name.charAt(i) << ((i & 3) * 8);
        // The real class declares normal and alternate initializers under C004.
        MEMORY[2048 / 4] = MEMORY[2056 / 4] = 0xc004;
        int source = MAP.romStart + SOURCE;
        active(ACTIVE, source, 132);
        FirmwareResolver r = reader();
        require(AudioBindings.activeClass(r, source, 132) == MAP.romStart + ACTIVE,
                "self, parent, size and name identify active class");
        active(ACTIVE + 32, source, 132);
        require(AudioBindings.activeClass(r, source, 132) == 0, "duplicate class rejected");
        active(ACTIVE + 32, source + 4, 132);
        require(AudioBindings.activeClass(r, source, 132) == MAP.romStart + ACTIVE,
                "unrelated class does not create ambiguity");
        require(AudioBindings.activeClass(r, source, 120) == 0,
                "another native object layout is unavailable");
        require(AudioBindings.activeClass(r, MAP.romStart + FIRST_BYTES - 16, 132) == 0,
                "class search cannot cross readable bank");
    }

    public static void main(String[] args) throws Exception {
        ranges();
        classes();
        System.out.println("PASS: audio memory bounds and class relationship ambiguity");
    }

    private static void require(boolean value, String reason) {
        if (!value)
            throw new AssertionError(reason);
    }
}
