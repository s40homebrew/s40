@ SPDX-License-Identifier: MIT
@ Instruction signatures for Rm614Profile.
@ Calls and literal operands are decoded and validated by the Java resolver.

@ These families share the audited KNI/app/dispatcher recipes. Start
@ with a fresh array so C3 overrides cannot change the RM-443 profile.
@ ARM exception-vector prologue in the initial mapped page. This is
@ an identity check, not RM-443's ROM-range walker.
@ pattern ROM_VECTORS pattern arm ROM_VECTORS
@ anchor
    mrs sp, spsr
    tst sp, #0x20
    sub lr, lr, #6
    addeq lr, lr, #2
    ldr sp, [pc] @ mask=literal
    ldr sp, [sp]
    cmp sp, #0xca
    beq #0 @ mask=target
@ end

@ Cache synchronization over an address range; preserves the paired data/instruction maintenance operations.
@ pattern CACHE pattern arm CACHE
    push {r4, r5, r6, lr}
@ anchor
    mov r4, r0
    add r5, r1, r0
    ldr ip, [pc, #0x18]
    mov r1, r5
    blx ip
    ldr ip, [pc, #0x10]
    mov r0, r4
    mov r1, r5
    blx ip
    pop {r4, r5, r6, pc}
@ end

@ Java task message loop: receives messages, routes timer/Phonet events and drains internal work.
@ pattern TASK_LOOP pattern thumb TASK_LOOP
    blx #0 @ mask=target
@ anchor
    mov r1, sp
    cmp r0, #0
    str r1, [r5, #4]
    beq #52
    movs r4, r0
    movs r1, #0xff
    subs r4, #0xc0
    adds r1, #3
    cmp r4, r1
    bhi #30
    bl #0 @ mask=target
    cmp r0, #0
    bne #8
    lsls r0, r4, #0x10
    lsrs r0, r0, #0x10
    blx #0 @ mask=target
    b #22
    lsls r0, r4, #0x10
    lsrs r0, r0, #0x10
    cmp r0, #0x4c
    bne #14
    bl #0 @ mask=target
    b #8
    ldrb r1, [r0]
    cmp r1, #1
    beq #2
    blx #0 @ mask=target
    blx #0 @ mask=target
    blx #0 @ mask=target
    cmp r0, #0
    bne #10
    bl #0 @ mask=target
    blx #0 @ mask=target
    cmp r0, #0
    bne #-22
    blx #0 @ mask=target
    movs r0, #0
    str r0, [r5, #4]
    b #-102
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
    beq #52
    ldr r1, [r4, #8]
    cmp r1, #0
    beq #46
@ anchor
    subs r1, r1, #1
    str r1, [r4, #8]
    ldr r0, [r4]
    movs r1, #0x3c
    lsls r0, r0, #4
    adds r0, r0, r4
    ldr r0, [r0, #0xc]
    str r0, [r5]
    ldr r0, [r4]
    lsls r0, r0, #4
    adds r0, r0, r4
    ldr r0, [r0, #0x10]
    str r0, [r6]
    ldr r0, [r4]
    lsls r0, r0, #4
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

@ Counts queued messages through the task-local message/context layout.
@ pattern MSG_COUNT pattern thumb MSG_COUNT
    push {r3, r4, r5, r6, r7, lr}
    movs r5, #0
    bl #0 @ mask=target
    adds r0, #0x40
    ldrb r0, [r0, #0x1b]
    ldr r6, [pc, #0] @ mask=literal
    subs r0, r0, #1
    lsls r0, r0, #0x18
    lsrs r0, r0, #0x18
@ anchor
    movs r3, #0x24
    muls r0, r3, r0
    ldr r1, [r6, #0x10]
    adds r0, r0, r1
    ldr r7, [r0, #8]
    movs r4, #1
    lsls r1, r4, #0x18
    lsrs r1, r1, #0x18
    movs r0, r7
    bl #0 @ mask=target
    ldr r0, [r0]
    adds r4, r4, #1
    adds r5, r0, r5
    cmp r4, #4
    blt #-22
    ldr r0, [r6, #0xc]
    lsls r0, r0, #0x1b
    bpl #84
@ end

@ Recognizes the Java VM timer interval and dispatches only IDs in that interval.
@ pattern VM_TIMER pattern thumb VM_TIMER
    movs r1, r0
@ anchor
    subs r1, #0xc0
    lsls r1, r1, #0x10
    lsrs r1, r1, #0x10
    movs r2, r1
    movs r0, #0
    subs r1, #0x40
    push {r4, lr}
    cmp r1, #0xb
    bhi #6
    movs r0, r1
    bl #0 @ mask=target
    b #18
    cmp r2, #0x4d
    bne #16
    bl #0 @ mask=target
    cmp r0, #0
    bne #6
    ldr r1, [pc, #0] @ mask=literal
    movs r0, #0x4d
    blx #0 @ mask=target
    movs r0, #1
    pop {r4, pc}
@ end

@ Reserves a display, records its owner and pairs resource acquisition with release.
@ pattern LCD_RESERVE pattern thumb LCD_RESERVE
    push {r3, r4, r5, r6, r7, lr}
    movs r4, r0
    movs r0, #0
    cmp r4, #2
    str r0, [sp]
    blo #2
    blx #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    lsls r7, r4, #2
    subs r0, r0, #4
    adds r5, r7, r0
    subs r5, #0x40
    ldr r0, [r5, #0x3c]
    adds r0, r0, #1
    beq #2
    blx #0 @ mask=target
    movs r0, #0 @ mask=imm8 ; resource ID is checked against the paired release
    bl #0 @ mask=target
    bl #0 @ mask=target
    ldr r6, [pc, #0] @ mask=literal
@ anchor
    ldr r0, [r6, #4]
    str r0, [r5, #0x3c]
    bl #0 @ mask=target
    blx #0 @ mask=target
    blx #0 @ mask=target
    adr r2, #0 @ mask=literal
    ldm r2, {r2, r3}
    blx #0 @ mask=target
    movs r2, #0x1e
    mvns r2, r2
    blx #0 @ mask=target
    blx #0 @ mask=target
    ldr r1, [r6, #4]
    adds r0, r0, r1
    adds r0, r0, #1
    movs r1, #1
    lsls r1, r1, #0x10
    cmp r0, r1
    str r0, [r6, #4]
    ble #2
    movs r0, #0
    str r0, [r6, #4]
    cmp r4, #1
    beq #2
    blx #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    ldr r1, [sp]
    movs r2, #1
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    adds r1, #8
    adds r1, r7, r1
    subs r1, #0x40
    str r0, [r1, #0x3c]
    movs r0, #0 @ mask=imm8 ; resource ID is checked against the paired release
    bl #0 @ mask=target
    ldr r0, [r5, #0x3c]
@ end

@ Checks the display owner, prepares the update arguments and submits through the firmware LCD path.
@ pattern LCD_UPDATE pattern thumb LCD_UPDATE
    push {r0, r1, r2, r3, r4, r5, r6, r7, lr}
    ldr r0, [pc, #0] @ mask=literal
@ anchor
    lsls r4, r1, #2
    subs r0, r0, #4
    adds r0, r4, r0
    subs r0, #0x40
    ldr r0, [r0, #0x3c]
    movs r6, r1
    movs r7, r2
    adds r0, r0, #1
    sub sp, #0x14
    bne #2
    blx #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    subs r0, #0xc
    adds r5, r0, r6
    subs r5, #0x20
    ldrb r0, [r5, #0x1f]
    bl #0 @ mask=target
    ldr r0, [sp, #0x38]
    ldr r1, [sp, #0x3c]
    movs r2, #0
@ end

@ The special timer builds a native message; it does not enter the Java VM.
@ Interpreter return path whose call synchronizes generated bytecode before restoring the VM registers.
@ pattern BYTECODE_SYNC pattern arm BYTECODE_SYNC
@ anchor
    ldr r0, [sb, #4]
    mov r1, r5
    bl #0 @ mask=target
    mov r0, r7
    add sp, sp, #0x14
    pop {r4, r5, r6, r7, r8, sb, sl, fp, pc}
@ end

@ Thumb wrapper that invokes the ARM data-cache and write-buffer helpers.
@ pattern DCACHE_VENEER pattern thumb DCACHE_VENEER
    push {r4, lr}
    blx #0 @ mask=target
    blx #0 @ mask=target
@ anchor
    movs r0, #0
    pop {r4, pc}
@ end

@ Thumb wrapper that invokes the ARM instruction-cache helper.
@ pattern ICACHE_VENEER pattern thumb ICACHE_VENEER
    push {r4, lr}
    blx #0 @ mask=target
@ anchor
    movs r0, #0
    pop {r4, pc}
@ end

@ Cleans data-cache lines in a bounded range while preserving the previous interrupt mask.
@ pattern DCACHE_RANGE pattern arm DCACHE_RANGE
    ldr r2, [pc] @ mask=literal
@ anchor
    subs r3, r1, r0
    cmp r2, r3
    bls #0 @ mask=target
    mrs r3, apsr
    orr r2, r3, #0xc0
    msr cpsr_c, r2
    bic r0, r0, #0x1f
    bic r1, r1, #0x1f
    mcr p15, #0, r0, c7, c14, #1
    add r0, r0, #0x20
    cmp r0, #0
    beq #8
    cmp r0, r1
    bls #-28
    mov r0, #0
    mcr p15, #0, r0, c7, c10, #4
    msr cpsr_c, r3
    bx lr
@ end

@ Invalidates instruction-cache lines in a bounded range and restores processor status.
@ pattern ICACHE_RANGE pattern arm ICACHE_RANGE
@ anchor
    mrs r3, apsr
    orr r2, r3, #0xc0
    msr cpsr_c, r2
    bic r0, r0, #0x1f
    bic r1, r1, #0x1f
    mcr p15, #0, r0, c7, c5, #1
    add r0, r0, #0x20
    cmp r0, #0
    beq #4
    cmp r0, r1
    bls #-28
    msr cpsr_c, r3
    bx lr
@ end

@ Drains the ARM write buffer through CP15 before returning.
@ pattern WRITE_BUFFER pattern arm WRITE_BUFFER
@ anchor
    mov r0, #0
    mcr p15, #0, r0, c7, c10, #4
    bx lr
@ end

@ Cleans the whole data cache and drains the write buffer with interrupts masked.
@ pattern DCACHE_ALL pattern arm DCACHE_ALL
@ anchor
    mrs r3, apsr
    orr r2, r3, #0xc0
    msr cpsr_c, r2
    mrc p15, #0, apsr_nzcv, c7, c14, #3
    bne #-12
    mov r0, #0
    mcr p15, #0, r0, c7, c10, #4
    msr cpsr_c, r3
    bx lr
@ end

@ Foreground Java UI refresh: validated source-copy and task-16 signal path.
@ Foreground Java refresh path; validates the surface-copy and task notification relationships.
@ pattern UI_REFRESH pattern thumb UI_REFRESH
    push {r0, r1, r2, r3, r4, r5, r6, r7, lr}
    sub sp, #0x14
    movs r6, r0
    ldr r5, [sp, #0x40]
    ldr r4, [sp, #0x38]
    movs r7, r1
    movs r0, #0
    blx #0 @ mask=target
@ anchor
    mov r3, sp
    strh r6, [r3, #0xc]
    strh r7, [r3, #0xe]
    ldr r0, [sp, #0x1c]
    ldr r1, [pc, #0] @ mask=literal
    strh r0, [r3, #0x10]
    ldr r0, [sp, #0x20]
    strh r0, [r3, #0x12]
    movs r0, r4
    bne #4
    ldr r0, [r1, #4]
    adds r0, #0x80
    ldr r0, [r0, #0x18]
    ldr r1, [r1, #4]
    movs r6, r0
    ldr r1, [r1, #0x10]
    movs r2, #1
    movs r0, r2
    cmp r1, #4
    blo #0
    movs r0, #0
    cmp r1, #2
    bne #0
    movs r2, #0
    tst r0, r2
    beq #38
    ldr r2, [sp, #0x3c]
    movs r3, r5
    add r1, sp, #4
    add r0, sp, #0xc
    bl #0 @ mask=target
    cmp r0, #0
    beq #22
    cmp r4, #0
    beq #2
    movs r3, #1
    b #0
    movs r3, #0
    movs r0, r6
    add r2, sp, #4
    add r1, sp, #0xc
    bl #0 @ mask=target
    cmp r0, #0
    bne #4
    movs r0, #0
    blx #0 @ mask=target
    b #-310
@ end

@ Copies the selected Java surface and maintains its bounds and dirty-state fields.
@ pattern UI_COPY pattern thumb UI_COPY
    push {r0, r1, r2, r3, r4, r5, r6, r7, lr}
    ldr r5, [pc, #0] @ mask=literal
@ anchor
    movs r6, r1
    ldr r1, [r5, #4]
    movs r0, #0xe4
    ldrb r0, [r0, r1]
    movs r4, r2
    sub sp, #0x14
    cmp r0, #0
    beq #122
    ldr r2, [r1, #0x10]
    movs r7, #1
    movs r0, r7
    cmp r2, #4
    blo #0
    movs r0, #0
    movs r1, r7
    cmp r2, #2
    bne #0
    movs r1, #0
    tst r0, r1
    beq #10
    movs r0, #2
    cmp r3, #0
    beq #0
    movs r0, #0
    bl #0 @ mask=target
    ldr r0, [r5, #4]
    movs r1, r7
    ldr r3, [r0, #0x10]
    cmp r3, #4
    blo #0
    movs r1, #0
    movs r2, r7
    cmp r3, #2
    bne #0
    movs r2, #0
    tst r1, r2
    beq #18
    ldrh r2, [r0, #0xe]
    movs r1, #8
    str r2, [sp]
    movs r2, #0xa
    ldrh r3, [r0, #0xc]
    ldrsh r2, [r0, r2]
    ldrsh r1, [r0, r1]
    movs r0, #0
    bl #0 @ mask=target
    ldr r1, [r5, #4]
    movs r0, r7
    ldr r1, [r1, #0x10]
    cmp r1, #4
    blo #0
    movs r0, #0
    cmp r1, #2
    bne #0
    movs r7, #0
    tst r0, r7
    beq #4
    movs r0, #3
    bl #0 @ mask=target
    ldr r1, [r5, #4]
    movs r0, #2
    adds r1, #0xe0
    ldrb r2, [r1, #4]
    cmp r2, #1
    beq #0
    movs r0, #0
    strb r0, [r1, #4]
    ldr r0, [pc, #0] @ mask=literal
    movs r1, #0
    ldr r2, [r0, #4]
    movs r0, r2
    adds r0, #0xa0
    ldrh r5, [r0, #8]
    ldrh r7, [r0, #0xa]
    strh r1, [r0, #8]
    strh r1, [r0, #0xa]
    movs r3, r2
    movs r2, #2
    ldrsh r2, [r4, r2]
    ldrsh r1, [r4, r1]
    ldrh r0, [r4, #6]
    str r2, [sp, #0x10]
    str r1, [sp, #0xc]
    str r0, [sp, #8]
    ldrh r2, [r4, #4]
    movs r1, #2
    ldrsh r1, [r6, r1]
    str r2, [sp, #4]
    movs r0, #0
    str r1, [sp]
    ldrsh r0, [r6, r0]
    movs r2, #0x94
    ldr r1, [r2, r3]
    movs r6, r0
    movs r2, r3
    ldr r0, [sp, #0x14]
    adds r2, #0x9c
    movs r3, r6
    blx #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    ldr r0, [r0, #4]
    movs r2, r0
    adds r2, #0xa0
    movs r1, r0
    strh r5, [r2, #8]
    adds r1, #0xe0
    strh r7, [r2, #0xa]
    ldrb r2, [r1, #5]
    cmp r2, #0
    beq #58
    movs r2, #0
    ldrsh r2, [r4, r2]
    adds r0, #0x80
    ldr r1, [r0, #4]
    cmp r1, r2
    blt #0
    movs r1, r2
    movs r2, #2
    str r1, [r0, #4]
    ldrsh r2, [r4, r2]
    ldr r1, [r0, #8]
    cmp r1, r2
    blt #0
    movs r1, r2
    str r1, [r0, #8]
    ldrh r2, [r4, #4]
    ldr r1, [r0, #0xc]
    cmp r1, r2
    bhs #0
    movs r1, r2
    str r1, [r0, #0xc]
    ldrh r2, [r4, #6]
    ldr r1, [r0, #0x10]
    cmp r1, r2
    bhs #0
    movs r1, r2
    str r1, [r0, #0x10]
    movs r0, #0
    add sp, #0x24
    pop {r4, r5, r6, r7, pc}
    movs r2, #1
    strb r2, [r1, #5]
    movs r1, #0
    ldrsh r1, [r4, r1]
    adds r0, #0x80
    str r1, [r0, #4]
    movs r1, #2
    ldrsh r1, [r4, r1]
    str r1, [r0, #8]
    ldrh r1, [r4, #4]
    str r1, [r0, #0xc]
    ldrh r1, [r4, #6]
    str r1, [r0, #0x10]
    movs r0, #0
    blx #0 @ mask=target
    bl #0 @ mask=target
    movs r0, #1
    b #-46
@ end

@ Notifies task 16 after the foreground surface has been updated.
@ pattern UI_SIGNAL pattern thumb UI_SIGNAL
@ anchor
    push {r4, lr}
    movs r1, #0
    movs r0, #0x10
    blx #0 @ mask=target
    pop {r4, pc}
@ end

@ Only BYTECODE_SYNC is globally searched; its first word is exact.
@ Builds the native timer notification message without reentering the Java interpreter.
@ The older destination form substitutes SUBS r0,r0,#2 at byte 76;
@ Rm614Profile builds that exact alternative from this common handler.
@ pattern SPECIAL_TIMER pattern thumb SPECIAL_TIMER
    push {r1, r2, r3, r4, r5, lr}
    ldr r5, [pc, #0] @ mask=literal
    ldr r0, [r5, #4]
    cmp r0, #0
    beq #74
    movs r0, #0xc
    blx #0 @ mask=target
    movs r4, #0
    cmp r0, #0
    beq #60
@ anchor
    movs r2, #2
    strb r2, [r0, #3]
    movs r2, #0x11
    strb r2, [r0, #1]
    movs r2, #0x33
    strb r4, [r0, #2]
    strb r2, [r0]
    movs r2, #1
    strb r2, [r0, #4]
    movs r2, #8
    strb r2, [r0, #5]
    ldr r2, [r5, #4]
    adds r1, r0, #4
    lsrs r3, r2, #8
    strb r3, [r0, #6]
    strb r2, [r0, #7]
    strb r4, [r0, #8]
    strb r4, [r1, #5]
    strb r4, [r1, #6]
    movs r2, r0
    strb r4, [r1, #7]
    movs r1, #0xc
    movs r0, #0
    mov r3, sp
    stm r3!, {r0, r1, r2}
    movs r2, r0
    movs r1, r0
    ldr r0, [pc, #0] @ mask=literal
    movs r3, #2
    bl #0 @ mask=target
    str r4, [r5, #4]
    pop {r1, r2, r3, r4, r5, pc}
@ end
