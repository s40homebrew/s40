/* SPDX-License-Identifier: MIT */
/* Experimental X-GOLD arena growth. A persistent exit hook waits until the
 * adjacent method reservation is gone; no live object is moved. The original
 * shrink/GC routine then runs normally. Expanded arenas persist until reboot. */
#define XGOLD_GROWTH
#include "patch_xgold.c"
#include "growth_vmm.h"

extern char native_entry[], growth_entry[], growth_entry_header[];
extern char growth_entry_callback[], growth_entry_original[], growth_entry_continuation[];
extern u32 growth_image_base(void);
#define G_MAGIC 0x48475833u
#define G_ALLOC (4095u + 4096u + 5u * 4096u + 4096u + PAYLOAD_CAPACITY)
struct grown {
    u32 magic, raw, base, ttbr, state, busy, bytes, code;
    u32 page[5], original[2], l1word[2], installed[2];
    u32 shrink_call, shrink_next, hook;
    struct growth_vmm vmm;
    u32 saved[RESULT_WORDS];
};
static void grow_callback(struct grown* s, u32 from_exit);
static void save_plan(struct grown* s, volatile u32* r) {
    u32 reservation = s->saved[W_RESERVATION];
    for (u32 i = 0; i < X_EXIT_CALLS; i++) s->saved[i] = r[i];
    s->saved[W_RESERVATION] = reservation ? reservation : 4 << 20;
    /* Diagnostic method/object pointers are temporary bootstrap state. */
    for (u32 i = W_OWNER; i <= W_PAYLOAD_CODE; i++) s->saved[i] = 0;
}
static u32 offset(char* symbol) { return (u32)symbol - (u32)native_entry; }
static u32 table_at(const struct grown* s, u32 n) { return s->base + 5 * 4096 + n * 1024; }
static u32 physical(const struct grown* s, u32 address) {
    for (u32 i = 0; i < 5; i++)
        if ((address & ~4095u) == s->page[i]) return s->base + i * 4096 + (address & 4095);
    return 0;
}
static u32 grown_entry(const struct grown* s, u32 section, u32 i) {
    u32 address = (s->original[section] & 0xfff00000u) + i * 4096;
    u32 moved = physical(s, address);
    return (moved ? moved : address) | page_flags(s->original[section]);
}
static u32 value(u32 address, u32 width) {
    return width == 2 ? *(volatile u16*)address : *(volatile u32*)address;
}
static void store_value(u32 address, u32 width, u32 v) {
    if (width == 2) *(volatile u16*)address = (u16)v;
    else *(volatile u32*)address = v;
}
static int plan_ok(volatile u32* r) {
    if (r[G_PATCH_COUNT] != 13 || r[W_PAYLOAD_BYTES] > PAYLOAD_CAPACITY ||
        !r[W_PAYLOAD_BYTES] || (r[W_PAYLOAD_BYTES] & 3) || (r[W_SITE] & 1) ||
        (r[W_SITE] & 4095) > 4096 - PATCH_BYTES || (r[G_SHRINK] & 3) ||
        (r[G_SHRINK] & 4095) > 4088) return 0;
    u32 pages[5] = {r[W_SITE] & ~4095u, r[G_RUNTIME_FREE] & ~4095u,
        r[G_PROVIDER] & ~4095u, r[G_RELEASE_WRAPPER] & ~4095u, r[G_SHRINK] & ~4095u};
    if ((pages[0] >> 20) == (pages[2] >> 20) ||
        (r[G_RUNTIME_TOTAL] & ~4095u) != pages[1] ||
        (r[G_RELEASE_HELPER] & ~4095u) != pages[2]) return 0;
    for (u32 i = 0; i < 5; i++) {
        if (!within(pages[i], 4096, r[W_ROM_START], r[W_ROM_END])) return 0;
        if ((pages[i] >> 20) != (pages[0] >> 20) && (pages[i] >> 20) != (pages[2] >> 20)) return 0;
        for (u32 j = 0; j < i; j++) if (pages[i] == pages[j]) return 0;
    }
    for (u32 i = 0; i < 13; i++) {
        volatile u32* p = r + G_PATCHES + i * 4;
        if ((p[1] != 2 && p[1] != 4) || (p[0] & (p[1] - 1)) ||
            !within(p[0], p[1], r[W_ROM_START], r[W_ROM_END])) return 0;
        int found = 0;
        for (u32 j = 1; j < 4; j++) if ((p[0] & ~4095u) == pages[j]) found = 1;
        if (!found || value(p[0], p[1]) != p[2]) return 0;
    }
    return same((volatile u16*)r[W_SITE], r + W_EXPECTED);
}
static void sync_pages(struct grown* s) {
    for (u32 i = 0; i < 5; i++) {
        cache_range(s->saved, s->base + i * 4096, 4096);
        cache_range(s->saved, s->page[i], 4096);
    }
}
static void publish_grown(struct grown* s, int restore) {
    for (u32 i = 0; i < 2; i++) *(volatile u32*)s->l1word[i] = restore ? s->original[i] : s->installed[i];
    mapping_sync(s->l1word[0], 4); mapping_sync(s->l1word[1], 4);
}
static int code_matches(struct grown* s, volatile u32* r) {
    u32 source = growth_image_base();
    u32 special[4] = {offset(growth_entry_header), offset(growth_entry_callback),
        offset(growth_entry_original), offset(growth_entry_continuation)};
    u32 expected[4] = {(u32)s, s->code + ((u32)grow_callback - (u32)native_entry),
        s->shrink_call, s->shrink_next};
    for (u32 i = 0; i < s->bytes; i += 4) {
        u32 want = *(u32*)(source + i);
        if (i == 8) want = 0; /* unused Java entry binding in resident copy */
        for (u32 j = 0; j < 4; j++) if (i == special[j]) want = expected[j];
        if (*(u32*)(s->code + i) != want) return 0;
    }
    return s->bytes == r[W_PAYLOAD_BYTES];
}
static struct grown* owned_grown(volatile u32* r, struct mapping* m) {
    u32 va = m->pa & ~4095u;
    if (!ram(r, va - 4096, 10 * 4096)) return 0;
    struct grown* s = (struct grown*)(va - 4096);
    if (s->magic != G_MAGIC || s->base != va || !ram(r, s->raw, G_ALLOC) ||
        ((s->raw + 8191) & ~4095u) != va || s->ttbr != r[W_TTBR0] ||
        s->state < 1 || s->state > 4 || s->bytes > PAYLOAD_CAPACITY || (s->bytes & 3) ||
        s->code != va + 6 * 4096 || s->hook != s->code + offset(growth_entry) ||
        s->page[0] != (r[W_SITE] & ~4095u) || s->page[1] != (r[G_RUNTIME_FREE] & ~4095u) ||
        s->page[2] != (r[G_PROVIDER] & ~4095u) || s->page[3] != (r[G_RELEASE_WRAPPER] & ~4095u) ||
        s->page[4] != (r[G_SHRINK] & ~4095u) || s->shrink_next != r[G_SHRINK] + 8 ||
        s->shrink_call != r[G_SHRINK] - 232 || !code_matches(s, r)) return 0;
    const u32* cfg = (const u32*)&s->vmm;
    u32 expected_cfg[8] = {r[G_HEAP_STATE], r[G_PAGE_SIZE], r[G_AREA_GETTER],
        r[G_RESERVE_FIXED], r[G_LOCK], r[G_UNLOCK], r[W_RAM_START], r[W_RAM_END]};
    for (u32 i = 0; i < 8; i++) if (cfg[i] != expected_cfg[i]) return 0;
    if (s->saved[W_SITE] != r[W_SITE] || s->saved[W_CACHE_SYNC] != r[W_CACHE_SYNC]) return 0;
    for (u32 i = W_BACKEND; i <= W_RAM_END; i++)
        if (s->saved[i] != r[i]) return 0;
    for (u32 i = G_PROVIDER; i <= G_PATCH_COUNT; i++)
        if (s->saved[i] != r[i]) return 0;
    for (u32 i = 0; i < 13; i++) {
        u32 at = G_PATCHES + i * 4;
        if (s->saved[at] != r[at] || s->saved[at + 1] != r[at + 1] ||
            !physical(s, r[at])) return 0;
    }
    for (u32 i = 0; i < 2; i++) {
        u32 page = s->page[i ? 2 : 0];
        if (!representable_section(s->original[i]) ||
            (s->original[i] & 0xfff00000u) != (page & 0xfff00000u) ||
            s->l1word[i] != r[W_TTBR0] + (page >> 20) * 4 ||
            s->installed[i] != (table_at(s, i) | coarse_flags(s->original[i])) ||
            *(volatile u32*)s->l1word[i] != s->installed[i]) return 0;
        for (u32 j = 0; j < 256; j++) if (*(u32*)(table_at(s, i) + j * 4) != grown_entry(s, i, j)) return 0;
    }
    for (u32 i = 0; i < 10 * 4096; i += 1024) {
        struct mapping own;
        if (!translate(r, va - 4096 + i, &own) || own.pa != va - 4096 + i) return 0;
    }
    if (*(u32*)r[G_SHRINK] != 0xe51ff004 || *(u32*)(r[G_SHRINK] + 4) != s->hook) return 0;
    return s;
}
static int activate(struct grown* s) {
    volatile u32* r = s->saved;
    for (u32 i = 0; i < 13; i++) {
        volatile u32* p = r + G_PATCHES + i * 4;
        if (!physical(s, p[0]) || value(p[0], p[1]) != p[2]) return 0;
    }
    if (!same((volatile u16*)r[W_SITE], r + W_EXPECTED)) return 0;
    for (u32 i = 0; i < 13; i++) {
        volatile u32* p = r + G_PATCHES + i * 4;
        store_value(physical(s, p[0]), p[1], p[3]);
    }
    put_code(physical(s, r[W_SITE]), r + W_DESIRED);
    sync_pages(s);
    int ok = same((volatile u16*)r[W_SITE], r + W_DESIRED);
    for (u32 i = 0; i < 13; i++) {
        volatile u32* p = r + G_PATCHES + i * 4;
        if (value(p[0], p[1]) != p[3]) ok = 0;
    }
    if (!ok) {
        for (u32 i = 0; i < 13; i++) {
            volatile u32* p = r + G_PATCHES + i * 4;
            store_value(physical(s, p[0]), p[1], p[2]);
        }
        put_code(physical(s, r[W_SITE]), r + W_EXPECTED); sync_pages(s);
    }
    return ok;
}
static void grow_callback(struct grown* s, u32 from_exit) {
    u32 state = cpsr();
    irq_restore(state | 0xc0);
    if (s->magic != G_MAGIC || s->state != 1) {
        irq_restore(state); return;
    }
    if (from_exit) s->saved[X_EXIT_CALLS]++;
    s->saved[X_CALLBACK_CPSR] = state;
    if (s->busy || (state & 0x80)) {
        s->saved[X_WAIT_REASON] = s->busy ? 2 : 1;
        irq_restore(state); return;
    }
    if (ttbr() != s->ttbr || sctlr() != s->saved[W_SCTLR] || ttbcr() != 0 ||
        !plan_ok(s->saved) || !physical(s, s->saved[W_SITE])) {
        s->saved[X_WAIT_REASON] = 3; s->state = 4; irq_restore(state); return;
    }
    for (u32 i = 0; i < 13; i++)
        if (!physical(s, s->saved[G_PATCHES + i * 4])) {
            s->saved[X_WAIT_REASON] = 3; s->state = 4; irq_restore(state); return;
        }
    s->busy = 1; s->saved[X_GROW_ATTEMPTS]++; irq_restore(state);
    u32 heap_base, reserved;
    int answer = growth_extend(&s->vmm, &heap_base, &reserved, s->saved + X_WAIT_REASON);
    irq_restore(state | 0xc0);
    u32 old_domain = dacr(); domain(old_domain | 3);
    s->saved[X_HELPER_RESULT] = answer;
    if (answer == 1) s->saved[W_RESERVATION] = 8 << 20;
    else if (answer == -2) s->saved[W_RESERVATION] = 0;
    if (answer == 1) s->state = activate(s) ? 2 : 4;
    else if (answer != 0) s->state = 4;
    s->busy = 0; domain(old_domain); irq_restore(state);
}
static void report(volatile u32* r, struct grown* s) {
    r[W_GROWTH_STATE] = s ? s->state : 0;
    r[W_GROWTH_LIMIT] = 6 << 20;
    r[W_HEAP_BASE] = *(u32*)r[G_HEAP_STATE];
    r[W_RESERVATION] = s ? s->saved[W_RESERVATION] : 4 << 20;
    if (s && s->state == 2) r[W_POOL_BYTES] = (8 << 20) - 16;
    r[W_SHADOW] = s ? s->base : 0;
    r[W_SHADOW_STATE] = s ? 1 : 3;
    r[W_STATUS] = s && s->state == 4 ? 208 : 0;
    for (u32 i = X_EXIT_CALLS; i < RESULT_WORDS; i++) r[i] = s ? s->saved[i] : 0;
}
void heap_booster_main(volatile u32* r) {
    r[W_MAGIC] = MAGIC; r[W_STATUS] = 201; r[W_CPSR] = cpsr();
    if ((r[W_CPSR] & 31) != 31 && (r[W_CPSR] & 31) != 19) return;
    // The handset enters in SVC with FIQ masked (0x53). Original VMM lock
    // helpers mask/restore IRQ only, preserving that FIQ state. OS calls need
    // IRQ enabled; requiring FIQ enabled wrongly rejects the normal caller.
    if (r[W_CPSR] & 0x80) { r[W_STATUS] = 216; return; }
    r[W_CPU] = midr();
    if ((r[W_CPU] & 0xff0ffff0u) != 0x410fb760u) { r[W_STATUS] = 202; return; }
    r[W_SCTLR] = sctlr(); r[W_TTBR0] = ttbr(); r[W_TTBR1] = ttbr1();
    r[W_TTBCR] = ttbcr(); r[W_DACR] = dacr();
    if (r[W_BACKEND] != ARMV6_XGOLD || !(r[W_SCTLR] & 1) ||
        (r[W_SCTLR] & (128 | 0x00800000)) || r[W_TTBCR] || (r[W_TTBR0] & 16383) ||
        !tables(r, r[W_TTBR0], 16384)) { r[W_STATUS] = 204; return; }
    u32 op = r[W_OPERATION], mib = r[W_MIB];
    if (op > OP_RESTORE || (op == OP_APPLY && (mib < 3 || mib > 6)) ||
        !plan_ok(r) || (r[W_CACHE_SYNC] & 3) || !firmware_function(r, r[W_CACHE_SYNC]) ||
        !firmware_function(r, r[W_ALLOC]) || !firmware_function(r, r[W_FREE])) {
        r[W_STATUS] = 206; return;
    }
    u32 raw = 0, release = 0, irq, dom;
    struct grown* s = 0;
    struct shadow* legacy = 0;
retry:
    s = 0; legacy = 0;
    irq = cpsr(); irq_restore(irq | 0xc0); dom = dacr(); domain(dom | 3);
    struct mapping m;
    r[W_STATUS] = 205;
    if (ttbr() != r[W_TTBR0] || !translate(r, r[W_SITE], &m)) goto done;
    r[W_PHYSICAL] = m.pa; r[W_L1] = m.l1; r[W_L2] = m.l2;
    if (!plan_ok(r)) { r[W_STATUS] = 206; goto done; }
    if (ram(r, m.pa, PATCH_BYTES)) {
        s = owned_grown(r, &m);
        if (!s) legacy = owned_shadow(r, &m);
        if (!s && !legacy) { r[W_STATUS] = 210; goto done; }
    } else if (!flash_section(r, &m)) { r[W_STATUS] = 210; goto done; }
    if (s && s->busy) { r[W_STATUS] = 215; goto done; }
    if (s && s->state == 1 && op == OP_APPLY && mib == 3) {
        r[W_STATUS] = 213; goto done;
    }
    if (op == OP_CHECK) { report(r, s); goto done; }
    if (s && s->state == 4) { report(r, s); goto done; }
    if (op == OP_RESTORE && !s) {
        if (legacy) {
            cache_range(r, legacy->page, 4096); publish_mapping(legacy, 1);
            if (!same((volatile u16*)r[W_SITE], r + W_DESIRED)) {
                publish_mapping(legacy, 0); r[W_STATUS] = 208; goto done;
            }
            release = legacy->raw; legacy->magic = 0;
        }
        report(r, 0); r[W_SHADOW_STATE] = 2; goto done;
    }
    if (s) {
        if (s->state == 2 || op == OP_RESTORE || mib == 3) {
            put_code(physical(s, r[W_SITE]), r + W_DESIRED); sync_pages(s);
            if (!same((volatile u16*)r[W_SITE], r + W_DESIRED)) {
                put_code(physical(s, r[W_SITE]), r + W_EXPECTED); sync_pages(s);
                r[W_STATUS] = 208; goto done;
            }
            /* The wrapper may still be returning after callback busy clears.
             * Keep its code mapped until reboot, including pending cancel. */
            if (s->state != 2) s->state = 3;
        } else {
            save_plan(s, r);
            s->state = 1;
        }
        report(r, s); goto done;
    }
    if (mib == 3 && !quota_fits(r)) { r[W_STATUS] = 207; goto done; }
    if (mib < 4) { r[W_STATUS] = 213; goto done; } /* use existing quota writer */
    if (!raw) {
        domain(dom); irq_restore(irq);
        raw = ((u32 (*)(u32))r[W_ALLOC])(G_ALLOC);
        if (!raw) { r[W_STATUS] = 211; return; }
        if (!ram(r, raw, G_ALLOC)) { ((void (*)(u32))r[W_FREE])(raw); r[W_STATUS] = 211; return; }
        goto retry;
    }
    u32 va = (raw + 8191) & ~4095u;
    for (u32 i = 0; i < 10 * 4096; i += 1024) {
        struct mapping own;
        if (!translate(r, va - 4096 + i, &own) || own.pa != va - 4096 + i) {
            r[W_STATUS] = 211; goto done;
        }
    }
    s = (struct grown*)(va - 4096);
    for (u32 i = 0; i < sizeof(*s) / 4; i++) ((u32*)s)[i] = 0;
    s->raw = raw; s->base = va; s->ttbr = r[W_TTBR0]; s->state = 1;
    s->bytes = r[W_PAYLOAD_BYTES]; s->code = va + 6 * 4096;
    s->page[0] = r[W_SITE] & ~4095u; s->page[1] = r[G_RUNTIME_FREE] & ~4095u;
    s->page[2] = r[G_PROVIDER] & ~4095u; s->page[3] = r[G_RELEASE_WRAPPER] & ~4095u;
    s->page[4] = r[G_SHRINK] & ~4095u;
    save_plan(s, r);
    s->shrink_call = r[G_SHRINK] - 232; s->shrink_next = r[G_SHRINK] + 8;
    if (*(u32*)r[G_SHRINK] != 0xe92d4038 ||
        (*(u32*)(r[G_SHRINK] + 4) & 0xff000000) != 0xeb000000) { r[W_STATUS] = 206; goto done; }
    for (u32 i = 0; i < 2; i++) {
        struct mapping part;
        u32 address = s->page[i ? 2 : 0];
        if (!translate(r, address, &part)) { r[W_STATUS] = 205; goto done; }
        u32 original = i == 0 && legacy ? legacy->original : part.l1;
        if (!representable_section(original) || (original & 0xfff00000u) != (address & 0xfff00000u)) {
            r[W_STATUS] = 210; goto done;
        }
        s->original[i] = original; s->l1word[i] = part.l1word;
        s->installed[i] = table_at(s, i) | coarse_flags(original);
        for (u32 j = 0; j < 256; j++) ((u32*)table_at(s, i))[j] = grown_entry(s, i, j);
    }
    for (u32 i = 0; i < 5; i++)
        for (u32 j = 0; j < 1024; j++) ((u32*)(va + i * 4096))[j] = ((u32*)s->page[i])[j];
    u32 source = growth_image_base();
    for (u32 i = 0; i < s->bytes; i += 4) *(u32*)(s->code + i) = *(u32*)(source + i);
    *(u32*)(s->code + 8) = 0;
    *(u32*)(s->code + offset(growth_entry_header)) = (u32)s;
    *(u32*)(s->code + offset(growth_entry_callback)) = s->code + ((u32)grow_callback - (u32)native_entry);
    *(u32*)(s->code + offset(growth_entry_original)) = s->shrink_call;
    *(u32*)(s->code + offset(growth_entry_continuation)) = s->shrink_next;
    s->hook = s->code + offset(growth_entry);
    *(u32*)physical(s, r[G_SHRINK]) = 0xe51ff004;
    *(u32*)physical(s, r[G_SHRINK] + 4) = s->hook;
    s->vmm = (struct growth_vmm){r[G_HEAP_STATE], r[G_PAGE_SIZE], r[G_AREA_GETTER],
        r[G_RESERVE_FIXED], r[G_LOCK], r[G_UNLOCK], r[W_RAM_START], r[W_RAM_END]};
    s->magic = G_MAGIC;
    sync_pages(s); cache_range(r, s->code, s->bytes); mapping_sync(table_at(s, 0), 2048);
    publish_grown(s, 0);
    if (*(u32*)r[G_SHRINK] != 0xe51ff004 || *(u32*)(r[G_SHRINK] + 4) != s->hook ||
        !same((volatile u16*)r[W_SITE], r + W_EXPECTED)) {
        publish_grown(s, 1);
        if (legacy) publish_mapping(legacy, 0);
        s->magic = 0; r[W_STATUS] = 208; goto done;
    }
    if (legacy) { release = legacy->raw; legacy->magic = 0; }
    raw = 0; report(r, s);
done:
    domain(dom); irq_restore(irq);
    if (raw) ((void (*)(u32))r[W_FREE])(raw);
    if (release) ((void (*)(u32))r[W_FREE])(release);
    if (r[W_STATUS] == 0 && op == OP_APPLY && s && s->state == 1) {
        grow_callback(s, 0); report(r, s);
    }
}
