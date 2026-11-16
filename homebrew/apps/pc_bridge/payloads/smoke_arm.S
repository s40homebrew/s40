/* SPDX-License-Identifier: MIT */
.syntax unified
.arm
.section .text.entry,"ax",%progbits
.global bridge_main
.type bridge_main,%function
bridge_main:
    add r0, r0, r1
    eor r1, r1, r2
    mov r2, #0x55
    mov r3, #0xaa
    bx lr
.size bridge_main, .-bridge_main
