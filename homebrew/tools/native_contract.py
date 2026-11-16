# SPDX-License-Identifier: MIT
"""Address-free native image and pinned-descriptor wire contract.

The packer and allocation checks import these values. gen_imports.py emits the
same values for Java, C and preprocessed assembly; firmware API indices remain
in gen_imports.py. Changes here require compatibility review of existing images.

The S4D0 resource starts with eight little-endian words: magic, version,
initialized bytes, total memory bytes, entry offset, relocation count, stack
bytes and binding count. The initialized image follows, then word-sized
R_ARM_RELATIVE offsets and (image offset, API index) binding pairs.

RESULT_WORDS describes a separate pinned Java int[] used for commands and
results. TRANSFER is a 4 KiB staging area while loading; before RUN it is cleared
and filled with the optional NUL-terminated UTF-8 launch argument. The resolved
firmware table begins at API_DESCRIPTOR_WORD. Private bootstrap diagnostics
reuse several result words before application allocation, as named below.
"""

WORD_BYTES = 4
IMAGE_MAGIC = 0x30443453  # S4D0, little endian
IMAGE_VERSION = 2
IMAGE_HEADER_WORDS = 8
IMAGE_HEADER_BYTES = IMAGE_HEADER_WORDS * WORD_BYTES
IMAGE_STACK_BYTES = 192 * 1024
IMAGE_MAX_RELOCATIONS = 65536
IMAGE_MAX_BINDINGS = 256
RELOCATION_BYTES = WORD_BYTES
BINDING_BYTES = 2 * WORD_BYTES

RESULT_WORDS = {
    "MAGIC": 0,
    "COMMAND": 1,
    "IMAGE": 2,
    "STATUS": 3,
    "PHASE": 4,
    "DETAIL0": 5,
    "TASK_ID": 6,
    "APP_ID": 7,
    "DETAIL1": 8,
    "DETAIL2": 9,
    "ELAPSED_MS": 10,
    "ALLOCATION_BYTES": 11,
    "TRANSFER_OFFSET": 12,
    "TRANSFER_BYTES": 13,
    "ENTRY_OFFSET": 14,
    "BINDING_COUNT": 15,
    "ERROR_TEXT": 16,
    "METHOD_SLOT": 80,
    "OBSERVED_SP": 81,
    "LOADER_SOURCE": 82,
    "LOADER_BYTES": 83,
    "RESIDENT_LOADER": 84,
    "UI_REFRESH": 85,
    "CLOCK_RESULT": 86,
    "DISPLAY_WIDTH": 87,
    "DISPLAY_HEIGHT": 88,
    "TIMER_MESSAGE_LAST": 89,
    "IGNORED_TIMER": 90,
    "SPECIAL_TIMER": 91,
    "SPECIAL_TIMER_HANDLER": 92,
    "INPUT_LAYOUT": 93,
    "LAUNCH_OPTIONS": 94,
    "CANVAS_LAYOUT": 95,
    "TRANSFER": 96,
}
RESULT_MAGIC = 0x444F4F4D  # Historical DOOM descriptor tag, retained for ABI compatibility.
ERROR_TEXT_WORDS = 64
ERROR_TEXT_BYTES = ERROR_TEXT_WORDS * WORD_BYTES
TRANSFER_BYTES = 4096
TRANSFER_WORDS = TRANSFER_BYTES // WORD_BYTES
API_DESCRIPTOR_WORD = RESULT_WORDS["TRANSFER"] + TRANSFER_WORDS
LOADER_MAX_BYTES = 3072
LOADER_API_COUNT = 5
LOADER_PATCH_MAGIC = 0xA40F0000
LOADER_PATCH_MASK = 0xFFFF0000
KVM_ARRAY_DATA_BYTES = 12
STAGE_MAX_BYTES = 56
STAGE_LOADER_GAP_WORDS = 16
STAGE_PATCH_ENTRY = 0xDEAD0001
STAGE_PATCH_LENGTH = 0xDEAD0002
STAGE_PATCH_CACHE = 0xDEAD0003

# Bootstrap diagnostics reuse loading words before any native allocation.
BOOTSTRAP_WORDS = {
    "CACHE_FLAGS": 7,
    "QUERY_FLAGS": 8,
    "CACHE_CODE": 9,
    "QUERY_CODE": 10,
    "CACHE_SIGNATURE": 11,
    "QUERY_SIGNATURE": 12,
    "CACHE_METHOD_INDEX": 13,
    "QUERY_METHOD_INDEX": 14,
    "METHOD_COUNT": 15,
}

COMMANDS = {"INSTALL": 0, "ALLOCATE": 1, "TRANSFER": 2, "RUN": 3, "ABORT": 4}
STATUSES = {
    "OK": 0,
    "BOOTSTRAP_BOUNDS": 20,
    "ALLOCATION_BOUNDS": 21,
    "ALLOCATION_FAILED": 22,
    "TRANSFER_BOUNDS": 23,
    "IMAGE_BOUNDS": 24,
    "RELOCATION": 25,
    "COMMAND": 26,
    "IMPORT": 27,
}
LOADER_APIS = {"KNI_PARAM": 0, "KNI_RETURN": 1, "ALLOCATE": 2, "FREE": 3, "CACHE_SYNC": 4}
PHASES = {"ENTER": 1, "RUNNING": 2}


def constants():
    """Flat names shared by generated NativeProtocol and FW_* definitions."""
    values = {
        name: value
        for name, value in globals().items()
        if name.isupper() and isinstance(value, int)
    }
    values.update(("RESULT_" + name + "_WORD", value) for name, value in RESULT_WORDS.items())
    values.update(("BOOTSTRAP_" + name + "_WORD", value) for name, value in BOOTSTRAP_WORDS.items())
    # Command-specific aliases make word reuse explicit without changing layout.
    values["RESULT_IMAGE_BYTES_WORD"] = RESULT_WORDS["TRANSFER_OFFSET"]
    values["RESULT_RELOCATION_COUNT_WORD"] = RESULT_WORDS["TRANSFER_BYTES"]
    for prefix, group in (
        ("COMMAND", COMMANDS),
        ("STATUS", STATUSES),
        ("LOADER_API", LOADER_APIS),
        ("PHASE", PHASES),
    ):
        values.update((prefix + "_" + name, value) for name, value in group.items())
    return values


def generated_sources():
    values = constants()
    header = [
        "/* SPDX-License-Identifier: MIT */",
        "/* Generated from tools/native_contract.py; also consumed by assembly. */",
        "#ifndef S40_NATIVE_PROTOCOL_H",
        "#define S40_NATIVE_PROTOCOL_H",
    ]
    java = ["// Generated from tools/native_contract.py.", "final class NativeProtocol {"]
    for name, value in values.items():
        number = f"0x{value:08X}" if value > 0x7FFFFFFF else str(value)
        header.append(f"#define FW_{name} {number}")
        java.append(f"    static final int {name} = {number};")
    header.extend(["#define FW_WORD_OFFSET(word) ((word) * FW_WORD_BYTES)", "#endif", ""])
    java.extend(["}", ""])
    return "\n".join(header), "\n".join(java)
