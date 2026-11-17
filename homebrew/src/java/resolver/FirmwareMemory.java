/* SPDX-License-Identifier: MIT */
/**
 * Memory-only discovery contract for ports with their own native launcher.
 * Windows are conservative family mappings, not function-address tables.
 * discover must validate firmware metadata before publishing the final regions.
 * No KVM, display or native-entry assumptions are supplied by this type.
 */
abstract class FirmwareMemory {
    final String familyId;
    final int romStart, romLimit, bootBytes, secondaryStart, secondaryLimit;
    final int ramStart, ramLimit;
    final boolean libraryDescriptors;

    FirmwareMemory(String family, int first, int firstLimit, int readablePrefix, int second,
            int secondLimit, int ramFirst, int ramLast, boolean libraries) {
        if (family == null || (first & 3) != 0 || (firstLimit & 3) != 0 || firstLimit - first <= 0
                || readablePrefix < 4 || (readablePrefix & 3) != 0
                || readablePrefix > firstLimit - first || ramLast - ramFirst <= 0
                || (second != 0
                        && ((second & 3) != 0 || (secondLimit & 3) != 0
                                || secondLimit - second <= 0)))
            throw new IllegalArgumentException("Invalid firmware memory windows");
        familyId = family;
        romStart = first;
        romLimit = firstLimit;
        bootBytes = readablePrefix;
        secondaryStart = second;
        secondaryLimit = secondLimit;
        ramStart = ramFirst;
        ramLimit = ramLast;
        libraryDescriptors = libraries;
    }

    abstract boolean discover(FirmwareResolver resolver);
}
