@ SPDX-License-Identifier: MIT
@ Assembly templates for RapuTimeResolver.
@ The specialized resolver validates variable operands and patch-state alternatives.
@ Exact arrays preserve the decoder markers and any patch replacement bytes.

@ RAPU native dispatch through a literal-loaded entry point; preserves the VM register save/restore sequence.
@ pattern DISPATCH words arm DISPATCH
    ldrb r2, [lr, #1]
    ldrb r3, [lr, #2]
    mov sl, lr
    add lr, sl, #3
    orr r1, r3, r2, lsl #8
    ldr r1, [r8, r1, lsl #2]
    ldrh r4, [r1, #0x1e]
    ldr r0, [r1, #0x14]
    sub r4, r6, r4, lsl #2
    ldr r2, [r4]
    cmp r2, #0
    beq #0
    ldr r2, [r1, #4]
    sub r3, r4, #4
    cmp r2, #0
    beq #0
    ldr sb, [r5, #0x438]
    tst r0, #8
    addeq r3, r3, #4
    cmp sb, #0
    beq #0
    str r3, [sb, #0x20]
    str r4, [sb, #0x24]
    str sb, [r5, #0x468]
    str r1, [r5, #0x448]
    ldr ip, [pc]
    add sl, r5, #0x420
    stm sl, {r6, r7, r8, fp, lr}
    blx ip
    ldm sl, {r6, r7, r8, fp, lr}
@ end

@ Thumb native-call wrapper with application-state restoration after the indirect call.
@ pattern INVOKE halfwords thumb INVOKE
    push {r4, r5, r6, lr}
    ldr r1, [pc, #0]
    ldr r5, [pc, #0]
    ldr r0, [r1]
    ldr r4, [r5]
    cmp r0, #0
    bne #2
    movs r0, #0x10
    str r0, [r1]
    subs r0, r0, #1
    str r0, [r1]
    ldr r1, [pc, #0]
    lsls r0, r0, #2
    str r2, [r1, r0]
    blx r2
    ldr r0, [r5]
    cmp r4, r0
    beq #14
@ end

@ Cache synchronization wrapper: calls both range helpers and flushes the instruction prefetch buffer.
@ pattern CACHE words arm CACHE
    push {r4, r5, r6, lr}
    ldr ip, [pc]
    mov r4, r0
    mov r5, r1
    blx ip
    ldr ip, [pc]
    mov r0, r4
    mov r1, r5
    blx ip
    mov ip, #0
    mcr p15, #0, ip, c7, c5, #4
    pop {r4, r5, r6, pc}
@ end

@ Data-cache range clean helper with interrupt-state preservation and write-buffer barriers.
@ pattern CLEAN words arm CLEAN
    push {r0, lr}
    mov r2, r0
    add r3, r1, r0
    sub r3, r3, #1
    mov lr, #0
    mcr p15, #0, lr, c7, c10, #4
    mcrr p15, #0, r3, r2, c14
    mcr p15, #0, lr, c7, c10, #4
    mrs r2, apsr
    cpsid if
    str r2, [sp, #-4]!
    bl #0
    pop {r2}
    msr cpsr_fsxc, r2
    pop {r0, pc}
@ end

@ Instruction-cache range invalidation helper, including its barriers and checked trailing call.
@ pattern INVALIDATE words arm INVALIDATE
    push {r0, lr}
    mov lr, #0
    mov r2, r0
    add r3, r1, r0
    sub r3, r3, #1
    mcrr p15, #0, r3, r2, c5
    mcr p15, #0, lr, c7, c5, #4
    mcrr p15, #0, r3, r2, c5
    mcr p15, #0, lr, c7, c5, #4
    mcr p15, #0, r2, c7, c5, #7
    mcr p15, #0, lr, c7, c10, #4
    mcr p15, #0, lr, c7, c5, #4
    bl #0
    pop {r0, pc}
@ end
