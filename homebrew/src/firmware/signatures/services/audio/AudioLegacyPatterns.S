@ SPDX-License-Identifier: MIT
@ Instruction signatures for AudioLegacyBindings.
@ Calls and literal operands are decoded and validated by the Java resolver.

@ Registration checks resource routing and reads the legacy object ID at +4.
@ pattern REGISTER_OBJECT recipe thumb register_object
    push {r4, r5, r6, r7, lr}
    adds r5, r1, #0
    adds r4, r0, #0
    adds r7, r2, #0
@ anchor
    sub sp, #0x5c
    beq #140
    bl #0 @ mask=target
    adds r1, r0, #0
    adds r2, r5, #0
    adds r0, r7, #0
    movs r3, #0
    bl #0 @ mask=target
    cmp r0, #0
    beq #2
    movs r0, #1
    b #0
    movs r0, #0
    cmp r0, #0
    beq #110
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    lsls r0, r0, #2
    ldr r5, [r1, r0]
    lsrs r0, r7, #8
    lsls r1, r7, #8
    orrs r0, r1
    mov r3, sp
    str r0, [sp, #4]
    ldrb r0, [r3, #4]
    movs r6, #0x54
    movs r2, #0x50
    strb r0, [r3, #8]
    ldrb r0, [r3, #5]
    add r7, sp, #8
    strb r0, [r3, #9]
    ldrh r0, [r4, #4]
    lsrs r1, r0, #8
    lsls r0, r0, #8
    orrs r0, r1
    str r0, [sp, #4]
    ldrb r0, [r3, #4]
    strb r0, [r3, #0xa]
    ldrb r0, [r3, #5]
    strb r0, [r3, #0xb]
    ldr r0, [r4]
    ldr r1, [r0, #0x10]
    adds r0, r7, #4
    bl #0 @ mask=target
    adds r0, #4
    lsls r4, r0, #0x10
    lsrs r4, r4, #0x10
    subs r0, r6, r4
    lsls r2, r0, #0x10
    lsrs r2, r2, #0x10
    ldr r1, [r5, #0x10]
    adds r0, r7, r4
    bl #0 @ mask=target
    adds r0, r0, r4
    lsls r4, r0, #0x10
    lsrs r4, r4, #0x10
    cmp r4, #0x54
    bls #2
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    add r2, sp, #8
    adds r1, #2
    adds r0, r4, #0
    bl #0 @ mask=target
    movs r0, #0
    b #-1786
    adds r2, r7, #0
    adds r1, r5, #0
    adds r0, r4, #0
    bl #0 @ mask=target
    b #-16
@ end

@ Ready uses registration index +8 and a 16-byte per-task object slot.
@ pattern READY recipe thumb ready
    push {r4, r5, r6, lr}
    adds r5, r0, #0
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    lsls r0, r0, #2
    ldr r0, [r1, r0]
@ anchor
    ldrh r1, [r5, #8]
    ldr r0, [r0, #4]
    lsls r1, r1, #4
    adds r4, r0, r1
    ldr r0, [r4]
    cmp r0, r5
    beq #2
    bl #0 @ mask=target
    movs r0, #3
    strb r0, [r4, #0xe]
    pop {r4, r5, r6, pc}
@ end

@ Unregister validates that the indexed slot belongs to this object.
@ pattern UNREGISTER_OBJECT recipe thumb unregister_object
    push {r4, r5, r6, r7, lr}
    adds r6, r0, #0
    sub sp, #0x5c
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    lsls r0, r0, #2
@ anchor
    ldr r5, [r1, r0]
    ldrh r1, [r6, #8]
    ldr r0, [r5, #4]
    ldr r7, [r5]
    lsls r2, r1, #4
    adds r4, r0, r2
    ldr r0, [pc, #0] @ mask=literal
    cmp r1, r0
    beq #4
    ldr r0, [r4]
    cmp r0, r6
    beq #36
    ldr r1, [r5, #0x10]
@ end

@ SendToServer deletes the owned message if no server is registered.
@ pattern SEND recipe thumb send
    push {r4, r5, r6, lr}
    adds r6, r1, #0
    adds r4, r0, #0
    adds r5, r2, #0
    bl #0 @ mask=target
    cmp r0, #0
@ anchor
    beq #12
    lsls r2, r4, #0x18
    lsrs r2, r2, #0x18
    adds r3, r5, #0
    adds r1, r6, #0
    bl #0 @ mask=target
    pop {r4, r5, r6, pc}
    adds r0, r5, #0
    bl #0 @ mask=target
    movs r0, #5
    mvns r0, r0
    pop {r4, r5, r6, pc}
@ end

@ Active_Source construction followed by its +96/+100 watermarks setter.
@ pattern SOURCE_CREATE recipe thumb source_create
    push {r4, lr}
    ldr r1, [pc, #0] @ mask=literal
    ldr r0, [pc, #0] @ mask=literal
    bl #0 @ mask=target
@ anchor
    pop {r4, pc}
    str r1, [r0, #0x60]
    str r2, [r0, #0x64]
    bx lr
@ end

@ A CM_Source_Producer is constructed and stored at source +44.
@ pattern SOURCE_SHARE recipe thumb source_share
    push {r4, lr}
    ldr r1, [pc, #0] @ mask=literal
    adds r4, r0, #0
    adds r3, r0, #0
    movs r2, #0
    ldr r0, [pc, #0] @ mask=literal
    subs r1, #3
    bl #0 @ mask=target
@ anchor
    str r0, [r4, #0x2c]
    pop {r4, pc}
@ end

@ Factory records hold type 19; publication unregisters the original source.
@ pattern SOURCE_ID recipe thumb source_id
    push {r3, r4, r5, r6, r7, lr}
    adds r6, r0, #0
    movs r5, #0
    ldr r7, [pc, #0] @ mask=literal
    movs r4, #0
@ anchor
    subs r7, #8
    ldr r1, [r7]
    ldr r2, [r7, #4]
    b #70
    lsls r0, r4, #2
    ldr r0, [r1, r0]
    cmp r0, #0
    bne #60
    movs r0, #0xc
    bl #0 @ mask=target
    ldr r2, [r7]
    lsls r1, r4, #2
    str r0, [r2, r1]
    movs r1, #0x13
    str r1, [r0, #8]
    adds r5, r0, #0
    str r6, [r0]
    ldr r0, [pc, #0] @ mask=literal
    ldrb r0, [r0]
    lsls r0, r0, #8
    adds r1, r0, r4
    movs r0, #0
    str r1, [r5, #4]
    str r0, [r6, #0x1c]
    ldrh r0, [r6, #8]
    ldr r1, [pc, #0] @ mask=literal
    cmp r0, r1
    beq #14
    adds r0, r6, #0
    bl #0 @ mask=target
@ end

@ Forget consumes types 5/19 and destroys the untaken source with C005.
@ pattern SOURCE_FORGET recipe thumb source_forget
    push {r4, lr}
@ anchor
    ldr r1, [r0, #4]
    cmp r1, #5
    beq #2
    cmp r1, #0x13
    bne #12
    bl #0 @ mask=target
    cmp r0, #0
    beq #4
    ldr r1, [pc, #0] @ mask=literal
    bl #0 @ mask=target
    pop {r4, pc}
@ end

@ Sets EOF state 3/5 at source +55 and clears source field +32 before notification.
@ pattern SOURCE_EOF recipe thumb source_eof
    ldr r2, [r0, #0xc]
    adds r1, r0, #0
    adds r1, #0x20
    cmp r2, #0
@ anchor
    beq #4
    ldrb r2, [r1, #0x17]
    cmp r2, #4
    bne #2
    movs r2, #5
    b #0
    movs r2, #3
    strb r2, [r1, #0x17]
    movs r1, #0
    str r1, [r0, #0x20]
    b #-66
@ end

@ The seven-word fragment owns data and uses a default freeing destructor.
@ pattern FRAGMENT_INIT recipe thumb fragment_init
    push {r3, r4, r5, r6, r7, lr}
    adds r7, r3, #0
    adds r6, r2, #0
    adds r5, r1, #0
    ldr r4, [sp, #0x18]
    cmp r0, #0
    bne #4
    movs r0, #0x1c
    bl #0 @ mask=target
    movs r1, #0
    str r1, [r0]
@ anchor
    str r1, [r0, #0x14]
    str r1, [r0, #8]
    str r5, [r0, #4]
    str r6, [r0, #0xc]
    cmp r4, #0
    str r7, [r0, #0x10]
    beq #2
    str r4, [r0, #0x18]
    pop {r3, r4, r5, r6, r7, pc}
    ldr r1, [pc, #0] @ mask=literal
    str r1, [r0, #0x18]
    pop {r3, r4, r5, r6, r7, pc}
@ end

@ Queue bytes +48, fragments +52 and status +55; cancellation consumes data.
@ pattern SOURCE_WRITE recipe thumb source_write
    push {r4, r5, r6, lr}
    adds r6, r0, #0
    adds r6, #0x20
    adds r4, r0, #0
@ anchor
    ldrb r0, [r6, #0x17]
    adds r5, r1, #0
    cmp r0, #5
    bne #2
    bl #0 @ mask=target
    ldrb r0, [r6, #0x17]
    cmp r0, #3
    bne #2
    bl #0 @ mask=target
    ldrb r0, [r6, #0x17]
    cmp r0, #4
    bne #8
    adds r0, r5, #0
    bl #0 @ mask=target
    movs r0, #4
    pop {r4, r5, r6, pc}
    ldrb r0, [r6, #0x16]
    cmp r0, #0
    beq #2
    bl #0 @ mask=target
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
    adds r0, #1
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
    ldrb r0, [r6, #0x16]
    cmp r0, #0
    beq #2
    bl #0 @ mask=target
    ldr r1, [r4, #0x58]
    adds r0, r4, #0
    blx r1
    ldrb r1, [r6, #0x17]
    cmp r1, #4
    beq #0
    strb r0, [r6, #0x17]
    ldrb r0, [r6, #0x17]
    pop {r4, r5, r6, pc}
@ end

@ Map percent through native range bytes +17/+18 and round the result.
@ pattern VOLUME_SCALE recipe thumb volume_scale
    push {r3, r4, r5, r6, r7, lr}
    adds r7, r0, #0
    ldr r0, [pc, #0] @ mask=literal
    adds r5, r1, #0
    ldr r4, [r0]
    cmp r4, #0
    bne #2
    movs r0, #0
@ anchor
    pop {r3, r4, r5, r6, r7, pc}
    subs r0, r2, r5
    blx #0 @ mask=target
    adds r6, r0, #0
    subs r0, r7, r5
    blx #0 @ mask=target
    adds r1, r6, #0
    blx #0 @ mask=target
    adds r5, r0, #0
    ldrb r0, [r4, #0x12]
    ldrb r4, [r4, #0x11]
    subs r0, r0, r4
    blx #0 @ mask=target
    adds r1, r5, #0
    blx #0 @ mask=target
    adds r5, r0, #0
    adds r0, r4, #0
    blx #0 @ mask=target
    adds r1, r5, #0
    blx #0 @ mask=target
    movs r1, #0x3f
    lsls r1, r1, #0x18
    blx #0 @ mask=target
    blx #0 @ mask=target
    pop {r3, r4, r5, r6, r7, pc}
@ end

@ Method dispatch preserves the original object and method, supplying zero arguments.
@ pattern CLASS_CALL recipe thumb class_call
@ anchor
    push {r3, r4, r5, lr}
    adds r4, r0, #0
    adds r5, r1, #0
    movs r2, #0
    str r2, [sp]
    bl #0 @ mask=target
    adds r3, r0, #0
    adds r0, r4, #0
    adds r1, r5, #0
    adds r4, r3, #0
    movs r2, #0
    movs r3, #0
    blx r4
    pop {r3, r4, r5, pc}
@ end

@ Skips null and the task's current message; otherwise frees the allocation at message -8.
@ Its free call must match the resolved OS deallocator.
@ pattern DELETE_MESSAGE recipe thumb delete_message
@ anchor
    push {r4, lr}
    adds r4, r0, #0
    beq #16
    adds r0, r4, #0
    bl #0 @ mask=target
    cmp r0, #0
    bne #6
    adds r0, r4, #0
    subs r0, #8
    bl #0 @ mask=target
    pop {r4, pc}
@ end

@ Releases the data pointer at fragment +4, then releases the fragment itself.
@ Both calls must match the resolved OS deallocator.
@ pattern FRAGMENT_DESTRUCTOR recipe thumb fragment_destructor
@ anchor
    push {r4, lr}
    adds r4, r0, #0
    ldr r0, [r0, #4]
    bl #0 @ mask=target
    adds r0, r4, #0
    bl #0 @ mask=target
    pop {r4, pc}
@ end
