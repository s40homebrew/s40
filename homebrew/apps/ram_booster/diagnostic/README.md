# Booster staged probe

A separate MIDlet for locating class-loading and bootstrap failures. It copies
the existing booster classes and native payloads unchanged, with its own suite
identity and a small launcher that defers loading them until after the first
screen appears. It does not replace the regular booster.

Build from the SDK repo root after building RAM Booster:

```sh
bash homebrew/build.sh ram_booster
bash homebrew/apps/ram_booster/diagnostic/build.sh
```

Install the matching `S40BoosterProbe.jar` / `.jad` from
`homebrew/dist/ram_booster_probe/`. Set `BOOSTER_JAR` when building to probe a
different existing booster JAR.

1. Open the probe and confirm **Launcher OK** appears.
2. Select **Test loading** to load the booster classes and construct a session.
3. Select **Run next test** one stage at a time. Stop after an error or reboot;
   record the last stage and the platform/firmware string.

| Stage | Last action before cleanup |
| --- | --- |
| 1 — Memory view | Open and restore the memory view |
| 2 — Firmware scan | Discover policy, pool and cache helper |
| 3 — Method lookup | Find the owning methods and executable storage |
| 4 — Binding / restore | Install and undo the temporary payload/binding without invoking it |
| 5 — Native Check | Invoke native Check |

Each stage repeats the earlier steps in a fresh transaction and finishes
cleanup before formatting its result. No stage requests a quota change.
Results stay on screen; the probe does not save a log.
