/* SPDX-License-Identifier: MIT */
/* A returning, UI-free native debugging payload. Search up to 1 MiB on-device
 * without transferring the whole region over USB/Bluetooth. Inputs r0-r3:
 * start, byte length, needle, mask. Outputs: r0 = first match (0 if absent),
 * r1 = number of matches. The caller must supply mapped readable memory. */
#include <stdint.h>

__attribute__((section(".text.entry"), visibility("default"))) uint64_t bridge_main(uint32_t start,
                                                                                    uint32_t bytes,
                                                                                    uint32_t needle,
                                                                                    uint32_t mask) {
    if ((start & 3) || (bytes & 3) || bytes > 0x100000 || start + bytes < start)
        return UINT64_MAX;
    uint32_t first = 0, matches = 0;
    const volatile uint32_t* words = (const volatile uint32_t*)(uintptr_t)start;
    for (uint32_t i = 0; i < bytes / 4; i++) {
        if ((words[i] & mask) == (needle & mask)) {
            if (!matches)
                first = start + i * 4;
            matches++;
        }
    }
    return ((uint64_t)matches << 32) | first;
}
