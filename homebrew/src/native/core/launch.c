/* SPDX-License-Identifier: MIT */
#include "s40/runtime.h"

const char* s40_launch_argument(void) {
    /* The Java launcher clears and fills this former transfer buffer after all
     * image transfers have completed. The loader no longer reads it on run.
     */
    return (const char*)(s40_result + 96);
}
