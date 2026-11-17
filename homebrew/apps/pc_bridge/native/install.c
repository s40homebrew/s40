/* SPDX-License-Identifier: MIT */
#include <stddef.h>

#include "control.h"
#include "firmware/bindings.h"
#include "resident_image.h"
#include "s40/runtime.h"

static volatile uint32_t* panel_record(const struct pb_services* services, uint32_t panel) {
    unsigned object_id = *(volatile uint16_t*)(panel + 8);
    unsigned device = *(volatile uint8_t*)services->device;
    unsigned task = PB_FN(unsigned (*)(unsigned), services->object_task)((device << 8) | object_id);
    if (task >= 64)
        return 0;
    uint32_t ctx = *(volatile uint32_t*)(services->task_contexts + 4 * task);
    if (!pb_readable(services, ctx, 24) || (ctx & 3))
        return 0;
    unsigned index = *(volatile uint16_t*)(panel + 10);
    unsigned count = *(volatile uint16_t*)(ctx + 20);
    uint32_t records = *(volatile uint32_t*)(ctx + 4);
    if (!count || count > 1024 || index >= count || !pb_ram(services, records, count * 16) ||
        (records & 3))
        return 0;
    volatile uint32_t* record = (volatile uint32_t*)(uintptr_t)(records + index * 16);
    if (record[0] != panel || *(volatile uint16_t*)((uintptr_t)record + 12) != object_id ||
        !*(volatile uint8_t*)((uintptr_t)record + 14))
        return 0;
    return record;
}

int s40_app_main(void) {
    const uint32_t* api = s40_result + FW_API_DESCRIPTOR;
    if (api[API_BRIDGE_ABI] != FW_BRIDGE_ABI_RM443)
        s40_error("PC Suite bridge services are unavailable");
    const struct pb_services resolved = {
            api[API_BRIDGE_PANEL_POINTER],  api[API_BRIDGE_ORIGINAL_CLASS],
            api[API_BRIDGE_ORIGINAL_RX],    api[API_BRIDGE_CLASS_SIZES],
            api[API_BRIDGE_TASK_CONTEXTS],  api[API_BRIDGE_DEVICE],
            api[API_BRIDGE_OBJECT_TASK],    api[API_FW_OS_BLOCK_ALLOC_NO_WAIT],
            api[API_FW_OS_BLOCK_DEALLOC],   api[API_BRIDGE_MESSAGE_NEW],
            api[API_BRIDGE_MESSAGE_DELETE], api[API_BRIDGE_CALL_CLASS3],
            api[API_BRIDGE_RAM_START],      api[API_BRIDGE_RAM_END],
            api[API_BRIDGE_ROM_START],      api[API_BRIDGE_ROM_END]};
    const struct pb_services* services = &resolved;
    uint32_t panel = *(volatile uint32_t*)services->panel_pointer;
    if ((panel & 3) || !pb_ram(services, panel, PB_PANEL_BYTES))
        s40_error("Panel object is unavailable");
    volatile uint32_t* record = panel_record(services, panel);
    if (!record)
        s40_error("Panel receive record failed validation");
    uint32_t cls = *(volatile uint32_t*)panel;
    if (cls != services->original_class) {
        if ((cls & 3) || !pb_ram(services, cls, sizeof(struct pb_class)))
            s40_error("Unsupported Panel class; reboot before installing");
        struct pb_class* c = (struct pb_class*)(uintptr_t)cls;
        if (c->self != cls || c->parent != services->original_class || c->magic != PB_MAGIC ||
            !pb_ram(services, c->control, sizeof(struct pb_control)) || (c->control & 3))
            s40_error("Unknown Panel hook; reboot before installing");
        struct pb_control* p = (struct pb_control*)(uintptr_t)c->control;
        if (p->magic != PB_MAGIC || p->version != PB_CONTROL_VERSION || &p->panel_class != c ||
            p->panel != panel || p->record != (uint32_t)(uintptr_t)record || record[2] != p->hook)
            s40_error("Bridge state is inconsistent; reboot before installing");
        s40_result[5] = 2;
        s40_result[8] = PB_MAGIC;
        return 0;
    }
    if (record[2] != services->original_receiver)
        s40_error("This firmware needs a PC Suite bridge adapter");
    unsigned char* base = fw_os_block_alloc_no_wait(PB_IMAGE_MEMORY);
    if (!base)
        s40_error("Resident bridge allocation failed");
    for (unsigned i = 0; i < PB_IMAGE_MEMORY; i++)
        base[i] = 0;
    for (unsigned i = 0; i < sizeof(pb_image); i++)
        base[i] = pb_image[i];
    for (unsigned i = 0; i < sizeof(pb_relocations) / sizeof(pb_relocations[0]); i++)
        *(uint32_t*)(base + pb_relocations[i]) += (uint32_t)(uintptr_t)base;
    pb_sync_fn sync = (pb_sync_fn)(uintptr_t)s40_result[FW_API_DESCRIPTOR + API_CACHE_SYNC];
    sync(base, PB_IMAGE_MEMORY);
    struct pb_control* p =
            ((struct pb_control * (*)(const struct pb_services*))(base + PB_IMAGE_ENTRY))(services);
    if ((unsigned char*)p < base || (unsigned char*)(p + 1) > base + PB_IMAGE_MEMORY ||
        p->magic != PB_MAGIC) {
        fw_os_block_dealloc(base);
        s40_error("Resident bridge initialization failed");
    }
    p->base = (uint32_t)(uintptr_t)base;
    p->bytes = PB_IMAGE_MEMORY;
    p->panel = panel;
    p->record = (uint32_t)(uintptr_t)record;
    p->sync = sync;
    p->boot_cookie = p->base ^ fw_os_current_time_read_in_ms();
    /* Publish only after relocation/cache sync/initialization. Existing callbacks
     * have no references to this image. Aligned ARM pointer stores are atomic. */
    __asm__ volatile("" ::: "memory");
    *(volatile uint32_t*)panel = (uint32_t)(uintptr_t)&p->panel_class;
    record[2] = p->hook;
    s40_result[5] = 1;
    s40_result[8] = PB_MAGIC;
    return 0;
}
