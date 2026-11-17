@ SPDX-License-Identifier: MIT
@ Instruction signatures for AudioDeviceBindings.
@ Calls and literal operands are decoded and validated by the Java resolver.

@ The paired constructor/limits helper and source state operations establish
@ the 16-byte ComMgr / 120-byte source layout. Calls and literal addresses
@ are decoded separately; native field accesses and control flow stay exact.
@ Constructs Active_Source, followed by watermarks at source +120/+124.
@ The checked class literal and exported dispatcher identify the constructor.
@ pattern AUDIO_DEVICE_CREATE recipe thumb AUDIO_DEVICE_CREATE
    ldr r1, [pc, #0] @ mask=literal
    ldr r0, [pc, #0] @ mask=literal
    push {r4, lr}
    blx #0 @ mask=call
@ anchor
    pop {r4, pc}
    push {r0, r1, r2, r3, r4, r5, r6, lr}
    mov r4, r1
    mov r5, r2
    str r2, [r0, #0x7c]
    str r1, [r0, #0x78]
    rev r0, r0
    movs r2, #4
    str r0, [sp]
    add r6, sp, #4
    add r0, sp, #4
@ end

@ Constructs a 24-byte producer and stores it at source +48.
@ Its class parent, _CallClass2 target and shared return epilogue are checked.
@ pattern AUDIO_DEVICE_SHARE recipe thumb AUDIO_DEVICE_SHARE
    push {r2, r3, r4, lr}
    mov r4, r0
    rev r0, r0
    movs r2, #4
@ anchor
    str r0, [sp]
    add r0, sp, #4
    mov r1, sp
    blx #0 @ mask=call
    ldr r1, [pc, #0] @ mask=literal
    movs r0, #4
    add r2, sp, #4
    adds r1, #0xe
    blx #0 @ mask=call
    ldr r1, [pc, #0] @ mask=literal
    ldr r0, [pc, #0] @ mask=literal
    movs r2, #0
    subs r1, r1, #3
    mov r3, r4
    blx #0 @ mask=call
    str r0, [r4, #0x30]
    b #0 @ mask=target
@ end

@ Selects EOF state 3/5 at source +64 and clears source field +36 before notifying.
@ This layout is checked beside the exported fragment initializer.
@ pattern AUDIO_DEVICE_EOF recipe thumb AUDIO_DEVICE_EOF
    push {r1, r2, r3, r4, r5, lr}
    mov r5, r0
    ldr r1, [r0, #0x10]
    mov r4, r5
@ anchor
    movs r0, #5
    adds r4, #0x40
    cmp r1, #0
    beq #6
    ldrb r1, [r4]
    cmp r1, #4
    beq #0
    movs r0, #3
    strb r0, [r4]
    movs r0, #0
    str r0, [r5, #0x24]
    rev r0, r5
    movs r2, #4
    str r0, [sp]
    add r0, sp, #4
    mov r1, sp
    blx #0 @ mask=call
    ldrb r0, [r4]
    mov r3, sp
    ldr r1, [pc, #0] @ mask=literal
    str r0, [sp]
    ldrb r0, [r3]
    adds r1, #0x15
    add r2, sp, #4
    strb r0, [r3, #8]
    movs r0, #5
    blx #0 @ mask=call
    mov r0, r5
    blx #0 @ mask=call
    pop {r1, r2, r3, r4, r5, pc}
@ end

@ Looks up registration index +12, validates the slot owner and writes ready state 3.
@ The task lookup must be the resolved current-task export.
@ pattern AUDIO_DEVICE_READY recipe thumb AUDIO_DEVICE_READY
    push {r3, r4, r5, lr}
    mov r5, r0
    blx #0 @ mask=call
    ldr r1, [pc, #0] @ mask=literal
@ anchor
    lsls r0, r0, #2
    ldrh r3, [r5, #0xc]
    ldr r0, [r1, r0]
    lsls r2, r3, #4
    ldr r1, [r0, #4]
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

@ Consumes cancelled fragments or queues data at source +52 and notifies the consumer.
@ Status +64 and end-position +76 distinguish this source layout from the classic one.
@ pattern AUDIO_DEVICE_WRITE recipe thumb AUDIO_DEVICE_WRITE
    push {r1, r2, r3, r4, r5, r6, r7, lr}
    movs r2, #4
    mov r4, r0
    rev r0, r0
@ anchor
    mov r5, r1
    str r0, [sp]
    add r6, sp, #4
    add r0, sp, #4
    mov r1, sp
    blx #0 @ mask=call
    ldr r0, [r5, #0xc]
    movs r2, #4
    mov r1, sp
    rev r0, r0
    str r0, [sp]
    adds r0, r6, #4
    blx #0 @ mask=call
    ldr r1, [pc, #0] @ mask=literal
    movs r0, #8
    add r2, sp, #4
    adds r1, #0xc
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
    bne #8
    mov r0, r5
    blx #0 @ mask=call
    movs r0, #4
    pop {r1, r2, r3, r4, r5, r6, r7, pc}
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
    bne #2
    str r5, [r5]
    b #6
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
    bne #-96
    movs r0, #0
    pop {r1, r2, r3, r4, r5, r6, r7, pc}
@ end

@ ARM/VFP mapping through native endpoint bytes +13/+14, preserving integer arguments.
@ The RAM-global literal and 0.5f rounding constant are verified; ARM state is retained.
@ pattern AUDIO_DEVICE_VOLUME recipe arm AUDIO_DEVICE_VOLUME
    ldr r3, [pc] @ mask=literal
    vpush {d8}
@ anchor
    ldr r3, [r3]
    cmp r3, #0
    moveq r0, #0
    beq #72
    sub r0, r0, r1
    vmov s0, r0
    sub r0, r2, r1
    vmov s1, r0
    ldrb r0, [r3, #0xd]
    ldrb r1, [r3, #0xe]
    vcvt.f32.u32 s0, s0
    sub r1, r1, r0
    vcvt.f32.u32 s1, s1
    vmov s2, r1
    vcvt.f32.s32 s2, s2
    vdiv.f32 s0, s0, s1
    vmov s1, r0
    vcvt.f32.u32 s1, s1
    vmla.f32 s1, s0, s2
    vldr s0, [pc] @ mask=0xff00,0xffff
    vadd.f32 s0, s1, s0
    vcvt.u32.f32 s16, s0
    vmov r0, s16
    vpop {d8}
    bx lr
@ end

@ Fills a requested PCM block and delegates live underflow to WAIT.
@ Both device backends check the wait/end links; the exported backend also checks Read.
@ pattern AUDIO_DEVICE_READER recipe thumb AUDIO_DEVICE_READER
    push {r0, r1, r2, r3, r4, r5, r6, r7, lr}
    sub sp, #4
    mov r1, r2
    movs r5, #0x40
@ anchor
    mov r6, r3
    ldr r7, [r3]
    ldr r0, [sp, #8]
    mov r2, r7
    blx #0 @ mask=call
    cmp r0, r7
    mov r4, r0
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
    mov r5, r0
    mov r0, r5
    str r4, [r6]
    add sp, #0x14
    pop {r4, r5, r6, r7, pc}
@ end

@ Installs a firmware consumer callback, returning 0x100 for pending input.
@ Callers check the shared end predicate and callback; exports add consumer/OK checks.
@ pattern AUDIO_DEVICE_WAIT recipe thumb AUDIO_DEVICE_WAIT
    push {r4, r5, r6, lr}
    movs r4, #0xff
    ldr r2, [pc, #0] @ mask=literal
    adds r4, #1
    mov r5, r0
    blx #0 @ mask=call
@ anchor
    cmp r0, #0
    bne #22
    movs r4, #0
    mov r0, r5
    blx #0 @ mask=call
    cmp r0, #0
    beq #10
    mov r0, r5
    blx #0 @ mask=call
    cmp r0, #0
    beq #0
    movs r4, #0x80
    mov r0, r4
    pop {r4, r5, r6, pc}
@ end
