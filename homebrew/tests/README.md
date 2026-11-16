# Focused host checks

These tests execute Java/C/ARM code using synthetic inputs and modeled firmware
services. They require no firmware dumps or research workspace. Run the checks
for the component being changed.

From the SDK repository, source `tools/env.sh` in Bash to select the JDK and
build dependencies. Python checks use Python 3; ARM execution requires Unicorn.

These checks compile their own small fixtures and need no application build:

```sh
python3 homebrew/tests/bootstrap/test_chunked_resource.py
python3 homebrew/tests/bootstrap/test_native_limits.py
python3 -m pytest tests/test_clean_build_outputs.py
```

They cover resource reconstruction and closure, native allocation budgets, and
build cleanup preserving unrelated files. Audio tests compile production code
and check PCM ownership/backpressure and playback-clock pacing:

```sh
clang -std=c99 -Wall -Wextra -fsanitize=address,undefined -Ihomebrew/runtime/include homebrew/tests/audio/test_stream.c -o /tmp/s40-audio-test
/tmp/s40-audio-test
clang -std=c99 -Wall -Wextra -fsanitize=address,undefined -Ihomebrew/runtime/include homebrew/tests/audio/test_clock.c -o /tmp/s40-audio-clock-test
/tmp/s40-audio-clock-test
```

After building Hello, check the compiled device selector, ROM-range helpers,
loader placement and progress callbacks; then execute the carrier/loader's
allocation, relocation, private-stack and cleanup paths:

```sh
bash homebrew/tests/resolver/check.sh hello
python3 homebrew/tests/bootstrap/verify.py homebrew/build/hello
```

The Java runner also accepts another application name and `S40_BUILD_DIR`.
The ARM check accepts a build directory directly.

Application-specific checks run after building that application:

```sh
bash homebrew/apps/ram_booster/tests/check.sh
python3 homebrew/examples/native_time/tests/check.py
python3 homebrew/apps/pc_bridge/tests/test_resident.py
python3 homebrew/apps/pc_bridge/tests/test_install.py
```

RAM Booster covers packaged bootstrap restoration and UI resume/retry behavior.
Native Time checks packaged binding/install/restore behavior. PC Bridge checks
actual installer/resident ARM publication, protocol handling and allocation
ownership with modeled firmware services.
