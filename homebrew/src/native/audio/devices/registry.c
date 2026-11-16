/* SPDX-License-Identifier: MIT */
#include "registry.h"

#include "../commgr/driver.h"
#include "../internal.h"

const audio_profile* const audio_profiles[] = {
        &audio_rm443_1012, &audio_rm813_1453, &audio_rm217_0721, &audio_rm614_0460,
        &audio_rm614_0863, &audio_rm839_0904, &audio_rm217_0730,
};
const unsigned audio_profile_count = sizeof(audio_profiles) / sizeof(audio_profiles[0]);

static int binding_matches(audio_rom_binding binding) {
    if (!binding.entry)
        return 0;
    fw_rom_fingerprint prefix = {binding.entry & ~(uintptr_t)1, binding.prefix_fnv32};
    return fw_rom_matches(&prefix);
}
int audio_profile_resolve(const audio_profile* profile, uintptr_t discovered_clock,
                          audio_abi* output) {
    if (!discovered_clock || profile->clock_anchor != discovered_clock)
        return 0;
    if (!profile->parent_class.address || !fw_rom_matches(&profile->parent_class))
        return 0;
    audio_abi candidate = profile->configuration;
    candidate.parent_class = profile->parent_class.address;
    /* Required bindings fail closed; the converter is optional only for the
     * external adapter's native-volume contract. No indirect call uses raw data. */
#define BIND(field)                                                                \
    do {                                                                           \
        if (!binding_matches(profile->bindings.field))                             \
            return 0;                                                              \
        candidate.imports.field = (audio_fn_##field)profile->bindings.field.entry; \
    } while (0)
    BIND(register_object);
    BIND(ready);
    BIND(unregister_object);
    BIND(send);
    BIND(delete_message);
    BIND(source_create);
    BIND(source_share);
    BIND(source_limits);
    BIND(source_id);
    BIND(source_forget);
    BIND(source_eof);
    BIND(fragment_init);
    BIND(source_write);
    BIND(class_call);
    if (candidate.volume_percent || profile->bindings.volume_scale.entry)
        BIND(volume_scale);
#undef BIND
    *output = candidate;
    return 1;
}

static audio_abi selected;
static int profile_open(const s40_audio_format* format) { return audio_cm_open(&selected, format); }
static const s40_audio_driver profile_driver = {profile_open, audio_cm_write, audio_cm_queued,
                                                audio_cm_status, audio_cm_close};
int s40_audio_bind_firmware(uintptr_t discovered_clock) {
    s40_audio_bind(NULL); /* Close the old stream before replacing its ABI. */
    if (!audio_cm_available())
        return 0;
    for (unsigned i = 0; i < audio_profile_count; ++i) {
        if (!audio_profile_resolve(audio_profiles[i], discovered_clock, &selected))
            continue;
        s40_audio_bind(&profile_driver);
        s40_audio_set_message_dispatch(audio_cm_dispatch);
        return 1;
    }
    return 0;
}
