# Licenses and external sources

The main repository's original SDK, launchers, tools, examples and documentation
are MIT licensed under [LICENSE](LICENSE). Existing third-party material is not
relicensed by that file.

| Component | License / source |
| --- | --- |
| newlib/libm linked into native apps | Individual permissive notices in [COPYING.NEWLIB](homebrew/licenses/COPYING.NEWLIB); [upstream](https://sourceware.org/newlib/) |
| GCC runtime helpers | GPLv3 with GCC Runtime Library Exception; [GPL](homebrew/licenses/COPYING.GCC3), [exception](homebrew/licenses/COPYING.RUNTIME), [upstream](https://gcc.gnu.org/) |
| DoomGeneric and S40 Doom backend | GPL-2.0-or-later; separate [Doom fork](https://github.com/s40homebrew/doom) |
| TyrQuake and S40 port | Retain upstream GPL notices; separate [TyrQuake fork](https://github.com/s40homebrew/tyrquake) |
| Linux, BusyBox and musl | Linux/BusyBox GPL-2.0; musl MIT and included notices; see [Linux port](https://github.com/s40homebrew/linux/tree/s40-linux/s40) for exact component boundaries |
| ProGuard (build only) | GPL-2.0 with its published exception; [source and license](https://github.com/Guardsquare/proguard/tree/v7.10.0). Setup keeps the distribution license alongside the tool. |
| JDK 1.4.2 compiler (build only) | Obtained separately under its original JDK license; [download and preparation](docs/building.md#legacy-java-compiler). |
| Nokia S40 API classes and README excerpt | Nokia SDK material, obtained separately from [S40-SDK-Classes](https://github.com/0x726564/S40-SDK-Classes). Not covered by this project's MIT grant. |
