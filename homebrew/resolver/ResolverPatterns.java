/* SPDX-License-Identifier: MIT */
/** Common semantic recipe IDs. Profiles supply audited code for these roles. */
final class ResolverPatterns {
    static final int RANGE = 0;
    static final int CACHE = 1;
    static final int KNI_PARAM = 2;
    static final int TASK_LOOP = 3;
    static final int APP_ID = 4;
    static final int EVENT_POP = 5;
    static final int DISPATCH = 6;
    static final int INVOKE = 7;
    static final int KNI_RETURN = 8;
    static final int MSG_COUNT = 9;
    static final int VM_TIMER = 10;
    static final int LCD_RESERVE = 11;
    static final int LCD_UPDATE = 12;
    static final int SPECIAL_TIMER = 13;
    static final int BYTECODE_SYNC = 14;
    static final int DCACHE_VENEER = 15;
    static final int ICACHE_VENEER = 16;
    static final int DCACHE_RANGE = 17;
    static final int ICACHE_RANGE = 18;
    static final int WRITE_BUFFER = 19;
    static final int DCACHE_ALL = 20;
    static final int UI_REFRESH = 21;
    static final int UI_COPY = 22;
    static final int UI_SIGNAL = 23;
    static final int COUNT = 24;
    // Search 1..7 globally, plus BYTECODE_SYNC on carrier-based profiles.
    static final int SEARCH_COUNT = 8;
}
