/* SPDX-License-Identifier: MIT */
/**
 * Separate capability from the full graphics/native-application ABI.
 * Model selection provides readable scan windows; content discovery locates the
 * functions. These ranges are platform mappings, not per-version addresses.
 */
final class HeapBoosterProfile implements HeapBoosterProtocol {
    final String id;
    final int romStart, romEnd, ramStart, ramEnd;
    final int backend, physicalStart, physicalEnd;
    final int mapPrefixBytes;
    HeapBoosterProfile(String name, int first, int end, int ramFirst, int ramLast, int writer,
            int physicalFirst, int physicalLast) {
        this(name, first, end, ramFirst, ramLast, writer, physicalFirst, physicalLast, 0);
    }
    HeapBoosterProfile(String name, int first, int end, int ramFirst, int ramLast, int writer,
            int physicalFirst, int physicalLast, int prefixBytes) {
        id = name;
        romStart = first;
        romEnd = end;
        ramStart = ramFirst;
        ramEnd = ramLast;
        backend = writer;
        physicalStart = physicalFirst;
        physicalEnd = physicalLast;
        mapPrefixBytes = prefixBytes;
    }
    boolean rom(int address, int bytes) {
        return bytes >= 0 && address - romStart >= 0
                && address - romStart <= romEnd - romStart - bytes;
    }
    boolean armv6() {
        return backend == ARMV6_IDENTITY || backend == ARMV6_RAPU;
    }
    String payloadResource() {
        return armv6() ? "/ram_booster.bin" : "/ram_booster_arm926.bin";
    }
    boolean ram(int address, int bytes) {
        return bytes >= 0 && address - ramStart >= 0
                && address - ramStart <= ramEnd - ramStart - bytes;
    }
}
