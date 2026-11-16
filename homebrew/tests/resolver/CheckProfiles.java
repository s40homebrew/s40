/* SPDX-License-Identifier: MIT */
/** Host checks of the actual preverified runtime device selector. No ROM access. */
public final class CheckProfiles {
    public static void main(String[] args) {
        FirmwareProfile expected = FirmwareProfiles.select("Nokia6303c/10.12");
        require(expected != null && expected.id.equals("RM-443"), "Supported device");
        String[] supported = {"Nokia6303c/08.90", "Nokia6303classic/10.12", "Nokia6303c-08.90",
                "Nokia6303c", " Nokia6303classic ", "NOKIA6303C/10.12", "Nokia6303c-1/custom",
                "Nokia6303c 08.90"};
        for (String platform : supported) {
            require(FirmwareProfiles.select(platform) == expected, platform);
        }
        FirmwareProfile c3 = FirmwareProfiles.select("NokiaC3-00/08.63");
        require(c3 != null && c3.id.equals("RM-614") && c3 != expected, "C3 runtime profile");
        require(FirmwareProfiles.select("NokiaC3-00/04.60") == c3,
                "C3 early firmware independence");
        require(FirmwareProfiles.select("NokiaC3-00/08.70") == c3, "C3 firmware independence");
        require(FirmwareProfiles.select("NokiaC3-01/08.63") == null, "Different C3 model");
        require(FirmwareProfiles.select("NokiaC3-00Extra") == null, "C3 model boundary");
        FirmwareProfile classic = FirmwareProfiles.select("Nokia6300/07.21");
        require(classic != null && classic.id.equals("RM-217"), "6300 profile");
        require(FirmwareProfiles.select("Nokia6300/06.60") == classic, "6300 firmware independent");
        // Shared builders must not flatten the distinct native/bootstrap ABIs.
        require(expected.descriptorBytes == 36 && c3.descriptorBytes == 36
                        && classic.descriptorBytes == 0,
                "Library descriptor policies");
        require(expected.kniReturnSpLdrOffset == 4 && c3.kniReturnSpLdrOffset == 4
                        && classic.kniReturnSpLdrOffset == 2,
                "KNI variant relationships");
        require(c3.specialTimer == 76 && c3.taskSpecialCallOffset == 50
                        && c3.canvasLayout == FirmwareApi.CANVAS_CURRENT
                        && classic.canvasLayout == FirmwareApi.CANVAS_LEGACY,
                "Carrier timer and legacy canvas policies");
        require(expected.ramRange(expected.ramStart, 4)
                        && expected.ramRange(expected.ramLimit - 4, 4)
                        && !expected.ramRange(expected.ramStart - 4, 4)
                        && !expected.ramRange(expected.ramLimit - 4, 8)
                        && !expected.ramRange(expected.ramStart + 1, 4)
                        && !expected.ramRange(expected.ramStart, -4),
                "Aligned RAM record bounds");
        String[] unsupported = {null, "", "generic", "j2me", "Nokia6303ci/10.12", "Nokia6303cExtra",
                "Nokia6300i/07.21", "Nokia6300 4G", "Nokia6300-4G", "Nokia6300-1extra/07.30",
                "Nokia6300 07..30", "Nokia6300 07.30.", "Nokia6300 .07.30", "OtherNokia6303c/10.12",
                "RM-443"};
        for (String platform : unsupported) {
            require(FirmwareProfiles.select(platform) == null, "Unsupported: " + platform);
        }
        require(FirmwareProfiles.selectFrom(
                        new FirmwareProfile[] {expected, expected}, "Nokia6303c/10.12")
                        == null,
                "Ambiguous profiles must not choose by order");
        System.out.println(
                "PASS: runtime device selection, firmware suffixes, unknown models and ambiguity");
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}
