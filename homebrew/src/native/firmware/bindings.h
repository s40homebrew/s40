/* SPDX-License-Identifier: MIT */
#ifndef S40_INTERNAL_FIRMWARE_H
#define S40_INTERNAL_FIRMWARE_H
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

#include "firmware_api.h"
/* Firmware bindings are resolved by Java and installed by the native loader. */
extern volatile int* fw_lcd_owner;
/* Plain GDI canvas accepted by native copy/refresh routines. For the opaque
 * ARGB path, pixels must have a writable four-byte HAL prefix immediately
 * before them: uint16 format, uint16 row width. Unknown fields stay zero.
 */
struct fw_gdi_canvas {
    uint32_t* pixels;
    uint16_t width, height, stride;
    uint8_t format, pad;
    uint32_t extra[3];
};
/* Older ARM926 GDI stores dimensions after a reserved pointer-sized field. */
struct fw_gdi_canvas_legacy {
    uint32_t* pixels;
    uint32_t unused;
    uint16_t width, height;
    uint8_t format, pad[3];
};
#if defined(__arm__) && UINTPTR_MAX == UINT32_MAX
/* These are firmware ABI records only on the target's 32-bit ARM ABI. Host
 * service tests use native pointers and must not claim the target layout. */
#define FW_CANVAS_OFFSET(type, field, offset) \
    typedef char type##_##field##_offset[(offsetof(struct type, field) == (offset)) ? 1 : -1]
FW_CANVAS_OFFSET(fw_gdi_canvas, pixels, 0);
FW_CANVAS_OFFSET(fw_gdi_canvas, width, 4);
FW_CANVAS_OFFSET(fw_gdi_canvas, height, 6);
FW_CANVAS_OFFSET(fw_gdi_canvas, format, 10);
FW_CANVAS_OFFSET(fw_gdi_canvas_legacy, pixels, 0);
FW_CANVAS_OFFSET(fw_gdi_canvas_legacy, width, 8);
FW_CANVAS_OFFSET(fw_gdi_canvas_legacy, height, 10);
FW_CANVAS_OFFSET(fw_gdi_canvas_legacy, format, 12);
typedef char fw_gdi_canvas_size[(sizeof(struct fw_gdi_canvas) == 24) ? 1 : -1];
typedef char fw_gdi_canvas_legacy_size[(sizeof(struct fw_gdi_canvas_legacy) == 16) ? 1 : -1];
#undef FW_CANVAS_OFFSET
#endif
/* OS heap blocks have firmware lifetime. Only pass unmodified OS allocations
 * to dealloc; SDK malloc adds its own tracking prefix in memory.c. */
void* fw_os_block_alloc_no_wait(unsigned bytes);
void fw_os_block_dealloc(void* block);
/* Milliseconds wrap at 2^32; use unsigned differences for elapsed time. */
uint32_t fw_os_current_time_read_in_ms(void);
unsigned fw_os_task_own_id_read(void);
unsigned fw_msg_count(void);
/* Returns zero, an integer timer tag, or an owned native message pointer.
 * See firmware/message.h before interpreting or transferring ownership. */
uintptr_t fw_os_msg_receive(void);
void fw_CM_ComMgr_ProcessPhonetMessage(void* owned_message);
int fw_CM_ComMgr_ProcessInternalQueue(void);
void fw_CM_ComMgr_ScanISISubscriptions(void);
void fw_CM_Timer_Timeout(unsigned timer_id);
int fw_vm_timer(unsigned message_tag);
int fw_app_id(void);
int fw_event_pop(int application_id, int* category, int* type, int* value);
/* Native descriptors include zero. Negative results indicate failure; read
 * and write return byte counts, lseek returns the new absolute position.
 * These APIs do not accept host O_* flags or SDK/newlib FILE pointers.
 * Paths/flags and the SDK conversion limit are in firmware/files.h. */
int fw_filesys_open(const uint16_t* path, unsigned flags, unsigned mode);
int fw_filesys_close(int descriptor);
int fw_filesys_read(int descriptor, void* buffer, unsigned bytes);
int fw_filesys_write(int descriptor, const void* buffer, unsigned bytes);
int fw_filesys_lseek(int descriptor, int offset, int origin);
int fw_filesys_unlink(const uint16_t* path);
int fw_filesys_mkdir(const uint16_t* path, unsigned mode);
/* Master-display ID 1 returns a reservation token. Release that same token;
 * callers must not invoke this pair for the profile's foreground-only path. */
int fw_gdi_display_master_reserve_enable(unsigned display_id);
void fw_gdi_display_master_reserve_disable(unsigned display_id, int token);
/* canvas is the current-layout record above, never its bare pixel pointer.
 * The ordinary runtime passes a zero final argument; wider semantics are not
 * part of this binding. Bounds describe the destination update rectangle. */
void fw_gdi_display_master_update(struct fw_gdi_canvas* canvas, int display_id, int x, int y, int width,
                                  int height, int reserved_zero);
#endif
