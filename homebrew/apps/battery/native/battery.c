/* SPDX-License-Identifier: MIT */
/* RM-443 10.12 adapter. Only short, read-only energy getters are called.
 * The shared resolver/bootstrap remain unchanged. This app deliberately uses
 * exact-build entry points, guarded by their code and RAM-pointer literals.
 */
#include <stdint.h>

#include "s40/runtime.h"

static int matches(uintptr_t address, const unsigned char* bytes, unsigned size) {
    const volatile unsigned char* rom = (const volatile unsigned char*)address;
    for (unsigned i = 0; i < size; ++i)
        if (rom[i] != bytes[i])
            return 0;
    return 1;
}

static unsigned getter(uintptr_t address) { return ((unsigned (*)(void))(address | 1u))(); }

/* Read the soft-float double without assuming an atomic 64-bit RAM load. */
static double energy(uintptr_t address) {
    const volatile uint32_t* words = (const volatile uint32_t*)address;
    union {
        uint32_t words[2];
        double value;
    } sample;
    for (unsigned attempt = 0; attempt < 4; ++attempt) {
        sample.words[0] = words[0];
        sample.words[1] = words[1];
        if (sample.words[0] == words[0] && sample.words[1] == words[1])
            return sample.value;
    }
    return -1.0;
}

int s40_app_main(void) {
    static const unsigned char capacity[] = {
            0x7d, 0x48, 0x10, 0xb5, 0x00, 0x68, 0x3c, 0x21, 0x40, 0x1e, 0x40, 0x00, 0xa0, 0xf3,
            0xcc, 0xed, 0x40, 0x1d, 0x04, 0x04, 0x24, 0x0c, 0x02, 0xf0, 0x96, 0xff, 0xa0, 0x42,
            0x02, 0xd8, 0x02, 0xf0, 0x92, 0xff, 0x04, 0x00, 0x20, 0x00, 0x10, 0xbd};
    static const unsigned char nominal[] = {0x15, 0x48, 0x80, 0x88, 0x70, 0x47};
    static const unsigned char bars[] = {0x84, 0x48, 0x1c, 0x38, 0x00, 0x79, 0x70, 0x47};
    static const unsigned char voltage[] = {0x77, 0x48, 0x40, 0x89, 0x70, 0x47};
    static const unsigned char charger[] = {0x02, 0x48, 0x00, 0x78, 0x70, 0x47};
    if (!matches(0xF0166C40, capacity, sizeof(capacity)) ||
        !matches(0xF0169B86, nominal, sizeof(nominal)) ||
        !matches(0xF0166C38, bars, sizeof(bars)) ||
        !matches(0xF0166C6E, voltage, sizeof(voltage)) ||
        !matches(0xF016C37E, charger, sizeof(charger)) ||
        *(const volatile uint32_t*)0xF0166E38 != 0xD9A2A000 ||
        *(const volatile uint32_t*)0xF0166E4C != 0xD803FCC4 ||
        *(const volatile uint32_t*)0xF0169BDC != 0xD8040270 ||
        *(const volatile uint32_t*)0xF016C388 != 0xD80031C8)
        return 41;

    /* D9A2A000 holds the firmware's remaining-time estimate plus one.
     * Zero is uninitialised; the capacity getter would underflow on zero.
     * The native capacity getter converts this to mAh and caps at nominal.
     */
    unsigned full = getter(0xF0169B86);
    if (!full || full == 65535 || full > 20000)
        return 42;
    unsigned charger_state = getter(0xF016C37E);
    unsigned remaining = UINT32_MAX;
    int32_t charge = *(const volatile int32_t*)0xD9A2A030;
    int32_t accounted = *(const volatile int32_t*)0xD9A2A038;
    unsigned paused = *(const volatile uint8_t*)0xD803FCB7;
    unsigned source = 0;
    if (charger_state == 4 && *(const volatile uint8_t*)0xD9A2A040 == 1) {
        remaining = full;
        source = 3;
    } else if (paused || charger_state == 2 || charger_state == 6) {
        /* The discharge getter can return its +5 mAh floor while charging.
         * Mirror the arithmetic used when the firmware resumes its battery
         * monitor (F0166AF6 -> F016C9A6 -> F0169854), without calling those
         * mutating functions. Energy is in microamp-seconds; the charging
         * counter is in milliamp-seconds. The accounted counter is subtracted
         * so charge already applied to the model is never added twice.
         */
        double used = energy(0xD8040208);
        double empty = energy(0xD8040220);
        double added = paused ? ((double)charge - accounted) / 3600.0 : 0.0;
        if (!*(const volatile uint16_t*)0xD8040264 && used >= 0.0 && used <= 1.0e13 &&
            empty > 0.0 && empty <= 1.0e13 && added >= -(double)full && added <= 2.0 * full) {
            double base = (empty - used) / 3600000.0;
            /* An empty/sentinel model with no accumulated charge supplies no
             * useful charging estimate. Do not present the +5 floor as 1%.
             */
            if (base > 0.0 || added > 0.0) {
                double estimate = base + added + 5.0;
                if (estimate < 0.0)
                    estimate = 0.0;
                if (estimate > full)
                    estimate = full;
                remaining = (unsigned)estimate;
                source = 2;
            }
        }
    } else if (*(const volatile uint32_t*)0xD9A2A000) {
        remaining = getter(0xF0166C40);
        source = 1;
    }
    if (remaining != UINT32_MAX && remaining > full) {
        remaining = UINT32_MAX;
        source = 0;
    }

    /* App result occupies the error-text area only on successful return.
     * Loader/session control words and API bindings are left intact.
     */
    s40_result[17] = remaining;
    s40_result[18] = full;
    s40_result[19] = getter(0xF0166C38);
    s40_result[20] = getter(0xF0166C6E);
    s40_result[21] = charger_state;
    s40_result[22] = source;
    s40_result[23] = (uint32_t)charge;
    s40_result[16] = 0x42415432; /* BAT2: complete snapshot */
    return 0;
}
