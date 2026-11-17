#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Exercise signature assembly and matching without firmware inputs."""

import os
import runpy
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GENERATOR = runpy.run_path(str(ROOT / "tools/gen_signatures.py"))
LLVM = Path(os.environ.get("LLVM") or
            (Path(shutil.which("llvm-mc")).parent if shutil.which("llvm-mc")
             else "/opt/homebrew/opt/llvm/bin"))


def assemble(source):
    with tempfile.TemporaryDirectory(prefix="s40-signature-test-") as temporary:
        path = Path(temporary) / "Fixture.S"
        path.write_text(source)
        return GENERATOR["assemble"](GENERATOR["parse"](path), LLVM)


def block(body, mode="thumb", kind="recipe"):
    anchor = "push {r4, lr}\nmovs r4, #7" if mode == "thumb" else ".word 0x12345678"
    return (f"@ pattern TEST {kind} {mode} test\n@ anchor\n{anchor}\n"
            f"{body}\n@ end\n")


class SignatureTests(unittest.TestCase):
    def test_real_instruction_masks(self):
        # The expected bits are architectural encodings, not firmware excerpts.
        cases = [
            ("thumb", "bl #4 @ mask=call", [0xf000, 0xe800], [0xf800, 0xe800]),
            ("thumb", "blx #4 @ mask=call", [0xf000, 0xe800], [0xf800, 0xe800]),
            ("thumb", "bl #4 @ mask=target", [0xf000, 0xf800], [0xf800, 0xf800]),
            ("thumb", "blx #4 @ mask=target", [0xf000, 0xe800], [0xf800, 0xf800]),
            ("thumb", "b #4 @ mask=target", [0xe000], [0xf800]),
            ("thumb", "bne #4 @ mask=target", [0xd100], [0xff00]),
            ("thumb", "ldr r2, [pc, #12] @ mask=literal", [0x4a00], [0xff00]),
            ("thumb", "adr r3, #12 @ mask=literal", [0xa300], [0xff00]),
            ("thumb", "cmp r2, #17 @ mask=imm8", [0x2a00], [0xff00]),
            ("arm", "bl #4 @ mask=target", [0, 0xeb00], [0, 0xff00]),
            ("arm", "bne #4 @ mask=target", [0, 0x1a00], [0, 0xff00]),
            ("arm", "blx #0 @ mask=target", [0, 0xfa00], [0, 0xfe00]),
            ("arm", "blx #2 @ mask=target", [0, 0xfa00], [0, 0xfe00]),
            ("arm", "ldr r2, [pc, #-12] @ mask=literal", [0x2000, 0xe51f], [0xf000, 0xffff]),
        ]
        for mode, instruction, words, masks in cases:
            with self.subTest(instruction=instruction):
                signature, anchor, actual, actual_masks = assemble(block(instruction, mode))[0]
                self.assertEqual(actual[2:], words)
                self.assertEqual(actual_masks[2:], masks)
                self.assertEqual((signature.alignment, anchor), (2 if mode == "thumb" else 4, 0))

    def test_data_explicit_masks_and_nonzero_anchor(self):
        result = assemble("""@ pattern TABLE recipe data table
.word 0 @ mask=ignore
@ anchor
.word 0x12345678
.hword 0xabcd, 0x4567 @ mask=0xff00,0x000f
@ end
""")[0]
        signature, anchor, words, masks = result
        self.assertEqual((signature.alignment, anchor), (4, 4))
        self.assertEqual(words, [0, 0, 0x5678, 0x1234, 0xab00, 7])
        self.assertEqual(masks, [0, 0, 0xffff, 0xffff, 0xff00, 0xf])
        # Profiles require the specialized return type, using the same arrays.
        profile = assemble(block("bx lr", kind="pattern"))
        self.assertIn("static FirmwareResolver.Pattern TEST()", GENERATOR["java_source"]("Profile", profile))

    def test_rejects_invalid_contracts(self):
        cases = [
            (block("movs r0, #1 @ mask=call"), "call mask"),
            (block("bx lr @ mask=target"), "target mask"),
            (block("ldr r0, [r1] @ mask=literal"), "literal mask"),
            (block("adds r0, r1, r2 @ mask=imm8"), "imm8 mask"),
            (block("bl #4 @ mask=0xffff"), "one 16-bit value"),
            (block("bx lr @ mask=0x10000"), "one 16-bit value"),
            (block("bx lr @ mask=unknown"), "unknown mask"),
            (block(".space 1"), "whole halfwords"),
            (block("bl unresolved_target"), "relocation"),
            (block("bx lr\n@ anchor"), "duplicate anchor"),
            ("@ pattern P recipe thumb p\n@ anchor\nbl #4 @ mask=call\n@ end\n", "four exact bytes"),
            ("@ pattern P recipe thumb p\npush {lr}\n@ anchor\n@ end\n", "four exact bytes"),
            (block("label: bx lr"), "without labels"),
        ]
        for source, message in cases:
            with self.subTest(message=message, source=source):
                with self.assertRaisesRegex(ValueError, message):
                    assemble(source)

    def test_generated_factories_execute_production_matcher(self):
        source = block("bl #4 @ mask=call\nldr r2, [pc, #12] @ mask=literal")
        source += """@ pattern PATCH halfwords thumb patch
movs r0, #3
bx lr
@ end
@ pattern ARM words arm arm_code
stmdb sp!, {r4, lr}
bx lr
@ end
"""
        generated = GENERATOR["java_source"]("Fixture", assemble(source))
        java_home = os.environ.get("JAVA8_HOME") or os.environ.get("JAVA_HOME")
        javac = str(Path(java_home) / "bin/javac") if java_home else "javac"
        java = str(Path(java_home) / "bin/java") if java_home else "java"
        with tempfile.TemporaryDirectory(prefix="s40-signature-java-") as temporary:
            build = Path(temporary)
            (build / "Fixture.java").write_text(generated)
            (build / "CheckSignature.java").write_text("""
final class CheckSignature {
    static void require(boolean condition) {
        if (!condition) throw new AssertionError();
    }
    public static void main(String[] args) {
        RomRecipe recipe = Fixture.TEST();
        int[] code = {0x2407b510, 0xf802f000, 0x00004a03};
        require(recipe.matches(code, 0x1000, 0x1000, 0x1000, 0x100a));
        code[1] = 0xe812f003; // A relocated BLX is accepted by mask=call.
        code[2] = 0x4a7f; // Literal displacement may change; destination may not.
        require(recipe.matches(code, 0x1000, 0x1000, 0x1000, 0x100a));
        code[2] = 0x4b7f;
        require(!recipe.matches(code, 0x1000, 0x1000, 0x1000, 0x100a));
        require(!recipe.matches(code, 0x1000, 0x1000, 0x1000, 0x1008));
        RomRecipe another = Fixture.TEST();
        recipe.words[0] = 0;
        recipe.masks[1] = 0;
        require(another.words[0] == 0xb510 && another.masks[1] == 0xffff);
        int[] patch = Fixture.PATCH();
        require(patch.length == 2 && patch[0] == 0x2003 && patch[1] == 0x4770);
        patch[0] = 0;
        require(Fixture.PATCH()[0] == 0x2003);
        int[] arm = Fixture.ARM();
        require(arm.length == 2 && arm[0] == 0xe92d4010 && arm[1] == 0xe12fff1e);
    }
}
""")
            subprocess.run([javac, "-d", str(build), str(ROOT / "src/java/resolver/RomRecipe.java"),
                            str(build / "Fixture.java"), str(build / "CheckSignature.java")], check=True)
            subprocess.run([java, "-cp", str(build), "CheckSignature"], check=True)

    def test_plain_arrays_reject_masks_and_partial_words(self):
        for kind, body, message in [
                ("bytes", ".byte 1 @ mask=ignore", "without masks"),
                ("halfwords", "bl #4 @ mask=call", "without masks"),
                ("halfwords", ".hword 1 @ mask=0xffff", "without masks"),
                ("words", ".hword 1", "whole 32-bit words"),
                ("halfwords", "@ anchor\n.hword 1, 2", "do not use anchors")]:
            with self.subTest(kind=kind, body=body):
                with self.assertRaisesRegex(ValueError, message):
                    assemble(f"@ pattern P {kind} thumb p\n{body}\n@ end\n")

    def test_c_headers_compile_exact_arrays_and_preserve_other_outputs(self):
        source = """@ pattern code_bytes bytes thumb patch
movs r0, #3
bx lr
.byte 0x7f
@ end
@ pattern code_halfwords halfwords thumb patch
movs r0, #3
bx lr
@ end
@ pattern code_words words arm patch
stmdb sp!, {r4, lr}
bx lr
@ end
"""
        with tempfile.TemporaryDirectory(prefix="s40-signature-c-") as temporary:
            build = Path(temporary)
            path = build / "Fixture.S"
            path.write_text(source)
            (build / "Stale.h").write_text("old header")
            (build / "Stale.java").write_text("other language")
            command = [sys.executable, str(ROOT / "tools/gen_signatures.py"), "--llvm", str(LLVM),
                       str(build), str(path)]
            subprocess.run(command + ["--format", "c"], check=True)
            self.assertFalse((build / "Stale.h").exists())
            self.assertEqual((build / "Stale.java").read_text(), "other language")
            (build / "check.c").write_text("""
#include "Fixture.h"
#include "Fixture.h"
_Static_assert(sizeof(code_bytes) == 5, "byte array width");
_Static_assert(sizeof(code_halfwords) == 4, "halfword array width");
_Static_assert(sizeof(code_words) == 8, "word array width");
int main(void) {
    return !(code_bytes[0] == 0x03 && code_bytes[1] == 0x20 &&
             code_bytes[2] == 0x70 && code_bytes[3] == 0x47 && code_bytes[4] == 0x7f &&
             code_halfwords[0] == 0x2003 && code_halfwords[1] == 0x4770 &&
             code_words[0] == 0xe92d4010u && code_words[1] == 0xe12fff1eu);
}
""")
            subprocess.run([os.environ.get("HOST_CC", "clang"), "-std=c11", "-Wall", "-Werror",
                            str(build / "check.c"), "-o", str(build / "check")], check=True)
            subprocess.run([str(build / "check")], check=True)
            header = (build / "Fixture.h").read_bytes()
            subprocess.run(command, check=True)
            self.assertFalse((build / "Stale.java").exists())
            self.assertEqual((build / "Fixture.h").read_bytes(), header)
        with self.assertRaisesRegex(ValueError, "C output requires"):
            GENERATOR["c_source"]("Fixture", assemble(block("bx lr")))


if __name__ == "__main__":
    unittest.main()
