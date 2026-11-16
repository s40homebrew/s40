/* SPDX-License-Identifier: MIT */
#ifndef PB_CONTROL_H
#define PB_CONTROL_H
#include <stdint.h>

#include "rm443_1012.h"

#define PB_MAGIC 0x53343042u
#define PB_VERSION 1u
#define PB_CHUNK 1024u
#define PB_SLOTS 16u
#define PB_MAX_ALLOC 0x100000u

#define PB_FN(type, address) ((type)(uintptr_t)(address))
typedef int (*pb_receiver)(void*, unsigned, void*, unsigned char*, unsigned);
typedef void (*pb_sync_fn)(void*, unsigned);
struct pb_class {
    uint32_t self, parent, methods, sizes, name, magic, control;
};
struct pb_allocation {
    uint32_t address, size;
};
struct pb_control {
    uint32_t magic, version, base, bytes;
    uint32_t hook, panel, record;
    uint32_t original_class, original_receiver;
    pb_sync_fn sync;
    uint32_t boot_cookie, calls;
    struct pb_class panel_class;
    uint32_t methods[4];
    struct pb_allocation allocations[PB_SLOTS];
};
static inline int pb_ram(uint32_t p, unsigned n) {
    return n <= PB_RAM_END - PB_RAM_START && p >= PB_RAM_START && p <= PB_RAM_END - n;
}
static inline int pb_readable(uint32_t p, unsigned n) {
    return pb_ram(p, n) ||
           (n <= PB_ROM_END - PB_ROM_START && p >= PB_ROM_START && p <= PB_ROM_END - n);
}
static inline void* pb_alloc(unsigned n) { return PB_FN(void* (*)(unsigned), PB_ALLOC)(n); }
static inline void pb_free(void* p) { PB_FN(void (*)(void*), PB_FREE)(p); }
static inline unsigned char* pb_message(unsigned n) {
    return PB_FN(unsigned char* (*)(unsigned), PB_MESSAGE_NEW)(n);
}
static inline void pb_delete(void* p) { PB_FN(void (*)(void*), PB_MESSAGE_DELETE)(p); }
void pb_call(uint32_t address, const uint32_t* arguments, uint32_t* results);
#endif
