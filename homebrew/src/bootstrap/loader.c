/* SPDX-License-Identifier: MIT */
/* Position-independent loader, initially entered through a Java int[] carrier
 * and then copied to OS-owned storage. Enter the application only after all
 * chunks and relocations have been checked and KVM metadata restored.
 */
typedef unsigned int u32;
#include "firmware_api.h"
#include "native_limits.h"
/* loader.S lays out these five callable services in this exact order. */
typedef struct {
    void (*kni_param)(unsigned, void*);
    void (*kni_return)(void);
    void* (*allocate)(u32);
    void (*release)(void*);
    void (*synchronize)(void*, u32);
} loader_services;
/* C function-pointer layout must match the assembly's patched word table. */
#define CHECK_LOADER_SERVICE(field, slot)                                              \
    _Static_assert(__builtin_offsetof(loader_services, field) == FW_WORD_OFFSET(slot), \
                   "loader service ABI: " #field)
CHECK_LOADER_SERVICE(kni_param, FW_LOADER_API_KNI_PARAM);
CHECK_LOADER_SERVICE(kni_return, FW_LOADER_API_KNI_RETURN);
CHECK_LOADER_SERVICE(allocate, FW_LOADER_API_ALLOCATE);
CHECK_LOADER_SERVICE(release, FW_LOADER_API_FREE);
CHECK_LOADER_SERVICE(synchronize, FW_LOADER_API_CACHE_SYNC);
_Static_assert(sizeof(loader_services) == FW_WORD_OFFSET(FW_LOADER_API_COUNT),
               "loader service table size");
#undef CHECK_LOADER_SERVICE
extern void on_native_stack(void*, void*, u32*);
void loader_command(u32* p, const loader_services* api) {
    u32 op = p[FW_RESULT_COMMAND_WORD];
    unsigned char* base = (unsigned char*)p[FW_RESULT_IMAGE_WORD];
    p[FW_RESULT_MAGIC_WORD] = FW_RESULT_MAGIC;
    p[FW_RESULT_STATUS_WORD] = FW_STATUS_OK;
    if (op == FW_COMMAND_INSTALL) {
        /* Move the callable loader out of Java's heap before resource I/O can
         * allocate or collect. Internal branches/literals are PC-relative. */
        if (!p[FW_RESULT_METHOD_SLOT_WORD] || !p[FW_RESULT_LOADER_SOURCE_WORD] ||
            !p[FW_RESULT_LOADER_BYTES_WORD] ||
            p[FW_RESULT_LOADER_BYTES_WORD] > FW_LOADER_MAX_BYTES ||
            p[FW_RESULT_RESIDENT_LOADER_WORD]) {
            p[FW_RESULT_STATUS_WORD] = FW_STATUS_BOOTSTRAP_BOUNDS;
            return;
        }
        unsigned char* self = api->allocate(p[FW_RESULT_LOADER_BYTES_WORD]);
        if (!self) {
            p[FW_RESULT_STATUS_WORD] = FW_STATUS_ALLOCATION_FAILED;
            return;
        }
        for (u32 i = 0; i < p[FW_RESULT_LOADER_BYTES_WORD]; i++)
            self[i] = ((unsigned char*)p[FW_RESULT_LOADER_SOURCE_WORD])[i];
        api->synchronize(self, p[FW_RESULT_LOADER_BYTES_WORD]);
        p[FW_RESULT_RESIDENT_LOADER_WORD] = (u32)self;
        *(volatile u32*)p[FW_RESULT_METHOD_SLOT_WORD] = (u32)self;
        return;
    }
    if (op == FW_COMMAND_ALLOCATE) {
        if (base || !p[FW_RESULT_ALLOCATION_BYTES_WORD] ||
            p[FW_RESULT_ALLOCATION_BYTES_WORD] > S40_NATIVE_ALLOCATION_LIMIT) {
            p[FW_RESULT_STATUS_WORD] = FW_STATUS_ALLOCATION_BOUNDS;
            return;
        }
        base = api->allocate(p[FW_RESULT_ALLOCATION_BYTES_WORD]);
        if (!base) {
            p[FW_RESULT_STATUS_WORD] = FW_STATUS_ALLOCATION_FAILED;
            return;
        }
        p[FW_RESULT_IMAGE_WORD] = (u32)base;
        for (u32 i = 0; i < p[FW_RESULT_ALLOCATION_BYTES_WORD]; i++)
            base[i] = 0;
        return;
    }
    if (op == FW_COMMAND_TRANSFER) {
        if (!base || p[FW_RESULT_TRANSFER_BYTES_WORD] > FW_TRANSFER_BYTES ||
            p[FW_RESULT_TRANSFER_OFFSET_WORD] > p[FW_RESULT_ALLOCATION_BYTES_WORD] ||
            p[FW_RESULT_TRANSFER_BYTES_WORD] >
                    p[FW_RESULT_ALLOCATION_BYTES_WORD] - p[FW_RESULT_TRANSFER_OFFSET_WORD]) {
            p[FW_RESULT_STATUS_WORD] = FW_STATUS_TRANSFER_BOUNDS;
            return;
        }
        unsigned char* src = (unsigned char*)(p + FW_RESULT_TRANSFER_WORD);
        for (u32 i = 0; i < p[FW_RESULT_TRANSFER_BYTES_WORD]; i++)
            base[p[FW_RESULT_TRANSFER_OFFSET_WORD] + i] = src[i];
        return;
    }
    if (op == FW_COMMAND_RUN || op == FW_COMMAND_ABORT) {
        /* Clear our own KVM binding while still running: the dispatcher has
         * already loaded its target, so normal return remains valid. */
        if (p[FW_RESULT_METHOD_SLOT_WORD]) {
            *(volatile u32*)p[FW_RESULT_METHOD_SLOT_WORD] = 0;
            p[FW_RESULT_METHOD_SLOT_WORD] = 0;
        }
        if (op == FW_COMMAND_RUN && base) {
            u32 image = p[FW_RESULT_IMAGE_BYTES_WORD], n = p[FW_RESULT_RELOCATION_COUNT_WORD],
                bindings = p[FW_RESULT_BINDING_COUNT_WORD];
            if (image < FW_WORD_BYTES || (image & (FW_WORD_BYTES - 1)) ||
                image > p[FW_RESULT_ALLOCATION_BYTES_WORD] ||
                n > (p[FW_RESULT_ALLOCATION_BYTES_WORD] - image) / FW_RELOCATION_BYTES ||
                bindings > (p[FW_RESULT_ALLOCATION_BYTES_WORD] - image - FW_WORD_BYTES * n) /
                                   FW_BINDING_BYTES ||
                p[FW_RESULT_ALLOCATION_BYTES_WORD] - image - FW_WORD_BYTES * n -
                                FW_BINDING_BYTES * bindings <
                        FW_IMAGE_STACK_BYTES ||
                p[FW_RESULT_ENTRY_OFFSET_WORD] >= image)
                p[FW_RESULT_STATUS_WORD] = FW_STATUS_IMAGE_BOUNDS;
            else {
                u32* rel = (u32*)(base + image);
                for (u32 i = 0; i < n; i++) {
                    u32 off = rel[i];
                    if ((off & (FW_WORD_BYTES - 1)) || off > image - FW_WORD_BYTES) {
                        p[FW_RESULT_STATUS_WORD] = FW_STATUS_RELOCATION;
                        break;
                    }
                    *(u32*)(base + off) += (u32)base;
                }
                /* Bound import slots contain zero until this load. API IDs are
                 * validated before dereferencing the resolved descriptor table.
                 * Code/literal changes are synchronized together below. */
                u32* bind = rel + n;
                for (u32 i = 0; !p[FW_RESULT_STATUS_WORD] && i < bindings; i++) {
                    u32 off = bind[2 * i], id = bind[2 * i + 1];
                    /* The legacy foreground backend has no master-display
                     * services. Only its four unused slots may remain NULL;
                     * every bootstrap/file/event binding is still required. */
                    int optional_display =
                            p[FW_CANVAS_LAYOUT_WORD] == FW_CANVAS_LEGACY && p[FW_UI_REFRESH_WORD] &&
                            (id == API_FW_GDI_DISPLAY_MASTER_RESERVE_ENABLE ||
                             id == API_FW_GDI_DISPLAY_MASTER_RESERVE_DISABLE ||
                             id == API_FW_GDI_DISPLAY_MASTER_UPDATE || id == API_FW_LCD_OWNER);
                    if ((off & (FW_WORD_BYTES - 1)) || off > image - FW_WORD_BYTES ||
                        id >= FW_API_COUNT || (!p[FW_API_DESCRIPTOR + id] && !optional_display) ||
                        *(u32*)(base + off)) {
                        p[FW_RESULT_STATUS_WORD] = FW_STATUS_IMPORT;
                        break;
                    }
                    *(u32*)(base + off) = p[FW_API_DESCRIPTOR + id];
                }
                if (!p[FW_RESULT_STATUS_WORD]) {
                    api->synchronize(base, image);
                    on_native_stack(base + p[FW_RESULT_ENTRY_OFFSET_WORD],
                                    base + p[FW_RESULT_ALLOCATION_BYTES_WORD], p);
                }
            }
        }
        if (base)
            api->release(base);
        p[FW_RESULT_IMAGE_WORD] = 0;
        return;
    }
    p[FW_RESULT_STATUS_WORD] = FW_STATUS_COMMAND;
}
