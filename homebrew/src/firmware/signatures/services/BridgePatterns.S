@ SPDX-License-Identifier: MIT
@ Instruction signatures for BridgeBindings.
@ Calls and literal operands are decoded and validated by the Java resolver.

@ PANEL matches the constructor's superclass and registration sequence.
@ These positions are inside that checked sequence, not ROM displacements.
@ pattern PANEL recipe thumb PANEL
    movs r2, #0
    str r2, [sp]
    str r2, [sp, #4]
    ldr r2, [pc, #0] @ mask=literal
    ldr r0, [pc, #0] @ mask=literal
@ anchor
    ldr r1, [sp, #0xc]
    adds r2, r2, #1
    movs r3, #0
    blx #0 @ mask=call
    blx #0 @ mask=call
    ldr r7, [pc, #0] @ mask=literal
    subs r7, #0x10
    ldr r0, [r7, #4]
    blx #0 @ mask=call
    ldr r0, [r7, #4]
    movs r2, #0xe
    movs r1, #0x53
    blx #0 @ mask=call
    ldr r1, [pc, #0] @ mask=literal
    ldr r0, [r7, #4]
    blx #0 @ mask=call
    movs r0, #1
    strb r0, [r7]
    blx #0 @ mask=call
    str r0, [r6, #0x1c]
    ldr r1, [pc, #0] @ mask=literal
    ldr r0, [r7, #4]
    blx #0 @ mask=call
    str r0, [r7, #8]
@ end

@ MessageParser receive-method prologue; binds the method slot and expected message argument layout.
@ pattern RECEIVE recipe thumb RECEIVE
    push {r0, r1, r2, r3, r4, r5, r6, r7, lr}
@ anchor
    sub sp, #0xa4
    movs r4, r3
    ldr r3, [sp, #0xa4]
    ldr r0, [sp, #0xac]
    str r3, [sp, #0x94]
    str r0, [sp, #0x90]
    ldr r2, [sp, #0xc8]
    movs r0, #0
    adds r3, #0xff
    adds r3, #1
    str r0, [sp, #0x8c]
    str r3, [sp, #0xa0]
    ldr r3, [sp, #0xa4]
    movs r5, #0xe9
    movs r7, r3
    adds r3, #0xe0
    str r3, [sp, #0x9c]
    ldr r3, [pc, #0] @ mask=literal
    lsls r5, r5, #4
    ldr r3, [r3]
    ldr r1, [pc, #0] @ mask=literal
    movs r6, r3
    adds r3, #0xc0
@ end
