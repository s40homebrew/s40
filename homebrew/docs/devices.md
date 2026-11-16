# Device profiles and porting

| Runtime profile | Model tokens | Firmware examined | Display |
| --- | --- | --- | --- |
| Nokia 6300 / RM-217 | `Nokia6300` | 07.21, 07.30 | 240×320 |
| Nokia 6303 classic / RM-443 | `Nokia6303c`, `Nokia6303classic` | 08.90, 10.12 | 240×320 |
| Nokia C3-00 / RM-614 | `NokiaC3-00` | 04.60, 08.63 | 320×240 |

The platform string selects a family. Firmware suffixes do not select address
tables: the resolver discovers services inside that family's mapped ROM windows.
Unknown or ambiguous model identities are rejected before bootstrap.

RM-217 still needs handset testing. C3-00 04.60 and RM-443 have run Doom on
physical phones. Firmware discovery alone does not establish handset support.

## Family differences

RM-217 covers the original 6300, not the 6300i or 6300 4G. It has a 16 MiB physical
RAM envelope and no named native export tables. A checked range walker supplies
ROM bounds; masked instructions and call/literal relationships identify services.
Its wrapped-cache bootstrap enters the native dispatcher. A 16-byte legacy canvas
passes opaque 32-bit pixels to the foreground Java refresh helper, which converts
them to the LCD's 24-bit transfer buffer. The 07.21 and 07.30 firmware use the same
recipes despite moved function addresses.

RM-443 discovers its ROM end from a range walker and a zero-terminated range
table. Its bootstrap uses a wrapped cache-range stage. The display backend
submits the application buffer directly to the master-display service.

RM-614 scans two mapped prefixes: `[0x80130400, 0x80EC0000)` and
`[0x85000400, 0x85900000)`. These fixed windows exclude RAM and the unmapped gap;
firmware moving required services outside them will fail discovery. Its Java
switch carrier is synchronized by KVM's bytecode loader. The display backend
copies into the foreground Java surface and queues its native UI worker.
C3 also has a different timer policy and keyboard layout, supplied as runtime
configuration. Firmware 04.60 shares the 08.63 bootstrap, with an instruction
variant in the special-timer helper.

RM-443 and RM-614 resolve 27 core bindings. RM-217 resolves 23 plus a mandatory
foreground-refresh callback; four unused master-display bindings remain zero.
The loader permits those missing bindings only with the legacy canvas policy.
Descriptor word 85 carries the foreground callback and word 95 the canvas layout.

## Adding a device

1. Establish the ROM/RAM maps, Java object and method layouts, getRGB behavior,
   native calling convention and executable-memory/cache rules. Keep firmware,
   databases and porting evidence outside the public repository.
2. Add mappings and bounded instruction recipes to `src/java/profiles/RmNNNProfile.java`.
   Resolve symbols and relationships rather than embedding a table of firmware
   function addresses. Keep scans within known readable regions.
3. Verify message/timer dispatch, event packing, display ownership and formats,
   file services and allocation. Add runtime backends where behavior differs;
   keep device checks out of applications.
4. Register a unique model token in `FirmwareProfiles.java`. Exercise discovery
   against your own firmware, including ambiguous and malformed matches.
   Public regression tests should exercise maintained code without private inputs.
5. Test Hello's check/start/exit/relaunch, focus loss and multiple keys in the
   firmware emulator and on the phone before trying a larger application.

RAM Booster and Native Time have separate registries. Supporting a phone there
does not make it a shared-runtime profile. See RAM Booster's
[porting guide](../apps/ram_booster/docs/porting.md) for its quota and arena-growth
contracts, and its [device list](../apps/ram_booster/README.md) for available modes.
Battery Info and PC Bridge use fixed RM-443 10.12 adapters on top of the shared
bootstrap; they do not extend the runtime's device support.
