/* SPDX-License-Identifier: MIT */
/* ARM1176 X-GOLD, legacy (SCTLR.XP=0) identity-mapped SRAM/DDR.
 * Flash sections are never written: replace one L1 section with an OS-owned
 * coarse table mapping the same 1 MiB, except one copied 4 KiB code page.
 * Only the one L1 word is published. Restore reinstates it before freeing.
 * All 256 replacement entries and the allocation header are checked on reuse.
 */
typedef unsigned int u32;
typedef unsigned short u16;
#include "patch_common.h"
#define READ_CP(name, crn, crm, op) \
    static inline u32 name(void) { u32 v; \
        __asm__ volatile("mrc p15,0,%0," #crn "," #crm "," #op : "=r"(v)); return v; }
READ_CP(midr, c0, c0, 0)
READ_CP(sctlr, c1, c0, 0)
READ_CP(ttbr, c2, c0, 0)
READ_CP(ttbr1, c2, c0, 1)
READ_CP(ttbcr, c2, c0, 2)
READ_CP(dacr, c3, c0, 0)
static inline u32 cpsr(void) {
    u32 v; __asm__ volatile("mrs %0,cpsr" : "=r"(v)); return v;
}
static inline void irq_restore(u32 v) {
    __asm__ volatile("msr cpsr_c,%0" :: "r"(v) : "memory", "cc");
}
static inline void drain(void) {
    u32 z = 0; __asm__ volatile("mcr p15,0,%0,c7,c10,4" :: "r"(z) : "memory");
}
static inline void domain(u32 v) {
    __asm__ volatile("mcr p15,0,%0,c3,c0,0" :: "r"(v) : "memory"); drain();
    u32 z = 0;
    __asm__ volatile("mcr p15,0,%0,c7,c5,4" :: "r"(z) : "memory");
}
static int within(u32 p, u32 n, u32 first, u32 end) {
    return end > first && n <= end - first && p >= first && p <= end - n;
}
static int ram(volatile u32* r, u32 p, u32 n) {
    return within(p, n, r[W_RAM_START], r[W_RAM_END]) &&
           within(p, n, r[W_PHYSICAL_START], r[W_PHYSICAL_END]);
}
static int tables(volatile u32* r, u32 p, u32 n) {
    /* Audited identity SRAM bank; live TTBR is read, not hard-coded. */
    return within(p, n, 0x00080000u, 0x000d0000u) || ram(r, p, n);
}
struct mapping { u32 pa, l1, l2, l1word, domain; };
static int translate(volatile u32* r, u32 va, struct mapping* m) {
    m->l1word = r[W_TTBR0] + (va >> 20) * 4;
    m->l1 = *(volatile u32*)m->l1word;
    m->domain = (m->l1 >> 5) & 15;
    m->l2 = m->pa = 0;
    if ((m->l1 & 3) == 2) {
        if (m->l1 & 0x40000) {
            /* Startup maps most DDR with 16 MiB supersections. This backend
             * uses only their 32-bit physical-address form and domain zero. */
            if (m->l1 & 0x00f001e0) return 0;
            m->pa = (m->l1 & 0xff000000u) | (va & 0xffffff);
        } else {
            m->pa = (m->l1 & 0xfff00000u) | (va & 0xfffff);
        }
        return 1;
    }
    if ((m->l1 & 3) != 1) return 0;
    u32 table = m->l1 & 0xfffffc00u;
    if (!tables(r, table, 1024)) return 0;
    m->l2 = ((volatile u32*)table)[(va >> 12) & 255];
    /* ARMv6's backward-compatible coarse table also permits type 3 extended
     * small pages. The original mapper uses them at partial RAM boundaries. */
    u32 kind = m->l2 & 3;
    u32 size = kind == 1 ? 65536 : kind >= 2 ? 4096 : 0;
    if (!size) return 0;
    m->pa = (m->l2 & ~(size - 1)) | (va & (size - 1));
    return 1;
}
static void cache_range(volatile u32* r, u32 address, u32 bytes) {
    /* Discovered original positive-length D-clean/I-invalidate leaf. */
    ((void (*)(u32, u32))r[W_CACHE_SYNC])(address, bytes);
    drain();
}
static void mapping_sync(u32 address, u32 bytes) {
    for (u32 a = address & ~31u; a < address + bytes; a += 32)
        __asm__ volatile("mcr p15,0,%0,c7,c10,1" :: "r"(a) : "memory");
    drain();
    u32 z = 0;
    __asm__ volatile("mcr p15,0,%0,c8,c7,0\n\tmcr p15,0,%0,c7,c5,0\n\tmcr p15,0,%0,c7,c5,4"
                     :: "r"(z) : "memory");
}
struct shadow {
    u32 magic, raw, shadow, page, site, ttbr, l1word, original, table, installed;
    u32 reserved[22]; /* fixed 128-byte recoverable header */
};
#define SHADOW_MAGIC 0x48535831u
#define ALLOC_BYTES (4095 + sizeof(struct shadow) + 4096 + 1024)
static u32 page_flags(u32 section) {
    return (section & 12) | (((section >> 10) & 3) * 0x550) | 2;
}
static u32 coarse_flags(u32 section) {
    /* The firmware builder maps section NS bit 19 to coarse NS bit 3,
     * including with XP clear. Keep the original domain and security state. */
    return (section & 0x1e0) | ((section >> 16) & 8) | 1;
}
static int representable_section(u32 section) {
    /* Preserve all permitted descriptor bits. Do not silently discard a new
     * firmware's TEX/S/nG/XN, supersection, or implementation-defined flags. */
    return (section & 3) == 2 && !(section & (0x000fffffu & ~0x00080deeu));
}
static u32 entry(const struct shadow* s, u32 i) {
    u32 pa = (s->original & 0xfff00000u) + i * 4096;
    if (i == ((s->page >> 12) & 255)) pa = s->shadow;
    return pa | page_flags(s->original);
}
static int flash_section(volatile u32* r, const struct mapping* m) {
    u32 page = r[W_SITE] & ~4095u;
    return representable_section(m->l1) && m->pa == r[W_SITE] &&
           within(page, 4096, r[W_ROM_START], r[W_ROM_END]);
}
static struct shadow* owned_shadow(volatile u32* r, const struct mapping* m) {
    if ((m->l1 & 3) != 1 || (m->l2 & 3) != 2) return 0;
    u32 va = m->pa & ~4095u;
    if (!ram(r, va - sizeof(struct shadow), sizeof(struct shadow) + 4096 + 1024)) return 0;
    struct shadow* s = (struct shadow*)(va - sizeof(struct shadow));
    if (s->magic != SHADOW_MAGIC || s->shadow != va || s->site != r[W_SITE] ||
        s->page != (r[W_SITE] & ~4095u) || s->ttbr != r[W_TTBR0] ||
        s->l1word != m->l1word || !ram(r, s->raw, ALLOC_BYTES) ||
        ((s->raw + sizeof(*s) + 4095) & ~4095u) != va || s->table != va + 4096 ||
        !representable_section(s->original) ||
        (s->original & 0xfff00000u) != (s->page & 0xfff00000u) ||
        s->installed != (s->table | coarse_flags(s->original)) || s->installed != m->l1)
        return 0;
    struct mapping own;
    if (!translate(r, va, &own) || own.pa != va ||
        !translate(r, s->table, &own) || own.pa != s->table) return 0;
    for (u32 i = 0; i < 256; i++)
        if (((volatile u32*)s->table)[i] != entry(s, i)) return 0;
    return s;
}
static void publish_mapping(const struct shadow* s, int restore) {
    *(volatile u32*)s->l1word = restore ? s->original : s->installed;
    mapping_sync(s->l1word, 4);
}
#ifndef XGOLD_GROWTH
void heap_booster_main(volatile u32* r) {
    r[W_MAGIC] = MAGIC; r[W_STATUS] = 201; r[W_CPSR] = cpsr();
    if ((r[W_CPSR] & 31) != 31 && (r[W_CPSR] & 31) != 19) return;
    r[W_CPU] = midr();
    if ((r[W_CPU] & 0xff0ffff0u) != 0x410fb760u) { r[W_STATUS] = 202; return; }
    r[W_SCTLR] = sctlr(); r[W_TTBR0] = ttbr(); r[W_TTBR1] = ttbr1();
    r[W_TTBCR] = ttbcr(); r[W_DACR] = dacr();
    u32 address = r[W_SITE], op = r[W_OPERATION];
    if (op > OP_RESTORE || r[W_BACKEND] != ARMV6_XGOLD ||
        !within(address, PATCH_BYTES, r[W_ROM_START], r[W_ROM_END]) || (address & 1) ||
        (address & 4095) > 4096 - PATCH_BYTES ||
        (r[W_CACHE_SYNC] & 3) || !firmware_function(r, r[W_CACHE_SYNC])) {
        r[W_STATUS] = 203; return;
    }
    if (!(r[W_SCTLR] & 1) || (r[W_SCTLR] & (128 | 0x00800000)) || r[W_TTBCR] ||
        (r[W_TTBR0] & 16383) || !tables(r, r[W_TTBR0], 16384)) {
        r[W_STATUS] = 204; return;
    }
    u32 raw = 0, release = 0, state, previous;
retry:
    state = cpsr(); irq_restore(state | 0xc0); previous = dacr();
    domain(previous | 3); /* identity-mapped translation tables use domain 0 */
    struct mapping m;
    r[W_STATUS] = 205;
    if (ttbr() != r[W_TTBR0] || sctlr() != r[W_SCTLR] || ttbcr() != 0 ||
        !translate(r, address, &m)) goto done;
    r[W_PHYSICAL] = m.pa; r[W_L1] = m.l1; r[W_L2] = m.l2;
    u32 access = (previous >> (m.domain * 2)) & 3;
    if (access != 1 && access != 3) goto done;
    volatile u16* target = (volatile u16*)address;
    if (!same(target, r + W_EXPECTED)) { r[W_STATUS] = 206; goto done; }
    if (op == OP_APPLY && !quota_fits(r)) { r[W_STATUS] = 207; goto done; }
    if (ram(r, m.pa, PATCH_BYTES)) {
        struct shadow* s = owned_shadow(r, &m);
        if (!s) { r[W_STATUS] = 210; goto done; }
        r[W_SHADOW] = s->shadow; r[W_SHADOW_STATE] = 1;
        if (op == OP_CHECK) { r[W_STATUS] = 0; goto done; }
        if (op == OP_RESTORE) {
            if (!firmware_function(r, r[W_FREE])) { r[W_STATUS] = 212; goto done; }
            cache_range(r, s->page, 4096);
            publish_mapping(s, 1);
            if (!same(target, r + W_DESIRED)) {
                publish_mapping(s, 0); r[W_STATUS] = 208; goto done;
            }
            release = s->raw; s->magic = 0;
            r[W_SHADOW] = 0; r[W_SHADOW_STATE] = 2; r[W_STATUS] = 0; goto done;
        }
        if (same(target, r + W_DESIRED)) { r[W_STATUS] = 0; goto done; }
        domain(previous | 3 | (3u << (m.domain * 2)));
        put_code(address, r + W_DESIRED); cache_range(r, address, PATCH_BYTES);
        int ok = same(target, r + W_DESIRED);
        if (!ok) { put_code(address, r + W_EXPECTED); cache_range(r, address, PATCH_BYTES); }
        r[W_STATUS] = ok ? 0 : 208; goto done;
    }
    if (!flash_section(r, &m)) { r[W_STATUS] = 210; goto done; }
    if (op == OP_RESTORE) { r[W_STATUS] = same(target, r + W_DESIRED) ? 0 : 206; goto done; }
    if (!firmware_function(r, r[W_ALLOC]) || !firmware_function(r, r[W_FREE])) {
        r[W_STATUS] = 212; goto done;
    }
    if (op == OP_CHECK) { r[W_SHADOW_STATE] = 3; r[W_STATUS] = 0; goto done; }
    if (!raw) {
        domain(previous); irq_restore(state);
        raw = ((u32 (*)(u32))r[W_ALLOC])(ALLOC_BYTES);
        if (!raw) { r[W_STATUS] = 211; return; }
        if (!ram(r, raw, ALLOC_BYTES)) {
            ((void (*)(u32))r[W_FREE])(raw); r[W_STATUS] = 211; return;
        }
        goto retry;
    }
    u32 va = (raw + sizeof(struct shadow) + 4095) & ~4095u;
    struct mapping storage;
    for (u32 i = 0; i < 5120; i += 1024)
        if (!translate(r, va + i, &storage) || storage.pa != va + i) {
            r[W_STATUS] = 211; goto done;
        }
    struct shadow* s = (struct shadow*)(va - sizeof(struct shadow));
    s->magic = SHADOW_MAGIC; s->raw = raw; s->shadow = va; s->page = address & ~4095u;
    s->site = address; s->ttbr = r[W_TTBR0]; s->l1word = m.l1word; s->original = m.l1;
    s->table = va + 4096; s->installed = s->table | coarse_flags(m.l1);
    for (u32 i = 0; i < 256; i++) ((volatile u32*)s->table)[i] = entry(s, i);
    for (u32 i = 0; i < 1024; i++) ((volatile u32*)va)[i] = ((volatile u32*)s->page)[i];
    put_code(va + (address & 4095), r + W_DESIRED);
    cache_range(r, va, 4096); cache_range(r, s->page, 4096);
    mapping_sync(s->table, 1024);
    publish_mapping(s, 0);
    if (!same(target, r + W_DESIRED)) {
        publish_mapping(s, 1); s->magic = 0; r[W_STATUS] = 208; goto done;
    }
    r[W_SHADOW] = va; r[W_SHADOW_STATE] = 1; r[W_STATUS] = 0; raw = 0;
done:
    domain(previous); irq_restore(state);
    if (raw) ((void (*)(u32))r[W_FREE])(raw);
    if (release) ((void (*)(u32))r[W_FREE])(release);
}
#endif
