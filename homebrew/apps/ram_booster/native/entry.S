/* SPDX-License-Identifier: MIT */
/* Zero Java arguments: no KNI operand-stack cleanup is necessary. The live
 * int[] data pointer is bound before entering the synchronized carrier. */
.syntax unified
.arm
.section .text.entry,"ax",%progbits
.global native_entry
.type native_entry,%function
native_entry:
    ldr r0, output_address
    b heap_booster_main
.align 2
output_address:
    .word 0xB0057E01
.size native_entry,.-native_entry
