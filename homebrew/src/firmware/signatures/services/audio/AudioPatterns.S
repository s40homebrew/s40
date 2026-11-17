@ SPDX-License-Identifier: MIT
@ Instruction signatures for AudioBindings.
@ Calls and literal operands are decoded and validated by the Java resolver.

@ Shared by RM-443 08.90/10.12 and RM-614 04.60/08.63. Masks omit only
@ literal-pool displacement and decoded BL/BLX targets; layout operations,
@ state transitions and internal control flow remain exact.
@ Constructs Active_Source, immediately followed by its +108/+112 watermarks setter.
@ Class literals and the constructor call must match the checked class graph and export.
@ pattern AUDIO_CREATE_LIMITS recipe thumb AUDIO_CREATE_LIMITS
    push {r4, lr}
    ldr r1, [pc, #0] @ mask=literal
    ldr r0, [pc, #0] @ mask=literal
    blx #0 @ mask=call
@ anchor
    pop {r4, pc}
    push {r4, r5, r6, r7, lr}
    sub sp, #0x14
    str r2, [r0, #0x70]
    movs r6, #0xff
    movs r5, r2
    lsls r6, r6, #0x10
    movs r2, r0
    movs r4, r1
    ands r2, r6
    str r1, [r0, #0x6c]
@ end

@ Constructs a CM_Source_Producer and stores it at source +44.
@ The producer class, common ConnLayer parent and _CallClass2 target are checked.
@ pattern AUDIO_SHARE recipe thumb AUDIO_SHARE
    push {r2, r3, r4, lr}
    movs r4, r0
    movs r1, #0xff
    lsls r1, r1, #0x10
@ anchor
    ands r1, r4
    lsrs r0, r0, #0x18
    lsrs r1, r1, #8
    orrs r0, r1
    movs r1, #0xff
    lsls r1, r1, #8
    ands r1, r4
    lsls r1, r1, #8
    orrs r0, r1
    lsls r1, r4, #0x18
    orrs r0, r1
    str r0, [sp]
    movs r2, #4
    add r0, sp, #4
    mov r1, sp
    blx #0 @ mask=call
    ldr r1, [pc, #0] @ mask=literal
    movs r0, #4
    adds r1, #0xb
    add r2, sp, #4
    blx #0 @ mask=call
    ldr r1, [pc, #0] @ mask=literal
    ldr r0, [pc, #0] @ mask=literal
    movs r3, r4
    movs r2, #0
    blx #0 @ mask=call
    str r0, [r4, #0x2c]
    pop {r2, r3, r4, pc}
@ end

@ Selects EOF state 3/5 at source +55, clears source field +32, then notifies.
@ The complete state transition is located beside the exported fragment initializer.
@ pattern AUDIO_EOF recipe thumb AUDIO_EOF
    push {r1, r2, r3, r4, r5, lr}
    movs r4, r0
    ldr r1, [r0, #0xc]
    movs r5, r4
@ anchor
    movs r0, #5
    adds r5, #0x20
    cmp r1, #0
    beq #6
    ldrb r1, [r5, #0x17]
    cmp r1, #4
    beq #0
    movs r0, #3
    movs r1, #0xff
    strb r0, [r5, #0x17]
    movs r0, #0
    lsls r1, r1, #0x10
    ands r1, r4
    str r0, [r4, #0x20]
    lsrs r0, r4, #0x18
    lsrs r1, r1, #8
    orrs r0, r1
    movs r1, #0xff
    lsls r1, r1, #8
    ands r1, r4
    lsls r1, r1, #8
    orrs r0, r1
    lsls r1, r4, #0x18
    orrs r0, r1
    str r0, [sp]
    movs r2, #4
    add r0, sp, #4
    mov r1, sp
    blx #0 @ mask=call
    ldrb r0, [r5, #0x17]
    mov r3, sp
    ldr r1, [pc, #0] @ mask=literal
    str r0, [sp]
    ldrb r0, [r3]
    adds r1, #0x15
    add r2, sp, #4
    strb r0, [r3, #8]
    movs r0, #5
    blx #0 @ mask=call
    movs r0, r4
    blx #0 @ mask=call
    pop {r1, r2, r3, r4, r5, pc}
@ end

@ Maps an input interval through native endpoint bytes +17/+18 and rounds.
@ The RAM global and repeated conversion/addition helper targets are checked.
@ pattern AUDIO_VOLUME recipe thumb AUDIO_VOLUME
    push {r3, r4, r5, r6, r7, lr}
    movs r6, r0
    ldr r0, [pc, #0] @ mask=literal
    movs r5, r1
@ anchor
    ldr r4, [r0]
    cmp r4, #0
    bne #2
    movs r0, #0
    pop {r3, r4, r5, r6, r7, pc}
    subs r0, r2, r5
    blx #0 @ mask=call
    movs r7, r0
    subs r0, r6, r5
    blx #0 @ mask=call
    movs r1, r7
    blx #0 @ mask=call
    movs r5, r0
    ldrb r0, [r4, #0x12]
    ldrb r4, [r4, #0x11]
    subs r0, r0, r4
    blx #0 @ mask=call
    movs r1, r5
    blx #0 @ mask=call
    movs r5, r0
    movs r0, r4
    blx #0 @ mask=call
    movs r1, r5
    blx #0 @ mask=call
    movs r1, #0x3f
    lsls r1, r1, #0x18
    blx #0 @ mask=call
    blx #0 @ mask=call
    pop {r3, r4, r5, r6, r7, pc}
@ end

@ Reads the requested PCM block; a live short read enters the wait helper.
@ The read export, wait routine and shared end-of-source predicate are verified.
@ pattern AUDIO_RAW_READ recipe thumb AUDIO_RAW_READ
    push {r0, r1, r2, r3, r4, r5, r6, r7, lr}
    ldr r7, [r3]
    movs r1, r2
    sub sp, #4
@ anchor
    ldr r0, [sp, #8]
    movs r6, r3
    movs r5, #0x40
    movs r2, r7
    blx #0 @ mask=call
    movs r4, r0
    cmp r0, r7
    bhs #22
    ldr r0, [sp, #8]
    blx #0 @ mask=call
    cmp r0, #0
    beq #2
    movs r5, #0xc0
    b #8
    ldr r1, [sp, #4]
    ldr r0, [sp, #8]
    blx #0 @ mask=call
    movs r5, r0
    movs r0, r5
    str r4, [r6]
    add sp, #0x14
    pop {r4, r5, r6, r7, pc}
@ end

@ Registers a firmware consumer callback and returns 0x100 while waiting.
@ Consumer/Source_Ok exports, end predicate and callback ROM bounds are checked.
@ pattern AUDIO_RAW_WAIT recipe thumb AUDIO_RAW_WAIT
    push {r4, r5, r6, lr}
    movs r4, #0xff
    ldr r2, [pc, #0] @ mask=literal
    movs r5, r0
    adds r4, #1
    blx #0 @ mask=call
@ anchor
    cmp r0, #0
    bne #22
    movs r4, #0
    movs r0, r5
    blx #0 @ mask=call
    cmp r0, #0
    beq #10
    movs r0, r5
    blx #0 @ mask=call
    cmp r0, #0
    beq #0
    movs r4, #0x80
    movs r0, r4
    pop {r4, r5, r6, pc}
@ end

@ Uses registration index +10 to validate its 16-byte task slot, then marks it ready.
@ Its current-task call must match the resolved OS export.
@ pattern AUDIO_READY recipe thumb AUDIO_READY
    push {r4, r5, r6, lr}
    movs r5, r0
    blx #0 @ mask=call
    ldr r1, [pc, #0] @ mask=literal
@ anchor
    lsls r0, r0, #2
    ldr r0, [r1, r0]
    ldrh r1, [r5, #0xa]
    ldr r0, [r0, #4]
    lsls r1, r1, #4
    adds r4, r0, r1
    ldr r0, [r4]
    cmp r0, r5
    beq #2
    blx #0 @ mask=call
    movs r0, #3
    strb r0, [r4, #0xe]
    pop {r4, r5, r6, pc}
@ end

@ The same +10 registration-index check, with an expanded diagnostic failure path.
@ Both variants retain the owner comparison and ready-state write.
@ pattern AUDIO_READY_TRACE recipe thumb AUDIO_READY_TRACE
    push {r3, r4, r5, lr}
    movs r5, r0
    blx #0 @ mask=call
    ldr r1, [pc, #0] @ mask=literal
@ anchor
    lsls r0, r0, #2
    ldr r0, [r1, r0]
    ldrh r3, [r5, #0xa]
    ldr r1, [r0, #4]
    lsls r2, r3, #4
    adds r4, r1, r2
    ldr r1, [r4]
    cmp r1, r5
    beq #18
    ldr r1, [r5]
    ldr r2, [r1, #0x10]
    str r2, [sp]
    ldr r1, [r0, #0x10]
    ldrh r2, [r4, #0xc]
    ldr r0, [pc, #0] @ mask=literal
    blx #0 @ mask=call
    blx #0 @ mask=call
    movs r0, #3
    strb r0, [r4, #0xe]
    pop {r3, r4, r5, pc}
@ end

@ Consumes cancelled writes; otherwise links the fragment and updates queue bytes +48.
@ Status +55, fragment count +52 and consumer notification establish the source layout.
@ pattern AUDIO_SOURCE_WRITE recipe thumb AUDIO_SOURCE_WRITE
    push {r1, r2, r3, r4, r5, r6, r7, lr}
    movs r4, r0
    movs r5, r1
    movs r7, #0xff
@ anchor
    lsls r7, r7, #0x10
    movs r1, r4
    ands r1, r7
    lsrs r0, r0, #0x18
    lsrs r1, r1, #8
    orrs r0, r1
    asrs r1, r7, #8
    ands r1, r4
    lsls r1, r1, #8
    orrs r0, r1
    lsls r1, r4, #0x18
    orrs r0, r1
    str r0, [sp]
    movs r2, #4
    add r6, sp, #4
    add r0, sp, #4
    mov r1, sp
    blx #0 @ mask=call
    ldr r0, [r5, #0xc]
    movs r2, r0
    ands r2, r7
    lsrs r1, r0, #0x18
    lsrs r2, r2, #8
    orrs r1, r2
    asrs r2, r7, #8
    ands r2, r0
    lsls r2, r2, #8
    orrs r1, r2
    lsls r0, r0, #0x18
    orrs r1, r0
    str r1, [sp]
    adds r0, r6, #4
    movs r2, #4
    mov r1, sp
    blx #0 @ mask=call
    ldr r1, [pc, #0] @ mask=literal
    movs r0, #8
    adds r1, #9
    add r2, sp, #4
    blx #0 @ mask=call
    movs r6, r4
    adds r6, #0x20
    ldrb r0, [r6, #0x17]
    cmp r0, #5
    bne #2
    blx #0 @ mask=call
    ldrb r0, [r6, #0x17]
    cmp r0, #3
    bne #2
    blx #0 @ mask=call
    ldrb r0, [r6, #0x17]
    cmp r0, #4
    bne #8
    movs r0, r5
    blx #0 @ mask=call
    movs r0, #4
    pop {r1, r2, r3, r4, r5, r6, r7, pc}
    ldrb r0, [r6, #0x16]
    cmp r0, #0
    beq #2
    blx #0 @ mask=call
    ldr r0, [r4, #0x30]
    ldr r1, [r5, #0xc]
    adds r0, r0, r1
    str r0, [r4, #0x30]
    ldr r0, [r4, #0x40]
    str r0, [r5, #8]
    ldr r0, [r4, #0x40]
    ldr r1, [r5, #0xc]
    adds r0, r0, r1
    str r0, [r4, #0x40]
    ldrh r0, [r4, #0x34]
    adds r0, r0, #1
    strh r0, [r4, #0x34]
    ldr r0, [r4, #0xc]
    cmp r0, #0
    bne #2
    str r5, [r5]
    b #6
    ldr r0, [r0]
    str r0, [r5]
    ldr r0, [r4, #0xc]
    str r5, [r0]
    str r5, [r4, #0xc]
    ldrb r0, [r6, #0x17]
    cmp r0, #4
    beq #6
    ldr r1, [r4, #0x64]
    movs r0, r4
    blx r1
    strb r0, [r6, #0x17]
    ldrb r0, [r6, #0x16]
    cmp r0, #0
    beq #2
    blx #0 @ mask=call
    ldr r1, [r4, #0x60]
    movs r0, r4
    blx r1
    ldrb r0, [r6, #0x17]
    cmp r0, #2
    bne #-96
    movs r0, #0
    pop {r1, r2, r3, r4, r5, r6, r7, pc}
@ end
