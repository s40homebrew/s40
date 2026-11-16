/* SPDX-License-Identifier: MIT */
#ifndef S40_AUDIO_PROTOCOL_H
#define S40_AUDIO_PROTOCOL_H
#include <stddef.h>
#include <stdint.h>

#include "firmware/message.h"

/* ISI payload integers have network byte order; PCM/WAV data is little endian.
 * Byte-array integer types make packet layout independent of host alignment. */
typedef uint8_t audio_be16[2];
typedef uint8_t audio_be32[4];
typedef uint8_t audio_le16[2];
typedef uint8_t audio_le32[4];
static inline unsigned audio_get_be16(const uint8_t* value) {
    return ((unsigned)value[0] << 8) | value[1];
}
static inline void audio_put_be16(uint8_t* value, unsigned number) {
    value[0] = (uint8_t)(number >> 8);
    value[1] = (uint8_t)number;
}
static inline void audio_put_be32(uint8_t* value, uint32_t number) {
    audio_put_be16(value, number >> 16);
    audio_put_be16(value + 2, number);
}
static inline void audio_put_le16(uint8_t* value, unsigned number) {
    value[0] = (uint8_t)number;
    value[1] = (uint8_t)(number >> 8);
}
static inline void audio_put_le32(uint8_t* value, uint32_t number) {
    audio_put_le16(value, number);
    audio_put_le16(value + 2, number >> 16);
}

enum audio_resource { AUDIO_RESOURCE_POLICY = 0x0b, AUDIO_RESOURCE_MUSIC = 0x75 };
enum audio_policy_message {
    AUDIO_CLIENT_CREATE = 0x00,
    AUDIO_CLIENT_CREATED = 0x01,
    AUDIO_CLIENT_CREATE_FAILED = 0x02,
    AUDIO_CLIENT_DELETE = 0x04,
    AUDIO_CLIENT_DELETED = 0x05,
    AUDIO_CLIENT_DELETE_FAILED = 0x06,
    AUDIO_CONTROL_SET = 0x14,
    AUDIO_CONTROL_SET_DONE = 0x15,
    AUDIO_CONTROL_SET_FAILED = 0x16,
    AUDIO_POLICY_REQUEST = 0xa0,
    AUDIO_POLICY_REPLY = 0xa1
};
enum audio_music_message {
    AUDIO_PLAYER_CLOSE = 0x02,
    AUDIO_PLAYER_CLOSED = 0x03,
    AUDIO_PLAYER_PLAY = 0x07,
    AUDIO_PLAYER_PLAYING = 0x08,
    AUDIO_PLAYER_OPEN = 0x12,
    AUDIO_PLAYER_OPENED = 0x13
};
enum audio_policy_subblock {
    AUDIO_POLICY_APPLICATION = 1,
    AUDIO_POLICY_APPLICATION_REPLY = 2,
    AUDIO_POLICY_STREAM_CREATE = 3,
    AUDIO_POLICY_STREAM_REPLY = 4,
    AUDIO_POLICY_APPLICATION_DELETE = 5,
    AUDIO_POLICY_APPLICATION_DELETED = 6,
    AUDIO_POLICY_STREAM_DELETED = 8
};
enum {
    AUDIO_APPLICATION_MEDIA = 4,
    AUDIO_APPLICATION_PLAYBACK = 4,
    AUDIO_APPLICATION_FLAGS = 12,
    AUDIO_CONTROL_VALUE = 3,
    AUDIO_CONTROL_VALUE_REPLY = 6,
    AUDIO_CONTROL_ACCEPTED = 2,
    AUDIO_CLIENT_MEDIA = 8,
    AUDIO_CLIENT_LAST = 32,
    AUDIO_OUTPUT_GROUP = 6,
    AUDIO_OUTPUT_PROPERTY = 1,
    AUDIO_OUTPUT_IHF = 13,
    AUDIO_MIME_WAV = 36,
    AUDIO_MIME_PCM = 136,
    AUDIO_PLAYER_OK = 1,
    AUDIO_PLAYBACK_SUBBLOCK = 0,
    AUDIO_STREAM_SUBBLOCK = 22,
    AUDIO_PCM_SUBBLOCK = 23,
    AUDIO_PCM_BITS = 16,
    AUDIO_PCM_BLOCK_FRAMES = 256,
    AUDIO_WRITE_MAX_FRAMES = 512,
    AUDIO_SOURCE_CANCELLED = 4,
    AUDIO_SOURCE_EOF = 5,
    AUDIO_CM_RECEIVE = 0xc009,
    AUDIO_CLASS_DELETE = 0xc005,
    AUDIO_FRAGMENT_BYTES = 28,
    AUDIO_REPLY_TIMEOUT_MS = 1500,
    AUDIO_REPLY_PUMP_LIMIT = 20000
};
#define AUDIO_CODE(resource, message) (((unsigned)(resource) << 8) | (message))

typedef struct {
    uint8_t transaction, message;
} audio_message;
typedef struct {
    uint8_t id, length;
} audio_subblock;
typedef struct {
    audio_message message;
    audio_be16 application;
    uint8_t reserved[2], status, count;
} audio_policy_header;
typedef struct {
    audio_subblock block;
    uint8_t count, reserved;
} audio_stream_create;
typedef struct {
    audio_subblock block;
    uint8_t reserved[2];
    audio_be16 application_type, subtype;
    audio_be32 priority, flags;
} audio_application_descriptor;
typedef struct {
    audio_subblock block;
    audio_be16 application;
} audio_application_delete;
typedef struct {
    audio_subblock block;
    uint8_t status, count;
    audio_be16 stream;
    uint8_t reserved[2];
} audio_stream_reply;
typedef struct {
    audio_subblock block;
    audio_be16 application;
    uint8_t status, reserved[3];
} audio_application_deleted;
typedef struct {
    audio_policy_header header;
    audio_stream_create stream;
} audio_policy_implicit;
typedef struct {
    audio_policy_header header;
    audio_application_descriptor application;
    audio_stream_create stream;
} audio_policy_explicit;
typedef struct {
    audio_policy_header header;
    audio_application_delete application;
} audio_policy_delete;

typedef struct {
    audio_message message;
    uint8_t client, reserved;
} audio_client_message;
typedef struct {
    audio_message message;
    uint8_t client, group, reserved[3], count;
} audio_control_header;
typedef struct {
    audio_subblock block;
    uint8_t property, flags;
    audio_be16 value;
    uint8_t reserved[2];
} audio_control_value;
typedef struct {
    audio_control_header header;
    audio_control_value value;
} audio_control_request;
typedef struct {
    audio_message message;
    uint8_t group, status, reserved[2], unchanged, count;
} audio_control_reply;
typedef struct {
    audio_subblock block;
    uint8_t property, status, applied, reserved[3];
} audio_control_value_reply;

typedef struct {
    audio_message message;
    audio_be16 handle;
} audio_player_command;
typedef struct {
    audio_message message;
    audio_be16 status;
} audio_player_reply;
typedef struct {
    audio_player_reply header;
    audio_be16 handle;
} audio_player_open_reply;
typedef struct {
    audio_message message;
    uint8_t reserved[2];
    audio_be32 source_type, source_key, mime;
    uint8_t reserved2[4], mode, volume, retain, count;
} audio_player_open_header;
typedef struct {
    audio_subblock block;
    uint8_t enabled, reserved;
    uint8_t reserved2[4];
    audio_be32 repeat_count;
    uint8_t ramp_mode, ramp_step, reserved3[6];
} audio_playback_descriptor;
typedef struct {
    audio_subblock block;
    audio_be16 stream;
} audio_policy_stream;
typedef struct {
    audio_subblock block;
    uint8_t reserved[2], channels, bits;
    audio_be16 frames;
    audio_be32 rate;
} audio_pcm_descriptor;
typedef struct {
    audio_player_open_header header;
    audio_playback_descriptor playback;
    audio_policy_stream policy;
} audio_player_open_wav;
typedef struct {
    audio_player_open_wav base;
    audio_pcm_descriptor pcm;
} audio_player_open_pcm;
typedef struct {
    uint8_t riff[4];
    audio_le32 riff_size;
    uint8_t wave[4], fmt[4];
    audio_le32 fmt_size;
    audio_le16 format, channels;
    audio_le32 rate, bytes_per_second;
    audio_le16 block_align, bits;
    uint8_t data[4];
    audio_le32 data_size;
} audio_wav_header;

#define AUDIO_LAYOUT(name, condition) typedef char audio_layout_##name[(condition) ? 1 : -1]
AUDIO_LAYOUT(policy_header, sizeof(audio_policy_header) == 8);
AUDIO_LAYOUT(policy_implicit, sizeof(audio_policy_implicit) == 12);
AUDIO_LAYOUT(policy_explicit, sizeof(audio_policy_explicit) == 28);
AUDIO_LAYOUT(policy_delete, sizeof(audio_policy_delete) == 12);
AUDIO_LAYOUT(control_request, sizeof(audio_control_request) == 16);
AUDIO_LAYOUT(control_value, offsetof(audio_control_request, value.value) == 12);
AUDIO_LAYOUT(control_reply, sizeof(audio_control_reply) == 8);
AUDIO_LAYOUT(control_value_reply, sizeof(audio_control_value_reply) == 8);
AUDIO_LAYOUT(player_command, sizeof(audio_player_command) == 4);
AUDIO_LAYOUT(player_open_wav, sizeof(audio_player_open_wav) == 48);
AUDIO_LAYOUT(player_open_pcm, sizeof(audio_player_open_pcm) == 60);
AUDIO_LAYOUT(player_volume, offsetof(audio_player_open_header, volume) == 21);
AUDIO_LAYOUT(player_repeat, offsetof(audio_player_open_wav, playback.repeat_count) == 32);
AUDIO_LAYOUT(pcm_channels, offsetof(audio_player_open_pcm, pcm.channels) == 52);
AUDIO_LAYOUT(pcm_frames, offsetof(audio_player_open_pcm, pcm.frames) == 54);
AUDIO_LAYOUT(pcm_rate, offsetof(audio_player_open_pcm, pcm.rate) == 56);
AUDIO_LAYOUT(wav_header, sizeof(audio_wav_header) == 44);
#endif
