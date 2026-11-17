@ SPDX-License-Identifier: MIT
@ Assembly templates for HeapGrowthResolver.
@ The specialized resolver validates variable operands and patch-state alternatives.
@ Exact arrays preserve the decoder markers and any patch replacement bytes.

@ Heap-page provider: allocates or extends the arena, rounds requests and calls the shared page mapper.
@ pattern PROVIDER words arm PROVIDER
    push {r4, r5, r6, r7, r8, sb, sl, lr}
    mov r5, r1
    mov r8, r2
    mov sl, r0
    ldr r4, [r1]
    ldr sb, [pc, #0x2b4]
    ldr r6, [pc, #0x2ac]
    cmp r4, #0
    mov r7, #0
    bne #224
    mov r2, #0
    mov r1, #0x400000
    mov r0, r2
    bl #-908
    movs r4, r0
    beq #360
    str r4, [r5]
    ldr r0, [r6]
    add sl, sl, #0x10
    cmp r0, #0
    bne #4
    blx #-12349974
    str r0, [r6]
    cmp r0, sl
    blo #24
    cmp r0, #0
    bne #4
    blx #-12349998
    str r0, [r6]
    mov sl, r0
    mov r5, #1
    b #52
    cmp r0, #0
    bne #4
    blx #-12350026
    str r0, [r6]
    mov r1, r0
    mov r0, sl
    bl #-16114016
    add r5, r0, #1
    ldr r0, [r6]
    cmp r0, #0
    bne #4
    blx #-12350062
    str r0, [r6]
    mul sl, r0, r5
    ldr r0, [r6, #4]
    cmp r0, #0
    bne #16
    blx #-12350086
    mov r1, r0
    mov r0, #0x400000
    bl #-7700768
    str r0, [r6, #4]
    cmp r0, r5
    blo #200
    ldr r2, [sb]
    mov r1, sl
    mov r0, r4
    bl #-1092
    cmp r0, #0
    subne r0, sl, #0x10
    strne r5, [r4, #0xc]
    addne r7, r4, #0x10
    strne r0, [r8]
    mov r0, r7
    pop {r4, r5, r6, r7, r8, sb, sl, pc}
    ldr r0, [r6]
    cmp r0, #0
    bne #4
    blx #-12350170
    str r0, [r6]
    cmp r0, sl
    blo #24
    cmp r0, #0
    bne #4
    blx #-12350194
    str r0, [r6]
    mov sl, r0
    mov r5, #1
    b #52
    cmp r0, #0
    bne #4
    blx #-12350222
    str r0, [r6]
    mov r1, r0
    mov r0, sl
    bl #-16114212
    add r5, r0, #1
    ldr r0, [r6]
    cmp r0, #0
    bne #4
    blx #-12350258
    str r0, [r6]
    mul sl, r0, r5
    ldr r0, [r6, #4]
    cmp r0, #0
    bne #16
    blx #-12350282
    mov r1, r0
    mov r0, #0x400000
    bl #-7700964
    str r0, [r6, #4]
    ldr r1, [r4, #0xc]
    add r1, r1, r5
    cmp r0, r1
    bhs #4
    mov r0, #0
    pop {r4, r5, r6, r7, r8, sb, sl, pc}
    ldr r0, [r6]
    cmp r0, #0
    bne #4
    blx #-12350338
    str r0, [r6]
    ldr r1, [r4, #0xc]
    ldr r2, [sb]
    mla r0, r0, r1, r4
    mov r1, sl
    bl #-1328
    cmp r0, #0
    moveq sl, #0
    beq #12
    ldr r1, [r4, #0xc]
    mov r7, r0
    add r1, r1, r5
    str r1, [r4, #0xc]
    str sl, [r8]
    b #-256
@ end

@ Runtime free/total-memory getters whose quota arithmetic must agree with the provider patch state.
@ pattern RUNTIME halfwords thumb RUNTIME
    ldr r0, [pc, #0x364]
    push {r4, lr}
    movs r2, #1
    ldr r3, [r0]
    lsls r2, r2, #0x16
    ldr r0, [r3, #0x5c]
    ldr r1, [r3]
    cmp r0, r2
    bge #0
    mov r2, r0
    ldr r0, [pc, #0x354]
    ldr r4, [pc, #0x358]
    lsls r1, r1, #2
    ldr r3, [r3, #0x78]
    ldr r0, [r0, r1]
    ldr r4, [r4, r1]
    adds r0, r0, r4
    lsls r0, r0, #2
    cmp r3, #1
    bne #6
    ldr r3, [pc, #0x348]
    ldr r1, [r3, r1]
    lsls r1, r1, #2
    adds r0, r1, r0
    subs r0, r2, r0
    cmp r0, #0
    bgt #0
    movs r0, #0
    ldr r3, [pc, #0x320]
    movs r2, #0
    ldr r1, [r3]
    subs r4, r1, #4
    str r0, [r4]
    stm r1!, {r2}
    str r1, [r3]
    pop {r4, pc}
    ldr r1, [pc, #0x31c]
    push {r4, lr}
    movs r0, #1
    ldr r1, [r1]
    lsls r0, r0, #0x16
    ldr r1, [r1, #0x5c]
    cmp r1, r0
    bge #0
    mov r0, r1
    ldr r3, [pc, #0x300]
    movs r2, #0
    ldr r1, [r3]
    subs r4, r1, #4
    str r0, [r4]
    stm r1!, {r2}
    str r1, [r3]
    pop {r4, pc}
@ end

@ Page-release helper: rounds the released extent and chooses whole-region or partial unmapping.
@ pattern RELEASE words arm RELEASE
    push {r2, r3, r4, r5, r6, r7, r8, sb, sl, lr}
    mov r5, r0
    mov r7, #0
    mov r6, r1
    ldr sl, [pc, #0x6a8]
    mov sb, r2
    mov r8, r3
    mov r4, r7
    ldr r0, [sl]
    cmp r0, #0
    bne #4
    blx #-12348922
    str r0, [sl]
    mov r1, r0
    sub r0, r0, #1
    bic r0, r5, r0
    cmp r0, r5
    addne r0, r0, r1
    subne r4, r0, r5
    subne r6, r6, r4
    cmp r8, #0
    bne #4
    cmp r6, r1
    blt #52
    cmp r8, #0
    mov r7, r0
    beq #12
    mov r1, #0x400000
    blx #-6970022
    mov r4, #0
    b #24
    mvn r3, #0
    str r3, [sp]
    str r3, [sp, #4]
    mov r3, #0xc
    mov r2, #8
    mov r1, r6
    blx #-6970556
    mov r0, r7
    str r4, [sb]
    pop {r2, r3, r4, r5, r6, r7, r8, sb, sl, pc}
@ end

@ Heap shrink path: computes unused pages, updates the arena limit and releases the trailing extent.
@ pattern SHRINK words arm SHRINK
    push {r3, r4, r5, lr}
    bl #-244
    ldr r5, [pc, #-0x504]
    mov r4, r0
    ldr r0, [r5, #0x10]
    ldr r1, [r5, #0xc]
    sub r1, r0, r1
    cmp r4, r1
    bge #144
    mov r1, #0
    mvn r0, #0x80000000
    blx #-3619680
    ldr r1, [r5, #0x18]
    cmp r1, #0
    beq #120
    ldr r0, [r5, #0xc]
    sub r2, r1, r0
    cmp r4, r2
    addge r0, r0, r4
    bge #16
    ldr r0, [r5, #0x10]
    sub r2, r0, r1
    cmp r2, #0x10
    addhi r0, r2, r2, lsr #31
    addhi r0, r1, r0, asr #1
    ldr r3, [r5, #0x10]
    sub r2, r3, r0
    cmp r2, #0
    ble #64
    cmp r0, r1
    blo #56
    cmp r0, r3
    bhs #48
    ldr r1, [pc, #-0x580]
    add r3, sp, #0
    bl #4348
    cmp r0, #0
    beq #28
    mov r1, r0
    str r0, [r5, #0x10]
    ldr r0, [r5, #0x18]
    ldr r2, [pc, #-0x558]
    sub r1, r1, r0
    asr r1, r1, #2
    bl #-2236
    bl #-1744
    pop {r3, r4, r5, pc}
@ end

@ Application heap-size calculation, including its inline jump table and minimum/maximum bounds.
@ pattern SIZE words arm SIZE
    push {r4, r5, r6, r7}
    mov r4, #0
    ldr r5, [pc, #0x198]
    ldr r6, [pc, #-0x1c]
    mov r2, r4
    mov ip, r4
    mov r3, r4
    mov r0, #1
    ldr r1, [r5, r0, lsl #2]
    cmp r1, #0
    beq #28
    ldr r7, [r1, #0x58]
    cmp r7, #5
    ldrlt r1, [r1, #0x5c]
    addge r3, r3, #1
    addlt ip, ip, #1
    addlt r2, r2, r1
    ldr r1, [r6, r0, lsl #2]
    add r4, r4, r1
    add r0, r0, #1
    cmp r0, #0xa
    blt #-60
    ldr r1, [pc, #-0x6c]
    ldr r0, [r6]
    cmp ip, #6
    ldr r1, [r1]
    add r0, r0, r1
    add r0, r0, r4
    lsl r1, r0, #2
    ldrlo pc, [pc, ip, lsl #2]
    b #44
    .word 0x94 @ inline jump-table offset
    .word 0x94 @ inline jump-table offset
    .word 0x94 @ inline jump-table offset
    .word 0x9c @ inline jump-table offset
    .word 0x9c @ inline jump-table offset
    .word 0xa4 @ inline jump-table offset
    mov r0, #0x80000
    b #16
    mov r0, #0xc0000
    b #8
    mov r0, #0x100000
    b #0
    mov r0, #0x180000
    add r1, r1, r2
    cmp r1, r0
    movlt r0, r1
    cmp r3, #0
    addne r0, r0, #0x200000
    cmp r0, #0x400000
    ble #8
    pop {r4, r5, r6, r7}
    mov r0, #0x400000
    bx lr
    cmp r0, #0x8000
    pop {r4, r5, r6, r7}
    movlt r0, #0x8000
    bx lr
@ end

@ VMM request dispatcher: selects the reserve/map operation from the request flags.
@ pattern VMM halfwords thumb VMM
    push {r0, r1, r2, r3, r4, r5, r6, r7, lr}
    movs r0, #0xf7
    ands r2, r0
    movs r0, #0xf1
    sub sp, #0xc
    lsls r0, r0, #8
    ands r3, r0
    movs r0, #0xff
    ldr r7, [sp, #0x2c]
    movs r5, #0
    adds r0, #0x1b
    mov r6, r2
    mov r4, r3
    bl #-3490290
    movs r0, #3
    movs r1, #1
    lsls r0, r0, #0xc
    mov r3, r4
    ands r3, r0
    lsls r1, r1, #0xc
    subs r0, r3, r1
    beq #22
    subs r0, r0, r1
    beq #52
    subs r0, r0, r1
    bne #36
    ldr r1, [sp, #0x10]
    ldr r0, [sp, #0xc]
    mov r3, r4
    mov r2, r6
    str r7, [sp]
    bl #-484
    b #18
    ldr r1, [sp, #0x34]
    ldr r0, [sp, #0x30]
    mov r3, r4
    mov r4, sp
    mov r2, r6
    stm r4!, {r0, r1, r7}
    ldr r1, [sp, #0x10]
    ldr r0, [sp, #0xc]
    bl #-306
    mov r5, r0
    movs r0, #0xff
    adds r0, #0x1b
    bl #-3469664
    mov r0, r5
    b #-628
    ldr r1, [sp, #0x34]
    ldr r0, [sp, #0x30]
    mov r3, r4
    mov r4, sp
    mov r2, r6
    stm r4!, {r0, r1, r7}
    ldr r1, [sp, #0x10]
    ldr r0, [sp, #0xc]
    bl #548
    b #-38
@ end

@ VMM reservation path: validates the requested extent and either finds or creates its mapping.
@ pattern VMM_RESERVE halfwords thumb VMM_RESERVE
    push {r0, r1, r2, r3, r4, r5, r6, r7, lr}
    sub sp, #0x24
    lsls r0, r3, #0x11
    mov r4, r2
    lsrs r6, r0, #0x1f
    ldr r7, [pc, #0x2dc]
    lsls r0, r3, #0x17
    mov r5, r3
    bpl #34
    movs r0, #0xf7
    ands r0, r4
    cmp r0, #0x30
    bne #26
    ldr r0, [sp, #0x24]
    cmp r0, #0
    bne #20
    ldr r0, [sp, #0x28]
    bl #954
    movs r1, #0
    mov r3, r0
    movs r2, #1
    mov r0, r1
    blx #1312
    str r0, [sp, #0x24]
    b #86
    bl #-1988
    cmp r4, #0
    str r0, [sp, #0x20]
    bne #18
    cmp r6, #0
    beq #14
    ldr r1, [sp, #0x24]
    cmp r1, #0
    beq #8
    ldr r2, [sp, #0x28]
    bl #-306
    cmp r0, #0
    bne #38
    str r6, [sp]
    ldr r2, [sp, #0x28]
    ldr r0, [sp, #0x20]
    movs r3, #0
    add r1, sp, #0x24
    bl #-406
    movs r6, r0
    beq #20
    cmp r4, #0
    beq #22
    mov r1, r4
    orrs r1, r5
    mov r2, r7
    mov r0, r6
    mov r3, r7
    bl #-544
    cmp r0, #0
    bne #4
    movs r0, #0
    add sp, #0x34
    pop {r4, r5, r6, r7, pc}
    ldr r0, [r6, #8]
    lsls r0, r0, #0x17
    bmi #4
    mov r0, r6
    blx #1228
@ end

@ VMM mapping wrapper: accounts for the returned mapped extent in the caller descriptor.
@ pattern VMM_DISPATCH halfwords thumb VMM_DISPATCH
    push {r0, r1, r2, r3, r4, r5, r6, r7, lr}
    sub sp, #4
    mov r4, r1
    mov r5, r3
    mov r7, r2
    ldr r0, [r1]
    ldr r6, [sp, #0x28]
    cmp r0, #0
    beq #28
    adds r0, r0, r5
    mov r1, r7
    bl #-240
    ldr r1, [r4]
    mov r2, r0
    ldr r0, [sp, #4]
    lsrs r1, r1, #0xe
    lsls r1, r1, #0xe
    blx #1596
    cmp r0, #0
    bne #24
    cmp r6, #0
    bne #18
    mov r1, r7
    mov r0, r5
    bl #-270
    mov r1, r0
    ldr r0, [sp, #4]
    blx #1548
    cmp r0, #0
    bne #4
    movs r0, #0
    add sp, #0x14
    pop {r4, r5, r6, r7, pc}
    ldr r1, [r0]
    adds r1, r1, r5
    str r1, [r4]
    b #-14
@ end

@ Fixed-address mapping entry, including initial range and overlap checks.
@ pattern VMM_FIXED_HEAD halfwords thumb VMM_FIXED_HEAD
    push {r3, r4, r5, r6, r7, lr}
    mov r5, r2
    mov r6, r1
    bl #-40
    movs r4, r0
    bne #12
    ldr r0, [pc, #0x48]
    mov r2, r6
    adr r1, #204
    adds r0, #0x61
    bl #32316
    b #348
    ldr r2, [r4, #4]
    ldr r0, [r4]
    adds r1, r6, r5
    adds r7, r0, r2
    cmp r7, r6
    bhi #226
    ldr r0, [r4, #0xc]
    ldr r0, [r0]
    cmp r1, r0
    bhi #216
    mov r2, r5
    mov r1, r6
    mov r0, r4
    bl #-982
    pop {r3, r4, r5, r6, r7, pc}
@ end

@ Fixed-address mapping tail: splits or merges area records and reports invalid overlap.
@ pattern VMM_FIXED_TAIL halfwords thumb VMM_FIXED_TAIL
    b #96
    ldr r3, [r4, #0x18]
    cmp r3, #5
    bne #90
    cmp r2, r5
    bne #2
    cmp r0, r6
    beq #28
    ldr r2, [r4, #0xc]
    ldr r2, [r2]
    subs r3, r2, r0
    cmp r3, r5
    mov ip, r3
    bls #62
    cmp r0, r6
    bne #16
    cmp r7, r1
    bhs #2
    str r5, [r4, #4]
    b #4
    mov r0, r4
    bl #-790
    mov r0, r4
    b #-260
    subs r3, r7, r1
    bne #8
    mov r1, r6
    mov r0, r4
    bl #-806
    b #-274
    cmp r7, r1
    bls #6
    mov r0, r4
    bl #-818
    b #-24
    cmp r2, r1
    blo #10
    mov r1, r6
    mov r0, r4
    bl #-832
    str r5, [r0, #4]
    b #-302
    cmp ip, r5
    bne #2
    cmp r0, r6
    beq #-66
    ldr r0, [pc, #0x194]
    mov r3, r5
    mov r2, r6
    adr r1, #352
    bl #31964
    movs r0, #0
    b #-326
@ end

@ Area lookup: bounds-checks the area index before reading its descriptor.
@ pattern VMM_AREA halfwords thumb VMM_AREA
    cmp r0, #0x68
    bhs #12
    ldr r1, [pc, #0x3fc]
    lsls r0, r0, #2
    ldr r0, [r1, r0]
    cmp r0, #0
    beq #2
    ldr r0, [r0, #0xc]
    bx lr
    ldr r0, [pc, #0x3f0]
    subs r0, #0xd0
    ldr r0, [r0, #0x14]
    bx lr
@ end

@ VMM lock entry: resolves the current task and validates its descriptor extent.
@ pattern VMM_LOCK halfwords thumb VMM_LOCK
    push {r1, r2, r3, r4, r5, r6, r7, lr}
    mov r4, r0
    bl #1867636
    mov r5, r0
    bl #-3969006
    cmp r0, #0
    beq #252
    ldr r1, [pc, #0x254]
    ldr r0, [r1, #0xc]
    cmp r4, r0
    blo #74
@ end

@ VMM unlock entry: resolves the current task and validates its descriptor extent.
@ pattern VMM_UNLOCK halfwords thumb VMM_UNLOCK
    push {r4, r5, r6, r7, lr}
    sub sp, #0x34
    mov r4, r0
    bl #1846938
    mov r5, r0
    movs r0, #0
    str r0, [sp, #0x28]
    bl #-3989708
    cmp r0, #0
    beq #252
    ldr r6, [pc, #0x318]
    ldr r0, [r6, #0xc]
    cmp r4, r0
    blo #74
@ end
