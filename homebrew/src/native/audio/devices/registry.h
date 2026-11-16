/* SPDX-License-Identifier: MIT */
#ifndef S40_AUDIO_DEVICE_REGISTRY_H
#define S40_AUDIO_DEVICE_REGISTRY_H
#include "../commgr/abi.h"
#include "firmware/guard.h"

/* Callable entry retains its instruction-set bit. Prefix checks use the even
 * address only after the independently discovered clock identifies the map. */
typedef struct {
    uintptr_t entry;
    uint32_t prefix_fnv32;
} audio_rom_binding;
typedef struct {
    audio_rom_binding register_object;
    audio_rom_binding ready;
    audio_rom_binding unregister_object;
    audio_rom_binding send;
    audio_rom_binding delete_message;
    audio_rom_binding source_create;
    audio_rom_binding source_share;
    audio_rom_binding source_limits;
    audio_rom_binding source_id;
    audio_rom_binding source_forget;
    audio_rom_binding source_eof;
    audio_rom_binding fragment_init;
    audio_rom_binding source_write;
    audio_rom_binding class_call;
    audio_rom_binding volume_scale;
} audio_rom_imports;
typedef struct {
    const char* name;
    uintptr_t clock_anchor;
    fw_rom_fingerprint parent_class;
    audio_abi configuration;
    audio_rom_imports bindings;
    unsigned provisional;
} audio_profile;

extern const audio_profile audio_rm443_1012;
extern const audio_profile audio_rm813_1453;
extern const audio_profile audio_rm217_0721;
extern const audio_profile audio_rm614_0460;
extern const audio_profile audio_rm614_0863;
extern const audio_profile audio_rm839_0904;
extern const audio_profile audio_rm217_0730;

extern const audio_profile* const audio_profiles[];
extern const unsigned audio_profile_count;
/* Resolve into a temporary typed ABI; leave output unchanged on failure. */
int audio_profile_resolve(const audio_profile*, uintptr_t discovered_clock, audio_abi*);
#endif
