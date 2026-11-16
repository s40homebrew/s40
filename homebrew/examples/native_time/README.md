# Native Time

A small Java ME example that reads the native OS millisecond counter on Nokia
6303 classic and Asha 302. The value is a wrapping counter, not the date or
wall-clock time. See [device compatibility](../../docs/devices.md).

Follow [build setup](../../../docs/building.md), then run from the repository root:

```sh
bash homebrew/build.sh native_time
```

Install `homebrew/dist/native_time/NativeTime.jar` and its matching JAD, then
choose **Read native time**. Initial firmware discovery may take several seconds.
If the app reports a cleanup failure, reboot before using it again.

This example performs a single native call and returns to Java. For a complete
native event loop with graphics and input, start with [Hello](../hello/README.md).
The app's `src/` directory contains its launcher and device discovery code.

After building, run `python3 homebrew/examples/native_time/tests/check.py` with
the configured Java tools. The check exercises packaged native-call setup and
restoration without firmware input.

MIT licensed; see [COPYING](COPYING).
