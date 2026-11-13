/* SPDX-License-Identifier: MIT */
/* Small position-independent loader, copied into the same int[] carrier as
 * the already emulator-tested OS-info payload. Never calls the application until
 * all chunks and relocations have been checked and KVM metadata restored.
 */
typedef unsigned int u32;
#include "firmware_api.h"
#include "native_limits.h"
#define CALL(type, index) ((type)api[index])
#define MAGIC 0x444F4F4D
extern void on_native_stack(void*, void*, u32*);
void loader_command(u32* p, const u32* api) {
    u32 op = p[1];
    unsigned char* base = (unsigned char*)p[2];
    p[0] = MAGIC;
    p[3] = 0;
    if (op == 0) {
        /* Move the callable loader out of Java's heap before resource I/O can
         * allocate or collect. Internal branches/literals are PC-relative. */
        if (!p[80] || !p[82] || !p[83] || p[83] > 3072 || p[84]) {
            p[3] = 20;
            return;
        }
        unsigned char* self = CALL(void* (*)(u32), 2)(p[83]);
        if (!self) {
            p[3] = 22;
            return;
        }
        for (u32 i = 0; i < p[83]; i++)
            self[i] = ((unsigned char*)p[82])[i];
        CALL(void (*)(void*, u32), 4)(self, p[83]);
        p[84] = (u32)self;
        *(volatile u32*)p[80] = (u32)self;
        return;
    }
    if (op == 1) {
        if (base || !p[11] || p[11] > S40_NATIVE_ALLOCATION_LIMIT) {
            p[3] = 21;
            return;
        }
        base = CALL(void* (*)(u32), 2)(p[11]);
        if (!base) {
            p[3] = 22;
            return;
        }
        p[2] = (u32)base;
        for (u32 i = 0; i < p[11]; i++)
            base[i] = 0;
        return;
    }
    if (op == 2) {
        if (!base || p[13] > 4096 || p[12] > p[11] || p[13] > p[11] - p[12]) {
            p[3] = 23;
            return;
        }
        unsigned char* src = (unsigned char*)(p + 96);
        for (u32 i = 0; i < p[13]; i++)
            base[p[12] + i] = src[i];
        return;
    }
    if (op == 3 || op == 4) {
        /* Clear our own KVM binding while still running: the dispatcher has
         * already loaded its target, so normal return remains valid. */
        if (p[80]) {
            *(volatile u32*)p[80] = 0;
            p[80] = 0;
        }
        if (op == 3 && base) {
            u32 image = p[12], n = p[13], bindings = p[15];
            if (image < 4 || (image & 3) || image > p[11] || n > (p[11] - image) / 4 ||
                bindings > (p[11] - image - 4 * n) / 8 ||
                p[11] - image - 4 * n - 8 * bindings < 196608 || p[14] >= image)
                p[3] = 24;
            else {
                u32* rel = (u32*)(base + image);
                for (u32 i = 0; i < n; i++) {
                    u32 off = rel[i];
                    if ((off & 3) || off > image - 4) {
                        p[3] = 25;
                        break;
                    }
                    *(u32*)(base + off) += (u32)base;
                }
                /* Bound import slots contain zero until this load. API IDs are
                 * validated before dereferencing the resolved descriptor table.
                 * Code/literal changes are synchronized together below. */
                u32* bind = rel + n;
                for (u32 i = 0; !p[3] && i < bindings; i++) {
                    u32 off = bind[2 * i], id = bind[2 * i + 1];
                    if ((off & 3) || off > image - 4 || id >= FW_API_COUNT ||
                        !p[FW_API_DESCRIPTOR + id] || *(u32*)(base + off)) {
                        p[3] = 27;
                        break;
                    }
                    *(u32*)(base + off) = p[FW_API_DESCRIPTOR + id];
                }
                if (!p[3]) {
                    CALL(void (*)(void*, u32), 4)(base, image);
                    on_native_stack(base + p[14], base + p[11], p);
                }
            }
        }
        if (base)
            CALL(void (*)(void*), 3)(base);
        p[2] = 0;
        return;
    }
    p[3] = 26;
}
