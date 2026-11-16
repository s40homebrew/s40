/* SPDX-License-Identifier: MIT */
ENTRY(bridge_main)
SECTIONS {
    . = 0;
    .text : { KEEP(*(.text.entry)) *(.text*) }
    /* A raw blob carries no relocations. Data references and runtime imports
     * need a loader, so deliberately reject them in this small example build. */
    .unsupported : { *(.data*) *(.bss*) *(.rodata*) *(.got*) }
    ASSERT(SIZEOF(.unsupported) == 0, "Payload needs data/relocations; use returning code with register arguments")
    /DISCARD/ : { *(.ARM.exidx*) *(.ARM.extab*) *(.comment) *(.note*) }
}
