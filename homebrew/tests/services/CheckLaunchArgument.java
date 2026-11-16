/* SPDX-License-Identifier: MIT */
import java.io.IOException;
import java.util.Arrays;

public final class CheckLaunchArgument {
    private static void check(boolean ok) {
        if (!ok)
            throw new AssertionError("Native argument contract");
    }

    private static void rejected(String text) throws Exception {
        try {
            NativeLaunchArgument.encode(text);
            throw new AssertionError("Accepted invalid launch argument");
        } catch (IOException expected) {
        }
    }

    public static void main(String[] args) throws Exception {
        int[] words = new int[1130];
        Arrays.fill(words, 0xAABBCCDD);
        byte[] text = NativeLaunchArgument.encode("C:/caf\u00E9/\uD83C\uDFAE.gb");
        NativeLaunchArgument.write(words, text);
        for (int i = 0; i < text.length; ++i)
            check(((words[96 + (i >> 2)] >>> ((i & 3) * 8)) & 255) == (text[i] & 255));
        check(words[95] == 0xAABBCCDD && words[1120] == 0xAABBCCDD);
        check(words[1119] == 0);
        char[] maximum = new char[4095];
        Arrays.fill(maximum, 'x');
        NativeLaunchArgument.write(words, NativeLaunchArgument.encode(new String(maximum)));
        check(words[1119] == 0x00787878);
        NativeLaunchArgument.write(words, NativeLaunchArgument.encode(null));
        check(words[96] == 0 && words[1119] == 0);
        rejected(new String(maximum) + "x");
        rejected("a\0b");
        rejected("\uD800");
        rejected("\uDC00");
        System.out.println(
                "PASS launch argument encoding, bounds, termination and descriptor isolation");
    }
}
