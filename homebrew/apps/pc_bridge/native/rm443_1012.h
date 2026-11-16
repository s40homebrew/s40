/* SPDX-License-Identifier: MIT */
#ifndef PB_RM443_1012_H
#define PB_RM443_1012_H
/* Protocol-task adapter for Nokia 6303c RM-443 10.12. New device adapters
 * must establish message ownership, class lookup AND cached receive records,
 * executable OS allocations and cache synchronization before publishing hooks.
 * The installer validates live class/receiver/prologue instead of trusting a
 * model-name match. See devices/rm443/10.12/analysis/native_bridge/README.txt. */
#define PB_PANEL_POINTER 0xd80f0658u
#define PB_ORIGINAL_CLASS 0xf11717c0u
#define PB_ORIGINAL_RX 0xf060ffc5u
#define PB_ORIGINAL_PROLOGUE 0xb0a9b5ffu
#define PB_CLASS_SIZES 0x011c000du
#define PB_TASK_CONTEXTS 0xd80cd36cu
#define PB_DEVICE 0xd80cc9f0u
#define PB_OBJECT_TASK 0xf0adaae0u
#define PB_ALLOC 0xf04f0341u
#define PB_FREE 0xf04efa6fu
#define PB_MESSAGE_NEW 0xf0b9b857u
#define PB_MESSAGE_DELETE 0xf0b9b869u
#define PB_CALL_CLASS3 0xf04ed2f3u
#define PB_RAM_START 0xd8000000u
#define PB_RAM_END 0xda000000u
#define PB_ROM_START 0xf0100400u
#define PB_ROM_END 0xf14db000u
#endif
