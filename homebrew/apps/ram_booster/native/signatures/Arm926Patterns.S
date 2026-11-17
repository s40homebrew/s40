@ SPDX-License-Identifier: MIT
@ Native memory-discovery templates; call and literal operands are checked by the C resolver.

@ ARM926 page-table walker: preserves descriptor indexing and translation checks; helper calls are decoded separately.
@ pattern walker bytes thumb walker
    push {r0, r1, r2, r4, r5, r6, r7, lr}
    movs r5, #0
    movs r6, r0
    mvns r5, r5
    sub sp, #8
    bl #12654
    movs r7, r0
    bl #12624
    lsls r0, r0, #0x1f
    beq #6
    movs r0, #0xd
    lsls r0, r0, #0x1c
    subs r4, r0, r7
    b #0
    movs r4, #0
    bl #12654
    str r0, [sp, #4]
    lsrs r0, r0, #2
    lsls r0, r0, #2
    adds r0, r0, #1
    bl #12654
    blx #13164
    lsrs r1, r6, #0x14
    adds r0, r7, r4
    lsls r1, r1, #2
    ldr r0, [r0, r1]
    ldr r1, [sp, #0xc]
    str r0, [r1]
    lsls r1, r0, #0x1e
    lsrs r1, r1, #0x1e
    cmp r1, #1
    beq #22
    cmp r1, #2
    beq #14
    cmp r1, #3
    bne #34
    lsrs r0, r0, #0xc
    lsls r0, r0, #0xc
    adds r0, r0, r4
    lsls r1, r6, #0xc
    lsrs r1, r1, #0x16
    b #12
    movs r5, #0
    b #18
    lsrs r0, r0, #0xa
    lsls r0, r0, #0xa
    adds r0, r0, r4
    lsls r1, r6, #0xc
    lsrs r1, r1, #0x18
    lsls r1, r1, #2
    ldr r1, [r0, r1]
    ldr r0, [sp, #0x10]
    movs r5, #1
    str r1, [r0]
    ldr r0, [sp, #4]
    bl #12580
    movs r0, r5
    add sp, #0x14
    pop {r4, r5, r6, r7, pc}
@ end

@ Leading CP15 instructions required at the independently resolved MMU helper call targets.
@ pattern helper_insn words arm helper_insn
    mrc p15, #0, r0, c2, c0, #0
    mrc p15, #0, r0, c1, c0, #0
    mrc p15, #0, r0, c3, c0, #0
    mcr p15, #0, r0, c3, c0, #0
    mcr p15, #0, r1, c7, c10, #4
    mcr p15, #0, r0, c3, c0, #0
@ end

@ Legacy page-table walker with separate large/small-page address formation.
@ pattern legacy_walker bytes thumb legacy_walker
    push {r0, r1, r2, r4, r5, r6, r7, lr}
    adds r6, r0, #0
    adds r7, r2, #0
    movs r5, #0
    mvns r5, r5
    sub sp, #8
    bl #10596
    str r0, [sp, #4]
    bl #10566
    lsls r0, r0, #0x1f
    beq #10
    ldr r1, [sp, #4]
    movs r0, #3
    lsls r0, r0, #0x1c
    adds r0, r1, r0
    rsbs r4, r0, #0
    b #0
    movs r4, #0
    bl #10592
    str r0, [sp]
    lsrs r0, r0, #2
    lsls r0, r0, #2
    adds r0, #1
    bl #10592
    ldr r0, [sp, #4]
    lsrs r1, r6, #0x14
    lsls r1, r1, #2
    adds r0, r0, r4
    ldr r0, [r0, r1]
    ldr r1, [sp, #0xc]
    str r0, [r1]
    lsls r1, r0, #0x1e
    lsrs r1, r1, #0x1e
    cmp r1, #1
    beq #22
    cmp r1, #2
    beq #14
    cmp r1, #3
    bne #32
    lsrs r0, r0, #0xc
    lsls r0, r0, #0xc
    adds r0, r0, r4
    lsls r1, r6, #0xc
    lsrs r1, r1, #0x16
    b #12
    movs r5, #0
    b #16
    lsrs r0, r0, #0xa
    lsls r0, r0, #0xa
    adds r0, r0, r4
    lsls r1, r6, #0xc
    lsrs r1, r1, #0x18
    lsls r1, r1, #2
    ldr r0, [r0, r1]
    movs r5, #1
    str r0, [r7]
    ldr r0, [sp]
    bl #10522
    adds r0, r5, #0
    add sp, #0x14
    pop {r4, r5, r6, r7, pc}
@ end

@ Tagged RAM-range list traversal; the native resolver validates the recursive call slot.
@ pattern legacy_tag_lookup halfwords thumb legacy_tag_lookup
    push {r4, r5, r6, lr}
    adds r6, r1, #0
    adds r5, r2, #0
    adds r4, r0, #0
    lsls r0, r4, #0x1e
    beq #2
    .hword 0, 0 @ call slot validated separately by the native resolver
    ldr r0, [r4]
    cmp r0, r6
    bne #6
    subs r5, #1
    bhs #2
    adds r0, r4, #0
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
