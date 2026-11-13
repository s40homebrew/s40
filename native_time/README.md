# Native Time

A Java ME example that calls the native OS millisecond counter. Supports Nokia
6303 classic, with an experimental Asha 302 profile for firmware 14.53/15.09.

Follow [build setup](../docs/building.md), then:

```sh
bash native_time/build.sh
```

Install `native_time/dist/NativeTime.jar` and its matching JAD, then choose
**Read native time**. The result is a wrapping 32-bit millisecond counter.

`NativeTime.java` contains the memory view, native binding, 40-byte ARM routine
and carrier. `TimeResolver.java` and `RapuTimeResolver.java` discover firmware
services. The 6303 uses Java array storage; the 302 uses executable method
storage. Temporary VM changes are restored before displaying the result.

MIT licensed; see [COPYING](COPYING).
