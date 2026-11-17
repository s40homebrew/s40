@ SPDX-License-Identifier: MIT
@ Instruction signatures for AudioPolicyBindings.
@ Calls and literal operands are decoded and validated by the Java resolver.

@ These roots preserve native layout operations and control flow across
@ the two 301 builds. Calls and literals are checked through relationships
@ after the single batched search; no firmware address is a lookup key.
@ Registers the device-addressed object using its ID at +8.
@ Task/context references and the shared stack-release epilogue are checked.
@ pattern AUDIO_POLICY_CM_REGISTER recipe thumb AUDIO_POLICY_CM_REGISTER
    push {r4, r5, r6, r7, lr}
    movs r4, r2
    sub sp, #0x5c
    mov r5, r0
@ anchor
    mov r6, r1
    beq #114
    blx #0 @ mask=call
    mov r1, r0
    movs r3, #0
    mov r2, r6
    mov r0, r4
    blx #0 @ mask=call
    cmp r0, #0
    beq #94
    blx #0 @ mask=call
    ldr r1, [pc, #0] @ mask=literal
    lsls r0, r0, #2
    movs r7, #0x54
    movs r2, #0x50
    ldr r6, [r1, r0]
    lsls r0, r4, #0x10
    lsrs r1, r0, #0x18
    lsls r0, r4, #8
    orrs r1, r0
    mov r0, sp
    add r4, sp, #4
    strh r1, [r0]
    strh r1, [r0, #4]
    ldrh r0, [r5, #8]
    lsrs r1, r0, #8
    lsls r0, r0, #8
    orrs r1, r0
    mov r0, sp
    strh r1, [r0]
    strh r1, [r0, #6]
    ldr r0, [r5]
    ldr r1, [r0, #0x10]
    add r0, sp, #8
    blx #0 @ mask=call
    adds r0, r0, #4
    ldr r1, [r6, #0x10]
    uxth r5, r0
    subs r0, r7, r5
    uxth r2, r0
    adds r0, r4, r5
    blx #0 @ mask=call
    adds r0, r0, r5
    uxth r4, r0
    cmp r4, #0x54
    bls #2
    blx #0 @ mask=call
    ldr r1, [pc, #0] @ mask=literal
    add r2, sp, #4
    mov r0, r4
    blx #0 @ mask=call
    movs r0, #0
    b #0 @ mask=target
    mov r2, r4
    mov r1, r6
    mov r0, r5
    blx #0 @ mask=call
    b #0 @ mask=target
@ end

@ Uses registration index +12 to verify its task-slot owner before setting state 3.
@ Register, Unregister and Send must use the same task accessor and context.
@ pattern AUDIO_POLICY_CM_READY recipe thumb AUDIO_POLICY_CM_READY
    push {r3, r4, r5, lr}
    mov r5, r0
    blx #0 @ mask=call
    ldr r1, [pc, #0] @ mask=literal
    lsls r0, r0, #2
    ldrh r3, [r5, #0xc]
    ldr r0, [r1, r0]
    lsls r2, r3, #4
@ anchor
    ldr r1, [r0, #4]
    adds r4, r1, r2
    ldr r1, [r4]
    cmp r1, r5
    beq #18
    ldr r1, [r5]
    ldr r1, [r1, #0x10]
    str r1, [sp]
    ldr r1, [r0, #0x10]
    ldrh r2, [r4, #0xc]
    ldr r0, [pc, #0] @ mask=literal
    blx #0 @ mask=call
    blx #0 @ mask=call
    movs r0, #3
    strb r0, [r4, #0xe]
    pop {r3, r4, r5, pc}
@ end

@ Validates the indexed object slot before unregistering it.
@ Factory publication must call this same routine; its task/context links are checked.
@ pattern AUDIO_POLICY_CM_UNREGISTER recipe thumb AUDIO_POLICY_CM_UNREGISTER
    push {r4, r5, r6, r7, lr}
    sub sp, #0x64
    mov r6, r0
    blx #0 @ mask=call
    ldr r1, [pc, #0] @ mask=literal
    lsls r0, r0, #2
@ anchor
    ldr r4, [r1, r0]
    ldrh r1, [r6, #0xc]
    ldr r0, [pc, #0] @ mask=literal
    ldr r2, [r4, #4]
    ldr r7, [r4]
    lsls r3, r1, #4
    adds r5, r2, r3
    cmp r1, r0
    beq #4
    ldr r0, [r5]
    cmp r0, r6
    beq #52
    ldr r1, [r4, #0x10]
    movs r2, #0x1e
    add r0, sp, #0x14
    blx #0 @ mask=call
@ end

@ Selects the per-task server endpoint and deletes the message on failure.
@ The delete call, task/context references and shared return are followed and checked.
@ pattern AUDIO_POLICY_CM_SEND recipe thumb AUDIO_POLICY_CM_SEND
    push {r0, r1, r2, r4, r5, r6, r7, lr}
@ anchor
    sub sp, #0x60
    mov r6, r1
    blx #0 @ mask=call
    ldr r1, [pc, #0] @ mask=literal
    lsls r0, r0, #2
    ldr r7, [r1, r0]
    ldr r0, [sp, #0x60]
    blx #0 @ mask=call
    mov r5, r0
    movs r0, #0x56
    str r0, [sp, #0x5c]
    rev16 r0, r5
    movs r2, #0x50
    lsls r0, r0, #0x10
    str r0, [sp, #4]
    ldrh r1, [r6, #8]
    lsrs r0, r1, #8
    lsls r1, r1, #8
    orrs r0, r1
    mov r1, sp
    strh r0, [r1]
    strh r0, [r1, #8]
    ldr r1, [r6]
    add r0, sp, #8
    adds r0, #2
    ldr r1, [r1, #0x10]
    blx #0 @ mask=call
    adds r0, r0, #6
    ldr r1, [r7, #0x10]
    uxth r4, r0
    ldr r0, [sp, #0x5c]
    subs r0, r0, r4
    uxth r2, r0
    add r0, sp, #4
    adds r0, r0, r4
    blx #0 @ mask=call
    adds r0, r0, r4
    uxth r4, r0
    cmp r4, #0x56
    bls #2
    blx #0 @ mask=call
    ldr r1, [pc, #0] @ mask=literal
    add r2, sp, #4
    mov r0, r4
    subs r1, r1, #1
    blx #0 @ mask=call
    cmp r5, #0
    beq #12
    ldr r3, [sp, #0x68]
    ldr r2, [sp, #0x60]
    mov r1, r6
    mov r0, r5
    blx #0 @ mask=call
    b #0 @ mask=target
    ldr r0, [sp, #0x68]
    blx #0 @ mask=call
    movs r0, #5
    mvns r0, r0
    b #0 @ mask=target
@ end

@ Constructs Active_Source, then sets its +120/+124 watermarks.
@ C003, the 132/120/16-byte class chain and the method dispatcher are checked.
@ pattern AUDIO_POLICY_SOURCE_CREATE recipe thumb AUDIO_POLICY_SOURCE_CREATE
    ldr r1, [pc, #0] @ mask=literal
    ldr r0, [pc, #0] @ mask=literal
    push {r4, lr}
    blx #0 @ mask=call
    pop {r4, pc}
@ anchor
    push {r1, r2, r3, lr}
    str r2, [r0, #0x7c]
    str r1, [r0, #0x78]
    rev r2, r0
    rev r1, r1
    str r2, [sp]
    str r1, [sp, #4]
    ldr r0, [r0, #0x7c]
    mov r2, sp
    rev r1, r0
    movs r0, #0xc
    str r1, [sp, #8]
    ldr r1, [pc, #0] @ mask=literal
    adds r1, r1, #2
    blx #0 @ mask=call
    pop {r1, r2, r3, pc}
@ end

@ Constructs CM_Source_Producer and stores it at source +48.
@ Its 24-byte class shares ConnLayer with the source; its dispatcher is validated.
@ pattern AUDIO_POLICY_SOURCE_SHARE recipe thumb AUDIO_POLICY_SOURCE_SHARE
    rev r1, r0
    push {r3, r4, r5, lr}
    mov r4, r0
    str r1, [sp]
    ldr r1, [pc, #0] @ mask=literal
    movs r0, #4
    mov r2, sp
    adds r1, r1, #6
    blx #0 @ mask=call
    ldr r1, [pc, #0] @ mask=literal
    ldr r0, [pc, #0] @ mask=literal
    movs r2, #0
    mov r3, r4
    blx #0 @ mask=call
@ anchor
    str r0, [r4, #0x30]
    pop {r3, r4, r5, pc}
@ end

@ Publishes a type-22 factory record and unregisters the original source.
@ Allocation, deallocation and Unregister calls must agree with other resolved routines.
@ pattern AUDIO_POLICY_SOURCE_ID recipe thumb AUDIO_POLICY_SOURCE_ID
@ anchor
    push {r3, r4, r5, r6, r7, lr}
    rev r1, r0
    mov r6, r0
    str r1, [sp]
    ldr r1, [pc, #0] @ mask=literal
    movs r0, #4
    mov r2, sp
    blx #0 @ mask=call
    movs r0, #0xb9
    blx #0 @ mask=call
    ldr r4, [pc, #0] @ mask=literal
    subs r4, #8
    movs r7, #0
    ldr r1, [r4]
    ldr r0, [r4, #4]
    b #8
    lsls r2, r7, #2
    ldr r2, [r1, r2]
    cmp r2, #0
    beq #56
    adds r7, r7, #1
    cmp r7, r0
    blo #-16
    ldr r5, [r4, #4]
    mov r0, r5
    adds r0, #8
    str r0, [r4, #4]
    lsls r0, r0, #2
    blx #0 @ mask=call
    mov r7, r0
    ldr r0, [r4, #4]
    lsls r1, r0, #2
    mov r0, r7
    blx #0 @ mask=call
    cmp r5, #0
    beq #92
    lsls r2, r5, #2
    ldr r1, [r4]
    mov r5, r4
    mov r0, r7
    blx #0 @ mask=call
    ldr r0, [r5]
    blx #0 @ mask=call
    str r7, [r5]
    b #-76
    movs r0, #0xc
    blx #0 @ mask=call
    ldr r1, [r4]
    mov r5, r0
    lsls r0, r7, #2
    movs r2, #0x16
    str r5, [r1, r0]
    ldr r0, [pc, #0] @ mask=literal
    str r6, [r5]
    str r2, [r5, #8]
    ldrb r0, [r0]
    lsls r0, r0, #8
    adds r1, r0, r7
    movs r0, #0
    str r1, [r5, #4]
    str r0, [r6, #0x20]
    ldr r0, [pc, #0] @ mask=literal
    ldrh r1, [r6, #0xc]
    cmp r1, r0
    beq #14
    mov r0, r6
    blx #0 @ mask=call
    ldr r0, [r5, #4]
    movs r1, #1
    lsls r1, r1, #0x1f
    orrs r0, r1
    str r0, [r5, #4]
    adds r5, r5, #4
    beq #-116
    movs r0, #0xb9
    blx #0 @ mask=call
    mov r0, r5
    pop {r3, r4, r5, r6, r7, pc}
    str r7, [r4]
    b #-152
@ end

@ Consumes type-5/type-22 IDs and destroys an untaken source through C005.
@ The adjusted selector literal and dispatcher must match source construction.
@ pattern AUDIO_POLICY_SOURCE_FORGET recipe thumb AUDIO_POLICY_SOURCE_FORGET
    push {r4, lr}
    mov r4, r0
    ldr r0, [pc, #0] @ mask=literal
    adds r0, r0, #1
    blx #0 @ mask=call
    ldr r0, [r4, #4]
    cmp r0, #5
    beq #2
@ anchor
    cmp r0, #0x16
    bne #16
    mov r0, r4
    blx #0 @ mask=call
    cmp r0, #0
    beq #6
    ldr r1, [pc, #0] @ mask=literal
    subs r1, #0x18
    blx #0 @ mask=call
    pop {r4, pc}
@ end

@ Sets EOF state 3/5 at source +64, clears source field +36 and notifies.
@ These field accesses establish the device-source EOF layout.
@ pattern AUDIO_POLICY_SOURCE_EOF recipe thumb AUDIO_POLICY_SOURCE_EOF
    push {r2, r3, r4, lr}
    mov r4, r0
@ anchor
    ldr r2, [r0, #0x10]
    movs r1, #5
    adds r0, #0x40
    cmp r2, #0
    beq #6
    ldrb r2, [r0]
    cmp r2, #4
    beq #0
    movs r1, #3
    strb r1, [r0]
    movs r1, #0
    str r1, [r4, #0x24]
    rev r1, r4
    mov r2, sp
    str r1, [sp]
    ldrb r1, [r0]
    movs r0, #5
    strb r1, [r2, #4]
    ldr r1, [pc, #0] @ mask=literal
    adds r1, #0x15
    blx #0 @ mask=call
    mov r0, r4
    blx #0 @ mask=call
    pop {r2, r3, r4, pc}
@ end

@ Initializes a 28-byte fragment with data, lengths and an optional destructor.
@ The default destructor, allocator and stack-release epilogue are checked separately.
@ pattern AUDIO_POLICY_FRAGMENT_INIT recipe thumb AUDIO_POLICY_FRAGMENT_INIT
    push {r4, r5, r6, r7, lr}
    movs r4, r0
    sub sp, #0x14
    mov r5, r1
    mov r6, r2
    mov r7, r3
    bne #6
    movs r0, #0x1c
    blx #0 @ mask=call
    mov r4, r0
    movs r0, #0
    str r7, [r4, #0x10]
    str r6, [r4, #0xc]
    str r5, [r4, #4]
@ anchor
    ldr r1, [sp, #0x28]
    str r0, [r4]
    str r0, [r4, #0x14]
    cmp r1, #0
    str r0, [r4, #8]
    beq #2
    ldr r0, [sp, #0x28]
    b #0
    ldr r0, [pc, #0] @ mask=literal
    rev r1, r7
    mov r2, sp
    str r0, [r4, #0x18]
    rev r0, r4
    str r1, [sp, #0xc]
    str r0, [sp]
    rev r0, r5
    movs r1, #0x43
    str r0, [sp, #4]
    rev r0, r6
    lsls r1, r1, #0x10
    str r0, [sp, #8]
    movs r0, #0x10
    blx #0 @ mask=call
    mov r0, r4
@ end

@ Rejects terminal writes or links a fragment, updating queue +52 and end +76.
@ State +64 and consumer callbacks establish the active-source queue contract.
@ pattern AUDIO_POLICY_SOURCE_WRITE recipe thumb AUDIO_POLICY_SOURCE_WRITE
    push {r2, r3, r4, r5, r6, lr}
    mov r4, r0
@ anchor
    rev r0, r0
    mov r5, r1
    str r0, [sp]
    ldr r0, [r1, #0xc]
    mov r2, sp
    rev r1, r0
    movs r0, #8
    str r1, [sp, #4]
    ldr r1, [pc, #0] @ mask=literal
    adds r1, r1, #4
    blx #0 @ mask=call
    mov r6, r4
    adds r6, #0x40
    ldrb r0, [r6]
    cmp r0, #5
    bne #2
    blx #0 @ mask=call
    ldrb r0, [r6]
    cmp r0, #3
    bne #2
    blx #0 @ mask=call
    ldrb r0, [r6]
    cmp r0, #4
    beq #90
    ldr r0, [r4, #0x3c]
    cmp r0, #0
    beq #2
    blx #0 @ mask=call
    ldr r0, [r4, #0x34]
    ldr r1, [r5, #0xc]
    adds r0, r0, r1
    str r0, [r4, #0x34]
    ldr r0, [r4, #0x4c]
    str r0, [r5, #8]
    ldr r0, [r4, #0x4c]
    ldr r1, [r5, #0xc]
    adds r0, r0, r1
    str r0, [r4, #0x4c]
    ldrh r0, [r4, #0x38]
    adds r0, r0, #1
    strh r0, [r4, #0x38]
    ldr r0, [r4, #0x10]
    cmp r0, #0
    beq #58
    ldr r0, [r0]
    str r0, [r5]
    ldr r0, [r4, #0x10]
    str r5, [r0]
    str r5, [r4, #0x10]
    ldrb r0, [r6]
    cmp r0, #4
    beq #6
    ldr r1, [r4, #0x70]
    mov r0, r4
    blx r1
    strb r0, [r6]
    ldr r0, [r4, #0x3c]
    cmp r0, #0
    beq #2
    blx #0 @ mask=call
    ldr r1, [r4, #0x6c]
    mov r0, r4
    blx r1
    ldrb r0, [r6]
    cmp r0, #2
    bne #0
    movs r0, #0
    pop {r2, r3, r4, r5, r6, pc}
    mov r0, r5
    blx #0 @ mask=call
    movs r0, #4
    pop {r2, r3, r4, r5, r6, pc}
    str r5, [r5]
    b #-58
@ end

@ Maps an input interval through endpoint bytes +13/+14 and rounds to a native level.
@ The RAM global and repeated floating-point conversion/addition calls are checked.
@ pattern AUDIO_POLICY_VOLUME_SCALE recipe thumb AUDIO_POLICY_VOLUME_SCALE
    push {r3, r4, r5, r6, r7, lr}
    mov r5, r1
    mov r6, r0
    ldr r0, [pc, #0] @ mask=literal
    ldr r4, [r0]
    cmp r4, #0
@ anchor
    beq #64
    subs r0, r2, r5
    blx #0 @ mask=call
    mov r7, r0
    subs r0, r6, r5
    blx #0 @ mask=call
    mov r1, r7
    blx #0 @ mask=call
    mov r5, r0
    ldrb r0, [r4, #0xe]
    ldrb r4, [r4, #0xd]
    subs r0, r0, r4
    blx #0 @ mask=call
    mov r1, r5
    blx #0 @ mask=call
    mov r5, r0
    mov r0, r4
    blx #0 @ mask=call
    mov r1, r5
    blx #0 @ mask=call
    movs r1, #0x3f
    lsls r1, r1, #0x18
    blx #0 @ mask=call
    blx #0 @ mask=call
    pop {r3, r4, r5, r6, r7, pc}
    movs r0, #0
    pop {r3, r4, r5, r6, r7, pc}
@ end

@ Loads the raw context at player +276 and records the number of bytes read.
@ Its cross-bank call must lead to the checked PCM reader and consumer-wait helper.
@ pattern AUDIO_POLICY_RAW_READER recipe thumb AUDIO_POLICY_RAW_READER
    movs r1, #0xff
@ anchor
    adds r1, #0x15
    push {r4, lr}
    ldr r4, [r1, r0]
    adds r0, #0xc0
    ldr r1, [r0, #4]
    mov r3, r4
    ldr r2, [r4, #0x2c]
    adds r3, #0x30
    mov r0, r4
    blx #0 @ mask=call
    ldr r1, [r4, #0x30]
    str r1, [r4, #0x34]
    pop {r4, pc}
@ end

@ Builds 0x0BA0 with an application descriptor, flags 12 and a stream subblock.
@ Existing applications use the shorter request; the default-preference getter is checked.
@ pattern AUDIO_POLICY_POLICY recipe thumb AUDIO_POLICY_POLICY
    push {r4, r5, r6, r7, lr}
    sub sp, #0x14
    mov r5, r0
    movs r0, #0
    str r0, [sp, #0x10]
    mov r4, r1
    mov r0, r5
    blx #0 @ mask=call
    cmp r0, #0
    str r0, [sp, #0xc]
    beq #242
    mov r0, r5
    str r5, [sp]
    blx #0 @ mask=call
    movs r5, r0
    beq #62
    ldr r7, [pc, #0] @ mask=literal
    subs r7, #0x20
    ldr r0, [r7, #0x14]
    ldr r0, [r0, #8]
    mov r8, r8
    mov r6, r0
@ anchor
    ldr r1, [r7, #0x14]
    ldr r0, [r5, #0x4c]
    cmp r1, #0
    str r0, [sp, #4]
    beq #40
    movs r5, #0
    b #32
    ldr r0, [r7, #0x14]
    mov r1, r5
    blx #0 @ mask=call
    ldr r2, [r0, #0x4c]
    ldr r1, [sp, #4]
    cmp r2, r1
    bne #14
    ldr r2, [r0, #4]
    ldr r1, [sp]
    cmp r2, r1
    beq #6
    adds r0, #0x40
    ldrh r0, [r0]
    cmp r0, #0
    bne #4
    adds r5, r5, #1
    cmp r5, r6
    blt #-40
    ldr r5, [sp, #0xc]
    ldr r0, [pc, #0] @ mask=literal
    movs r7, #0xa0
    adds r5, #0x40
    ldrh r1, [r5]
    cmp r1, #0
    beq #30
    movs r4, #1
    str r4, [r0]
    movs r0, #0xc
    blx #0 @ mask=call
    mov r1, r0
    adds r1, #8
    mov ip, r1
    strb r7, [r0, #1]
    ldrh r1, [r5]
    lsrs r1, r1, #8
    strb r1, [r0, #2]
    ldrh r1, [r5]
    strb r1, [r0, #3]
    strb r4, [r0, #7]
    b #80
    movs r6, #2
    str r6, [r0]
    blx #0 @ mask=call
    str r0, [sp]
    movs r0, #0x1c
    blx #0 @ mask=call
    mov r2, r0
    adds r2, #8
    mov r1, r0
    adds r1, #0x18
    strb r7, [r0, #1]
    movs r3, #0
    mov ip, r1
    mov r1, r6
    strb r3, [r0, #2]
    strb r3, [r0, #3]
    strb r1, [r0, #7]
    ldr r1, [sp]
    movs r5, #1
    strb r5, [r0, #8]
    ldr r5, [sp]
    movs r6, #0x10
    lsrs r7, r4, #8
    strb r6, [r0, #9]
    strb r3, [r2, #2]
    strb r7, [r2, #4]
    lsrs r1, r1, #8
    strb r4, [r2, #5]
    strb r1, [r2, #6]
    strb r5, [r2, #7]
    strb r3, [r2, #8]
    strb r3, [r2, #9]
    strb r3, [r2, #0xa]
    strb r3, [r2, #0xb]
    strb r3, [r2, #0xc]
    strb r3, [r2, #0xd]
    movs r1, #0xc
    strb r3, [r2, #0xe]
    strb r1, [r2, #0xf]
    mov r2, ip
    movs r4, #3
    movs r3, #4
    strb r4, [r2]
    strb r3, [r2, #1]
    movs r1, #1
    strb r1, [r2, #2]
    movs r3, #0xb
    str r3, [sp, #4]
    str r1, [sp]
    str r0, [sp, #8]
    ldr r1, [pc, #0] @ mask=literal
    ldr r0, [pc, #0] @ mask=literal
    ldr r2, [sp, #0xc]
    movs r3, #0
    blx #0 @ mask=call
    cmp r0, #1
    bne #0
    str r0, [sp, #0x10]
    ldr r0, [sp, #0x10]
    add sp, #0x14
    pop {r4, r5, r6, r7, pc}
@ end

@ Resolves a method and invokes it with the original object/selector and no arguments.
@ Source creation and Forget share this dispatcher; CLASS_CALL2 shares its lookup helper.
@ pattern AUDIO_POLICY_CLASS_CALL recipe thumb AUDIO_POLICY_CLASS_CALL
@ anchor
    push {r3, r4, r5, r6, r7, lr}
    mov r5, r0
    movs r0, #0
    str r0, [sp]
    mov r6, r1
    mov r0, r5
    blx #0 @ mask=call
    movs r3, #0
    mov r4, r0
    mov r2, r3
    mov r1, r6
    mov r0, r5
    blx r4
    pop {r3, r4, r5, r6, r7, pc}
@ end

@ Skips null and the task's current message; otherwise frees the allocation at message -8.
@ The deallocator must match factory-record and fragment cleanup.
@ pattern AUDIO_POLICY_DELETE recipe thumb AUDIO_POLICY_DELETE
@ anchor
    push {r4, lr}
    movs r4, r0
    beq #14
    blx #0 @ mask=call
    cmp r0, #0
    bne #6
    mov r0, r4
    subs r0, #8
    blx #0 @ mask=call
    pop {r4, pc}
@ end

@ Frees both the fragment data at +4 and the fragment itself.
@ Both free calls must use the same deallocator as message and factory cleanup.
@ pattern AUDIO_POLICY_DESTRUCTOR recipe thumb AUDIO_POLICY_DESTRUCTOR
@ anchor
    push {r3, r4, r5, lr}
    rev r1, r0
    mov r4, r0
    str r1, [sp]
    ldr r1, [pc, #0] @ mask=literal
    movs r0, #4
    mov r2, sp
    blx #0 @ mask=call
    ldr r0, [r4, #4]
    blx #0 @ mask=call
    mov r0, r4
    blx #0 @ mask=call
    pop {r3, r4, r5, pc}
@ end

@ Preserves two arguments across method lookup and invokes the resolved method.
@ The lookup helper matches CLASS_CALL; its shared return releases the expected stack.
@ pattern AUDIO_POLICY_CLASS_CALL2 recipe thumb AUDIO_POLICY_CLASS_CALL2
@ anchor
    push {r0, r1, r2, r3, r4, r5, r6, r7, lr}
    sub sp, #4
    mov r4, r0
    movs r0, #0
    mov r5, r1
    str r0, [sp]
    mov r6, r2
    mov r0, r4
    blx #0 @ mask=call
    mov r7, r0
    ldr r3, [sp, #0x10]
    mov r2, r6
    mov r1, r5
    mov r0, r4
    blx r7
    b #-40
@ end

@ Defaults to preference 4, with manifest overrides for music 3 and navigation 28.
@ Both override comparisons must use the same string-comparison helper.
@ pattern AUDIO_POLICY_PREFERENCE recipe thumb AUDIO_POLICY_PREFERENCE
    push {r4, r5, lr}
    sub sp, #0x14
@ anchor
    movs r5, #0
    movs r4, #4
    str r5, [sp, #0x10]
    blx #0 @ mask=call
    blx #0 @ mask=call
    cmp r0, #0x20
    bne #58
    movs r0, #6
    mov r1, sp
    str r5, [sp]
    blx #0 @ mask=call
    ldr r0, [sp]
    add r2, sp, #0x10
    adr r1, #0 @ mask=literal
    blx #0 @ mask=call
    cmp r0, #0
    bne #34
    ldr r0, [sp, #0x10]
    cmp r0, #0
    beq #28
    ldr r1, [pc, #0] @ mask=literal
    blx #0 @ mask=call
    cmp r0, #0
    beq #12
    ldr r0, [sp, #0x10]
    adr r1, #0 @ mask=literal
    blx #0 @ mask=call
    cmp r0, #0
    beq #4
    b #4
    movs r4, #3
    b #0
    movs r4, #0x1c
    add sp, #0x14
    mov r0, r4
    pop {r4, r5, pc}
@ end
