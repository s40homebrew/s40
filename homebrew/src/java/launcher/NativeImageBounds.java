/* SPDX-License-Identifier: MIT */
/** Header validation shared by the launcher and executable host checks. */
final class NativeImageBounds {
    static int allocationBytes(
            int bytes, int memory, int entry, int relocations, int stack, int bindings) {
        if (bytes <= 0 || bytes > memory || memory > NativeBuildConfig.MEMORY_LIMIT
                || (memory & 3) != 0 || entry < 0 || entry >= bytes || relocations < 0
                || relocations > NativeProtocol.IMAGE_MAX_RELOCATIONS
                || stack != NativeProtocol.IMAGE_STACK_BYTES || bindings < 1
                || bindings > NativeProtocol.IMAGE_MAX_BINDINGS)
            return 0;
        long total = (long) memory + (long) NativeProtocol.RELOCATION_BYTES * relocations
                + (long) NativeProtocol.BINDING_BYTES * bindings + stack;
        if (total > NativeBuildConfig.ALLOCATION_LIMIT || total > 0x7fffffffL)
            return 0;
        return (int) total;
    }
}
