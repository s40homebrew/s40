/* SPDX-License-Identifier: MIT */
#ifndef S40_FIRMWARE_GUARD_H
#define S40_FIRMWARE_GUARD_H
#include <stdint.h>

typedef struct {
    uintptr_t address; /* Read address, with any Thumb entry bit cleared. */
    uint32_t fnv32;
} fw_rom_fingerprint;
enum { FW_ROM_FINGERPRINT_BYTES = 32 };

/* Call only after a discovered binding has identified the readable firmware
 * map. This checks an exact-build ABI prefix; it is neither address discovery
 * nor a safe probe of arbitrary memory, nor cryptographic image verification.
 */
static inline int fw_rom_matches(const fw_rom_fingerprint* entry) {
    const volatile uint8_t* bytes = (const volatile uint8_t*)entry->address;
    uint32_t hash = UINT32_C(2166136261);
    for (unsigned i = 0; i < FW_ROM_FINGERPRINT_BYTES; ++i)
        hash = (hash ^ bytes[i]) * UINT32_C(16777619);
    return hash == entry->fnv32;
}
#endif
