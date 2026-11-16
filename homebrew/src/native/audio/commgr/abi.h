/* SPDX-License-Identifier: MIT */
#ifndef S40_AUDIO_ABI_H
#define S40_AUDIO_ABI_H
#include <stddef.h>
#include <stdint.h>

#include "protocol.h"

typedef void (*audio_receive_fn)(void*, unsigned, unsigned, uint8_t*, unsigned);
typedef struct {
    uintptr_t method;
    audio_receive_fn function;
} audio_method;
typedef struct audio_class {
    const struct audio_class *self, *parent;
    const audio_method* methods;
    uint32_t size_flags;
    const char* name;
} audio_class;

/* These are native object layouts, not wire packets. Keep their differences
 * explicit: later ComMgr adds a device byte before the registration index. */
typedef struct {
    const audio_class* klass;
    uint16_t object_id, resource, index;
    uint8_t type, device;
} audio_cm_legacy;
typedef struct {
    const audio_class* klass;
    uint32_t resource;
    uint16_t object_id, index;
} audio_cm_classic;
typedef struct {
    const audio_class* klass;
    uint32_t resource;
    uint16_t object_id;
    uint8_t device, reserved;
    uint16_t index, reserved2;
} audio_cm_device;
typedef union {
    audio_cm_legacy legacy;
    audio_cm_classic classic;
    audio_cm_device device;
} audio_cm_object;
enum audio_cm_layout { AUDIO_CM_CLASSIC, AUDIO_CM_DEVICE, AUDIO_CM_LEGACY };
enum audio_source_layout { AUDIO_SOURCE_CLASSIC, AUDIO_SOURCE_DEVICE };
enum audio_policy_mode { AUDIO_POLICY_NONE, AUDIO_POLICY_IMPLICIT, AUDIO_POLICY_EXPLICIT };
enum audio_transport { AUDIO_TRANSPORT_WAV, AUDIO_TRANSPORT_RAW_PCM };
enum audio_route_mode { AUDIO_ROUTE_EXISTING, AUDIO_ROUTE_OWN_IHF };

/* Only aligned queue snapshots/status reads are allowed. Firmware owns all
 * source mutation and locking; the opaque prefixes are not SDK fields. */
typedef struct {
    uint8_t opaque[48];
    volatile uint32_t queued_bytes;
    uint16_t fragments;
    uint8_t mutex;
    volatile uint8_t status;
} audio_source_classic;
typedef struct {
    uint8_t opaque[52];
    volatile uint32_t queued_bytes;
    uint8_t reserved[8];
    volatile uint8_t status;
} audio_source_device;
/* The fragment constructor writes seven 32-bit words. Its default destructor
 * owns both the fragment and PCM allocation; host pointers never go here. */
typedef struct {
    uint32_t next, data, start, bytes, flags, position, destructor;
} audio_fragment_storage;

typedef unsigned (*audio_fn_register_object)(void*, unsigned object_id, unsigned resource);
typedef void (*audio_fn_ready)(void*);
typedef void (*audio_fn_unregister_object)(void*);
typedef void (*audio_fn_send)(unsigned resource, void* object, void* body);
typedef void (*audio_fn_delete_message)(void* body);
typedef void* (*audio_fn_source_create)(void);
typedef void* (*audio_fn_source_share)(void*);
typedef void (*audio_fn_source_limits)(void*, unsigned high_bytes, unsigned low_bytes);
typedef const uint32_t* (*audio_fn_source_id)(void*);
typedef void (*audio_fn_source_forget)(const uint32_t*);
typedef void (*audio_fn_source_eof)(void*);
typedef void* (*audio_fn_fragment_init)(void* storage, void* pcm, unsigned bytes, unsigned flags,
                                        void* destructor);
typedef int (*audio_fn_source_write)(void*, void* fragment);
typedef void (*audio_fn_class_call)(void*, unsigned method);
typedef unsigned (*audio_fn_volume_scale)(unsigned level, unsigned source_min, unsigned source_max);
typedef struct {
    audio_fn_register_object register_object;
    audio_fn_ready ready;
    audio_fn_unregister_object unregister_object;
    audio_fn_send send;
    audio_fn_delete_message delete_message;
    audio_fn_source_create source_create;
    audio_fn_source_share source_share;
    audio_fn_source_limits source_limits;
    audio_fn_source_id source_id;
    audio_fn_source_forget source_forget;
    audio_fn_source_eof source_eof;
    audio_fn_fragment_init fragment_init;
    audio_fn_source_write source_write;
    audio_fn_class_call class_call;
    audio_fn_volume_scale volume_scale;
} audio_imports;
typedef struct {
    audio_imports imports;
    uintptr_t parent_class;
    enum audio_cm_layout object_layout;
    enum audio_source_layout source_layout;
    enum audio_policy_mode policy;
    enum audio_transport transport;
    enum audio_route_mode route;
    unsigned volume_percent;
} audio_abi;

static inline unsigned audio_cm_size(const audio_abi* abi) {
    return abi->object_layout == AUDIO_CM_DEVICE ? 16 : 12;
}
static inline unsigned audio_cm_id(const audio_cm_object* object, const audio_abi* abi) {
    return abi->object_layout == AUDIO_CM_LEGACY ? object->legacy.object_id
                                                 : object->classic.object_id;
}
static inline void audio_cm_set_index(audio_cm_object* object, const audio_abi* abi,
                                      unsigned index) {
    if (abi->object_layout == AUDIO_CM_LEGACY)
        object->legacy.index = (uint16_t)index;
    else if (abi->object_layout == AUDIO_CM_DEVICE)
        object->device.index = (uint16_t)index;
    else
        object->classic.index = (uint16_t)index;
}
static inline unsigned audio_source_queued(const void* source, const audio_abi* abi) {
    return abi->source_layout == AUDIO_SOURCE_DEVICE
                   ? ((const audio_source_device*)source)->queued_bytes
                   : ((const audio_source_classic*)source)->queued_bytes;
}
static inline unsigned audio_source_state(const void* source, const audio_abi* abi) {
    return abi->source_layout == AUDIO_SOURCE_DEVICE
                   ? ((const audio_source_device*)source)->status
                   : ((const audio_source_classic*)source)->status;
}
AUDIO_LAYOUT(source_classic_queue, offsetof(audio_source_classic, queued_bytes) == 48);
AUDIO_LAYOUT(source_classic_status, offsetof(audio_source_classic, status) == 55);
AUDIO_LAYOUT(source_device_queue, offsetof(audio_source_device, queued_bytes) == 52);
AUDIO_LAYOUT(source_device_status, offsetof(audio_source_device, status) == 64);
AUDIO_LAYOUT(fragment_storage, sizeof(audio_fragment_storage) == AUDIO_FRAGMENT_BYTES);
#if UINTPTR_MAX == UINT32_MAX
AUDIO_LAYOUT(cm_class, sizeof(audio_class) == 20);
AUDIO_LAYOUT(cm_legacy_size, sizeof(audio_cm_legacy) == 12);
AUDIO_LAYOUT(cm_legacy_id, offsetof(audio_cm_legacy, object_id) == 4);
AUDIO_LAYOUT(cm_legacy_index, offsetof(audio_cm_legacy, index) == 8);
AUDIO_LAYOUT(cm_classic_size, sizeof(audio_cm_classic) == 12);
AUDIO_LAYOUT(cm_classic_index, offsetof(audio_cm_classic, index) == 10);
AUDIO_LAYOUT(cm_device_size, sizeof(audio_cm_device) == 16);
AUDIO_LAYOUT(cm_device_index, offsetof(audio_cm_device, index) == 12);
#endif
#endif
