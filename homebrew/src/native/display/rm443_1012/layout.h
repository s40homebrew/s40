/* SPDX-License-Identifier: MIT */
/* Partial RM-443 10.12 layouts, valid only after the adapter identity guard.
 * Firmware addresses are 32-bit words even when host-side tools include this
 * header. Reserved spans are intentionally unnamed: their purpose is not part
 * of the transport contract. No structure here claims a complete firmware ABI.
 */
#ifndef S40_VIDEO_RM443_LAYOUT_H
#define S40_VIDEO_RM443_LAYOUT_H
#include <stddef.h>
#include <stdint.h>

typedef struct {
    uint8_t watchdog_timer, watchdog_armed, dma_armed, reserved03;
    uint8_t powered, dma_channel, reserved06[2];
    uint32_t completion;
    uint8_t reserved0c[0x43 - 0x0c];
    uint8_t panel_id[3], panel_revision, reserved47, mode_guard;
} rm443_lcd_state;

typedef struct {
    uint8_t update_guard, reserved01[3];
    uint32_t pending_updates;
} rm443_lcd_pending;

typedef struct {
    uint8_t update_guard, reserved01[11];
    uint32_t before_update;
} rm443_display_hooks;

typedef struct {
    uint8_t reserved00[20];
    uint32_t state;
} rm443_java_foreground;

typedef struct {
    uint8_t reserved00[8];
    uint16_t object_id, reserved0a;
} rm443_java_server;

typedef struct {
    uint32_t queue;
} rm443_cm_task;
typedef struct {
    uint32_t tail;
    uint8_t reserved04[56], flags;
} rm443_cm_queue;

/* The head is UINT32_MAX when empty (not zero). Reading it is sufficient;
 * never follow a waiter pointer while the scheduler can change the list. */
typedef struct {
    uint32_t waiter_head;
    uint8_t available, binary, owner_task, reserved07;
} rm443_os_resource;

typedef struct {
    uint8_t reserved00[0x24];
    /* These stock bus configuration words are copied exactly. Individual bit
     * meanings are not established, so do not expose them as a GPIO API. */
    uint32_t bus_config24, bus_config28;
    uint8_t reserved2c[0x148 - 0x2c];
    uint32_t input;
} rm443_gpio_registers;

#define RM443_LAYOUT_ASSERT(type, field, offset) \
    typedef char type##_##field##_offset[(offsetof(type, field) == (offset)) ? 1 : -1]
RM443_LAYOUT_ASSERT(rm443_lcd_state, completion, 8);
RM443_LAYOUT_ASSERT(rm443_lcd_state, panel_id, 0x43);
RM443_LAYOUT_ASSERT(rm443_lcd_state, mode_guard, 0x48);
RM443_LAYOUT_ASSERT(rm443_lcd_pending, pending_updates, 4);
RM443_LAYOUT_ASSERT(rm443_display_hooks, before_update, 12);
RM443_LAYOUT_ASSERT(rm443_java_foreground, state, 20);
RM443_LAYOUT_ASSERT(rm443_java_server, object_id, 8);
RM443_LAYOUT_ASSERT(rm443_cm_queue, flags, 60);
RM443_LAYOUT_ASSERT(rm443_os_resource, owner_task, 6);
RM443_LAYOUT_ASSERT(rm443_gpio_registers, input, 0x148);
typedef char rm443_resource_size[(sizeof(rm443_os_resource) == 8) ? 1 : -1];
#undef RM443_LAYOUT_ASSERT

enum {
    RM443_SCREEN_WIDTH = 240,
    RM443_SCREEN_HEIGHT = 320,
    RM443_FRAME_WORDS = RM443_SCREEN_WIDTH * RM443_SCREEN_HEIGHT / 2,
    RM443_JAVA_TASK = 16,
    RM443_JAVA_FOREGROUND = 3,
    RM443_TIMER_MESSAGE_LAST = 488,
    RM443_RESOURCE_COUNT = 178,
    RM443_SCRATCHPAD_DISPLAY = 1,
    RM443_CM_SUBSCRIPTIONS_DIRTY = 1,
    RM443_PERIODIC_TIMER = 259,
    RM443_PERIODIC_TICKS = 1000,
    RM443_TIMER_STOPPED = 1,
    RM443_TIMER_ACTIVE = 2,
    RM443_TIMER_QUEUED = 3,
    RM443_BUS_CLOCK = 25,
    RM443_TE_PIN = 25,
    RM443_DATA_PIN_FIRST = 16,
    RM443_DATA_PIN_LAST = 23,
    RM443_DATA_PIN_ACTIVE = 2,
    RM443_DATA_PIN_IDLE = 0x20,
    RM443_DMA_REQUEST = 18,
    RM443_DMA_CHANNEL_COUNT = 6,
    RM443_WATCHDOG_US = 80000,
    RM443_COMMAND_TIMEOUT_MS = 80,
    RM443_FORMAT_SETTLE_MS = 40,
    RM443_TE_TIMEOUT_MS = 50,
    RM443_POLL_LIMIT = 262144,
    RM443_BUS_IDLE = 1,
    RM443_COMMAND_READY = 8,
    RM443_LCD_READ_FORMAT = 0x0c,
    RM443_LCD_SET_FORMAT = 0x3a,
    RM443_LCD_SET_LUT = 0x2d,
    RM443_LCD_READ_PIXEL = 0x2e,
    RM443_FORMAT_MASK = 7,
    RM443_FORMAT_RGB565 = 5,
    RM443_FORMAT_NORMAL = 7,
    RM443_KEYBOARD_RESOURCE = 0xe1,
    RM443_KEY_EVENT_FIRST = 4,
    RM443_KEY_EVENT_LAST = 6,
    RM443_KEY_MESSAGE_LENGTH = 22,
    RM443_LIGHT_RESOURCE = 14,
    RM443_LIGHT_REQUEST = 0x90,
    RM443_LIGHT_MAX_SECONDS = 120
};

#define RM443_PTR(type, address) ((volatile type*)(uintptr_t)(address))
#define RM443_LCD RM443_PTR(rm443_lcd_state, 0xd80bcc18u)
#define RM443_LCD_PENDING RM443_PTR(rm443_lcd_pending, 0xd80bd178u)
#define RM443_DISPLAY_HOOKS RM443_PTR(rm443_display_hooks, 0xd8071b78u)
#define RM443_FOREGROUND_SLOT RM443_PTR(uint32_t, 0xd8176954u)
#define RM443_JAVA_SERVER RM443_PTR(rm443_java_server, 0xd8176d88u)
#define RM443_JAVA_TASK_SLOT RM443_PTR(uint32_t, 0xd80cd3acu)
#define RM443_JAVA_QUEUE 0xd80cf2e4u
#define RM443_SCRATCHPAD_RESOURCES RM443_PTR(uint8_t, 0xd803b25bu)
#define RM443_OS_RESOURCES RM443_PTR(rm443_os_resource, 0xd80c57dcu)
#define RM443_PERIODIC_TIMER_STATE RM443_PTR(uint8_t, 0xd80c33e2u)
#define RM443_LCD_CONTROL RM443_PTR(uint32_t, 0xffd22208u)
#define RM443_GPIO RM443_PTR(rm443_gpio_registers, 0xffd31c00u)

static inline int rm443_ram_object(uintptr_t address, size_t bytes) {
    return address >= 0xd8000000u && address <= 0xda000000u - bytes && !(address & 3u);
}
#endif
