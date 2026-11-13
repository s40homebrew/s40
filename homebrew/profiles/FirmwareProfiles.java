/* SPDX-License-Identifier: MIT */
/**
 * Runtime selection before any getRGB heap write or firmware memory access.
 *
 * Model identity chooses a bootstrap/mapping candidate, not an address table.
 * The resolver must still discover and validate that device's actual ROM.
 * Allocate the registry, recipes and resolver before entering the forged view.
 */
final class FirmwareProfiles {
    static final String PLATFORM_PROPERTY = "microedition.platform";
    static final String RM443_BOOTSTRAP = "rm443-getrgb-v1";
    static final String CARRIER_BOOTSTRAP = "s40-getrgb-carrier-v1";

    private static final FirmwareProfile[] SUPPORTED =
            new FirmwareProfile[] {Rm443Profile.create(), Rm614Profile.create()};

    private FirmwareProfiles() {}

    static FirmwareProfile select(String platform) {
        return selectFrom(SUPPORTED, platform);
    }

    /** Kept separate so host tests can exercise overlapping registry entries. */
    static FirmwareProfile selectFrom(FirmwareProfile[] profiles, String platform) {
        if (platform == null) {
            return null;
        }
        String model = platform.trim().toLowerCase();
        FirmwareProfile selected = null;
        for (int i = 0; i < profiles.length; i++) {
            FirmwareProfile candidate = profiles[i];
            for (int j = 0; j < candidate.platformModels.length; j++) {
                String expected = candidate.platformModels[j].toLowerCase();
                if (matchesModel(model, expected)) {
                    if (selected != null) {
                        // Registry order must never decide between two ABIs.
                        return null;
                    }
                    selected = candidate;
                    break;
                }
            }
        }
        return selected;
    }

    private static boolean matchesModel(String platform, String expected) {
        if (!platform.startsWith(expected)) {
            return false;
        }
        if (platform.length() == expected.length()) {
            return true;
        }
        // Firmware suffixes are allowed; model suffixes are not. In particular,
        // "Nokia6303ci" must not accidentally select the "Nokia6303c" ABI.
        char separator = platform.charAt(expected.length());
        return separator == '/' || separator == '-' || separator == ' ';
    }
}
