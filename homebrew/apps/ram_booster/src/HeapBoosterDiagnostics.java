/* SPDX-License-Identifier: MIT */
/** Human-readable explanations live outside the allocation-free memory bridge. */
final class HeapBoosterDiagnostics {
    static String stage(int stage) {
        switch (stage) {
            case HeapBoosterSession.PREPARE:
                return "loading native resources";
            case HeapBoosterSession.MEMORY_VIEW:
                return "opening the memory view";
            case HeapBoosterSession.DISCOVERY:
                return "finding the launch policy";
            case HeapBoosterSession.METHOD_LOOKUP:
                return "locating executable methods";
            case HeapBoosterSession.INSTALL:
                return "preparing the native call";
            case HeapBoosterSession.NATIVE_CALL:
                return "checking or updating native memory";
            case HeapBoosterSession.CLEANUP:
                return "restoring temporary VM state";
            default:
                return "unknown stage";
        }
    }
    static String reason(int error) {
        if (error == 1)
            return "Unknown operation or size.";
        if (error == 3)
            return "Native resource has an invalid size or result binding.";
        if (error == 4)
            return "Reserved bytecode did not load as expected.";
        if (error == 5)
            return "Image conversion changed the memory-view marker.";
        if (error == 6)
            return "The image copy did not open the memory view.";
        if (error == 7 || error == 8)
            return "Java object spacing differs from the expected memory layout.";
        if (error == 9)
            return "Java object references are outside the profile's RAM map.";
        if (error == 11 || error == 12)
            return "Class or method-table layout was not recognized.";
        if (error == 13 || error == 14)
            return "More than one native entry or carrier matched.";
        if (error == 16 || error == 17 || error == 23)
            return "Temporary native code or binding could not be written back correctly.";
        if (error == 18)
            return "The native call returned without its result marker.";
        if (error == 90)
            return "Java exception; see Details for the failed stage and exception.";
        if (error == 10)
            return "Size exceeds the detected Java pool. Go Back for sizes that fit.";
        if (error == 19)
            return "Native entry method not found. Open Details for method diagnostics.";
        if (error == 20)
            return "Bytecode carrier not found. Open Details for method diagnostics.";
        if (error == 21 || error == 22)
            return "Payload code buffer not identified. No native call made.";
        if (error == 91)
            return "Temporary VM state could not be fully restored. Reboot before continuing.";
        if (error == 101)
            return "A unique launch-policy pattern was not found.";
        if (error == 102)
            return "A unique Java-pool supplier was not found.";
        if (error == 103)
            return "A unique cache-sync helper was not found.";
        if (error == 104)
            return "The policy block crosses a supported page boundary.";
        if (error == 105)
            return "A unique firmware memory-range table was not found.";
        if (error == 201)
            return "Native code is not in privileged mode. No patch applied.";
        if (error == 202)
            return "CPU backend mismatch. No patch applied.";
        if (error >= 203 && error <= 205)
            return "Memory mapping not supported. No patch applied. Please record the diagnostic "
                    + "values in Details.";
        if (error == 206)
            return "Code changed after discovery. No patch applied.";
        if (error == 207)
            return "Size exceeds the checked shared pool.";
        if (error == 208)
            return "Patch readback failed; rollback was attempted. Reboot before testing another "
                    + "app.";
        if (error == 209)
            return "Translation-table or RAM-region helper not recognized. No patch applied.";
        if (error == 210)
            return "This flash mapping needs another RAM-copy layout. No patch applied.";
        if (error == 211)
            return "Could not allocate a suitable RAM copy. No patch applied.";
        if (error == 212)
            return "The OS allocator or deallocator was not found. The mapping was left unchanged.";
        return "Bootstrap stopped. Record this code and the diagnostic values.";
    }
}
