/* SPDX-License-Identifier: MIT */
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.midlet.MIDlet;

/**
 * One-shot installer. Successful installation leaves no Java UI or task alive;
 * the native bridge belongs to the firmware protocol task until the next boot.
 */
public final class PcBridgeInstaller extends MIDlet implements Runnable, CommandListener {
    private static final int INSTALL = 1;
    private static final int COMPLETED = 0x53343042;
    private boolean started;
    private NativeSession session;

    protected void startApp() {
        if (started)
            return;
        started = true;
        String platform;
        try {
            platform = System.getProperty("microedition.platform");
        } catch (SecurityException unavailable) {
            platform = null;
        }
        FirmwareProfile profile = FirmwareProfiles.select(platform);
        if (profile == null || !"RM-443".equals(profile.id)) {
            fail("Requires Nokia 6303 classic (RM-443).\nDetected: "
                    + (platform == null ? "unavailable" : platform));
            return;
        }
        session = new NativeSession(profile, "/application.bin", FirmwareResolver.CAP_BRIDGE);
        new Thread(this).start();
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    public void run() {
        session.execute(false, INSTALL);
        if (session.error == 0 && session.failure == null && session.applicationStarted
                && session.result[8] == COMPLETED
                && (session.result[5] == 1 || session.result[5] == 2)) {
            notifyDestroyed();
            return;
        }
        String message = "Installation failed. Check " + session.error + ".";
        if (session.error == 90)
            message += "\nBootstrap cleanup failed; reboot before trying again.";
        if (session.failure != null)
            message += "\n" + session.failure.toString();
        if (!session.applicationStarted && session.error >= 100)
            message += "\nDiscovery: " + session.resolver.failureName();
        message += "\nNative stage " + session.result[4] + ", outcome " + session.result[5]
                + ", marker 0x" + Integer.toHexString(session.result[8]);
        StringBuffer nativeMessage = new StringBuffer();
        for (int i = 0; i < 256; i++) {
            int c = (session.result[16 + (i >> 2)] >>> ((i & 3) * 8)) & 255;
            if (c == 0)
                break;
            nativeMessage.append((char) c);
        }
        if (nativeMessage.length() != 0)
            message += "\n" + nativeMessage.toString();
        fail(message);
    }

    private void fail(String message) {
        final Form failure = new Form("S40 PC Bridge");
        failure.append(message);
        failure.addCommand(new Command("Exit", Command.EXIT, 1));
        failure.setCommandListener(this);
        Display.getDisplay(this).setCurrent(failure);
    }

    public void commandAction(Command command, Displayable source) {
        notifyDestroyed();
    }
}
