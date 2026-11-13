/* SPDX-License-Identifier: MIT */
/* RAPU's BSE helpers supply mapping and physical RAM information. Calling the
 * audited helpers also keeps the D0000000 table alias and boot-time RAM-bank
 * selection in firmware. No build-specific function/global address is stored.
 * See ../docs/porting.md and the RM-470/RM-813 analyses for instruction traces.
 */
static const unsigned char rapu_walker[] = {
        0xf7, 0xb5, 0x82, 0xb0, 0x00, 0x25, 0xed, 0x43, 0x06, 0x46, 0x03, 0xf0, 0xdb, 0xfd, 0x07,
        0x46, 0x03, 0xf0, 0xcc, 0xfd, 0xc0, 0x07, 0x03, 0xd0, 0x0d, 0x20, 0x00, 0x07, 0xc4, 0x1b,
        0x00, 0xe0, 0x00, 0x24, 0x03, 0xf0, 0xef, 0xfd, 0x01, 0x90, 0x80, 0x08, 0x80, 0x00, 0x40,
        0x1c, 0x03, 0xf0, 0xef, 0xfd, 0x04, 0xf0, 0x8a, 0xe8, 0x31, 0x0d, 0x38, 0x19, 0x89, 0x00,
        0x40, 0x58, 0x03, 0x99, 0x08, 0x60, 0x81, 0x07, 0x89, 0x0f, 0x01, 0x29, 0x03, 0xd0, 0x02,
        0x29, 0x0b, 0xd1, 0x00, 0x25, 0x09, 0xe0, 0x80, 0x0a, 0x31, 0x03, 0x80, 0x02, 0x09, 0x0e,
        0x00, 0x19, 0x89, 0x00, 0x01, 0x25, 0x41, 0x58, 0x04, 0x98, 0x01, 0x60, 0x01, 0x98, 0x03,
        0xf0, 0xd2, 0xfd, 0x05, 0xb0, 0x28, 0x46, 0xf0, 0xbd};
static const u32 rapu_calls[] = {10, 16, 34, 46, 50, 104};
static const u32 rapu_helpers[] = {0xee120f10, 0xee110f10, 0xee130f10,
                                   0xee030f10, 0xee071f9a, 0xee030f10};
static const u16 tag_lookup[] = {0xb570, 0x460e, 0x4615, 0x4604, 0x07a0, 0xd001, 0,
                                 0,      0x6820, 0x42b0, 0xd103, 0x1e6d, 0xd201, 0x4620,
                                 0xbd70, 0x2802, 0xd006, 0x2803, 0xd101, 0x68a4, 0xe7ee,
                                 0x6860, 0x1904, 0xe7eb, 0x2000, 0xbd70};
static u32 thumb_call(u32 pc, int arm) {
    const volatile u16* p = (const volatile u16*)pc;
    if ((p[0] & 0xf800) != 0xf000 || (p[1] & 0xf800) != (arm ? 0xe800 : 0xf800))
        return 0;
    int offset = ((p[0] & 2047) << 12) | ((p[1] & 2047) << 1);
    if (offset & 0x400000)
        offset -= 0x800000;
    u32 target = pc + 4 + offset;
    return arm ? target & ~3u : target;
}
static int rapu_discover(volatile u32* r) {
    u32 begin = r[W_ROM_START], end = begin + 65536, walker = 0, tags = 0;
    for (u32 p = begin; p < end - 128; p += 2) {
        const volatile u16* h = (const volatile u16*)p;
        if (h[0] == 0xb5f7 && h[1] == 0xb082) {
            u32 i;
            for (i = 0; i < sizeof(rapu_walker); i++) {
                int masked = 0;
                for (u32 j = 0; j < 6; j++)
                    if (i >= rapu_calls[j] && i < rapu_calls[j] + 4)
                        masked = 1;
                if (!masked && ((const volatile unsigned char*)p)[i] != rapu_walker[i])
                    break;
            }
            if (i != sizeof(rapu_walker))
                continue;
            u32 writer = 0;
            for (i = 0; i < 6; i++) {
                u32 helper = thumb_call(p + rapu_calls[i], i == 4);
                if (helper < begin || helper > end - 16 || (helper & 3))
                    break;
                if (i != 4) {
                    if (*(const volatile u32*)helper != 0x46c04778)
                        break;
                    helper += 4;
                }
                const volatile u32* code = (const volatile u32*)helper;
                if (code[0] != rapu_helpers[i])
                    break;
                // TTBR reader strips cacheability attributes before returning.
                if (i == 0 ? (code[1] != 0xe3c0001f || code[2] != 0xe12fff1e)
                           : code[1] != 0xe12fff1e)
                    break;
                if (i == 3)
                    writer = helper;
                if (i == 5 && writer != helper)
                    break;
            }
            if (i == 6) {
                if (walker)
                    return 0;
                walker = p;
            }
        }
        if (h[0] == 0x4603 && (h[1] & 0xff00) == 0x4800 && h[2] == 0x6800 && h[3] == 0x2800 &&
            h[4] == 0xd002 && h[5] == 0x460a && h[6] == 0x4619 && (h[7] & 0xf800) == 0xe000 &&
            h[8] == 0x4770) {
            u32 literal = ((p + 6) & ~3u) + 4 * (h[1] & 255);
            if (literal < begin || literal > end - 4)
                continue;
            u32 global = *(const volatile u32*)literal;
            if ((global & 3) || global < r[W_RAM_START] || global > r[W_RAM_END] - 4)
                continue;
            int offset = h[7] & 2047;
            if (offset & 1024)
                offset -= 2048;
            u32 body = p + 18 + 2 * offset;
            if (body < begin || body > end - sizeof(tag_lookup))
                continue;
            const volatile u16* code = (const volatile u16*)body;
            u32 i;
            for (i = 0; i < sizeof(tag_lookup) / 2; i++)
                if (i != 6 && i != 7 && code[i] != tag_lookup[i])
                    break;
            if (i == sizeof(tag_lookup) / 2) {
                if (tags)
                    return 0;
                tags = p | 1;
            }
        }
    }
    r[W_WALKER] = walker;
    r[W_RAM_TAGS] = tags;
    return walker && tags;
}
static u32 rapu_translate(volatile u32* r, u32 va, u32* domain_id) {
    u32 l1 = 0, l2 = 0;
    int kind = ((int (*)(u32, u32*, u32*))(r[W_WALKER] | 1))(va, &l1, &l2);
    r[W_L1] = l1;
    r[W_L2] = l2;
    *domain_id = (l1 >> 5) & 15;
    if (kind == 0 && (l1 & 3) == 2 && !(l1 & (1u << 18)))
        return (l1 & 0xfff00000) | (va & 0xfffff);
    if (kind != 1 || (l1 & 3) != 1)
        return 0xffffffffu;
    if ((l2 & 3) == 1)
        return (l2 & 0xffff0000) | (va & 65535);
    if ((l2 & 3) == 2 || ((r[W_SCTLR] & (1u << 23)) && (l2 & 3) == 3))
        return (l2 & 0xfffff000) | (va & 4095);
    return 0xffffffffu;
}
static int rapu_ram(volatile u32* r, u32 address, u32 bytes) {
    // Type 0 BSE records are physical RAM banks: kind, record size, byte size,
    // physical base. The firmware's bounded list lookup follows link records.
    // Unlike launch quotas this reflects boot-time board configuration.
    for (u32 i = 0; i < 16; i++) {
        const u32* tag = ((const u32* (*)(u32, u32))r[W_RAM_TAGS])(0, i);
        if (!tag)
            return 0;
        if (tag[0] != 0 || tag[1] < 16 || (tag[1] & 3))
            return 0;
        u32 size = tag[2], base = tag[3];
        if (bytes <= size && address >= base && address - base <= size - bytes) {
            r[W_PHYSICAL_START] = base;
            r[W_PHYSICAL_END] = base + size;
            return 1;
        }
    }
    return 0;
}
