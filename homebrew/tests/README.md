# SDK checks

Run the checks for the component you changed from the repository root. They
execute maintained Java, C or ARM code with synthetic inputs and modeled
firmware services; no firmware dumps or research workspace are required.

Use [build setup](../../docs/building.md) for the compiler dependencies. Source
`tools/env.sh` in Bash to select them. ARM execution checks also require Python's
`unicorn` and `pyelftools` packages; the build-cleanup check uses `pytest`.

## Checks that build their own fixtures

| Component | Command |
| --- | --- |
| Chunked resource loading | `python3 homebrew/tests/bootstrap/test_chunked_resource.py` |
| Native allocation limits | `python3 homebrew/tests/bootstrap/test_native_limits.py` |
| Build-directory cleanup | `python3 -m pytest tests/test_clean_build_outputs.py` |
| Input, timing, paths, files and launcher helpers | `python3 homebrew/tests/services/check.py` |
| PCM lifecycle, device selection and mixer clock | `python3 homebrew/tests/audio/check.py` |
| Display, scaling, message handling and keep-awake | `bash homebrew/tests/video/check.sh` |
| Compiled RM-443 display adapter | `python3 homebrew/tests/video/check_rm443.py` |

C checks use Clang with memory/undefined-behavior sanitizers. Java checks use the
configured JDK; ARM adapter checks also compile with Clang and LLD.

## Checks against built applications

Build Hello first, then check firmware selection and the native bootstrap:

```sh
bash homebrew/build.sh hello
bash homebrew/tests/resolver/check.sh hello
python3 homebrew/tests/bootstrap/verify.py homebrew/build/hello
```

The resolver runner accepts another application name and `S40_BUILD_DIR`.
The bootstrap runner accepts a build directory directly.

Build the corresponding app before running its checks:

| Application | Command |
| --- | --- |
| RAM Booster | `bash homebrew/apps/ram_booster/tests/check.sh` |
| Native Time | `python3 homebrew/examples/native_time/tests/check.py` |
| PC Bridge resident code | `python3 homebrew/apps/pc_bridge/tests/test_resident.py` |
| PC Bridge installer | `python3 homebrew/apps/pc_bridge/tests/test_install.py` |

These cover packaged code and resource lifetime. They do not replace handset
checks when adding a firmware profile or changing device-specific behavior.
