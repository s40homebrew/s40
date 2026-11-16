/* SPDX-License-Identifier: MIT */
import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;

/** Plain Java heap allocation and garbage-collection checker. */
public final class S40HeapCheck extends MIDlet implements CommandListener, Runnable {
    private final Command back = new Command("Back", Command.BACK, 1);
    private final Command exit = new Command("Exit", Command.EXIT, 2);
    private List menu;
    private volatile boolean busy;
    private int testChunks;
    private final int[] chunks = {4, 6, 8, 12, 16, 18, 20, 24, 28};
    protected void startApp() {
        if (menu == null) {
            menu = new List("S40 Heap Check", List.IMPLICIT);
            menu.append("Show Java memory", null);
            menu.append("Retain 1 MiB + collect", null);
            menu.append("Retain 1.5 MiB + collect", null);
            menu.append("Retain 2 MiB + collect", null);
            menu.append("Retain 3 MiB + collect", null);
            menu.append("Retain 4 MiB + collect", null);
            menu.append("Retain 4.5 MiB + collect", null);
            menu.append("Retain 5 MiB + collect", null);
            menu.append("Retain 6 MiB + collect", null);
            menu.append("Retain 7 MiB + collect", null);
            menu.addCommand(exit);
            menu.setCommandListener(this);
        }
        if (!busy)
            Display.getDisplay(this).setCurrent(menu);
    }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
    public void commandAction(Command c, Displayable d) {
        if (busy)
            return;
        if (c == exit) {
            notifyDestroyed();
            return;
        }
        if (c == back) {
            Display.getDisplay(this).setCurrent(menu);
            return;
        }
        if (d != menu)
            return;
        int i = menu.getSelectedIndex();
        if (i == 0) {
            show(memory());
            return;
        }
        testChunks = chunks[i - 1];
        busy = true;
        Form wait = new Form("Testing");
        wait.append("Allocating, collecting and checking retained arrays.");
        Display.getDisplay(this).setCurrent(wait);
        new Thread(this).start();
    }
    public void run() {
        int count = 0;
        boolean oom = false, correct = true;
        byte[][] blocks = new byte[testChunks][];
        System.gc();
        try {
            for (int i = 0; i < blocks.length; i++) {
                blocks[i] = new byte[262144];
                count++;
                for (int j = 0; j < 262144; j += 4096)
                    blocks[i][j] = (byte) (i + (j >>> 12));
                blocks[i][262143] = (byte) (i ^ 85);
            }
        } catch (OutOfMemoryError e) {
            oom = true;
        }
        // Even a quota-limited run must preserve its successful allocations.
        System.gc();
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < 262144; j += 4096)
                if (blocks[i][j] != (byte) (i + (j >>> 12)))
                    correct = false;
            if (blocks[i][262143] != (byte) (i ^ 85))
                correct = false;
        }
        long heldFree = Runtime.getRuntime().freeMemory();
        for (int i = 0; i < blocks.length; i++)
            blocks[i] = null;
        blocks = null;
        System.gc();
        busy = false;
        show((!correct ? "DATA MISMATCH"
                             : oom ? "LIMIT REACHED"
                                   : "PASS")
                + "\nRetained: " + (count * 256) + " KiB\nFree while held: " + (heldFree / 1024)
                + " KiB\n"
                + (correct ? "Survived System.gc(); sampled bytes checked.\n"
                           : "Sampled bytes changed after System.gc().\n")
                + (correct && count > 16 ? "More than 4 MiB retained in Java arrays.\n" : "")
                + "Arrays released.\n\n" + memory());
    }
    private String memory() {
        return System.getProperty("microedition.platform")
                + "\nTotal Java heap: " + (Runtime.getRuntime().totalMemory() / 1024)
                + " KiB\nFree Java heap: " + (Runtime.getRuntime().freeMemory() / 1024) + " KiB";
    }
    private void show(String text) {
        save(text);
        Form form = new Form("Heap result");
        form.append(text);
        form.addCommand(back);
        form.addCommand(exit);
        form.setCommandListener(this);
        Display.getDisplay(this).setCurrent(form);
    }
    private void save(String text) {
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore("HeapCheckResult", true);
            byte[] bytes = text.getBytes("UTF-8");
            if (store.getNumRecords() == 0)
                store.addRecord(bytes, 0, bytes.length);
            else
                store.setRecord(1, bytes, 0, bytes.length);
        } catch (Exception ignored) {
            // Storage is supplementary; the on-screen result remains useful.
        } finally {
            if (store != null)
                try {
                    store.closeRecordStore();
                } catch (Exception ignored) {
                }
        }
    }
}
