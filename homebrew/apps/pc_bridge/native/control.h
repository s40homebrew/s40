/* SPDX-License-Identifier: MIT */
#ifndef PB_CONTROL_H
#define PB_CONTROL_H
#include <stdint.h>

#define PB_MAGIC 0x53343042u
#define PB_VERSION 1u
#define PB_CONTROL_VERSION 2u
#define PB_PANEL_BYTES 284u
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
/* Copied into the resident allocation before publication. No pointer here
 * refers to a Java descriptor, launcher image or caller-owned record. */
struct pb_services {
    uint32_t panel_pointer, original_class, original_receiver, class_sizes;
    uint32_t task_contexts, device, object_task;
    uint32_t allocate, release, message_new, message_delete, call_class3;
    uint32_t ram_start, ram_end, rom_start, rom_end;
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
    struct pb_services services;
};
static inline int pb_ram(const struct pb_services* s, uint32_t p, unsigned n) {
    return n <= s->ram_end - s->ram_start && p >= s->ram_start && p <= s->ram_end - n;
}
static inline int pb_readable(const struct pb_services* s, uint32_t p, unsigned n) {
    return pb_ram(s, p, n) ||
           (n <= s->rom_end - s->rom_start && p >= s->rom_start && p <= s->rom_end - n);
}
static inline void* pb_alloc(const struct pb_services* s, unsigned n) {
    return PB_FN(void* (*)(unsigned), s->allocate)(n);
}
static inline void pb_free(const struct pb_services* s, void* p) {
    PB_FN(void (*)(void*), s->release)(p);
}
static inline unsigned char* pb_message(const struct pb_services* s, unsigned n) {
    return PB_FN(unsigned char* (*)(unsigned), s->message_new)(n);
}
static inline void pb_delete(const struct pb_services* s, void* p) {
    PB_FN(void (*)(void*), s->message_delete)(p);
}
void pb_call(uint32_t address, const uint32_t* arguments, uint32_t* results);
#endif
