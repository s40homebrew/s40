/* SPDX-License-Identifier: MIT */
/** A masked Thumb-halfword recipe shared by application and maintenance discovery. */
class RomRecipe {
    final String name;
    final int alignment, anchorOffset, anchor, size;
    final int[] words, masks;

    RomRecipe(String patternName, int instructionAlignment, int offset, int anchorWord, int[] data,
            int[] mask) {
        name = patternName;
        alignment = instructionAlignment;
        anchorOffset = offset;
        anchor = anchorWord;
        words = data;
        masks = mask;
        size = data.length * 2;
    }

    /** The caller owns the memory view; matching never allocates or follows pointers. */
    final boolean matches(int[] view, int base, int address, int first, int limit) {
        if ((address & (alignment - 1)) != 0 || address - first < 0 || limit - first < size
                || address - first > limit - first - size)
            return false;
        for (int i = 0; i < words.length; i++) {
            int at = address + i * 2;
            int half = (view[((at & ~3) - base) >>> 2] >>> ((at & 2) * 8)) & 65535;
            if ((half & masks[i]) != words[i])
                return false;
        }
        return true;
    }
}
