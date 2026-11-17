@ SPDX-License-Identifier: MIT
@ Instruction signatures for VideoBindings.
@ LCD call targets, callbacks and ownership data are validated by the resolver.

@ Named public update -> panel wrapper -> RGB submit. Hooks and completion layouts must agree.
@ pattern UPDATE recipe thumb update
@ anchor
    push {r4, r5, r6, lr}
    movs r4, r0
    movs r5, r1
    movs r0, #1
    bl #0 @ mask=target
    ldr r6, [pc, #0] @ mask=literal
    ldr r0, [r6, #0xc]
    cmp r0, #0
    beq #0
    blx r0
    ldrb r0, [r6]
    ldr r1, [pc, #0] @ mask=literal
    lsls r0, r0, #2
    ldr r2, [r1, r0]
    movs r1, r5
    movs r0, r4
    blx r2
    movs r0, #1
    pop {r4, r5, r6, pc}
@ end

@ Increment the pending count, install completion, then select driver submit at table+48.
@ pattern WRAPPER recipe thumb wrapper
    push {r4, r5, r6, lr}
    ldr r4, [pc, #0] @ mask=literal
@ anchor
    movs r5, r1
    ldr r1, [r4, #4]
    adds r1, r1, #1
    str r1, [r4, #4]
    movs r1, r0
    movs r0, #0
    bl #0 @ mask=target
    ldrb r0, [r4]
    lsls r1, r0, #2
    ldr r0, [pc, #0] @ mask=literal
    adds r0, #0x30
    ldr r2, [r0, r1]
    cmp r2, #0
    beq #6
    ldr r0, [pc, #0] @ mask=literal
    movs r1, r5
    blx r2
@ end

@ Selector zero stores the callback at +8; completion decrements +4 and releases the gate.
@ pattern SET_COMPLETION recipe thumb set_completion
    ldr r2, [pc, #0] @ mask=literal
@ anchor
    push {r4, lr}
    cmp r0, #0
    bne #2
    str r1, [r2, #8]
    pop {r4, pc}
    ldr r4, [r2, #8]
    ldr r0, [r2, #4]
    subs r0, r0, #1
    str r0, [r2, #4]
    movs r0, #0
    bl #0 @ mask=target
    cmp r4, #0
    beq #-22
    blx r4
    pop {r4, pc}
@ end

@ Gate changes run under the paired IRQ lock and use display-hooks+8.
@ pattern GATE recipe thumb gate
@ anchor
    push {r4, r5, r6, lr}
    movs r5, r0
    bl #0 @ mask=target
    ldr r4, [pc, #0] @ mask=literal
    ldr r0, [r4, #8]
    cmp r0, #0
    beq #6
    bl #0 @ mask=target
    movs r0, r5
    b #26
    cmp r5, #0
    bne #2
    movs r0, #0
    b #32
    bl #0 @ mask=target
    bl #0 @ mask=target
@ end

@ Bus clock 25, data pins 16..23/mode 2, and peripheral control writes.
@ pattern SUBMIT_BUS recipe thumb submit_bus
@ anchor
    push {r1, r2, r3, r4, r5, r6, r7, lr}
    movs r4, r0
    movs r6, r1
    bl #0 @ mask=target
    ldr r7, [pc, #0] @ mask=literal
    ldr r0, [r7, #8]
    lsrs r0, r0, #1
    lsls r0, r0, #1
    str r0, [r7, #8]
    bl #0 @ mask=target
    movs r2, #0
    movs r1, #1
    movs r0, #0x19
    bl #0 @ mask=target
    movs r2, #0
    movs r1, #1
    movs r0, #0x19
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    ldr r0, [pc, #0] @ mask=literal
    str r1, [r0, #0x28]
    ldr r1, [pc, #0] @ mask=literal
    str r1, [r0, #0x24]
    movs r1, #2
    movs r0, #0x10
    bl #0 @ mask=target
    movs r1, #2
    movs r0, #0x11
    bl #0 @ mask=target
    movs r1, #2
    movs r0, #0x12
    bl #0 @ mask=target
    movs r1, #2
    movs r0, #0x13
    bl #0 @ mask=target
    movs r1, #2
    movs r0, #0x14
    bl #0 @ mask=target
    movs r1, #2
    movs r0, #0x15
    bl #0 @ mask=target
    movs r1, #2
    movs r0, #0x16
    bl #0 @ mask=target
    movs r1, #2
    movs r0, #0x17
    bl #0 @ mask=target
@ end

@ Panel revision/mode/power offsets and stock window/arm arguments.
@ pattern SUBMIT_STATE recipe thumb submit_state
    ldr r1, [pc, #0] @ mask=literal
@ anchor
    movs r5, #1
    adds r1, #0x40
    ldrb r0, [r1, #6]
    cmp r0, #1
    beq #10
    bl #0 @ mask=target
    ldr r0, [r7, #8]
    lsls r0, r0, #0x1c
    bpl #-8
    b #160
    ldrb r0, [r1, #8]
    ldr r2, [pc, #0] @ mask=literal
    cmp r0, #0
    bne #222
    movs r0, r2
    adds r0, #0x18
    ldrb r1, [r0, #4]
    cmp r1, #1
    bne #216
    movs r1, r2
    ldrb r1, [r1, #4]
    cmp r1, #0
    beq #208
    movs r2, r0
    ldrh r0, [r2, #2]
    ldrh r1, [r2]
    movs r3, #0x2d
    lsls r3, r3, #4
    subs r0, r0, r1
    muls r0, r3, r0
    asrs r1, r0, #0x1f
    lsrs r1, r1, #0x1e
    adds r0, r1, r0
    asrs r0, r0, #2
    str r0, [sp]
    ldrh r0, [r2, #2]
    subs r0, r0, #1
    lsls r1, r0, #0x10
    ldrh r0, [r2]
    lsrs r1, r1, #0x10
    movs r2, #0
    movs r3, #0xef
    bl #0 @ mask=target
    movs r2, #0
    ldr r0, [sp]
    movs r1, r2
    bl #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    str r4, [r0, #8]
@ end

@ DMA request 18, state callbacks, watchdog 80000us and channel/armed fields.
@ pattern SUBMIT_DMA recipe thumb submit_dma
    ldr r3, [pc, #0] @ mask=literal
@ anchor
    adds r0, r1, r0
    ldr r2, [sp]
    movs r1, #0x12
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    strb r0, [r1, #5]
    ldrb r0, [r1, #5]
    cmp r0, #0xff
    bne #58
    bl #0 @ mask=target
    ldr r0, [r7, #8]
    lsls r0, r0, #0x1c
    bpl #-8
    movs r2, #0
    movs r1, r2
    movs r0, #0x19
    bl #0 @ mask=target
    movs r2, #0
    movs r1, r2
    movs r0, #0x19
    bl #0 @ mask=target
    bl #0 @ mask=target
    ldr r0, [r7, #8]
    orrs r0, r5
    str r0, [r7, #8]
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    ldr r0, [pc, #0] @ mask=literal
    str r1, [r0, #0x28]
    movs r1, #0
    str r1, [r0, #0x24]
    b #76
    blx r4
    pop {r1, r2, r3, r4, r5, r6, r7, pc}
    strb r5, [r1, #2]
    ldr r2, [pc, #0] @ mask=literal
    ldr r0, [pc, #0] @ mask=literal
    movs r1, #0
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    strb r0, [r1]
    strb r5, [r1, #1]
@ end

@ Wait for the bus, reset/start control, configure timing, and write one command word.
@ pattern LCD_COMMAND recipe thumb lcd_command
    ldr r1, [pc, #0] @ mask=literal
@ anchor
    ldr r2, [r1, #8]
    lsls r2, r2, #0x1c
    bpl #-8
    movs r2, #2
    str r2, [r1, #8]
    movs r2, #1
    str r2, [r1, #8]
    ldr r3, [pc, #0] @ mask=literal
    ldr r2, [r3, #4]
    ldr r3, [r3]
    lsls r2, r2, #0x1a
    lsrs r2, r2, #2
    lsls r3, r3, #0x1c
    lsrs r3, r3, #8
    orrs r2, r3
    str r2, [r1, #4]
    str r0, [r1, #0x10]
    movs r0, #1
    bx lr
@ end

@ Byte-write entry preserves source/count and configures the same LCD bus as command writes.
@ pattern LCD_WRITE recipe thumb lcd_write
@ anchor
    push {r3, r4, r5, r6, r7, lr}
    movs r6, r0
    ldr r0, [pc, #0] @ mask=literal
    movs r5, r1
    ldr r1, [r0, #8]
    lsls r1, r1, #0x1c
    bpl #-8
    movs r1, #2
    str r1, [r0, #8]
    movs r1, #1
    str r1, [r0, #8]
    ldr r7, [pc, #0] @ mask=literal
    ldr r1, [r7, #4]
    ldr r3, [r7]
    lsls r1, r1, #0x1a
    lsrs r2, r1, #2
@ end

@ Fixed-timing read discards the first bus value and stores the next byte through r0.
@ pattern LCD_READ_BYTE recipe thumb lcd_read_byte
    ldr r1, [pc, #0] @ mask=literal
@ anchor
    ldr r2, [r1, #8]
    lsls r2, r2, #0x1c
    bpl #-8
    ldr r2, [pc, #0] @ mask=literal
    str r2, [r1, #4]
    ldr r2, [r1, #0x10]
    ldr r1, [r1, #0x10]
    strb r1, [r0]
    movs r0, #1
    bx lr
@ end

@ Abort writes control value 2; its register base must agree with the submit path.
@ pattern LCD_ABORT recipe thumb lcd_abort
    ldr r1, [pc, #0] @ mask=literal
@ anchor
    movs r0, #2
    str r0, [r1, #8]
    movs r0, #1
    bx lr
@ end

@ Arm waits for bus readiness, resets control, then handles an optional descriptor in r2.
@ pattern LCD_ARM recipe thumb lcd_arm
    push {r3, r4, r5, r6, r7, lr}
    ldr r5, [pc, #0] @ mask=literal
@ anchor
    ldr r3, [r5, #8]
    lsls r3, r3, #0x1c
    bpl #-8
    movs r3, #2
    str r3, [r5, #8]
    movs r3, #1
    str r3, [r5, #8]
    ldr r6, [pc, #0] @ mask=literal
    lsls r7, r3, #0x12
    cmp r2, #0
    beq #98
    ldr r3, [r2]
    str r3, [r5, #0x14]
    ldr r3, [r2, #4]
@ end

@ Word read discards the initial value and reorders four bus bytes into the result at r0.
@ pattern LCD_READ_WORD recipe thumb lcd_read_word
    ldr r1, [pc, #0] @ mask=literal
@ anchor
    push {r3, lr}
    ldr r2, [r1, #8]
    lsls r2, r2, #0x1c
    bpl #-8
    ldr r2, [pc, #0] @ mask=literal
    str r2, [r1, #4]
    ldr r2, [r1, #0x10]
    mov r3, sp
    strb r2, [r3]
    ldr r2, [r1, #0x10]
    strb r2, [r3, #3]
    ldr r2, [r1, #0x10]
    strb r2, [r3, #2]
    ldr r2, [r1, #0x10]
    strb r2, [r3, #1]
    ldr r1, [r1, #0x10]
    strb r1, [r3]
    ldr r1, [sp]
    str r1, [r0]
    movs r0, #1
    pop {r3, pc}
@ end

@ Encode row/column bounds as big-endian bytes for commands 2B/2A, then issue memory-write 2C.
@ pattern LCD_WINDOW recipe thumb lcd_window
@ anchor
    push {r3, r4, r5, lr}
    movs r4, r2
    movs r5, r3
    lsrs r2, r0, #8
    mov r3, sp
    strb r2, [r3]
    strb r0, [r3, #1]
    lsrs r0, r1, #8
    strb r0, [r3, #2]
    movs r0, #0x2b
    strb r1, [r3, #3]
    bl #0 @ mask=target
    movs r1, #4
    mov r0, sp
    bl #0 @ mask=target
    lsrs r0, r4, #8
    mov r3, sp
    strb r0, [r3]
    lsrs r0, r5, #8
    strb r4, [r3, #1]
    strb r0, [r3, #2]
    movs r0, #0x2a
    strb r5, [r3, #3]
    bl #0 @ mask=target
    movs r1, #4
    mov r0, sp
    bl #0 @ mask=target
    movs r0, #0x2c
    bl #0 @ mask=target
    movs r0, #1
    pop {r3, r4, r5, pc}
@ end

@ Stock DMA completion owns IRQ, bus clocks, pins and the user completion callback.
@ pattern DMA_COMPLETE recipe thumb dma_complete
    push {r4, r5, r6, lr}
    bl #0 @ mask=target
    ldr r4, [pc, #0] @ mask=literal
@ anchor
    ldrb r0, [r4, #1]
    cmp r0, #1
    bne #146
    ldrb r0, [r4]
    bl #0 @ mask=target
    movs r6, #0
    strb r6, [r4, #1]
    strb r6, [r4, #2]
    movs r0, r4
    adds r0, #0x18
    strb r6, [r0, #4]
    ldr r5, [r4, #8]
    ldr r4, [pc, #0] @ mask=literal
    ldr r0, [r4, #8]
    lsls r0, r0, #0x1c
    bpl #-8
    movs r2, #0
    movs r1, r2
    movs r0, #0x19
    bl #0 @ mask=target
    movs r2, #0
    movs r1, r2
    movs r0, #0x19
    bl #0 @ mask=target
    bl #0 @ mask=target
    ldr r0, [r4, #8]
    movs r1, #1
    orrs r0, r1
    str r0, [r4, #8]
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    ldr r0, [pc, #0] @ mask=literal
    str r1, [r0, #0x28]
    str r6, [r0, #0x24]
    movs r1, #0x20
    movs r0, #0x10
    bl #0 @ mask=target
    movs r1, #0x20
    movs r0, #0x11
    bl #0 @ mask=target
    movs r1, #0x20
    movs r0, #0x12
    bl #0 @ mask=target
    movs r1, #0x20
    movs r0, #0x13
    bl #0 @ mask=target
    movs r1, #0x20
    movs r0, #0x14
    bl #0 @ mask=target
    movs r1, #0x20
    movs r0, #0x15
    bl #0 @ mask=target
    movs r1, #0x20
    movs r0, #0x16
    bl #0 @ mask=target
    movs r1, #0x20
    movs r0, #0x17
    bl #0 @ mask=target
    bl #0 @ mask=target
    cmp r5, #0
    beq #-214
    blx r5
    b #-218
    bl #0 @ mask=target
    b #-224
@ end

@ Watchdog failure records state 3 at LCD-state+1 before the stock failure-reporting path.
@ pattern TIMEOUT recipe thumb timeout
@ anchor
    push {r4, lr}
    movs r0, #3
    ldr r1, [pc, #0] @ mask=literal
    strb r0, [r1, #1]
    bl #0 @ mask=target
    movs r1, #0xef
    adr r0, #1012
    bl #0 @ mask=target
    pop {r4, pc}
@ end

@ Pin/mode entry validates mode <63 and pin <79, then decodes the mode bit fields.
@ pattern GPIO_MODE recipe thumb gpio_mode
@ anchor
    push {r0, r1, r4, r5, r6, r7, lr}
    movs r5, r1
    cmp r1, #0x3f
    sub sp, #0xc
    blo #2
    bl #0 @ mask=target
    ldr r0, [sp, #0xc]
    cmp r0, #0x4f
    blo #2
    bl #0 @ mask=target
    lsls r0, r5, #0x1a
    str r0, [sp, #8]
    lsrs r0, r0, #0x1d
    cmp r0, #7
@ end

@ Paired interrupt-control helpers write one to set (+8) and clear (+12) registers.
@ pattern IRQ_LOCK recipe thumb irq_lock
    ldr r1, [pc, #0] @ mask=literal
@ anchor
    movs r0, #1
    str r0, [r1, #8]
    bx lr
@ end

@ Unlock complements IRQ_LOCK; these callbacks bracket peripheral state changes.
@ pattern IRQ_UNLOCK recipe thumb irq_unlock
    ldr r1, [pc, #0] @ mask=literal
@ anchor
    movs r0, #1
    str r0, [r1, #0xc]
    bx lr
@ end

@ Clock helper preserves its three arguments and indexes the clock descriptor by r0.
@ pattern CLOCK_ENABLE recipe thumb clock_enable
@ anchor
    push {r3, r4, r5, r6, r7, lr}
    movs r5, r2
    movs r3, r2
    movs r6, r1
    movs r2, r1
    movs r4, r0
    movs r1, r0
    ldr r0, [pc, #0] @ mask=literal
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    lsls r0, r4, #2
    ldr r0, [r1, r0]
    lsls r0, r0, #0x10
    lsrs r4, r0, #0x1d
    .hword 0xf000 @ mask=0xf800 ; Thumb call prefix at the signature boundary
@ end

@ Reset shares the clock argument ABI and clears the pending flag at state+3.
@ pattern CLOCK_RESET recipe thumb clock_reset
@ anchor
    push {r0, r1, r2, r4, r5, r6, r7, lr}
    movs r5, r0
    movs r1, r0
    ldr r0, [pc, #0] @ mask=literal
    ldr r3, [sp, #8]
    ldr r2, [sp, #4]
    bl #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    ldrb r1, [r0, #3]
    cmp r1, #1
    bne #8
    movs r1, #0
    strb r1, [r0, #3]
    ldr r0, [pc, #0] @ mask=literal
    .hword 0xf000 @ mask=0xf800 ; Thumb call prefix at the signature boundary
@ end

@ Only the watchdog pool capacity immediate varies; it is not consumed by the SDK.
@ pattern WATCHDOG recipe thumb watchdog
    push {r0, r1, r2, r4, r5, r6, r7, lr}
    ldr r5, [pc, #0] @ mask=literal
    ldr r6, [pc, #0] @ mask=literal
@ anchor
    subs r5, #8
    ldrb r0, [r5]
    movs r4, r2
    cmp r0, #0
    bne #44
    subs r7, r0, #1
    lsls r1, r0, #4
    adds r2, r0, #1
    adds r3, r1, r6
    adds r0, r0, #1
    strb r2, [r3, #0xc]
    cmp r0, #0 @ mask=imm8
    str r7, [r6, r1]
@ end

@ DMA entry checks initialization and accesses channel state relative to the table's +128 base.
@ pattern DMA_START recipe thumb dma_start
    push {r0, r1, r2, r3, r4, r5, r6, r7, lr}
    ldr r7, [pc, #0] @ mask=literal
@ anchor
    movs r4, r0
    ldrb r0, [r7]
    ldr r6, [pc, #0] @ mask=literal
    movs r5, r1
    adds r6, #0x80
    cmp r0, #0
    sub sp, #0x24
    beq #34
    movs r3, r6
    movs r0, #0
    subs r3, #0x74
    movs r1, r0
    lsls r2, r0, #2
    str r1, [r6, r2]
@ end

@ Timer/message immediates vary. The task still receives/deallocates OS messages and restarts
@ 1000 ticks.
@ pattern LCD_TASK recipe thumb lcd_task
    push {r3, r4, r5, r6, r7, lr}
    movs r5, #0 @ mask=imm8
    adds r5, #0 @ mask=imm8
    ldr r4, [pc, #0] @ mask=literal
@ anchor
    movs r6, #1
    subs r7, r5, #1
    bl #0 @ mask=target
    cmp r0, #0xb1
    bne #12
    ldr r0, [pc, #0] @ mask=literal
    bl #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    bl #0 @ mask=target
    b #26
    cmp r0, r5
    bne #2
    strb r6, [r4]
    b #-32
    cmp r0, r7
    bne #28
    ldrb r0, [r4]
    cmp r0, #0
    beq #2
    movs r0, #0xc6
    b #0
    movs r0, #0xc7
    bl #0 @ mask=target
    movs r1, #0x7d
    movs r0, #0xff
    lsls r1, r1, #3
    adds r0, #0 @ mask=imm8
    bl #0 @ mask=target
    b #-66
    bl #0 @ mask=target
    b #-72
@ end

@ OS timer start preserves the timer ID and checks processor mode before table access.
@ pattern TIMER_START recipe thumb timer_start
@ anchor
    push {r0, r1, r4, r5, r6, r7, lr}
    movs r7, r0
    sub sp, #0x14
    blx #0 @ mask=target
    lsls r0, r0, #0x1b
    lsrs r0, r0, #0x1b
    cmp r0, #0x11
    bne #2
    bl #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    movs r2, #1
    lsls r2, r2, #0xb
    adds r2, r0, r2
    str r2, [sp, #0x10]
@ end

@ OS timer stop has the matching mode check and exposes the supported timer-count bound.
@ pattern TIMER_STOP recipe thumb timer_stop
@ anchor
    push {r1, r2, r3, r4, r5, r6, r7, lr}
    movs r6, r0
    movs r7, #0
    blx #0 @ mask=target
    lsls r0, r0, #0x1b
    lsrs r0, r0, #0x1b
    cmp r0, #0x11
    bne #2
    bl #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    movs r2, #1
    lsls r2, r2, #0xb
    adds r2, r0, r2
    str r2, [sp, #8]
    movs r2, #1
    movs r1, #0xff
    lsls r2, r2, #0xc
    adds r1, #0x28
@ end

@ Timer records have stride 28; the state byte is at record+18 after a 20-byte table prefix.
@ pattern TIMER_TABLE recipe thumb timer_table
    movs r0, #0x1c
    ldr r1, [pc, #0] @ mask=literal
@ anchor
    muls r0, r6, r0
    adds r1, #0x14
    adds r5, r0, r1
    ldrb r0, [r5, #0x12]
    cmp r0, #1
    beq #122
    cmp r0, #2
    beq #92
    cmp r0, #3
    bne #110
    ldrb r0, [r5, #0x11]
@ end

@ The stopped timer state is value 1 at record+18, matching TIMER_TABLE's state read.
@ pattern TIMER_STOP_STATE recipe thumb timer_stop_state
@ anchor
    movs r0, #1
    strb r0, [r5, #0x12]
    ldr r0, [pc, #0] @ mask=literal
    ldr r0, [r0]
@ end

@ Scratchpad index 1 selects a resource byte; paired exports must reference the same array.
@ pattern SCRATCHPAD recipe thumb scratchpad
@ anchor
    push {r4, lr}
    movs r4, r0
    cmp r0, #2
    blo #2
    blx #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    subs r0, #0x20
    adds r0, r0, r4
    subs r0, #0x20
    ldrb r0, [r0, #0x1f]
    blx #0 @ mask=target
    pop {r4, pc}
@ end

@ Internal queues use a ROM descriptor pointing to a RAM circular-list tail; zero means empty.
@ pattern CM_DISPATCH recipe thumb cm_dispatch
@ anchor
    push {r0, r1, r4, r5, r6, r7, lr}
    movs r5, r0
    ldr r0, [r0]
    sub sp, #0x44
    str r0, [sp, #0x30]
    blx #0 @ mask=target
@ end

@ Pop follows the circular-list tail, frees the removed node, and clears a single-node queue.
@ pattern CM_POP recipe thumb cm_pop
@ anchor
    push {r4, lr}
    ldr r1, [r0]
    movs r3, r0
    movs r4, #0
    cmp r1, #0
    beq #20
    ldr r0, [r1, #4]
    ldr r2, [r0, #4]
    ldr r4, [r0]
    cmp r2, r0
    beq #2
    str r2, [r1, #4]
    b #2
    movs r1, #0
    str r1, [r3]
    bl #0 @ mask=target
    movs r0, r4
    pop {r4, pc}
@ end

@ Internal processing indexes task contexts by current task, then loads descriptor+16.
@ pattern CM_QUEUE recipe thumb cm_queue
@ anchor
    push {r4, r5, lr}
    sub sp, #0x24
    blx #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    lsls r0, r0, #2
    ldr r5, [r1, r0]
    movs r2, #0x1e
    ldr r1, [r5, #0x10]
    add r0, sp, #4
    blx #0 @ mask=target
@ end

@ The ComMgr dirty flag is bit0 of queue byte60.
@ pattern CM_SUBSCRIPTIONS recipe thumb cm_subscriptions
@ anchor
    ldr r1, [sp, #0x90]
    adds r1, #0x20
    ldrb r0, [r1, #0x1c]
    lsls r2, r0, #0x1f
    beq #218
    lsrs r0, r0, #1
    lsls r0, r0, #1
    strb r0, [r1, #0x1c]
@ end

@ Java task initialization exposes its task ID and passes the queue descriptor to ComMgr_Init.
@ pattern TASK_ENTRY recipe thumb task_entry
@ anchor
    push {r4, r5, r6, lr}
    movs r0, #0x10
    blx #0 @ mask=target
    ldr r4, [pc, #0] @ mask=literal
    str r0, [r4]
    ldr r0, [pc, #0] @ mask=literal
    blx #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    bl #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    movs r1, #0xa
    blx #0 @ mask=target
    bl #0 @ mask=target
    bl #0 @ mask=target
@ end

@ The Java foreground object stores state 3 at byte 20.
@ pattern FOREGROUND recipe thumb foreground
    ldr r0, [pc, #0] @ mask=literal
@ anchor
    ldr r1, [r0, #4]
    cmp r1, #0
    beq #10
    ldr r1, [r1, #0x14]
    movs r0, #1
    lsls r1, r1, #0x18
    lsrs r1, r1, #0x18
    cmp r1, #3
    beq #0
    movs r0, #0
    bx lr
@ end

@ Keyboard messages E104..E106 are decoded into the existing Java event queue.
@ pattern KEYBOARD recipe thumb keyboard
    push {r0, r1, r2, r3, r4, lr}
    ldr r2, [pc, #0] @ mask=literal
@ anchor
    adds r1, r1, r2
    beq #86
    cmp r1, #1
    beq #86
    cmp r1, #2
    bne #76
    movs r2, #3
    ldrb r1, [r0, #8]
    ldrb r3, [r0, #9]
    lsls r1, r1, #8
    orrs r1, r3
    ldrb r3, [r0, #0xa]
    lsls r1, r1, #0x10
    lsls r3, r3, #8
    orrs r1, r3
    ldrb r3, [r0, #0xb]
    orrs r1, r3
    str r1, [sp]
    ldrb r1, [r0, #0xc]
    ldrb r3, [r0, #0xd]
    lsls r1, r1, #8
    orrs r1, r3
    mov r3, sp
    strh r1, [r3, #4]
    ldrb r1, [r0, #0xe]
    ldrb r3, [r0, #0xf]
    lsls r1, r1, #8
    orrs r1, r3
    mov r3, sp
    strh r1, [r3, #6]
    ldrb r1, [r0, #0x10]
    ldrb r3, [r0, #0x11]
    lsls r1, r1, #8
    orrs r1, r3
    mov r3, sp
    strh r1, [r3, #8]
    ldrb r1, [r0, #0x12]
    ldrb r0, [r0, #0x13]
    lsls r1, r1, #8
    orrs r1, r0
    strh r1, [r3, #0xa]
    movs r1, #1
    movs r0, #0
    bl #0 @ mask=target
    pop {r0, r1, r2, r3, r4, pc}
    movs r2, #1
    b #-84
    movs r2, #2
    b #-88
@ end

@ Allocate a two-byte ISI body, set message 1C and send through the Java server object.
@ pattern JAVA_SERVER recipe thumb java_server
    push {r4, lr}
    ldr r4, [pc, #0] @ mask=literal
    movs r0, #2
    bl #0 @ mask=target
@ anchor
    movs r1, #0x1c
    strb r1, [r0, #1]
    movs r2, r0
    movs r1, #0x11
    movs r0, r4
    bl #0 @ mask=target
    pop {r4, pc}
@ end

@ Resource release indexes eight-byte records, increments availability at +4 and reads type at +5.
@ pattern RESOURCE_TABLE recipe thumb resource_table
    ldr r1, [pc, #0] @ mask=literal
@ anchor
    lsls r0, r5, #3
    adds r2, r0, r1
    ldrb r0, [r2, #4]
    adds r0, r0, #1
    lsls r0, r0, #0x18
    lsrs r0, r0, #0x18
    cmp r0, #1
    strb r0, [r2, #4]
    bls #118
    ldrb r0, [r2, #5]
    cmp r0, #1
@ end

@ OS resources use an eight-byte record and UINT32_MAX empty waiter-head sentinel.
@ pattern RESOURCE_HEAD recipe thumb resource_head
@ anchor
    movs r0, #0xff
    strb r0, [r2, #6]
    ldr r1, [r2]
    adds r0, r1, #1
    beq #78
    ldr r0, [r1, #4]
    movs r3, #0
    str r0, [r2]
    subs r5, r3, #1
    str r5, [r1, #4]
    ldr r2, [pc, #0] @ mask=literal
    movs r0, #2
    str r3, [r1]
    strb r0, [r1, #0xd]
@ end

@ Alternate stock reader derives bus timing from configuration; identical output and call ABI.
@ pattern LCD_READ_BYTE_TIMED recipe thumb lcd_read_byte_timed
    ldr r1, [pc, #0] @ mask=literal
@ anchor
    ldr r2, [r1, #8]
    lsls r2, r2, #0x1c
    bpl #-8
    ldr r3, [pc, #0] @ mask=literal
    ldr r2, [r3, #4]
    ldr r3, [r3]
    lsls r2, r2, #0x1a
    lsrs r2, r2, #2
    lsls r3, r3, #0x1c
    lsrs r3, r3, #8
    orrs r2, r3
    movs r3, #1
    lsls r3, r3, #0x12
    adds r2, r2, r3
    str r2, [r1, #4]
    ldr r2, [r1, #0x10]
    ldr r1, [r1, #0x10]
    strb r1, [r0]
    movs r0, #1
    bx lr
@ end

@ The configured-timing variant preserves LCD_READ_WORD's byte order and output pointer ABI.
@ pattern LCD_READ_WORD_TIMED recipe thumb lcd_read_word_timed
    ldr r1, [pc, #0] @ mask=literal
@ anchor
    push {r3, lr}
    ldr r2, [r1, #8]
    lsls r2, r2, #0x1c
    bpl #-8
    ldr r2, [pc, #0] @ mask=literal
    ldr r3, [r2, #4]
    ldr r2, [r2]
    lsls r3, r3, #0x1a
    lsrs r3, r3, #2
    lsls r2, r2, #0x1c
    lsrs r2, r2, #8
    orrs r3, r2
    movs r2, #1
    lsls r2, r2, #0x12
    adds r2, r3, r2
    str r2, [r1, #4]
    ldr r2, [r1, #0x10]
    mov r3, sp
    strb r2, [r3]
    ldr r2, [r1, #0x10]
    strb r2, [r3, #3]
    ldr r2, [r1, #0x10]
    strb r2, [r3, #2]
    ldr r2, [r1, #0x10]
    strb r2, [r3, #1]
    ldr r1, [r1, #0x10]
    strb r1, [r3]
    ldr r1, [sp]
    str r1, [r0]
    movs r0, #1
    pop {r3, pc}
@ end
