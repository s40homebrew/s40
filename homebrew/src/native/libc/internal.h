/* SPDX-License-Identifier: MIT */
#ifndef S40_LIBC_INTERNAL_H
#define S40_LIBC_INTERNAL_H

/* Session-owned resources, not firmware entry points. Call after display and
 * audio release; FILE objects must close before their tracked allocations. */
void s40_cleanup(void);
void s40_files_cleanup(void);
void s40_memory_cleanup(void);

#endif
