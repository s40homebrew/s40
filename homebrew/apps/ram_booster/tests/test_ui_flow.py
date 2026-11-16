#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Run the packaged launcher across close/reopen with modeled LCDUI/RMS/native results.

This checks workflow and retry bounds, not handset rendering or arena activation.
"""

import os
import shutil
import subprocess
from pathlib import Path

P = Path(__file__).resolve().parents[3]
B = P / "build/ram_booster/host-ui"
JAR = P / "dist/ram_booster/S40RamBooster.jar"
SOURCES = {
    "javax/microedition/midlet/MIDlet.java": """
package javax.microedition.midlet;
public class MIDlet {
    public boolean destroyed;
    public void notifyDestroyed() { destroyed = true; }
}
""",
    "javax/microedition/lcdui/Command.java": """
package javax.microedition.lcdui;
public class Command { public Command(String s, int t, int p) {} }
""",
    "javax/microedition/lcdui/CommandListener.java": """
package javax.microedition.lcdui;
public interface CommandListener { void commandAction(Command c, Displayable d); }
""",
    "javax/microedition/lcdui/ItemCommandListener.java": """
package javax.microedition.lcdui;
public interface ItemCommandListener { void commandAction(Command c, Item i); }
""",
    "javax/microedition/lcdui/Displayable.java": """
package javax.microedition.lcdui;
public class Displayable {
    public String title; public CommandListener listener;
    public void addCommand(Command c) {} public void removeCommand(Command c) {}
    public void setCommandListener(CommandListener l) { listener = l; }
}
""",
    "javax/microedition/lcdui/Item.java": """
package javax.microedition.lcdui;
public class Item {
    public Command command; public ItemCommandListener listener;
    public void setDefaultCommand(Command c) { command = c; }
    public void setItemCommandListener(ItemCommandListener l) { listener = l; }
    public void tap() { listener.commandAction(command, this); }
}
""",
    "javax/microedition/lcdui/StringItem.java": """
package javax.microedition.lcdui;
public class StringItem extends Item {
    public String text;
    public StringItem(String label, String text, int appearance) { this.text = text; }
}
""",
    "javax/microedition/lcdui/Form.java": """
package javax.microedition.lcdui;
public class Form extends Displayable {
    public String text = ""; public StringItem button;
    public Form(String title) { this.title = title; }
    public int append(String s) { text += s; return 0; }
    public int append(Item i) { button = (StringItem)i; return 1; }
}
""",
    "javax/microedition/lcdui/Image.java": "package javax.microedition.lcdui; public class Image {}",
    "javax/microedition/lcdui/List.java": """
package javax.microedition.lcdui;
public class List extends Displayable {
    public int size, selected;
    public List(String title, int type) { this.title = title; }
    public int append(String s, Image i) { return size++; }
    public int getSelectedIndex() { return selected; }
}
""",
    "javax/microedition/lcdui/Display.java": """
package javax.microedition.lcdui;
import javax.microedition.midlet.MIDlet;
public class Display {
    public static volatile Displayable current;
    static Display singleton = new Display();
    public static Display getDisplay(MIDlet m) { return singleton; }
    public void setCurrent(Displayable d) { current = d; }
}
""",
    "javax/microedition/rms/RecordStore.java": """
package javax.microedition.rms;
import java.util.HashMap;
public class RecordStore {
    public static HashMap<String, byte[]> records = new HashMap<String, byte[]>();
    public static boolean failWrites;
    String name;
    public static RecordStore openRecordStore(String n, boolean create) {
        if (!create && !records.containsKey(n)) throw new RuntimeException();
        RecordStore r = new RecordStore(); r.name = n; return r;
    }
    public int getNumRecords() { return records.containsKey(name) ? 1 : 0; }
    public byte[] getRecord(int id) { return records.get(name).clone(); }
    public int addRecord(byte[] b, int off, int len) { setRecord(1,b,off,len); return 1; }
    public void setRecord(int id, byte[] b, int off, int len) {
        if (failWrites) throw new RuntimeException();
        byte[] copy = new byte[len]; System.arraycopy(b,off,copy,0,len); records.put(name,copy);
    }
    public void closeRecordStore() {}
}
""",
    "HeapBoosterSession.java": """
final class HeapBoosterSession implements HeapBoosterProtocol {
    static int calls, checks, applies, failCount, deviceState, active;
    final HeapBoosterProfile profile;
    final HeapPatchResolver resolver;
    final int[] result = new int[RESULT_WORDS];
    boolean needsRestart, nativeCalled;
    int error, stage, anchoredClass, openedViewBase, lookupViewBase;
    Throwable failure;
    HeapBoosterSession(HeapBoosterProfile p) { profile=p; resolver=new HeapPatchResolver(p); }
    void execute(int operation, int mib) {
        calls++; if (operation == OP_CHECK) checks++; else if(operation == OP_APPLY) applies++;
        error=0; nativeCalled=false;
        if (failCount > 0) { failCount--; error=24; stage=3; openedViewBase=100; lookupViewBase=200; return; }
        nativeCalled=true; stage=5;
        if (operation == OP_APPLY) deviceState=1;
        if (operation == OP_RESTORE) { deviceState=3; active=0; }
        resolver.pools=resolver.sites=resolver.caches=1;
        result[W_MAGIC]=MAGIC; result[W_GROWTH_LIMIT]=6<<20;
        result[W_POOL_BYTES]=(deviceState==2 ? 8<<20 : 4<<20)-16;
        result[W_GROWTH_STATE]=deviceState; result[W_CURRENT_MIB]=active;
        if (profile.id.equals("RM-217")) {
            result[W_GROWTH_LIMIT]=4<<20; result[W_GROWTH_KIND]=2;
            result[W_POOL_BYTES]=deviceState==2 ? 5<<20 : 0x280000;
        }
    }
}
""",
    "CheckBoosterUI.java": """
import javax.microedition.lcdui.*;
import javax.microedition.rms.RecordStore;
public class CheckBoosterUI {
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    static void idle(S40RamBooster app) throws Exception {
        java.lang.reflect.Field busy = S40RamBooster.class.getDeclaredField("busy"); busy.setAccessible(true);
        long end=System.currentTimeMillis()+5000;
        while (busy.getBoolean(app) && System.currentTimeMillis()<end) Thread.sleep(1);
        check(!busy.getBoolean(app));
    }
    static S40RamBooster launch() throws Exception {
        S40RamBooster a=new S40RamBooster(); a.startApp(); idle(a); return a;
    }
    static Form form() { return (Form)Display.current; }
    static void chooseCheck(S40RamBooster a) throws Exception {
        List menu=(List)Display.current; menu.selected=0;
        a.commandAction(new Command("select",0,0),menu); idle(a);
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("microedition.platform","Nokia3060/03.63");
        S40RamBooster a=launch(); check(((List)Display.current).size==3);
        HeapBoosterSession.failCount=1;
        chooseCheck(a); check(HeapBoosterSession.calls==2);
        check(form().button != null);
        String detail=new String(RecordStore.records.get("RamBoosterResult"),"UTF-8");
        check(detail.contains("Automatic retries: 1") && detail.contains("00000064 / 000000C8"));
        form().button.tap(); idle(a);
        check(HeapBoosterSession.applies==1 && form().text.contains("not active yet"));
        form().button.tap(); check(a.destroyed);
        byte[] saved=RecordStore.records.get("RamBoosterFlow"); check(saved[0]==4 && saved[1]==1);
        int calls=HeapBoosterSession.calls;
        S40RamBooster b=launch(); check(HeapBoosterSession.calls==calls+1 && HeapBoosterSession.applies==1);
        detail=new String(RecordStore.records.get("RamBoosterResult"),"UTF-8");
        check(detail.contains("Resumed saved request: true") && detail.contains("Close requested: true"));
        HeapBoosterSession.deviceState=2; HeapBoosterSession.active=4;
        S40RamBooster c=launch(); check(form().text.contains("4 MiB limit is active"));
        check(RecordStore.records.get("RamBoosterFlow")[0]==0);
        calls=HeapBoosterSession.calls; S40RamBooster d=launch(); check(HeapBoosterSession.calls==calls);
        // Repeated address-change stops are bounded; no third call or automatic Apply.
        HeapBoosterSession.failCount=2; chooseCheck(d); check(HeapBoosterSession.calls==calls+2);
        // Apply failure is never retried.
        HeapBoosterSession.deviceState=0; HeapBoosterSession.active=0;
        S40RamBooster e=launch(); chooseCheck(e);
        calls=HeapBoosterSession.calls; HeapBoosterSession.failCount=1;
        form().button.tap(); idle(e); check(HeapBoosterSession.calls==calls+1);
        // Lost/power-cleared native state must not be reported as an active boost.
        RecordStore.records.put("RamBoosterFlow",new byte[]{4,1});
        S40RamBooster f=launch(); check(form().text.contains("No boost is active"));
        check(RecordStore.records.get("RamBoosterFlow")[0]==0);
        // Missing RMS writes have a truthful manual-Check fallback.
        S40RamBooster g=launch(); chooseCheck(g); RecordStore.failWrites=true;
        form().button.tap(); idle(g); check(form().text.contains("Then tap Check phone"));
        System.setProperty("microedition.platform","Nokia6300/unrecorded");
        RecordStore.failWrites=false; HeapBoosterSession.deviceState=0;
        HeapBoosterSession.active=0; HeapBoosterSession.failCount=0;
        S40RamBooster h=launch(); chooseCheck(h);
        check(form().button != null);
        java.lang.reflect.Field menuField=S40RamBooster.class.getDeclaredField("menu");
        menuField.setAccessible(true); check(((List)menuField.get(h)).size==5);
        form().button.tap(); idle(h);
        HeapBoosterSession.deviceState=2; HeapBoosterSession.active=3;
        S40RamBooster i=launch(); chooseCheck(i);
        check(form().text.contains("3 MiB limit is active"));
        System.out.println("PASS packaged UI: close/reopen auto-Check, truthful pending/active/reset results, saved exit intent, bounded Check retry, no Apply retry and RMS failure fallback. LCDUI/RMS/native results modeled.");
    }
}
""",
}


def main():
    javac = (
        str(Path(os.environ["JAVA8_HOME"]) / "bin/javac")
        if "JAVA8_HOME" in os.environ
        else shutil.which("javac")
    )
    if not javac:
        raise SystemExit("Set JAVA8_HOME or put a JDK on PATH")
    java = Path(javac).parent
    files = []
    for name, source in SOURCES.items():
        path = B / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
        files.append(str(path))
    subprocess.run([str(java / "javac"), "-cp", str(JAR), "-d", str(B), *files], check=True)
    subprocess.run(
        [str(java / "java"), "-cp", os.pathsep.join(map(str, [B, JAR])), "CheckBoosterUI"],
        check=True,
    )


if __name__ == "__main__":
    main()
