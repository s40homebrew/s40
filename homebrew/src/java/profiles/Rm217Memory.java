/* SPDX-License-Identifier: MIT */
/**
 * Descriptor-free memory-service recipes shared by the runtime and RAM Booster.
 * These are instruction contracts, not addresses or firmware-version seeds.
 * FILE_READ independently binds its alloc/free calls at offsets 168 and 248.
 */
final class Rm217Memory {
    private Rm217Memory() {}

    // Force recipe allocation before entering a forged Java memory view.
    static void prepare() {
        RomRanges.prepare();
    }

    static final RomRecipe RANGE = Rm217MemoryPatterns.RANGE();

    static final RomRecipe ALLOC = Rm217MemoryPatterns.OS_BLOCK_ALLOC_NO_WAIT();

    static final RomRecipe FREE = Rm217MemoryPatterns.OS_BLOCK_DEALLOC();

    static final RomRecipe FILE_READ = Rm217MemoryPatterns.FILE_READ_NATIVE();
}
