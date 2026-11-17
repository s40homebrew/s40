@ SPDX-License-Identifier: MIT
@ Assembly templates for HeapPatchResolver.
@ The specialized resolver validates variable operands and patch-state alternatives.
@ Exact arrays preserve the decoder markers and any patch replacement bytes.

@ 15 Thumb-1 halfwords, not ARM words. STACK reloads the app from an SP slot;
@ FRAME reloads it from [r3+0x40]. Variant 2 differs at cfg+0x20 -> app+0x6c.
@ Keep observed address assertions in tests, and variant semantics here.
@ pattern STACK halfwords thumb STACK
    ldr r1, [sp]
    ldr r0, [r4, #0xc]
    str r0, [r1, #0x58]
    ldr r1, [sp]
    ldr r0, [r4, #0x14]
    str r0, [r1, #0x5c]
    ldr r2, [sp]
    ldr r1, [r4, #0x18]
    movs r0, #2
    str r1, [r2, #0x68]
    ldr r1, [sp]
    str r0, [r1, #0x6c]
    ldr r1, [sp]
    ldr r0, [r4, #0x24]
    str r0, [r1, #0x70]
@ end

@ pattern FRAME halfwords thumb FRAME
    ldr r0, [r4, #0xc]
    ldr r1, [r3, #0x40]
    str r0, [r1, #0x58]
    ldr r0, [r4, #0x14]
    ldr r1, [r3, #0x40]
    str r0, [r1, #0x5c]
    ldr r0, [r4, #0x18]
    ldr r1, [r3, #0x40]
    str r0, [r1, #0x68]
    ldr r1, [r3, #0x40]
    movs r0, #2
    str r0, [r1, #0x6c]
    ldr r0, [r4, #0x24]
    ldr r1, [r3, #0x40]
    str r0, [r1, #0x70]
@ end

@ Same fields as form 2, but the compiler reloads the app before cfg.
@ The preceding +18 store leaves R1=app, so the shared patch is valid.
@ pattern FRAME_APP_FIRST halfwords thumb FRAME_APP_FIRST
    ldr r1, [r3, #0x40]
    ldr r0, [r4, #0xc]
    str r0, [r1, #0x58]
    ldr r1, [r3, #0x40]
    ldr r0, [r4, #0x14]
    str r0, [r1, #0x5c]
    ldr r1, [r3, #0x40]
    ldr r0, [r4, #0x18]
    str r0, [r1, #0x68]
    ldr r1, [r3, #0x40]
    ldr r0, [r4, #0x20]
    str r0, [r1, #0x6c]
    ldr r1, [r3, #0x40]
    ldr r0, [r4, #0x24]
    str r0, [r1, #0x70]
@ end

@ App-first with a constant +6c store through R0 (302). Its preceding
@ +18 store leaves R0=app, so the patch must reload R1 before using it.
@ pattern FRAME_APP_CONSTANT halfwords thumb FRAME_APP_CONSTANT
    ldr r1, [r3, #0x40]
    ldr r0, [r4, #0xc]
    str r0, [r1, #0x58]
    ldr r1, [r3, #0x40]
    ldr r0, [r4, #0x14]
    str r0, [r1, #0x5c]
    ldr r1, [r3, #0x40]
    ldr r0, [r4, #0x18]
    str r0, [r1, #0x68]
    ldr r0, [r3, #0x40]
    movs r1, #2
    str r1, [r0, #0x6c]
    ldr r1, [r3, #0x40]
    ldr r0, [r4, #0x24]
    str r0, [r1, #0x70]
@ end

@ pattern PATCH_APP_CONSTANT halfwords thumb PATCH_APP_CONSTANT
    ldr r1, [r3, #0x40]
    ldr r0, [r4, #0xc]
    str r0, [r1, #0x58]
    cmp r0, #5
    ldr r0, [r4, #0x14]
    bne #2
    movs r0, #0
    lsls r0, r0, #0x14
    str r0, [r1, #0x5c]
    ldr r0, [r4, #0x18]
    str r0, [r1, #0x68]
    movs r0, #2
    str r0, [r1, #0x6c]
    ldr r0, [r4, #0x24]
    str r0, [r1, #0x70]
@ end

@ Replacement: app+0x58=type; app+0x5c=(type==5 ? MiB<<20 : cfg+0x14);
@ then preserve app+0x68/+0x6c/+0x70. R1 already holds app at entry. In the
@ 301 STACK context R2 already holds app too; the removed R2 reload is dead.
@ Compare/shift flags are overwritten before use after this block. A new
@ recipe must check live registers, flags and incoming branches in context;
@ matching the stores alone is insufficient. See apps/ram_booster/docs/porting.md.
@ pattern PATCH halfwords thumb PATCH
    ldr r0, [r4, #0xc]
    str r0, [r1, #0x58]
    cmp r0, #5
    bne #4
    movs r0, #0
    lsls r0, r0, #0x14
    b #0
    ldr r0, [r4, #0x14]
    str r0, [r1, #0x5c]
    ldr r0, [r4, #0x18]
    str r0, [r1, #0x68]
    movs r0, #2
    str r0, [r1, #0x6c]
    ldr r0, [r4, #0x24]
    str r0, [r1, #0x70]
@ end

@ Older frame initializer (6303 08.90): the tail initializes +60/+64,
@ not +6c/+70. Loading the default quota between CMP and BNE preserves
@ flags and saves a branch, leaving room for those stores in 30 bytes.
@ pattern FRAME_ZERO halfwords thumb FRAME_ZERO
    ldr r0, [r4, #0xc]
    ldr r1, [r3, #0x40]
    str r0, [r1, #0x58]
    ldr r0, [r4, #0x14]
    ldr r1, [r3, #0x40]
    str r0, [r1, #0x5c]
    ldr r0, [r4, #0x18]
    ldr r1, [r3, #0x40]
    str r0, [r1, #0x68]
    ldr r0, [r3, #0x40]
    ldr r1, [pc, #0]
    str r6, [r0, #0x60]
    ldr r0, [r3, #0x40]
    adds r1, r1, #4
    str r6, [r0, #0x64]
@ end

@ pattern PATCH_ZERO halfwords thumb PATCH_ZERO
    ldr r0, [r4, #0xc]
    str r0, [r1, #0x58]
    cmp r0, #5
    ldr r0, [r4, #0x14]
    bne #2
    movs r0, #0
    lsls r0, r0, #0x14
    str r0, [r1, #0x5c]
    ldr r0, [r4, #0x18]
    str r0, [r1, #0x68]
    movs r0, r1
    ldr r1, [pc, #0]
    str r6, [r0, #0x60]
    adds r1, r1, #4
    str r6, [r0, #0x64]
@ end

@ Legacy zero-tail initializer. R7 was cleared just before this block;
@ preserve both zero stores and the following frame/literal loads. The
@ next LSLS overwrites our comparison flags before any conditional use.
@ pattern FRAME_LEGACY halfwords thumb FRAME_LEGACY
    ldr r0, [r4, #0xc]
    ldr r1, [r3, #0x40]
    str r0, [r1, #0x58]
    ldr r0, [r4, #0x14]
    ldr r1, [r3, #0x40]
    str r0, [r1, #0x5c]
    ldr r0, [r4, #0x18]
    ldr r1, [r3, #0x40]
    str r0, [r1, #0x68]
    ldr r0, [r3, #0x40]
    str r7, [r0, #0x60]
    ldr r0, [r3, #0x40]
    str r7, [r0, #0x64]
    ldr r0, [r3, #0x38]
    ldr r1, [pc, #0]
@ end

@ pattern PATCH_LEGACY halfwords thumb PATCH_LEGACY
    ldr r0, [r4, #0xc]
    str r0, [r1, #0x58]
    cmp r0, #5
    ldr r0, [r4, #0x14]
    bne #2
    movs r0, #0
    lsls r0, r0, #0x14
    str r0, [r1, #0x5c]
    ldr r0, [r4, #0x18]
    str r0, [r1, #0x68]
    str r7, [r1, #0x60]
    str r7, [r1, #0x64]
    mov r8, r8
    ldr r0, [r3, #0x38]
    ldr r1, [pc, #0]
@ end

@ Full-touch frame: cfg in R7, app in frame+44, tail fields at +74/+78.
@ pattern FRAME_TOUCH halfwords thumb FRAME_TOUCH
    ldr r1, [r3, #0x44]
    ldr r0, [r7, #0xc]
    str r0, [r1, #0x58]
    ldr r1, [r3, #0x44]
    ldr r0, [r7, #0x14]
    str r0, [r1, #0x5c]
    ldr r1, [r3, #0x44]
    ldr r0, [r7, #0x18]
    str r0, [r1, #0x68]
    ldr r1, [r3, #0x44]
    movs r0, #2
    str r0, [r1, #0x74]
    ldr r1, [r3, #0x44]
    ldr r0, [r7, #0x24]
    str r0, [r1, #0x78]
@ end

@ pattern PATCH_TOUCH halfwords thumb PATCH_TOUCH
    ldr r0, [r7, #0xc]
    str r0, [r1, #0x58]
    cmp r0, #5
    bne #4
    movs r0, #0
    lsls r0, r0, #0x14
    b #0
    ldr r0, [r7, #0x14]
    str r0, [r1, #0x5c]
    ldr r0, [r7, #0x18]
    str r0, [r1, #0x68]
    movs r0, #2
    str r0, [r1, #0x74]
    ldr r0, [r7, #0x24]
    str r0, [r1, #0x78]
@ end

@ pattern LEGACY_SCHEDULER halfwords thumb LEGACY_SCHEDULER
    push {r4, lr}
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    cmp r0, #0
    beq #78
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    cmp r0, #0
    bne #72
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    cmp r0, #0
    bne #10
    movs r0, #1
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    b #34
@ end

@ pattern LEGACY_RESET halfwords thumb LEGACY_RESET
    push {r4, lr}
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    cmp r0, #0
    beq #22
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    adds r3, r0, #1
    bne #2
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    movs r0, #0
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    movs r2, #0
    movs r1, #0x18
    movs r0, #0
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    pop {r4, pc}
@ end

@ pattern LEGACY_REINIT halfwords thumb LEGACY_REINIT
    push {r4, lr}
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    cmp r0, #0
    bne #26
    movs r1, #0
    movs r0, #0x7d
    lsls r0, r0, #0xe
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    movs r0, #5
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    adds r3, r0, #1
    bne #4
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    pop {r4, pc}
    cmp r0, #0
    bne #-8
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    movs r0, #1
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    pop {r4, pc}
@ end

@ pattern LEGACY_VM_HEAD halfwords thumb LEGACY_VM_HEAD
    push {r4, r5, r6, r7, lr}
    ldr r6, [pc, #0]
    sub sp, #0x1fc
    movs r5, #0
    adds r0, r6, #0
    sub sp, #0x118
    adds r0, #0x50
    str r5, [r6]
    strb r5, [r0]
@ end

@ pattern LEGACY_VM_LINK halfwords thumb LEGACY_VM_LINK
    ldr r0, [r6]
    movs r7, #1
    orrs r0, r7
    str r0, [r6]
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    str r5, [sp, #0x310]
    ldr r1, [pc, #0]
    ldr r2, [pc, #0]
    ldr r0, [r1]
    str r0, [sp, #0x30c]
    add r0, sp, #0x300
    str r0, [r1]
    add r0, sp, #0x180
    str r0, [sp, #0x300]
    ldr r0, [r2]
    str r0, [sp, #0x308]
    add r0, sp, #0x180
    .hword 1, 1 @ BLX marker for callPattern; target decoded separately
    adds r7, r0, #0
    bne #32
    ldr r6, [pc, #0]
    mov r0, sp
    str r0, [r6]
    .hword 1, 1 @ BLX marker for callPattern; target decoded separately
    cmp r0, #0
    bne #12
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    ldr r1, [pc, #0]
    str r0, [r1]
@ end

@ pattern LEGACY_HEAP_WRAPPER halfwords thumb LEGACY_HEAP_WRAPPER
    ldr r0, [pc, #0]
    push {r4, lr}
    movs r4, #0
    str r4, [r0]
    ldr r0, [pc, #0]
    str r4, [r0]
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
@ end

@ pattern LEGACY_HEAP_INIT halfwords thumb LEGACY_HEAP_INIT
    push {r4, r5, r6, lr}
    ldr r5, [pc, #0]
    movs r4, #0
    str r4, [r5, #4]
    .hword 1, 1 @ BLX marker for callPattern; target decoded separately
    ldr r1, [pc, #0]
    movs r0, #0
    adds r3, r4, #0
    lsls r2, r0, #3
    str r3, [r1, r2]
    adds r2, r2, r1
    adds r0, #1
    cmp r0, #0x74
    str r3, [r2, #4]
    blt #-16
    ldr r2, [pc, #0]
    movs r0, #1
    str r2, [r1, #4]
    str r0, [r5]
    pop {r4, r5, r6, pc}
@ end

@ pattern FIXED_RESET halfwords thumb FIXED_RESET
    push {r4, r5, r6, lr}
    movs r5, r0
    movs r6, r1
    movs r0, #0xc
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    movs r1, #0xdb
    strb r1, [r0, #1]
    lsrs r1, r5, #0x18
    strb r1, [r0, #4]
    lsrs r1, r5, #0x10
    strb r1, [r0, #5]
    lsrs r1, r5, #8
    strb r1, [r0, #6]
    movs r1, #0
    strb r5, [r0, #7]
    strb r1, [r0, #8]
    strb r1, [r0, #9]
    lsrs r1, r6, #8
    strb r1, [r0, #0xa]
    movs r4, r0
    strb r6, [r0, #0xb]
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    movs r1, r0
    movs r3, r4
    movs r2, #0xe1
    movs r0, #0x7a
    .hword 0, 0 @ BL marker for callPattern; target decoded separately
    pop {r4, r5, r6, pc}
@ end

@ pattern DIRECT_CACHE words arm DIRECT_CACHE
    push {r4, r5, r6, lr}
    add r5, r0, r1
    bic r0, r0, #0x1f
    mcr p15, #0, r0, c7, c14, #1
    mcr p15, #0, r0, c7, c5, #1
    add r0, r0, #0x20
    mcr p15, #0, r0, c7, c14, #1
    mcr p15, #0, r0, c7, c5, #1
    add r0, r0, #0x20
    cmp r0, r5
    blo #-36
    mcr p15, #0, r5, c7, c10, #4
    pop {r4, r5, r6, pc}
@ end

@ RAPU's ARM I-cache range helper, including the outer-cache tail call.
@ Only that call's relocation varies. The other RAPU form enters Thumb.
@ pattern RAPU_ARM_ICACHE words arm RAPU_ARM_ICACHE
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
@ end

@ Leaf ARM11 range synchronizer: clean D range, invalidate I range twice,
@ synchronize branch targets/prefetch, preserving incoming interrupt state.
@ pattern XGOLD_CACHE words arm XGOLD_CACHE
    add r1, r1, r0
    sub r1, r1, #1
    mov r2, r0
    mov ip, #0x1f
    bic r1, r1, ip
    bic r0, r0, ip
    mov ip, #7
    bic r2, r2, ip
    mov ip, #0
    mcr p15, #0, ip, c7, c10, #4
    mrs r3, apsr
    cpsid if
    mcrr p15, #0, r1, r0, c12
    mcr p15, #0, ip, c7, c10, #4
    msr cpsr_fsxc, r3
    mcr p15, #0, ip, c7, c10, #5
    mrs r3, apsr
    cpsid if
    mcrr p15, #0, r1, r0, c5
    mcr p15, #0, ip, c7, c5, #4
    mcrr p15, #0, r1, r0, c5
    mcr p15, #0, ip, c7, c5, #4
    mcr p15, #0, r2, c7, c5, #7
    mcr p15, #0, ip, c7, c10, #4
    mcr p15, #0, ip, c7, c5, #4
    msr cpsr_fsxc, r3
    bx lr
@ end
