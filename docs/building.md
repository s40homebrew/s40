# Building

On macOS or Linux, install Bash, Python 3, Git, a Java 8 JDK, LLVM with LLD,
and the Arm GNU `arm-none-eabi` toolchain with newlib. Heap Check and Native
Time only need the Java tools; RAM Booster also needs LLVM. Native apps use
newlib/libm/libgcc from Arm GNU Toolchain 15.3.rel2.

## Repository layout

Keep the repositories as siblings in a workspace directory:

```text
workspace/
  main/       # s40homebrew/s40: docs, SDK, Hello, RAM Booster, Heap Check, Native Time
  doom/       # s40homebrew/doom
  tyrquake/   # s40homebrew/tyrquake
  linux/      # s40homebrew/linux
```

From that workspace directory, clone the main repo and whichever ports you need:

```sh
git clone https://github.com/s40homebrew/s40.git main
git clone --branch s40 https://github.com/s40homebrew/doom.git doom
git clone --branch s40 https://github.com/s40homebrew/tyrquake.git tyrquake
git clone --branch s40-linux https://github.com/s40homebrew/linux.git linux
cd main
```

Run the setup and application commands below from `main/`. The port build
scripts find this sibling checkout automatically. For another layout, set
`S40_SDK_ROOT` to the main repository's absolute path.

## Java ME tools

```sh
python3 tools/setup.py
```

Setup fetches ProGuard 7.10.0 and the S40v6 API classes. Prepare the legacy
compiler separately as described below. Set `LEGACY_JAVAC` to use an already
relocated compiler. See [third-party licenses](../THIRD_PARTY.md).

Java 8 runs the legacy compiler and ProGuard. The compiler emits Java 1.3 /
class version 45.3 bytecode; the bootstrap depends on its exact switch-method
layout. Java 8's own `javac` cannot compile these phone classes.

Build scripts source `tools/env.sh`. Override `JAVA8_HOME`, `LEGACY_JAVAC`,
`PROGUARD_JAR`, `SDK_CLASSES`, `LLVM`, `CC`, `LD` or `ARM_GCC` as needed.
`LLVM` is the directory containing Clang and the LLVM tools; `LD` selects LLD.

### Legacy Java compiler

Use JDK 1.4.2's compiler. If you already have its `lib/tools.jar`, run:

```sh
python3 tools/prepare_javac.py /path/to/jdk1.4.2/lib/tools.jar
```

Otherwise, obtain `j2sdk-1_4_2_30-solaris-i586.tar.Z` from
[Oracle's Java 1.4 archive](https://www.oracle.com/java/technologies/java-archive-javase-v14-downloads.html).
After reading and accepting the [Oracle license](https://www.oracle.com/downloads/licenses/binary-code-license.html),
the direct-download method supplies the acceptance cookie explicitly:

```sh
mkdir -p tools/.deps/jdk1.4.2
curl --fail --location --cookie 'oraclelicense=accept-securebackup-cookie' --output tools/.deps/j2sdk-1_4_2_30-solaris-i586.tar.Z 'https://download.oracle.com/otn-pub/java/j2sdk/1.4.2_30-b04/j2sdk-1_4_2_30-solaris-i586.tar.Z'
gzip -dc tools/.deps/j2sdk-1_4_2_30-solaris-i586.tar.Z | tar -xf - -C tools/.deps/jdk1.4.2
python3 tools/prepare_javac.py tools/.deps/jdk1.4.2/SUNWj3dev/reloc/j2se/lib/tools.jar
```

The Solaris archive contains the compiler as Java bytecode; no Solaris
installation or native installer is needed. The preparation script relocates
the compiler package to avoid collisions with Java 8 and writes
`tools/.deps/legacy-javac.jar`. Keep the archive's license files alongside it.
These downloaded tools stay outside Git and the phone application's JAR.

Oracle's URL and cookie requirements can change. If the download returns an
HTML page instead of an archive, use the archive page to sign in and download
the same file, then continue with extraction and preparation.

## Applications

From the main repository root:

```sh
bash homebrew/build.sh hello
bash homebrew/build.sh ram_booster
bash homebrew/build.sh heap_check
bash homebrew/build.sh battery
bash homebrew/build.sh pc_bridge
bash native_time/build.sh
```

Install the matching JAR/JAD pairs from `homebrew/dist/<app>/` or
`native_time/dist/`.

See the [Doom](https://github.com/s40homebrew/doom),
[TyrQuake](https://github.com/s40homebrew/tyrquake) and
[Linux](https://github.com/s40homebrew/linux/tree/s40-linux/s40) guides for
their builds; run each port's commands from its own repository.

## Focused checks

Public tests exercise maintained code using host execution or compiled ARM with
explicit service stubs. They need no firmware dumps, private captures or emulator
checkout. See [the test guide](../homebrew/tests/README.md) and each application's
README for the checks relevant to a change.

After building the corresponding applications:

```sh
bash homebrew/apps/ram_booster/tests/check.sh
python3 native_time/tests/check.py
python3 homebrew/apps/pc_bridge/tests/test_resident.py
python3 homebrew/apps/pc_bridge/tests/test_install.py
```

ARM checks require Python with Unicorn; Java checks use the Java 8 JDK. Individual
checks document additional tools where needed.
