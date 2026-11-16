/* SPDX-License-Identifier: MIT */
/** Shared model-token boundaries, evaluated before any firmware-memory access. */
final class PlatformIdentity {
    private PlatformIdentity() {}

    /** Both arguments are trimmed/lowercase model strings supplied by registries. */
    static boolean matches(String platform, String model) {
        if (!platform.startsWith(model))
            return false;
        int end = model.length();
        if (end == platform.length())
            return true;
        if (platform.charAt(end) == '-') {
            // Numeric hardware variants, e.g. Nokia6700c-1/13.10. A dotted
            // number is also an accepted firmware suffix, e.g. -08.90.
            int first = ++end;
            while (end < platform.length() && digit(platform.charAt(end)))
                end++;
            if (end == first)
                return false;
            if (end == platform.length())
                return true;
            if (platform.charAt(end) == '.')
                return firmwareNumber(platform, first);
        }
        char separator = platform.charAt(end);
        if (separator == '/')
            return true; // Release text is not a firmware-version allowlist.
        if (separator != ' ')
            return false;
        while (end < platform.length() && platform.charAt(end) == ' ')
            end++;
        // A bare space must not turn Nokia6300 4G into the original 6300.
        return firmwareNumber(platform, end);
    }

    private static boolean digit(char value) {
        return value >= '0' && value <= '9';
    }

    private static boolean firmwareNumber(String text, int start) {
        boolean dot = false, component = false;
        for (int i = start; i < text.length(); i++) {
            char value = text.charAt(i);
            if (digit(value)) {
                component = true;
            } else if (value == '.' && component) {
                dot = true;
                component = false;
            } else {
                return false;
            }
        }
        return dot && component;
    }
}
