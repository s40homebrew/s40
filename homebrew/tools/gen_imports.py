#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Generate runtime-bound veneers, import names, and a shared numeric ABI.
This generator does not read firmware or embed ROM/RAM addresses. Every bind
slot is initially zero and is filled by the native loader after discovery.
"""

import json
import sys
from pathlib import Path

from native_contract import API_DESCRIPTOR_WORD, LOADER_PATCH_MAGIC, RESULT_WORDS, generated_sources

CONFIG_WORDS = {
    name: RESULT_WORDS[name]
    for name in (
        "DISPLAY_WIDTH",
        "DISPLAY_HEIGHT",
        "TIMER_MESSAGE_LAST",
        "IGNORED_TIMER",
        "SPECIAL_TIMER",
        "SPECIAL_TIMER_HANDLER",
        "INPUT_LAYOUT",
        "LAUNCH_OPTIONS",
        "CANVAS_LAYOUT",
        "UI_REFRESH",
    )
}
# Runtime policy IDs shared by the profile and native input adapter.
INPUT_LAYOUTS = {"INPUT_KEYPAD": 0, "INPUT_QWERTY": 1}
CANVAS_LAYOUTS = {"CANVAS_CURRENT": 0, "CANVAS_LEGACY": 1}
# Optional services share the same resolved table as bootstrap/core imports.
# Entries are data slots, not unconditional veneers: a missing capability must
# never leave a callable zero veneer in an otherwise valid native application.
# Group limits are exclusive. ABI values describe executable contracts, not
# firmware versions or whether a handset has been tested.
OPTIONAL_SERVICES = {
    "AUDIO": """
        ABI PARENT_CLASS REGISTER_OBJECT READY UNREGISTER_OBJECT SEND
        DELETE_MESSAGE SOURCE_CREATE SOURCE_SHARE SOURCE_LIMITS SOURCE_ID
        SOURCE_FORGET SOURCE_EOF FRAGMENT_INIT SOURCE_WRITE CLASS_CALL VOLUME_SCALE
    """.split(),
    "VIDEO": """
        ABI LCD_WRITE_BYTES LCD_COMMAND LCD_READ_BYTE LCD_ABORT LCD_ARM
        LCD_READ_WORD LCD_GATE LCD_WINDOW LCD_TIMEOUT LCD_DMA_COMPLETE
        LCD_SET_COMPLETION LCD_PUBLIC_COMPLETE GPIO_PIN_MODE IRQ_UNLOCK IRQ_LOCK
        TIMER_START TIMER_STOP WATCHDOG_START DMA_START CLOCK_ENABLE CLOCK_RESET
        ISI_DELETE KEYBOARD_DECODE SCRATCHPAD_TAKE SCRATCHPAD_GIVE CM_SEND_SERVER
        LCD_STATE LCD_PENDING DISPLAY_HOOKS FOREGROUND_SLOT JAVA_SERVER
        JAVA_TASK_SLOT JAVA_TASK_DESCRIPTOR JAVA_QUEUE SCRATCHPAD_RESOURCES
        OS_RESOURCES PERIODIC_TIMER_STATE LCD_CONTROL GPIO JAVA_TASK_ID
        RESOURCE_COUNT PERIODIC_TIMER_ID RAM_START RAM_END
    """.split(),
    "BATTERY": """
        ABI CAPACITY NOMINAL BARS VOLTAGE CHARGER ESTIMATE CHARGE ACCOUNTED
        PAUSED COMPLETE USED_ENERGY EMPTY_ENERGY MODEL_INVALID
    """.split(),
    "BRIDGE": """
        ABI PANEL_POINTER ORIGINAL_CLASS ORIGINAL_RX CLASS_SIZES TASK_CONTEXTS
        DEVICE OBJECT_TASK MESSAGE_NEW MESSAGE_DELETE CALL_CLASS3
        RAM_START RAM_END ROM_START ROM_END
    """.split(),
}
CAPABILITY_ABIS = {
    "AUDIO_ABI_NONE": 0,
    "AUDIO_ABI_LEGACY": 1,
    "AUDIO_ABI_CLASSIC": 2,
    "AUDIO_ABI_DEVICE_IHF": 3,
    "AUDIO_ABI_DEVICE_MEDIA": 4,
    "VIDEO_ABI_NONE": 0,
    "VIDEO_ABI_RM443": 1,
    "BATTERY_ABI_NONE": 0,
    "BATTERY_ABI_RM443": 1,
    "BRIDGE_ABI_NONE": 0,
    "BRIDGE_ABI_RM443": 1,
}
# These four discovered helpers have ordinary native call veneers. Other
# dynamic entries use custom comparison veneers or are loader/data pointers.
DIRECT_INTERNAL_VENEERS = ("fw_msg_count", "fw_vm_timer", "fw_app_id", "fw_event_pop")
# C library and compiler support are linked into the payload. Only device
# services cross this ABI, so phones need not export a particular C runtime.


def main():
    build_dir = Path(sys.argv[1])
    build_dir.mkdir(parents=True, exist_ok=True)
    entries = []
    entries += [
        ("fw_" + n, n)
        for n in """
            os_block_alloc_no_wait os_block_dealloc os_current_time_read_in_ms
            os_task_own_id_read os_msg_receive filesys_open
            filesys_read filesys_write filesys_close
            filesys_lseek filesys_unlink filesys_mkdir
            gdi_display_master_reserve_enable
            gdi_display_master_reserve_disable gdi_display_master_update
            CM_ComMgr_ProcessPhonetMessage CM_ComMgr_ProcessInternalQueue
            CM_ComMgr_ScanISISubscriptions
            CM_Timer_Timeout
    """.split()
    ]
    export_count = len(entries)
    entries += [
        (n, "")
        for n in [
            "fw_msg_count",
            "fw_vm_timer",
            "fw_app_id",
            "fw_event_pop",
            "fw_lcd_owner",
            "cache_sync",
            "kni_param",
            "kni_return",
        ]
    ]
    required_count = len(entries)
    groups = {}
    for group, fields in OPTIONAL_SERVICES.items():
        groups[group + "_FIRST"] = len(entries)
        entries.extend((group.lower() + "_" + field.lower(), "") for field in fields)
        groups[group + "_LIMIT"] = len(entries)
    ids = {n: i for i, (n, _) in enumerate(entries)}
    asm = ["/* SPDX-License-Identifier: MIT */", ".syntax unified", ".arm", ".text"]
    bindings = []

    def slot(name, api):
        label = "__fwbind_" + name
        asm.extend([f".global {label}", f".hidden {label}", f"{label}: .word 0"])
        bindings.append(dict(symbol=label, api=ids[api], name=name))

    for n, _ in entries[:export_count] + [(name, "") for name in DIRECT_INTERNAL_VENEERS]:
        asm += [
            f'.section .text.{n},"ax",%progbits',
            ".balign 4",
            f".global {n}",
            f".hidden {n}",
            f".type {n}, %function",
            f"{n}:",
            "ldr ip, [pc, #0]",
            "bx ip",
        ]
        slot(n, n)
        asm += [f".size {n}, .-{n}"]
    asm += [
        '.section .data.fw_lcd_owner,"aw",%progbits',
        ".balign 4",
        ".global fw_lcd_owner",
        ".hidden fw_lcd_owner",
        "fw_lcd_owner:",
    ]
    slot("fw_lcd_owner", "fw_lcd_owner")
    (build_dir / "imports.S").write_text("\n".join(asm) + "\n")
    (build_dir / "imports.json").write_text(
        json.dumps(dict(entries=entries, bindings=bindings), indent=2) + "\n"
    )
    java = [
        "// SPDX-License-Identifier: MIT",
        "// Generated; firmware names and ABI indices, never addresses.",
        "final class FirmwareApi {",
        f"    static final int COUNT = {len(entries)};",
        f"    static final int REQUIRED_COUNT = {required_count};",
        f"    static final int EXPORT_COUNT = {export_count};",
        f"    static final int DESCRIPTOR = {API_DESCRIPTOR_WORD};",
        f"    static final int PATCH_MAGIC = 0x{LOADER_PATCH_MAGIC:08X};",
    ]
    header = [
        "/* SPDX-License-Identifier: MIT */",
        "/* Generated by gen_imports.py. */",
        "#ifndef ADAPTIVE_FIRMWARE_API_H",
        "#define ADAPTIVE_FIRMWARE_API_H",
        '#include "native_protocol.h"',
        f"#define FW_API_COUNT {len(entries)}",
        f"#define FW_API_REQUIRED_COUNT {required_count}",
        f"#define FW_API_DESCRIPTOR {API_DESCRIPTOR_WORD}",
    ]
    for name, word in CONFIG_WORDS.items():
        java.append(f"    static final int {name}_WORD = {word};")
        header.append(f"#define FW_{name}_WORD {word}")
    for name, value in {**INPUT_LAYOUTS, **CANVAS_LAYOUTS, **CAPABILITY_ABIS, **groups}.items():
        java.append(f"    static final int {name} = {value};")
        header.append(f"#define FW_{name} {value}")
    for n, i in ids.items():
        key = n.upper()
        java.append(f"    static final int {key} = {i};")
        header.append(f"#define API_{key} {i}")
    java += ["    static final String[] NAMES = new String[] {"]
    java += ['        "%s",' % symbol for _, symbol in entries]
    java += ["    };", "    static final String[] LABELS = new String[] {"]
    java += ['        "%s",' % name for name, _ in entries]
    java += ["    };", "}"]
    header += ["#endif"]
    protocol_header, protocol_java = generated_sources()
    # Both package-private classes live in this established build input, so
    # external ports do not need to duplicate a growing generated source list.
    (build_dir / "FirmwareApi.java").write_text("\n".join(java) + "\n" + protocol_java)
    (build_dir / "firmware_api.h").write_text("\n".join(header) + "\n")
    (build_dir / "native_protocol.h").write_text(protocol_header)


if __name__ == "__main__":
    main()
