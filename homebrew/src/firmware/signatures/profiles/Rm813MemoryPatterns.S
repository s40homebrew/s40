@ SPDX-License-Identifier: MIT
@ Instruction signatures for Rm813Memory.
@ Calls and literal operands are decoded and validated by the Java resolver.

@ Adjacent RAM and ROM tables consumed by the firmware range checker.
@ Ends are image metadata; family starts and zero terminators are structural.
@ pattern RAPU_MEMORY_RANGES recipe data RAPU_MEMORY_RANGES
    .word 0xd8000000
    .space 4 @ mask=ignore
    .word 0x0
    .word 0x0
@ anchor
    .word 0xf0100000
    .space 4 @ mask=ignore
    .word 0xf3000000
    .space 4 @ mask=ignore
    .word 0x0
    .word 0x0
@ end
