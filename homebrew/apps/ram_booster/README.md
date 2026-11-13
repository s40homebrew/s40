# RAM Booster

A Java ME proof of concept that raises the foreground Java heap quota for
subsequently launched apps. It changes the launch policy in RAM; the shared
Java pool stays the same. Restore or reboot to undo it.

| Phone | Offered quotas |
| --- | --- |
| Nokia 301, Asha 302 | 3, 4, 6, 8 MiB |
| C2-01, C3-00, 6700 classic | 3, 4 MiB |
| 6303 classic | 3 MiB |

Choices are filtered by the discovered pool. Existing apps keep their current
quota; other running apps still share that pool.

## Build and use

Follow [build setup](../../../docs/building.md), then run from the repo root:

```sh
bash homebrew/build.sh ram_booster
bash homebrew/build.sh heap_check
```

Install both matching JAR/JAD pairs from `homebrew/dist/ram_booster/` and
`homebrew/dist/heap_check/`.

1. Run Heap Check to see the stock quota, then exit it.
2. Open RAM Booster, choose **Check**, then **Apply**. Start with 3 MiB;
   on Asha 302 use 6 MiB because its stock foreground quota is already 4 MiB.
3. Exit the booster and reopen Heap Check. Use **Retain 2 MiB** (or **Retain
   5 MiB** for the 302), then **Collect**, and check that the data survives.
4. Exit the checker, reopen the booster and **Restore**. Relaunch the checker
   to confirm the stock quota. A reboot also clears the change.

For other quotas, retain one MiB less than the selected quota. Temporary Java
VM changes are restored after every operation. If cleanup fails or the phone
hangs, reboot before trying again.

## Source

`src/` contains discovery, device mappings, UI and the session template.
`native/` contains ARM926 and ARMv6 writers. `tools/` generates protocol
constants and the executable switch carrier. [Porting](docs/porting.md)
describes the contracts that need checking for another device.

MIT licensed; see [COPYING](../../COPYING).
