/* SPDX-License-Identifier: MIT */
#ifndef HEAP_GROWTH_VMM_H
#define HEAP_GROWTH_VMM_H
/* X-GOLD shared object arena only. All addresses are supplied by discovery.
 * The permanent/method provider and its cached page limit remain unchanged.
 * Caller must execute in the Java task with IRQ enabled. FIQ may be masked,
 * as observed on RM-767; original lock helpers preserve its state. This helper
 * never changes code or commits pages; it enlarges one uncommitted VMM tail.
 */
struct growth_vmm {
    unsigned int heap_state, page_size; /* addresses of globals, not values */
    unsigned int area_getter, reserve_fixed, lock, unlock;
    unsigned int ram_start, ram_end;
};
static int growth_range(unsigned int p, unsigned int n,
                        unsigned int first, unsigned int end) {
    return end > first && n <= end - first && p >= first && p <= end - n;
}
static int growth_ram(const struct growth_vmm* c, unsigned int p, unsigned int n) {
    return !(p & 3) && growth_range(p, n, c->ram_start, c->ram_end);
}
static int growth_function(unsigned int p) {
    /* Audited helpers are Thumb functions in the X-GOLD primary code window.
     * The caller additionally validates the discovered instruction recipes. */
    return (p & 1) && p >= 0x40000001u && p < 0x42000000u;
}
/* 1: exact 8 MiB reservation established/already present; 0: occupied gap or
 * absent uncommitted tail; -1: invalid state/context; -2: helper/readback error.
 * heap_base/reserved_bytes are outputs, never Java pointers retained by us.
 * Optional trace[8]: reason, area, committed_end, candidate_node,
 * candidate_start, candidate_end, next_start, bad_fields_mask.
 * Reasons: 4 no tail/sentinel, 5 tail mismatch, 6 occupied neighbor,
 * 7 no exact tail, 8 extended/already 8 MiB, 9 invalid state, 10 readback failure.
 * Bad-field bits 0..6: n[2], n[5], n[7], n[8], n[9], type, reservation end.
 * The caller owns trace storage; no node outside the existing validated walk
 * is inspected for diagnostics.
 */
static int growth_extend(const struct growth_vmm* c, unsigned int* heap_base,
                         unsigned int* reserved_bytes, unsigned int* trace) {
    unsigned int psr;
    __asm__ volatile("mrs %0,cpsr" : "=r"(psr));
    *heap_base = *reserved_bytes = 0;
    if (trace) for (unsigned int i = 0; i < 8; i++) trace[i] = 0;
    if ((psr & 0x80) || ((psr & 31) != 19 && (psr & 31) != 31) ||
        c->ram_start < 0x60000000u || c->ram_end > 0x62000000u ||
        !growth_ram(c, c->heap_state, 0x18) || !growth_ram(c, c->page_size, 4) ||
        !growth_function(c->area_getter) || !growth_function(c->reserve_fixed) ||
        !growth_function(c->lock) || !growth_function(c->unlock)) {
        if (trace) trace[0] = 9;
        return -1;
    }
    ((void (*)(unsigned int))c->lock)(0x11a);
    int answer = -1;
    unsigned int reason = 9;
    unsigned int base = *(volatile unsigned int*)c->heap_state;
    unsigned int page_size = *(volatile unsigned int*)c->page_size;
    if (page_size != 0x4000 || (base & 0x3fff) ||
        !growth_range(base, 0x800000, 0x90000000u, 0xc0000000u)) goto done;
    unsigned int pages = ((volatile unsigned int*)base)[3];
    if (!pages || pages > 0x200) goto done;
    unsigned int committed_end = base + pages * page_size;
    if (trace) trace[2] = committed_end;
    volatile unsigned int* h = (volatile unsigned int*)c->heap_state;
    if (h[2] != base + 16 || h[4] != committed_end || h[3] != base + 16) goto done;
    *heap_base = base;
    unsigned int area = ((unsigned int (*)(void))c->area_getter)();
    if (!growth_ram(c, area, 0x5c)) goto done;
    if (trace) trace[1] = area;
    unsigned int first = ((volatile unsigned int*)area)[1];
    unsigned int end = ((volatile unsigned int*)area)[12];
    if (!growth_range(base, 0x800000, first, end) ||
        first < 0x90000000u || end > 0xc0000000u) goto done;
    unsigned int previous = area + 4;
    unsigned int node = ((volatile unsigned int*)area)[4];
    unsigned int previous_end = first, prefix_end = base;
    for (unsigned int count = 0; count < 4096; count++) {
        if (!growth_ram(c, node, 0x2c)) goto done;
        volatile unsigned int* n = (volatile unsigned int*)node;
        unsigned int start = n[0], bytes = n[1], next = n[3];
        if (n[4] != previous || start < previous_end ||
            !growth_range(start, bytes, first, end)) goto done;
        if (trace) {
            trace[3] = node; trace[4] = start; trace[5] = start + bytes;
        }
        if (!next) { answer = 0; reason = 4; goto done; }
        if (!bytes || (start & 0x3fff) || (bytes & 0x3fff)) goto done;
        unsigned int finish = start + bytes;
        if (finish > base && start < committed_end) {
            /* Every committed prefix byte must remain mapped. Anonymous
             * committed nodes have type 0, as emitted by the original mmap. */
            if (start > prefix_end || finish > committed_end || n[6] != 0) goto done;
            prefix_end = finish;
        }
        if (start == committed_end && prefix_end == committed_end) {
            unsigned int bad_fields = (n[2] != 0) | ((n[5] != 0) << 1) |
                ((n[7] != 0) << 2) | ((n[8] != 0) << 3) |
                ((n[9] != 0xffffffffu) << 4) | ((n[6] != 5) << 5) |
                ((finish != base + 0x400000 && finish != base + 0x800000) << 6);
            if (trace) trace[7] = bad_fields;
            if (bad_fields) {
                answer = 0; reason = 5; goto done;
            }
            *reserved_bytes = finish - base;
            if (!growth_ram(c, next, 0x2c)) goto done;
            volatile unsigned int* following = (volatile unsigned int*)next;
            if (following[4] != node || following[0] < finish || following[0] > end) goto done;
            if (trace) trace[6] = following[0];
            if (finish == base + 0x800000) { answer = 1; reason = 8; goto done; }
            if (following[0] < base + 0x800000) { answer = 0; reason = 6; goto done; }
            unsigned int wanted = base + 0x800000 - start;
            unsigned int result = ((unsigned int (*)(unsigned int, unsigned int, unsigned int))
                                   c->reserve_fixed)(area, start, wanted);
            if (result != node || n[0] != start || n[1] != wanted || n[3] != next ||
                following[4] != node || h[0] != base || h[4] != committed_end) {
                answer = -2; reason = 10; goto done;
            }
            *reserved_bytes = 0x800000; answer = 1; reason = 8; goto done;
        }
        if (start >= committed_end) { answer = 0; reason = 7; goto done; }
        previous = node; previous_end = finish; node = next;
    }
done:
    if (trace) trace[0] = reason;
    ((void (*)(unsigned int))c->unlock)(0x11a);
    return answer;
}
#endif
