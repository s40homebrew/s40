/* SPDX-License-Identifier: MIT */
#include "services.h"

#include "firmware_api.h"

/* The launcher resolves and validates this entire capability before native
 * entry. No ROM probing, model selection or firmware addresses belong here. */
int audio_abi_configure(unsigned contract, audio_abi* output) {
    audio_abi candidate = {0};
    candidate.transport = AUDIO_TRANSPORT_RAW_PCM;
    candidate.volume_percent = 1;
    candidate.route = AUDIO_ROUTE_OWN_IHF;
    candidate.source_layout = AUDIO_SOURCE_CLASSIC;
    switch (contract) {
        case FW_AUDIO_ABI_LEGACY:
            candidate.object_layout = AUDIO_CM_LEGACY;
            candidate.policy = AUDIO_POLICY_NONE;
            break;
        case FW_AUDIO_ABI_CLASSIC:
            candidate.object_layout = AUDIO_CM_CLASSIC;
            candidate.policy = AUDIO_POLICY_IMPLICIT;
            break;
        case FW_AUDIO_ABI_DEVICE_IHF:
        case FW_AUDIO_ABI_DEVICE_MEDIA:
            candidate.object_layout = AUDIO_CM_DEVICE;
            candidate.source_layout = AUDIO_SOURCE_DEVICE;
            candidate.policy = contract == FW_AUDIO_ABI_DEVICE_MEDIA ? AUDIO_POLICY_EXPLICIT
                                                                     : AUDIO_POLICY_IMPLICIT;
            if (contract == FW_AUDIO_ABI_DEVICE_MEDIA)
                candidate.route = AUDIO_ROUTE_EXISTING;
            break;
        default:
            return 0;
    }
    *output = candidate;
    return 1;
}

int audio_abi_from_services(const uint32_t* services, audio_abi* output) {
    audio_abi candidate;
    if (!services || !audio_abi_configure(services[API_AUDIO_ABI], &candidate))
        return 0;
    for (unsigned i = FW_AUDIO_FIRST + 1; i < FW_AUDIO_LIMIT; ++i)
        if (!services[i])
            return 0;
    candidate.parent_class = services[API_AUDIO_PARENT_CLASS];
#define BIND(field, name) \
    candidate.imports.field = (audio_fn_##field)(uintptr_t)services[API_AUDIO_##name]
    BIND(register_object, REGISTER_OBJECT);
    BIND(ready, READY);
    BIND(unregister_object, UNREGISTER_OBJECT);
    BIND(send, SEND);
    BIND(delete_message, DELETE_MESSAGE);
    BIND(source_create, SOURCE_CREATE);
    BIND(source_share, SOURCE_SHARE);
    BIND(source_limits, SOURCE_LIMITS);
    BIND(source_id, SOURCE_ID);
    BIND(source_forget, SOURCE_FORGET);
    BIND(source_eof, SOURCE_EOF);
    BIND(fragment_init, FRAGMENT_INIT);
    BIND(source_write, SOURCE_WRITE);
    BIND(class_call, CLASS_CALL);
    BIND(volume_scale, VOLUME_SCALE);
#undef BIND
    *output = candidate;
    return 1;
}
