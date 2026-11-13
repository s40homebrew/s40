/* SPDX-License-Identifier: MIT */
import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;

public final class S40RamBooster
        extends MIDlet implements CommandListener, Runnable, HeapBoosterProtocol {
    private final Command back = new Command("Back", Command.BACK, 1);
    private final Command exit = new Command("Exit", Command.EXIT, 2);
    private final Command details = new Command("Details", Command.SCREEN, 3);
    private Form resultForm, detailsForm;
    private String detailsText;
    private List menu;
    private HeapBoosterSession session;
    private String platform;
    private volatile boolean busy;
    private int operation, mib;
    private final int[] sizes = {3, 4, 6, 8};
    private int sizeCount, restoreIndex, helpIndex, poolBytes;

    protected void startApp() {
        if (menu == null) {
            platform = System.getProperty("microedition.platform");
            HeapBoosterProfile p = HeapBoosterProfiles.select(platform);
            if (p != null)
                session = new HeapBoosterSession(p);
            rebuildMenu();
        }
        if (!busy)
            Display.getDisplay(this).setCurrent(menu);
    }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
    private int firstTestMiB() {
        // The 302's ordinary foreground quota is already 4 MiB in 15.09.
        return session != null && "RM-813".equals(session.profile.id) ? 6 : 3;
    }
    private void rebuildMenu() {
        menu = new List("S40 RAM Booster 0.6.3", List.IMPLICIT);
        menu.append("Check firmware / status", null);
        sizeCount = 0;
        if (session != null) {
            for (int i = 0; i < sizes.length; i++) {
                if (poolBytes != 0 && sizes[i] * 1048576 > poolBytes)
                    break;
                menu.append(sizes[i] + " MiB"
                                + (sizes[i] == firstTestMiB()   ? " - first test"
                                                : sizes[i] == 8 ? " - experimental"
                                                                : ""),
                        null);
                sizeCount++;
            }
            restoreIndex = menu.append("Restore firmware default", null);
        } else
            restoreIndex = -1;
        helpIndex = menu.append("How to test", null);
        menu.addCommand(exit);
        menu.setCommandListener(this);
    }
    public void commandAction(Command c, Displayable d) {
        if (busy)
            return;
        if (c == exit) {
            notifyDestroyed();
            return;
        }
        if (c == details) {
            detailsForm = show("Technical details", detailsText);
            return;
        }
        if (c == back) {
            Display.getDisplay(this).setCurrent(d == detailsForm ? (Displayable) resultForm : menu);
            return;
        }
        if (d != menu)
            return;
        int choice = menu.getSelectedIndex();
        if (choice == helpIndex) {
            int first = firstTestMiB();
            show("How to test",
                    "This changes the launch quota for new Java apps. It does not create more RAM.\n\n"
                            + "Close other Java apps. Select Check, then " + first
                            + " MiB. Exit the booster and open S40 Heap Check. Its total should be "
                            + first + " MiB; run Retain " + (first - 1) + " MiB + "
                            + "collect.\n\nApplies to newly launched foreground apps. Background "
                            + "quotas "
                            + "are preserved. Reboot or Restore returns the launch policy to "
                            + "normal.\n\nSize choices follow the detected shared Java pool. "
                            + "Raising a "
                            + "quota does not reserve memory or enlarge that pool.");
            return;
        }
        if (session == null) {
            show("Unsupported phone",
                    platform
                            + ("\n\nNo memory map available for this model. Profiles: Nokia 301, "
                                    + "Asha 302, C2-01, C3-00, 6303 classic and 6700 classic."));
            return;
        }
        if (session.needsRestart) {
            show("Restart required", HeapBoosterDiagnostics.reason(session.error));
            return;
        }
        operation = choice == 0 ? OP_CHECK : (choice == restoreIndex ? OP_RESTORE : OP_APPLY);
        mib = choice > 0 && choice <= sizeCount ? sizes[choice - 1] : 0;
        busy = true;
        Form working = new Form(operation == OP_CHECK ? "Checking firmware"
                        : operation == OP_APPLY       ? "Applying quota"
                                                      : "Restoring quota");
        working.append("Discovering the launch policy and preparing native access. Please wait.");
        Display.getDisplay(this).setCurrent(working);
        new Thread(this).start();
    }
    public void run() {
        session.execute(operation, mib);
        if (session.resolver.pools == 1 && session.result[W_POOL_BYTES] != 0) {
            poolBytes = session.result[W_POOL_BYTES];
            rebuildMenu();
        }
        String text = platform + "\n" + session.profile.id + "\n";
        if (session.error == 0) {
            if (operation == OP_APPLY)
                text += "APPLIED: " + mib
                        + (" MiB\nExit, then launch the game or Heap Check. Already running apps "
                                + "keep their current quota.\n");
            else if (operation == OP_RESTORE)
                text += "RESTORED original launch policy. Newly launched ordinary apps get the "
                        + "firmware default.\n";
            else
                text += "READY. Launch policy: "
                        + (session.result[W_CURRENT_MIB] == 0
                                        ? "firmware default"
                                        : session.result[W_CURRENT_MIB] + " MiB")
                        + ".\n";
        } else {
            text += "Stopped: " + session.error + "\nWhile "
                    + HeapBoosterDiagnostics.stage(session.stage) + ".\n"
                    + HeapBoosterDiagnostics.reason(session.error) + "\n";
            if (session.failure != null)
                text += session.failure.toString() + "\n";
        }
        text += "\nJava pool: "
                + (session.result[W_POOL_BYTES] == 0
                                ? "not detected"
                                : (session.result[W_POOL_BYTES] / 1024) + " KiB");
        if (operation == OP_CHECK)
            text += "\nCheck does not change the launch quota.";
        else if (!session.nativeCalled)
            text += "\nStopped before native execution; launch quota unchanged.";
        String summary = text;
        text = "Stage: " + HeapBoosterDiagnostics.stage(session.stage);
        text += "\nNative call: " + (session.nativeCalled ? "attempted" : "not made");
        if (session.failure != null)
            text += "\nException: " + session.failure.toString();
        text += "\nPolicy site: "
                + (session.result[W_SITE] == 0 ? "not detected" : hex(session.result[W_SITE]));
        if (session.error >= 11 && session.error <= 23) {
            text += "\nClass / methods: " + hex(session.result[W_OWNER]) + " / "
                    + hex(session.result[W_METHOD_TABLE]);
            text += "\nMethod count: " + session.result[W_METHOD_COUNT];
            text += "\nNative entry: " + hex(session.result[W_NATIVE_METHOD]) + " flags "
                    + hex(session.result[W_NATIVE_FLAGS]);
            text += "\nNative code: " + hex(session.result[W_NATIVE_CODE]);
            text += "\nCarrier: " + hex(session.result[W_CARRIER]) + " bytes "
                    + session.result[W_CARRIER_BYTES];
            text += "\nCarrier flags: " + hex(session.result[W_CARRIER_FLAGS]);
            text += "\nPayload code buffer: " + hex(session.result[W_PAYLOAD_CODE]);
        }
        if (session.stage < HeapBoosterSession.DISCOVERY)
            text += "\nDiscovery: not run";
        if (session.result[W_SHADOW_STATE] == 3)
            text += "\nCode page: RAM copy on Apply";
        if (session.result[W_SHADOW_STATE] == 1)
            text += "\nCode page: RAM copy active";
        if (session.result[W_SHADOW_STATE] == 2)
            text += "\nCode page: original mapping restored";
        if (session.result[W_MAGIC] != MAGIC)
            text += "\nCPU / MMU: not measured";
        else {
            text += "\nPhysical: " + hex(session.result[W_PHYSICAL]);
            text += "\nCPSR: " + hex(session.result[W_CPSR]);
            text += "\nCPU: "
                    + (session.result[W_STATUS] == 201 ? "not measured"
                                                       : hex(session.result[W_CPU]));
            if (session.result[W_STATUS] == 201 || session.result[W_STATUS] == 202)
                text += "\nMMU: not measured";
            else {
                text += "\nSCTLR: " + hex(session.result[W_SCTLR]);
                text += "\nTTBR0: " + hex(session.result[W_TTBR0]);
                if (session.profile.armv6()) {
                    text += "\nTTBR1: " + hex(session.result[W_TTBR1]);
                    text += "\nTTBCR: " + hex(session.result[W_TTBCR]);
                } else if (session.profile.backend == HeapBoosterProfile.ARM926_RAP)
                    text += "\nTable walker: " + hex(session.result[W_WALKER]);
                text += "\nDACR: " + hex(session.result[W_DACR]);
                text += "\nL1: " + hex(session.result[W_L1]) + " L2: " + hex(session.result[W_L2]);
            }
        }
        text += "\nMatches (site/pool/cache): " + session.resolver.sites + "/"
                + session.resolver.pools + "/" + session.resolver.caches;
        detailsText = text;
        resultForm = show(session.error == 0 ? "Result" : "Diagnostic", summary);
        resultForm.addCommand(details);
        busy = false;
    }
    private Form show(String title, String text) {
        Form form = new Form(title);
        form.append(text);
        form.addCommand(back);
        form.addCommand(exit);
        form.setCommandListener(this);
        Display.getDisplay(this).setCurrent(form);
        return form;
    }
    private static String hex(int value) {
        String s = Integer.toHexString(value).toUpperCase();
        while (s.length() < 8)
            s = "0" + s;
        return s;
    }
}
