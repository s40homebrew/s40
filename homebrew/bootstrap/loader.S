/* SPDX-License-Identifier: MIT */
/* KNI belongs only to this bootstrap/teardown image, never to the application image. */
#include "firmware_api.h"
.syntax unified
.arm
.section .text.entry,"ax",%progbits
.global native_entry
.type native_entry,%function
native_entry:
    push {r4-r8,lr}
    sub sp,sp,#8
    adr r5, loader_api
    mov r0,#1
    mov r1,sp
    ldr r2,[r5,#0]
    blx r2
    ldr r0,[sp]
    cmp r0,#0
    addne r0,r0,#12
    mov r4,r0
    mov r1,r5
    blne loader_command
    ldr r2,[r5,#4]
    blx r2
    cmp r4,#0
    beq 4f
    ldr r1,[r4,#4]
    sub r1,r1,#3
    cmp r1,#1
    bhi 4f
    ldr r0,[r4,#336]  /* p[84]: native loader's own allocation */
    cmp r0,#0
    beq 4f
    mov r1,#0
    str r1,[r4,#336]
    ldr ip,[r5,#12]
    add sp,sp,#8
    pop {r4-r8,lr}
    bx ip             /* tail-free self: no instruction in freed RAM follows */
4:
    add sp,sp,#8
    pop {r4-r8,pc}
.balign 4
loader_api:
    .word 0xA40F0000 + API_KNI_PARAM
    .word 0xA40F0000 + API_KNI_RETURN
    .word 0xA40F0000 + API_FW_OS_BLOCK_ALLOC_NO_WAIT
    .word 0xA40F0000 + API_FW_OS_BLOCK_DEALLOC
    .word 0xA40F0000 + API_CACHE_SYNC
.size native_entry,.-native_entry
.section .text.stack,"ax",%progbits
.global on_native_stack
.type on_native_stack,%function
/* r0=entry, r1=stack_top, r2=diagnostics */
on_native_stack:
    push {r4-r11,lr}
    sub sp,sp,#4
    mov r4,sp
    bic sp,r1,#7
    mov r3,r0
    mov r0,r2
    blx r3
    mov sp,r4
    add sp,sp,#4
    pop {r4-r11,pc}
.size on_native_stack,.-on_native_stack
