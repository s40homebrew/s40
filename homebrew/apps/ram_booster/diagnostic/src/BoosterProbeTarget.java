/* SPDX-License-Identifier: MIT */

/** Loaded by name only after BoosterProbe is on screen. No Apply/Restore path. */
public final class BoosterProbeTarget implements Runnable {
    private HeapBoosterSession session;
    private boolean initialized;
    private int nextStage = HeapBoosterSession.MEMORY_VIEW;
    private String report;

    public BoosterProbeTarget() {}

    public void run() {
        if (!initialized) {
            HeapBoosterProfile profile =
                    HeapBoosterProfiles.select(System.getProperty("microedition.platform"));
            if (profile == null)
                throw new IllegalArgumentException("No profile for this platform");
            session = new HeapBoosterSession(profile);
            initialized = true;
            report = "OK session constructed: " + profile.id;
            return;
        }
        if (nextStage > HeapBoosterSession.NATIVE_CALL || session.needsRestart)
            return;
        int requested = nextStage;
        session.probe(requested);
        // Formatting happens only after executeTo's finally restored the view.
        report = (session.error == 0 ? "PASS" : "STOP") + " test " + requested
                + "\nProbe 0.2.0\n" + System.getProperty("microedition.platform") + "\n"
                + session.profile.id + "\nStatus: " + session.error
                + "\nLast stage: " + HeapBoosterDiagnostics.stage(session.stage)
                + "\nNative call: " + (session.nativeCalled ? "attempted" : "not made");
        if (session.error != 0)
            report += "\n" + HeapBoosterDiagnostics.reason(session.error);
        if (session.failure != null)
            report += "\n" + session.failure.toString();
        report += "\nPool KiB: " + session.result[6] / 1024 + "\nPolicy: " + hex(session.result[4])
                + "\nMatches: " + session.resolver.sites + "/" + session.resolver.pools + "/"
                + session.resolver.caches + "\nClass/methods: " + hex(session.result[49]) + "/"
                + hex(session.result[50]) + "\nMethod count: " + session.result[51]
                + "\nNative: " + hex(session.result[52]) + "\nCarrier: " + hex(session.result[53])
                + "\nCarrier bytes: " + session.result[56]
                + "\nPayload code: " + hex(session.result[58])
                + "\nCPSR: " + hex(session.result[8])
                + "\n\nNo launch-quota change requested.";
        if (session.error == 0)
            nextStage++;
    }

    public String toString() {
        return report;
    }

    private static String hex(int value) {
        String text = Integer.toHexString(value).toUpperCase();
        while (text.length() < 8)
            text = "0" + text;
        return text;
    }
}
