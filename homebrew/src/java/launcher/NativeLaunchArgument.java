/* SPDX-License-Identifier: MIT */
import java.io.IOException;

/** Reuses the completed loader's transfer buffer for one immutable UTF-8 string. */
final class NativeLaunchArgument {
    static byte[] encode(String text) throws IOException {
        if (text == null)
            text = "";
        if (text.indexOf('\0') >= 0)
            throw new IOException("Native argument contains NUL");
        for (int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            if (c >= 0xD800 && c <= 0xDBFF) {
                if (++i >= text.length() || text.charAt(i) < 0xDC00 || text.charAt(i) > 0xDFFF)
                    throw new IOException("Invalid native argument text");
            } else if (c >= 0xDC00 && c <= 0xDFFF) {
                throw new IOException("Invalid native argument text");
            }
        }
        byte[] data = text.getBytes("UTF-8");
        if (data.length >= NativeProtocol.TRANSFER_BYTES)
            throw new IOException("Native argument is too long");
        return data;
    }

    static void write(int[] result, byte[] data) {
        for (int i = NativeProtocol.RESULT_TRANSFER_WORD; i < NativeProtocol.API_DESCRIPTOR_WORD;
                ++i)
            result[i] = 0;
        for (int i = 0; i < data.length; ++i)
            result[NativeProtocol.RESULT_TRANSFER_WORD + (i >> 2)] |= (data[i] & 255)
                    << ((i & 3) * 8);
    }
}
