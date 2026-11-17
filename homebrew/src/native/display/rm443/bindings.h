/* SPDX-License-Identifier: MIT */
/* Typed views of the shared resolver's optional video capability. Java validates
 * instruction layouts and call relationships before publishing VIDEO_ABI.
 * These slots are immutable for the duration of the native session. */
#ifndef S40_VIDEO_RM443_BINDINGS_H
#define S40_VIDEO_RM443_BINDINGS_H
#include <stdint.h>

#include "firmware/bindings.h"
#include "s40/runtime.h"
#define RM443_BINDING(name) (s40_result[FW_API_DESCRIPTOR + API_VIDEO_##name])

typedef int (*rm443_lcd_write_bytes_fn)(const void*, unsigned);
#define rm443_lcd_write_bytes ((rm443_lcd_write_bytes_fn)(uintptr_t)RM443_BINDING(LCD_WRITE_BYTES))
typedef int (*rm443_lcd_command_fn)(unsigned);
#define rm443_lcd_command ((rm443_lcd_command_fn)(uintptr_t)RM443_BINDING(LCD_COMMAND))
typedef int (*rm443_lcd_read_byte_fn)(uint8_t*);
#define rm443_lcd_read_byte ((rm443_lcd_read_byte_fn)(uintptr_t)RM443_BINDING(LCD_READ_BYTE))
typedef void (*rm443_lcd_abort_fn)(void);
#define rm443_lcd_abort ((rm443_lcd_abort_fn)(uintptr_t)RM443_BINDING(LCD_ABORT))
typedef int (*rm443_lcd_arm_fn)(unsigned, unsigned, const void*);
#define rm443_lcd_arm ((rm443_lcd_arm_fn)(uintptr_t)RM443_BINDING(LCD_ARM))
typedef int (*rm443_lcd_read_word_fn)(uint32_t*);
#define rm443_lcd_read_word ((rm443_lcd_read_word_fn)(uintptr_t)RM443_BINDING(LCD_READ_WORD))
typedef int (*rm443_lcd_gate_fn)(unsigned);
#define rm443_lcd_gate ((rm443_lcd_gate_fn)(uintptr_t)RM443_BINDING(LCD_GATE))
typedef int (*rm443_lcd_window_fn)(unsigned, unsigned, unsigned, unsigned);
#define rm443_lcd_window ((rm443_lcd_window_fn)(uintptr_t)RM443_BINDING(LCD_WINDOW))
typedef void (*rm443_lcd_timeout_fn)(void);
#define rm443_lcd_timeout ((rm443_lcd_timeout_fn)(uintptr_t)RM443_BINDING(LCD_TIMEOUT))
typedef void (*rm443_lcd_dma_complete_fn)(void);
#define rm443_lcd_dma_complete \
    ((rm443_lcd_dma_complete_fn)(uintptr_t)RM443_BINDING(LCD_DMA_COMPLETE))
typedef int (*rm443_lcd_set_completion_fn)(int, void (*callback)(int));
#define rm443_lcd_set_completion \
    ((rm443_lcd_set_completion_fn)(uintptr_t)RM443_BINDING(LCD_SET_COMPLETION))
typedef void (*rm443_lcd_public_complete_fn)(void);
#define rm443_lcd_public_complete \
    ((rm443_lcd_public_complete_fn)(uintptr_t)RM443_BINDING(LCD_PUBLIC_COMPLETE))
typedef int (*rm443_gpio_pin_mode_fn)(unsigned, unsigned);
#define rm443_gpio_pin_mode ((rm443_gpio_pin_mode_fn)(uintptr_t)RM443_BINDING(GPIO_PIN_MODE))
typedef void (*rm443_irq_unlock_fn)(void);
#define rm443_irq_unlock ((rm443_irq_unlock_fn)(uintptr_t)RM443_BINDING(IRQ_UNLOCK))
typedef void (*rm443_irq_lock_fn)(void);
#define rm443_irq_lock ((rm443_irq_lock_fn)(uintptr_t)RM443_BINDING(IRQ_LOCK))
#define rm443_milliseconds fw_os_current_time_read_in_ms
typedef int (*rm443_timer_start_fn)(unsigned, unsigned);
#define rm443_timer_start ((rm443_timer_start_fn)(uintptr_t)RM443_BINDING(TIMER_START))
typedef uint32_t (*rm443_timer_stop_fn)(uint32_t);
#define rm443_timer_stop ((rm443_timer_stop_fn)(uintptr_t)RM443_BINDING(TIMER_STOP))
typedef unsigned (*rm443_watchdog_start_fn)(void (*callback)(void), unsigned, unsigned);
#define rm443_watchdog_start ((rm443_watchdog_start_fn)(uintptr_t)RM443_BINDING(WATCHDOG_START))
typedef unsigned (*rm443_dma_start_fn)(const void*, unsigned, unsigned, void*);
#define rm443_dma_start ((rm443_dma_start_fn)(uintptr_t)RM443_BINDING(DMA_START))
typedef int (*rm443_clock_enable_fn)(unsigned, unsigned, unsigned);
#define rm443_clock_enable ((rm443_clock_enable_fn)(uintptr_t)RM443_BINDING(CLOCK_ENABLE))
typedef int (*rm443_clock_reset_fn)(unsigned, unsigned, unsigned);
#define rm443_clock_reset ((rm443_clock_reset_fn)(uintptr_t)RM443_BINDING(CLOCK_RESET))
typedef void (*rm443_isi_delete_fn)(const void*);
#define rm443_isi_delete ((rm443_isi_delete_fn)(uintptr_t)RM443_BINDING(ISI_DELETE))
typedef void (*rm443_keyboard_decode_fn)(const void*, unsigned);
#define rm443_keyboard_decode ((rm443_keyboard_decode_fn)(uintptr_t)RM443_BINDING(KEYBOARD_DECODE))
typedef int (*rm443_scratchpad_take_fn)(unsigned);
#define rm443_scratchpad_take ((rm443_scratchpad_take_fn)(uintptr_t)RM443_BINDING(SCRATCHPAD_TAKE))
typedef int (*rm443_scratchpad_give_fn)(unsigned);
#define rm443_scratchpad_give ((rm443_scratchpad_give_fn)(uintptr_t)RM443_BINDING(SCRATCHPAD_GIVE))
typedef void (*rm443_cm_send_server_fn)(unsigned, void*, void*);
#define rm443_cm_send_server ((rm443_cm_send_server_fn)(uintptr_t)RM443_BINDING(CM_SEND_SERVER))

#endif
