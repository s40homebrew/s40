/* SPDX-License-Identifier: MIT */
/* Persistent shrink-entry wrapper. Bind the four marker words in the OS-owned
 * copied payload, never in Java storage. callback(header, 1) may extend/activate;
 * it must return normally on pending/error. All incoming registers and flags
 * are restored before replaying the original shrink PUSH and selector call.
 */
.syntax unified
.arm
.section .text.growth_entry,"ax",%progbits
.global growth_entry
.type growth_entry,%function
growth_entry:
    push {r0-r12,lr}
    mrs r1,cpsr
    push {r1,r2}
    ldr r0,growth_header_address
    ldr r12,growth_callback_address
    mov r1,#1
    blx r12
    pop {r1,r2}
    msr cpsr_f,r1
    pop {r0-r12,lr}
    push {r3,r4,r5,lr}
    adr lr,growth_after_selector
    ldr pc,growth_selector_address
growth_after_selector:
    ldr pc,growth_continuation_address
.align 2
.global growth_entry_header
.global growth_entry_callback
.global growth_entry_original
.global growth_entry_continuation
growth_entry_header:
growth_header_address:       .word 0xB0057E31
growth_entry_callback:
growth_callback_address:     .word 0xB0057E32
growth_entry_original:
growth_selector_address:     .word 0xB0057E33
growth_entry_continuation:
growth_continuation_address: .word 0xB0057E34
.size growth_entry,.-growth_entry

/* A raw linked symbol is an offset in this relocation-free binary. Recover
 * the live load address from PC before copying the resident image. */
.global growth_image_base
.type growth_image_base,%function
growth_image_base:
    adr r0,growth_image_base
    ldr r1,growth_image_offset
    sub r0,r0,r1
    bx lr
growth_image_offset: .word growth_image_base
.size growth_image_base,.-growth_image_base
