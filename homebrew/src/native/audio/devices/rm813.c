/* SPDX-License-Identifier: MIT */
/* Exact firmware entries and 32-byte prefix fingerprints; callable ARM/Thumb
 * bits are preserved. Each named binding is declared once. */
#include "registry.h"

const audio_profile audio_rm813_1453 = {
        .name = "Nokia Asha 302 RM-813 14.53",
        .clock_anchor = 0xf0519c3fu,
        .parent_class = {0xf0a149f0u, 0x7693ac40u},
        .configuration =
                {
                        .object_layout = AUDIO_CM_DEVICE,
                        .source_layout = AUDIO_SOURCE_DEVICE,
                        .policy = AUDIO_POLICY_IMPLICIT,
                        .transport = AUDIO_TRANSPORT_RAW_PCM,
                        .route = AUDIO_ROUTE_OWN_IHF,
                        .volume_percent = 1,
                },
        .bindings =
                {
                        .source_create = {0xf045014bu, 0x3cef87c4u},
                        .source_share = {0xf04e0d59u, 0xdc97315au},
                        .source_limits = {0xf0450157u, 0xaa1e61a8u},
                        .source_eof = {0xf04e0387u, 0x72e4d85bu},
                        .volume_scale = {0xf3a2fac8u, 0x9467bd02u},
                        .register_object = {0xf04c7b3du, 0x4af78fcfu},
                        .ready = {0xf04c7cd1u, 0x7fc08f7du},
                        .unregister_object = {0xf04c7283u, 0xad2ac8b3u},
                        .send = {0xf04c84a5u, 0xbd9128a7u},
                        .delete_message = {0xf0520801u, 0x5dc343e7u},
                        .source_id = {0xf04e12f5u, 0xee942843u},
                        .source_forget = {0xf04e13d9u, 0x2078957u},
                        .fragment_init = {0xf04e046bu, 0x5a7c0d13u},
                        .source_write = {0xf04e0e17u, 0x9c800467u},
                        .class_call = {0xf051fecdu, 0xd56d9c3eu},
                },
        .provisional = 1,
};
