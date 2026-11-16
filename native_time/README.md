# Native Time

A Java ME example that calls the native OS millisecond counter on Nokia
6303 classic (RM-443) and Asha 302 (RM-813).

Follow [build setup](../docs/building.md), then run from the repository root:

```sh
bash native_time/build.sh
```

Install `native_time/dist/NativeTime.jar` and its matching JAD, then choose
**Read native time**. Discovery may take several seconds. The result is a
wrapping 32-bit millisecond counter, not the date or wall-clock time.

## Implementation

`src/NativeTime.java` contains the reversible getRGB memory view, native binding,
40-byte ARM routine and executable carrier. `src/TimeResolver.java` and
`src/RapuTimeResolver.java` discover firmware services from instruction patterns
and exports. Model selection chooses the memory layout; firmware versions are
not allowlisted.

The 6303 executes the routine from Java array storage. The 302 uses VM-managed
executable method storage and a Thumb carrier to synchronize caches and call the
ARM routine. Temporary method bindings, carrier literals, storage bytes and
array length are restored before displaying results. A cleanup failure disables
further queries; reboot before using the app again.

## Host check

After building, source `tools/env.sh` in Bash and run:

```sh
python3 native_time/tests/check.py
```

This checks the compiled binding/install/restore methods with synthetic VM
records, including quickened returns, bounded writes and exact restoration.
It also checks the CLDC package and JAD size. No firmware input is required.

MIT licensed; see [COPYING](COPYING).
