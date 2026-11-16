/* SPDX-License-Identifier: MIT */
/* Internal CM work must progress even when the OS queue is empty or key-only. */
#include <assert.h>
#include <stdint.h>

#include "firmware/bindings.h"
#include "firmware/message.h"
#include "s40/runtime.h"

static uint32_t descriptor[1200];
static unsigned queued_keys, keys, internal_pending, active;
static unsigned internal_calls, subscriptions, restores, awake_calls;
static uintptr_t messages[5];
static unsigned message_count, message_index, phonet_calls, vm_timers, cm_timers;
static fw_isi_header external_packet, internal_packet;
unsigned fw_msg_count(void) { return queued_keys + message_count - message_index; }
uintptr_t fw_os_msg_receive(void) {
    if (queued_keys) {
        --queued_keys;
        return 0x1000;
    }
    assert(message_index < message_count);
    return messages[message_index++];
}
int s40_video_dispatch_message(uintptr_t msg) {
    if (!active)
        return 0;
    assert(msg == 0x1000);
    ++keys;
    internal_pending = 1; /* Keyboard delivery queued a local service message. */
    return 1;
}
int s40_video_prepare_services(void) {
    if (active && !internal_pending)
        return 0;
    if (active) {
        ++restores;
        active = 0;
    }
    return 1;
}
void s40_display_service_awake(void) { ++awake_calls; }
int fw_CM_ComMgr_ProcessInternalQueue(void) {
    assert(!active);
    ++internal_calls;
    internal_pending = 0;
    return 0;
}
void fw_CM_ComMgr_ScanISISubscriptions(void) {
    assert(!active);
    ++subscriptions;
}
void fw_CM_ComMgr_ProcessPhonetMessage(void* p) {
    assert(!active && p == &external_packet);
    ++phonet_calls;
}
int fw_vm_timer(unsigned timer) {
    assert(timer == FW_OS_TIMER_MESSAGE_BASE + 5 || timer == FW_OS_TIMER_MESSAGE_BASE + 6);
    ++vm_timers;
    return timer == FW_OS_TIMER_MESSAGE_BASE + 6;
}
void fw_CM_Timer_Timeout(unsigned timer) {
    assert(timer == 5);
    ++cm_timers;
}
int fw_event_pop(int app, int* category, int* type, int* value) {
    (void)app;
    (void)category;
    (void)type;
    (void)value;
    return 0;
}
uint32_t fw_os_current_time_read_in_ms(void) { return 0; }
unsigned fw_os_task_own_id_read(void) { return 1; }
int fw_app_id(void) { return 1; }
int s40_app_main(void) { return 0; }
void s40_video_suspend(void) {}
void s40_video_close(void) {}
void s40_audio_close(void) {}
void s40_display_close(void) {}
int s40_display_keep_awake(int enabled) {
    (void)enabled;
    return 0;
}
void s40_cleanup(void) {}

int main(void) {
    s40_result = descriptor;
    active = 1;
    s40_pump();
    assert(!internal_calls && !subscriptions && !restores && awake_calls == 1);
    internal_pending = 1; /* No OS packet wakes the old implementation. */
    s40_pump();
    assert(internal_calls == 1 && subscriptions == 1 && restores == 1);
    active = 1;
    queued_keys = 3;
    s40_pump();
    assert(keys == 3 && !queued_keys && internal_calls == 2 && subscriptions == 2 && restores == 2);
    assert(awake_calls == 3 && !internal_pending);
    /* Ordinary dispatch still distinguishes timer tags from packet pointers,
     * ignores the profile's timer, and leaves internal packets to ComMgr. */
    descriptor[FW_TIMER_MESSAGE_LAST_WORD] = FW_OS_TIMER_MESSAGE_BASE + 8;
    descriptor[FW_IGNORED_TIMER_WORD] = 2;
    descriptor[FW_SPECIAL_TIMER_WORD] = 6;
    internal_packet.type = FW_MESSAGE_INTERNAL;
    messages[0] = FW_OS_TIMER_MESSAGE_BASE + 2;
    messages[1] = FW_OS_TIMER_MESSAGE_BASE + 5;
    messages[2] = FW_OS_TIMER_MESSAGE_BASE + 6;
    messages[3] = (uintptr_t)&external_packet;
    messages[4] = (uintptr_t)&internal_packet;
    message_count = 5;
    s40_pump();
    assert(message_index == message_count && phonet_calls == 1 && vm_timers == 2 && cm_timers == 1);
    assert(internal_calls == 8 && subscriptions == 3 && restores == 2);
    return 0;
}
