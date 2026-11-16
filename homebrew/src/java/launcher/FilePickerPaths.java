/* SPDX-License-Identifier: MIT */
import java.io.UnsupportedEncodingException;

/** Entry rules shared by the UI picker and its path-boundary tests. */
final class FilePickerPaths {
    private static final String[] FOLDERS = {"predeffilereceived/", "predefphotos/",
            "predefvideos/", "predefmusic/", "predeftones/", "predefrecordings/", "predefthemes/",
            "predefgraphics/", "predefplaylist/"};
    private static final String[] LABELS = {"Received files/", "Photos/", "Videos/", "Music/",
            "Tones/", "Recordings/", "Themes/", "Graphics/", "Playlists/"};

    static String label(String name) {
        for (int i = 0; i < FOLDERS.length; ++i)
            if (FOLDERS[i].equals(name))
                return LABELS[i];
        return name;
    }

    static String url(String nativePath) throws UnsupportedEncodingException {
        byte[] bytes = nativePath.getBytes("UTF-8");
        StringBuffer url = new StringBuffer("file:///");
        String hex = "0123456789ABCDEF";
        for (int i = 0; i < bytes.length; ++i) {
            int c = bytes[i] & 255;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '/' || c == ':' || c == '.' || c == '-' || c == '_') {
                url.append((char) c);
            } else {
                url.append('%').append(hex.charAt(c >> 4)).append(hex.charAt(c & 15));
            }
        }
        return url.toString();
    }

    static boolean accepts(String name, String[] suffixes) {
        if (name == null || name.length() == 0 || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0
                || name.indexOf('\0') >= 0)
            return false;
        boolean directory = name.endsWith("/");
        String component = directory ? name.substring(0, name.length() - 1) : name;
        if (component.length() == 0 || component.equals(".") || component.equals("..")
                || component.indexOf('/') >= 0)
            return false;
        if (directory || suffixes == null)
            return true;
        String lower = name.toLowerCase();
        for (int i = 0; i < suffixes.length; ++i)
            if (lower.endsWith(suffixes[i].toLowerCase()))
                return true;
        return false;
    }

    static String parent(String root, String current) {
        if (!current.startsWith(root) || current.length() <= root.length())
            return root;
        return current.substring(0, current.lastIndexOf('/', current.length() - 2) + 1);
    }

    static int compare(String first, String second) {
        boolean a = first.endsWith("/"), b = second.endsWith("/");
        if (a != b)
            return a ? -1 : 1;
        return first.toLowerCase().compareTo(second.toLowerCase());
    }
}
