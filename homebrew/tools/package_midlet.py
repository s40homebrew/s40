#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Create a reproducible MIDlet JAR and matching JAD without host metadata."""

import struct
import sys
import zipfile
from pathlib import Path


def native_chunk_bytes(manifest):
    """Parse the explicitly opted-in resource limit, including folded lines."""
    logical = []
    for line in manifest.splitlines():
        if line.startswith(" ") and logical:
            logical[-1] += line[1:]
        else:
            logical.append(line)
    values = []
    for line in logical:
        name, separator, value = line.partition(":")
        if separator and name.lower() == "s40-native-chunk-bytes":
            values.append(value.strip())
    if not values:
        return None
    if len(values) != 1 or not values[0].isascii() or not values[0].isdecimal():
        raise ValueError("Invalid S40-Native-Chunk-Bytes attribute")
    size = int(values[0])
    if not 4096 <= size <= 65536 or size & (size - 1):
        raise ValueError("Native chunks must be a power of two from 4096 to 65536 bytes")
    return size


def main():
    project_dir, example_dir, build_dir, dist_dir = map(Path, sys.argv[1:5])
    artifact = sys.argv[5]
    dist_dir.mkdir(parents=True, exist_ok=True)
    jar_path = dist_dir / (artifact + ".jar")
    jad_path = dist_dir / (artifact + ".jad")
    manifest = (example_dir / "MANIFEST.MF").read_text().rstrip() + "\n\n"
    chunk_size = native_chunk_bytes(manifest)
    # Write the supplied manifest verbatim with JAR-required CRLF endings. The
    # JDK jar tool would append Created-By with the local JDK version and vendor.
    # Use the ZIP epoch for every entry, including the manifest. ZipFile.write()
    # copies filesystem mtimes; writestr(name, ...) uses the current local time.
    ZIP_EPOCH = (1980, 1, 1, 0, 0, 0)
    with zipfile.ZipFile(jar_path, "w", zipfile.ZIP_DEFLATED) as archive:

        def write_entry(name, data):
            entry = zipfile.ZipInfo(name, date_time=ZIP_EPOCH)
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, data)

        write_entry("META-INF/MANIFEST.MF", manifest.replace("\n", "\r\n").encode())
        write_entry("licenses/COPYING.MIT", (project_dir / "COPYING").read_bytes())
        # Shared notices apply to every app. An example's optional licenses/
        # adds only its own dependencies (for example Doom's GPL and attribution).
        # Reject collisions so an app cannot silently replace a shared notice.
        license_names = {"COPYING.MIT"}
        for license_dir in (project_dir / "licenses", example_dir / "licenses"):
            if not license_dir.is_dir():
                continue
            for license_path in sorted(license_dir.iterdir()):
                if license_path.is_file():
                    if license_path.name in license_names:
                        raise SystemExit("Duplicate license filename: " + license_path.name)
                    license_names.add(license_path.name)
                    write_entry("licenses/" + license_path.name, license_path.read_bytes())
        classes = build_dir / "preverified"
        if chunk_size is not None and not (classes / "application.bin").is_file():
            raise SystemExit("Chunked native application is missing application.bin")
        for path in sorted(classes.rglob("*")):
            if path.is_file():
                name = path.relative_to(classes).as_posix()
                if name == "META-INF/MANIFEST.MF":
                    raise SystemExit("Unexpected manifest in preverified output")
                if chunk_size is not None:
                    if name == "application.bin.parts" or name.startswith("application.bin.part"):
                        raise SystemExit("Native chunk resource collision: " + name)
                    if name == "application.bin":
                        data = path.read_bytes()
                        if not 0 < len(data) <= 0x7FFFFFFF:
                            raise SystemExit("Native application has invalid chunked size")
                        count = (len(data) - 1) // chunk_size + 1
                        write_entry(
                            "application.bin.parts",
                            struct.pack("<5I", 0x50433453, 1, len(data), chunk_size, count),
                        )
                        for number, offset in enumerate(range(0, len(data), chunk_size)):
                            write_entry(
                                "application.bin.part" + str(number),
                                data[offset : offset + chunk_size],
                            )
                        # Keep the build's original file intact for dependency
                        # fingerprints and native-image inspection tools.
                        continue
                write_entry(name, path.read_bytes())
    jad = manifest.replace("Manifest-Version: 1.0\n", "").strip()
    # Inspect decompressed entries: scanning the compressed JAR itself misses
    # embedded paths. Reject macOS/Linux home paths and this workspace's root.
    with zipfile.ZipFile(jar_path) as archive:
        for entry in archive.infolist():
            if entry.date_time != ZIP_EPOCH or entry.extra:
                raise SystemExit("Unexpected ZIP timestamp/extra metadata: " + entry.filename)
            data = archive.read(entry)
            if any(
                prefix in data
                for prefix in (str(project_dir.parent).encode(), b"/Users/", b"/home/")
            ):
                raise SystemExit("Host path leaked into JAR entry: " + entry.filename)
    jad += (
        "\nMIDlet-Jar-URL: "
        + artifact
        + ".jar\nMIDlet-Jar-Size: "
        + str(jar_path.stat().st_size)
        + "\n"
    )
    jad_path.write_bytes(jad.replace("\n", "\r\n").encode())
    print("Built", jar_path)


if __name__ == "__main__":
    main()
