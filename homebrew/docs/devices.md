# Device compatibility

Shared-launcher apps include profiles for the phones below. A profile chooses
the discovery rules; the running firmware must still pass validation before
native code starts. Similar model names do not imply compatibility.

| Phone | Shared native launcher | Optional PCM audio |
| --- | --- | --- |
| Nokia 6300 / RM-217 | 07.21, 07.30; provisional | 07.21, 07.30; provisional |
| Nokia 6303 classic / RM-443 | 08.90, 10.12 | 08.90 (provisional), 10.12 |
| Nokia C3-00 / RM-614 | 04.60, 08.63 | 04.60, 08.63; provisional |
| Asha 302 / RM-813 | No shared launcher | 14.53; standalone binding, provisional |
| Nokia 301 / RM-839 | No shared launcher | 02.33, 09.04; standalone bindings, provisional |

Provisional features have matching firmware layouts but still need handset
validation. Discovery checks the running firmware rather than selecting from
a version allowlist; other versions can work when their layouts and service
relationships match. Standalone audio support requires a port to supply its own
bootstrap, native services and message pump.

All shared-launcher profiles provide ordinary graphics. The optional accelerated
RGB565 display and keep-awake request support RM-443 08.90 and 10.12; other
profiles use ordinary output. The 6300 and 6303 screens are 240×320 with a numeric
keypad; C3-00 is 320×240 with a QWERTY keyboard. Apps choose bindings using the
reported input layout.

## Standalone utilities

These apps have their own device requirements; their availability does not add
a shared native launcher profile.

- [RAM Booster](../apps/ram_booster/README.md) supports Nokia 301, Asha 302,
  C2-01, C3-00, 6700 classic, 6300, 6303 classic and Asha 306. Available quotas
  and expansion behavior differ by model; its guide explains how to use them.
- [Native Time](../examples/native_time/README.md) supports 6303 classic and
  Asha 302.
- [Battery Info](../apps/battery/README.md) and
  [PC Bridge](../apps/pc_bridge/README.md) support RM-443 08.90 and 10.12;
  08.90 is provisional.
- [Heap Check](../apps/heap_check/README.md) is ordinary Java ME and uses no
  native firmware access.

## Adding a device

Begin with a small example before porting a game. Keep firmware dumps, databases
and device captures outside the public repository.

1. Establish the device's readable memory regions, Java/native calling
   conventions and executable-memory rules.
2. Add memory and bootstrap assumptions under `src/java/profiles/`. Capability
   modules under `src/java/resolver/services/` use the shared exports, decoders
   and bounded search helpers, then publish a complete generated binding group.
   Native drivers consume that group without repeating discovery.
3. Verify allocation, file, timer, event and display behavior. Add differences
   to runtime adapters while keeping application-facing APIs shared.
4. Register an unambiguous model identity and check malformed or conflicting
   discovery results. Host regressions should run without private firmware data.
5. Exercise Hello's check/start/exit/relaunch, focus loss and multiple keys on
   the target. Add optional video or audio only after the basic session works.

The [architecture guide](architecture.md) explains component responsibilities.
Private layout and protocol contracts live beside each implementation.
RAM Booster has a separate [porting guide](../apps/ram_booster/docs/porting.md).
