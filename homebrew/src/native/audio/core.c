/* SPDX-License-Identifier: MIT */
#include "internal.h"
#include "s40/audio.h"
static const s40_audio_driver* backend;
static int (*message_dispatch)(uintptr_t);
void s40_audio_bind(const s40_audio_driver* driver) {
    s40_audio_close();
    message_dispatch = 0;
    backend = driver;
}
void s40_audio_set_message_dispatch(int (*dispatch)(uintptr_t)) { message_dispatch = dispatch; }
int s40_audio_dispatch_message(uintptr_t message) {
    return message_dispatch ? message_dispatch(message) : 0;
}
int s40_audio_open(const s40_audio_format* format) {
    if (!backend || !format || (format->rate != 11025 && format->rate != 22050) ||
        (format->channels != 1 && format->channels != 2) || format->volume > 100 ||
        (format->buffer_ms && (format->buffer_ms < 100 || format->buffer_ms > 500)))
        return -1;
    s40_audio_close();
    return backend->open(format);
}
int s40_audio_write(const int16_t* samples, unsigned frames) {
    if (!backend || (!samples && frames))
        return -1;
    if (!frames)
        return 0;
    return backend->write(samples, frames);
}
unsigned s40_audio_queued(void) { return backend ? backend->queued() : 0; }
int s40_audio_status(void) { return backend ? backend->status() : 0; }
void s40_audio_close(void) {
    if (backend)
        backend->close();
}
