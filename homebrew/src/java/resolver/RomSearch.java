/* SPDX-License-Identifier: MIT */
/**
 * One bounded, anchor-indexed pass for a group of optional discovery recipes.
 * Construct before opening the memory view. Cached locations are revalidated
 * only after the owning resolver has revalidated its core firmware identity.
 */
final class RomSearch {
    private static final int BUCKETS = 256;
    private final RomRecipe[] recipes;
    final int[] heads = new int[BUCKETS];
    private final int[] next, found, counts;
    private boolean cached;
    private int first, bytes, second, secondBytes;

    RomSearch(RomRecipe[] source) {
        recipes = new RomRecipe[source.length];
        next = new int[source.length];
        found = new int[source.length];
        counts = new int[source.length];
        for (int i = 0; i < source.length; i++) {
            RomRecipe recipe = source[i];
            if (recipe == null || (recipe.alignment != 2 && recipe.alignment != 4)
                    || recipe.anchorOffset < 0 || (recipe.anchorOffset & 1) != 0
                    || recipe.anchorOffset > recipe.size - 4)
                throw new IllegalArgumentException("Invalid ROM search recipe");
            recipes[i] = recipe;
            int bucket = recipe.anchor & (BUCKETS - 1);
            next[i] = heads[bucket];
            heads[bucket] = i + 1;
        }
    }

    int address(int index) {
        return counts[index] == 1 ? found[index] : 0;
    }

    int matches(int index) {
        return counts[index];
    }

    boolean resolve(FirmwareResolver resolver) {
        if (cached && resolver.reused && first == resolver.romStart && bytes == resolver.romBytes
                && second == resolver.secondaryStart && secondBytes == resolver.secondaryBytes) {
            boolean valid = true;
            for (int i = 0; i < recipes.length; i++)
                if (!resolver.matches(found[i], recipes[i])) {
                    valid = false;
                    break;
                }
            if (valid)
                return true;
        }
        cached = false;
        for (int i = 0; i < recipes.length; i++) {
            found[i] = 0;
            counts[i] = 0;
        }
        first = resolver.romStart;
        bytes = resolver.romBytes;
        second = resolver.secondaryStart;
        secondBytes = resolver.secondaryBytes;
        resolver.scan(this, first, bytes);
        if (secondBytes != 0)
            resolver.scan(this, second, secondBytes);
        if (resolver.error != 0)
            return false;
        for (int i = 0; i < recipes.length; i++)
            if (counts[i] != 1)
                return false;
        cached = true;
        return true;
    }

    /** Called only for an anchor from a validated ROM window. */
    void candidate(FirmwareResolver resolver, int anchorAddress, int anchor) {
        for (int entry = heads[anchor & (BUCKETS - 1)]; entry != 0; entry = next[entry - 1]) {
            int index = entry - 1;
            RomRecipe recipe = recipes[index];
            if (anchor != recipe.anchor)
                continue;
            int address = anchorAddress - recipe.anchorOffset;
            if (resolver.matches(address, recipe)) {
                found[index] = address;
                if (counts[index] < 2)
                    counts[index]++;
            }
        }
    }
}
