/* SPDX-License-Identifier: MIT */
/* Fixed-arena ARM926 growth. The old pool stays intact until the firmware's
 * queued VM reset or scheduler reset request runs on its own task. All code changes use the
 * existing flash-to-RAM shadow writer; no resident callback points into Java storage. */
#define heap_booster_main heap_booster_quota_main
#include "patch_arm926.c"
#undef heap_booster_main

#define FIXED_MAGIC 0x48424631u
struct fixed_owner {
    u32 magic, raw, base, bytes, provider, config, heap_state, reset;
    u32 stock_base, stock_bytes, policy, version, reserved[4];
    u32 reset_stub[13];
};
/* These are instruction/VM-layout contracts, selected by the checked supplier
 * and configuration code below. Firmware versions and model names are not
 * inputs. The older arena leaves enough room for a 3/4 MiB app quota without
 * requesting the 9 MiB reservation used by the later fixed-arena layout. */
struct fixed_layout {
    u32 stock_bytes, grown_bytes, shift, state_size, state_base;
    u16 provider_stock, provider_grown, config_stock, config_grown;
};

static u32 fixed_allocation_bytes(const struct fixed_layout* l) {
    return l->grown_bytes + sizeof(struct fixed_owner) + 15;
}

static void copy_words(u32* to, const volatile u32* from, u32 count) {
    for (u32 i = 0; i < count; i++)
        to[i] = from[i];
}
static void capture(u32 address, u32* words) {
    for (u32 i = 0; i < 8; i++)
        words[i] = 0;
    for (u32 i = 0; i < PATCH_HALFWORDS; i++)
        words[i / 2] |= (u32)((volatile u16*)address)[i] << ((i & 1) * 16);
}
static u32 change(volatile u32* r, u32 address, const u32* expected, const u32* desired,
                  u32 operation) {
    u32 t[RESULT_WORDS];
    copy_words(t, r, RESULT_WORDS);
    t[W_OPERATION] = operation;
    t[W_MIB] = 3; /* Generic 30-byte shadow transaction, not a quota request. */
    t[W_POOL_BYTES] = 3u << 20;
    t[W_SITE] = address;
    t[W_SHADOW] = t[W_SHADOW_STATE] = 0;
    copy_words(t + W_EXPECTED, expected, 8);
    copy_words(t + W_DESIRED, desired, 8);
    heap_booster_quota_main(t);
    return t[W_STATUS];
}
static int fixed_inputs(volatile u32* r, struct fixed_layout* l) {
    u32 p = r[F_PROVIDER], c = r[F_CONFIG_SITE];
    if (r[W_BACKEND] != ARM926_RAP || p < r[W_ROM_START] || p > r[W_ROM_END] - 30 || (p & 3) ||
        c < r[W_ROM_START] || c > r[W_ROM_END] - 30 || (c & 1) || r[F_BASE_LITERAL] != p + 24 ||
        r[F_SIZE_INSTRUCTION] != p + 4 || !ram(r, r[F_HEAP_STATE], 64) ||
        !ram(r, r[W_NATIVE_METHOD], 32) || !firmware_function(r, r[F_RESET_HELPER]) ||
        !firmware_function(r, r[W_ALLOC]) || !firmware_function(r, r[W_FREE]))
        return 0;
    volatile u16* code = (volatile u16*)p;
    if (code[0] != 0xb510 || code[3] != 0x4804 || code[7] != 0x4802 || code[8] != 0x6020 ||
        code[9] != 0xbd10 || code[10] != 0x4770 || code[11] != 0 ||
        call_target(p + 10, 1) != r[F_CLEAR_HELPER])
        return 0;
    if (code[1] == 0x000c && code[4] == 0x0489) {
        l->stock_bytes = 0x3c0000;
        l->grown_bytes = 9u << 20;
        l->shift = 18;
        l->state_size = 5;
        l->state_base = 6;
        l->provider_stock = 0x210f;
        l->provider_grown = 0x2124;
        l->config_stock = 0x220f;
        l->config_grown = 0x2224;
    } else if (code[1] == 0x1c0c && code[4] == 0x04c9) {
        l->stock_bytes = 0x280000;
        l->grown_bytes = 5u << 20;
        l->shift = 19;
        l->state_size = 1;
        l->state_base = 2;
        l->provider_stock = 0x2105;
        l->provider_grown = 0x210a;
        l->config_stock = 0x2205;
        l->config_grown = 0x220a;
    } else
        return 0;
    if (code[2] != l->provider_stock && code[2] != l->provider_grown)
        return 0;
    if (l->shift == 19) {
        // This layout restarts at the next Java scheduler pass. Discovery has
        // verified that the scheduler's getter controls its complete reset
        // path. Recheck the getter/flag edge before publishing a reset stub.
        u32 getter = r[F_RESET_HELPER] & ~1u;
        if (!(r[F_RESET_HELPER] & 1) || getter > r[W_ROM_END] - 6 || !ram(r, r[F_RESET_FLAG], 1))
            return 0;
        code = (volatile u16*)getter;
        u32 literal = ((getter + 4) & ~3u) + ((code[0] & 255) << 2);
        if ((code[0] & 0xff00) != 0x4800 || code[1] != 0x7a40 || code[2] != 0x4770 ||
            literal < r[W_ROM_START] || literal > r[W_ROM_END] - 4 ||
            *(volatile u32*)literal + 9 != r[F_RESET_FLAG])
            return 0;
    } else if (r[F_RESET_FLAG])
        return 0;
    code = (volatile u16*)c;
    return (code[0] == l->config_stock || code[0] == l->config_grown) &&
           code[1] == (u16)(0x0012 | (l->shift << 6)) && code[2] == 0x7008 && code[3] == 0x3914 &&
           code[4] == 0x600a;
}
static struct fixed_owner* fixed_owned(volatile u32* r, const struct fixed_layout* l, u32 base) {
    if (!ram(r, base - sizeof(struct fixed_owner), l->grown_bytes + sizeof(struct fixed_owner)))
        return 0;
    struct fixed_owner* o = (struct fixed_owner*)(base - sizeof(struct fixed_owner));
    if (o->magic != FIXED_MAGIC || o->base != base || o->bytes != l->grown_bytes ||
        !ram(r, o->raw, fixed_allocation_bytes(l)) || ((o->raw + sizeof(*o) + 15) & ~15u) != base ||
        o->version != 1 || o->provider != r[F_PROVIDER] || o->config != r[F_CONFIG_SITE] ||
        o->heap_state != r[F_HEAP_STATE] || o->reset != r[F_RESET_HELPER] ||
        o->reserved[1] != r[F_RESET_FLAG] || o->policy != r[W_SITE] ||
        o->stock_bytes != l->stock_bytes || !ram(r, o->stock_base, o->stock_bytes))
        return 0;
    return o;
}
void heap_booster_main(volatile u32* r) {
    struct fixed_layout layout;
    u32 operation = r[W_OPERATION];
    r[W_OPERATION] = OP_CHECK;
    heap_booster_quota_main(r);
    r[W_OPERATION] = operation;
    if (r[W_STATUS])
        return;
    if ((r[W_CPSR] & 0x80) != 0) {
        r[W_STATUS] = 216;
        return;
    }
    if (!fixed_inputs(r, &layout)) {
        r[W_STATUS] = 217;
        return;
    }
    u32 p = r[F_PROVIDER], c = r[F_CONFIG_SITE];
    u32 current_base = *(volatile u32*)r[F_BASE_LITERAL];
    u32 capacity = (u32)(*(volatile u16*)r[F_SIZE_INSTRUCTION] & 255) << layout.shift;
    volatile u32* heap = (volatile u32*)r[F_HEAP_STATE];
    if (heap[layout.state_base] != current_base || heap[layout.state_size] != capacity ||
        r[W_POOL_BYTES] != capacity) {
        r[W_STATUS] = 218;
        return;
    }
    r[W_GROWTH_KIND] = 2;
    r[W_HEAP_BASE] = current_base;
    r[W_RESERVATION] = capacity;
    r[W_GROWTH_LIMIT] = layout.grown_bytes - (1u << 20);
    if (operation == OP_APPLY && (r[W_MIB] < 3 || r[W_MIB] > (r[W_GROWTH_LIMIT] >> 20))) {
        r[W_STATUS] = 207;
        return;
    }
    if (capacity == layout.grown_bytes) {
        if (!fixed_owned(r, &layout, current_base) || *(volatile u16*)c != layout.config_grown) {
            r[W_STATUS] = 217;
            return;
        }
        r[W_GROWTH_STATE] = 2;
        heap_booster_quota_main(r);
        return;
    }
    if (capacity != layout.stock_bytes || *(volatile u16*)c != layout.config_stock) {
        r[W_STATUS] = 217;
        return;
    }
    u32 provider_old[8], provider_new[8], config_old[8], config_new[8];
    capture(p, provider_old);
    capture(c, config_old);
    copy_words(provider_new, provider_old, 8);
    copy_words(config_new, config_old, 8);
    u32 status = change(r, p, provider_old, provider_old, OP_CHECK);
    if (!status)
        status = change(r, c, config_old, config_old, OP_CHECK);
    if (status) {
        r[W_STATUS] = status;
        return;
    }
    if (operation != OP_APPLY || (r[W_MIB] << 20) <= capacity) {
        heap_booster_quota_main(r);
        return;
    }
    u32 alloc_bytes = fixed_allocation_bytes(&layout);
    u32 raw = ((u32 (*)(u32))r[W_ALLOC])(alloc_bytes);
    if (!raw || !ram(r, raw, alloc_bytes)) {
        if (raw)
            ((void (*)(u32))r[W_FREE])(raw);
        r[W_STATUS] = 219;
        return;
    }
    u32 base = (raw + sizeof(struct fixed_owner) + 15) & ~15u;
    struct fixed_owner* o = (struct fixed_owner*)(base - sizeof(*o));
    for (u32 i = 0; i < sizeof(*o) / 4; i++)
        ((u32*)o)[i] = 0;
    o->magic = FIXED_MAGIC;
    o->raw = raw;
    o->base = base;
    o->bytes = layout.grown_bytes;
    o->provider = p;
    o->config = c;
    o->heap_state = r[F_HEAP_STATE];
    o->reset = r[F_RESET_HELPER];
    o->stock_base = current_base;
    o->stock_bytes = capacity;
    o->policy = r[W_SITE];
    o->version = 1;
    o->reserved[1] = r[F_RESET_FLAG];
    // Invoked once only AFTER Java has restored its forged array, carrier and
    // payload. Unbind the native method before queuing reset. Keep its return
    // status in OS storage: another Java thread may compact objects even when
    // this thread allocates nothing between cleanup and the final call. ARM entry
    // preserves the same callee-saved registers as an ordinary KNI function.
    static const u32 reset_code[10] = {0xe92d4010, 0xe59f401c, 0xe3a00000, 0xe5840000, 0xe3a01000,
                                       0xe59fc014, 0xe12fff3c, 0xe59f4008, 0xe5840000, 0xe8bd8010};
    copy_words(o->reset_stub, reset_code, 10);
    o->reset_stub[10] = r[W_NATIVE_METHOD] + 4;
    o->reserved[0] = ~0u;
    o->reset_stub[11] = (u32)&o->reserved[0];
    o->reset_stub[12] = r[F_RESET_HELPER];
    if (r[F_RESET_FLAG]) {
        // Unbind, record success, then request the scheduler's normal reset.
        // There is no firmware call and no synchronous VM reset while this
        // native frame is still live. The flag is written only after Java has
        // restored all forged array, carrier and payload state.
        o->reset_stub[4] = 0xe59f4014; /* LDR r4, status literal. */
        o->reset_stub[5] = 0xe5840000;
        o->reset_stub[6] = 0xe59f4010; /* LDR r4, flag literal. */
        o->reset_stub[7] = 0xe5c40000;
        o->reset_stub[8] = 0xe1a00000;
        o->reset_stub[12] = r[F_RESET_FLAG];
    }
    cache_range((u32)o->reset_stub, sizeof(o->reset_stub));
    provider_new[1] = (provider_new[1] & 0xffff0000u) | layout.provider_grown;
    provider_new[6] = base;
    config_new[0] = (config_new[0] & 0xffff0000u) | layout.config_grown;
    u32 changed = 0;
    status = change(r, p, provider_old, provider_new, OP_APPLY);
    if (!status) {
        changed = 1;
        status = change(r, c, config_old, config_new, OP_APPLY);
    }
    if (!status) {
        changed = 2;
        r[W_POOL_BYTES] = layout.grown_bytes;
        heap_booster_quota_main(r);
        status = r[W_STATUS];
        r[W_POOL_BYTES] = capacity;
    }
    if (!status) {
        r[W_GROWTH_STATE] = 1;
        r[F_RESET_STUB] = (u32)o->reset_stub;
        r[W_STATUS] = 0;
        return;
    }
    int rollback = status != 208;
    if (changed >= 2 && change(r, c, config_new, config_old, OP_RESTORE))
        rollback = 0;
    if (changed >= 1 && change(r, p, provider_new, provider_old, OP_RESTORE))
        rollback = 0;
    if (rollback) {
        o->magic = 0;
        ((void (*)(u32))r[W_FREE])(raw);
    }
    r[W_STATUS] = rollback ? status : 208;
}
