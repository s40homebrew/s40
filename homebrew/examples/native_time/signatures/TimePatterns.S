@ SPDX-License-Identifier: MIT
@ Assembly templates for TimeResolver.
@ The specialized resolver validates variable operands and patch-state alternatives.
@ Exact arrays preserve the decoder markers and any patch replacement bytes.

@ Checked boot range walker: two variable BL halves and one LDR literal.
@ Boot range-table walker; rangeAt validates the call and literal opcode placeholders.
@ pattern RANGE halfwords thumb RANGE
    push {r4, r5, r6, lr}
    movs r4, r0
    movs r5, r1
    bl #0
    lsls r0, r0, #0x1f
    beq #24
    ldr r0, [pc, #0]
    adds r2, r4, r5
    b #24
    cmp r1, r4
    bhi #4
    ldr r3, [r0, #4]
    cmp r3, r4
    bhi #8
    cmp r2, r1
    blo #8
    ldr r1, [r0, #4]
    cmp r2, r1
    bhs #2
    movs r0, #1
    pop {r4, r5, r6, pc}
    adds r0, #8
    ldr r1, [r0]
    cmp r1, #0
    bne #-34
    movs r0, #0
    pop {r4, r5, r6, pc}
@ end

@ ARM data/instruction cache synchronization used before entering the native clock routine.
@ pattern CACHE words arm CACHE
    push {r4, r5, r6, lr}
    add r5, r0, r1
    bic r0, r0, #0x1f
    mcr p15, #0, r0, c7, c14, #1
    mcr p15, #0, r0, c7, c5, #1
    add r0, r0, #0x20
    mcr p15, #0, r0, c7, c14, #1
    mcr p15, #0, r0, c7, c5, #1
    add r0, r0, #0x20
    cmp r0, r5
    blo #-36
    mcr p15, #0, r5, c7, c10, #4
    pop {r4, r5, r6, pc}
@ end

@ The branch destinations vary; every other instruction must match exactly.
@ Interpreter native-method dispatch, with branch destinations validated by the specialized matcher.
@ pattern DISPATCH words arm DISPATCH
    ldrb r2, [lr, #1]
    ldrb r3, [lr, #2]
    mov sl, lr
    add lr, sl, #3
    orr r1, r3, r2, lsl #8
    ldr r1, [r8, r1, lsl #2]
    ldrh r4, [r1, #0x1e]
    ldr r0, [r1, #0x14]
    sub r4, r6, r4, lsl #2
    ldr r2, [r4]
    cmp r2, #0
    beq #0
    ldr r2, [r1, #4]
    sub r3, r4, #4
    cmp r2, #0
    beq #0
    ldr sb, [r5, #0x438]
    tst r0, #8
    addeq r3, r3, #4
    cmp sb, #0
    beq #0
    str r3, [sb, #0x20]
    str r4, [sb, #0x24]
    str sb, [r5, #0x468]
    str r1, [r5, #0x448]
    add sl, r5, #0x420
    stm sl, {r6, r7, r8, fp, lr}
    blx r2
@ end

@ Native-invoke guard whose conditional branch must reach the checked dispatcher.
@ pattern INVOKE words arm INVOKE
    mov r1, r4
    ldrh r4, [r1, #0x1e]
    ldr r0, [r1, #0x14]
    add lr, sl, #3
    sub r4, r6, r4, lsl #2
    tst r0, #0x100
    bne #0
@ end
