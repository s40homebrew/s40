/* SPDX-License-Identifier: MIT */
import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;

/** A standalone native OS call. No shared homebrew runtime or firmware addresses. */
public final class NativeTime extends MIDlet implements CommandListener, Runnable {
    private static final int LENGTH = 0x40000000;
    // Seven ARMv5 instructions, one function pointer, two result words.
    // PC-relative stores keep both the code and its output position independent.
    private static final int[] CODE = {
        0xE92D4010, // push {r4,lr}           (8-byte stack alignment)
        0xE59F2010, // ldr r2,[pc,#16]        (word 7: resolved OS function)
        0xE12FFF32, // blx r2                (os_current_time_read_in_ms)
        0xE58F0010, // str r0,[pc,#16]        (word 9: milliseconds)
        0xE3A00001, // mov r0,#1
        0xE58F0004, // str r0,[pc,#4]         (word 8: completed)
        0xE8BD8010, // pop {r4,pc}
        0, 0, 0
    };

    private final TimeResolver rom = new TimeResolver();
    private final Command query = new Command("Read native time", Command.OK, 1);
    private final Command exit = new Command("Exit", Command.EXIT, 2);
    private final Screen screen = new Screen();
    private volatile String[] lines = {"Press Read native time.", "First scan may take a while."};
    private volatile boolean running;
    private boolean started;
    private int milliseconds;

    // These five allocations must remain consecutive and in this order.
    private int[] payload, canary, view, destination;
    private Object[] pointers;
    private int base, method, savedFlags;
    private boolean forged, patched;
    private boolean rapu, storageLoaded, literalsChanged, storageChanged, needsRestart;
    private int carrier, storage, saved0, saved1, saved2, saved3;
    private static final int[] CARRIER = {0xE01D4280, 0xE0114790, 0xE0194798,
        0xE00D4800, 0xE0014900, 0xE7ED4A00, 0xE7ED4B00, 0xE7F11C04,
        0xE7E9B510, 0x0000BD10};

    // A zero-argument void method requires neither parameter access nor a KNI
    // return value. The ARM routine writes its answer into reserved result words.
    private static native void call();

    protected void startApp() {
        if (running) return;
        if (!started) {
            started = true;
            String platform = System.getProperty("microedition.platform");
            // Version-independent; do not probe an unrelated phone's memory map.
            String model = platform == null ? "" : platform;
            int slash = model.indexOf('/');
            if (slash >= 0) model = model.substring(0, slash);
            rapu = isRapuModel(model);
            rom.rapuMode = rapu;
            if (rapu || model.equals("Nokia6303c") || model.equals("Nokia6303classic")) {
                screen.addCommand(query);
            } else {
                lines = new String[] {"For Nokia 6303c or Asha 302.", model};
            }
            screen.addCommand(exit);
            screen.setCommandListener(this);
        }
        Display.getDisplay(this).setCurrent(screen);
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}

    private static boolean isRapuModel(String model) {
        // RM-813's product-info field 5 supplies Nokia302 or Nokia3020.
        return "Nokia302".equals(model) || "Nokia3020".equals(model)
                || "Nokia302-1".equals(model) || "NokiaAsha302".equals(model);
    }

    public void commandAction(Command command, Displayable displayable) {
        if (running || (needsRestart && command != exit)) return;
        if (command == exit) notifyDestroyed();
        if (command == query) {
            running = true;
            new Thread(this).start();
        }
    }

    public void run() {
        screen.removeCommand(query);
        screen.removeCommand(exit);
        lines = new String[] {"Resolving native API...", "Please wait."};
        screen.repaint();
        screen.serviceRepaints();
        int error = 0;
        Throwable failure = null;
        long startedAt = System.currentTimeMillis();
        try {
            error = queryNative();
        } catch (Throwable t) {
            failure = t;
        } finally {
            // Restore only words we actually changed, before formatting text.
            boolean restored = true;
            try {
                restored = restoreBindings();
            } catch (Throwable t) {
                failure = t;
                restored = false;
            } finally {
                try {
                    if (forged) {
                        view[0x3FFFFFFF] = 1; // index scaling wraps to array.length
                        forged = false;
                        if (view.length != 1) restored = false;
                    }
                } catch (Throwable t) { failure = t; restored = false; }
            }
            if (!restored) { error = 90; needsRestart = true; }
        }
        long elapsed = System.currentTimeMillis() - startedAt;
        lines = error == 0 && failure == null
                ? new String[] {"NATIVE OS CALL: OK",
                    "OS ms: " + (milliseconds & 0xFFFFFFFFL),
                    "32-bit counter; not date/time.",
                    "OS entry: " + hex(rom.time), "Cache: " + hex(rom.cache),
                    "Query took " + elapsed + " ms", "Read again to see it change."}
                : new String[] {"Native call failed: " + error,
                    failure == null ? "See README.txt for check codes." : failure.toString()};
        running = false;
        if (!needsRestart) screen.addCommand(query);
        screen.addCommand(exit);
        screen.repaint();
    }

    private int queryNative() {
        // Do not assume RAPU Java data RAM is executable. Prime separate method
        // storage before forging the heap, then never run its restored bytecode
        // as Java again: its instruction cache may still contain native bytes.
        if (rapu) {
            if (carrierSwitch(0) != 1) return 9;
            if (!storageLoaded) {
                if (payloadSpace(0, 0) != 1) return 9;
                storageLoaded = true;
            }
        }
        int[] pixel = {LENGTH};
        Image image = Image.createRGBImage(pixel, 1, 1, true);
        image.getRGB(pixel, 0, 1, 0, 0, 1, 1);
        if (pixel[0] != LENGTH) return 1;

        payload = canary = view = destination = null;
        pointers = null;
        method = carrier = storage = 0;
        System.gc();
        payload = new int[512];
        canary = new int[2];
        pointers = new Object[4];
        view = new int[1];
        destination = new int[8];
        canary[0] = 0x13579BDF;
        canary[1] = 0x2468ACE0;
        pointers[0] = payload;
        pointers[1] = this;
        pointers[2] = view;
        pointers[3] = canary;

        // getRGB's signed-overflow check followed by 16-bit native truncation
        // copies one pixel to destination[11], the adjacent view.length word.
        image.getRGB(destination, 0x4000000B, 0x40000001,
                0x40000000, 0, 0x40000001, 1);
        forged = view.length == LENGTH;
        if (!forged) return 2;
        // No deliberate allocations, UI, resource I/O or Java callbacks below.
        if (view[13] != 0x13579BDF || view[14] != 0x2468ACE0
                || canary[0] != 0x13579BDF || canary[1] != 0x2468ACE0) return 3;
        view[13] = 0x13579BE0;
        boolean writable = canary[0] == 0x13579BE0;
        view[13] = 0x13579BDF;
        if (!writable || canary[0] != 0x13579BDF) return 3;

        int codeObject = view[5], self = view[6], array = view[7];
        if (!ram(codeObject, 12 + payload.length * 4) || !ram(self, 4)
                || !ram(array, 16) || !ram(view[8], 20)) return 4;
        base = array + 12;
        if (read(array + 8) != LENGTH || read(view[8] + 12) != canary[0]) return 4;

        int error = rom.resolve(view, base);
        if (error != 0) return error;
        error = findMethods(self);
        if (error != 0) return error;
        if (rapu) return callRapu();

        int index = placement(codeObject + 12, payload.length, method);
        if (index < 0) return 7;
        int address = codeObject + 12 + index * 4;
        for (int i = 0; i < CODE.length; i++) payload[index + i] = CODE[i];
        payload[index + 7] = rom.time;

        // RM-443 dispatcher supplies r0=flags, r1=method; its cache helper
        // flushes two lines before testing the intentionally wrapped end.
        patched = true;
        write(method + 20, address);
        write(method + 4, rom.cache);
        call();
        write(method + 20, savedFlags);
        write(method + 4, address);
        call();
        if (payload[index + 8] != 1) return 8;
        milliseconds = payload[index + 9];
        return 0;
    }

    private int findMethods(int self) {
        method = carrier = storage = 0;
        int owner = read(self);
        if (!ram(owner, 40)) return 5;
        int table = read(owner + 36);
        if (!ram(table, 4)) return 5;
        int count = read(table);
        if (count < 1 || count > 512 || !ram(table, 4 + 32 * count)) return 5;
        for (int i = 0; i < count; i++) {
            int m = table + 4 + 32 * i;
            if (read(m + 24) == owner && read(m + 4) == 0
                    && (read(m + 20) & 0x050F) == 0x010A
                    && (read(m + 28) >>> 16) == 0) {
                if (method != 0) return 6;
                method = m;
            }
            if (rapu && read(m + 24) == owner) {
                int code = read(m + 4), flags = read(m + 20), args = read(m + 28) >>> 16;
                int bytes = read(m + 16) & 65535;
                if ((flags & 0x050F) == 0x000A && args == 1 && bytes == 114
                        && methodCode(code, 116) && verifyCarrier(code)) {
                    if (carrier != 0) return 10;
                    carrier = code;
                }
                if ((flags & 0x050F) == 0x000A && args == 2 && bytes == 80
                        && methodCode(code, 80) && read(code) == 0x0000AB1A
                        && read(code + 8) == 0x08000000 && read(code + 12) == 0x00000100
                        && (read(code + 76) & 0x00FF00FF) == 0x00040003) {
                    if (storage != 0) return 11;
                    storage = code;
                }
            }
        }
        if (method == 0) return 6;
        savedFlags = read(method + 20);
        return rapu && carrier == 0 ? 10 : rapu && storage == 0 ? 11 : 0;
    }

    private static int placement(int data, int words, int method) {
        for (int i = 0; i + CODE.length <= words; i++) {
            int a = data + 4 * i;
            if ((a & 0x108) == 0x108 && (a & 0x400) == 0 // native/static, not abstract
                    && ((a + method) ^ 0x80000000) < (a ^ 0x80000000)
                    && CODE.length * 4 <= 64 - (a & 31)) return i;
        }
        return -1;
    }

    private int callRapu() {
        int status = installRapu();
        if (status != 0) return status;
        call();
        if (read(storage + 44) != 1) return 8;
        milliseconds = read(storage + 48);
        return 0;
    }
    private int installRapu() {
        if (carrier == 0) return 10;
        if (storage == 0) return 11;
        int entry = storage + 12;
        for (int i = 0; i < CODE.length; i++) payload[i] = read(entry + 4 * i);
        storageChanged = true;
        for (int i = 0; i < CODE.length; i++) write(entry + 4 * i, i == 7 ? rom.time : CODE[i]);
        for (int i = 0; i < CODE.length; i++)
            if (read(entry + 4 * i) != (i == 7 ? rom.time : CODE[i])) return 12;
        saved0 = read(carrier + 40);
        saved1 = read(carrier + 48);
        saved2 = read(carrier + 56);
        saved3 = read(carrier + 64);
        literalsChanged = true;
        write(carrier + 40, entry);
        write(carrier + 48, CODE.length * 4);
        write(carrier + 56, rom.cache);
        write(carrier + 64, entry);
        if (read(carrier + 40) != entry || read(carrier + 48) != CODE.length * 4
                || read(carrier + 56) != rom.cache || read(carrier + 64) != entry) return 12;
        patched = true;
        write(method + 4, carrier + 13);
        if (read(method + 4) != carrier + 13) return 12;
        return 0;
    }
    private boolean restoreBindings() {
        boolean restored = true;
        if (patched) {
            write(method + 4, 0);
            write(method + 20, savedFlags);
            if (read(method + 4) != 0 || read(method + 20) != savedFlags) restored = false;
            patched = false;
        }
        if (literalsChanged) {
            write(carrier + 40, saved0);
            write(carrier + 48, saved1);
            write(carrier + 56, saved2);
            write(carrier + 64, saved3);
            if (read(carrier + 40) != saved0 || read(carrier + 48) != saved1
                    || read(carrier + 56) != saved2 || read(carrier + 64) != saved3) restored = false;
            literalsChanged = false;
        }
        if (storageChanged) {
            for (int i = 0; i < CODE.length; i++) {
                write(storage + 12 + i * 4, payload[i]);
                if (read(storage + 12 + i * 4) != payload[i]) restored = false;
            }
            storageChanged = false;
        }
        return restored;
    }
    private boolean verifyCarrier(int code) {
        if (read(code + 8) != 0x0A000000) return false;
        for (int i = 0; i < CARRIER.length; i++)
            if (read(code + 12 + 8 * i) != CARRIER[i]) return false;
        return true;
    }
    private boolean ram(int a, int bytes) {
        return (a & 3) == 0 && rom.ram(a, bytes);
    }
    private boolean methodCode(int a, int bytes) {
        // Follow an owned VM method record: executable bytecode pages need not
        // share the ordinary Java-object RAM window. Check view representation,
        // alignment, declared length and invariant bytes before any mutation.
        int index = (a - base) >>> 2;
        return a != 0 && (a & 3) == 0 && index <= view.length
                && (bytes + 3) / 4 <= view.length - index;
    }
    private int read(int a) { return view[(a - base) >>> 2]; }
    private void write(int a, int v) { view[(a - base) >>> 2] = v; }
    private static String hex(int v) { return "0x" + Integer.toHexString(v); }

    // Sorted lookupswitch keys carry the reviewed Thumb cache/call bridge.
    // Keep exact bytecode geometry under the packaged-class tests.
    private static int carrierSwitch(int value) {
        switch (value) {
            case -2143150624: return 0;
            case -1874390560: return 0;
            case -1740170784: return 0;
            case 4722144: return 0;
            case 4784608: return 0;
            case 4910567: return 0;
            case 4976103: return 0;
            case 69005799: return 0;
            case 280357351: return 0;
            case 280821760: return 0;
            default: return 1;
        }
    }
    private static int payloadSpace(int value, int unused) {
        switch (value) {
            case 65536: case 131072: case 196608: case 262144:
            case 327680: case 393216: case 458752: case 524288: return 0;
            default: return 1;
        }
    }

    private final class Screen extends Canvas {
        private final Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        Screen() {}
        protected void paint(Graphics g) {
            g.setColor(0x102030);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(0xFFFFFF);
            g.setFont(font);
            String[] text = lines;
            for (int i = 0; i < text.length; i++)
                g.drawString(text[i], 5, 5 + i * (font.getHeight() + 3), Graphics.TOP | Graphics.LEFT);
        }
    }
}
