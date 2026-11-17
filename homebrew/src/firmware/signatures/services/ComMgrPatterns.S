@ SPDX-License-Identifier: MIT
@ Instruction signatures for ComMgrBindings.
@ Calls and literal operands are decoded and validated by the Java resolver.

@ Positions within the matched SendToObject routing block and task lookup.
@ SendToObject routing block that selects local delivery or resolves the destination task.
@ pattern SEND_OBJECT recipe thumb SEND_OBJECT
    ldr r1, [pc, #0] @ mask=literal
@ anchor
    ldr r0, [sp, #0x68]
    ldrb r1, [r1]
    movs r5, #5
    mvns r5, r5
    cmp r0, r1
    bne #60
    ldr r0, [sp, #0x64]
    blx #0 @ mask=call
    movs r7, r0
    blx #0 @ mask=call
    cmp r7, r0
    bne #44
    ldr r1, [sp, #0x64]
    ldr r0, [sp, #0x70]
    blx #0 @ mask=call
    cmp r0, #0
    beq #24
@ end

@ Decodes an object identifier into device/task components before consulting the task owner.
@ pattern OBJECT_TASK recipe thumb OBJECT_TASK
    push {r3, r4, r5, lr}
    ldr r1, [pc, #0] @ mask=literal
@ anchor
    lsls r4, r0, #0x16
    ldrb r1, [r1]
    lsrs r0, r0, #8
    lsrs r0, r0, #2
    movs r5, #0xff
    lsrs r4, r4, #0x16
    lsls r0, r0, #2
    cmp r0, r1
    bne #10
    ldr r0, [pc, #0] @ mask=literal
    ldrh r0, [r0]
    cmp r4, r0
    bhs #2
    ldr r0, [pc, #0] @ mask=literal
    ldrb r5, [r0, r4]
    blx #0 @ mask=call
    mov r3, sp
    str r0, [sp]
    ldrh r0, [r3]
    mov r2, sp
    lsrs r1, r0, #8
    lsls r0, r0, #8
    orrs r1, r0
    str r1, [sp]
    ldr r1, [pc, #0] @ mask=literal
    movs r0, #2
    subs r1, r1, #2
    blx #0 @ mask=call
    lsrs r0, r4, #8
    lsls r1, r4, #8
    orrs r0, r1
    ldr r1, [pc, #0] @ mask=literal
    str r0, [sp]
    subs r1, #0x42
    movs r0, #2
    mov r2, sp
    blx #0 @ mask=call
    ldr r1, [pc, #0] @ mask=literal
    lsls r0, r5, #8
    str r0, [sp]
    subs r1, #0x40
    movs r0, #2
    mov r2, sp
    blx #0 @ mask=call
    movs r0, r5
    pop {r3, r4, r5, pc}
@ end
