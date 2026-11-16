/* SPDX-License-Identifier: MIT */
public final class CheckFilePickerPaths {
    private static void check(boolean ok) {
        if (!ok)
            throw new AssertionError("Picker path contract");
    }

    public static void main(String[] args) throws Exception {
        String[] suffixes = {".gb", ".gbc"};
        check(FilePickerPaths.accepts("GAME.GBC", suffixes));
        check(FilePickerPaths.accepts("predeffilereceived/", suffixes));
        check(!FilePickerPaths.accepts("game.gb.sav", suffixes));
        check(!FilePickerPaths.accepts("../", suffixes));
        check(!FilePickerPaths.accepts("../game.gb", suffixes));
        check(!FilePickerPaths.accepts("E:/game.gb", suffixes));
        check(!FilePickerPaths.accepts("x\\game.gb", suffixes));
        String root = "C:/predefgallery/";
        check(FilePickerPaths.parent(root, root).equals(root));
        check(FilePickerPaths.parent(root, root + "predeffilereceived/").equals(root));
        check(FilePickerPaths.parent(root, root + "predeffilereceived/games/")
                        .equals(root + "predeffilereceived/"));
        check(FilePickerPaths.parent(root, "E:/").equals(root));
        check(FilePickerPaths.compare("games/", "a.gb") < 0);
        check(FilePickerPaths.label("predeffilereceived/").equals("Received files/"));
        check(FilePickerPaths.label("my games/").equals("my games/"));
        check(FilePickerPaths.url("C:/one%2f+two space/")
                        .equals("file:///C:/one%252f%2Btwo%20space/"));
        System.out.println(
                "PASS picker suffixes, directory navigation, root boundaries and labels");
    }
}
