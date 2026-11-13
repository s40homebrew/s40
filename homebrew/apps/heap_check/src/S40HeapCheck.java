/* SPDX-License-Identifier: MIT */
import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;

/** Plain Java heap allocation and garbage-collection checker. */
public final class S40HeapCheck extends MIDlet implements CommandListener, Runnable {
    private final Command back = new Command("Back", Command.BACK, 1);
    private final Command exit = new Command("Exit", Command.EXIT, 2);
    private List menu;
    private volatile boolean busy;
    private int testMiB;
    protected void startApp() {
        if (menu == null) {
            menu = new List("S40 Heap Check", List.IMPLICIT);
            menu.append("Show Java memory", null);
            menu.append("Retain 2 MiB + collect", null);
            menu.append("Retain 3 MiB + collect", null);
            menu.append("Retain 5 MiB + collect", null);
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
        testMiB = i == 1 ? 2 : i == 2 ? 3 : i == 3 ? 5 : 7;
        busy = true;
        Form wait = new Form("Testing");
        wait.append("Allocating, collecting and checking retained arrays.");
        Display.getDisplay(this).setCurrent(wait);
        new Thread(this).start();
    }
    public void run() {
        int count = 0;
        boolean oom = false, correct = true;
        byte[][] blocks = new byte[testMiB * 4][];
        System.gc();
        try {
            for (int i = 0; i < blocks.length; i++) {
                blocks[i] = new byte[262144];
                count++;
                for (int j = 0; j < 262144; j += 4096)
                    blocks[i][j] = (byte) (i + (j >>> 12));
                blocks[i][262143] = (byte) (i ^ 85);
            }
            System.gc();
            for (int i = 0; i < blocks.length; i++) {
                for (int j = 0; j < 262144; j += 4096)
                    if (blocks[i][j] != (byte) (i + (j >>> 12)))
                        correct = false;
                if (blocks[i][262143] != (byte) (i ^ 85))
                    correct = false;
            }
        } catch (OutOfMemoryError e) {
            oom = true;
        }
        long heldFree = Runtime.getRuntime().freeMemory();
        for (int i = 0; i < blocks.length; i++)
            blocks[i] = null;
        blocks = null;
        System.gc();
        busy = false;
        show((oom ? "LIMIT REACHED"
                             : correct ? "PASS"
                                       : "DATA MISMATCH")
                + "\nRetained: " + (count * 256) + " KiB\nFree while held: " + (heldFree / 1024)
                + " KiB\n" + (oom ? "" : "Survived System.gc() with data intact.\n")
                + "Arrays released.\n\n" + memory());
    }
    private String memory() {
        return System.getProperty("microedition.platform")
                + "\nTotal Java heap: " + (Runtime.getRuntime().totalMemory() / 1024)
                + " KiB\nFree Java heap: " + (Runtime.getRuntime().freeMemory() / 1024) + " KiB";
    }
    private void show(String text) {
        Form form = new Form("Heap result");
        form.append(text);
        form.addCommand(back);
        form.addCommand(exit);
        form.setCommandListener(this);
        Display.getDisplay(this).setCurrent(form);
    }
}
