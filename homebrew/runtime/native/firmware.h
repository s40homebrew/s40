/* SPDX-License-Identifier: MIT */
#ifndef S40_INTERNAL_FIRMWARE_H
#define S40_INTERNAL_FIRMWARE_H
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

#include "firmware_api.h"
/* Firmware bindings are resolved by Java and installed by the native loader. */
extern volatile int* fw_lcd_owner;
void* fw_os_block_alloc_no_wait(unsigned);
void fw_os_block_dealloc(void*);
uint32_t fw_os_current_time_read_in_ms(void);
unsigned fw_os_task_own_id_read(void);
unsigned fw_msg_count(void);
uintptr_t fw_os_msg_receive(void);
void fw_CM_ComMgr_ProcessPhonetMessage(void*);
int fw_CM_ComMgr_ProcessInternalQueue(void);
void fw_CM_ComMgr_ScanISISubscriptions(void);
void fw_CM_Timer_Timeout(unsigned);
int fw_vm_timer(unsigned);
int fw_app_id(void);
int fw_event_pop(int, int*, int*, int*);
int fw_filesys_open(const uint16_t*, unsigned, unsigned);
int fw_filesys_close(int);
int fw_filesys_read(int, void*, unsigned);
int fw_filesys_write(int, const void*, unsigned);
int fw_filesys_lseek(int, int, int);
int fw_filesys_unlink(const uint16_t*);
int fw_filesys_mkdir(const uint16_t*, unsigned);
int fw_gdi_display_master_reserve_enable(unsigned);
void fw_gdi_display_master_reserve_disable(unsigned, int);
void fw_gdi_display_master_update(void*, int, int, int, int, int, int);
/* Session-internal tracked file/allocation teardown, after display release. */
void s40_cleanup(void);
#endif
