# Firmware signatures

Firmware discovery matches short instruction sequences and then follows their
calls, literals and data structures. Readable ARM assembly signatures live in
`src/firmware/signatures/`, grouped by profile or service. The build assembles them
with LLVM MC and generates Java factories in its `signatures/` output directory.
Generated files are build artifacts; edit the assembly sources instead.

A signature should describe what establishes the required ABI: for example, a
field access, a call sequence or a table stride. Explain that relationship above
the block, and explain why an operand may vary beside its mask.

```asm
@ The getter preserves its destination across a relocated helper call.
@ pattern GETTER recipe thumb getter
@ anchor
push {r4, lr}
movs r4, #7
bl #4                   @ mask=target ; helper address varies
ldr r0, [pc, #12]        @ mask=literal ; global address lives in a literal pool
pop {r4, pc}
@ end
```

`@ pattern` takes a Java factory name, a return type (`recipe` for `RomRecipe` or
`pattern` for `FirmwareResolver.Pattern`), an instruction set (`thumb`, `arm`, or
`data`), and a diagnostic name. Names use letters, digits and underscores, with
a letter or underscore first. The source filename becomes the generated Java
class name, so filenames must also be unique Java identifiers.

`@ anchor` precedes four exact bytes used to find candidates. It may occur once
per block and cannot cover masked bytes. Thumb recipes require two-byte candidate
alignment; ARM and data recipes require four-byte alignment. Matching still
checks every masked halfword after locating a candidate.

Each source line contains one instruction or a `.byte`, `.hword`, `.word` or `.space`
directive. Code uses ARMv6 unified syntax. Data tables and inline data use explicit
directives; an intentionally partial instruction prefix can use `.hword` with a
comment explaining the boundary. Labels and unresolved symbols are rejected.
Use numeric PC-relative branch operands: `bl #4` expresses a displacement, not an
absolute firmware address. Keep exact branch operands when the relationship
within the matched sequence matters.

By default every bit matches. A trailing `@ mask=...` changes only that line:

| Mask | What remains fixed |
| --- | --- |
| `target` | Branch condition and kind, including BL versus BLX; the destination may vary. Supports direct ARM and Thumb branches and calls. |
| `call` | A two-halfword Thumb call; both the destination and BL versus BLX may vary. |
| `literal` | The destination register and PC-relative load/address instruction; its displacement may vary. Supports Thumb literal LDR/ADR and ARM literal LDR. |
| `imm8` | A Thumb immediate MOV, CMP, ADD or SUB opcode and register; its eight-bit immediate may vary. |
| `ignore` | No bits from the line. Use for data holes, with a reason. |
| `0xff00,0xffff` | One explicit 16-bit mask per emitted halfword, in little-endian order. |

For recipes, the generator rejects incompatible masks, odd byte counts, masked
or truncated anchors, and assembly that needs relocations. It preserves the chosen instruction
encoding; aliases that change flags, register encoding or instruction width must
not be substituted merely because they look equivalent.

Use the generated factory at the same initialization point as the former recipe:

```java
private static final RomRecipe GETTER = GetterPatterns.GETTER();
```

Every factory creates fresh arrays so profile-specific adjustments cannot change
another profile's recipe. Services must prepare their recipes before installing a
forged memory view; discovery must not trigger recipe allocation while that view
is active.

Utilities with specialized instruction decoders or replacement patches can use
`bytes`, `halfwords` or `words` instead of `recipe`. These generate fresh `int[]`
factories containing exact bytes, 16-bit halfwords or little-endian 32-bit words.
They take no `@ anchor` and reject masks; a `words` block must emit a multiple of
four bytes.
An opcode placeholder consumed by a utility's decoder remains an exact value in
the generated array. Keep its interpretation beside the source block.

```asm
@ A replacement leaf function; these are emitted patch bytes, not a masked match.
@ pattern RETURN_THREE halfwords thumb return_three
movs r0, #3
bx lr
@ end
```

App-local utility sources can live beside the app and be supplied explicitly to
the generator. They need not be included in the shared resolver's signature set.

For native code, `--format c` generates a header per source file, with an include
guard and `static const uint8_t`, `uint16_t` or `uint32_t` arrays. Symbol names stay
unchanged. C output accepts only the exact array kinds; masked recipes belong to
the Java resolver. Byte arrays may include an odd number of bytes. Include the
generated header from the build output; do not copy its arrays into native sources.
Generation removes stale artifacts only for the selected language (`.h` or
`.java`), so both formats may share an output directory.

Normal SDK builds generate signatures automatically. To generate selected sources
directly, run `python3 homebrew/tools/gen_signatures.py OUTPUT SOURCE.S` from the
repository root. `LLVM` may name the directory containing `llvm-mc`, or use
`--llvm DIRECTORY`. The public generator and matcher checks run with
`python3 homebrew/tests/resolver/test_signatures.py`; they require LLVM, a JDK and
a C compiler, and do not use firmware dumps.
