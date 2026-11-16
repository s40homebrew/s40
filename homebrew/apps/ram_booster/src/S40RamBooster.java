/* SPDX-License-Identifier: MIT */
import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;

public final class S40RamBooster extends MIDlet
        implements CommandListener, ItemCommandListener, Runnable, HeapBoosterProtocol {
    private final Command back = new Command("Back", Command.BACK, 1);
    private final Command exit = new Command("Exit", Command.EXIT, 2);
    private final Command details = new Command("Details", Command.SCREEN, 3);
    private final Command closeBooster = new Command("Close booster", Command.OK, 1);
    private final Command useFirst = new Command("Use recommended size", Command.OK, 1);
    private final Command checkAgain = new Command("Check again", Command.OK, 1);
    private Form resultForm, detailsForm;
    private String detailsText;
    private List menu;
    private HeapBoosterSession session;
    private String platform;
    private volatile boolean busy;
    private int operation, mib;
    private long operationMillis;
    private int[] sizes = {3, 4, 6, 8};
    private int sizeCount, restoreIndex, helpIndex, poolBytes, growthLimit;
    private int pendingMiB, automaticRetries, retryOpenedBase, retryLookupBase;
    private boolean reopenedRequest, closeRequested, pendingSaved;

    protected void startApp() {
        if (menu == null) {
            platform = System.getProperty("microedition.platform");
            HeapBoosterProfile p = HeapBoosterProfiles.select(platform);
            if (p != null)
                session = new HeapBoosterSession(p);
            rebuildMenu();
            if (session != null && session.profile.backend == ARMV6_XGOLD) {
                loadPending();
                if (pendingMiB != 0) {
                    reopenedRequest = true;
                    startOperation(OP_CHECK, 0);
                    return;
                }
            }
        }
        if (!busy)
            Display.getDisplay(this).setCurrent(menu);
    }
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
    private int firstTestMiB() {
        // The 302's ordinary foreground quota is already 4 MiB in 15.09.
        return growthLimit != 0 ? (growthLimit <= 4 * 1048576 ? 3 : 4)
                : session != null && "RM-813".equals(session.profile.id) ? 6
                                                                         : 3;
    }
    private void rebuildMenu() {
        menu = new List("S40 RAM Booster 0.10.0", List.IMPLICIT);
        menu.append("Check phone", null);
        sizeCount = 0;
        if (session != null) {
            for (int i = 0; poolBytes != 0 && i < sizes.length; i++) {
                if (poolBytes != 0 && sizes[i] * 1048576 > poolBytes
                        && sizes[i] * 1048576 > growthLimit)
                    break;
                menu.append(sizes[i] + " MiB"
                                + (sizes[i] == firstTestMiB() ? " - recommended"
                                                : sizes[i] == 8
                                                        || (growthLimit != 0 && sizes[i] >= 4)
                                                ? " - experimental"
                                                : ""),
                        null);
                sizeCount++;
            }
            restoreIndex = menu.append("Restore default", null);
        } else
            restoreIndex = -1;
        helpIndex = menu.append("How to test", null);
        menu.addCommand(exit);
        menu.setCommandListener(this);
    }
    public void commandAction(Command c, Displayable d) {
        if (busy)
            return;
        if (c == exit || c == closeBooster) {
            closeApp();
            return;
        }
        if (c == useFirst) {
            startOperation(OP_APPLY, firstTestMiB());
            return;
        }
        if (c == checkAgain) {
            startOperation(OP_CHECK, 0);
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
            show("How to use",
                    "1. Tap Check phone.\n2. Choose the recommended size.\n"
                            + "3. Follow the button on the result screen.\n\n"
                            + "A higher memory limit helps newly opened games. "
                            + "Already running games keep their old limit.\n\n"
                            + "For a full reset, restart the phone.");
            return;
        }
        if (session == null) {
            show("Unsupported phone",
                    platform
                            + ("\n\nNo memory map available for this model. Profiles: Nokia 301, "
                                    + "Asha 302/306, C2-01, C3-00, 6300, 6303 classic and 6700 classic."));
            return;
        }
        if (session.needsRestart) {
            show("Restart required", HeapBoosterDiagnostics.reason(session.error));
            return;
        }
        startOperation(choice == 0 ? OP_CHECK : (choice == restoreIndex ? OP_RESTORE : OP_APPLY),
                choice > 0 && choice <= sizeCount ? sizes[choice - 1] : 0);
    }
    public void commandAction(Command c, Item item) {
        commandAction(c, (Displayable) null);
    }
    private void closeApp() {
        if (pendingMiB != 0) {
            closeRequested = true;
            pendingSaved = savePending();
        }
        notifyDestroyed();
    }
    private void startOperation(int nextOperation, int nextMiB) {
        if (busy || session == null)
            return;
        if (session.needsRestart) {
            show("Restart phone", "Restart the phone before trying again.");
            return;
        }
        operation = nextOperation;
        mib = nextMiB;
        busy = true;
        Form working = new Form(operation == OP_CHECK ? "Checking phone"
                        : operation == OP_APPLY       ? "Setting memory"
                                                      : "Restoring default");
        working.append("Please wait...");
        Display.getDisplay(this).setCurrent(working);
        new Thread(this).start();
    }
    public void run() {
        long started = System.currentTimeMillis();
        automaticRetries = retryOpenedBase = retryLookupBase = 0;
        session.execute(operation, mib);
        // Retry only the known pre-native address-change stop, after execute()
        // has repaired the temporary view. Never repeat an Apply or native call.
        if (operation == OP_CHECK && session.error == 24 && !session.nativeCalled
                && !session.needsRestart) {
            retryOpenedBase = session.openedViewBase;
            retryLookupBase = session.lookupViewBase;
            automaticRetries = 1;
            session.execute(OP_CHECK, 0);
        }
        // Measure outside the forged-memory interval, including its cleanup.
        operationMillis = System.currentTimeMillis() - started;
        try {
            reportResult();
        } finally {
            busy = false;
        }
    }
    private void reportResult() {
        if (session.resolver.pools == 1 && session.result[W_POOL_BYTES] != 0) {
            poolBytes = session.result[W_POOL_BYTES];
            // The ordinary 3 MiB backend does not report growth capability.
            // Keep a successful Check's result across ordinary quota changes;
            // a failed Check has not established a replacement capability.
            if (session.result[W_GROWTH_LIMIT] != 0
                    || (operation == OP_CHECK && session.error == 0))
                growthLimit = session.result[W_GROWTH_LIMIT];
            sizes = growthLimit >= 8 * 1048576 ? new int[] {3, 4, 5, 6, 7, 8}
                    : growthLimit != 0         ? new int[] {3, 4, 5, 6}
                                               : new int[] {3, 4, 6, 8};
            rebuildMenu();
        }
        int requested = pendingMiB;
        boolean waiting = session.error == 0 && session.result[W_GROWTH_STATE] == 1
                && session.profile.backend == ARMV6_XGOLD;
        if (waiting && (operation == OP_APPLY || pendingMiB == 0)) {
            pendingMiB = operation == OP_APPLY ? mib : -1;
            closeRequested = false;
            reopenedRequest = false;
            pendingSaved = savePending();
        } else if (session.error == 0 && !waiting && pendingMiB != 0) {
            pendingMiB = 0;
            closeRequested = false;
            pendingSaved = savePending();
        }
        String summary = resultSummary(requested);
        String text;
        text = platform + "\n" + session.profile.id + "\nVersion: 0.10.0"
                + "\nStatus: " + session.error;
        if (session.error != 0)
            text += "\n" + HeapBoosterDiagnostics.reason(session.error);
        text += "\nStage: " + HeapBoosterDiagnostics.stage(session.stage);
        text += "\nAutomatic retries: " + automaticRetries;
        if (automaticRetries != 0)
            text += "\nFirst view opened / lookup: " + hex(retryOpenedBase) + " / "
                    + hex(retryLookupBase);
        text += "\nResumed saved request: " + reopenedRequest
                + "\nClose requested: " + closeRequested + "\nSaved target: " + pendingMiB;
        text += "\nLaunch limit: " + session.result[W_CURRENT_MIB] + " MiB (0 = default)"
                + "\nJava arena capacity: " + session.result[W_POOL_BYTES] / 1024 + " KiB";
        text += "\nOperation time: " + operationMillis + " ms";
        text += "\nNative call: " + (session.nativeCalled ? "attempted" : "not made");
        if (session.failure != null)
            text += "\nException: " + session.failure.toString();
        text += "\nPolicy site: "
                + (session.result[W_SITE] == 0 ? "not detected" : hex(session.result[W_SITE]));
        if (growthLimit != 0)
            text += "\nGrowth state: " + session.result[W_GROWTH_STATE]
                    + "\nHeap base: " + hex(session.result[W_HEAP_BASE]) + "\nReservation: "
                    + (session.result[W_RESERVATION] == 0
                                    ? "not verified"
                                    : session.result[W_RESERVATION] / 1024 + " KiB");
        if (session.profile.backend == ARMV6_XGOLD && session.result[W_GROWTH_STATE] != 0) {
            text += "\nExit hook calls: " + session.result[X_EXIT_CALLS]
                    + "\nExtension attempts: " + session.result[X_GROW_ATTEMPTS]
                    + "\nLast callback CPSR: " + hex(session.result[X_CALLBACK_CPSR])
                    + "\nExpansion reason: " + growthReason(session.result[X_WAIT_REASON])
                    + "\nHelper result: " + session.result[X_HELPER_RESULT]
                    + "\nTask arena: " + hex(session.result[X_AREA])
                    + "\nCommitted end: " + hex(session.result[X_COMMITTED_END])
                    + "\nLast tail node: " + hex(session.result[X_TAIL_NODE])
                    + "\nTail start / end: " + hex(session.result[X_TAIL_START]) + " / "
                    + hex(session.result[X_TAIL_END])
                    + "\nNext range: " + hex(session.result[X_NEXT_START])
                    + "\nTail field mask: " + hex(session.result[X_BAD_TAIL_FIELDS]);
        }
        if (session.error == 9 || (session.error >= 11 && session.error <= 24)) {
            text += "\nAnchored class: " + hex(session.anchoredClass);
            text += "\nView opened / lookup: " + hex(session.openedViewBase) + " / "
                    + hex(session.lookupViewBase);
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
                    + (session.result[W_STATUS] == 201 || session.result[W_STATUS] == 216
                                    ? "not measured"
                                    : hex(session.result[W_CPU]));
            if (session.result[W_STATUS] == 201 || session.result[W_STATUS] == 202
                    || session.result[W_STATUS] == 216)
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
        saveResult(summary + "\n\n" + detailsText);
        resultForm = show(resultTitle(), summary);
        resultForm.addCommand(details);
        if (session.error != 0) {
            addButton(resultForm, "Details", details);
        } else if (waiting && !reopenedRequest) {
            resultForm.removeCommand(back);
            addButton(resultForm, "Close booster", closeBooster);
        } else if (waiting) {
            addButton(resultForm, "Details", details);
        } else if (operation == OP_CHECK && requested != 0 && session.result[W_GROWTH_STATE] == 0
                && session.result[W_CURRENT_MIB] == 0) {
            addButton(resultForm, "Check phone", checkAgain);
        } else if (operation == OP_CHECK && sizeCount != 0 && session.result[W_CURRENT_MIB] == 0
                && session.result[W_GROWTH_STATE] != 1 && requested == 0) {
            addButton(resultForm, "Use " + firstTestMiB() + " MiB", useFirst);
        } else {
            addButton(resultForm, "Close booster", closeBooster);
        }
    }
    private String resultTitle() {
        if (session.needsRestart || session.error == 25 || session.error == 220)
            return "Restart phone";
        if (session.error != 0)
            return "Could not finish";
        if (session.result[W_GROWTH_STATE] == 1)
            return session.result[W_GROWTH_KIND] == 2 ? "Restarting Java"
                    : reopenedRequest                 ? "Not active"
                                                      : "Close and reopen";
        if (operation == OP_RESTORE)
            return "Default restored";
        if (operation == OP_APPLY || session.result[W_CURRENT_MIB] != 0)
            return "Memory limit set";
        return "Phone checked";
    }
    private String resultSummary(int requested) {
        if (session.needsRestart || session.error == 25 || session.error == 220)
            return "Restart the phone before trying again.\nCode: " + session.error;
        if (session.error != 0)
            return "Could not "
                    + (operation == OP_CHECK ? "check the phone" : "change the memory limit")
                    + ".\nCode: " + session.error + ". Open Details.";
        if (session.result[W_GROWTH_STATE] == 1) {
            if (session.result[W_GROWTH_KIND] == 2)
                return "Java is restarting to set " + mib
                        + " MiB.\nOpen the booster again and tap Check phone.";
            if (reopenedRequest)
                return (pendingMiB > 0 ? pendingMiB + " MiB did not activate."
                                       : "The boost did not activate.")
                        + "\nThe memory limit is unchanged.\nOpen Details for support.";
            return (pendingMiB > 0 ? pendingMiB + " MiB is not active yet."
                                   : "The boost is not active yet.")
                    + "\nClose this app, then open it again.\n"
                    + (pendingSaved ? "I'll check it automatically." : "Then tap Check phone.");
        }
        if (operation == OP_RESTORE)
            return "Newly opened games will use the phone's default memory limit.";
        int active = operation == OP_APPLY ? mib : session.result[W_CURRENT_MIB];
        if (active != 0)
            return active
                    + " MiB limit is active.\nClose the booster, then open your game or Heap Check.";
        if (requested != 0)
            return "The earlier request is no longer pending.\nNo boost is active. Tap Check phone to start again.";
        if (sizeCount == 0)
            return "No supported boost size fits the discovered Java arena.\nOpen Details for support.";
        return "Ready to try " + firstTestMiB() + " MiB.\nTap the button below.";
    }
    private void addButton(Form form, String label, Command command) {
        StringItem button = new StringItem(null, label, Item.BUTTON);
        button.setDefaultCommand(command);
        button.setItemCommandListener(this);
        form.append(button);
    }
    private void loadPending() {
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore("RamBoosterFlow", false);
            if (store.getNumRecords() != 0) {
                byte[] saved = store.getRecord(1);
                if (saved.length == 2 && (saved[0] == -1 || (saved[0] >= 4 && saved[0] <= 6))) {
                    pendingMiB = saved[0];
                    closeRequested = saved[1] == 1;
                    pendingSaved = true;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (store != null) {
                try {
                    store.closeRecordStore();
                } catch (Throwable ignored) {
                }
            }
        }
    }
    private boolean savePending() {
        RecordStore store = null;
        try {
            byte[] saved = {(byte) pendingMiB, (byte) (closeRequested ? 1 : 0)};
            store = RecordStore.openRecordStore("RamBoosterFlow", true);
            if (store.getNumRecords() == 0)
                store.addRecord(saved, 0, saved.length);
            else
                store.setRecord(1, saved, 0, saved.length);
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (store != null) {
                try {
                    store.closeRecordStore();
                } catch (Throwable ignored) {
                }
            }
        }
    }
    private void saveResult(String text) {
        RecordStore store = null;
        try {
            byte[] bytes = text.getBytes("UTF-8");
            store = RecordStore.openRecordStore("RamBoosterResult", true);
            if (store.getNumRecords() == 0)
                store.addRecord(bytes, 0, bytes.length);
            else
                store.setRecord(1, bytes, 0, bytes.length);
        } catch (Throwable ignored) {
        } finally {
            if (store != null) {
                try {
                    store.closeRecordStore();
                } catch (Throwable ignored) {
                }
            }
        }
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
    private static String growthReason(int reason) {
        switch (reason) {
            case 1:
                return "IRQ disabled";
            case 2:
                return "callback busy";
            case 3:
                return "callback context changed";
            case 4:
                return "no uncommitted tail";
            case 5:
                return "tail layout differs";
            case 6:
                return "adjacent range occupied";
            case 7:
                return "no tail at committed end";
            case 8:
                return "8 MiB reservation established";
            case 9:
                return "invalid arena state";
            case 10:
                return "reservation readback failed";
            default:
                return "not attempted";
        }
    }
    private static String hex(int value) {
        String s = Integer.toHexString(value).toUpperCase();
        while (s.length() < 8)
            s = "0" + s;
        return s;
    }
}
