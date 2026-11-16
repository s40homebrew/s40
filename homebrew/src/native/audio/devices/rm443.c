/* SPDX-License-Identifier: MIT */
/* Exact firmware entries and 32-byte prefix fingerprints; callable ARM/Thumb
 * bits are preserved. Each named binding is declared once. */
#include "registry.h"

const audio_profile audio_rm443_1012 = {
        .name = "Nokia 6303 classic RM-443 10.12",
        .clock_anchor = 0xf02d500bu,
        .parent_class = {0xf111f3a8u, 0xfc968444u},
        .configuration =
                {
                        .object_layout = AUDIO_CM_CLASSIC,
                        .source_layout = AUDIO_SOURCE_CLASSIC,
                        .policy = AUDIO_POLICY_IMPLICIT,
                        .transport = AUDIO_TRANSPORT_RAW_PCM,
                        .route = AUDIO_ROUTE_OWN_IHF,
                        .volume_percent = 1,
                },
        .bindings =
                {
                        .register_object = {0xf0b9c36fu, 0xefece329u},
                        .ready = {0xf0b9c4e5u, 0xd0c8627bu},
                        .unregister_object = {0xf0b9bb11u, 0xc053550au},
                        .send = {0xf0b9cbc9u, 0x1de09f20u},
                        .delete_message = {0xf0b9b869u, 0x7ca2635eu},
                        .source_create = {0xf042e3e3u, 0x3eb07abeu},
                        .source_share = {0xf0b97d0du, 0x1b7f8634u},
                        .source_limits = {0xf042e3efu, 0x35866c2fu},
                        .source_id = {0xf0b984d9u, 0x904ec5c8u},
                        .source_forget = {0xf0b985a5u, 0xdfced847u},
                        .source_eof = {0xf0b96f69u, 0x9cfad77au},
                        .fragment_init = {0xf0b970abu, 0x27a3636au},
                        .source_write = {0xf0b97e11u, 0x9659000au},
                        .class_call = {0xf04ed355u, 0x30bbf620u},
                        .volume_scale = {0xf0ccc217u, 0x3941c1b3u},
                },
        .provisional = 0,
};
