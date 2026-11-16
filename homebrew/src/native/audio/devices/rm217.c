/* SPDX-License-Identifier: MIT */
/* Exact firmware entries and 32-byte prefix fingerprints; callable ARM/Thumb
 * bits are preserved. Each named binding is declared once. */
#include "registry.h"

const audio_profile audio_rm217_0721 = {
        .name = "Nokia 6300 RM-217 07.21",
        .clock_anchor = 0xf08a6d4fu,
        .parent_class = {0xf0d469c4u, 0xbf142d12u},
        .configuration =
                {
                        .object_layout = AUDIO_CM_LEGACY,
                        .source_layout = AUDIO_SOURCE_CLASSIC,
                        .policy = AUDIO_POLICY_NONE,
                        .transport = AUDIO_TRANSPORT_RAW_PCM,
                        .route = AUDIO_ROUTE_OWN_IHF,
                        .volume_percent = 1,
                },
        .bindings =
                {
                        .register_object = {0xf051596fu, 0x16b11590u},
                        .ready = {0xf0515adfu, 0x5b951218u},
                        .unregister_object = {0xf0515191u, 0xd02fbaeu},
                        .send = {0xf051608fu, 0x41d2ae4u},
                        .delete_message = {0xf05199b3u, 0x8e2c1271u},
                        .source_create = {0xf08b2489u, 0x63d26756u},
                        .source_share = {0xf0652ecbu, 0xe93e6cdeu},
                        .source_limits = {0xf08b2495u, 0xd2c5eba4u},
                        .source_id = {0xf065315du, 0xb15d46f7u},
                        .source_forget = {0xf0653229u, 0x7af0ac4au},
                        .source_eof = {0xf065296bu, 0x994f3eu},
                        .fragment_init = {0xf06529e3u, 0xd2cee66eu},
                        .source_write = {0xf0652f39u, 0x61b7fe3fu},
                        .class_call = {0xf05745e3u, 0xa483b30eu},
                        .volume_scale = {0xf070b56fu, 0x4ecce7bu},
                },
        .provisional = 1,
};

const audio_profile audio_rm217_0730 = {
        .name = "Nokia 6300 RM-217 07.30",
        .clock_anchor = 0xf08a755fu,
        .parent_class = {0xf0d47274u, 0xe980a751u},
        .configuration =
                {
                        .object_layout = AUDIO_CM_LEGACY,
                        .source_layout = AUDIO_SOURCE_CLASSIC,
                        .policy = AUDIO_POLICY_NONE,
                        .transport = AUDIO_TRANSPORT_RAW_PCM,
                        .route = AUDIO_ROUTE_OWN_IHF,
                        .volume_percent = 1,
                },
        .bindings =
                {
                        .register_object = {0xf05147f7u, 0xd055cfabu},
                        .ready = {0xf0514967u, 0xec241fb5u},
                        .unregister_object = {0xf0514019u, 0x1ce59af4u},
                        .send = {0xf0514f17u, 0x199f5607u},
                        .delete_message = {0xf0519c77u, 0x8d0f46c9u},
                        .source_create = {0xf08b2c99u, 0x2529a467u},
                        .source_share = {0xf06531b3u, 0xe4634050u},
                        .source_limits = {0xf08b2ca5u, 0xe6c760d6u},
                        .source_id = {0xf0653445u, 0xb188d395u},
                        .source_forget = {0xf0653511u, 0xb1d565b0u},
                        .source_eof = {0xf0652c53u, 0x994f3eu},
                        .fragment_init = {0xf0652ccbu, 0xcd121454u},
                        .source_write = {0xf0653221u, 0xe5ee3057u},
                        .class_call = {0xf05748bfu, 0xa483b30eu},
                        .volume_scale = {0xf070bd8fu, 0xd874e13bu},
                },
        .provisional = 1,
};
