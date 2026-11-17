@ SPDX-License-Identifier: MIT
@ Instruction signatures for Rm217Profile.
@ Calls and literal operands are decoded and validated by the Java resolver.

@ Removes one event from the Java queue and copies its three payload fields to caller outputs.
@ pattern EVENT_POP pattern thumb EVENT_POP
@ anchor
    push {r3, r4, r5, r6, r7, lr}
    adds r6, r2, #0
    adds r5, r1, #0
    adds r7, r3, #0
    bl #0 @ mask=target
    adds r4, r0, #0
    movs r0, #0
    cmp r4, #0
    beq #54
    ldr r1, [r4, #8]
    cmp r1, #0
    beq #48
    subs r1, #1
    str r1, [r4, #8]
    ldr r0, [r4]
    movs r3, #0xc
    muls r0, r3, r0
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
    adds r0, #1
    movs r1, #0x28
    blx #0 @ mask=target
    movs r0, #1
    str r1, [r4]
    pop {r3, r4, r5, r6, r7, pc}
@ end

@ Copies the VM result-stack pointer back to its global return slot.
@ pattern KNI_RETURN pattern thumb KNI_RETURN
    ldr r0, [pc, #0] @ mask=literal
    ldr r1, [pc, #0] @ mask=literal
@ anchor
    ldr r0, [r0]
    ldr r0, [r0, #0x24]
    str r0, [r1]
    bx lr
@ end

@ Java task message loop: receives messages, routes timer/Phonet events and drains internal work.
@ pattern TASK_LOOP pattern thumb TASK_LOOP
@ anchor
    movs r5, #0xff
    adds r5, #0x1f
    movs r6, #0xff
    adds r6, #0x40
    bl #0 @ mask=target
    cmp r0, #0
    beq #38
    adds r4, r0, #0
    subs r4, #0xc0
    cmp r4, r5
    bhi #20
    cmp r0, r6
    beq #26
    bl #0 @ mask=target
    cmp r0, #0
    bne #18
    lsls r0, r4, #0x10
    lsrs r0, r0, #0x10
    bl #0 @ mask=target
    b #8
    ldrb r1, [r0]
    cmp r1, #1
    beq #2
    bl #0 @ mask=target
    bl #0 @ mask=target
    bl #0 @ mask=target
    cmp r0, #0
    bne #10
    bl #0 @ mask=target
    bl #0 @ mask=target
    cmp r0, #0
    bne #-22
    bl #0 @ mask=target
    b #-80
@ end

@ Counts queued messages through the task-local message/context layout.
@ pattern MSG_COUNT pattern thumb MSG_COUNT
    push {r1, r2, r3, r4, r5, r6, r7, lr}
    blx #0 @ mask=target
    lsls r5, r0, #0x1b
    ldr r1, [pc, #0] @ mask=literal
@ anchor
    movs r0, #1
    lsls r0, r0, #0xb
    adds r6, r1, r0
    lsls r0, r0, #1
    adds r4, r1, r0
    lsrs r5, r5, #0x1b
    movs r7, #0
    cmp r5, #0x12
    bne #56
    blx #0 @ mask=target
    subs r0, r7, #2
    strh r0, [r4]
    strb r7, [r4]
    movs r0, #0x99
    strb r0, [r4]
    str r5, [r4]
    str r7, [r4]
    str r7, [r4]
    movs r0, #0x10
    strb r0, [r6]
    blx #0 @ mask=target
    movs r2, #0
    str r2, [sp]
    str r2, [sp, #4]
    movs r2, #0x99
    adds r3, r5, #0
    movs r1, #0
    movs r0, #0xfe
    bl #0 @ mask=target
    bl #0 @ mask=target
    movs r0, #0x66
    mvns r0, r0
    blx #0 @ mask=target
    ldr r5, [pc, #0] @ mask=literal
    ldrb r0, [r5]
    cmp r0, #0
    bne #54
    blx #0 @ mask=target
    strh r7, [r4]
    strb r7, [r4]
    movs r0, #0x99
    strb r0, [r4]
    str r7, [r4]
    str r7, [r4]
    str r7, [r4]
    movs r0, #0x10
    strb r0, [r6]
    blx #0 @ mask=target
    movs r2, #0
    str r2, [sp]
    str r2, [sp, #4]
    movs r2, #0x99
    movs r1, #0
    movs r0, #0
    movs r3, #0
    bl #0 @ mask=target
    bl #0 @ mask=target
    movs r0, #0x66
    mvns r0, r0
    blx #0 @ mask=target
    blx #0 @ mask=target
    lsls r0, r0, #0x1b
    lsrs r0, r0, #0x1b
    cmp r0, #0x11
    bne #2
    bl #0 @ mask=target
    movs r7, #0
    blx #0 @ mask=target
    ldrb r0, [r5, #3]
    movs r1, #0x1c
    ldr r2, [pc, #0] @ mask=literal
    muls r1, r0, r1
    adds r1, r1, r2
    ldr r2, [r1, #8]
    b #6
    adds r7, #1
    ldr r2, [r2, #4]
    lsls r7, r7, #0x18
    lsrs r7, r7, #0x18
    adds r3, r2, #1
    bne #-14
    ldrb r2, [r1, #0x18]
@ end

@ Recognizes the Java VM timer interval and dispatches only IDs in that interval.
@ pattern VM_TIMER pattern thumb VM_TIMER
@ anchor
    subs r0, #0xff
    subs r0, #0x3a
    movs r1, #0
    cmp r0, #5
    push {r4, lr}
    bhi #4
    bl #0 @ mask=target
    movs r1, #1
    adds r0, r1, #0
    pop {r4, pc}
@ end

@ Foreground Java refresh path; validates the surface-copy and task notification relationships.
@ pattern UI_REFRESH pattern thumb UI_REFRESH
    push {r0, r1, r2, r3, r4, r5, r6, r7, lr}
    ldr r5, [pc, #0] @ mask=literal
@ anchor
    adds r7, r3, #0
    ldr r0, [r5, #4]
    adds r6, r2, #0
    sub sp, #0xc
    ldr r4, [sp, #0x30]
    cmp r0, #0
    beq #82
    movs r0, #0xc
    bl #0 @ mask=target
    ldr r0, [r5, #4]
    ldr r0, [r0, #0x10]
    cmp r0, #3
    bne #62
    movs r0, #2
    cmp r4, #0
    beq #0
    movs r0, #0
    bl #0 @ mask=target
    ldr r1, [sp, #0x34]
    ldr r0, [sp, #0x10]
    ldr r2, [sp, #0x38]
    str r1, [sp, #4]
    lsls r1, r0, #0x10
    ldr r0, [sp, #0xc]
    str r2, [sp, #8]
    lsls r2, r6, #0x10
    lsls r3, r7, #0x10
    lsrs r3, r3, #0x10
    lsrs r2, r2, #0x10
    asrs r1, r1, #0x10
    lsls r0, r0, #0x10
    asrs r0, r0, #0x10
    str r4, [sp]
    bl #0 @ mask=target
    ldr r0, [r5, #4]
    ldr r0, [r0, #0x10]
    cmp r0, #3
    bne #8
    cmp r4, #0
    beq #0
    movs r0, #1
    bl #0 @ mask=target
    movs r0, #0xc
    bl #0 @ mask=target
    add sp, #0x1c
    pop {r4, r5, r6, r7, pc}
@ end

@ Java file-open adapter: converts the path and mode before calling the native file service.
@ pattern FILE_ADAPTER_OPEN pattern thumb file_adapter_open
@ anchor
    push {r3, r4, r5, r6, r7, lr}
    adds r5, r0, #0
    movs r0, #0
    mvns r0, r0
    adds r7, r2, #0
    adds r6, r1, #0
    str r0, [sp]
    adds r0, r5, #0
    bl #0 @ mask=target
    adds r4, r0, #0
    bl #0 @ mask=target
    cmp r0, #0
    bne #0 @ mask=target
    movs r0, #1
    lsls r0, r0, #0xb
    orrs r0, r6
    lsls r1, r0, #0x10
    lsls r2, r7, #0x10
    lsrs r2, r2, #0x10
    lsrs r1, r1, #0x10
    adds r0, r4, #0
    bl #0 @ mask=target
    str r0, [sp]
    b #0 @ mask=target
    movs r0, #0x22
    bl #0 @ mask=target
    cmp r4, r5
    beq #0 @ mask=target
    cmp r4, #0
    beq #0 @ mask=target
    adds r0, r4, #0
    bl #0 @ mask=target
    ldr r0, [sp]
    pop {r3, r4, r5, r6, r7, pc}
@ end

@ Java unlink adapter: resolves its path, invokes the native service and releases temporary storage.
@ pattern FILE_ADAPTER_UNLINK pattern thumb file_adapter_unlink
@ anchor
    push {r4, r5, r6, lr}
    movs r5, #0
    adds r6, r0, #0
    mvns r5, r5
    bl #0 @ mask=target
    adds r4, r0, #0
    bl #0 @ mask=target
    cmp r0, #0
    bne #0 @ mask=target
    adds r0, r4, #0
    bl #0 @ mask=target
    cmp r0, #1
    bne #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    movs r5, #0
    ldr r2, [r0]
    cmp r2, #0
    beq #0 @ mask=target
    movs r1, #0
    adds r0, r4, #0
    blx r2
    b #0 @ mask=target
    movs r0, #0x22
    bl #0 @ mask=target
    cmp r4, r6
    beq #0 @ mask=target
    cmp r4, #0
    beq #0 @ mask=target
    adds r0, r4, #0
    bl #0 @ mask=target
    adds r0, r5, #0
    pop {r4, r5, r6, pc}
@ end

@ Java directory-creation adapter with the same path conversion and cleanup contract.
@ pattern FILE_ADAPTER_MKDIR pattern thumb file_adapter_mkdir
@ anchor
    push {r4, r5, r6, lr}
    movs r5, #0
    adds r6, r0, #0
    mvns r5, r5
    bl #0 @ mask=target
    adds r4, r0, #0
    bl #0 @ mask=target
    cmp r0, #0
    bne #0 @ mask=target
    adds r0, r4, #0
    bl #0 @ mask=target
    lsls r0, r0, #0x18
    asrs r0, r0, #0x18
    cmp r0, #1
    bne #0 @ mask=target
    movs r5, #0
    b #0 @ mask=target
    movs r0, #0x22
    bl #0 @ mask=target
    cmp r4, r6
    beq #0 @ mask=target
    cmp r4, #0
    beq #0 @ mask=target
    adds r0, r4, #0
    bl #0 @ mask=target
    adds r0, r5, #0
    pop {r4, r5, r6, pc}
@ end

@ Native millisecond clock: combines the time source with its saved accumulator state.
@ pattern OS_CURRENT_TIME_READ_IN_MS pattern thumb os_current_time_read_in_ms
    push {r4, lr}
    bl #0 @ mask=target
    bl #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    ldr r2, [pc, #0] @ mask=literal
@ anchor
    ldr r1, [r0, #0x1c]
    ldr r0, [r0, #0x18]
    blx #0 @ mask=target
    movs r2, #0xf
    blx #0 @ mask=target
    adr r2, #0 @ mask=literal
    ldm r2, {r2, r3}
    blx #0 @ mask=target
    adds r4, r0, #0
    ldr r0, [pc, #0] @ mask=literal
    ldr r0, [r0]
    lsls r0, r0, #0x1a
    bpl #0 @ mask=target
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    strh r0, [r1]
    str r4, [r1]
    ldr r1, [pc, #0] @ mask=literal
    movs r0, #0x5f
    strb r0, [r1]
    bl #0 @ mask=target
    adds r0, r4, #0
    pop {r4, pc}
@ end

@ Java long-return wrapper for the native millisecond clock.
@ pattern KVM_CURRENT_TIME_MILLIS pattern thumb kvm_current_time_millis
    push {r4, lr}
    bl #0 @ mask=target
    ldr r4, [pc, #0] @ mask=literal
@ anchor
    movs r3, #3
    ldr r2, [r4]
    adds r2, #4
    mvns r3, r3
    str r0, [r3, r2]
    stm r2!, {r1}
    str r2, [r4]
    pop {r4, pc}
@ end

@ Clock accumulator helper; the resolver checks its relationship to the current-time wrapper.
@ pattern KVM_TIME_ACCUMULATOR pattern thumb kvm_time_accumulator
    push {r4, lr}
    ldr r4, [pc, #0] @ mask=literal
@ anchor
    ldr r0, [r4, #4]
    cmp r0, #0
    bne #0 @ mask=target
    bl #0 @ mask=target
    b #0 @ mask=target
    bl #0 @ mask=target
    ldr r0, [r4, #0x10]
    ldr r2, [r4, #8]
    ldr r1, [r4, #0x14]
    ldr r3, [r4, #0xc]
    adds r0, r0, r2
    adcs r1, r3
    pop {r4, pc}
@ end

@ Clock delta helper reached from the checked time accumulator.
@ pattern KVM_TIME_DELTA pattern thumb kvm_time_delta
    push {r4, r5, r6, lr}
    bl #0 @ mask=target
    ldr r4, [pc, #0] @ mask=literal
@ anchor
    adds r5, r0, #0
    ldr r0, [r4]
    ldr r1, [r4, #0xc]
    subs r2, r5, r0
    ldr r0, [r4, #8]
    movs r3, #0
    adds r0, r0, r2
    adcs r1, r3
    str r0, [r4, #8]
    str r1, [r4, #0xc]
    str r5, [r4]
    pop {r4, r5, r6, pc}
@ end

@ Reads the current task identifier through the scheduler state.
@ pattern TASK_OWN_ID pattern thumb TASK_OWN_ID
    ldr r0, [pc, #0] @ mask=literal
@ anchor
    ldrb r1, [r0]
    cmp r1, #0
    bne #2
    movs r0, #0xff
    bx lr
    ldr r1, [pc, #0] @ mask=literal
    ldrb r1, [r1]
    cmp r1, #0
    bne #2
    ldrb r0, [r0, #3]
    bx lr
    movs r0, #0xfe
    bx lr
@ end

@ Converts the foreground Java display state into a native rendering request.
@ pattern FOREGROUND_RENDER pattern thumb foreground_render
@ anchor
    push {r4, r5, r6, r7, lr}
    sub sp, #0x14
    adds r4, r2, #0
    add r2, sp, #0x28
    adds r5, r3, #0
    ldm r2, {r2, r6, r7}
    mov r3, sp
    strh r0, [r3, #0xc]
    strh r1, [r3, #0xe]
    strh r4, [r3, #0x10]
    ldr r4, [pc, #0] @ mask=literal
    strh r5, [r3, #0x12]
    ldr r0, [r4, #4]
    adds r5, r0, #0
    adds r5, #0x64
    cmp r2, #0
    beq #0 @ mask=target
    adds r5, r2, #0
    ldr r1, [r0]
    lsls r1, r1, #4
    adds r0, r1, r0
    ldr r1, [r0, #0x14]
    ldr r0, [r0, #0x18]
    adds r1, r1, r6
    lsls r2, r1, #0x10
    adds r0, r0, r7
    lsls r3, r0, #0x10
    asrs r2, r2, #0x10
    asrs r3, r3, #0x10
    add r0, sp, #0xc
    add r1, sp, #4
    bl #0 @ mask=target
    cmp r0, #0
    beq #0 @ mask=target
    adds r0, r5, #0
    add r2, sp, #4
    add r1, sp, #0xc
    bl #0 @ mask=target
    ldr r0, [r4, #4]
    adds r0, #0xa0
    ldrb r0, [r0, #0xe]
    cmp r0, #0
    beq #0 @ mask=target
    movs r0, #1
    add r2, sp, #4
    add r1, sp, #0xc
    bl #0 @ mask=target
    b #0 @ mask=target
    adds r0, r5, #0
    add r2, sp, #4
    add r1, sp, #0xc
    bl #0 @ mask=target
    b #0 @ mask=target
@ end

@ Presents the foreground surface and dispatches the corresponding screen update.
@ pattern FOREGROUND_PRESENT pattern thumb foreground_present
@ anchor
    push {r4, r5, r6, r7, lr}
    ldr r6, [r0]
    adds r7, r0, #0
    adds r5, r2, #0
    adds r4, r1, #0
    ldrb r0, [r0, #0xc]
    subs r6, #4
    sub sp, #0x14
    bl #0 @ mask=target
    strh r0, [r6]
    ldrh r0, [r7, #8]
    movs r2, #0
    add r3, sp, #4
    strh r0, [r6, #2]
    ldrh r0, [r5]
    ldrh r1, [r5, #2]
    stm r3!, {r0, r1, r2}
    ldrh r0, [r4, #2]
    ldrh r1, [r5, #6]
    adds r0, r0, r1
    subs r0, #1
    lsls r2, r0, #0x10
    lsrs r2, r2, #0x10
    str r2, [sp]
    ldrh r0, [r4]
    ldrh r1, [r5, #4]
    ldrh r2, [r4, #2]
    adds r0, r0, r1
    subs r0, #1
    lsls r3, r0, #0x10
    lsrs r3, r3, #0x10
    ldrh r1, [r4]
    adds r0, r6, #0
    bl #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    ldr r0, [r0, #4]
    ldr r0, [r0, #0x10]
    cmp r0, #3
    bne #0 @ mask=target
    adds r1, r5, #0
    movs r0, #0
    bl #0 @ mask=target
    movs r2, #1
    str r2, [sp]
    b #0 @ mask=target
    .space 4 @ mask=ignore
    movs r3, #2
    str r2, [sp, #4]
    ldrsh r2, [r4, r3]
    movs r3, #0
    ldrsh r1, [r4, r3]
    adds r3, r5, #0
    adds r0, r7, #0
    bl #0 @ mask=target
    movs r1, #0
    movs r0, #0
    bl #0 @ mask=target
    add sp, #0x14
    pop {r4, r5, r6, r7, pc}
@ end

@ Maps Java image formats into the native display format accepted by the HAL.
@ pattern FORMAT_TO_HAL pattern thumb format_to_hal
    cmp r0, #0xb
    beq #0 @ mask=target
    bgt #0 @ mask=target
    cmp r0, #0
    beq #0 @ mask=target
    cmp r0, #8
    beq #0 @ mask=target
    cmp r0, #9
    bne #0 @ mask=target
@ anchor
    movs r0, #6
    bx lr
    cmp r0, #0xc
    beq #0 @ mask=target
    cmp r0, #0xd
    bne #0 @ mask=target
    movs r0, #4
    bx lr
    movs r0, #9
    bx lr
    movs r0, #8
    bx lr
    movs r0, #1
    bx lr
    movs r0, #0
    bx lr
@ end

@ Java task receive wrapper; its call target becomes the shared native receive service.
@ pattern RECEIVE pattern thumb RECEIVE
@ anchor
    push {r4, r5, r6, r7, lr}
    movs r5, #0
    sub sp, #0x14
    blx #0 @ mask=target
    lsls r6, r0, #0x1b
    ldr r1, [pc, #0] @ mask=literal
    movs r0, #1
    lsls r0, r0, #0xb
    adds r2, r1, r0
    lsls r0, r0, #1
    adds r4, r1, r0
    lsrs r6, r6, #0x1b
    movs r7, #0x99
    cmp r6, #0x12
    str r2, [sp, #0x10]
    bne #58
@ end

@ Processes the current task internal ComMgr queue.
@ pattern INTERNAL pattern thumb INTERNAL
    push {r4, lr}
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
@ anchor
    lsls r0, r0, #2
    ldr r0, [r1, r0]
    movs r1, #0
    bl #0 @ mask=target
    pop {r4, pc}
@ end

@ Dispatches a received Phonet message through the current task context.
@ pattern PHONET pattern thumb PHONET
@ anchor
    push {r4, r5, r6, r7, lr}
    adds r4, r0, #0
    sub sp, #0x34
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    lsls r0, r0, #2
    ldr r6, [r1, r0]
    ldrb r1, [r4, #1]
    ldrb r0, [r4, #6]
    lsls r1, r1, #0x1e
    lsrs r1, r1, #0x16
    adds r5, r0, r1
    ldrb r0, [r4, #3]
    cmp r0, #0x10
    bne #10
@ end

@ Routes a native timer timeout through the current task handlers.
@ pattern TIMEOUT pattern thumb TIMEOUT
@ anchor
    push {r4, lr}
    adds r4, r0, #0
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    lsls r0, r0, #2
    adds r1, #0xb8
    ldr r1, [r1, r0]
    ldrh r0, [r1, #8]
    cmp r0, r4
    bne #2
    adds r0, r1, #0
    b #34
    ldr r0, [r1, #0xc]
    ldrh r2, [r0, #8]
    cmp r2, r4
@ end

@ Scans the current task ISI subscriptions after processing queued work.
@ pattern SUBSCRIPTIONS pattern thumb SUBSCRIPTIONS
@ anchor
    push {r4, r5, r6, r7, lr}
    sub sp, #0x6c
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    lsls r0, r0, #2
    ldr r0, [r1, r0]
    str r0, [sp, #0x68]
    ldr r0, [r0]
    str r0, [sp, #0x64]
    adds r0, #0x20
    ldrb r1, [r0, #0x18]
    lsls r2, r1, #0x1f
    beq #214
    movs r2, #1
    bics r1, r2
@ end
