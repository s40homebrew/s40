/* SPDX-License-Identifier: MIT */
import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;

/**
 * Keep this entry class independent of every booster class. In particular, do
 * not give its fields, method signatures, or constant pool typed references to
 * HeapBoosterSession or BoosterProbeTarget. Class.forName intentionally defers
 * loading until after the launcher is visible and inside a Throwable handler.
 * Some VM failures still terminate outside Java; the last displayed stage then
 * remains useful. This cannot diagnose an installer rejection before startApp.
 */
public final class BoosterProbe extends MIDlet implements CommandListener, Runnable {
    private final Command test = new Command("Test loading", Command.OK, 1);
    private final Command check = new Command("Run next test", Command.OK, 1);
    private final Command exit = new Command("Exit", Command.EXIT, 2);
    private Form screen;
    private String log;
    private Runnable target;
    private volatile boolean busy;
    private boolean loadingDone, failed;
    private int nextTest = 1;
    private static final String[] TESTS = {"Memory view", "Firmware scan", "Method lookup",
            "Binding / restore", "Native Check"};
    private static final String[] EXPLANATIONS = {
            "Open and restore the memory view. No firmware scan or native call.",
            "Find the policy, pool and cache helper, then restore. No native call.",
            "Inspect the owning class and executable methods, then restore. No native call.",
            "Install and restore the temporary native binding and payload. Do not invoke them.",
            "Run native Check, then restore. No launch-quota change."};

    protected void startApp() {
        if (screen == null) {
            log = "Probe 0.2.0 opened\n" + System.getProperty("microedition.platform")
                    + "\n\nSelect Test loading. No launch-quota change is requested.\n";
            show("Launcher OK");
        }
    }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private void show(String title) {
        screen = new Form(title);
        if (busy)
            screen.append("Stage: " + title + "\n\n");
        screen.append(log);
        if (loadingDone && target != null && nextTest <= TESTS.length) {
            screen.append("\n\nNext: test " + nextTest + " - " + TESTS[nextTest - 1]
                    + "\n" + EXPLANATIONS[nextTest - 1]
                    + "\nEach test repeats the earlier steps. If the phone reboots, report this test number.");
        }
        screen.addCommand(exit);
        if (!busy && !failed) {
            if (!loadingDone)
                screen.addCommand(test);
            else if (target != null)
                screen.addCommand(check);
        }
        screen.setCommandListener(this);
        Display.getDisplay(this).setCurrent(screen);
    }

    public void commandAction(Command command, Displayable displayable) {
        if (busy)
            return;
        if (command == exit) {
            notifyDestroyed();
            return;
        }
        if (command != test && command != check)
            return;
        busy = true;
        show(loadingDone ? "Test " + nextTest + ": " + TESTS[nextTest - 1] : "Loading classes");
        new Thread(this).start();
    }

    public void run() {
        String stage = "Starting test";
        try {
            if (!loadingDone) {
                String[] names = {"HeapBoosterProfile", "HeapBoosterProfiles", "HeapPatchResolver",
                        "HeapBoosterSession", "S40RamBooster"};
                for (int i = 0; i < names.length; i++) {
                    stage = "Load " + names[i];
                    show(stage);
                    Class.forName(names[i]);
                    log += "OK " + names[i] + "\n";
                }
                stage = "Construct session";
                show(stage);
                target = (Runnable) Class.forName("BoosterProbeTarget").newInstance();
                target.run();
                log += target.toString() + "\n\nLoading finished. Run the tests one at a time. "
                        + "Stop after an error or reboot and report the last test. There is no Apply option.";
                loadingDone = true;
            } else {
                stage = "Test " + nextTest + ": " + TESTS[nextTest - 1];
                // No UI/log allocations during the session's temporary VM
                // modifications. The original session restores them itself.
                target.run();
                log = target.toString();
                failed = log.startsWith("STOP");
                if (!failed)
                    nextTest++;
                if (failed || nextTest > TESTS.length)
                    target = null;
            }
        } catch (Throwable error) {
            failed = true;
            log = "FAILED at " + stage + "\n" + error.toString() + "\n\n" + log;
        }
        busy = false;
        show(failed ? "Test stopped"
                        : loadingDone && target == null ? "Tests finished"
                        : loadingDone ? "Ready for test " + nextTest : "Loading result");
    }
}
