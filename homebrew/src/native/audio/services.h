/* SPDX-License-Identifier: MIT */
#ifndef S40_AUDIO_SERVICES_H
#define S40_AUDIO_SERVICES_H
#include "commgr/abi.h"

/* Decode the shared resolver's audio capability. Neither routine reads ROM or
 * calls firmware. Failed conversion leaves the destination unchanged. */
int audio_abi_configure(unsigned contract, audio_abi* output);
int audio_abi_from_services(const uint32_t* services, audio_abi* output);
#endif
