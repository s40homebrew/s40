/* SPDX-License-Identifier: MIT */
#ifndef S40_NATIVE_PATH_H
#define S40_NATIVE_PATH_H
#include <stddef.h>
#include <stdint.h>

/* Convert a root-relative or drive-absolute UTF-8 path to firmware UTF-16.
 * Returns zero for malformed text, overlong paths or an empty path.
 */
int s40_path16(const char* root, const char* path, uint16_t* output, size_t capacity);
#endif
