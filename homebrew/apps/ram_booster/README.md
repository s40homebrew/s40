# S40 RAM Booster

A Java ME app that changes the foreground Java memory quota for newly opened
apps. Changes stay in RAM and disappear on reboot. Ordinary quota mode leaves
the shared Java arena unchanged; selected models also have arena expansion.

| Phone | Quotas after Check | Arena behavior |
| --- | --- | --- |
| Nokia 301 | 3, 4, 6, 8 MiB | Existing shared arena |
| Asha 302 (RM-813) | 3, 4, 6, 8 MiB | Existing 8 MiB arena; stock quota is 4 MiB |
| C2-01, C3-00, 6700 classic | 3, 4 MiB | Existing shared arena |
| Nokia 6300 (RM-217) | 3, 4 MiB | Replaces the 2.5 MiB arena with 5 MiB |
| 6303 classic (RM-443) | 3 MiB ordinary; 4–8 MiB with expansion | Replaces the 3.75 MiB arena with 9 MiB |
| Asha 306 (RM-767) | 3 MiB ordinary; 4, 5, 6 MiB with expansion | Extends the object reservation from 4 to 8 MiB |

Check discovers the pool and available expansion mechanism before offering
sizes. A quota is a ceiling, not a reservation of free RAM: VM overhead and
other Java apps use the same arena. The 6300i, 6300 4G and 6700 slide are
different platforms and are not supported by these profiles.

## Build and install

Follow [build setup](../../../docs/building.md), then run from the SDK repo root:

```sh
bash homebrew/build.sh ram_booster
bash homebrew/build.sh heap_check
```

Install the matching JAR/JAD pairs from `homebrew/dist/ram_booster/` and
`homebrew/dist/heap_check/`. The optional command
`python3 homebrew/apps/ram_booster/tools/package_kit.py` packages both apps.

## Use

1. Open [Heap Check](../heap_check/README.md), note the stock memory result,
   then exit it completely. On a stock 6300, use the 1 or 1.5 MiB retention test.
2. Open RAM Booster and choose **Check phone**, then the recommended size:
   **6 MiB on Asha 302**, **4 MiB on 6303/306 with expansion**, or **3 MiB**
   on the other models.
3. Follow the result screen. On 6300/6303, expansion restarts Java and closes
   its apps; reopen the booster and Check again. On 306, use **Close booster**
   and reopen it for the automatic status check. If saving the request failed,
   the app asks for a manual Check instead.
4. Once the requested limit is active, exit the booster and reopen Heap Check.
   Retain one MiB below the selected quota and verify the data through collection.
   Try larger quotas only after the smaller result succeeds.
5. Use **Restore default**, then relaunch the checker to confirm the original
   quota. Reboot the phone to release any expanded arena and resident hooks.

Already-running apps keep their old quota. Check does not apply a quota.
**Details** shows the latest result, also saved in the `RamBoosterResult` RMS
store. Check may retry once automatically; Apply is never retried automatically.
If the result requests a phone restart, reboot before continuing.

## Expansion and Restore

**6300 and 6303:** the first expansion allocates a new contiguous OS-owned arena
and restarts the Java VM after restoring temporary bootstrap state. Existing
objects are not copied into the new arena. Check reports activation only when
the VM is using the new base and size: 5120 KiB on 6300 or 9216 KiB on 6303.
Later quota changes reuse that arena. Restore resets the launch quota but keeps
the arena until reboot; a live heap is never shrunk.

**Asha 306:** expansion preserves object addresses and commits physical RAM
as the VM needs it. The separate method arena and single-object limit remain
unchanged. An occupied adjacent reservation can leave the request pending.
If reopening reports **Not active**, read Details; repeating the exit sequence
does not make an occupied neighbour free. Restore cancels pending activation
or resets an active quota, but the resident hook and expanded reservation remain
until reboot. Do not reboot between Apply and the first status check, since
that cancels the request. Reboot before installing a different booster version
over an existing resident hook.

Ordinary quota patches are undone by Restore or reboot. No mode writes flash.
Native games use separate OS allocations and do not need the Java heap boost;
reboot after expansion to return its native RAM before running those games.

For loading or bootstrap failures, use the [staged probe](diagnostic/README.md).
Implementation details are in the [porting guide](docs/porting.md).

MIT licensed; see [COPYING](../../COPYING).
