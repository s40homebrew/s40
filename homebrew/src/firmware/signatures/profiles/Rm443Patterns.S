@ SPDX-License-Identifier: MIT
@ Instruction signatures for Rm443Profile.
@ Calls and literal operands are decoded and validated by the Java resolver.

@ Boot range walker: accepts only intervals contained in a zero-terminated ROM range table.
@ pattern RANGE pattern thumb RANGE
    push {r4, r5, r6, lr}
    movs r4, r0
    movs r5, r1
    bl #0 @ mask=target
    lsls r0, r0, #0x1f
    beq #24
    ldr r0, [pc, #0] @ mask=literal
@ anchor
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

@ Cache synchronization over an address range; preserves the paired data/instruction maintenance operations.
@ pattern CACHE pattern arm CACHE
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
@ anchor
    mcr p15, #0, r5, c7, c10, #4
    pop {r4, r5, r6, pc}
@ end

@ Loads a native argument through the current VM frame and stores it through the caller output pointer.
@ pattern KNI_PARAM pattern thumb KNI_PARAM
    ldr r2, [pc, #0] @ mask=literal
    lsls r0, r0, #2
    ldr r2, [r2]
@ anchor
    ldr r2, [r2, #0x20]
    ldr r0, [r2, r0]
    str r0, [r1]
@ end

@ Java task message loop: receives messages, routes timer/Phonet events and drains internal work.
@ pattern TASK_LOOP pattern thumb TASK_LOOP
    blx #0 @ mask=target
@ anchor
    mov r1, sp
    cmp r0, #0
    str r1, [r4, #4]
    beq #44
    movs r1, r0
    movs r2, #0xff
    subs r1, #0xc0
    adds r2, #0x29
    cmp r1, r2
    bhi #22
    lsls r5, r1, #0x10
    lsrs r5, r5, #0x10
    cmp r5, #0x7f
    beq #24
    bl #0 @ mask=target
    cmp r0, #0
    bne #16
    movs r0, r5
    blx #0 @ mask=target
    b #8
    ldrb r1, [r0]
    cmp r1, #1
    beq #2
    bl #0 @ mask=target
    bl #0 @ mask=target
    blx #0 @ mask=target
    cmp r0, #0
    bne #10
    bl #0 @ mask=target
    bl #0 @ mask=target
    cmp r0, #0
    bne #-22
    bl #0 @ mask=target
    movs r0, #0
    str r0, [r4, #4]
    b #-94
@ end

@ Reads the current application ID, returning -1 when no application record exists.
@ pattern APP_ID pattern thumb APP_ID
    ldr r1, [pc, #0] @ mask=literal
    movs r0, #0
@ anchor
    ldr r1, [r1]
    mvns r0, r0
    cmp r1, #0
    beq #0
    ldr r0, [r1, #4]
    bx lr
@ end

@ Removes one event from the Java queue and copies its three payload fields to caller outputs.
@ pattern EVENT_POP pattern thumb EVENT_POP
    push {r3, r4, r5, r6, r7, lr}
    movs r5, r1
    movs r6, r2
    movs r7, r3
    bl #0 @ mask=target
    movs r4, r0
    movs r0, #0
    cmp r4, #0
    beq #54
    ldr r1, [r4, #8]
    cmp r1, #0
@ anchor
    beq #48
    subs r1, r1, #1
    str r1, [r4, #8]
    ldr r0, [r4]
    movs r3, #0xc
    muls r0, r3, r0
    movs r1, #0x28
    adds r0, r0, r4
    ldr r0, [r0, #0xc]
    str r0, [r5]
    ldr r0, [r4]
    muls r0, r3, r0
    adds r0, r0, r4
    ldr r0, [r0, #0x10]
    str r0, [r6]
    ldr r0, [r4]
    muls r0, r3, r0
    adds r0, r0, r4
    ldr r0, [r0, #0x14]
    str r0, [r7]
    ldr r0, [r4]
    adds r0, r0, #1
    blx #0 @ mask=target
    movs r0, #1
    str r1, [r4]
    pop {r3, r4, r5, r6, r7, pc}
@ end

@ Interpreter native-method dispatch: follows method metadata and saves VM registers before entering native code.
@ pattern DISPATCH pattern arm DISPATCH
    ldrb r2, [lr, #1]
    ldrb r3, [lr, #2]
    mov sl, lr
    add lr, sl, #3
@ anchor
    orr r1, r3, r2, lsl #8
    ldr r1, [r8, r1, lsl #2]
    ldrh r4, [r1, #0x1e]
    ldr r0, [r1, #0x14]
    sub r4, r6, r4, lsl #2
    ldr r2, [r4]
    cmp r2, #0
    beq #0 @ mask=target
    ldr r2, [r1, #4]
    sub r3, r4, #4
    cmp r2, #0
    beq #0 @ mask=target
    ldr sb, [r5, #0x438]
    tst r0, #8
    addeq r3, r3, #4
    cmp sb, #0
    beq #0 @ mask=target
    str r3, [sb, #0x20]
    str r4, [sb, #0x24]
    str sb, [r5, #0x468]
    str r1, [r5, #0x448]
    add sl, r5, #0x420
    stm sl, {r6, r7, r8, fp, lr}
    blx r2
@ end

@ Native-invoke guard: inspects the method flags and branches to the checked dispatcher.
@ pattern INVOKE pattern arm INVOKE
    mov r1, r4
    ldrh r4, [r1, #0x1e]
    ldr r0, [r1, #0x14]
    add lr, sl, #3
@ anchor
    sub r4, r6, r4, lsl #2
    tst r0, #0x100
    bne #0 @ mask=target
@ end

@ Copies the VM result-stack pointer back to its global return slot.
@ pattern KNI_RETURN pattern thumb KNI_RETURN
    ldr r0, [pc, #0] @ mask=literal
    ldr r1, [r0]
    ldr r0, [pc, #0] @ mask=literal
    ldr r1, [r1, #0x24]
@ anchor
    str r1, [r0]
    bx lr
@ end

@ Counts queued messages through the task-local message/context layout.
@ pattern MSG_COUNT pattern thumb MSG_COUNT
    push {r1, r2, r3, r4, r5, r6, r7, lr}
    blx #0 @ mask=target
    lsls r5, r0, #0x1b
    ldr r0, [pc, #0] @ mask=literal
    movs r1, #1
    lsls r1, r1, #0xb
    adds r6, r0, r1
    lsls r1, r1, #1
@ anchor
    lsrs r5, r5, #0x1b
    movs r7, #0
    adds r4, r0, r1
    cmp r5, #0x12
    bne #0 @ mask=target
    bl #0 @ mask=target
    subs r0, r7, #2
    strh r0, [r4]
    strb r7, [r4]
    movs r0, #0x99
    strb r0, [r4]
    str r5, [r4]
    str r7, [r4]
    str r7, [r4]
    movs r0, #0x10
@ end

@ Recognizes the Java VM timer interval and dispatches only IDs in that interval.
@ pattern VM_TIMER pattern thumb VM_TIMER
    movs r1, r0
@ anchor
    subs r1, #0xc0
    lsls r1, r1, #0x10
    lsrs r1, r1, #0x10
    movs r0, #0
    subs r1, #0x78
    push {r4, lr}
    cmp r1, #6
    bhi #6
    movs r0, r1
    bl #0 @ mask=target
    movs r0, #1
    pop {r4, pc}
@ end

@ Reserves a display, records its owner and pairs resource acquisition with release.
@ pattern LCD_RESERVE pattern thumb LCD_RESERVE
    push {r4, r5, r6, lr}
    movs r4, r0
    cmp r0, #2
    blo #2
    blx #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    lsls r1, r4, #2
    subs r0, #0x1c
    adds r4, r1, r0
    subs r4, #0x40
    ldr r0, [r4, #0x3c]
    adds r0, r0, #1
    beq #2
    blx #0 @ mask=target
    movs r0, #0 @ mask=imm8
    blx #0 @ mask=target
    blx #0 @ mask=target
    ldr r5, [pc, #0] @ mask=literal
    subs r5, #8
@ anchor
    ldr r0, [r5, #4]
    str r0, [r4, #0x3c]
    blx #0 @ mask=target
    bl #0 @ mask=target
    blx #0 @ mask=target
    adr r2, #0 @ mask=literal
    ldm r2, {r2, r3}
    blx #0 @ mask=target
    movs r2, #0x1e
    mvns r2, r2
    blx #0 @ mask=target
    blx #0 @ mask=target
    ldr r1, [r5, #4]
    adds r0, r0, r1
    adds r0, r0, #1
    movs r1, #1
    lsls r1, r1, #0x10
    cmp r0, r1
    str r0, [r5, #4]
    ble #2
    movs r0, #0
    str r0, [r5, #4]
    movs r0, #0 @ mask=imm8
    blx #0 @ mask=target
    ldr r0, [r4, #0x3c]
    pop {r4, r5, r6, pc}
@ end

@ Checks the display owner, prepares the update arguments and submits through the firmware LCD path.
@ pattern LCD_UPDATE pattern thumb LCD_UPDATE
    push {r0, r1, r2, r3, r4, r5, r6, r7, lr}
    movs r7, r0
    movs r5, r1
    lsls r0, r1, #2
    ldr r1, [pc, #0] @ mask=literal
@ anchor
    movs r6, r3
    subs r1, #0x1c
    adds r0, r0, r1
    subs r0, #0x40
    ldr r0, [r0, #0x3c]
    sub sp, #0xc
    adds r0, r0, #1
    bne #2
    blx #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    subs r0, #0x20
    adds r4, r0, r5
    subs r4, #0x20
    ldrb r0, [r4, #0x1f]
    blx #0 @ mask=target
    add r0, sp, #0x30
    ldm r0, {r0, r1, r2}
    movs r3, r6
    mov r6, sp
    stm r6!, {r0, r1, r2}
    ldr r2, [sp, #0x14]
    movs r1, r5
    movs r0, r7
    bl #0 @ mask=target
    ldrb r0, [r4, #0x1f]
    blx #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    movs r1, #1
    blx #0 @ mask=target
    add sp, #0x1c
    pop {r4, r5, r6, r7, pc}
@ end
