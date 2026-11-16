/* SPDX-License-Identifier: MIT */
import java.util.Enumeration;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.List;

/** Read-only file picker confined to one native directory tree before launch. */
public final class HomebrewFilePicker implements CommandListener, Runnable {
    public interface Listener {
        /** Native path (for example C:/predefgallery/game.gb), not a file URL. */
        void selected(String path);
    }

    private static final int LIMIT = 128;
    private final Display display;
    private final Displayable previous;
    private final String root, title;
    private String current;
    private final String[] suffixes;
    private final Listener listener;
    private List list;
    private final Form loading;
    private Vector entries;
    private final Command cancel = new Command("Back", Command.BACK, 1);
    private volatile boolean closed, ready;

    /**
     * root is a native directory with a trailing slash. suffixes are matched
     * case-insensitively (for example {".gb", ".gbc"}); null accepts all files.
     * Directory navigation stays inside root; Back from root returns to previous.
     * Each listing retains at most 128 entries and runs off the UI event thread.
     * No files are written; the phone controls which directories Java can access.
     */
    public static void show(Display display, Displayable previous, String root, String title,
            String[] suffixes, Listener listener) {
        HomebrewFilePicker picker =
                new HomebrewFilePicker(display, previous, root, title, suffixes, listener);
        picker.load();
    }

    private HomebrewFilePicker(Display display, Displayable previous, String root, String title,
            String[] suffixes, Listener listener) {
        if (root == null || root.length() < 3 || root.charAt(1) != ':' || !root.endsWith("/"))
            throw new IllegalArgumentException("Picker needs a native directory ending in /");
        this.display = display;
        this.previous = previous;
        this.root = root;
        current = root;
        this.title = title;
        this.suffixes = suffixes;
        this.listener = listener;
        loading = new Form("Loading...");
        loading.append("Reading files...");
        loading.addCommand(cancel);
        loading.setCommandListener(this);
    }

    private void load() {
        ready = false;
        display.setCurrent(loading);
        new Thread(this).start();
    }

    public void run() {
        final Vector names = new Vector();
        final String directoryPath = current;
        FileConnection directory = null;
        String error = null;
        boolean truncated = false;
        try {
            directory = (FileConnection) Connector.open(
                    FilePickerPaths.url(directoryPath), Connector.READ);
            Enumeration entries = directory.list("*", true);
            /* Enumeration has no explicit close operation. Drain the native
             * cursor to EOF, including after Back or the item limit, while
             * keeping the retained name list bounded.
             */
            while (entries.hasMoreElements()) {
                String name = (String) entries.nextElement();
                if (closed)
                    continue;
                if (!FilePickerPaths.accepts(name, suffixes))
                    continue;
                if (names.size() == LIMIT) {
                    truncated = true;
                    continue;
                }
                int position = 0;
                while (position < names.size()
                        && FilePickerPaths.compare((String) names.elementAt(position), name) < 0)
                    ++position;
                names.insertElementAt(name, position);
            }
        } catch (Exception failure) {
            error = failure instanceof SecurityException ? "File access was not allowed."
                                                         : "Cannot open " + directoryPath;
        } finally {
            if (directory != null) {
                try {
                    directory.close();
                } catch (Exception ignored) {
                }
            }
        }
        final String failureText = error;
        final boolean limited = truncated;
        display.callSerially(new Runnable() {
            public void run() {
                if (closed)
                    return;
                try {
                    String heading = title;
                    if (!directoryPath.equals(root)) {
                        heading = FilePickerPaths.label(directoryPath.substring(
                                directoryPath.lastIndexOf('/', directoryPath.length() - 2) + 1));
                        heading = heading.substring(0, heading.length() - 1);
                    }
                    String[] labels = new String[names.size()];
                    for (int i = 0; i < names.size(); ++i)
                        labels[i] = FilePickerPaths.label((String) names.elementAt(i));
                    /* Populate before presentation. Nokia's live empty-list
                     * insertion path can stall while restoring its native peer.
                     */
                    List next = new List(limited ? heading + " (first 128)" : heading,
                            List.IMPLICIT, labels, null);
                    next.addCommand(cancel);
                    next.setCommandListener(HomebrewFilePicker.this);
                    list = next;
                    entries = names;
                    ready = true;
                    if (failureText != null || names.isEmpty()) {
                        Alert alert = new Alert(title,
                                failureText != null ? failureText
                                                    : "No matching files in " + directoryPath,
                                null, AlertType.INFO);
                        alert.setTimeout(Alert.FOREVER);
                        display.setCurrent(alert, list);
                    } else {
                        display.setCurrent(list);
                    }
                } catch (Throwable failure) {
                    closed = true;
                    Alert alert = new Alert(title, "Cannot display files: " + failure.toString(),
                            null, AlertType.ERROR);
                    alert.setTimeout(Alert.FOREVER);
                    display.setCurrent(alert, previous);
                }
            }
        });
    }

    public void commandAction(Command command, Displayable source) {
        if (closed)
            return;
        if (command == cancel) {
            if (ready && !current.equals(root)) {
                current = FilePickerPaths.parent(root, current);
                load();
            } else {
                closed = true;
                display.setCurrent(previous);
            }
        } else if (command == List.SELECT_COMMAND && ready) {
            int selected = list.getSelectedIndex();
            if (selected >= 0) {
                String name = (String) entries.elementAt(selected);
                if (name.endsWith("/")) {
                    current += name;
                    load();
                } else {
                    closed = true;
                    display.setCurrent(previous);
                    listener.selected(current + name);
                }
            }
        }
    }
}
