/* SPDX-License-Identifier: MIT */
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Shared export/recipe behavior with synthetic memory, never firmware data. */
public final class CheckRomServices implements RomSymbols.Reader {
    private final int base;
    private final int[] memory = new int[1024];
    private final RomSymbols symbols = new RomSymbols(this);

    CheckRomServices(int origin) {
        base = origin;
    }

    public boolean containsRom(int address, int bytes) {
        int offset = address - base;
        return bytes >= 0 && offset >= 0 && offset <= memory.length * 4 - bytes;
    }
    public int readWord(int address) {
        require((address & 3) == 0 && containsRom(address, 4), "unchecked read");
        return memory[(address - base) >>> 2];
    }
    public boolean matchesText(int address, String text) {
        if (!containsRom(address, text.length() + 1))
            return false;
        for (int i = 0; i <= text.length(); i++) {
            int offset = address - base + i;
            int value = (memory[offset >>> 2] >>> ((offset & 3) * 8)) & 255;
            if (value != (i == text.length() ? 0 : text.charAt(i)))
                return false;
        }
        return true;
    }
    private void putText(int offset, String text) {
        for (int i = 0; i < text.length(); i++)
            memory[(offset + i) >>> 2] |= text.charAt(i) << (((offset + i) & 3) * 8);
    }
    private void exports() {
        memory[6] = base + 64;
        memory[7] = base + 128;
        memory[8] = base + 256;
        memory[16] = 1;
        memory[17] = 3;
        memory[18] = 1;
        memory[20] = 2;
        putText(129, "func");
        putText(134, "obj");
        memory[68] = 1;
        memory[69] = (base + 512) | 1;
        memory[71] = RomSymbols.FUNCTION;
        memory[72] = 6;
        memory[73] = base + 544;
        memory[75] = RomSymbols.OBJECT;
        require(symbols.validate(base, 4096, true), "valid export table");
        for (boolean hashed : new boolean[] {false, true}) {
            require(symbols.find(base, "func", RomSymbols.FUNCTION, 4096, hashed)
                            == ((base + 512) | 1),
                    "relocated callable and Thumb bit");
            require(symbols.find(base, "obj", RomSymbols.OBJECT, 4096, hashed) == base + 544,
                    "typed object export");
            require(symbols.find(base, "obj", RomSymbols.FUNCTION, 4096, hashed) == 0
                            && symbols.malformed,
                    "object cannot become callable");
        }
        memory[69] = base + 514;
        require(!symbols.validate(base, 4096, true), "misaligned ARM export rejected");
        memory[69] = (base + 512) | 1;
        memory[21] = 1;
        require(symbols.find(base, "missing", RomSymbols.FUNCTION, 4096, true) == 0
                        && symbols.malformed,
                "bounded cyclic hash chain");
        memory[21] = 0;
        memory[72] = 1;
        memory[75] = RomSymbols.FUNCTION;
        require(symbols.find(base, "func", RomSymbols.FUNCTION, 4096, true) == 0
                        && symbols.malformed,
                "ambiguous export");
        memory[72] = 6;
        memory[75] = RomSymbols.OBJECT;
        memory[7] = base + 4092;
        require(symbols.find(base, "func", RomSymbols.FUNCTION, 4096, false) == 0
                        && symbols.malformed,
                "out-of-range name pointer");
    }

    private static FirmwareResolver resolver(int capabilities, int[] memory) throws Exception {
        FirmwareResolver r = new FirmwareResolver(Rm443Profile.create(), capabilities);
        Field view = FirmwareResolver.class.getDeclaredField("view");
        Field origin = FirmwareResolver.class.getDeclaredField("viewBase");
        view.setAccessible(true);
        origin.setAccessible(true);
        view.set(r, memory);
        origin.setInt(r, r.romStart);
        r.romBytes = memory.length * 4;
        return r;
    }

    private static void search() throws Exception {
        int[] memory = new int[1024];
        FirmwareResolver r = resolver(0, memory);
        RomRecipe a = new RomRecipe("a", 2, 0, 0x2201b510, new int[] {0xb510, 0x2201, 0xbd10},
                new int[] {65535, 65535, 65535});
        RomRecipe b = new RomRecipe("b", 2, 0, 0x2202b510, new int[] {0xb510, 0x2202, 0xbd10},
                new int[] {65535, 65535, 65535});
        memory[4] = a.anchor;
        memory[5] = 0xbd10;
        memory[8] = 0xb5100000;
        memory[9] = 0xbd102202;
        RomSearch search = new RomSearch(new RomRecipe[] {a, b});
        require(search.resolve(r) && search.address(0) == r.romStart + 16
                        && search.address(1) == r.romStart + 34 && r.scanPasses == 1,
                "one pass, shared anchor bucket and halfword alignment");
        r.reused = true;
        require(search.resolve(r) && r.scanPasses == 1, "revalidate cached recipe locations");
        memory[5] = 0;
        require(!search.resolve(r) && search.address(0) == 0 && r.scanPasses == 2,
                "changed cached recipe rescans and becomes unavailable");
        memory[5] = 0xbd10;
        memory[24] = a.anchor;
        memory[25] = 0xbd10;
        require(!search.resolve(r) && search.matches(0) == 2 && search.address(0) == 0,
                "ambiguous global recipe rejected");
        require(r.findUnique(b, r.romStart + 33, 16) == r.romStart + 34,
                "local search aligns candidate");
        require(r.findUnique(a, r.romStart + 4090, 20) == 0 && r.error == 0,
                "local search cannot cross mapped bounds");
        memory[100] = 0xe59f2010;
        memory[106] = 0x12345678;
        require(r.armLiteral(r.romStart + 400, 2) == 0x12345678,
                "ARM literal instruction decoding");
        require(r.armLiteral(r.romStart + 400, 3) == 0 && r.error != 0,
                "wrong destination register rejected");
        r.error = 0;
        memory[150] = 0xf812f000; // BL from +600 to +640.
        memory[160] = 0x46c04778; // Thumb BX PC / NOP.
        memory[161] = 0xe51ff004; // ARM LDR PC literal.
        memory[162] = (r.romStart + 704) | 1;
        require(r.thumbCall(r.romStart + 600) == ((r.romStart + 704) | 1),
                "bounded Thumb-to-ARM veneer preserves target state");
        memory[162] = r.romStart + 706;
        require(r.thumbCall(r.romStart + 600) == 0 && r.error != 0,
                "interworking veneer rejects misaligned ARM entry");
        r.error = 0;
        memory[180] = 0xe7fde001;
        require(r.thumbBranch(r.romStart + 720) == r.romStart + 726
                        && r.thumbBranch(r.romStart + 722) == r.romStart + 720,
                "Thumb branch displacement sign extension");
        memory[162] = r.romStart + 4096;
        require(r.thumbCall(r.romStart + 600) == 0 && r.error != 0,
                "interworking veneer cannot escape validated ROM");
    }

    private static void serviceContext() throws Exception {
        FirmwareMemory mapping = new FirmwareMemory("synthetic", 0x7ffffc00, 0x7ffffe00, 64,
                0x80000000, 0x80000200, 0x10000000, 0x10001000, false) {
            boolean discover(FirmwareResolver r) {
                require(!r.rom(r.romStart + 64, 4), "only discovery prefix initially readable");
                if (r.word(r.romStart) != 0x12345678)
                    return false;
                return r.setRomRegions(256, 256) && r.setRamLimit(0x10000800);
            }
        };
        FirmwareResolver r = new FirmwareResolver(mapping, FirmwareResolver.CAP_AUDIO);
        int[] memory = new int[384];
        memory[0] = 0x12345678;
        require(r.resolveServices(memory, 0x7ffffc00) && r.serviceOnly() && r.profile == null,
                "explicit service context needs no fabricated bootstrap profile");
        require(r.rom(0x7ffffc00, 256) && r.rom(0x80000000, 256) && !r.rom(0x7ffffd00, 1)
                        && !r.rom(0x80000100, 1),
                "independent banks across signed address boundary preserve unmapped gap");
        require(r.ramBytes(0x10000001, 3) && !r.ram(0x10000001, 3) && !r.ramBytes(0x100007ff, 2),
                "byte spans and aligned records share discovered RAM bounds");
        require(r.api[FirmwareApi.AUDIO_ABI] == 0 && r.api[FirmwareApi.KNI_PARAM] == 0,
                "unknown service ABI cannot imply audio or native bootstrap support");
        require(r.resolveServices(memory, 0x7ffffc00) && r.reused,
                "service mapping revalidated on reuse");
        require(!r.resolve(memory, 0x7ffffc00), "service context cannot enter core launcher path");
        r.api[FirmwareApi.AUDIO_ABI] = FirmwareApi.AUDIO_ABI_DEVICE_IHF;
        r.api[FirmwareApi.AUDIO_REGISTER_OBJECT] = 0x12345;
        memory[0] = 0;
        require(!r.resolveServices(memory, 0x7ffffc00), "changed mapping metadata rejected");
        for (int value : r.api)
            require(value == 0, "failed mapping clears every capability");
        FirmwareMemory unpublished = new FirmwareMemory(
                "synthetic", 0x1000, 0x2000, 64, 0, 0, 0x10000000, 0x10001000, false) {
            boolean discover(FirmwareResolver resolver) {
                return true;
            }
        };
        require(!new FirmwareResolver(unpublished, 0).resolveServices(new int[1024], 0x1000),
                "service discovery must explicitly publish validated banks");
    }

    private static void optional() throws Exception {
        int[] memory = new int[1024];
        FirmwareResolver r = resolver(FirmwareResolver.CAP_AUDIO, memory);
        for (int i = 0; i < r.api.length; i++)
            r.api[i] = i + 1;
        r.systemDescriptor = r.romStart + 4092;
        r.detail = 42;
        r.phase = 9;
        Method bind = FirmwareResolver.class.getDeclaredMethod("resolveOptional");
        bind.setAccessible(true);
        bind.invoke(r);
        require(r.error == 0 && r.detail == 42 && r.phase == 9,
                "malformed optional metadata cannot poison core diagnostics");
        for (int i = 0; i < FirmwareApi.REQUIRED_COUNT; i++)
            require(r.api[i] == i + 1, "optional failure preserves core");
        for (int i = FirmwareApi.REQUIRED_COUNT; i < r.api.length; i++)
            require(r.api[i] == 0, "failed and unrequested groups clear completely");
        r.api[FirmwareApi.AUDIO_ABI] = FirmwareApi.AUDIO_ABI_DEVICE_IHF;
        require(!r.resolve(memory, r.romStart + 1), "misaligned core view rejected");
        for (int value : r.api)
            require(value == 0, "failed core discovery clears old capability and core bindings");
        r.api[FirmwareApi.AUDIO_ABI] = FirmwareApi.AUDIO_ABI_DEVICE_IHF;
        require(!r.resolveServices(memory, r.romStart), "launcher cannot use service-only entry");
        for (int value : r.api)
            require(value == 0, "wrong discovery entry cannot retain old bindings");
    }

    public static void main(String[] args) throws Exception {
        new CheckRomServices(0x1000).exports();
        new CheckRomServices(0xf0100400).exports();
        search();
        optional();
        serviceContext();
        System.out.println(
                "PASS: shared typed exports, bounded batched recipes and optional isolation");
    }
    private static void require(boolean value, String reason) {
        if (!value)
            throw new AssertionError(reason);
    }
}
