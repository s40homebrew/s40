/* SPDX-License-Identifier: MIT */
/* Exact firmware entries and 32-byte prefix fingerprints; callable ARM/Thumb
 * bits are preserved. Each named binding is declared once. */
#include "registry.h"

const audio_profile audio_rm614_0460 = {
        .name = "Nokia C3-00 RM-614 04.60",
        .clock_anchor = 0x8037fbe5u,
        .parent_class = {0x80bc3ff8u, 0x6cafeaadu},
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
                        .register_object = {0x805352fbu, 0xaea5e44au},
                        .ready = {0x80535497u, 0x80df8edeu},
                        .unregister_object = {0x80534a09u, 0xbaea01f2u},
                        .send = {0x80535ce1u, 0x273d023fu},
                        .delete_message = {0x80558eb5u, 0x73fa7de8u},
                        .source_id = {0x8034cf65u, 0x904ec5c8u},
                        .source_forget = {0x8034d031u, 0x15e6deaau},
                        .source_write = {0x8034c89du, 0x9659000au},
                        .class_call = {0x805572cfu, 0xf1b00d8u},
                        .fragment_init = {0x8034bb37u, 0xff4eb37cu},
                        .source_create = {0x806352c3u, 0x790bfd0fu},
                        .source_share = {0x8034c799u, 0x1b7f8634u},
                        .source_limits = {0x806352cfu, 0x35866c2fu},
                        .source_eof = {0x8034b9f5u, 0x9cfad77au},
                        .volume_scale = {0x857f7bdbu, 0x5e3c1e1bu},
                },
        .provisional = 1,
};

const audio_profile audio_rm614_0863 = {
        .name = "Nokia C3-00 RM-614 08.63",
        .clock_anchor = 0x8038303du,
        .parent_class = {0x80bdc634u, 0xe1b4fb79u},
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
                        .register_object = {0x80542c2bu, 0xcc9e0e5eu},
                        .ready = {0x80542dc7u, 0x996a497eu},
                        .unregister_object = {0x80542339u, 0xdff4fc5au},
                        .send = {0x80543611u, 0x61296f14u},
                        .delete_message = {0x8054ba4du, 0x126738beu},
                        .source_id = {0x80355419u, 0x904ec5c8u},
                        .source_forget = {0x803554e5u, 0x4d677f6u},
                        .source_write = {0x80354d51u, 0x9659000au},
                        .class_call = {0x8054c6f3u, 0xf1b00d8u},
                        .fragment_init = {0x80353febu, 0x57bdb66fu},
                        .source_create = {0x8063a1fbu, 0x70cce417u},
                        .source_share = {0x80354c4du, 0x1b7f8634u},
                        .source_limits = {0x8063a207u, 0x35866c2fu},
                        .source_eof = {0x80353ea9u, 0x9cfad77au},
                        .volume_scale = {0x858049b7u, 0x67f71a4bu},
                },
        .provisional = 1,
};
