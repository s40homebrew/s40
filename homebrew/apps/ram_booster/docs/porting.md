# Porting RAM Booster

Port the memory primitive, firmware discovery, native entry and code writer.
Display, audio and game APIs are not needed. The app's supported models and
current handset status are listed in the [README](../README.md).

## Profiles and discovery

`HeapBoosterProfiles.java` supplies model tokens, mapped ROM/RAM windows and
architecture policy. `HeapPatchResolver.java` runs the policy, pool, cache and
allocator recipes; `HeapGrowthResolver.java` discovers the X-GOLD extension.
Matched code selects the recipe. Firmware suffixes do not select addresses or
bypass validation. Shared `PlatformIdentity`, `RomRanges`, `RomRecipe` and
`Rm217Memory` implementations also serve the application runtime.

Establish mapped ranges before scanning. The RM-217 and RM-813 range walkers
narrow profile ceilings using firmware metadata. A second code bank separated
by an unmapped gap needs a separate range, not one larger sweep. RM-767 keeps
OS/table allocations in identity RAM while Java objects can occupy separate
virtual arenas; accepting an object pointer there must not widen native
physical-RAM bounds.

Policy matching recognizes stock and patched forms so a later app instance can
Check or Restore without saved addresses. The nearby `Failed to load main class`
string corroborates the instruction recipe; it is not sufficient by itself.
Pool recognition follows constant-building code and verifies the returned
arena and output stores. Growable-pool recognition checks reservation and
growth ceilings plus header accounting. Cache helpers and their call edges
are validated before use.

Named `os_block_*` functions normally come from the firmware's library/ELF
exports. RM-217 has no such descriptors: allocator patterns are corroborated
by calls from the file-read native handler. Its BSE type-4 lookup supplies
physical RAM bounds. These describe RAM banks, not available free memory.

## Java/native bridge

The session prepares resources, backups, resolver constants and its owning
`Class` reference before opening the getRGB memory view. Nothing may allocate
Java objects, update UI, log or trigger GC until cleanup completes.

The current KVM layout has four-byte references, int-array length at +8 and
data at +12. Forging length `0x40000000` makes index `0x3FFFFFFF` wrap to the
array's own length word. The image copy must target that same array, without
assuming a separate destination is adjacent. Backup/canary/box/target ordering
and spacing still determine the anchor offsets. A changed view base stops
before method lookup; a length change must be recorded even if the copy throws.

The class method-table pointer is at +36. Each 32-byte method record contains
code at +4, flags at +20, owner at +24 and argument count in the high halfword
at +28. Follow the anchored owning Class and validate record ownership, bounds,
length and alignment. Bytecode can live outside the Java object heap; do not
reject it solely for that placement. Match private/static/native modifiers
independently of VM-private flag bits.

The zero-argument native entry uses the generated 114-byte Thumb switch
carrier: entry at +13, literals at +40/+48/+56/+64 for payload, byte length,
cache helper and entry. The unique `0xB0057E01` payload literal receives the
result-array data pointer. There is no KNI argument cleanup.

Native payloads execute from `payloadSpace(int,int)`, not a Java array: RAPU
can mark data pages non-executable. The reserved method contains a 12-byte
header, a **12,288-byte** sparse lookup table and four return bytes, totaling
12,304 bytes. Check its record, signature, geometry and invariant bytes.
KVM quickens return opcodes in place, so those mutable bytes are not matched
against the JAR. Establish writable and executable method storage for each VM.

Call `payloadSpace` as Java once before forging the view, then never call it as
Java again in that session. Read back installed native bytes before binding;
synchronize them through the carrier on every native invocation. Cleanup
restores method binding, carrier literals, payload bytes, canary and array
length before reporting results. Persistent patches must not retain pointers
into this app-owned storage. Cleanup failure preserves diagnostics and prevents
another transaction until reboot.

## Launch-policy semantics

The 30-byte override changes only launch type 5 at app+0x58 and its quota at
app+0x5C. Other launch types retain their configured quota. Recognizers cover
these layouts:

| Variant | Distinguishing contract |
| --- | --- |
| 0 — 301 | App pointer reloaded from the stack |
| 1 — C3 | App at frame+0x40; app+0x6C receives constant 2 |
| 2 — C2 / newer 6303 | Frame form; app+0x6C comes from config+0x20 |
| 3 — older 6303 | Preserves app+0x60/+0x64 stores and the final R1 adjustment |
| 4 — 6700 | App-first load order; shares variant 2's replacement |
| 5 — 302 | Reloads R1 because the preceding store leaves only R0 pointing to the app |
| 6 — 306 | App at frame+0x44, config in R7; preserves app+0x74/+0x78 |
| 7 — 6300 | Preserves R7 zero stores and following frame/literal loads |

Check register/flag liveness, untouched fields and incoming branches into the
block, not just the final quota. Variant 3's moved literal load needs its
immediate recomputed for PC alignment when reconstructing Restore bytes.
Stock and patched recipes must both remain discoverable at either Thumb
halfword alignment.

## MMU and cache backends

| Backend | Mapping and cache contract |
| --- | --- |
| ARMv6 identity — 301 | Existing DDR code, section/coarse descriptors and inner-cache maintenance |
| ARM926 RAP — C2, 6300, 6303 | Firmware walker establishes `0xD0000000 + physical_table - TTBR`; validates its CP15 call edges |
| ARM926 BCM — C3 | Identity-mapped tables/code; reads live TTBR |
| ARMv6 RAPU — 302, 6700 | BSE walker and type-0 RAM records; firmware range helper also maintains the external L220 cache |
| ARMv6 X-GOLD — 306 | ARM1176 legacy maps with XP clear and TTBCR zero; identity OS/table RAM |

RAPU requires a single TTBR0 address space. Do not replace its range helper
with an inner-cache-only loop. ARM926 direct writes support section/coarse/fine
descriptors; the patch stays within one 1 KiB boundary. Flash-backed code needs
a RAM shadow. Changing a compiler architecture flag does not port these rules.

Native OS allocation/free occurs outside interrupt exclusion and is separate
from Java allocation. Before writes, recheck expected bytes and live mappings
under IRQ/FIQ exclusion. Preserve processor/domain state, synchronize code
aliases, clean page-table stores, drain writes and invalidate TLB/I-cache as
required. Readback failure rolls confirmed changes back in reverse order.
Check and repeated Apply of an unchanged direct patch do not rewrite code.

### Shadow ownership

The RAP shadow uses an 8319-byte OS block for alignment, a 4 KiB code copy and
a 128-byte ownership header. Use the global `os_block_alloc_no_wait` /
`os_block_dealloc` pair; app-owned allocators have the wrong lifetime. The
header records the raw allocation, aliases, site, table identity and original
descriptors so later instances can recover the shadow.

A 64 KiB large page becomes sixteen small-page entries together. Preserve
physical pages, cache bits and each access-permission quarter except for the
redirected page. RAP flash shadows support coarse small/large pages; direct
DDR support for section/fine tables does not imply shadow support for them.
X-GOLD ordinary mode converts a section to a coarse table and preserves its
other 255 pages and section attributes.

Publish only after preparing and synchronizing the copy. Restore reinstates
original descriptors before freeing it. If the deallocator cannot be
rediscovered, retain the mapping and report failure. Ownership cleanup must
precede the common no-op comparison even when the copied instructions already
contain stock bytes.

## Fixed-arena replacement

`patch_fixed_growth.c` uses the ARM926 writer for pool-supplier, configured-size
and launch-policy changes. Discovery links the initializer, supplier and live
VM state. RM-443 uses a 9 MiB replacement; RM-217 uses 5 MiB. A contiguous
OS allocation holds the arena and owner/reset record. That record stays before
the arena, outside the firmware's later clear operation.

Supplier and policy can share a large-page group. Rollback must undo later
shadows first, so restoring one mapping does not discard a still-owned sibling.
Fresh Check verifies the owner, supplier and live VM base/size before reporting
activation. Subsequent quota changes reuse the allocation; Restore changes
only the quota. The replacement arena remains until reboot.

The initial native call stages the changes and returns. Java restores its
forged view and executable bytes, then immediately invokes an independent
OS-owned reset stub before any UI/RMS allocation. The stub unbinds the native
method first and holds no Java object pointer. On RM-443 it queues the
firmware reset; on RM-217 it clears the discovered reset-request byte for the
normal Java scheduler. Both reset paths must be linked to the matched VM
initializer. A pending reset is not an active arena, and reset-enqueue failure
requires reboot.

## X-GOLD arena extension

`patch_growth.c` extends the object reservation to 8 MiB without moving live
objects. The method/permanent arena and its cached limit stay at 4 MiB.
Discovery binds the object provider, Runtime handlers, release wrapper,
shrink continuation and VMM reservation/locking functions.

Five cloned code pages in two sections, coarse tables, metadata and resident
payload share an OS-owned allocation. The exit wrapper preserves registers
and flags, tries extension under VMM lock 0x11A, then resumes the original
shrink path. Entry and extension require privileged mode with IRQ enabled;
FIQ may already be masked and must retain its original state.

An occupied neighbour leaves the request pending. Only validated tail extension
and successful readback activate the growth patches and requested quota.
The release helper distinguishes existing permanent callers from the changed
object-arena path. Pending Restore cancels activation; active Restore resets
the quota without shrinking the heap. Published resident code is never freed
by Restore, since a callback may still be returning through it. Reboot releases
it. Check reports state without retrying activation.

## Protocol and validation

`HeapBoosterProtocol.java` defines the **144-word** HBP1 result/command array and
generates the C header. Use its named fields rather than duplicating offsets.
The entry marker alone is not success: check status as well. Architecture-specific
extension fields share the descriptor; fixed growth and X-GOLD interpret them
differently. X-GOLD callback observations persist across rearming and describe
the last attempt, not necessarily the current VMM state.

After building, run `bash homebrew/apps/ram_booster/tests/check.sh`. The retained
host tests execute packaged Java bootstrap and UI behavior with synthetic
memory/API boundaries. They do not establish firmware compatibility.
For a new port, validate instruction semantics, mappings, ownership and cache
operations, then exercise Check, Apply, exit, independent retention/GC checking,
Restore and reboot on the target. Keep firmware and device-specific experiments
outside the public repository; record handset status separately from emulation.
