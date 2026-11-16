/* SPDX-License-Identifier: MIT */
/**
 * HBP1 Java/native contract. These compile-time constants allocate no objects.
 * tools/generate_protocol.py emits the native header from this one definition.
 * Keep wire values stable: fixture tests and older diagnostic tools read them.
 */
interface HeapBoosterProtocol {
    int MAGIC = 0x48425031;
    int RESULT_WORDS = 144;
    int PAYLOAD_CAPACITY = 12288;
    int PATCH_HALFWORDS = 15;
    int PATCH_BYTES = 30;
    int OP_CHECK = 0;
    int OP_APPLY = 1;
    int OP_RESTORE = 2;
    int ARMV6_IDENTITY = 1;
    int ARM926_RAP = 2;
    int ARM926_BCM = 3;
    int ARMV6_RAPU = 4;
    int ARMV6_XGOLD = 5;

    // Inputs, results and processor snapshots. MAGIC records native entry,
    // not successful completion: STATUS must also be checked after returning.
    int W_MAGIC = 0;
    int W_OPERATION = 1;
    int W_MIB = 2;
    int W_STATUS = 3;
    int W_SITE = 4;
    int W_VARIANT = 5;
    int W_POOL_BYTES = 6;
    int W_PHYSICAL = 7;
    int W_CPSR = 8;
    int W_CPU = 9;
    int W_SCTLR = 10;
    int W_TTBR0 = 11;
    int W_TTBR1 = 12;
    int W_TTBCR = 13;
    int W_DACR = 14;
    int W_L1 = 15;
    int W_L2 = 16;
    int W_CURRENT_MIB = 17;
    int W_WALKER = 18;
    int W_BACKEND = 19;
    int W_ROM_START = 20;
    int W_ROM_END = 21;
    int W_PHYSICAL_START = 22;
    int W_PHYSICAL_END = 23;
    int W_ALLOC = 24;
    int W_FREE = 25;
    int W_RAM_START = 26;
    int W_RAM_END = 27;
    int W_SHADOW = 28;
    int W_CACHE_SYNC = 29;
    int W_RAM_TAGS = 30;
    int W_GROWTH_KIND = 31;
    // Eight words each, low halfword first; last halfword is padding.
    int W_EXPECTED = 32;
    int W_DESIRED = 40;
    int W_SHADOW_STATE = 48;
    int W_OWNER = 49;
    int W_METHOD_TABLE = 50;
    int W_METHOD_COUNT = 51;
    int W_NATIVE_METHOD = 52;
    int W_CARRIER = 53;
    int W_NATIVE_FLAGS = 54;
    int W_CARRIER_FLAGS = 55;
    int W_CARRIER_BYTES = 56;
    int W_NATIVE_CODE = 57;
    int W_PAYLOAD_CODE = 58;
    int W_GROWTH_LIMIT = 59;
    int W_GROWTH_STATE = 60;
    int W_HEAP_BASE = 61;
    int W_RESERVATION = 62;
    int W_PAYLOAD_BYTES = 63;
    int G_PROVIDER = 64;
    int G_HEAP_STATE = 65;
    int G_CURRENT_APP = 66;
    int G_PAGE_SIZE = 67;
    int G_MAP_HELPER = 68;
    int G_PAGE_GETTER = 69;
    int G_RUNTIME_FREE = 70;
    int G_RUNTIME_TOTAL = 71;
    int G_SHRINK = 72;
    int G_AREA_GETTER = 73;
    int G_RESERVE_FIXED = 74;
    int G_LOCK = 75;
    int G_UNLOCK = 76;
    int G_RELEASE_HELPER = 77;
    int G_RELEASE_WRAPPER = 78;
    int G_PATCH_COUNT = 79;
    int G_PATCHES = 80;
    int G_PATCH_STRIDE = 4;
    // X-GOLD resident observations; retained across rearming a pending request.
    int X_EXIT_CALLS = 132;
    int X_GROW_ATTEMPTS = 133;
    int X_CALLBACK_CPSR = 134;
    int X_HELPER_RESULT = 135;
    int X_WAIT_REASON = 136;
    int X_AREA = 137;
    int X_COMMITTED_END = 138;
    int X_TAIL_NODE = 139;
    int X_TAIL_START = 140;
    int X_TAIL_END = 141;
    int X_NEXT_START = 142;
    int X_BAD_TAIL_FIELDS = 143;
    // Fixed-arena growth reuses the architecture-specific extension area.
    int F_PROVIDER = 64;
    int F_HEAP_STATE = 65;
    int F_CONFIG_SITE = 66;
    int F_RESET_HELPER = 67;
    int F_BASE_LITERAL = 68;
    int F_SIZE_INSTRUCTION = 69;
    int F_CLEAR_HELPER = 70;
    int F_RESET_STUB = 71;
    int F_RESET_FLAG = 72;
}
