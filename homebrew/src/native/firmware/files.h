/* SPDX-License-Identifier: MIT */
#ifndef S40_FIRMWARE_FILES_H
#define S40_FIRMWARE_FILES_H

/* filesys_open uses the native/newlib bit values below, not host O_* values.
 * The resolver supplies the firmware entry point; a host's <fcntl.h> is not
 * an ABI definition for the phone. The access field is exclusive, the other
 * flags combine. Paths are NUL-terminated UTF-16, including the drive prefix.
 */
enum fw_file_open_flags {
    FW_FILE_READ_ONLY = 0x000,
    FW_FILE_WRITE_ONLY = 0x001,
    FW_FILE_READ_WRITE = 0x002,
    FW_FILE_ACCESS_MASK = 0x003,
    FW_FILE_APPEND = 0x008,
    FW_FILE_CREATE = 0x100,
    FW_FILE_TRUNCATE = 0x200
};
enum {
    FW_FILE_OWNER_READ_WRITE = 0600,
    FW_FILE_PATH_UNITS = 256 /* Includes the terminating UTF-16 NUL. */
};

#endif
