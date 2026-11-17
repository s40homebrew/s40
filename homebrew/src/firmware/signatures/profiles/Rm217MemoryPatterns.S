@ SPDX-License-Identifier: MIT
@ Instruction signatures for Rm217Memory.
@ Calls and literal operands are decoded and validated by the Java resolver.

@ Force recipe allocation before entering a forged Java memory view.
@ Boot range walker: accepts only intervals contained in a zero-terminated ROM range table.
@ pattern RANGE recipe thumb RANGE
@ anchor
    push {r4, r5, r6, lr}
    adds r4, r0, #0
    adds r5, r1, #0
    bl #0 @ mask=target
    lsls r0, r0, #0x1f
    beq #24
    ldr r0, [pc, #0] @ mask=literal
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

@ Nonblocking allocator entry; task-context and heap relationships are checked by the resolver.
@ pattern OS_BLOCK_ALLOC_NO_WAIT recipe thumb os_block_alloc_no_wait
@ anchor
    push {r4, r5, r6, r7, lr}
    adds r6, r0, #0
    movs r0, #0
    sub sp, #0x34
    ldr r5, [pc, #0] @ mask=literal
    str r0, [sp, #0x1c]
    str r0, [sp, #0x18]
    ldr r0, [r5]
    lsls r0, r0, #0x1c
    bpl #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    add r1, sp, #0xc
    ldrh r2, [r0, #4]
    movs r0, #2
    blx #0 @ mask=target
    blx #0 @ mask=target
    lsls r0, r0, #0x1b
    lsrs r0, r0, #0x1b
    cmp r0, #0x11
    bne #0 @ mask=target
    bl #0 @ mask=target
    bl #0 @ mask=target
    bl #0 @ mask=target
    ldr r2, [pc, #0] @ mask=literal
    movs r1, #1
    lsls r1, r1, #0xb
    adds r1, r2, r1
    str r1, [sp, #0x30]
    movs r1, #1
    lsls r1, r1, #0xc
    adds r4, r2, r1
    cmp r0, #1
    bne #0 @ mask=target
    blx #0 @ mask=target
    bl #0 @ mask=target
    strh r0, [r4]
    movs r0, #0x3d
    strb r0, [r4]
    movs r0, #0x88
    strb r0, [r4]
    str r6, [r4]
@ end

@ Deallocation entry with null/range guards before returning the block to its heap.
@ pattern OS_BLOCK_DEALLOC recipe thumb os_block_dealloc
@ anchor
    push {r4, r5, r6, r7, lr}
    adds r5, r0, #0
    sub sp, #0x34
    beq #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    ldr r0, [r0]
    lsls r0, r0, #0x1c
    bpl #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    add r1, sp, #0x10
    ldrh r2, [r0, #6]
    movs r0, #2
    blx #0 @ mask=target
    blx #0 @ mask=target
    lsls r0, r0, #0x1b
    lsrs r0, r0, #0x1b
    cmp r0, #0x11
    bne #0 @ mask=target
    bl #0 @ mask=target
    ldr r2, [pc, #0] @ mask=literal
    movs r1, #1
    ldr r7, [pc, #0] @ mask=literal
    lsls r1, r1, #0xb
    adds r6, r2, r1
    ldr r0, [r7, #8]
    lsls r1, r1, #1
    adds r4, r2, r1
    cmp r0, #0
    beq #0 @ mask=target
    ldr r0, [r7, #4]
    cmp r0, r5
    blo #0 @ mask=target
    ldr r0, [r7]
    cmp r0, r5
    bhi #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    ldr r0, [r0]
    lsls r0, r0, #0x17
    bpl #0 @ mask=target
    blx #0 @ mask=target
    bl #0 @ mask=target
    strh r0, [r4]
    movs r0, #0x3f
@ end

@ Java file-read bridge; its independently decoded allocation/free calls corroborate the heap services.
@ pattern FILE_READ_NATIVE recipe thumb file_read_native
    push {r4, r5, r6, r7, lr}
    ldr r3, [pc, #0] @ mask=literal
@ anchor
    sub sp, #0x24
    ldr r1, [r3]
    ldr r0, [pc, #0] @ mask=literal
    movs r2, #0
    adds r5, r1, #0
    adds r7, r1, #1
    lsls r1, r1, #2
    add r6, sp, #0x14
    str r2, [sp, #0x14]
    str r6, [r0, r1]
    add r1, sp, #0x14
    str r1, [sp, #0x10]
    adds r1, r7, #1
    lsls r7, r7, #2
    add r6, sp, #0xc
    str r2, [sp, #0xc]
    str r6, [r0, r7]
    str r2, [sp, #8]
    adds r2, r1, #1
    movs r4, #0
    lsls r1, r1, #2
    add r7, sp, #8
    str r2, [r3]
    str r7, [r0, r1]
    add r0, sp, #8
    str r0, [sp, #4]
    add r0, sp, #0x14
    bl #0 @ mask=target
    adds r1, r6, #0
    add r0, sp, #0x14
    bl #0 @ mask=target
    adds r1, r7, #0
    movs r0, #1
    bl #0 @ mask=target
    movs r0, #2
    bl #0 @ mask=target
    adds r7, r0, #0
    movs r0, #3
    bl #0 @ mask=target
    str r0, [sp, #0x20]
    movs r0, #4
    bl #0 @ mask=target
    str r0, [sp, #0x1c]
    ldr r1, [pc, #0] @ mask=literal
    adds r0, r6, #0
    adr r2, #0 @ mask=literal
    bl #0 @ mask=target
    str r0, [sp, #0x18]
    ldr r1, [pc, #0] @ mask=literal
    adds r0, r6, #0
    adr r2, #0 @ mask=literal
    bl #0 @ mask=target
    adds r1, r0, #0
    add r0, sp, #0x14
    bl #0 @ mask=target
    adds r6, r0, #0
    movs r2, #0
    adds r1, r7, #0
    bl #0 @ mask=target
    adds r3, r0, #1
    bne #0 @ mask=target
    bl #0 @ mask=target
    cmp r0, #9
    bne #0 @ mask=target
    ldr r0, [sp, #0x10]
    ldr r1, [sp, #0x18]
    movs r2, #1
    bl #0 @ mask=target
    b #0 @ mask=target
    ldr r0, [sp, #0x1c]
    bl #0 @ mask=target
    adds r7, r0, #0
    bne #0 @ mask=target
    ldr r0, [pc, #0] @ mask=literal
    movs r1, #0
    bl #0 @ mask=target
    b #0 @ mask=target
    adds r1, r7, #0
    ldr r2, [sp, #0x1c]
    adds r0, r6, #0
    bl #0 @ mask=target
    adds r4, r0, #0
    adds r3, r0, #1
    beq #0 @ mask=target
    adds r3, r7, #0
    ldr r0, [sp, #4]
    ldr r1, [sp, #0x20]
    adds r2, r4, #0
    bl #0 @ mask=target
    b #0 @ mask=target
    bl #0 @ mask=target
    cmp r0, #9
    bne #0 @ mask=target
    ldr r0, [sp, #0x10]
    ldr r1, [sp, #0x18]
    movs r2, #1
    bl #0 @ mask=target
    ldr r1, [pc, #0] @ mask=literal
    b #0 @ mask=target
    movs r1, #0
    ldr r0, [pc, #0] @ mask=literal
    bl #0 @ mask=target
    adds r0, r7, #0
    bl #0 @ mask=target
    ldr r3, [pc, #0] @ mask=literal
    ldr r0, [pc, #0] @ mask=literal
    str r5, [r3]
    ldr r0, [r0]
    ldr r1, [pc, #0] @ mask=literal
    ldr r0, [r0, #0x24]
    str r0, [r1]
    str r4, [r0]
    ldr r0, [r1]
    adds r0, #4
    str r0, [r1]
    b #0 @ mask=target
@ end
