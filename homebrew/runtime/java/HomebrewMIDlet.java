/* SPDX-License-Identifier: MIT */
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

/** Reusable MIDlet shell. Applications supply their name and control/help text. */
public abstract class HomebrewMIDlet extends MIDlet implements CommandListener {
    private Command queryCommand;
    private final Command checkCommand = new Command("Check firmware", Command.SCREEN, 2);
    private final Command exitCommand = new Command("Exit", Command.EXIT, 2);
    private Screen screen;
    private String[] lines;
    private boolean running, scanOnly;
    private NativeSession session;
    private String platformName, buildVersion;
    private int checkpointCode = -1, lastCheckpoint = -1;

    protected abstract String applicationName();
    protected abstract String[] instructions(int inputLayout);
    protected String startLabel() {
        return "Start";
    }
    protected String nativeResource() {
        return "/application.bin";
    }
    /** Opt-in video-readable checkpoints; ordinary applications have no delay. */
    protected boolean launchDiagnostics() {
        return false;
    }

    protected int nativeOptions() { return 0; }

    protected void configureCommands(Displayable target, boolean enabled) {}

    protected final void refreshInstructions() {
        if (session != null) {
            checkpointCode = -1;
            lines = instructions(session.profile.inputLayout);
            screen.repaint();
        }
    }

    protected void startApp() {
        if (screen == null) {
            screen = new Screen();
            queryCommand = new Command(startLabel(), Command.OK, 1);
            String platform = null;
            try {
                platform = System.getProperty(FirmwareProfiles.PLATFORM_PROPERTY);
            } catch (SecurityException unavailable) {
                // An unavailable identity must not select an arbitrary bootstrap.
            }
            FirmwareProfile profile = FirmwareProfiles.select(platform);
            if (profile != null
                    && (FirmwareProfiles.RM443_BOOTSTRAP.equals(profile.bootstrapId)
                            || FirmwareProfiles.CARRIER_BOOTSTRAP.equals(profile.bootstrapId))) {
                platformName = platform == null ? "Firmware name unavailable" : platform;
                buildVersion = getAppProperty("MIDlet-Version");
                session = new NativeSession(profile, nativeResource(),
                        launchDiagnostics() ? new NativeSession.Progress() {
                            public void checkpoint(int code, int percent) {
                                showCheckpoint(code, percent);
                            }
                        } : null);
                lines = instructions(profile.inputLayout);
                screen.addCommand(queryCommand);
                screen.addCommand(checkCommand);
                configureCommands(screen, true);
            } else {
                lines = new String[] {"DEVICE NOT SUPPORTED",
                        platform == null ? "Device name unavailable" : platform,
                        "No compatible device profile", "is included in this version."};
            }
            screen.addCommand(exitCommand);
            screen.setCommandListener(this);
            Display.getDisplay(this).setCurrent(screen);
        }
    }

    protected void pauseApp() {}

    protected void destroyApp(boolean unconditional) {}

    public void commandAction(Command command, Displayable displayable) {
        if (command == exitCommand) {
            notifyDestroyed();
        } else if ((command == queryCommand || command == checkCommand) && !running) {
            scanOnly = command == checkCommand;
            running = true;
            new Thread(new Runnable() {
                public void run() {
                    runQuery();
                }
            }).start();
        }
    }

    private void runQuery() {
        screen.removeCommand(queryCommand);
        screen.removeCommand(checkCommand);
        screen.removeCommand(exitCommand);
        configureCommands(screen, false);
        lines = new String[] {
                "Checking firmware...", "ROM bounds, exports, native ABI", "Please wait."};
        screen.repaint();
        screen.serviceRepaints();
        session.execute(scanOnly, nativeOptions());
        running = false;
        screen.addCommand(queryCommand);
        screen.addCommand(checkCommand);
        screen.addCommand(exitCommand);
        configureCommands(screen, true);
        showResult(session.error, session.failure);
    }

    private void showCheckpoint(int code, int percent) {
        // NativeSession calls only before forging or after restoring the heap.
        // serviceRepaints completes this frame before the next risky operation.
        String detail;
        switch (code) {
            case 0x10:
                detail = "Firmware scan + bootstrap";
                break;
            case 0x20:
                detail = "Native loader ready";
                break;
            case 0x21:
                detail = "Allocating native image";
                break;
            case 0x22:
                detail = "Loading native image: " + percent + "%";
                break;
            case 0x23:
                detail = "Entering native application";
                break;
            default:
                detail = "Launch checkpoint";
                break;
        }
        lines = new String[] {detail, platformName, "Version " + buildVersion};
        checkpointCode = code;
        lastCheckpoint = code;
        screen.repaint();
        screen.serviceRepaints();
        // Only S40/Java checkpoints pause. Streaming updates have no delay;
        // no Java callback is possible once native takeover begins.
        if (code != 0x22) {
            try {
                Thread.sleep(400);
            } catch (InterruptedException interrupted) { /* Continue the launch. */
            }
        }
    }

    private void showResult(int error, Throwable thrown) {
        checkpointCode = -1;
        int[] result = session.result;
        FirmwareProfile profile = session.profile;
        FirmwareResolver resolver = session.resolver;
        long discoveryMs = session.discoveryMs;
        // Application exit statuses may overlap resolver error numbers.
        if (scanOnly || (!session.applicationStarted && error >= 100)) {
            lines = new String[] {error == 0 && thrown == null ? "FIRMWARE CHECK PASSED"
                                                               : "FIRMWARE CHECK FAILED",
                    thrown == null ? "Check " + error : thrown.toString(),
                    error == 0 && thrown == null
                            ? (resolver.reused ? "Rechecked: " : "Full scan: ") + discoveryMs
                                    + " ms"
                            : "Phase " + resolver.phase + " / " + resolver.failureName(),
                    profile.scanWindows == null
                            ? "ROM end " + hex(resolver.romStart + resolver.romBytes)
                            : profile.id + " / " + (profile.scanWindows.length / 2)
                                    + " ROM windows",
                    "Exports " + resolver.exportCount, "System " + hex(resolver.systemDescriptor),
                    "Cache " + hex(resolver.api[FirmwareApi.CACHE_SYNC]),
                    "KNI get " + hex(resolver.api[FirmwareApi.KNI_PARAM]),
                    "KNI return " + hex(resolver.api[FirmwareApi.KNI_RETURN]),
                    "Alloc " + hex(resolver.api[FirmwareApi.FW_OS_BLOCK_ALLOC_NO_WAIT]),
                    "Events " + hex(resolver.api[FirmwareApi.FW_EVENT_POP]),
                    "LCD owner " + hex(resolver.api[FirmwareApi.FW_LCD_OWNER]),
                    error == 0 && thrown == null ? "Ready: choose Start"
                                                 : "No native application was started"};
            screen.repaint();
            screen.serviceRepaints();
            return;
        }
        StringBuffer message = new StringBuffer();
        for (int i = 0; i < 256; i++) {
            int c = (result[16 + (i >> 2)] >>> ((i & 3) * 8)) & 255;
            if (c == 0)
                break;
            message.append((char) c);
        }
        String text = message.toString();
        lines = new String[15];
        lines[0] = error == 0 && thrown == null ? "NATIVE APPLICATION RETURNED"
                                                : "NATIVE APPLICATION FAILED";
        lines[1] = thrown == null ? "Check " + error : thrown.toString();
        lines[2] = "Stage " + result[4] + " detail " + result[5];
        lines[3] = "Task " + result[6] + " app " + result[7];
        lines[4] = "Last key " + result[8] + " type " + result[9];
        lines[5] = "Native ms " + result[10];
        if (error == 12) {
            lines[2] = "Cache flags " + hex(result[7]);
            lines[3] = "Loader flags " + hex(result[8]);
            lines[4] = "Cache code " + hex(result[9]);
            lines[5] = "Loader code " + hex(result[10]);
        } else if (error == 18) {
            lines[2] = "Loader status " + result[3];
        }
        for (int i = 6; i < 14; i++) {
            int start = (i - 6) * 32;
            lines[i] = start < text.length()
                    ? text.substring(start, Math.min(start + 32, text.length()))
                    : "";
        }
        lines[14] = launchDiagnostics() && lastCheckpoint >= 0
                ? "Last checkpoint " + Integer.toHexString(lastCheckpoint).toUpperCase()
                : (resolver.reused ? "Recheck " : "Scan ") + discoveryMs + " ms";
        screen.repaint();
        screen.serviceRepaints();
    }

    private static String hex(int value) {
        return "0x" + Integer.toHexString(value).toUpperCase();
    }

    private final class Screen extends Canvas {
        // 5x7 glyph rows packed into five-bit groups. Render at 4x scale so a
        // distant phone video can distinguish the code without reading small text.
        private final int[][] digits = launchDiagnostics() ? new int[][] {
                {14,17,19,21,25,17,14}, {4,12,4,4,4,4,14},
                {14,17,1,2,4,8,31}, {30,1,1,14,1,1,30},
                {2,6,10,18,31,2,2}, {31,16,16,30,1,1,30},
                {14,16,16,30,17,17,14}, {31,1,2,4,8,8,8},
                {14,17,17,14,17,17,14}, {14,17,17,15,1,1,14},
                {14,17,17,31,17,17,17}, {30,17,17,30,17,17,30},
                {15,16,16,16,16,16,15}, {30,17,17,17,17,17,30},
                {31,16,16,30,16,16,31}, {31,16,16,30,16,16,16},
        } : null;
        Screen() {
            setFullScreenMode(true);
        }
        protected void paint(Graphics graphics) {
            int width = getWidth();
            int height = getHeight();
            graphics.setColor(0x000000);
            graphics.fillRect(0, 0, width, height);
            Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            graphics.setFont(font);
            int lineHeight = font.getHeight() + 3;
            int y = 5;
            graphics.setColor(0x00FF80);
            if (checkpointCode >= 0) {
                // Native and physical markers replace exactly this 64x40
                // patch. Keep all old digits inside it: a second stale code
                // elsewhere on screen would make a phone recording ambiguous.
                drawDigit(graphics, (checkpointCode >> 4) & 15, 6, 4);
                drawDigit(graphics, checkpointCode & 15, 34, 4);
                graphics.drawString("Boot stage", 74, 12, Graphics.TOP | Graphics.LEFT);
                y = 48;
            }
            graphics.drawString(applicationName(), 6, y, Graphics.TOP | Graphics.LEFT);
            y += lineHeight + 3;
            graphics.setColor(0xFFFFFF);
            for (int i = 0; i < lines.length && y < height; i++) {
                graphics.drawString(lines[i], 6, y, Graphics.TOP | Graphics.LEFT);
                y += lineHeight;
            }
        }

        private void drawDigit(Graphics graphics, int digit, int x, int y) {
            for (int row = 0; row < 7; row++)
                for (int column = 0; column < 5; column++)
                    if ((digits[digit][row] & (16 >> column)) != 0)
                        graphics.fillRect(x + column * 4, y + row * 4, 4, 4);
        }
    }
}
