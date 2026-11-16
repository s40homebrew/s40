#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Check the JAR layout and execute the actual CLDC-compatible stream on a JDK."""

import importlib.util
import shutil
import struct
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PACKER = ROOT / "tools/package_midlet.py"
SPEC = importlib.util.spec_from_file_location("package_midlet", PACKER)
PACKAGE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PACKAGE)

JAVA_TEST = r"""
import java.io.*;
import java.util.*;

public final class ChunkedStreamTest {
    static final class Source implements ChunkedResourceInputStream.ResourceSource {
        final HashMap<String, byte[]> files = new HashMap<String, byte[]>();
        int active, opened, high;
        boolean zeroBulk;
        public InputStream open(String name) throws IOException {
            if (active != 0) throw new IOException("Previous resource still open");
            byte[] bytes = files.get(name);
            if (bytes == null) return null;
            active++;
            opened++;
            high = Math.max(high, active);
            return new ByteArrayInputStream(bytes) {
                boolean closed;
                public synchronized int read(byte[] b, int off, int len) {
                    if (zeroBulk) { zeroBulk = false; return 0; }
                    return super.read(b, off, len);
                }
                public void close() {
                    if (!closed) { closed = true; active--; }
                }
            };
        }
    }
    static byte[] payload(int size) {
        byte[] result = new byte[size];
        for (int i = 0; i < size; i++) result[i] = (byte)(i * 37 + (i >>> 8));
        return result;
    }
    static void word(ByteArrayOutputStream out, int value) {
        for (int i = 0; i < 4; i++) out.write(value >>> (i * 8));
    }
    static Source fixture(int length, int size) {
        Source source = new Source();
        byte[] all = payload(length);
        int count = (length - 1) / size + 1;
        ByteArrayOutputStream index = new ByteArrayOutputStream();
        for (int value : new int[] {0x50433453, 1, length, size, count}) word(index, value);
        source.files.put("/application.bin.parts", index.toByteArray());
        for (int i = 0; i < count; i++)
            source.files.put("/application.bin.part" + i,
                Arrays.copyOfRange(all, i * size, Math.min(length, (i + 1) * size)));
        return source;
    }
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static void reconstruct(int length, int size, boolean single) throws Exception {
        Source source = fixture(length, size);
        source.zeroBulk = true;
        ChunkedResourceInputStream in = new ChunkedResourceInputStream(source, "/application.bin.parts");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (single) {
            int value;
            while ((value = in.read()) != -1) output.write(value);
        } else {
            byte[] buffer = new byte[8199];
            int got;
            check(in.read(buffer, 1, 0) == 0, "zero-length read");
            while ((got = in.read(buffer, 3, buffer.length - 3)) != -1)
                output.write(buffer, 3, got);
        }
        check(in.read() == -1, "stable EOF");
        check(Arrays.equals(payload(length), output.toByteArray()), "reconstruction mismatch");
        check(source.active == 0 && source.high == 1, "bounded resource lifetime");
        check(source.opened == 1 + (length - 1) / size + 1, "resource count");
        in.close(); in.close();
        try { in.read(); throw new AssertionError("closed stream accepted read"); }
        catch (IOException expected) { }
    }
    static void rejects(Source source, String label) throws Exception {
        InputStream in = null;
        boolean rejected = false;
        try {
            in = new ChunkedResourceInputStream(source, "/application.bin.parts");
            byte[] buffer = new byte[12003];
            while (in.read(buffer) != -1) { }
        } catch (IOException expected) { rejected = true; }
        finally { if (in != null) in.close(); }
        check(rejected, label + " was accepted");
        check(source.active == 0, label + " leaked resource");
    }
    public static void main(String[] args) throws Exception {
        for (int length : new int[] {1, 4095, 4096, 4097, 8192, 65535, 65536, 65537, 196613}) {
            reconstruct(length, 4096, false);
            reconstruct(length, 65536, true);
        }
        Source source = fixture(9000, 4096);
        source.files.remove("/application.bin.part1"); rejects(source, "missing chunk");
        source = fixture(9000, 4096);
        source.files.put("/application.bin.part0", new byte[4095]); rejects(source, "short first chunk");
        source = fixture(9000, 4096);
        source.files.put("/application.bin.part2", new byte[807]); rejects(source, "short final chunk");
        source = fixture(9000, 4096);
        source.files.put("/application.bin.part1", new byte[4097]); rejects(source, "long middle chunk");
        source = fixture(9000, 4096);
        source.files.put("/application.bin.part2", new byte[809]); rejects(source, "long final chunk");
        source = fixture(9000, 4096);
        source.files.put("/application.bin.parts", Arrays.copyOf(source.files.get("/application.bin.parts"), 19));
        rejects(source, "short index");
        source = fixture(9000, 4096);
        source.files.get("/application.bin.parts")[16] = 4; rejects(source, "wrong count");
        source = fixture(9000, 4096);
        source.files.get("/application.bin.parts")[12] = 1; rejects(source, "non power-of-two size");
        source = fixture(9000, 4096);
        source.files.put("/application.bin.parts", Arrays.copyOf(source.files.get("/application.bin.parts"), 21));
        rejects(source, "index trailing data");
        source = fixture(9000, 4096);
        InputStream early = new ChunkedResourceInputStream(source, "/application.bin.parts");
        early.read(); early.close();
        check(source.active == 0 && source.opened == 2, "early close opened unused parts");
        System.out.println("PASS chunk reconstruction, close ordering, boundaries, malformed inputs");
    }
}
"""


class ChunkedResources(unittest.TestCase):
    def test_manifest_option(self):
        self.assertIsNone(PACKAGE.native_chunk_bytes("MIDlet-Name: Legacy\n"))
        self.assertEqual(PACKAGE.native_chunk_bytes("S40-Native-Chunk-Bytes: 65\n 536\n"), 65536)
        for value in ("0", "-1", "4097", "131072", "bad", "６５５３６"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                PACKAGE.native_chunk_bytes("S40-Native-Chunk-Bytes: " + value)
        with self.assertRaises(ValueError):
            PACKAGE.native_chunk_bytes(
                "S40-Native-Chunk-Bytes: 4096\nS40-Native-Chunk-Bytes: 4096\n"
            )

    def test_packaging_opt_in_and_legacy(self):
        with tempfile.TemporaryDirectory(prefix="s40-resource-tests-") as temporary:
            base = Path(temporary)
            project, app, build, dist = (
                base / name for name in ("project", "app", "build", "dist")
            )
            for path in (project, app, build / "preverified", dist):
                path.mkdir(parents=True)
            (project / "COPYING").write_text("Fixture license\n")
            payload = bytes((n * 37 + (n >> 8)) & 255 for n in range(3 * 65536 + 19))
            source = build / "preverified/application.bin"
            source.write_bytes(payload)
            (build / "preverified/Fixture.class").write_bytes(b"fixture class")
            for chunked in (False, True):
                manifest = "Manifest-Version: 1.0\nMIDlet-Name: Fixture\n"
                if chunked:
                    manifest += "S40-Native-Chunk-Bytes: 65536\n"
                (app / "MANIFEST.MF").write_text(manifest)
                command = [
                    shutil.which("python3"),
                    str(PACKER),
                    str(project),
                    str(app),
                    str(build),
                    str(dist),
                    "Fixture",
                ]
                subprocess.run(command, check=True, capture_output=True)
                with zipfile.ZipFile(dist / "Fixture.jar") as archive:
                    if chunked:
                        self.assertNotIn("application.bin", archive.namelist())
                        index = archive.read("application.bin.parts")
                        self.assertEqual(
                            struct.unpack("<5I", index), (0x50433453, 1, len(payload), 65536, 4)
                        )
                        parts = [archive.read("application.bin.part" + str(n)) for n in range(4)]
                        self.assertEqual([len(part) for part in parts], [65536, 65536, 65536, 19])
                        self.assertEqual(b"".join(parts), payload)
                    else:
                        self.assertEqual(archive.read("application.bin"), payload)
                        self.assertNotIn("application.bin.parts", archive.namelist())
                self.assertEqual(source.read_bytes(), payload)
                first = (dist / "Fixture.jar").read_bytes()
                subprocess.run(command, check=True, capture_output=True)
                self.assertEqual((dist / "Fixture.jar").read_bytes(), first)

    def test_java_stream(self):
        javac, java = shutil.which("javac"), shutil.which("java")
        self.assertIsNotNone(javac, "host JDK required for actual stream test")
        self.assertIsNotNone(java, "host JDK required for actual stream test")
        with tempfile.TemporaryDirectory(prefix="s40-stream-tests-") as temporary:
            folder = Path(temporary)
            test = folder / "ChunkedStreamTest.java"
            test.write_text(JAVA_TEST)
            subprocess.run(
                [
                    javac,
                    "-d",
                    str(folder),
                    str(ROOT / "src/java/launcher/ChunkedResourceInputStream.java"),
                    str(test),
                ],
                check=True,
                capture_output=True,
            )
            result = subprocess.run(
                [java, "-cp", str(folder), "ChunkedStreamTest"],
                check=True,
                capture_output=True,
                text=True,
            )
            self.assertIn("PASS chunk reconstruction", result.stdout)


if __name__ == "__main__":
    unittest.main()
