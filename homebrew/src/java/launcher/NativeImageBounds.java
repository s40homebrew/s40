/* SPDX-License-Identifier: MIT */
/** Header validation shared by the launcher and executable host checks. */
final class NativeImageBounds {
    static int allocationBytes(
            int bytes, int memory, int entry, int relocations, int stack, int bindings) {
        if (bytes <= 0 || bytes > memory || memory > NativeBuildConfig.MEMORY_LIMIT
                || (memory & 3) != 0 || entry < 0 || entry >= bytes || relocations < 0
                || relocations > 65536 || stack != 196608 || bindings < 1 || bindings > 256)
            return 0;
        long total = (long) memory + 4L * relocations + 8L * bindings + stack;
        if (total > NativeBuildConfig.ALLOCATION_LIMIT || total > 0x7fffffffL)
            return 0;
        return (int) total;
    }
}
