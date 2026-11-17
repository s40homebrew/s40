/* SPDX-License-Identifier: MIT */
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

/** J2ME dashboard; each Refresh runs a short shared-homebrew native session. */
public final class BatteryInfo extends MIDlet implements CommandListener, Runnable {
    private final Command refresh = new Command("Refresh", Command.OK, 1);
    private final Command exit = new Command("Exit", Command.EXIT, 2);
    private Screen screen;
    private NativeSession session;
    private volatile boolean busy;
    private boolean closed;
    private volatile Reading reading;
    private volatile String message = "Press Refresh to read the battery.";

    protected void startApp() {
        if (screen != null)
            return;
        screen = new Screen();
        screen.addCommand(exit);
        screen.setCommandListener(this);
        String platform = null;
        try {
            platform = System.getProperty("microedition.platform");
        } catch (SecurityException unavailable) {
        }
        FirmwareProfile profile = FirmwareProfiles.select(platform);
        if (profile == null) {
            message = "No native launcher is available for this phone.";
        } else {
            session = new NativeSession(profile, "/application.bin", FirmwareResolver.CAP_BATTERY);
            screen.addCommand(refresh);
        }
        Display.getDisplay(this).setCurrent(screen);
        if (session != null)
            readBattery();
    }

    protected void pauseApp() {}
    protected synchronized void destroyApp(boolean unconditional) {
        closed = true;
    }

    public void commandAction(Command command, Displayable target) {
        if (command == refresh) {
            readBattery();
        } else if (command == exit) {
            synchronized (this) {
                closed = true;
            }
            notifyDestroyed();
        }
    }

    private synchronized void readBattery() {
        if (busy || closed || session == null)
            return;
        busy = true;
        screen.removeCommand(refresh);
        screen.removeCommand(exit);
        screen.repaint();
        screen.serviceRepaints();
        new Thread(this).start();
    }

    public void run() {
        session.execute(false);
        synchronized (this) {
            if (closed)
                return;
            int[] result = session.result;
            if (session.error == 0 && session.failure == null && result[16] == 0x42415432) {
                reading = new Reading(result);
                message = null;
            } else if (session.error == 41) {
                message = "Battery information is unavailable on this firmware.";
            } else if (session.error == 42) {
                message = "Battery estimate is not ready. Try Refresh again shortly.";
            } else {
                message = "Could not read battery. Check " + session.error;
                if (session.failure != null)
                    message += ": " + session.failure.toString();
                if (session.error >= 100)
                    message += ". " + session.resolver.failureName();
            }
            busy = false;
            if (session.error == 90) {
                message = "Restart the phone before trying again. Check 90.";
            } else {
                screen.addCommand(refresh);
            }
            screen.addCommand(exit);
            screen.repaint();
        }
    }

    private static final class Reading {
        final int percent, accent;
        final String headline, remaining, nominal, voltage, indicator, charge;
        Reading(int[] values) {
            int full = values[18];
            boolean available = values[22] != 0 && values[17] >= 0 && values[17] <= full;
            percent = available ? Math.min(100, (values[17] * 100 + full / 2) / full) : -1;
            headline = available ? percent + "%" : "--%";
            remaining = available ? values[17] + " mAh" : "Unavailable";
            nominal = full + " mAh";
            int mv = values[20];
            voltage = mv >= 2000 && mv <= 6000 ? mv + " mV" : "Unavailable";
            indicator = values[19] >= 0 && values[19] <= 4 ? values[19] + " / 4" : "Unavailable";
            boolean charging = values[21] == 2 || values[21] == 4 || values[21] == 6;
            accent = charging || percent < 0 ? 0x77D4C2
                    : percent <= 15          ? 0xF28D88
                    : percent <= 30          ? 0xEBC57A
                                             : 0x77D4C2;
            switch (values[21]) {
                case 1:
                    charge = "Not charging";
                    break;
                case 2:
                    charge = "Charging";
                    break;
                case 4:
                    charge = "Charge complete";
                    break;
                case 5:
                    charge = "Charger unavailable";
                    break;
                case 6:
                    charge = "Charger connected";
                    break;
                default:
                    charge = "Charge status unknown";
                    break;
            }
        }
    }

    private final class Screen extends Canvas {
        private static final int BG = 0x111A23, PANEL = 0x1A2733, LINE = 0x2A3946;
        private static final int TEXT = 0xEFF5F8, MUTED = 0xA1B0BC, ACCENT = 0x77D4C2;
        private final Font small =
                Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        private final Font value =
                Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
        private final Font large = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE);

        Screen() {
            setFullScreenMode(false);
        }

        protected void keyPressed(int key) {
            if (getGameAction(key) == FIRE || key == KEY_NUM5)
                readBattery();
        }

        protected void paint(Graphics g) {
            int w = getWidth(), h = getHeight();
            g.setColor(BG);
            g.fillRect(0, 0, w, h);
            Reading data = reading;
            if (busy || message != null || data == null) {
                g.setFont(large);
                g.setColor(TEXT);
                int y = Math.max(12, h / 3 - large.getHeight());
                g.drawString(busy ? "Reading..." : "Unable to read", w / 2, y,
                        Graphics.TOP | Graphics.HCENTER);
                g.setFont(small);
                g.setColor(MUTED);
                wrap(g, busy ? "Please wait." : message, 20, y + large.getHeight() + 14, w - 40,
                        h - 16);
                return;
            }

            // Nokia owns the title and softkeys. All positions below use the
            // actual Canvas viewport and real font heights, including its
            // smaller content area beneath the phone's title/status bars.
            int margin = 14;
            int y = 8;
            g.setFont(large);
            g.setColor(data.accent);
            g.drawString(data.headline, w / 2, y, Graphics.TOP | Graphics.HCENTER);
            y += large.getHeight() + 2;
            g.setFont(small);
            g.setColor(MUTED);
            g.drawString(data.percent >= 0 ? "Estimated charge" : "Estimate unavailable", w / 2, y,
                    Graphics.TOP | Graphics.HCENTER);
            y += small.getHeight() + 5;
            g.setColor(data.accent);
            g.drawString(data.charge, w / 2, y, Graphics.TOP | Graphics.HCENTER);
            y += small.getHeight() + 12;
            g.setColor(LINE);
            g.fillRoundRect(margin, y, w - 2 * margin, 5, 4, 4);
            if (data.percent > 0) {
                g.setColor(data.accent);
                g.fillRoundRect(
                        margin, y, Math.max(3, (w - 2 * margin) * data.percent / 100), 5, 4, 4);
            }
            y += 19;
            int rowHeight = (h - y - 10) / 4;
            // Four full-width rows avoid cramped labels and measure their
            // contents separately so neither labels nor values can overlap.
            g.setColor(PANEL);
            g.fillRoundRect(margin, y, w - 2 * margin, rowHeight * 4, 10, 10);
            row(g, y, rowHeight, "Remaining", data.remaining, true);
            row(g, y + rowHeight, rowHeight, "Full capacity", data.nominal, true);
            row(g, y + rowHeight * 2, rowHeight, "Voltage", data.voltage, true);
            row(g, y + rowHeight * 3, rowHeight, "Phone level", data.indicator, false);
        }

        private void row(Graphics g, int y, int height, String label, String text, boolean line) {
            int w = getWidth(), left = 25, right = w - 25;
            Font numberFont = value;
            if (small.stringWidth(label) + value.stringWidth(text) + 10 > right - left)
                numberFont = small;
            g.setFont(small);
            g.setColor(MUTED);
            int labelWidth = Math.max(0, right - left - numberFont.stringWidth(text) - 10);
            g.setClip(left, y, labelWidth, height);
            g.drawString(label, left, y + (height - small.getHeight()) / 2,
                    Graphics.TOP | Graphics.LEFT);
            g.setClip(0, 0, w, getHeight());
            g.setFont(numberFont);
            g.setColor(TEXT);
            g.drawString(text, right, y + (height - numberFont.getHeight()) / 2,
                    Graphics.TOP | Graphics.RIGHT);
            if (line) {
                g.setColor(LINE);
                g.drawLine(left, y + height - 1, right, y + height - 1);
            }
        }

        private void wrap(Graphics g, String text, int x, int y, int width, int bottom) {
            if (text == null)
                return;
            int start = 0;
            while (start < text.length() && y + small.getHeight() <= bottom) {
                int end = start + 1, space = -1;
                while (end <= text.length()
                        && small.substringWidth(text, start, end - start) <= width) {
                    if (text.charAt(end - 1) == ' ')
                        space = end - 1;
                    end++;
                }
                end--;
                if (end < text.length() && space > start)
                    end = space;
                if (end <= start)
                    end = start + 1;
                g.drawSubstring(text, start, end - start, x, y, Graphics.TOP | Graphics.LEFT);
                start = end;
                while (start < text.length() && text.charAt(start) == ' ')
                    start++;
                y += small.getHeight() + 4;
            }
        }
    }
}
