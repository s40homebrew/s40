/* SPDX-License-Identifier: MIT */
.syntax unified
.arm
.text
.global pb_call
.hidden pb_call
.type pb_call, %function
/* AAPCS, ARM/Thumb interworking. Capture r0-r3 immediately on return. */
pb_call:
    push {r4-r6, lr}
    mov r4, r0
    mov r5, r2
    ldm r1, {r0-r3}
    blx r4
    stm r5, {r0-r3}
    pop {r4-r6, pc}
.size pb_call, .-pb_call
