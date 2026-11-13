# Porting RAM Booster

`HeapBoosterProfiles.java` supplies model tokens and memory windows. All
profiles run the same policy/pool/cache recipes in `HeapPatchResolver.java`.
The matched firmware code selects the recipe.

The session temporarily enlarges an array through getRGB, finds its own KVM
methods, writes the native payload into reserved executable method storage,
and enters it through a generated Thumb switch carrier. Java arrays hold data
and backups: later phones can mark ordinary Java data memory non-executable.
Do not allocate, update UI or permit GC while the memory view is forged.

Check these contracts before adding a profile:

- Readable ROM windows, writable object RAM, KVM object/class/method fields,
  native invocation and instruction-cache synchronization.
- Bytecode storage may live outside object RAM. The carrier and storage lookup
  follow owned method records and validate their invariant bytes. KVM can
  rewrite return opcodes after first execution.
- The generator and session agree on a 114-byte carrier, literals at offsets
  40/48/56/64 and entry at +13. Payload storage is an 8208-byte method with an
  8192-byte switch table. Test the packaged class bytes when changing compilers.
- Launch-policy matching must recognize stock and patched forms so Restore
  works after restarting the app. Pool discovery bounds the offered quotas.
- ARMv6 and ARM926 have separate MMU/cache writers. RAPU needs its BSE RAM-bank
  mapping and external-cache path; changing a compiler architecture flag alone
  cannot port these operations.

Flash-backed RAP code is copied into an OS-owned RAM page and remapped.
Its ownership header lets another booster instance recover
the original descriptors. Restore must put the original mapping back before
freeing the copy. If the deallocator cannot be rediscovered, retain the mapping
and report failure rather than unmapping live code.

`HeapBoosterProtocol.java` generates the C field/operation constants.
`patch_common.h` supplies comparison, copy and quota checks. Backend writes
exclude interrupts, compare expected instructions, synchronize caches/TLB,
check readback and roll back on failure. Check and repeated Apply of an
unchanged quota must not rewrite code.
