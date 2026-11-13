/* SPDX-License-Identifier: MIT */
/** Minimal application configuration for the shared launcher. */
public final class HelloLauncher extends HomebrewMIDlet {
    protected String applicationName() {
        return "S40 Hello";
    }
    protected String[] instructions(int inputLayout) {
        return new String[] {"Native display and input example", "D-pad: move the white square",
                "Center / space: change its color", "Right softkey: return to launcher",
                "No data files required", "Choose Check firmware or Start"};
    }
}
