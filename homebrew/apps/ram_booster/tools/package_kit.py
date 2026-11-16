#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Package built MIDlets and public usage notes without private research inputs."""

import zipfile
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[3]


def main():
    entries = {}
    for folder, name in (("ram_booster", "S40RamBooster"), ("heap_check", "S40HeapCheck")):
        base = PROJECT / "dist" / folder
        jar = (base / (name + ".jar")).read_bytes()
        jad = (base / (name + ".jad")).read_bytes()
        fields = dict(line.split(": ", 1) for line in jad.decode().splitlines() if ": " in line)
        if int(fields["MIDlet-Jar-Size"]) != len(jar) or fields["MIDlet-Jar-URL"] != name + ".jar":
            raise ValueError("JAR/JAD mismatch: " + name)
        entries[name + ".jar"] = jar
        entries[name + ".jad"] = jad
        if folder == "ram_booster":
            version = fields["MIDlet-Version"]
    entries["READ_ME.txt"] = (
        f"S40 RAM Booster {version} - phone test kit\n\n"
        + """Copy both JAR/JAD pairs into the same directory on the phone.

1. Run S40 Heap Check before boosting. Record Show Java memory and a
   retention/GC test, then exit. Its 1 and 1.5 MiB tests suit smaller heaps.
2. Run S40 RAM Booster > Check phone. Follow the device-specific first
   Apply, exit/reopen and confirmation sequence in RAM_BOOSTER.md.
3. Once the booster confirms the quota is active, close it and launch the
   separate Heap Check app. Leave room for the checker and object headers:
   a full-quota retention test is not expected to pass.
4. Record the retained amount and sampled-data result, not just totalMemory.
   Reopen the booster to Restore, then relaunch Heap Check to compare.

RAM_BOOSTER.md is the current source of supported models, quota choices,
activation instructions, Restore/reboot behavior and validation evidence.
PORTING.md describes discovery, native entry and memory ownership.
HEAP_CHECK.md describes the independent Java allocation test.

Model selection ignores firmware release suffixes. Code and memory-map
validation must still pass on the actual phone. Fixture versions in the
notes are evidence, not a firmware allowlist or universal compatibility claim.
No flash writes are performed. Arena growth retains memory until reboot;
Restore resets the quota without shrinking an active Java arena.

Doom allocates its engine memory from the native OS heap. It does not
require this booster, and a Java boost does not add physical RAM.

Source: homebrew/apps/ram_booster/
"""
    ).encode()
    entries["HEAP_CHECK.md"] = (PROJECT / "apps/heap_check/README.md").read_bytes()
    entries["RAM_BOOSTER.md"] = (PROJECT / "apps/ram_booster/README.md").read_bytes()
    entries["PORTING.md"] = (PROJECT / "apps/ram_booster/docs/porting.md").read_bytes()
    out = PROJECT / "dist/ram_booster/S40RamBooster-test-kit.zip"
    with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in entries.items():
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data)
    with zipfile.ZipFile(out) as archive:
        if archive.testzip() is not None:
            raise ValueError("Test-kit archive verification failed")
    print(f"Packaged {out} ({out.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
