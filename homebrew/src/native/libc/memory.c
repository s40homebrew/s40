/* SPDX-License-Identifier: MIT */
/* Session-owned OS heap allocations. No Java heap or process-wide allocator. */
#include <errno.h>
#include <limits.h>
#include <stdlib.h>
#include <string.h>
#include "firmware/bindings.h"
#include "internal.h"

/* This prefix belongs to the SDK, never to the firmware allocator. On ARM it
 * is 16 bytes and preserves the OS heap's 8-byte payload alignment. */
typedef struct block {
    struct block *prev, *next;
    size_t size;
    unsigned pad;
} block;
#if UINTPTR_MAX == UINT32_MAX
typedef char s40_heap_prefix_size[(sizeof(block) == 16) ? 1 : -1];
typedef char s40_heap_size_offset[(offsetof(block, size) == 8) ? 1 : -1];
#endif
static block* blocks;
void* malloc(size_t n) {
    if (n > UINT_MAX - sizeof(block)) {
        errno = ENOMEM;
        return NULL;
    }
    block* b = fw_os_block_alloc_no_wait(sizeof(block) + (n ? n : 1));
    if (!b) {
        errno = ENOMEM;
        return NULL;
    }
    b->size = n;
    b->prev = NULL;
    b->next = blocks;
    if (blocks)
        blocks->prev = b;
    blocks = b;
    return b + 1;
}
void free(void* p) {
    if (!p)
        return;
    block* b = (block*)p - 1;
    if (b->prev)
        b->prev->next = b->next;
    else
        blocks = b->next;
    if (b->next)
        b->next->prev = b->prev;
    fw_os_block_dealloc(b);
}
void* calloc(size_t n, size_t s) {
    if (s && n > UINT_MAX / s) {
        errno = ENOMEM;
        return NULL;
    }
    void* p = malloc(n * s);
    if (p)
        memset(p, 0, n * s);
    return p;
}
void* realloc(void* p, size_t n) {
    if (!p)
        return malloc(n);
    if (!n) {
        free(p);
        return NULL;
    }
    block* b = (block*)p - 1;
    if (n <= b->size)
        return p;
    void* q = malloc(n);
    if (q) {
        memcpy(q, p, b->size);
        free(p);
    }
    return q;
}
void s40_memory_cleanup(void) {
    while (blocks)
        free(blocks + 1);
}
