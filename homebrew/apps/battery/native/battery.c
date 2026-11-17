/* SPDX-License-Identifier: MIT */
/* Battery bindings come from the shared, bounded firmware resolver. */
#include <stdint.h>

#include "firmware/bindings.h"
#include "s40/runtime.h"

static uintptr_t binding(unsigned id) { return s40_result[FW_API_DESCRIPTOR + id]; }
static unsigned getter(unsigned id) { return ((unsigned (*)(void))binding(id))(); }

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
    if (binding(API_BATTERY_ABI) != FW_BATTERY_ABI_RM443)
        return 41;

    /* The model holds the firmware's remaining-time estimate plus one.
     * Zero is uninitialised; the capacity getter would underflow on zero.
     * The native capacity getter converts this to mAh and caps at nominal.
     */
    unsigned full = getter(API_BATTERY_NOMINAL);
    if (!full || full == 65535 || full > 20000)
        return 42;
    unsigned charger_state = getter(API_BATTERY_CHARGER);
    unsigned remaining = UINT32_MAX;
    int32_t charge = *(const volatile int32_t*)binding(API_BATTERY_CHARGE);
    int32_t accounted = *(const volatile int32_t*)binding(API_BATTERY_ACCOUNTED);
    unsigned paused = *(const volatile uint8_t*)binding(API_BATTERY_PAUSED);
    unsigned source = 0;
    if (charger_state == 4 && *(const volatile uint8_t*)binding(API_BATTERY_COMPLETE) == 1) {
        remaining = full;
        source = 3;
    } else if (paused || charger_state == 2 || charger_state == 6) {
        /* The discharge getter can return its +5 mAh floor while charging.
         * Mirror the arithmetic used when the firmware resumes its battery
         * monitor, without calling its mutating functions. Energy is in microamp-seconds; the
         * charging counter is in milliamp-seconds. The accounted counter is subtracted so charge
         * already applied to the model is never added twice.
         */
        double used = energy(binding(API_BATTERY_USED_ENERGY));
        double empty = energy(binding(API_BATTERY_EMPTY_ENERGY));
        double added = paused ? ((double)charge - accounted) / 3600.0 : 0.0;
        if (!*(const volatile uint16_t*)binding(API_BATTERY_MODEL_INVALID) && used >= 0.0 &&
            used <= 1.0e13 && empty > 0.0 && empty <= 1.0e13 && added >= -(double)full &&
            added <= 2.0 * full) {
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
    } else if (*(const volatile uint32_t*)binding(API_BATTERY_ESTIMATE)) {
        remaining = getter(API_BATTERY_CAPACITY);
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
    s40_result[19] = getter(API_BATTERY_BARS);
    s40_result[20] = getter(API_BATTERY_VOLTAGE);
    s40_result[21] = charger_state;
    s40_result[22] = source;
    s40_result[23] = (uint32_t)charge;
    s40_result[16] = 0x42415432; /* BAT2: complete snapshot */
    return 0;
}
