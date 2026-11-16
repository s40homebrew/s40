/* SPDX-License-Identifier: MIT */
.syntax unified
.thumb
.section .text.entry,"ax",%progbits
.global bridge_main
.thumb_func
.type bridge_main,%function
bridge_main:
    adds r0, r0, r1
    eors r1, r2
    movs r2, #0x55
    movs r3, #0xaa
    bx lr
.size bridge_main, .-bridge_main
