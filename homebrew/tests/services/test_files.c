/* SPDX-License-Identifier: MIT */
/* Exercise real file/allocator wrappers with bounded firmware services. */
#include <assert.h>
#include <limits.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

typedef struct s40_file S40_FILE;
void *sdk_malloc(size_t), *sdk_calloc(size_t, size_t), *sdk_realloc(void*, size_t);
void sdk_free(void*);
S40_FILE* s40_fopen(const char*, const char*);
int s40_fclose(S40_FILE*), s40_ferror(S40_FILE*), s40_feof(S40_FILE*);
size_t s40_fread(void*, size_t, size_t, S40_FILE*);
size_t s40_fwrite(const void*, size_t, size_t, S40_FILE*);
int s40_fseek(S40_FILE*, long, int);
int s40_rename(const char*, const char*);
void s40_set_root(const char*);
void s40_set_log(S40_FILE*);
void s40_log_close(void);
void s40_files_cleanup(void), s40_memory_cleanup(void);
int sdk_errno;
static unsigned allocations, live_files, opens, closes, last_flags, last_mode, unlinks;
static int allocation_fails, read_fails, write_fails;
static uint16_t last_path[256];
static unsigned char data[64];
static unsigned position, length;

void* fw_os_block_alloc_no_wait(unsigned n) {
    if (allocation_fails) return NULL;
    void* p = malloc(n);
    assert(p);
    ++allocations;
    return p;
}
void fw_os_block_dealloc(void* p) { assert(allocations); --allocations; free(p); }
int fw_filesys_open(const uint16_t* p, unsigned flags, unsigned mode) {
    unsigned i = 0;
    do { last_path[i] = p[i]; } while (p[i++] && i < 256);
    last_flags = flags; last_mode = mode; position = 0; ++live_files; ++opens;
    return 0; /* Regression: fd zero must never be confused with failure. */
}
int fw_filesys_close(int fd) { assert(fd == 0 && live_files); --live_files; ++closes; return 0; }
int fw_filesys_read(int fd, void* p, unsigned n) {
    assert(fd == 0);
    if (read_fails) return -1;
    if (n > length - position) n = length - position;
    if (n > 3) n = 3; /* Short reads must be accumulated. */
    memcpy(p, data + position, n); position += n; return n;
}
int fw_filesys_write(int fd, const void* p, unsigned n) {
    assert(fd == 0);
    if (write_fails) return -1;
    if (n > 2) n = 2;
    assert(position + n <= sizeof(data));
    memcpy(data + position, p, n); position += n;
    if (position > length) length = position;
    return n;
}
int fw_filesys_lseek(int fd, int off, int whence) {
    assert(fd == 0);
    if (whence == 0 && off >= 0 && (unsigned)off <= length) position = off;
    else if (whence != 1 || off) return -1;
    return position;
}
int fw_filesys_unlink(const uint16_t* p) { (void)p; ++unlinks; return 0; }
int fw_filesys_mkdir(const uint16_t* p, unsigned mode) { (void)p; (void)mode; return 0; }

int main(void) {
    unsigned char* p = sdk_malloc(7);
    assert(p && (uintptr_t)p % 8 == 0);
    memset(p, 0x5a, 7);
    allocation_fails = 1;
    assert(!sdk_realloc(p, 40) && p[6] == 0x5a);
    allocation_fails = 0;
    p = sdk_realloc(p, 40);
    assert(p && p[6] == 0x5a);
    assert(!sdk_calloc(UINT_MAX, 2));
    sdk_free(p);
    s40_set_root("C:/gallery/");
    S40_FILE* f = s40_fopen("save.bin", "wb+");
    assert(f && last_flags == 0x302 && last_mode == 0600);
    assert(last_path[2] == '\\' && last_path[11] == 's');
    assert(s40_fwrite("abcdefgh", 2, 4, f) == 4);
    assert(s40_fseek(f, 0, 0) == 0);
    unsigned char out[12] = {0};
    assert(s40_fread(out, 2, 6, f) == 4 && s40_feof(f));
    assert(!memcmp(out, "abcdefgh", 8));
    assert(s40_fseek(f, 0, 0) == 0 && !s40_feof(f));
    read_fails = 1;
    assert(!s40_fread(out, 1, 1, f) && s40_ferror(f));
    read_fails = 0;
    s40_set_log(f);
    s40_log_close();
    s40_log_close(); /* Already closed: no double free or close. */
    assert(!live_files && !allocations);
    unsigned before_rename = opens;
    assert(!s40_rename("save.bin", "./save.bin"));
    assert(opens == before_rename + 1 && !unlinks && !live_files);
    assert(!memcmp(data, "abcdefgh", 8));
    allocation_fails = 1;
    assert(!s40_fopen("save.bin", "rb") && !live_files);
    allocation_fails = 0;
    unsigned before = opens;
    assert(!s40_fopen("\xc0\xaf", "r") && opens == before);
    assert(!s40_fopen("save.bin", NULL) && opens == before);
    f = s40_fopen("save.bin", "ab");
    assert(f && last_flags == 0x109);
    write_fails = 1;
    assert(!s40_fwrite("x", 1, 1, f) && s40_ferror(f));
    s40_files_cleanup();
    assert(!live_files && !allocations && opens == closes);
    assert(sdk_malloc(31) && sdk_calloc(2, 8));
    s40_memory_cleanup();
    assert(!allocations);
    return 0;
}
