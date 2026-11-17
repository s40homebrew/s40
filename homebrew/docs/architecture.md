# SDK architecture

A Java launcher discovers the running firmware and starts an ARM application.
The native runtime then provides graphics, input, timing, files and optional
audio directly through firmware services. Java resumes after the native session
has released its resources.

Native code runs on the launcher's OS task with its own stack. It does not run
in the background or create another task. Applications must pump messages
regularly so input, audio and phone UI remain responsive. Focus loss ends the
native session.

## Source organization

| Directory | Responsibility |
| --- | --- |
| `include/s40/` | Public application API and ownership contracts |
| `include/libc/` | Supported C/POSIX compatibility headers |
| `src/java/launcher/` | Launcher UI, file picker and native session management |
| `src/java/profiles/` | Device-family assumptions and discovery recipes |
| `src/java/resolver/` | Shared memory, export and instruction discovery |
| `src/java/resolver/services/` | Validated capability groups, including audio |
| `src/firmware/signatures/` | Assembly signatures compiled into discovery recipes |
| `src/bootstrap/` | Native loading, relocation and return to Java |
| `src/native/core/` | Session lifetime, event dispatch and timing |
| `src/native/display/` | Ordinary display, scaled video and device adapters |
| `src/native/input/` | Key-to-action mapping |
| `src/native/libc/` | Allocations, files, paths and C library integration |
| `src/native/audio/` | PCM output and shared firmware transport |
| `src/native/firmware/` | Private firmware interfaces and validation helpers |
| `tools/` | Build support, generated contracts and packaging |

Paths in this table are relative to `homebrew/`. Applications depend on the public
headers, not firmware bindings or bootstrap descriptors. Device differences
belong in profiles and adapters so ports can share their application code.

## Session lifetime

1. The launcher selects a device family and validates its firmware services.
   **Check firmware** performs discovery without starting the application.
2. **Start** loads the packaged native image, relocates it and supplies the
   launcher's options and optional argument. Temporary Java state is restored
   before application execution.
3. The runtime calls `s40_app_main`. The application controls its event loop,
   assets and rendering while the runtime handles phone messages.
4. Returning, calling `s40_finish`, reporting an error or losing focus starts
   cleanup. The runtime restores ordinary phone rendering, runs exit callbacks
   in reverse registration order, closes audio/display resources and files,
   and frees tracked memory.
5. Control returns to Java, which displays the result and permits another launch.

An exit callback is the place to save application state and release resources
outside the runtime's tracking. Callbacks can be registered for normal exit only
or for both normal and error exit; see [runtime.h](../include/s40/runtime.h).

## Discovery and optional drivers

A model name selects a memory and bootstrap profile. One shared resolver context
then validates the readable regions, named exports and instruction relationships.
Audio, video and utilities obtain their services through that context. Native
drivers consume the resolved bindings; they do not select firmware address tables.

Applications request the optional capabilities they use. Each capability is
published as a complete group or left unavailable, so a failed audio lookup does
not prevent ordinary graphics or native startup. Cached results are reused only
after their mappings, code and relationships are revalidated. Unknown or
ambiguous matches remain unavailable.

Native Time and RAM Booster share bounded discovery helpers with the
runtime while retaining their smaller execution paths. Ports with their own
bootstrap can use a service-only memory context. That context discovers optional
services without claiming support for the shared native launcher.

Firmware signatures are maintained as assembly. The build generates their Java
recipe factories under its own `signatures/` directory before compiling the
launcher; generated instruction arrays are not maintained source files. See
[firmware signatures](firmware-signatures.md) for the source format.

The native image and Java/bootstrap communication formats are defined once in
[tools/native_contract.py](../tools/native_contract.py). The build generates
matching C, assembly and Java constants from that definition. Firmware layouts,
message formats and callable bindings stay beside their implementing components.
These private interfaces may change; they are not application storage or APIs.

## Scope

The C layer combines bundled newlib/libm with firmware-backed memory and file
operations. Native applications allocate from the OS heap and do not need
RAM Booster, which changes the separate Java heap policy.

The SDK provides a limited C/POSIX subset, not a general operating system.
Networking, threads, dynamic native libraries and C++ unwinding are unsupported.
Consult the public headers when porting software and isolate missing services in
the port's platform layer. [Writing an application](applications.md) covers the
supported file and event behavior.
