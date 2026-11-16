/* SPDX-License-Identifier: MIT */
#ifndef S40_AUDIO_RM839_0233_IMPORTS_H
#define S40_AUDIO_RM839_0233_IMPORTS_H
#include <stdint.h>

unsigned fw_audio_cm_register(void*, unsigned, unsigned);
void fw_audio_cm_ready(void*);
void fw_audio_cm_unregister(void*);
void fw_audio_cm_send(unsigned, void*, void*);
void fw_audio_message_delete(void*);
void* fw_audio_source_create(void);
void* fw_audio_source_share(void*);
void fw_audio_source_limits(void*, unsigned, unsigned);
const uint32_t* fw_audio_source_id(void*);
void fw_audio_source_forget(const uint32_t*);
void fw_audio_source_eof(void*);
void* fw_audio_fragment_init(void*, void*, unsigned, unsigned, void*);
int fw_audio_source_write(void*, void*);
void fw_audio_class_call(void*, unsigned);

/* Typed table for linker symbols supplied by the standalone RM-839 02.33 port. */
#define AUDIO_LINKED_IMPORTS(volume)              \
    {.register_object = fw_audio_cm_register,     \
     .ready = fw_audio_cm_ready,                  \
     .unregister_object = fw_audio_cm_unregister, \
     .send = fw_audio_cm_send,                    \
     .delete_message = fw_audio_message_delete,   \
     .source_create = fw_audio_source_create,     \
     .source_share = fw_audio_source_share,       \
     .source_limits = fw_audio_source_limits,     \
     .source_id = fw_audio_source_id,             \
     .source_forget = fw_audio_source_forget,     \
     .source_eof = fw_audio_source_eof,           \
     .fragment_init = fw_audio_fragment_init,     \
     .source_write = fw_audio_source_write,       \
     .class_call = fw_audio_class_call,           \
     .volume_scale = (volume)}
#endif
