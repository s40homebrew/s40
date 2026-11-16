/* SPDX-License-Identifier: MIT */
/* Exact firmware entries and 32-byte prefix fingerprints; callable ARM/Thumb
 * bits are preserved. Each named binding is declared once. */
#include "registry.h"

const audio_profile audio_rm839_0904 = {
        .name = "Nokia 301 RM-839 09.04",
        .clock_anchor = 0x62e8c1c5u,
        .parent_class = {0x63ac030cu, 0x596c1f0au},
        .configuration =
                {
                        .object_layout = AUDIO_CM_DEVICE,
                        .source_layout = AUDIO_SOURCE_DEVICE,
                        .policy = AUDIO_POLICY_EXPLICIT,
                        .transport = AUDIO_TRANSPORT_RAW_PCM,
                        .route = AUDIO_ROUTE_EXISTING,
                        .volume_percent = 1,
                },
        .bindings =
                {
                        .register_object = {0x63584a1du, 0x7b27d4d3u},
                        .ready = {0x63584c8bu, 0xae735e94u},
                        .unregister_object = {0x635841b9u, 0x2deabf8u},
                        .send = {0x63585425u, 0xea37f55bu},
                        .delete_message = {0x63583edbu, 0xcb02df57u},
                        .source_create = {0x636642a1u, 0xb8158c2du},
                        .source_share = {0x6361f2f1u, 0xddd5279u},
                        .source_limits = {0x636642adu, 0x3ac98132u},
                        .source_id = {0x6361f7b9u, 0xabaeff74u},
                        .source_forget = {0x6361f86bu, 0xca462706u},
                        .source_eof = {0x6361ead9u, 0xa5caf9cu},
                        .fragment_init = {0x6361eb8bu, 0x42119910u},
                        .source_write = {0x6361f38fu, 0x9edd9220u},
                        .class_call = {0x6358e451u, 0xa9e9b1a7u},
                        .volume_scale = {0x637f31dbu, 0x66a6b84u},
                },
        .provisional = 1,
};
