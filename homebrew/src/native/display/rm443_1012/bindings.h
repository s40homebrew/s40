/* SPDX-License-Identifier: MIT */
/* One registry for ROM addresses and their 32-byte entry fingerprints. Calls
 * use the same named addresses as validation, including Thumb bit0. Entries
 * used only to validate an indirectly accessed layout have no callable binding.
 */
#ifndef S40_VIDEO_RM443_BINDINGS_H
#define S40_VIDEO_RM443_BINDINGS_H
#include <stdint.h>

#define RM443_VIDEO_ROM_ENTRIES(X)                     \
    X(lcd_write_bytes, 0xf02507a4u, 0x6fecf4b7u)       \
    X(lcd_command, 0xf0250840u, 0x62bf4d0cu)           \
    X(lcd_read_byte, 0xf0250868u, 0x5b9ee92au)         \
    X(lcd_abort, 0xf025087eu, 0xf32ba945u)             \
    X(lcd_arm, 0xf0250938u, 0x5739a10fu)               \
    X(lcd_read_word, 0xf02509e6u, 0xe20927cbu)         \
    X(lcd_gate, 0xf0250a38u, 0x94702ac8u)              \
    X(lcd_window, 0xf025106au, 0xbb39f27eu)            \
    X(lcd_timeout, 0xf0253846u, 0x314a4946u)           \
    X(lcd_dma_complete, 0xf025385cu, 0xa9ad70feu)      \
    X(stock_bus_enable, 0xf0253928u, 0xa934192fu)      \
    X(lcd_set_completion, 0xf0254170u, 0xb89d83e4u)    \
    X(lcd_public_complete, 0xf0254192u, 0x88380ec5u)   \
    X(gpio_pin_mode, 0xf02a30eau, 0xd0ced018u)         \
    X(irq_unlock, 0xf02a322cu, 0x4fc86668u)            \
    X(irq_lock, 0xf02a3234u, 0x9d0a5c14u)              \
    X(milliseconds, 0xf02d500au, 0xbd19f528u)          \
    X(timer_start, 0xf0316a94u, 0x2d334299u)           \
    X(timer_stop, 0xf0316bfeu, 0x2959ca5au)            \
    X(watchdog_start, 0xf037ad86u, 0x6403eadbu)        \
    X(dma_start, 0xf037bf6au, 0x6d9f2f7bu)             \
    X(clock_enable, 0xf037ccfeu, 0x03b405d0u)          \
    X(clock_reset, 0xf037cd90u, 0x5bc05c17u)           \
    X(isi_delete, 0xf0b9b868u, 0x7ca2635eu)            \
    X(keyboard_decode, 0xf0cfe4bau, 0xcc990b26u)       \
    X(scratchpad_take, 0xf0d4a744u, 0x9cd5645bu)       \
    X(scratchpad_give, 0xf0d4a760u, 0x6cd64846u)       \
    X(cm_process_internal, 0xf0b9e0eau, 0xd440f349u)   \
    X(cm_scan_subscriptions, 0xf0b9bc96u, 0xcdd10002u) \
    X(cm_dispatch_internal, 0xf0b9df36u, 0x66c6b153u)  \
    X(cm_pop_internal, 0xf042e714u, 0x633477d1u)       \
    X(cm_send_server, 0xf0b9cbc8u, 0x1de09f20u)        \
    X(cm_send_object, 0xf0b9ca78u, 0x32bc31f8u)        \
    X(cm_enqueue_packet, 0xf0b9c8a8u, 0x1288139cu)     \
    X(cm_dispatch_packet, 0xf0b9c9e2u, 0x98525758u)    \
    X(java_task_descriptor, 0xf1120c34u, 0xc9546728u)  \
    X(resource_reserve, 0xf04f001eu, 0x6472c9c9u)      \
    X(resource_release, 0xf04f01d8u, 0x4449b764u)

#define RM443_ROM_ADDRESS(name, address, hash) RM443_ROM_##name = (address),
enum { RM443_VIDEO_ROM_ENTRIES(RM443_ROM_ADDRESS) };
#undef RM443_ROM_ADDRESS

typedef int (*rm443_lcd_write_bytes_fn)(const void*, unsigned);
#define rm443_lcd_write_bytes \
    ((rm443_lcd_write_bytes_fn)(uintptr_t)(RM443_ROM_lcd_write_bytes | 1u))
typedef int (*rm443_lcd_command_fn)(unsigned);
#define rm443_lcd_command ((rm443_lcd_command_fn)(uintptr_t)(RM443_ROM_lcd_command | 1u))
typedef int (*rm443_lcd_read_byte_fn)(uint8_t*);
#define rm443_lcd_read_byte ((rm443_lcd_read_byte_fn)(uintptr_t)(RM443_ROM_lcd_read_byte | 1u))
typedef void (*rm443_lcd_abort_fn)(void);
#define rm443_lcd_abort ((rm443_lcd_abort_fn)(uintptr_t)(RM443_ROM_lcd_abort | 1u))
typedef int (*rm443_lcd_arm_fn)(unsigned, unsigned, const void*);
#define rm443_lcd_arm ((rm443_lcd_arm_fn)(uintptr_t)(RM443_ROM_lcd_arm | 1u))
typedef int (*rm443_lcd_read_word_fn)(uint32_t*);
#define rm443_lcd_read_word ((rm443_lcd_read_word_fn)(uintptr_t)(RM443_ROM_lcd_read_word | 1u))
typedef int (*rm443_lcd_gate_fn)(unsigned);
#define rm443_lcd_gate ((rm443_lcd_gate_fn)(uintptr_t)(RM443_ROM_lcd_gate | 1u))
typedef int (*rm443_lcd_window_fn)(unsigned, unsigned, unsigned, unsigned);
#define rm443_lcd_window ((rm443_lcd_window_fn)(uintptr_t)(RM443_ROM_lcd_window | 1u))
typedef void (*rm443_lcd_timeout_fn)(void);
#define rm443_lcd_timeout ((rm443_lcd_timeout_fn)(uintptr_t)(RM443_ROM_lcd_timeout | 1u))
typedef void (*rm443_lcd_dma_complete_fn)(void);
#define rm443_lcd_dma_complete \
    ((rm443_lcd_dma_complete_fn)(uintptr_t)(RM443_ROM_lcd_dma_complete | 1u))
typedef int (*rm443_lcd_set_completion_fn)(int, void (*callback)(int));
#define rm443_lcd_set_completion \
    ((rm443_lcd_set_completion_fn)(uintptr_t)(RM443_ROM_lcd_set_completion | 1u))
typedef void (*rm443_lcd_public_complete_fn)(void);
#define rm443_lcd_public_complete \
    ((rm443_lcd_public_complete_fn)(uintptr_t)(RM443_ROM_lcd_public_complete | 1u))
typedef int (*rm443_gpio_pin_mode_fn)(unsigned, unsigned);
#define rm443_gpio_pin_mode ((rm443_gpio_pin_mode_fn)(uintptr_t)(RM443_ROM_gpio_pin_mode | 1u))
typedef void (*rm443_irq_unlock_fn)(void);
#define rm443_irq_unlock ((rm443_irq_unlock_fn)(uintptr_t)(RM443_ROM_irq_unlock | 1u))
typedef void (*rm443_irq_lock_fn)(void);
#define rm443_irq_lock ((rm443_irq_lock_fn)(uintptr_t)(RM443_ROM_irq_lock | 1u))
typedef uint32_t (*rm443_milliseconds_fn)(void);
#define rm443_milliseconds ((rm443_milliseconds_fn)(uintptr_t)(RM443_ROM_milliseconds | 1u))
typedef int (*rm443_timer_start_fn)(unsigned, unsigned);
#define rm443_timer_start ((rm443_timer_start_fn)(uintptr_t)(RM443_ROM_timer_start | 1u))
typedef uint32_t (*rm443_timer_stop_fn)(uint32_t);
#define rm443_timer_stop ((rm443_timer_stop_fn)(uintptr_t)(RM443_ROM_timer_stop | 1u))
typedef unsigned (*rm443_watchdog_start_fn)(void (*callback)(void), unsigned, unsigned);
#define rm443_watchdog_start ((rm443_watchdog_start_fn)(uintptr_t)(RM443_ROM_watchdog_start | 1u))
typedef unsigned (*rm443_dma_start_fn)(const void*, unsigned, unsigned, void*);
#define rm443_dma_start ((rm443_dma_start_fn)(uintptr_t)(RM443_ROM_dma_start | 1u))
typedef int (*rm443_clock_enable_fn)(unsigned, unsigned, unsigned);
#define rm443_clock_enable ((rm443_clock_enable_fn)(uintptr_t)(RM443_ROM_clock_enable | 1u))
typedef int (*rm443_clock_reset_fn)(unsigned, unsigned, unsigned);
#define rm443_clock_reset ((rm443_clock_reset_fn)(uintptr_t)(RM443_ROM_clock_reset | 1u))
typedef void (*rm443_isi_delete_fn)(const void*);
#define rm443_isi_delete ((rm443_isi_delete_fn)(uintptr_t)(RM443_ROM_isi_delete | 1u))
typedef void (*rm443_keyboard_decode_fn)(const void*, unsigned);
#define rm443_keyboard_decode \
    ((rm443_keyboard_decode_fn)(uintptr_t)(RM443_ROM_keyboard_decode | 1u))
typedef int (*rm443_scratchpad_take_fn)(unsigned);
#define rm443_scratchpad_take \
    ((rm443_scratchpad_take_fn)(uintptr_t)(RM443_ROM_scratchpad_take | 1u))
typedef int (*rm443_scratchpad_give_fn)(unsigned);
#define rm443_scratchpad_give \
    ((rm443_scratchpad_give_fn)(uintptr_t)(RM443_ROM_scratchpad_give | 1u))
typedef void (*rm443_cm_send_server_fn)(unsigned, void*, void*);
#define rm443_cm_send_server ((rm443_cm_send_server_fn)(uintptr_t)(RM443_ROM_cm_send_server | 1u))

#endif
