@ SPDX-License-Identifier: MIT
@ Instruction signatures for BatteryBindings.
@ Calls and literal operands are decoded and validated by the Java resolver.

@ Converts the stored charge estimate into capacity using the same nominal-capacity getter twice.
@ pattern BATTERY_CAPACITY recipe thumb battery_capacity
    ldr r0, [pc, #0] @ mask=literal
@ anchor
    push {r4, lr}
    ldr r0, [r0]
    movs r1, #0x3c
    subs r0, r0, #1
    lsls r0, r0, #1
    blx #0 @ mask=target
    adds r0, r0, #5
    lsls r4, r0, #0x10
    lsrs r4, r4, #0x10
    bl #0 @ mask=target
    cmp r0, r4
    bhi #4
    bl #0 @ mask=target
    movs r4, r0
    movs r0, r4
    pop {r4, pc}
@ end

@ Loads the displayed battery-bar count from the monitor record.
@ pattern BATTERY_BARS recipe thumb battery_bars
    ldr r0, [pc, #0] @ mask=literal
@ anchor
    subs r0, #0x1c
    ldrb r0, [r0, #4]
    bx lr
@ end

@ Loads the measured battery voltage as an unsigned halfword.
@ pattern BATTERY_VOLTAGE recipe thumb battery_voltage
    ldr r0, [pc, #0] @ mask=literal
@ anchor
    ldrh r0, [r0, #0xa]
    bx lr
@ end

@ Loads nominal capacity from the battery model; the caller must share the same model record.
@ pattern BATTERY_NOMINAL recipe thumb battery_nominal
    ldr r0, [pc, #0] @ mask=literal
@ anchor
    ldrh r0, [r0, #4]
    bx lr
@ end

@ Returns the charger-state byte referenced by status notifications.
@ pattern BATTERY_CHARGER recipe thumb battery_charger
    ldr r0, [pc, #0] @ mask=literal
@ anchor
    ldrb r0, [r0]
    bx lr
@ end

@ Constructs a battery-status notification, including charger state and displayed bar count.
@ pattern BATTERY_STATUS recipe thumb battery_status
@ anchor
    push {r3, r4, r5, lr}
    movs r3, #0
    movs r4, r0
    movs r2, r3
    cmp r0, #0
    beq #194
    lsrs r0, r4, #5
    bne #190
    lsls r0, r4, #0x1f
    beq #2
    movs r2, #1
    movs r3, #4
    lsls r0, r4, #0x1d
    bpl #10
    adds r2, r2, #2
    lsls r2, r2, #0x18
    adds r3, #8
    lsrs r2, r2, #0x18
    lsls r3, r3, #0x10
    lsrs r3, r3, #0x10
    lsls r0, r4, #0x1c
    bpl #10
    adds r2, r2, #2
    lsls r2, r2, #0x18
    adds r3, #8
    lsrs r2, r2, #0x18
    lsls r3, r3, #0x10
    lsrs r3, r3, #0x10
    lsls r0, r4, #0x1b
    bpl #10
    adds r2, r2, #1
    lsls r2, r2, #0x18
    adds r3, r3, #4
    lsrs r2, r2, #0x18
    lsls r3, r3, #0x10
    lsrs r3, r3, #0x10
    movs r1, r4
    movs r0, #0x21
    bl #0 @ mask=target
    movs r5, r0
    adds r0, #8
    str r0, [sp]
    lsls r0, r4, #0x1f
    beq #12
    bl #0 @ mask=target
    movs r2, r0
    movs r1, #0
    mov r0, sp
    bl #0 @ mask=target
    lsls r0, r4, #0x1d
    bpl #28
    bl #0 @ mask=target
    movs r2, r0
    movs r1, #0x13
    mov r0, sp
    bl #0 @ mask=target
    movs r0, #0
    bl #0 @ mask=target
    movs r2, r0
    movs r1, #0x14
    mov r0, sp
    bl #0 @ mask=target
    lsls r0, r4, #0x1c
    bpl #28
    bl #0 @ mask=target
    movs r3, r0
    movs r2, #4
    movs r1, #0x16
    mov r0, sp
    bl #0 @ mask=target
    bl #0 @ mask=target
    movs r2, r0
    movs r1, #0x15
    mov r0, sp
    bl #0 @ mask=target
    lsls r0, r4, #0x1b
    bpl #12
    bl #0 @ mask=target
    movs r2, r0
    movs r1, #0x15
    mov r0, sp
    bl #0 @ mask=target
    movs r0, #0x21
    ldrh r1, [r5, #2]
    lsls r0, r0, #0x11
    bl #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    movs r1, r5
    blx #0 @ mask=target
    pop {r3, r4, r5, pc}
@ end

@ Updates charge-monitor state and calls the delta and energy helpers on state transitions.
@ pattern BATTERY_RESUME recipe thumb battery_resume
    push {r3, r4, r5, r6, r7, lr}
    ldr r4, [pc, #0] @ mask=literal
@ anchor
    subs r4, #0x1c
    ldrb r1, [r4, #0xc]
    cmp r1, r0
    beq #80
    ldrb r1, [r4, #0x13]
    cmp r1, #0
    bne #74
    movs r6, #1
    movs r7, #0
    cmp r0, #0
    strb r0, [r4, #0xc]
    beq #232
    movs r1, r4
    ldrb r1, [r1, #0xf]
    cmp r0, #1
    beq #58
    cmp r0, #2
    bne #44
    cmp r1, #0
    beq #40
    bl #0 @ mask=target
    cmp r0, #1
    beq #32
    movs r0, #1
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    str r0, [r1]
    strb r6, [r4, #0x14]
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    movs r0, #0x12
    ldrsh r0, [r1, r0]
    bl #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    movs r1, r4
    subs r1, #0x20
    strh r0, [r1, #0x20]
    ldrb r1, [r4, #0xa]
    ldr r0, [pc, #0] @ mask=literal
    bl #0 @ mask=target
    pop {r3, r4, r5, r6, r7, pc}
    cmp r1, #0
    beq #-16
    bl #0 @ mask=target
    cmp r0, #1
    beq #112
    ldrb r0, [r4, #0x14]
    cmp r0, #0
    bne #34
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    subs r5, r0, #0
    strh r0, [r1, #0x20]
    bge #4
    lsrs r0, r5, #0x1f
    adds r0, r0, r5
    asrs r5, r0, #1
    ldr r0, [pc, #0] @ mask=literal
    movs r1, r5
    adds r0, r0, #2
    bl #0 @ mask=target
    movs r0, r5
    bl #0 @ mask=target
    b #6
    movs r0, #1
    bl #0 @ mask=target
    strb r7, [r4, #0x14]
    cmp r0, #1
    bhi #8
    ldr r0, [pc, #0] @ mask=literal
    str r6, [r0]
    strb r6, [r4, #0x16]
    strb r6, [r4, #0xa]
    b #8
    ldr r1, [pc, #0] @ mask=literal
    str r0, [r1]
    movs r0, #2
    strb r7, [r4, #0x16]
    strb r0, [r4, #0xa]
    bl #0 @ mask=target
    ldrb r0, [r4, #0xa]
    cmp r0, #2
    bne #28
    ldr r0, [pc, #0] @ mask=literal
    ldrh r0, [r0, #0x1a]
    cmp r0, #0
    beq #2
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    movs r0, #0xdc
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    ldrh r0, [r1, #0xa]
    strh r0, [r1, #0xe]
    strh r0, [r1, #0xc]
    strh r0, [r1, #0x22]
    strb r7, [r4, #0xf]
    ldrb r0, [r4, #0xe]
    cmp r0, #0
    bne #0
    movs r6, #0
    ldr r0, [pc, #0] @ mask=literal
    strb r6, [r4, #0xd]
    ldr r1, [r0]
    ldr r0, [pc, #0] @ mask=literal
    subs r0, #0xb
    bl #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    ldr r1, [pc, #0] @ mask=literal
    adds r0, #0x50
    strh r1, [r0, #0x36]
    strh r1, [r0, #0x34]
    strh r7, [r0, #0x38]
    b #-176
    b #-2
    bl #0 @ mask=target
    cmp r0, #1
    beq #34
    movs r0, #0xdc
    bl #0 @ mask=target
    bl #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    strb r7, [r4, #6]
    adds r0, #0x70
    strb r7, [r4, #0x13]
    strb r7, [r0, #0x1b]
    strb r7, [r0, #0x1c]
    strb r7, [r0, #0x1a]
    ldrb r0, [r4, #0x12]
    cmp r0, #0
    beq #2
    bl #0 @ mask=target
    strb r7, [r4, #0xa]
    movs r1, #0xff
    strb r6, [r4, #0xf]
    adds r1, #0x91
    movs r0, #0xe0
    strb r6, [r4, #0xd]
    bl #0 @ mask=target
    b #-238
@ end

@ Computes the energy estimate from the battery model and shared conversion constants.
@ pattern BATTERY_ENERGY recipe thumb battery_energy
@ anchor
    push {r0, r4, r5, r6, r7, lr}
    movs r0, #0x1f
    movs r1, #8
    lsls r0, r0, #0x11
    bl #0 @ mask=target
    ldr r5, [pc, #0] @ mask=literal
    ldr r7, [pc, #0] @ mask=literal
    ldrh r0, [r5, #0x1c]
    movs r6, r5
    movs r4, r5
    subs r6, #0x40
    adds r4, #0x20
    cmp r0, #0
    beq #14
    ldr r0, [pc, #0] @ mask=literal
    movs r2, #0
    movs r1, #8
    subs r0, r0, #2
    bl #0 @ mask=target
    movs r0, #0
    pop {r3, r4, r5, r6, r7, pc}
    ldr r0, [sp]
    blx #0 @ mask=target
    ldr r2, [pc, #0] @ mask=literal
    adds r2, #0x18
    ldm r2, {r2, r3}
    blx #0 @ mask=target
    ldr r2, [pc, #0] @ mask=literal
    adds r2, #0x20
    ldm r2, {r2, r3}
    blx #0 @ mask=target
    ldr r3, [r6, #4]
    ldr r2, [r6]
    blx #0 @ mask=target
    str r1, [r6, #4]
    str r0, [r6]
    ldr r3, [r7, #4]
    ldr r2, [r7]
    blx #0 @ mask=target
    bhs #4
    ldm r7!, {r0, r1}
    str r1, [r6, #4]
    str r0, [r6]
    ldr r3, [r6, #0x1c]
    ldr r2, [r6, #0x18]
    ldr r1, [r6, #4]
    ldr r0, [r6]
    blx #0 @ mask=target
    bhi #12
    ldr r2, [r6, #0x1c]
    ldr r0, [r6, #0x18]
    str r2, [r6, #4]
    movs r1, #1
    str r0, [r6]
    strb r1, [r4, #1]
    b #2
    movs r0, #0
    strb r0, [r4, #1]
    ldr r3, [r6, #0x1c]
    ldr r2, [r6, #0x18]
    ldm r6!, {r0, r1}
    bl #0 @ mask=target
    str r0, [r5, #0x10]
    ldrb r1, [r4, #1]
    cmp r1, #0
    bne #4
    strb r1, [r4, #3]
    strb r1, [r4]
    strh r1, [r5, #0x1a]
    movs r2, r0
    ldr r0, [pc, #0] @ mask=literal
    movs r1, #8
    subs r0, r0, #2
    bl #0 @ mask=target
    ldr r0, [r5, #0x10]
    pop {r3, r4, r5, r6, r7, pc}
@ end

@ Accounts for newly accumulated charge and returns its scaled signed delta.
@ pattern BATTERY_DELTA recipe thumb battery_delta
    ldr r2, [pc, #0] @ mask=literal
@ anchor
    push {r4, lr}
    ldr r1, [r2, #0x14]
    ldr r0, [r2, #0xc]
    cmp r0, r1
    ble #4
    subs r1, r0, r1
    str r1, [r2, #0x10]
    str r0, [r2, #0x14]
    movs r1, #0xe1
    ldr r0, [r2, #0x10]
    lsls r1, r1, #4
    blx #0 @ mask=target
    lsls r0, r0, #0x10
    asrs r0, r0, #0x10
    pop {r4, pc}
@ end

@ Loads the displayed battery-bar count from the monitor record.
@ This layout uses forward monitor references and its corresponding transition sequence.
@ pattern BATTERY_BARS_FORWARD recipe thumb battery_bars_forward
    ldr r0, [pc, #0] @ mask=literal
@ anchor
    adds r0, #0x20
    ldrb r0, [r0, #4]
    bx lr
@ end

@ Loads the measured battery voltage as an unsigned halfword.
@ This layout uses forward monitor references and its corresponding transition sequence.
@ pattern BATTERY_VOLTAGE_FORWARD recipe thumb battery_voltage_forward
    ldr r0, [pc, #0] @ mask=literal
@ anchor
    adds r0, #0x3c
    ldrh r0, [r0, #0xa]
    bx lr
@ end

@ Updates charge-monitor state and calls the delta and energy helpers on state transitions.
@ This layout uses forward monitor references and its corresponding transition sequence.
@ pattern BATTERY_RESUME_FORWARD recipe thumb battery_resume_forward
    push {r3, r4, r5, r6, r7, lr}
    ldr r4, [pc, #0] @ mask=literal
@ anchor
    subs r4, #0x1c
    ldrb r1, [r4, #0xc]
    cmp r1, r0
    beq #82
    ldrb r1, [r4, #0x13]
    cmp r1, #0
    bne #76
    movs r6, #1
    movs r7, #0
    cmp r0, #0
    strb r0, [r4, #0xc]
    beq #252
    movs r1, r4
    ldrb r1, [r1, #0xf]
    cmp r0, #1
    beq #60
    cmp r0, #2
    bne #44
    cmp r1, #0
    beq #40
    bl #0 @ mask=target
    cmp r0, #1
    beq #32
    movs r0, #1
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    str r0, [r1]
    strb r6, [r4, #0x14]
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    movs r0, #0x12
    ldrsh r0, [r1, r0]
    bl #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    movs r1, r4
    subs r1, #0x20
    strh r0, [r1, #0x20]
    ldr r0, [pc, #0] @ mask=literal
    ldrb r1, [r4, #0xa]
    subs r0, r0, #3
    bl #0 @ mask=target
    pop {r3, r4, r5, r6, r7, pc}
    cmp r1, #0
    beq #-18
    bl #0 @ mask=target
    cmp r0, #1
    beq #176
    ldrb r0, [r4, #0x14]
    cmp r0, #0
    bne #92
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    subs r5, r0, #0
    strh r0, [r1, #0x20]
    bge #4
    lsrs r0, r5, #0x1f
    adds r0, r0, r5
    asrs r5, r0, #1
    ldr r0, [pc, #0] @ mask=literal
    movs r1, r5
    b #54
    .space 56 @ mask=ignore
    subs r0, r0, #1
    bl #0 @ mask=target
    movs r0, r5
    bl #0 @ mask=target
    b #6
    movs r0, #1
    bl #0 @ mask=target
    strb r7, [r4, #0x14]
    cmp r0, #1
    bhi #8
    ldr r0, [pc, #0] @ mask=literal
    str r6, [r0]
    strb r6, [r4, #0x16]
    strb r6, [r4, #0xa]
    b #8
    ldr r1, [pc, #0] @ mask=literal
    str r0, [r1]
    movs r0, #2
    strb r7, [r4, #0x16]
    strb r0, [r4, #0xa]
    bl #0 @ mask=target
    ldrb r0, [r4, #0xa]
    cmp r0, #2
    bne #34
    ldr r0, [pc, #0] @ mask=literal
    ldrh r0, [r0, #0x1a]
    cmp r0, #0
    beq #2
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    movs r0, #0xdd
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    adds r1, #0x3c
    ldrh r0, [r1, #0xa]
    strh r0, [r1, #0xe]
    b #0
    b #38
    strh r0, [r1, #0xc]
    strh r0, [r1, #0x22]
    strb r7, [r4, #0xf]
    ldrb r0, [r4, #0xe]
    cmp r0, #0
    bne #0
    movs r6, #0
    ldr r0, [pc, #0] @ mask=literal
    strb r6, [r4, #0xd]
    ldr r1, [r0]
    ldr r0, [pc, #0] @ mask=literal
    bl #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    ldr r1, [pc, #0] @ mask=literal
    adds r0, #0x8c
    strh r1, [r0, #0x36]
    strh r1, [r0, #0x34]
    strh r7, [r0, #0x38]
    b #-240
    bl #0 @ mask=target
    cmp r0, #1
    beq #34
    movs r0, #0xdd
    bl #0 @ mask=target
    bl #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    strb r7, [r4, #6]
    adds r0, #0xac
    strb r7, [r4, #0x13]
    strb r7, [r0, #0x1b]
    strb r7, [r0, #0x1c]
    strb r7, [r0, #0x1a]
    ldrb r0, [r4, #0x12]
    cmp r0, #0
    beq #2
    bl #0 @ mask=target
    strb r7, [r4, #0xa]
    movs r1, #0xff
    strb r6, [r4, #0xf]
    adds r1, #0x91
    movs r0, #0xe1
    strb r6, [r4, #0xd]
    bl #0 @ mask=target
    b #-300
@ end

@ Constructs a battery-status notification, including charger state and displayed bar count.
@ This layout uses forward monitor references and its corresponding transition sequence.
@ pattern BATTERY_STATUS_FORWARD recipe thumb battery_status_forward
@ anchor
    push {r3, r4, r5, lr}
    movs r3, #0
    movs r4, r0
    movs r2, r3
    cmp r0, #0
    beq #194
    lsrs r0, r4, #5
    bne #190
    lsls r0, r4, #0x1f
    beq #2
    movs r2, #1
    movs r3, #4
    lsls r0, r4, #0x1d
    bpl #10
    adds r2, r2, #2
    lsls r2, r2, #0x18
    adds r3, #8
    lsrs r2, r2, #0x18
    lsls r3, r3, #0x10
    lsrs r3, r3, #0x10
    lsls r0, r4, #0x1c
    bpl #10
    adds r2, r2, #2
    lsls r2, r2, #0x18
    adds r3, #8
    lsrs r2, r2, #0x18
    lsls r3, r3, #0x10
    lsrs r3, r3, #0x10
    lsls r0, r4, #0x1b
    bpl #10
    adds r2, r2, #1
    lsls r2, r2, #0x18
    adds r3, r3, #4
    lsrs r2, r2, #0x18
    lsls r3, r3, #0x10
    lsrs r3, r3, #0x10
    movs r1, r4
    movs r0, #0x21
    bl #0 @ mask=target
    movs r5, r0
    adds r0, #8
    str r0, [sp]
    lsls r0, r4, #0x1f
    beq #12
    bl #0 @ mask=target
    movs r2, r0
    movs r1, #0
    mov r0, sp
    bl #0 @ mask=target
    lsls r0, r4, #0x1d
    bpl #28
    bl #0 @ mask=target
    movs r2, r0
    movs r1, #0x13
    mov r0, sp
    bl #0 @ mask=target
    movs r0, #0
    bl #0 @ mask=target
    movs r2, r0
    movs r1, #0x14
    mov r0, sp
    bl #0 @ mask=target
    lsls r0, r4, #0x1c
    bpl #28
    bl #0 @ mask=target
    movs r3, r0
    movs r2, #4
    movs r1, #0x16
    mov r0, sp
    bl #0 @ mask=target
    bl #0 @ mask=target
    movs r2, r0
    movs r1, #0x15
    mov r0, sp
    bl #0 @ mask=target
    lsls r0, r4, #0x1b
    bpl #12
    bl #0 @ mask=target
    movs r2, r0
    movs r1, #0x15
    mov r0, sp
    bl #0 @ mask=target
    movs r0, #0x41
    ldrh r1, [r5, #2]
    lsls r0, r0, #0x10
    bl #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    movs r1, r5
    blx #0 @ mask=target
    pop {r3, r4, r5, pc}
@ end

@ Computes the energy estimate from the battery model and shared conversion constants.
@ This layout uses forward monitor references and its corresponding transition sequence.
@ pattern BATTERY_ENERGY_FORWARD recipe thumb battery_energy_forward
@ anchor
    push {r0, r4, r5, r6, r7, lr}
    movs r0, #0x3d
    movs r1, #8
    lsls r0, r0, #0x10
    bl #0 @ mask=target
    ldr r5, [pc, #0] @ mask=literal
    ldr r7, [pc, #0] @ mask=literal
    ldrh r0, [r5, #0x1c]
    movs r6, r5
    movs r4, r5
    subs r6, #0x40
    adds r4, #0x20
    cmp r0, #0
    beq #14
    ldr r0, [pc, #0] @ mask=literal
    movs r2, #0
    movs r1, #8
    subs r0, r0, #2
    bl #0 @ mask=target
    movs r0, #0
    pop {r3, r4, r5, r6, r7, pc}
    ldr r0, [sp]
    blx #0 @ mask=target
    ldr r2, [pc, #0] @ mask=literal
    adds r2, #0x18
    ldm r2, {r2, r3}
    blx #0 @ mask=target
    ldr r2, [pc, #0] @ mask=literal
    adds r2, #0x20
    ldm r2, {r2, r3}
    blx #0 @ mask=target
    ldr r3, [r6, #4]
    ldr r2, [r6]
    blx #0 @ mask=target
    str r1, [r6, #4]
    str r0, [r6]
    ldr r3, [r7, #4]
    ldr r2, [r7]
    blx #0 @ mask=target
    bhs #4
    ldm r7!, {r0, r1}
    str r1, [r6, #4]
    str r0, [r6]
    ldr r3, [r6, #0x1c]
    ldr r2, [r6, #0x18]
    ldr r1, [r6, #4]
    ldr r0, [r6]
    blx #0 @ mask=target
    bhi #12
    ldr r2, [r6, #0x1c]
    ldr r0, [r6, #0x18]
    str r2, [r6, #4]
    movs r1, #1
    str r0, [r6]
    strb r1, [r4, #1]
    b #2
    movs r0, #0
    strb r0, [r4, #1]
    ldr r3, [r6, #0x1c]
    ldr r2, [r6, #0x18]
    ldm r6!, {r0, r1}
    bl #0 @ mask=target
    str r0, [r5, #0x10]
    ldrb r1, [r4, #1]
    cmp r1, #0
    bne #4
    strb r1, [r4, #3]
    strb r1, [r4]
    strh r1, [r5, #0x1a]
    movs r2, r0
    ldr r0, [pc, #0] @ mask=literal
    movs r1, #8
    subs r0, r0, #2
    bl #0 @ mask=target
    ldr r0, [r5, #0x10]
    pop {r3, r4, r5, r6, r7, pc}
@ end

@ Reads the completion flag from the same charge-accounting record used by DELTA.
@ pattern BATTERY_COMPLETE recipe thumb battery_complete
    ldr r0, [pc, #0] @ mask=literal
@ anchor
    ldrb r0, [r0, #0x1c]
    bx lr
@ end
