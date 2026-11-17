@ SPDX-License-Identifier: MIT
@ Native memory-discovery templates; call and literal operands are checked by the C resolver.

@ RAPU page-table walker: derives the descriptor address and physical mapping from CP15 helper results.
@ pattern rapu_walker bytes thumb rapu_walker
    push {r0, r1, r2, r4, r5, r6, r7, lr}
    sub sp, #8
    movs r5, #0
    mvns r5, r5
    mov r6, r0
    bl #15286
    mov r7, r0
    bl #15256
    lsls r0, r0, #0x1f
    beq #6
    movs r0, #0xd
    lsls r0, r0, #0x1c
    subs r4, r0, r7
    b #0
    movs r4, #0
    bl #15326
    str r0, [sp, #4]
    lsrs r0, r0, #2
    lsls r0, r0, #2
    adds r0, r0, #1
    bl #15326
    blx #16660
    lsrs r1, r6, #0x14
    adds r0, r7, r4
    lsls r1, r1, #2
    ldr r0, [r0, r1]
    ldr r1, [sp, #0xc]
    str r0, [r1]
    lsls r1, r0, #0x1e
    lsrs r1, r1, #0x1e
    cmp r1, #1
    beq #6
    cmp r1, #2
    bne #22
    movs r5, #0
    b #18
    lsrs r0, r0, #0xa
    lsls r1, r6, #0xc
    lsls r0, r0, #0xa
    lsrs r1, r1, #0x18
    adds r0, r0, r4
    lsls r1, r1, #2
    movs r5, #1
    ldr r1, [r0, r1]
    ldr r0, [sp, #0x10]
    str r1, [r0]
    ldr r0, [sp, #4]
    bl #15268
    add sp, #0x14
    mov r0, r5
    pop {r4, r5, r6, r7, pc}
@ end

@ CP15 instruction contracts for the RAPU walker helper calls.
@ pattern rapu_helpers words arm rapu_helpers
    mrc p15, #0, r0, c2, c0, #0
    mrc p15, #0, r0, c1, c0, #0
    mrc p15, #0, r0, c3, c0, #0
    mcr p15, #0, r0, c3, c0, #0
    mcr p15, #0, r1, c7, c10, #4
    mcr p15, #0, r0, c3, c0, #0
@ end

@ Tagged RAM-range list traversal; follows sibling/child links and counts matching tags.
@ pattern tag_lookup halfwords thumb tag_lookup
    push {r4, r5, r6, lr}
    mov r6, r1
    mov r5, r2
    mov r4, r0
    lsls r0, r4, #0x1e
    beq #2
    .hword 0, 0 @ call slot validated separately by the native resolver
    ldr r0, [r4]
    cmp r0, r6
    bne #6
    subs r5, r5, #1
    bhs #2
    mov r0, r4
    pop {r4, r5, r6, pc}
    cmp r0, #2
    beq #12
    cmp r0, #3
    bne #2
    ldr r4, [r4, #8]
    b #-36
    ldr r0, [r4, #4]
    adds r4, r0, r4
    b #-42
    movs r0, #0
    pop {r4, r5, r6, pc}
@ end
