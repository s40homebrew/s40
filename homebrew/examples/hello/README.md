# Hello: native display and input

This is a small S40 application with no engine or asset dependency. It uses the
shared launcher, runtime profile selection, bootstrap and C API.

Build from the repository root with `bash homebrew/build.sh hello`. Install
`homebrew/dist/hello/S40Hello.jar` (matching JAD alongside it).

Choose **Check firmware**, then **Start**. The native app displays a square:

- D-pad moves it; native repeats continue movement.
- Center or Space changes its color.
- Right softkey returns to the Java launcher.

Focus loss also exits the native session. No external files are required.

This example and the shared foundation are MIT licensed; see `../../COPYING`.
The JAR also retains notices for its bundled newlib/libgcc components.
