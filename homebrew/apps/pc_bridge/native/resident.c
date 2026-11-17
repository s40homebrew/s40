/* SPDX-License-Identifier: MIT */
/* All pointers retained here belong to firmware or this OS heap image. */
#include "control.h"

static struct pb_control control;

static uint32_t le32(const unsigned char* p) {
    return (uint32_t)p[0] | (uint32_t)p[1] << 8 | (uint32_t)p[2] << 16 | (uint32_t)p[3] << 24;
}
static void put32(unsigned char* p, uint32_t v) {
    for (unsigned i = 0; i < 4; i++)
        p[i] = v >> (i * 8);
}
void* memcpy(void* d, const void* s, unsigned n) {
    unsigned char* out = d;
    const unsigned char* in = s;
    while (n--)
        *out++ = *in++;
    return d;
}
void* memset(void* d, int v, unsigned n) {
    unsigned char* out = d;
    while (n--)
        *out++ = v;
    return d;
}
void __aeabi_memcpy(void* d, const void* s, unsigned n) { memcpy(d, s, n); }
void __aeabi_memcpy4(void* d, const void* s, unsigned n) { memcpy(d, s, n); }
void __aeabi_memclr(void* d, unsigned n) { memset(d, 0, n); }
void __aeabi_memclr4(void* d, unsigned n) { memset(d, 0, n); }

static unsigned dispatch(unsigned op, const unsigned char* body, unsigned n, unsigned char* out,
                         unsigned* count) {
    uint32_t a = n >= 4 ? le32(body) : 0;
    uint32_t b = n >= 8 ? le32(body + 4) : 0;
    *count = 0;
    switch (op) {
        case 0: {
            if (n)
                return 1;
            const uint32_t info[8] = {PB_VERSION,
                                      0xff,
                                      control.base,
                                      control.bytes,
                                      control.services.ram_start,
                                      control.services.ram_end,
                                      PB_CHUNK,
                                      control.boot_cookie};
            for (unsigned i = 0; i < 8; i++)
                put32(out + i * 4, info[i]);
            *count = 32;
            return 0;
        }
        case 1:
            if (n != 8 || b > PB_CHUNK)
                return 1;
            if (!pb_readable(&control.services, a, b))
                return 3;
            memcpy(out, (const void*)(uintptr_t)a, b);
            *count = b;
            return 0;
        case 2:
            if (n < 8 || b > PB_CHUNK || n != 8 + b)
                return 1;
            if (!pb_ram(&control.services, a, b))
                return 3;
            memcpy((void*)(uintptr_t)a, body + 8, b);
            return 0;
        case 3: {
            if (n != 20)
                return 1;
            if ((!pb_readable(&control.services, a & ~1u, 4)) || (!(a & 1u) && (a & 3u)))
                return 3;
            uint32_t args[4], results[4];
            for (unsigned i = 0; i < 4; i++)
                args[i] = le32(body + 4 + i * 4);
            pb_call(a, args, results);
            for (unsigned i = 0; i < 4; i++)
                put32(out + i * 4, results[i]);
            *count = 16;
            return 0;
        }
        case 4:
            if (n != 4 || !a || a > PB_MAX_ALLOC)
                return 1;
            for (unsigned i = 0; i < PB_SLOTS; i++) {
                if (control.allocations[i].address)
                    continue;
                void* p = pb_alloc(&control.services, a);
                if (!p)
                    return 4;
                memset(p, 0, a);
                control.allocations[i].address = (uint32_t)(uintptr_t)p;
                control.allocations[i].size = a;
                put32(out, (uint32_t)(uintptr_t)p);
                *count = 4;
                return 0;
            }
            return 5;
        case 5:
            if (n != 4)
                return 1;
            for (unsigned i = 0; i < PB_SLOTS; i++) {
                if (!a || control.allocations[i].address != a)
                    continue;
                pb_free(&control.services, (void*)(uintptr_t)a);
                control.allocations[i].address = 0;
                control.allocations[i].size = 0;
                return 0;
            }
            return 5;
        case 6:
            if (n != 8 || !b)
                return 1;
            if (!pb_ram(&control.services, a, b))
                return 3;
            control.sync((void*)(uintptr_t)a, b);
            return 0;
        case 7:
            if (n)
                return 1;
            for (unsigned i = 0; i < PB_SLOTS; i++) {
                if (!control.allocations[i].address)
                    continue;
                put32(out + *count, control.allocations[i].address);
                put32(out + *count + 4, control.allocations[i].size);
                *count += 8;
            }
            return 0;
        case 8: {
            if (n)
                return 1;
            for (unsigned i = 0; i < PB_SLOTS; i++)
                if (control.allocations[i].address)
                    return 6;
            volatile uint32_t* panel = (volatile uint32_t*)(uintptr_t)control.panel;
            volatile uint32_t* record = (volatile uint32_t*)(uintptr_t)control.record;
            if (!pb_ram(&control.services, control.panel, PB_PANEL_BYTES) ||
                !pb_ram(&control.services, control.record, 16) || (control.panel & 3) ||
                (control.record & 3) || *panel != (uint32_t)(uintptr_t)&control.panel_class ||
                record[0] != control.panel || record[2] != control.hook)
                return 6;
            /* Never free this image: this callback is still executing. Forwarders
             * already selected by another task can also still be on its stack. */
            record[2] = control.original_receiver;
            *panel = control.original_class;
            return 0;
        }
        default:
            return 2;
    }
}

static int panel_receive(void* object, unsigned selector, void* source, unsigned char* message,
                         unsigned id) {
    /* CM_ISIMes' private prefix stores full wire payload length, including the
     * two object bytes. Its returned pointer starts at transaction, not objects. */
    unsigned length = message ? *(const uint16_t*)(message - 4) : 0;
    if (id != 0x0ee0 || !message || length < 16 || message[1] != 0xe0 || message[2] != 'S' ||
        message[3] != '4' || message[4] != '0' || message[5] != 'B')
        return ((pb_receiver)(uintptr_t)control.original_receiver)(object, selector, source,
                                                                   message, id);
    unsigned op = message[7];
    unsigned n = ((unsigned)message[8] << 8) | message[9];
    unsigned char* reply = pb_message(&control.services, 18 + PB_CHUNK);
    if (!reply) {
        pb_delete(&control.services, message);
        return 0;
    }
    reply[0] = message[0];
    reply[1] = 0xe1;
    memcpy(reply + 2, message + 2, 12);
    reply[6] = PB_VERSION;
    unsigned count = 0;
    unsigned status;
    if (message[6] != PB_VERSION)
        status = 2;
    else if (n > PB_CHUNK + 8 || length != 16 + n)
        status = 1;
    else
        status = dispatch(op, message + 14, n, reply + 18, &count);
    put32(reply + 14, status);
    reply[8] = (count + 4) >> 8;
    reply[9] = count + 4;
    /* Trim allocated CM message to actual wire size before handing ownership
     * to the sender. This is the same prefix used by CM_ISIMes_New. */
    *(uint16_t*)(reply - 4) = 20 + count;
    control.calls++;
    PB_FN(unsigned (*)(void*, unsigned, void*, unsigned, void*), control.services.call_class3)(
            source, 0xc011, object, 14, reply);
    pb_delete(&control.services, message);
    return 0;
}

__attribute__((visibility("default"))) struct pb_control* resident_entry(
        const struct pb_services* services) {
    control.services = *services;
    control.magic = PB_MAGIC;
    control.version = PB_CONTROL_VERSION;
    control.original_class = control.services.original_class;
    control.original_receiver = control.services.original_receiver;
    control.hook = (uint32_t)(uintptr_t)panel_receive;
    control.methods[0] = 0xc009;
    control.methods[1] = control.hook;
    control.panel_class.self = (uint32_t)(uintptr_t)&control.panel_class;
    control.panel_class.parent = control.services.original_class;
    control.panel_class.methods = (uint32_t)(uintptr_t)control.methods;
    control.panel_class.sizes = control.services.class_sizes;
    control.panel_class.name = (uint32_t)(uintptr_t)"PC Suite Native Bridge";
    control.panel_class.magic = PB_MAGIC;
    control.panel_class.control = (uint32_t)(uintptr_t)&control;
    return &control;
}
