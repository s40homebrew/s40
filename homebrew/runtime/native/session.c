/* SPDX-License-Identifier: MIT */
/* Native application lifetime and S40 message dispatch; no engine dependency. */
#include <setjmp.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>

#include "firmware.h"
#include "s40/audio.h"
#include "s40/runtime.h"

uint32_t* s40_result;
static jmp_buf finish_env;
static int app_id, exit_status, finishing;
static unsigned pump_depth;
static uint32_t start_ms;
static struct {
    void (*function)(void);
    int on_error;
} exit_callbacks[24];
static unsigned exit_count;

void s40_finish(int status) {
    exit_status = status;
    longjmp(finish_env, 1);
}

void s40_error(const char* format, ...) {
    va_list args;
    va_start(args, format);
    vsnprintf((char*)(s40_result + 16), 256, format, args);
    va_end(args);
    printf("ERROR: %s\n", (char*)(s40_result + 16));
    s40_finish(-1);
}

void s40_on_exit(void (*function)(void), int on_error) {
    if (exit_count < sizeof(exit_callbacks) / sizeof(*exit_callbacks)) {
        exit_callbacks[exit_count].function = function;
        exit_callbacks[exit_count++].on_error = on_error;
    }
}

/* Follow the selected profile's timer/message policy without scheduling KVM. */
void s40_pump(void) {
    if (pump_depth++) {
        --pump_depth;
        return;
    }
    for (unsigned budget = 0; budget < 32 && fw_msg_count(); budget++) {
        uintptr_t msg = fw_os_msg_receive();
        if (!msg)
            continue;
        if (msg >= 192 && msg <= s40_result[FW_TIMER_MESSAGE_LAST_WORD]) {
            unsigned timer = (unsigned)msg - 192;
            if (timer != s40_result[FW_IGNORED_TIMER_WORD]) {
                if (!fw_vm_timer((unsigned)msg))
                    fw_CM_Timer_Timeout(timer);
                else if (timer == s40_result[FW_SPECIAL_TIMER_WORD] &&
                         s40_result[FW_SPECIAL_TIMER_HANDLER_WORD])
                    ((void (*)(void))s40_result[FW_SPECIAL_TIMER_HANDLER_WORD])();
            }
        } else if (*(volatile unsigned char*)msg != 1) {
            fw_CM_ComMgr_ProcessPhonetMessage((void*)msg);
        }
        fw_CM_ComMgr_ProcessInternalQueue();
    }
    fw_CM_ComMgr_ProcessInternalQueue();
    fw_CM_ComMgr_ScanISISubscriptions();
    --pump_depth;
}

int s40_poll_event(s40_event* event) {
    if (!fw_event_pop(app_id, &event->category, &event->type, &event->value))
        return 0;
    // Native sessions yield the display when another phone UI takes focus.
    if (event->category == S40_EVENT_FOCUS && event->type == S40_FOCUS_LOST)
        s40_finish(0);
    return 1;
}

unsigned s40_input_layout(void) { return s40_result[FW_INPUT_LAYOUT_WORD]; }
uint32_t s40_launch_options(void) { return s40_result[FW_LAUNCH_OPTIONS_WORD]; }
uint32_t s40_uptime_ms(void) { return fw_os_current_time_read_in_ms() - start_ms; }
void s40_sleep_ms(uint32_t duration) {
    uint32_t start = fw_os_current_time_read_in_ms();
    do {
        s40_pump();
    } while ((uint32_t)(fw_os_current_time_read_in_ms() - start) < duration);
}

/* Called by the loader on a private stack. Each launch gets a new image/BSS. */
__attribute__((visibility("default"))) void native_main(uint32_t* result) {
    s40_result = result;
    result[0] = 0x444F4F4D;  // Historical descriptor magic; retained for ABI compatibility.
    result[4] = 1;
    result[6] = fw_os_task_own_id_read();
    app_id = fw_app_id();
    result[7] = app_id;
    start_ms = fw_os_current_time_read_in_ms();
    if (!setjmp(finish_env)) {
        if (app_id <= 0)
            s40_error("No native application ID");
        result[4] = 2;
        s40_pump();
        s40_finish(s40_app_main());
    }
    // A failing exit callback may jump back here. Never run callbacks twice.
    if (!finishing) {
        finishing = 1;
        while (exit_count) {
            --exit_count;
            if (!exit_status || exit_callbacks[exit_count].on_error)
                exit_callbacks[exit_count].function();
        }
    }
    s40_audio_close();
    s40_display_close();
    printf("Exit %d, ms %u\n", exit_status, s40_uptime_ms());
    result[3] = exit_status;
    result[10] = s40_uptime_ms();
    s40_cleanup();
}
