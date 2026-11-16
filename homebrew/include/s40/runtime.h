/* SPDX-License-Identifier: MIT */
/* Small native S40 API. Currently implemented by the audited Nokia profiles. */
#ifndef S40_RUNTIME_H
#define S40_RUNTIME_H
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

#include "s40/events.h"
/* Retained here for source compatibility; display-only clients may include
 * s40/display.h directly. */
#include "s40/display.h"

/* Implement this entry point in the application. Return zero to quit normally.
 * The runtime owns native_main, message dispatch, display release, tracked
 * allocations, open-file cleanup and the return to Java.
 */
int s40_app_main(void);
void s40_finish(int status) __attribute__((noreturn));
void s40_error(const char* format, ...) __attribute__((noreturn));
void s40_on_exit(void (*function)(void), int on_error);
void s40_pump(void);
/* Call s40_pump before draining a batch; polling itself only dequeues. */
int s40_poll_event(s40_event* event);
uint32_t s40_uptime_ms(void);
void s40_sleep_ms(uint32_t duration);
unsigned s40_input_layout(void);
/* Application-defined bits supplied by the Java launcher's nativeOptions(). */
uint32_t s40_launch_options(void);
/* Optional launcher UTF-8 string (max 4095 bytes), valid until session exit.
 * Empty when the launcher supplies none. Do not modify or free this storage.
 */
const char* s40_launch_argument(void);
/* Relative file paths use this root; include its trailing slash. */
void s40_set_root(const char* root);
const char* s40_get_root(void);
void s40_set_log(FILE* file);
void s40_log_close(void);
/* Low-level session diagnostics, not a stable application storage area.
 * The descriptor layout is defined by tools/native_contract.py.
 */
extern uint32_t* s40_result;
#endif
