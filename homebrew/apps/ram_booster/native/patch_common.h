/* SPDX-License-Identifier: MIT */
#pragma once
#include "heap_booster_protocol.h"

/* Architecture-independent policy handling only. Translation, exclusions and
 * cache/TLB operations stay in their CPU backend. Words hold two Thumb-1
 * instructions, low halfword first; never write the padding halfword. */
static int same(volatile u16* code, volatile u32* words) {
    for (u32 i = 0; i < PATCH_HALFWORDS; i++)
        if (code[i] != ((words[i / 2] >> ((i & 1) * 16)) & 65535))
            return 0;
    return 1;
}
static void put_code(u32 address, volatile u32* words) {
    for (u32 i = 0; i < PATCH_HALFWORDS; i++)
        ((volatile u16*)address)[i] = (u16)(words[i / 2] >> ((i & 1) * 16));
}
static int quota_fits(volatile u32* r) {
    u32 mib = r[W_MIB];
    return mib >= 3 && mib <= 8 && r[W_POOL_BYTES] >= (mib << 20);
}
static inline int firmware_function(volatile u32* r, u32 function) {
    u32 address = function & ~1u;
    return function && address >= r[W_ROM_START] && address < r[W_ROM_END];
}
