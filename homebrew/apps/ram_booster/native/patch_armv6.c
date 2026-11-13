/* SPDX-License-Identifier: MIT */
/* ARMv6 RAM-only patch backend. No allocation, resident helper, MMU mapping
 * changes, or flash writes. Selected code must translate into physical RAM:
 * RM-839 uses identity-mapped DDR; RAPU supplies live RAM-bank records.
 *
 * CP15 encodings: ARM1176JZ-S TRM DDI 0333H, chapters 3 and 6:
 * https://documentation-service.arm.com/static/5e8e358afd977155116a8b47
 * Domain-manager access is scoped to the target domain, with IRQ/FIQ masked;
 * DACR and interrupt state are restored before returning to the VM.
 *
 * Porting boundary: this file combines ARM1176 CP15/cache operations with the
 * RM-839 identity mappings and RAPU firmware-supplied mappings. Firmware
 * versions play no part in selection. For another CPU/map, add a backend and
 * select its carrier/build target explicitly; see apps/ram_booster/docs/porting.md.
 * HBP1 descriptor layout is documented there and shared with the Java session.
 */
typedef unsigned int u32;
typedef unsigned short u16;
#include "patch_common.h"

#define READ_CP(name, crn, crm, op)                                        \
    static inline u32 name(void) {                                         \
        u32 v;                                                             \
        __asm__ volatile("mrc p15,0,%0," #crn "," #crm "," #op : "=r"(v)); \
        return v;                                                          \
    }
READ_CP(midr, c0, c0, 0)
READ_CP(sctlr, c1, c0, 0)
READ_CP(ttbr0, c2, c0, 0)
READ_CP(ttbr1, c2, c0, 1)
READ_CP(ttbcr, c2, c0, 2)
READ_CP(dacr, c3, c0, 0)

#include "rapu.h"

static inline u32 cpsr(void) {
    u32 v;
    __asm__ volatile("mrs %0,cpsr" : "=r"(v));
    return v;
}
static inline void irq_restore(u32 v) {
    __asm__ volatile("msr cpsr_c,%0" ::"r"(v) : "memory", "cc");
}
static inline void barrier(void) {
    u32 zero = 0;
    __asm__ volatile("mcr p15,0,%0,c7,c10,4\n\tmcr p15,0,%0,c7,c5,4" ::"r"(zero) : "memory");
}
static inline void domain(u32 v) {
    __asm__ volatile("mcr p15,0,%0,c3,c0,0" ::"r"(v) : "memory");
    barrier();
}
static void sync_code(volatile u32* r, u32 start, u32 size) {
    if (r[W_BACKEND] == ARMV6_RAPU) {
        // RAPU has an outer cache. Its discovered wrapper maintains both levels
        // and the prefetch buffer; inner-cache-only loops are insufficient.
        ((void (*)(u32, u32))r[W_CACHE_SYNC])(start, size);
        barrier();
        return;
    }
    /* 32-byte lines and these CP15 operations belong to this backend. Do not
     * carry them into an ARM9/Cortex port just because its VM recipe matches. */
    u32 end = start + size;
    for (u32 a = start & ~31u; a < end; a += 32)
        __asm__ volatile("mcr p15,0,%0,c7,c10,1" ::"r"(a) : "memory");
    barrier();
    for (u32 a = start & ~31u; a < end; a += 32)
        __asm__ volatile("mcr p15,0,%0,c7,c5,1" ::"r"(a) : "memory");
    u32 zero = 0;
    __asm__ volatile("mcr p15,0,%0,c7,c5,6" ::"r"(zero) : "memory");
    barrier();
}
static int table_range(u32 p, u32 size) {
    /* Firmware MMU implementation uses the identity-mapped internal SRAM
     * table at 0x88000; additional tables may be in physical DDR. */
    return (p >= 0x80000 && p <= 0x90000 - size) || (p >= 0x60000000 && p <= 0x64000000 - size);
}
static u32 translation(volatile u32* r, u32 va, u32* domain_id) {
    u32 n = r[W_TTBCR] & 7, first;
    if (r[W_TTBCR] & ~7u)
        return 0; /* No unsupported table-disable/reserved modes. */
    if (n && (va >> (32 - n)))
        first = r[W_TTBR1] & ~0x3fffu;
    else
        first = r[W_TTBR0] & ~((1u << (14 - n)) - 1);
    u32 index = (va >> 20) & ((n && (va >> (32 - n))) ? 4095u : ((1u << (12 - n)) - 1));
    u32 at = first + index * 4;
    if (!table_range(at, 4))
        return 0;
    u32 l1 = *(volatile u32*)at;
    r[W_L1] = l1;
    *domain_id = (l1 >> 5) & 15;
    if ((l1 & 3) == 2) {
        if (l1 & (1u << 18))
            return 0; /* Supersections need a separate audit. */
        return (l1 & 0xfff00000) | (va & 0xfffff);
    }
    if ((l1 & 3) != 1)
        return 0;
    at = (l1 & 0xfffffc00) + ((va >> 12) & 255) * 4;
    if (!table_range(at, 4))
        return 0;
    u32 l2 = *(volatile u32*)at;
    r[W_L2] = l2;
    if ((l2 & 3) == 1)
        return (l2 & 0xffff0000) | (va & 65535);
    if ((l2 & 3) == 2 || ((r[W_SCTLR] & (1u << 23)) && (l2 & 3) == 3))
        return (l2 & 0xfffff000) | (va & 4095);
    return 0;
}

void heap_booster_main(volatile u32* r) {
    r[W_MAGIC] = MAGIC; /* HBP1 */
    r[W_STATUS] = 201;
    r[W_CPSR] = cpsr();
    if ((r[W_CPSR] & 31) == 16 || (r[W_CPSR] & 31) == 0)
        return;
    r[W_CPU] = midr();
    /* CPU implementation contract, with silicon variant/revision bits ignored;
     * not a firmware release check. CP15 decoding below depends on this core. */
    u32 cpu = r[W_CPU] & 0xff00fff0;
    if (cpu != 0x4100b760 && !(r[W_BACKEND] == ARMV6_RAPU && cpu == 0x4100b360)) {
        r[W_STATUS] = 202;
        return;
    }
    r[W_SCTLR] = sctlr();
    r[W_TTBR0] = ttbr0();
    r[W_TTBR1] = ttbr1();
    r[W_TTBCR] = ttbcr();
    r[W_DACR] = dacr();
    u32 address = r[W_SITE], op = r[W_OPERATION];
    if (op > OP_RESTORE || (r[W_BACKEND] != ARMV6_IDENTITY && r[W_BACKEND] != ARMV6_RAPU) ||
        address < r[W_ROM_START] || address > r[W_ROM_END] - PATCH_BYTES || (address & 1) ||
        (address & 4095) > 4096 - PATCH_BYTES) {
        r[W_STATUS] = 203;
        return;
    }
    if (!(r[W_SCTLR] & 1) || (r[W_SCTLR] & 128)) {
        r[W_STATUS] = 204;
        return;
    }
    if (r[W_BACKEND] == ARMV6_RAPU) {
        // The BSE walker uses TTBR0 for the entire address space. The read is
        // checked against live TTBCR rather than a firmware-version list.
        if (r[W_TTBCR] != 0) {
            r[W_STATUS] = 204;
            return;
        }
        if (!rapu_discover(r)) {
            r[W_STATUS] = 209;
            return;
        }
        if ((r[W_CACHE_SYNC] & 3) || r[W_CACHE_SYNC] < r[W_ROM_START] ||
            r[W_CACHE_SYNC] > r[W_ROM_END] - 52) {
            r[W_STATUS] = 203;
            return;
        }
    }
    // The same transaction is used by both ARMv6 mapping families. Exclude
    // interrupts for the mapping/byte check as well as the actual stores.
    u32 state = cpsr(), previous = dacr();
    irq_restore(state | 0xc0);
    u32 dom = 0;
    u32 physical = r[W_BACKEND] == ARMV6_RAPU ? rapu_translate(r, address, &dom)
                                              : translation(r, address, &dom);
    r[W_PHYSICAL] = physical;
    u32 access = (r[W_DACR] >> (dom * 2)) & 3;
    if ((r[W_BACKEND] == ARMV6_RAPU
                 ? (physical == 0xffffffffu || !rapu_ram(r, physical, PATCH_BYTES))
                 : physical != address) ||
        (access != 1 && access != 3)) {
        r[W_STATUS] = 205;
        goto done;
    }
    volatile u16* target = (volatile u16*)address;
    if (!same(target, r + W_EXPECTED)) {
        r[W_STATUS] = 206;
        goto done;
    }
    if (op == OP_CHECK) {
        r[W_STATUS] = 0;
        goto done;
    }
    if (op == OP_APPLY && !quota_fits(r)) {
        r[W_STATUS] = 207;
        goto done;
    }
    if (same(target, r + W_DESIRED)) {
        r[W_STATUS] = 0;
        goto done;
    }
    domain(previous | (3u << (dom * 2)));
    put_code(address, r + W_DESIRED);
    sync_code(r, address, PATCH_BYTES);
    int ok = same(target, r + W_DESIRED);
    if (!ok) {
        put_code(address, r + W_EXPECTED);
        sync_code(r, address, PATCH_BYTES);
    }
    r[W_STATUS] = ok ? 0 : 208;
done:
    domain(previous);
    irq_restore(state);
}
