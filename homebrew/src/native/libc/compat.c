/* SPDX-License-Identifier: MIT */
/* Freestanding C compatibility helpers. Memory and file ownership live in
 * memory.c and files.c; newlib supplies formatting, parsing and arithmetic. */
#include <ctype.h>
#include <errno.h>
#include <limits.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>

#include "internal.h"
#include "s40/runtime.h"
int errno;
int isupper(int c) { return c >= 'A' && c <= 'Z'; }
int islower(int c) { return c >= 'a' && c <= 'z'; }
int isalpha(int c) { return isupper(c) || islower(c); }
int isdigit(int c) { return c >= '0' && c <= '9'; }
int isprint(int c) { return c >= 32 && c < 127; }
int isspace(int c) { return c == ' ' || (c >= '\t' && c <= '\r'); }
int tolower(int c) { return isupper(c) ? c + 32 : c; }
int toupper(int c) { return islower(c) ? c - 32 : c; }
char* strdup(const char* s) {
    size_t n = strlen(s) + 1;
    char* p = malloc(n);
    if (p)
        memcpy(p, s, n);
    return p;
}
char* strrchr(const char* s, int c) {
    const char* p = NULL;
    do {
        if (*s == (char)c)
            p = s;
    } while (*s++);
    return (char*)p;
}
int strncasecmp(const char* a, const char* b, size_t n) {
    while (n--) {
        int x = tolower((unsigned char)*a++), y = tolower((unsigned char)*b++);
        if (x != y || !x)
            return x - y;
    }
    return 0;
}
int strcasecmp(const char* a, const char* b) { return strncasecmp(a, b, UINT_MAX); }
char* getenv(const char* s) {
    (void)s;
    return NULL;
}
double atof(const char* s) { return strtod(s, NULL); }
char* strerror(int e) {
    (void)e;
    return "native file error";
}
void exit(int n) { s40_finish(n); }
void abort(void) { s40_finish(-99); }

/* Close files first: each FILE is itself a tracked allocation. */
void s40_cleanup(void) {
    s40_files_cleanup();
    s40_memory_cleanup();
}
