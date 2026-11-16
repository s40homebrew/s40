/* SPDX-License-Identifier: MIT
 * Nokia 301 RM-839 02.33 bindings; see homebrew/docs/audio.md.
 */
#include <stddef.h>
#include <string.h>

#include "../commgr/driver.h"
#include "rm839_0233_imports.h"
#include "s40/audio.h"
static const audio_abi rm839_0233_abi = {
        .imports = AUDIO_LINKED_IMPORTS(NULL),
        .parent_class = 0x63ab89a0u,
        .object_layout = AUDIO_CM_DEVICE,
        .source_layout = AUDIO_SOURCE_DEVICE,
        .policy = AUDIO_POLICY_EXPLICIT,
        .transport = AUDIO_TRANSPORT_WAV,
        .route = AUDIO_ROUTE_EXISTING,
        .volume_percent = 0,
};
static int rm839_0233_open(const s40_audio_format* format) {
    return audio_cm_open(&rm839_0233_abi, format);
}
/* The adjacent JSON supplies TyrQuake's linker-veneer generator. */
const s40_audio_driver s40_audio_rm839_0233 = {rm839_0233_open, audio_cm_write, audio_cm_queued,
                                               audio_cm_status, audio_cm_close};
