# Porting RAM Booster

RAM Booster changes Java launch quotas and, on selected devices, the shared
Java arena. Port its discovery, native entry and code-writing support together.
It does not need the graphics, audio or game APIs of the shared native runtime.
The [user guide](../README.md) explains supported modes and Restore behavior.

## Find the device contracts

Start with the profile in `src/HeapBoosterProfiles.java`. Establish readable
ROM/RAM regions before adding discovery recipes. Separate disjoint mappings;
Java object addresses must not widen the bounds accepted for native allocations
or page tables. Physical RAM ranges do not describe available free memory.

`HeapPatchResolver.java` discovers the launch policy, pool, cache and allocator
services. `HeapGrowthResolver.java` handles growable arenas. Shared model and
ROM-validation helpers are listed in `shared-java.txt`. Firmware suffixes do
not bypass discovery: validate instruction relationships and callers, including
both stock and already-patched policy forms, so later instances can Check and
Restore without stored addresses.

A policy patch must change only the intended foreground launch quota. Preserve
other launch modes, register/flag liveness, surrounding stores and incoming
branches. Check Restore's reconstructed instructions at every supported alignment.
The resolver and native writer contain the per-variant contracts.

## Keep each Java/native transaction reversible

Prepare resources, backups and resolver state before opening the temporary
getRGB memory view. Do not allocate Java objects, update UI, log or trigger GC
until the view has been restored. Anchor the owning class and validate method
ownership, signatures, storage and bounds before replacing anything.

Executable storage differs between VMs. Use the device's validated carrier and
cache synchronization path; an array containing code is not necessarily
executable. KVM can rewrite executed bytecode, so validation must account for
its known mutable instructions without relaxing the rest of the match.

Read back installed bytes before binding. Cleanup restores method bindings,
carrier data, payload storage and the memory view before reporting a result.
A cleanup failure prevents further transactions until reboot. Persistent patches
must not retain pointers into Java-owned or temporary payload storage.

The source of truth for result and command fields is
[src/HeapBoosterProtocol.java](../src/HeapBoosterProtocol.java), which generates
the native header. Use its named fields and check final status, not just the
entry marker. Architecture-specific extension fields have different meanings.

## Code writes and ownership

Choose a writer that matches the device's memory translation and cache rules.
Changing the compiler's CPU flag is not enough. Check page mappings, code aliases,
cache maintenance and firmware call relationships before enabling writes.
RAPU needs its firmware range helper, including external cache maintenance.

Allocate native storage outside interrupt exclusion. Recheck the expected code
and live mapping immediately before writing, preserve processor/domain state,
and synchronize instruction and table changes as required by the device.
Roll confirmed changes back in reverse order after a failed readback. Check and
an unchanged Apply must not rewrite code.

Flash-backed code needs a RAM shadow with recoverable ownership metadata.
Publish it only after the copy and mappings are ready. Preserve unaffected pages
and permissions when splitting a larger mapping. Restore must reinstate the
original mapping before freeing a shadow; if its deallocator cannot be
rediscovered, retain the mapping and report failure. Ownership cleanup still
matters when the copied instructions already contain stock bytes.

## Arena expansion

Fixed-arena replacement is implemented by `native/patch_fixed_growth.c`.
It links the initializer, supplier and live VM state, allocates OS-owned storage
and stages a Java restart after temporary state is restored. The restart stub
must own no Java pointers. Existing objects are not copied. Report activation
only after a later Check observes the new arena in use. Reuse it for later
quota changes; Restore resets the quota without shrinking a live heap.
When patches share a page mapping, rollback must preserve still-owned siblings.

The growable-arena path in `native/patch_growth.c` preserves object addresses
and extends the object reservation without changing the method arena or
single-object limit. Extension runs under the firmware's VMM lock and preserves
its processor-state requirements. An occupied adjacent reservation leaves the
request pending; only successful extension and readback activate it. Check
reports the state without retrying activation.

Persistent hooks and allocations outlive the booster. Restore can cancel a
pending extension or reset an active quota, but published code cannot be freed
while a callback might still return through it. Reboot releases the resident
state. A pending reset is not activation; a failed reset request requires reboot.

## Validate a port

Build and run `bash homebrew/apps/ram_booster/tests/check.sh` for packaged
bootstrap restoration and UI behavior. These checks need no private firmware
inputs and do not establish device compatibility.

On the target, verify Check, Apply, exit/reopen, independent allocation and GC
retention with Heap Check, Restore and reboot. Check both ordinary quota mode
and any expansion mode separately. Keep firmware, databases and hardware
experiments outside the public repository. Use the
[staged probe](../diagnostic/README.md) to localize loading or bootstrap failures.
