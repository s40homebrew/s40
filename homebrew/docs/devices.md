# Device profiles and porting

| Runtime profile | Model tokens | Resolver versions | Screen |
| --- | --- | --- | --- |
| 6303 classic / RM-443 | `Nokia6303c`, `Nokia6303classic` | 08.90, 10.12 | 240×320 |
| C3-00 / RM-614 | `NokiaC3-00` | 08.63 | 320×240 |

The platform string selects a family. The resolver finds services in its
mapped ROM windows.
Family constants describe memory ranges, VM layouts and instruction patterns.

RM-443 discovers its ROM end and uses a wrapped cache-range bootstrap.
RM-614 scans two mapped ROM prefixes and uses a Java switch carrier; KVM's
bytecode loader synchronizes its instructions. Its screen, keyboard and timer
policy differ from RM-443. Both profiles resolve the same 27 core services.

## Adding a device

1. Establish the ROM/RAM maps, Java object and method layouts, getRGB behavior,
   native calling convention and executable-memory/cache rules.
2. Add the mappings and bounded instruction recipes in a new
   `profiles/RmNNNProfile.java`. Resolve symbols and relationships rather than
   putting firmware function addresses in a lookup table.
3. Verify display ownership, pixel formats, input, timers, files and allocation.
   Add runtime backends where needed; keep device checks out of applications.
4. Register a unique model token in `FirmwareProfiles.java`. Exercise discovery
   against your own firmware, including ambiguous/malformed matches.
5. Test Hello's check/start/exit/relaunch, focus loss and multiple keys in the
   firmware emulator and on the phone before trying a larger application.

RAM Booster and Native Time have their own smaller registries. Supporting a
phone there does not make it a shared-runtime profile. See the RAM Booster
[porting guide](../apps/ram_booster/docs/porting.md) for its different contract.
