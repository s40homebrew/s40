/* SPDX-License-Identifier: MIT */
/* ARM926 backend for RAP (6300/6303/C2-01) and BCM (C3). Never use the ARM1176 writer.
 * The verified ROM walker establishes the D0000000 translation-table alias.
 * We decode its legacy section/coarse/fine descriptors ourselves, under IRQ/FIQ
 * exclusion, limiting table accesses to the profile's DDR envelope. Flash-backed
 * coarse pages use an OS-owned RAM copy and reversible page-table updates;
 * flash itself is never written. This is a component-tested port; real MIDR,
 * mapping, cache behavior and patch persistence still require handset testing.
 *
 * Cache operations mirror the C2 ROM helpers: 32-byte clean/invalidate D lines,
 * drain write buffer, invalidate I lines. No ARMv6 TTBR1/TTBCR or barriers.
 * RAP uses the verified D0000000 table alias. BCM maps tables and primary code
 * at their physical DDR addresses, as established by its startup map builder.
 * Descriptor words 19..23 supply mapping family, scan bounds and DDR envelope.
 * New SoCs need table-alias and DDR evidence, not a firmware version allowlist.
 */
typedef unsigned int uint32_t;
typedef signed int int32_t;
typedef unsigned char uint8_t;
typedef unsigned short uint16_t;
typedef unsigned int uintptr_t;
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
READ_CP(ttbr, c2, c0, 0)
READ_CP(dacr, c3, c0, 0)
static inline u32 cpsr(void) {
    u32 v;
    __asm__ volatile("mrs %0,cpsr" : "=r"(v));
    return v;
}
static inline void irq_restore(u32 v) {
    __asm__ volatile("msr cpsr_c,%0" ::"r"(v) : "memory", "cc");
}
static inline void drain(void) {
    u32 z = 0;
    __asm__ volatile("mcr p15,0,%0,c7,c10,4" ::"r"(z) : "memory");
}
static inline void domain(u32 v) {
    __asm__ volatile("mcr p15,0,%0,c3,c0,0" ::"r"(v) : "memory");
    drain();
}
/* Whole audited ARMv5 walker, with only its six relative calls masked below.
 * Exact bytes are shared by RM-443 08.90/10.12 and RM-721 11.40. The matched address is discovered;
 * no firmware routine address is imported or called. */
static const uint8_t walker[] = {
        0xf7, 0xb5, 0x00, 0x25, 0x06, 0x00, 0xed, 0x43, 0x82, 0xb0, 0x03, 0xf0, 0xb7, 0xf8, 0x07,
        0x00, 0x03, 0xf0, 0xa8, 0xf8, 0xc0, 0x07, 0x03, 0xd0, 0x0d, 0x20, 0x00, 0x07, 0xc4, 0x1b,
        0x00, 0xe0, 0x00, 0x24, 0x03, 0xf0, 0xb7, 0xf8, 0x01, 0x90, 0x80, 0x08, 0x80, 0x00, 0x40,
        0x1c, 0x03, 0xf0, 0xb7, 0xf8, 0x03, 0xf0, 0xb6, 0xe9, 0x31, 0x0d, 0x38, 0x19, 0x89, 0x00,
        0x40, 0x58, 0x03, 0x99, 0x08, 0x60, 0x81, 0x07, 0x89, 0x0f, 0x01, 0x29, 0x0b, 0xd0, 0x02,
        0x29, 0x07, 0xd0, 0x03, 0x29, 0x11, 0xd1, 0x00, 0x0b, 0x00, 0x03, 0x00, 0x19, 0x31, 0x03,
        0x89, 0x0d, 0x06, 0xe0, 0x00, 0x25, 0x09, 0xe0, 0x80, 0x0a, 0x80, 0x02, 0x00, 0x19, 0x31,
        0x03, 0x09, 0x0e, 0x89, 0x00, 0x41, 0x58, 0x04, 0x98, 0x01, 0x25, 0x01, 0x60, 0x01, 0x98,
        0x03, 0xf0, 0x92, 0xf8, 0x28, 0x00, 0x05, 0xb0, 0xf0, 0xbd};
static const unsigned calls[] = {10, 16, 34, 46, 50, 120};
static const uint32_t helper_insn[] = {0xee120f10, 0xee110f10, 0xee130f10,
                                       0xee030f10, 0xee071f9a, 0xee030f10};
/* Earlier RAP compiler form (RM-217): -(TTBR + 0x30000000) is the same
 * D0000000 - TTBR alias. This walker has no write-buffer-drain call. All five
 * CP15 helpers and the matching DACR restore are still checked independently. */
static const uint8_t legacy_walker[] = {
        0xf7, 0xb5, 0x06, 0x1c, 0x17, 0x1c, 0x00, 0x25, 0xed, 0x43, 0x82, 0xb0, 0x02, 0xf0, 0xb2,
        0xfc, 0x01, 0x90, 0x02, 0xf0, 0xa3, 0xfc, 0xc0, 0x07, 0x05, 0xd0, 0x01, 0x99, 0x03, 0x20,
        0x00, 0x07, 0x08, 0x18, 0x44, 0x42, 0x00, 0xe0, 0x00, 0x24, 0x02, 0xf0, 0xb0, 0xfc, 0x00,
        0x90, 0x80, 0x08, 0x80, 0x00, 0x01, 0x30, 0x02, 0xf0, 0xb0, 0xfc, 0x01, 0x98, 0x31, 0x0d,
        0x89, 0x00, 0x00, 0x19, 0x40, 0x58, 0x03, 0x99, 0x08, 0x60, 0x81, 0x07, 0x89, 0x0f, 0x01,
        0x29, 0x0b, 0xd0, 0x02, 0x29, 0x07, 0xd0, 0x03, 0x29, 0x10, 0xd1, 0x00, 0x0b, 0x00, 0x03,
        0x00, 0x19, 0x31, 0x03, 0x89, 0x0d, 0x06, 0xe0, 0x00, 0x25, 0x08, 0xe0, 0x80, 0x0a, 0x80,
        0x02, 0x00, 0x19, 0x31, 0x03, 0x09, 0x0e, 0x89, 0x00, 0x40, 0x58, 0x01, 0x25, 0x38, 0x60,
        0x00, 0x98, 0x02, 0xf0, 0x8d, 0xfc, 0x28, 0x1c, 0x05, 0xb0, 0xf0, 0xbd};
static const unsigned legacy_calls[] = {12, 18, 40, 52, 122};

static uint32_t call_target(uint32_t pc, int arm) {
    const volatile uint16_t* p = (const volatile uint16_t*)(uintptr_t)pc;
    uint32_t first = p[0], second = p[1];
    if ((first & 0xf800) != 0xf000 || (second & 0xf800) != (arm ? 0xe800u : 0xf800u))
        return 0;
    if (arm && (second & 1))
        return 0;
    int32_t delta = (int32_t)(((first & 2047) << 12) | ((second & 2047) << 1));
    if (delta & 0x400000)
        delta -= 0x800000;
    uint32_t target = pc + 4 + (uint32_t)delta;
    return arm ? target & ~3u : target;
}

static int walker_at(uint32_t address, uint32_t begin, uint32_t end, int legacy) {
    const uint8_t* expected = legacy ? legacy_walker : walker;
    const unsigned* edges = legacy ? legacy_calls : calls;
    unsigned length = legacy ? sizeof(legacy_walker) : sizeof(walker), count = legacy ? 5 : 6;
    const volatile uint8_t* p = (const volatile uint8_t*)(uintptr_t)address;
    if (address > end - length)
        return 0;
    unsigned i;
    for (i = 0; i < length; i++) {
        int masked = 0;
        for (unsigned j = 0; j < count; j++)
            if (i >= edges[j] && i < edges[j] + 4)
                masked = 1;
        if (!masked && p[i] != expected[i])
            return 0;
    }
    uint32_t write_target = 0;
    for (i = 0; i < count; i++) {
        int arm = !legacy && i == 4;
        uint32_t target = call_target(address + edges[i], arm);
        if (target < begin || target > end - 12 || (target & 3))
            return 0;
        if (!arm) {
            if (*(const volatile uint32_t*)(uintptr_t)target != 0x46c04778u)
                return 0;
            target += 4;
        }
        unsigned instruction = legacy && i == 4 ? 5 : i;
        if (*(const volatile uint32_t*)(uintptr_t)target != helper_insn[instruction] ||
            *(const volatile uint32_t*)(uintptr_t)(target + 4) != 0xe12fff1eu)
            return 0;
        if (i == 3)
            write_target = target;
        if (i == count - 1 && target != write_target)
            return 0;
    }
    return 1;
}

__attribute__((noinline)) uint32_t find_table_walker(uint32_t begin) {
    /* The RAP profile supplies this readable ROM window. */
    const uint32_t end = begin + 65536;
    uint32_t found = 0;
    for (uint32_t address = begin; address <= end - sizeof(walker); address += 2) {
        const volatile uint8_t* p = (const volatile uint8_t*)(uintptr_t)address;
        if (p[0] != 0xf7 || p[1] != 0xb5)
            continue;
        int legacy = p[2] == 0x06 && p[3] == 0x1c;
        if ((!legacy && (p[2] != 0 || p[3] != 0x25)) || !walker_at(address, begin, end, legacy))
            continue;
        if (found)
            return 0; /* Ambiguity is a failure, never first-match wins. */
        found = address;
    }
    return found;
}

/* Mapping description: pte is the live L2 entry through the table alias. */
struct mapping {
    u32 pa, l1, l2, pte, domain;
};
static int ddr(volatile u32* r, u32 p, u32 n) {
    return p >= r[W_PHYSICAL_START] && n <= r[W_PHYSICAL_END] - r[W_PHYSICAL_START] &&
           p <= r[W_PHYSICAL_END] - n;
}
static int ram(volatile u32* r, u32 p, u32 n) {
    return p >= r[W_RAM_START] && n <= r[W_RAM_END] - r[W_RAM_START] && p <= r[W_RAM_END] - n;
}
/* The older BSE bootstrap takes its physical RAM arena from a unique type-4
 * record. Its live lookup is the authority for RM-217, whose MCU does not
 * embed a physical-RAM address table. The copied records are kernel-owned,
 * may reside outside ordinary OS RAM, and are followed only through this
 * checked firmware routine; no extra virtual window is scanned. */
__attribute__((noinline)) int legacy_ram_bounds(volatile u32* r) {
    static const u16 body[] = {0xb570, 0x1c0e, 0x1c15, 0x1c04, 0x07a0, 0xd001, 0,
                               0,      0x6820, 0x42b0, 0xd103, 0x3d01, 0xd201, 0x1c20,
                               0xbd70, 0x2802, 0xd006, 0x2803, 0xd101, 0x68a4, 0xe7ee,
                               0x6860, 0x1904, 0xe7eb, 0x2000, 0xbd70};
    u32 begin = r[W_ROM_START], end = begin + 65536, found = 0;
    if (r[W_ROM_END] - begin < 65536)
        return 0;
    for (u32 p = begin; p <= end - 20; p += 2) {
        const volatile u16* h = (const volatile u16*)p;
        if (h[0] != 0x1c03 || (h[1] & 0xff00) != 0x4800 || h[2] != 0x6800 || h[3] != 0x2800 ||
            h[4] != 0xd002 || h[5] != 0x1c0a || h[6] != 0x1c19 || (h[7] & 0xf800) != 0xe000 ||
            h[8] != 0x2000 || h[9] != 0x4770)
            continue;
        u32 literal = ((p + 6) & ~3u) + 4 * (h[1] & 255);
        if (literal < begin || literal > end - 4)
            continue;
        u32 global = *(const volatile u32*)literal;
        if ((global & 3) || !ram(r, global, 4))
            continue;
        int offset = h[7] & 2047;
        if (offset & 1024)
            offset -= 2048;
        u32 target = p + 18 + 2 * offset;
        if (target < begin || target > end - sizeof(body))
            continue;
        const volatile u16* code = (const volatile u16*)target;
        u32 i;
        for (i = 0; i < sizeof(body) / 2; i++)
            if (i != 6 && i != 7 && code[i] != body[i])
                break;
        u32 assertion = call_target(target + 12, 0);
        if (i != sizeof(body) / 2 || assertion < begin || assertion >= end)
            continue;
        if (found)
            return 0;
        found = p | 1;
    }
    if (!found)
        return 0;
    r[W_RAM_TAGS] = found;
    const volatile u32* tag = ((const volatile u32* (*)(u32, u32))found)(4, 0);
    if (!tag || ((u32)tag & 3) || tag[0] != 4 || tag[1] < 16 || (tag[1] & 3))
        return 0;
    u32 size = tag[2], base = tag[3];
    if (((base | size) & 4095) || size < 16384 || size > 0x1000000u || base > ~size ||
        size < r[W_RAM_END] - r[W_RAM_START] || ((const volatile u32* (*)(u32, u32))found)(4, 1))
        return 0;
    r[W_PHYSICAL_START] = base;
    r[W_PHYSICAL_END] = base + size;
    return 1;
}
static int translate(volatile u32* r, u32 va, struct mapping* m) {
    u32 alias = r[W_BACKEND] == ARM926_RAP ? 0xd0000000u : r[W_TTBR0];
    m->l1 = *(volatile u32*)(alias + (va >> 20) * 4);
    m->l2 = m->pte = m->pa = 0;
    m->domain = (m->l1 >> 5) & 15;
    u32 kind = m->l1 & 3;
    if (kind == 2) {
        m->pa = (m->l1 & 0xfff00000u) | (va & 0xfffff);
        return 1;
    }
    if (kind != 1 && kind != 3)
        return 0;
    u32 table = m->l1 & (kind == 3 ? 0xfffff000u : 0xfffffc00u), bytes = kind == 3 ? 4096 : 1024;
    if (!ddr(r, table, bytes) || (r[W_BACKEND] == ARM926_RAP && table < r[W_TTBR0]))
        return 0;
    u32 index = kind == 3 ? (va >> 10) & 1023 : (va >> 12) & 255;
    m->pte = alias + table - r[W_TTBR0] + index * 4;
    m->l2 = *(volatile u32*)m->pte;
    u32 size;
    if ((m->l2 & 3) == 1)
        size = 65536;
    else if ((m->l2 & 3) == 2)
        size = 4096;
    else if ((m->l2 & 3) == 3 && kind == 3)
        size = 1024;
    else
        return 0;
    m->pa = (m->l2 & ~(size - 1)) | (va & (size - 1));
    return 1;
}
static void publish(volatile u32* r, const struct mapping* m) {
    r[W_PHYSICAL] = m->pa;
    r[W_L1] = m->l1;
    r[W_L2] = m->l2;
}
static void cache_range(u32 address, u32 bytes) {
    for (u32 a = address & ~31u; a < address + bytes; a += 32)
        __asm__ volatile("mcr p15,0,%0,c7,c14,1" ::"r"(a) : "memory");
    drain();
    for (u32 a = address & ~31u; a < address + bytes; a += 32)
        __asm__ volatile("mcr p15,0,%0,c7,c5,1" ::"r"(a) : "memory");
}
/* Flash code is copied to one OS-owned 4 KiB page. This header precedes its
 * ordinary RAM alias, outside the executable page. No pointer into Java or this
 * temporary payload persists. The VM OS task outlives a MIDlet; os_block_*
 * allocations are explicitly freed on Restore, or reclaimed by reboot.
 * Large 64 KiB mappings are converted to 16 small pages, preserving each AP
 * quarter and C/B bits. Only the affected 4 KiB page points to the RAM copy.
 * Section/fine-table flash mappings need another shadow implementation.
 */
struct shadow {
    u32 magic, raw, shadow, page, ttbr, pte, count, physical, site, l1, reserved[6], old[16];
};
#define SHADOW_MAGIC 0x48534231u
#define ALLOC_BYTES (4096 + 4095 + sizeof(struct shadow))
static u32 remapped(const struct shadow* s, u32 i) {
    u32 old = s->old[i], base, flags;
    if (s->count == 16) {
        base = (old & 0xffff0000u) + i * 4096;
        u32 ap = (old >> (4 + 2 * (i / 4))) & 3;
        flags = (old & 12) | 2 | (ap * 0x550);
    } else {
        base = old & 0xfffff000u;
        flags = old & 4095;
    }
    u32 changed = s->count == 16 ? (s->page >> 12) & 15 : 0;
    return (i == changed ? s->physical : base) | flags;
}
static int shadow_entries(const struct shadow* s) {
    for (u32 i = 0; i < s->count; i++)
        if (((volatile u32*)s->pte)[i] != remapped(s, i))
            return 0;
    return 1;
}
static void mapping_sync(u32 pte, u32 count) {
    for (u32 a = pte & ~31u; a < pte + count * 4; a += 32)
        __asm__ volatile("mcr p15,0,%0,c7,c10,1" ::"r"(a) : "memory");
    drain();
    u32 z = 0;
    __asm__ volatile("mcr p15,0,%0,c8,c7,0\n\tmcr p15,0,%0,c7,c5,0" ::"r"(z) : "memory");
}
static void set_mapping(const struct shadow* s, int original) {
    for (u32 i = 0; i < s->count; i++)
        ((volatile u32*)s->pte)[i] = original ? s->old[i] : remapped(s, i);
    mapping_sync(s->pte, s->count);
}
static u32 ram_alias(volatile u32* r, u32 pa) {
    struct mapping m;
    // Common linear DDR alias, verified rather than assumed from its base.
    if (translate(r, r[W_RAM_START], &m)) {
        u32 va = r[W_RAM_START] + pa - m.pa;
        if (ram(r, va, 4096) && translate(r, va, &m) && m.pa == pa)
            return va;
    }
    for (u32 va = r[W_RAM_START]; va < r[W_RAM_END]; va += 4096)
        if (translate(r, va, &m) && m.pa == pa)
            return va;
    return 0;
}
static struct shadow* owned_shadow(volatile u32* r, const struct mapping* m) {
    if (r[W_BACKEND] != ARM926_RAP || (m->l1 & 3) != 1 || (m->l2 & 3) != 2)
        return 0;
    u32 va = ram_alias(r, m->pa & ~4095u);
    if (!va || !ram(r, va - sizeof(struct shadow), 4096 + sizeof(struct shadow)))
        return 0;
    struct shadow* s = (struct shadow*)(va - sizeof(struct shadow));
    if (s->magic != SHADOW_MAGIC || s->shadow != va || s->site != r[W_SITE] ||
        s->ttbr != r[W_TTBR0] || s->page != (r[W_SITE] & ~4095u) ||
        s->physical != (m->pa & ~4095u) || !ram(r, s->raw, ALLOC_BYTES) ||
        ((s->raw + sizeof(*s) + 4095) & ~4095u) != va || s->l1 != m->l1 ||
        (s->count != 1 && s->count != 16))
        return 0;
    u32 first = m->pte - (s->count == 16 ? ((s->page >> 12) & 15) * 4 : 0);
    if (s->pte != first)
        return 0;
    for (u32 i = 0; i < s->count; i++) {
        if (s->count == 16 && ((s->old[i] & 3) != 1 || s->old[i] != s->old[0]))
            return 0;
        if (s->count == 1 && (s->old[i] & 3) != 2)
            return 0;
    }
    return shadow_entries(s) ? s : 0;
}
static int can_shadow(volatile u32* r, const struct mapping* m) {
    u32 page = r[W_SITE] & ~4095u;
    if (r[W_BACKEND] != ARM926_RAP || (m->l1 & 3) != 1 || ((m->l2 & 3) != 1 && (m->l2 & 3) != 2) ||
        page < r[W_ROM_START] || page > r[W_ROM_END] - 4096)
        return 0;
    if ((m->l2 & 3) == 1) {
        u32 first = m->pte - ((page >> 12) & 15) * 4;
        for (u32 i = 0; i < 16; i++)
            if (((volatile u32*)first)[i] != m->l2)
                return 0;
    }
    return 1;
}
void heap_booster_main(volatile u32* r) {
    r[W_MAGIC] = MAGIC;
    r[W_STATUS] = 201;
    r[W_CPSR] = cpsr();
    if ((r[W_CPSR] & 31) != 31 && (r[W_CPSR] & 31) != 19)
        return;
    r[W_CPU] = midr();
    if ((r[W_CPU] & 0xff0ffff0u) != 0x41069260u) {
        r[W_STATUS] = 202;
        return;
    }
    r[W_SCTLR] = sctlr();
    r[W_TTBR0] = ttbr();
    r[W_TTBR1] = r[W_TTBCR] = 0;
    r[W_DACR] = dacr();
    u32 address = r[W_SITE], op = r[W_OPERATION];
    if (op > OP_RESTORE || (r[W_BACKEND] != ARM926_RAP && r[W_BACKEND] != ARM926_BCM) ||
        r[W_ROM_END] - r[W_ROM_START] < 65536 || address < r[W_ROM_START] ||
        address > r[W_ROM_END] - PATCH_BYTES || (address & 1) ||
        (address & 1023) > 1024 - PATCH_BYTES) {
        r[W_STATUS] = 203;
        return;
    }
    if (r[W_BACKEND] == ARM926_RAP && !r[W_PHYSICAL_START] && !r[W_PHYSICAL_END] &&
        !legacy_ram_bounds(r)) {
        r[W_STATUS] = 209;
        return;
    }
    if (r[W_PHYSICAL_END] - r[W_PHYSICAL_START] < 16384) {
        r[W_STATUS] = 203;
        return;
    }
    if (!(r[W_SCTLR] & 1) || (r[W_SCTLR] & 128) || (r[W_TTBR0] & 16383) ||
        !ddr(r, r[W_TTBR0], 16384)) {
        r[W_STATUS] = 204;
        return;
    }
    if (r[W_BACKEND] == ARM926_RAP) {
        r[W_WALKER] = find_table_walker(r[W_ROM_START]);
        if (!r[W_WALKER]) {
            r[W_STATUS] = 209;
            return;
        }
    }
    u32 raw = 0, release = 0, state, previous;
retry:
    state = cpsr();
    irq_restore(state | 0xc0);
    previous = dacr();
    domain(r[W_BACKEND] == ARM926_RAP ? (previous & ~3u) | 1u : previous | 12u);
    struct mapping m;
    r[W_STATUS] = 205;
    if (ttbr() != r[W_TTBR0] || sctlr() != r[W_SCTLR] || !translate(r, address, &m))
        goto done;
    publish(r, &m);
    u32 access = (previous >> (m.domain * 2)) & 3;
    if ((access != 1 && access != 3) || (r[W_BACKEND] == ARM926_BCM && m.pa != address))
        goto done;
    volatile u16* target = (volatile u16*)address;
    if (!same(target, r + W_EXPECTED)) {
        r[W_STATUS] = 206;
        goto done;
    }
    if (op == OP_APPLY && !quota_fits(r)) {
        r[W_STATUS] = 207;
        goto done;
    }
    if (ddr(r, m.pa, PATCH_BYTES)) {
        struct shadow* s = owned_shadow(r, &m);
        if (s) {
            r[W_SHADOW] = s->shadow;
            r[W_SHADOW_STATE] = 1;
        }
        if (op == OP_CHECK) {
            r[W_STATUS] = 0;
            goto done;
        }
        if (s && op == OP_RESTORE) {
            // Reopening the booster rediscovers exports. Losing that match
            // must not unmap an owned page and then call a null deallocator.
            if (!firmware_function(r, r[W_FREE])) {
                r[W_STATUS] = 212;
                goto done;
            }
            cache_range(s->page, 4096);
            domain(previous | 3u);
            set_mapping(s, 1);
            if (!same(target, r + W_DESIRED)) {
                set_mapping(s, 0);
                r[W_STATUS] = 208;
                goto done;
            }
            release = s->raw;
            s->magic = 0;
            r[W_SHADOW] = 0;
            r[W_SHADOW_STATE] = 2;
            r[W_STATUS] = 0;
            goto done;
        }
        if (same(target, r + W_DESIRED)) {
            r[W_STATUS] = 0;
            goto done;
        }
        domain(previous | (3u << (m.domain * 2)));
        put_code(address, r + W_DESIRED);
        cache_range(address, PATCH_BYTES);
        int ok = same(target, r + W_DESIRED);
        if (!ok) {
            put_code(address, r + W_EXPECTED);
            cache_range(address, PATCH_BYTES);
        }
        r[W_STATUS] = ok ? 0 : 208;
        goto done;
    }
    // No stores to flash. A valid coarse mapping can be redirected to OS RAM.
    if (!can_shadow(r, &m)) {
        r[W_STATUS] = 210;
        goto done;
    }
    if (op == OP_RESTORE) {
        r[W_STATUS] = same(target, r + W_DESIRED) ? 0 : 206;
        goto done;
    }
    if (!firmware_function(r, r[W_ALLOC]) || !firmware_function(r, r[W_FREE])) {
        r[W_STATUS] = 212;
        goto done;
    }
    if (op == OP_CHECK) {
        r[W_SHADOW_STATE] = 3;
        r[W_STATUS] = 0;
        goto done;
    }
    if (!raw) {
        domain(previous);
        irq_restore(state);
        raw = ((u32 (*)(u32))r[W_ALLOC])(ALLOC_BYTES);
        if (!raw) {
            r[W_STATUS] = 211;
            return;
        }
        if (!ram(r, raw, ALLOC_BYTES)) {
            ((void (*)(u32))r[W_FREE])(raw);
            r[W_STATUS] = 211;
            return;
        }
        goto retry;
    }
    u32 va = (raw + sizeof(struct shadow) + 4095) & ~4095u;
    struct mapping storage;
    if (!translate(r, va, &storage) || (storage.pa & 4095) || !ddr(r, storage.pa, 4096)) {
        r[W_STATUS] = 211;
        goto done;
    }
    u32 pa = storage.pa;
    for (u32 i = 1024; i < 4096; i += 1024)
        if (!translate(r, va + i, &storage) || storage.pa != pa + i) {
            r[W_STATUS] = 211;
            goto done;
        }
    struct shadow* s = (struct shadow*)(va - sizeof(struct shadow));
    s->magic = SHADOW_MAGIC;
    s->raw = raw;
    s->shadow = va;
    s->page = address & ~4095u;
    s->ttbr = r[W_TTBR0];
    s->count = (m.l2 & 3) == 1 ? 16 : 1;
    s->physical = pa;
    s->site = address;
    s->l1 = m.l1;
    s->pte = m.pte - (s->count == 16 ? ((s->page >> 12) & 15) * 4 : 0);
    for (u32 i = 0; i < s->count; i++)
        s->old[i] = ((volatile u32*)s->pte)[i];
    for (u32 i = 0; i < 1024; i++)
        ((volatile u32*)va)[i] = ((volatile u32*)s->page)[i];
    put_code(va + (address & 4095), r + W_DESIRED);
    cache_range(va, 4096);
    cache_range(s->page, 4096);
    domain(previous | 3u);
    set_mapping(s, 0);
    if (!same(target, r + W_DESIRED)) {
        set_mapping(s, 1);
        s->magic = 0;
        r[W_STATUS] = 208;
        goto done;
    }
    r[W_SHADOW] = va;
    r[W_SHADOW_STATE] = 1;
    r[W_STATUS] = 0;
    raw = 0;  // The page now owns the OS allocation.
done:
    domain(previous);
    irq_restore(state);
    if (raw)
        ((void (*)(u32))r[W_FREE])(raw);
    if (release)
        ((void (*)(u32))r[W_FREE])(release);
}
