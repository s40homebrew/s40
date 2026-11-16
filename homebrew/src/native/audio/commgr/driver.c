/* SPDX-License-Identifier: MIT
 * Shared copied-PCM stream; all firmware callbacks stay on the application task.
 */
#include "driver.h"

#include <string.h>

#include "runtime_services.h"

static const audio_abi* abi;

static audio_cm_object object;
static audio_class klass;
static void* source;
static uint32_t source_id[2];
static unsigned rate, stride, max_bytes, policy_id, stream_id, handle;
static unsigned serial, expected, expected_serial, route_client;
static audio_client_message* route_delete;
static int registered, state, reply, published, opened;
static unsigned silent, route_pending, route_serial;

/* Firmware owns outgoing messages. Its normal message allocator dereferences
 * failed allocations, so allocate the checked native header here instead. */
static void* message(unsigned bytes, unsigned id) {
    fw_isi_header* header = fw_os_block_alloc_no_wait(sizeof(*header) + bytes);
    if (!header)
        return NULL;
    memset(header, 0, sizeof(*header) + bytes);
    fw_isi_set_body_size(header, bytes);
    audio_message* body = (audio_message*)fw_isi_body(header);
    body->transaction = (uint8_t)++serial;
    body->message = (uint8_t)id;
    return body;
}
static void send(unsigned resource, void* body, unsigned response) {
    const audio_message* packet = body;
    expected = AUDIO_CODE(resource, response);
    expected_serial = packet->transaction;
    reply = 0;
    abi->imports.send(resource, &object, body); /* consumes even on send failure */
}
static int policy_reply(const audio_policy_header* packet, unsigned bytes) {
    if (bytes < sizeof(*packet) || packet->status)
        return 0;
    if (!policy_id)
        policy_id = audio_get_be16(packet->application);
    unsigned offset = sizeof(*packet), stream = 0;
    const uint8_t* body = (const uint8_t*)packet;
    for (unsigned i = 0; i < packet->count; ++i) {
        if (bytes - offset < 4)
            return 0;
        const audio_subblock* block = (const audio_subblock*)(body + offset);
        if (block->length < 4 || block->length > bytes - offset)
            return 0;
        switch (block->id) {
            case AUDIO_POLICY_STREAM_REPLY: {
                const audio_stream_reply* answer = (const audio_stream_reply*)block;
                if (block->length < offsetof(audio_stream_reply, reserved) || answer->status)
                    return 0;
                stream = audio_get_be16(answer->stream);
                break;
            }
            case AUDIO_POLICY_APPLICATION_REPLY:
            case AUDIO_POLICY_STREAM_DELETED:
                if (((const uint8_t*)block)[sizeof(*block)])
                    return 0;
                break;
            case AUDIO_POLICY_APPLICATION_DELETED:
                if (block->length < sizeof(audio_application_deleted) ||
                    ((const audio_application_deleted*)block)->status)
                    return 0;
                break;
            default:
                break;
        }
        offset += block->length;
    }
    if (stream)
        stream_id = stream;
    return 1;
}
static int route_reply(const audio_control_reply* packet, unsigned bytes) {
    if (bytes < sizeof(*packet) || packet->group != AUDIO_OUTPUT_GROUP || packet->status)
        return 0;
    if (!packet->count)
        return 1;
    if (packet->count != 1 || bytes < sizeof(*packet) + sizeof(audio_control_value_reply))
        return 0;
    const audio_control_value_reply* value = (const audio_control_value_reply*)(packet + 1);
    /* Acceptance does not imply immediate priority: another owner may win. */
    return value->block.id == AUDIO_CONTROL_VALUE_REPLY && value->block.length == sizeof(*value) &&
           value->property == AUDIO_OUTPUT_PROPERTY && value->status == AUDIO_CONTROL_ACCEPTED;
}
static int reply_matches(unsigned code) {
    if (code == expected)
        return 1;
    if (abi->route != AUDIO_ROUTE_OWN_IHF)
        return 0;
    return (expected == AUDIO_CODE(AUDIO_RESOURCE_POLICY, AUDIO_CLIENT_CREATED) &&
            code == AUDIO_CODE(AUDIO_RESOURCE_POLICY, AUDIO_CLIENT_CREATE_FAILED)) ||
           (expected == AUDIO_CODE(AUDIO_RESOURCE_POLICY, AUDIO_CONTROL_SET_DONE) &&
            code == AUDIO_CODE(AUDIO_RESOURCE_POLICY, AUDIO_CONTROL_SET_FAILED)) ||
           (expected == AUDIO_CODE(AUDIO_RESOURCE_POLICY, AUDIO_CLIENT_DELETED) &&
            code == AUDIO_CODE(AUDIO_RESOURCE_POLICY, AUDIO_CLIENT_DELETE_FAILED));
}
static void receive(void* self, unsigned method, unsigned sender, uint8_t* body, unsigned code) {
    (void)self;
    (void)method;
    (void)sender;
    unsigned bytes = fw_isi_length(fw_isi_from_body(body));
    bytes = bytes >= 2 ? bytes - 2 : 0;
    const audio_message* packet = (const audio_message*)body;
    /* A CREATE reply can arrive during failed-startup cleanup, after another
     * request changed `expected`. Retain ownership until the reserved delete
     * packet is sent; an unresolved registration blocks subsequent opens. */
    if (route_pending && bytes >= sizeof(audio_client_message) &&
        packet->transaction == route_serial) {
        if (code == AUDIO_CODE(AUDIO_RESOURCE_POLICY, AUDIO_CLIENT_CREATED)) {
            unsigned client = ((const audio_client_message*)body)->client;
            if (client >= 1 && client <= AUDIO_CLIENT_LAST) {
                route_client = client;
                route_pending = 0;
            }
        } else if (code == AUDIO_CODE(AUDIO_RESOURCE_POLICY, AUDIO_CLIENT_CREATE_FAILED))
            route_pending = 0;
    }
    if (bytes >= sizeof(*packet) && reply_matches(code) && packet->transaction == expected_serial) {
        reply = -1;
        switch (code) {
            case AUDIO_CODE(AUDIO_RESOURCE_POLICY, AUDIO_POLICY_REPLY):
                if (policy_reply((const audio_policy_header*)body, bytes))
                    reply = 1;
                break;
            case AUDIO_CODE(AUDIO_RESOURCE_POLICY, AUDIO_CLIENT_CREATED): {
                const audio_client_message* answer = (const audio_client_message*)body;
                if (bytes >= sizeof(*answer) && answer->client >= 1 &&
                    answer->client <= AUDIO_CLIENT_LAST) {
                    route_client = answer->client;
                    reply = 1;
                }
                break;
            }
            case AUDIO_CODE(AUDIO_RESOURCE_POLICY, AUDIO_CONTROL_SET_DONE):
                if (route_reply((const audio_control_reply*)body, bytes))
                    reply = 1;
                break;
            case AUDIO_CODE(AUDIO_RESOURCE_POLICY, AUDIO_CLIENT_DELETED):
                if (bytes >= sizeof(audio_client_message)) {
                    route_client = 0;
                    reply = 1;
                }
                break;
            case AUDIO_CODE(AUDIO_RESOURCE_MUSIC, AUDIO_PLAYER_OPENED): {
                const audio_player_open_reply* answer = (const audio_player_open_reply*)body;
                if (bytes >= sizeof(*answer) &&
                    audio_get_be16(answer->header.status) == AUDIO_PLAYER_OK) {
                    handle = audio_get_be16(answer->handle);
                    opened = 1;
                    reply = 1;
                }
                break;
            }
            case AUDIO_CODE(AUDIO_RESOURCE_MUSIC, AUDIO_PLAYER_PLAYING):
            case AUDIO_CODE(AUDIO_RESOURCE_MUSIC, AUDIO_PLAYER_CLOSED):
                if (bytes >= sizeof(audio_player_reply) &&
                    audio_get_be16(((const audio_player_reply*)body)->status) == AUDIO_PLAYER_OK)
                    reply = 1;
                break;
            default:
                break;
        }
    }
    abi->imports.delete_message(body);
}
static const audio_method methods[] = {{AUDIO_CM_RECEIVE, receive}, {0, NULL}};

/* Only this object's audio replies bypass general ComMgr/UI dispatch. */
int audio_cm_dispatch(uintptr_t address) {
    if (!registered || address < 4096 || !fw_isi_object_valid(audio_cm_id(&object, abi)))
        return 0;
    const fw_isi_header* header = (const fw_isi_header*)address;
    if (header->type == FW_MESSAGE_INTERNAL || fw_isi_length(header) < sizeof(audio_message) + 2 ||
        fw_isi_receiver(header) != audio_cm_id(&object, abi) ||
        (header->resource != AUDIO_RESOURCE_POLICY && header->resource != AUDIO_RESOURCE_MUSIC))
        return 0;
    audio_message* body = (audio_message*)fw_isi_body((fw_isi_header*)header);
    receive(&object, AUDIO_CM_RECEIVE, fw_isi_sender(header), (uint8_t*)body,
            AUDIO_CODE(header->resource, body->message));
    return 1;
}
static int wait_reply(void) {
    uint32_t start = fw_os_current_time_read_in_ms();
    for (unsigned budget = 0; !reply && budget < AUDIO_REPLY_PUMP_LIMIT; ++budget) {
        s40_pump();
        if (fw_os_current_time_read_in_ms() - start >= AUDIO_REPLY_TIMEOUT_MS)
            break;
    }
    return reply == 1;
}
static int command(unsigned id, unsigned response) {
    audio_player_command* packet = message(sizeof(*packet), id);
    if (!packet)
        return 0;
    audio_put_be16(packet->handle, handle);
    send(AUDIO_RESOURCE_MUSIC, packet, response);
    return wait_reply();
}
static unsigned queued_bytes(void) {
    /* Consumer cancellation retains the source until producer EOF. */
    return source ? audio_source_queued(source, abi) : 0;
}
static int source_status(void) {
    return source ? (int)audio_source_state(source, abi) : AUDIO_SOURCE_EOF;
}
static int enqueue(const void* data, unsigned bytes) {
    void* pcm = fw_os_block_alloc_no_wait(bytes);
    void* fragment = fw_os_block_alloc_no_wait(sizeof(audio_fragment_storage));
    if (!pcm || !fragment) {
        if (pcm)
            fw_os_block_dealloc(pcm);
        if (fragment)
            fw_os_block_dealloc(fragment);
        return -1;
    }
    if (silent)
        memset(pcm, 0, bytes);
    else
        memcpy(pcm, data, bytes);
    abi->imports.fragment_init(fragment, pcm, bytes, 0, NULL);
    /* High-water (1) and cancellation (4) both consume the fragment. */
    return abi->imports.source_write(source, fragment) == AUDIO_SOURCE_CANCELLED ? -1 : 1;
}
int audio_cm_status(void) {
    if (state == 1 && source_status() > 2)
        state = -2;
    return state;
}
unsigned audio_cm_queued(void) { return stride ? queued_bytes() / stride : 0; }
int audio_cm_write(const int16_t* pcm, unsigned frames) {
    if (audio_cm_status() != 1)
        return -1;
    unsigned bytes = queued_bytes();
    unsigned available = bytes >= max_bytes ? 0 : (max_bytes - bytes) / stride;
    if (frames > available)
        frames = available;
    if (frames > AUDIO_WRITE_MAX_FRAMES)
        frames = AUDIO_WRITE_MAX_FRAMES;
    if (!frames)
        return 0;
    if (enqueue(pcm, frames * stride) < 0) {
        state = -2;
        return -1;
    }
    return (int)frames;
}
static void close_route(void) {
    /* A consumed delete is never retried against a possibly reassigned ID. */
    if (!route_client || !route_delete)
        return;
    audio_client_message* packet = route_delete;
    route_delete = NULL;
    packet->client = (uint8_t)route_client;
    send(AUDIO_RESOURCE_POLICY, packet, AUDIO_CLIENT_DELETED);
    wait_reply();
}
void audio_cm_close(void) {
    state = 0;
    if (!abi)
        return;
    if (source) {
        void* owned = source;
        source = NULL;
        abi->imports.source_eof(owned);
        if (!published)
            abi->imports.class_call(owned, AUDIO_CLASS_DELETE);
    }
    if (registered) {
        if (opened)
            command(AUDIO_PLAYER_CLOSE, AUDIO_PLAYER_CLOSED);
        if (route_pending) {
            expected = AUDIO_CODE(AUDIO_RESOURCE_POLICY, AUDIO_CLIENT_CREATED);
            expected_serial = route_serial;
            reply = 0;
            wait_reply();
        }
        close_route();
        if (policy_id) {
            audio_policy_delete* packet = message(sizeof(*packet), AUDIO_POLICY_REQUEST);
            if (packet) {
                audio_put_be16(packet->header.application, policy_id);
                packet->header.count = 1;
                packet->application.block.id = AUDIO_POLICY_APPLICATION_DELETE;
                packet->application.block.length = sizeof(packet->application);
                audio_put_be16(packet->application.application, policy_id);
                send(AUDIO_RESOURCE_POLICY, packet, AUDIO_POLICY_REPLY);
                wait_reply();
            }
        }
    }
    if (published)
        abi->imports.source_forget(source_id);
    if (registered) {
        s40_pump();
        close_route(); /* delayed CREATE received while deleting the policy */
        abi->imports.unregister_object(&object);
    }
    if (route_delete) {
        abi->imports.delete_message(route_delete);
        route_delete = NULL;
    }
    registered = published = opened = 0;
    policy_id = stream_id = handle = 0;
    stride = expected = 0;
    /* Keep an unacknowledged route ID only as uncertainty; never reuse it in a
     * new registration, where the server may have reassigned that number. */
}
static int open_policy(void) {
    if (abi->policy == AUDIO_POLICY_NONE)
        return 1;
    audio_policy_header* header;
    audio_stream_create* stream;
    if (abi->policy == AUDIO_POLICY_IMPLICIT) {
        audio_policy_implicit* packet = message(sizeof(*packet), AUDIO_POLICY_REQUEST);
        if (!packet)
            return 0;
        header = &packet->header;
        stream = &packet->stream;
        header->count = 1;
    } else {
        audio_policy_explicit* packet = message(sizeof(*packet), AUDIO_POLICY_REQUEST);
        if (!packet)
            return 0;
        header = &packet->header;
        stream = &packet->stream;
        header->count = 2;
        packet->application.block.id = AUDIO_POLICY_APPLICATION;
        packet->application.block.length = sizeof(packet->application);
        audio_put_be16(packet->application.application_type, AUDIO_APPLICATION_MEDIA);
        audio_put_be16(packet->application.subtype, AUDIO_APPLICATION_PLAYBACK);
        audio_put_be32(packet->application.flags, AUDIO_APPLICATION_FLAGS);
    }
    stream->block.id = AUDIO_POLICY_STREAM_CREATE;
    stream->block.length = sizeof(*stream);
    stream->count = 1;
    send(AUDIO_RESOURCE_POLICY, header, AUDIO_POLICY_REPLY);
    return wait_reply() && policy_id && stream_id;
}
static int open_route(void) {
    if (abi->route != AUDIO_ROUTE_OWN_IHF)
        return 1;
    /* Reserve deletion before taking a lease: post-claim OOM must not prevent
     * release. Stock AudioServer delete removes every control owned by its ID. */
    route_delete = message(sizeof(*route_delete), AUDIO_CLIENT_DELETE);
    if (!route_delete)
        return 0;
    audio_client_message* client = message(sizeof(*client), AUDIO_CLIENT_CREATE);
    if (!client)
        return 0;
    client->client = AUDIO_CLIENT_MEDIA;
    route_pending = 1;
    route_serial = client->message.transaction;
    send(AUDIO_RESOURCE_POLICY, client, AUDIO_CLIENT_CREATED);
    if (!wait_reply() || !route_client)
        return 0;
    audio_control_request* control = message(sizeof(*control), AUDIO_CONTROL_SET);
    if (!control)
        return 0;
    control->header.client = (uint8_t)route_client;
    control->header.group = AUDIO_OUTPUT_GROUP;
    control->header.count = 1;
    control->value.block.id = AUDIO_CONTROL_VALUE;
    control->value.block.length = sizeof(control->value);
    control->value.property = AUDIO_OUTPUT_PROPERTY;
    audio_put_be16(control->value.value, AUDIO_OUTPUT_IHF);
    send(AUDIO_RESOURCE_POLICY, control, AUDIO_CONTROL_SET_DONE);
    return wait_reply();
}
static int prime_source(const s40_audio_format* format) {
    if (abi->transport == AUDIO_TRANSPORT_WAV) {
        audio_wav_header header = {0};
        memcpy(header.riff, "RIFF", sizeof(header.riff));
        audio_put_le32(header.riff_size, 0x7ffff024);
        memcpy(header.wave, "WAVE", sizeof(header.wave));
        memcpy(header.fmt, "fmt ", sizeof(header.fmt));
        audio_put_le32(header.fmt_size, 16);
        audio_put_le16(header.format, 1);
        audio_put_le16(header.channels, format->channels);
        audio_put_le32(header.rate, rate);
        audio_put_le32(header.bytes_per_second, rate * stride);
        audio_put_le16(header.block_align, stride);
        audio_put_le16(header.bits, AUDIO_PCM_BITS);
        memcpy(header.data, "data", sizeof(header.data));
        audio_put_le32(header.data_size, 0x7ffff000);
        if (enqueue(&header, sizeof(header)) < 0)
            return 0;
    }
    int16_t silence[AUDIO_WRITE_MAX_FRAMES] = {0};
    unsigned blocks = abi->transport == AUDIO_TRANSPORT_RAW_PCM ? 2 : 4;
    unsigned bytes = abi->transport == AUDIO_TRANSPORT_RAW_PCM ? AUDIO_PCM_BLOCK_FRAMES * stride
                                                               : sizeof(silence);
    for (unsigned i = 0; i < blocks; ++i)
        if (enqueue(silence, bytes) < 0)
            return 0;
    return 1;
}
static int open_player(const s40_audio_format* format) {
    unsigned bytes = sizeof(audio_player_open_header) + sizeof(audio_playback_descriptor);
    if (abi->policy != AUDIO_POLICY_NONE)
        bytes += sizeof(audio_policy_stream);
    if (abi->transport == AUDIO_TRANSPORT_RAW_PCM)
        bytes += sizeof(audio_pcm_descriptor);
    audio_player_open_header* packet = message(bytes, AUDIO_PLAYER_OPEN);
    if (!packet)
        return 0;
    audio_put_be32(packet->source_type, source_id[1]);
    audio_put_be32(packet->source_key, source_id[0]);
    audio_put_be32(packet->mime,
                   abi->transport == AUDIO_TRANSPORT_RAW_PCM ? AUDIO_MIME_PCM : AUDIO_MIME_WAV);
    packet->mode = 3;
    /* Use the current native level range after acquiring output policy. */
    packet->volume =
            (uint8_t)(abi->volume_percent ? abi->imports.volume_scale(format->volume, 0, 100)
                                          : format->volume);
    packet->retain = 1;
    packet->count = 1;
    audio_playback_descriptor* playback = (audio_playback_descriptor*)(packet + 1);
    playback->block.id = AUDIO_PLAYBACK_SUBBLOCK;
    playback->block.length = sizeof(*playback);
    playback->enabled = 1;
    audio_put_be32(playback->repeat_count, 1);
    uint8_t* next = (uint8_t*)(playback + 1);
    if (abi->policy != AUDIO_POLICY_NONE) {
        audio_policy_stream* policy = (audio_policy_stream*)next;
        policy->block.id = AUDIO_STREAM_SUBBLOCK;
        policy->block.length = sizeof(*policy);
        audio_put_be16(policy->stream, stream_id);
        next += sizeof(*policy);
        ++packet->count;
    }
    if (abi->transport == AUDIO_TRANSPORT_RAW_PCM) {
        audio_pcm_descriptor* pcm = (audio_pcm_descriptor*)next;
        pcm->block.id = AUDIO_PCM_SUBBLOCK;
        pcm->block.length = sizeof(*pcm);
        pcm->channels = (uint8_t)format->channels;
        pcm->bits = AUDIO_PCM_BITS;
        audio_put_be16(pcm->frames, AUDIO_PCM_BLOCK_FRAMES);
        audio_put_be32(pcm->rate, rate);
        ++packet->count;
    }
    send(AUDIO_RESOURCE_MUSIC, packet, AUDIO_PLAYER_OPENED);
    return wait_reply();
}
int audio_cm_available(void) { return !route_client && !route_pending; }
int audio_cm_open(const audio_abi* configuration, const s40_audio_format* format) {
    int stage = 10;
    if (!audio_cm_available()) {
        state = -25;
        return state;
    }
    abi = configuration;
    if (!abi)
        return -stage;
    silent = abi->volume_percent && !format->volume;
    rate = format->rate;
    stride = format->channels * sizeof(int16_t);
    unsigned buffer_ms = format->buffer_ms ? format->buffer_ms : 500;
    unsigned max_frames = rate * buffer_ms / 1000;
    max_bytes = max_frames * stride;
    memset(&object, 0, sizeof(object));
    klass.self = &klass;
    klass.parent = (const audio_class*)abi->parent_class;
    klass.methods = methods;
    klass.size_flags = audio_cm_size(abi) << 16;
    klass.name = "S40_PCM";
    object.classic.klass = &klass;
    audio_cm_set_index(&object, abi, 0xffff);
    if (!abi->imports.register_object(&object, 0, 0))
        goto fail;
    registered = 1;
    abi->imports.ready(&object);
    stage = 20;
    if (!open_policy())
        goto fail;
    stage = 25;
    if (!open_route())
        goto fail;
    stage = 30;
    source = abi->imports.source_create();
    if (!source || !abi->imports.source_share(source))
        goto fail;
    unsigned low_frames = max_frames / 8;
    if (low_frames > AUDIO_WRITE_MAX_FRAMES)
        low_frames = AUDIO_WRITE_MAX_FRAMES;
    abi->imports.source_limits(source, max_bytes, low_frames * stride);
    const uint32_t* id = abi->imports.source_id(source);
    if (!id)
        goto fail;
    source_id[0] = id[0];
    source_id[1] = id[1];
    published = 1;
    stage = 40;
    if (!prime_source(format))
        goto fail;
    stage = 50;
    if (!open_player(format))
        goto fail;
    stage = 60;
    if (!command(AUDIO_PLAYER_PLAY, AUDIO_PLAYER_PLAYING))
        goto fail;
    state = 1;
    return 1;
fail:
    audio_cm_close();
    state = -stage;
    return state;
}
