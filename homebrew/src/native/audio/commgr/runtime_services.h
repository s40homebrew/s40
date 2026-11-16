/* SPDX-License-Identifier: MIT */
/* Services supplied by the embedding native runtime (or a standalone port).
 * Keep this small boundary independent of generated firmware declarations. */
#ifndef S40_AUDIO_RUNTIME_SERVICES_H
#define S40_AUDIO_RUNTIME_SERVICES_H
#include <stdint.h>

void* fw_os_block_alloc_no_wait(unsigned);
void fw_os_block_dealloc(void*);
uint32_t fw_os_current_time_read_in_ms(void);
void s40_pump(void);

#endif
