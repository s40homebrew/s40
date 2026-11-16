/* SPDX-License-Identifier: MIT */
#include "path.h"
#include "firmware/files.h"

#include <stdio.h>

int s40_path16(const char* root, const char* path, uint16_t* output, size_t capacity) {
    /* Three UTF-8 bytes per UTF-16 unit is the largest encoded path. */
    char full[FW_FILE_PATH_UNITS * 3];
    int length;
    if (!root || !path || !*path || !output || !capacity)
        return 0;
    if (path[1] == ':') {
        length = snprintf(full, sizeof(full), "%s", path);
    } else {
        while (path[0] == '.' && (path[1] == '/' || path[1] == '\\'))
            path += 2;
        length = snprintf(full, sizeof(full), "%s%s", root, path);
    }
    if (length < 0 || (size_t)length >= sizeof(full))
        return 0;
    const unsigned char* input = (const unsigned char*)full;
    size_t used = 0;
    while (*input) {
        uint32_t code = *input++;
        unsigned extra = 0;
        uint32_t minimum = 0;
        if (code >= 0xC2 && code <= 0xDF) {
            extra = 1;
            minimum = 0x80;
            code &= 0x1F;
        } else if (code >= 0xE0 && code <= 0xEF) {
            extra = 2;
            minimum = 0x800;
            code &= 0x0F;
        } else if (code >= 0xF0 && code <= 0xF4) {
            extra = 3;
            minimum = 0x10000;
            code &= 7;
        } else if (code >= 0x80) {
            return 0;
        }
        while (extra--) {
            unsigned next = *input;
            if (next < 0x80 || next > 0xBF)
                return 0;
            ++input;
            code = (code << 6) | (next & 0x3F);
        }
        if (code < minimum || code > 0x10FFFF || (code >= 0xD800 && code <= 0xDFFF))
            return 0;
        size_t units = code >= 0x10000 ? 2 : 1;
        if (used >= capacity || units >= capacity - used)
            return 0;
        if (units == 2) {
            code -= 0x10000;
            output[used++] = 0xD800 | (code >> 10);
            output[used++] = 0xDC00 | (code & 0x3FF);
        } else {
            output[used++] = code == '/' ? '\\' : code;
        }
    }
    output[used] = 0;
    return 1;
}
