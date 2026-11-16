/* SPDX-License-Identifier: MIT */
#ifndef S40_INTERNAL_FIRMWARE_MESSAGE_H
#define S40_INTERNAL_FIRMWARE_MESSAGE_H
#include <stddef.h>
#include <stdint.h>

/* OS timers are tagged integers, not packet addresses. The last tag and the
 * ignored/special timer IDs are profile bindings, not universal constants. */
enum { FW_OS_TIMER_MESSAGE_BASE = 192, FW_MESSAGE_INTERNAL = 1, FW_ISI_OBJECT_MAX = 0x3ff };

static inline int fw_message_is_timer(uintptr_t message, unsigned last_tag) {
    return message >= FW_OS_TIMER_MESSAGE_BASE && message <= last_tag;
}
static inline unsigned fw_message_timer_id(uintptr_t message) {
    return (unsigned)message - FW_OS_TIMER_MESSAGE_BASE;
}

/* ComMgr's normalized native header. Its length is little-endian and includes
 * the two object-ID bytes after the length field: body size + 2. This is not
 * the byte order of an external Phonet frame. Uninterpreted device bits and
 * sender fields remain explicit; object helpers only use the two object bits.
 * Packet ownership is unchanged: ordinary ComMgr consumes a whole packet;
 * the stock ISI delete/send routines receive the body pointer instead.
 */
typedef struct {
    uint8_t type, receiver_device, sender_device, resource;
    uint8_t length_le[2];
    uint8_t receiver_object, sender_object;
} fw_isi_header;

typedef char fw_isi_header_size[(sizeof(fw_isi_header) == 8) ? 1 : -1];
typedef char fw_isi_length_offset[(offsetof(fw_isi_header, length_le) == 4) ? 1 : -1];

static inline unsigned fw_isi_length(const fw_isi_header* header) {
    return header->length_le[0] | ((unsigned)header->length_le[1] << 8);
}
static inline unsigned fw_isi_receiver(const fw_isi_header* header) {
    return ((unsigned)(header->receiver_device & 3u) << 8) | header->receiver_object;
}
static inline unsigned fw_isi_sender(const fw_isi_header* header) {
    return ((unsigned)(header->sender_device & 3u) << 8) | header->sender_object;
}
static inline int fw_isi_object_valid(unsigned object) {
    return object && object <= FW_ISI_OBJECT_MAX;
}
static inline uint8_t* fw_isi_body(fw_isi_header* header) { return (uint8_t*)(header + 1); }
static inline const uint8_t* fw_isi_body_const(const fw_isi_header* header) {
    return (const uint8_t*)(header + 1);
}
static inline fw_isi_header* fw_isi_from_body(void* body) {
    return (fw_isi_header*)((uint8_t*)body - sizeof(fw_isi_header));
}
static inline const fw_isi_header* fw_isi_from_body_const(const void* body) {
    return (const fw_isi_header*)((const uint8_t*)body - sizeof(fw_isi_header));
}
static inline void fw_isi_set_body_size(fw_isi_header* header, unsigned bytes) {
    unsigned normalized_length = bytes + 2;
    header->length_le[0] = (uint8_t)normalized_length;
    header->length_le[1] = (uint8_t)(normalized_length >> 8);
}
#endif
