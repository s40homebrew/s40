/* SPDX-License-Identifier: MIT */
/* SDK FILE objects wrap firmware descriptors; neither Java streams nor newlib
 * FILE objects cross this boundary. Firmware descriptor zero is valid. */
#include <errno.h>
#include <limits.h>
#include <stdlib.h>
#include <string.h>
#include "firmware/bindings.h"
#include "firmware/files.h"
#include "internal.h"
#include "path.h"
#include "s40/runtime.h"

struct s40_file {
    int fd, eof, error;
    struct s40_file* next;
};
static FILE *files, *logfile;
static FILE console_out = {-1, 0, 0, NULL}, console_err = {-2, 0, 0, NULL},
            console_in = {-3, 0, 0, NULL};
FILE *stdout = &console_out, *stderr = &console_err, *stdin = &console_in;
enum { ROOT_PATH_BYTES = 96, LOG_LINE_BYTES = 1024, COPY_BUFFER_BYTES = 4096 };
static char root[ROOT_PATH_BYTES] = "C:\\";
void s40_set_root(const char* s) { snprintf(root, sizeof(root), "%s", s); }
const char* s40_get_root(void) { return root; }
static int path16(const char* s, uint16_t* out) {
    if (!s40_path16(root, s, out, FW_FILE_PATH_UNITS)) {
        errno = EINVAL;
        return 0;
    }
    return 1;
}
FILE* fopen(const char* path, const char* mode) {
    uint16_t p[FW_FILE_PATH_UNITS];
    if (!path16(path, p))
        return NULL;
    unsigned flags;
    if (!mode || !*mode) {
        errno = EINVAL;
        return NULL;
    }
    if (*mode == 'r')
        flags = FW_FILE_READ_ONLY;
    else if (*mode == 'w')
        flags = FW_FILE_WRITE_ONLY | FW_FILE_CREATE | FW_FILE_TRUNCATE;
    else if (*mode == 'a')
        flags = FW_FILE_WRITE_ONLY | FW_FILE_CREATE | FW_FILE_APPEND;
    else {
        errno = EINVAL;
        return NULL;
    }
    if (strchr(mode, '+'))
        flags = (flags & ~FW_FILE_ACCESS_MASK) | FW_FILE_READ_WRITE;
    int fd = fw_filesys_open(p, flags, FW_FILE_OWNER_READ_WRITE);
    // Zero is a valid firmware descriptor (including on RM-217).
    if (fd < 0) {
        errno = ENOENT;
        return NULL;
    }
    FILE* f = calloc(1, sizeof(*f));
    if (!f) {
        fw_filesys_close(fd);
        return NULL;
    }
    f->fd = fd;
    f->next = files;
    files = f;
    return f;
}
int fclose(FILE* f) {
    if (!f || f->fd < 0)
        return EOF;
    FILE** p = &files;
    while (*p && *p != f)
        p = &(*p)->next;
    if (!*p)
        return EOF;
    *p = f->next;
    int r = fw_filesys_close(f->fd);
    if (logfile == f)
        logfile = NULL;
    free(f);
    return r < 0 ? EOF : 0;
}
size_t fread(void* p, size_t s, size_t n, FILE* f) {
    if (!s || !n || !f || f->fd < 0 || n > INT_MAX / s)
        return 0;
    unsigned bytes = s * n, done = 0;
    while (done < bytes) {
        int r = fw_filesys_read(f->fd, (char*)p + done, bytes - done);
        if (r <= 0) {
            if (r < 0)
                f->error = 1;
            else
                f->eof = 1;
            break;
        }
        done += r;
    }
    return done / s;
}
size_t fwrite(const void* p, size_t s, size_t n, FILE* f) {
    if (!s || !n || !f || n > INT_MAX / s)
        return 0;
    if (f == stdout || f == stderr) {
        if (logfile)
            return fwrite(p, s, n, logfile);
        return n;
    }
    if (f->fd < 0)
        return 0;
    unsigned bytes = s * n, done = 0;
    while (done < bytes) {
        int r = fw_filesys_write(f->fd, (const char*)p + done, bytes - done);
        if (r <= 0) {
            f->error = 1;
            break;
        }
        done += r;
    }
    return done / s;
}
int fseek(FILE* f, long off, int whence) {
    if (!f || f->fd < 0)
        return -1;
    int r = fw_filesys_lseek(f->fd, off, whence);
    if (r < 0) {
        f->error = 1;
        return -1;
    }
    f->eof = 0;
    return 0;
}
long ftell(FILE* f) { return !f || f->fd < 0 ? -1 : fw_filesys_lseek(f->fd, 0, SEEK_CUR); }
int fflush(FILE* f) {
    (void)f;
    return 0;
} /* writes are unbuffered */
int feof(FILE* f) { return f->eof; }
int ferror(FILE* f) { return f->error; }
int fgetc(FILE* f) {
    unsigned char c;
    return fread(&c, 1, 1, f) == 1 ? c : EOF;
}
int fputc(int c, FILE* f) {
    unsigned char b = c;
    return fwrite(&b, 1, 1, f) == 1 ? b : EOF;
}
char* fgets(char* p, int n, FILE* f) {
    int i = 0, c;
    while (i < n - 1 && (c = fgetc(f)) != EOF) {
        p[i++] = c;
        if (c == '\n')
            break;
    }
    p[i] = 0;
    return i ? p : NULL;
}
int fputs(const char* s, FILE* f) {
    size_t n = strlen(s);
    return fwrite(s, 1, n, f) == n ? 0 : EOF;
}
int vfprintf(FILE* f, const char* fmt, va_list ap) {
    char buf[LOG_LINE_BYTES];
    int n = vsnprintf(buf, sizeof(buf), fmt, ap);
    if (n < 0)
        return n;
    fwrite(buf, 1, (unsigned)n < sizeof(buf) ? (unsigned)n : sizeof(buf) - 1, f);
    return n;
}
int fprintf(FILE* f, const char* fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    int n = vfprintf(f, fmt, ap);
    va_end(ap);
    return n;
}
int vprintf(const char* fmt, va_list ap) { return vfprintf(stdout, fmt, ap); }
int printf(const char* fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    int n = vprintf(fmt, ap);
    va_end(ap);
    return n;
}
int putchar(int c) { return fputc(c, stdout); }
int puts(const char* s) {
    fputs(s, stdout);
    return putchar('\n');
}
int remove(const char* s) {
    uint16_t p[FW_FILE_PATH_UNITS];
    return path16(s, p) ? fw_filesys_unlink(p) : -1;
}
int mkdir(const char* s, unsigned mode) {
    uint16_t p[FW_FILE_PATH_UNITS];
    return path16(s, p) ? fw_filesys_mkdir(p, mode) : -1;
}
/* Firmware rename is not exported. Copy+close+unlink keeps every byte native;
 * not atomic on power loss. Callers must account for a partial copy. */
int rename(const char* a, const char* b) {
    uint16_t source_path[FW_FILE_PATH_UNITS], target_path[FW_FILE_PATH_UNITS];
    if (!path16(a, source_path) || !path16(b, target_path))
        return -1;
    FILE* in = fopen(a, "rb");
    if (!in)
        return -1;
    /* Avoid truncating our own source, including slash and ./ aliases already
     * normalized by path16. Resolve existence before accepting the no-op. */
    unsigned i = 0;
    while (source_path[i] && source_path[i] == target_path[i])
        ++i;
    if (source_path[i] == target_path[i])
        return fclose(in);
    FILE* out = fopen(b, "wb");
    if (!out) {
        fclose(in);
        return -1;
    }
    char buf[COPY_BUFFER_BYTES];
    size_t n;
    int ok = 1;
    while ((n = fread(buf, 1, sizeof(buf), in)))
        if (fwrite(buf, 1, n, out) != n) {
            ok = 0;
            break;
        }
    if (ferror(in))
        ok = 0;
    fclose(in);
    if (fclose(out))
        ok = 0;
    return ok ? remove(a) : -1;
}
void s40_set_log(FILE* f) { logfile = f; }
void s40_log_close(void) {
    if (logfile)
        fclose(logfile);
}
void s40_files_cleanup(void) {
    while (files)
        fclose(files);
}
