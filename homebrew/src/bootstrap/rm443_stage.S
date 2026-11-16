/* SPDX-License-Identifier: MIT */
#include "native_protocol.h"
/*
 * RM-443 family stage zero, entered in ARM state from KVM's native BLX.
 *
 * The Java bootstrap has already synchronized the two cache lines containing
 * this 44-byte stage. This stage synchronizes the complete second payload with
 * ordinary (start, length) arguments, calls it, and preserves KVM's return.
 * The three placeholders are patched exactly once in the Java int[] carrier.
 */
.syntax unified
.arm
.align 2
.global stage_start
.type stage_start, %function
stage_start:
    push {r4, lr}                 /* keep the native stack 8-byte aligned */
    ldr  r0, .Lpayload_address
    ldr  r1, .Lpayload_length
    ldr  r2, .Lcache_sync
    blx  r2                       /* discovered cache_sync(start, byte_length) */
    ldr  r2, .Lpayload_address
    blx  r2                       /* ARM payload; returns here */
    pop  {r4, pc}                 /* return to KVM's native dispatcher */

.align 2
.Lpayload_address:
    .word FW_STAGE_PATCH_ENTRY
.Lpayload_length:
    .word FW_STAGE_PATCH_LENGTH
.Lcache_sync:
    .word FW_STAGE_PATCH_CACHE
.size stage_start, .-stage_start
