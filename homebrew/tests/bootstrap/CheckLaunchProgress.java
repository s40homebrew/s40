/* SPDX-License-Identifier: MIT */
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

/** Host-only checks against the actual compiled session; no bootstrap is run. */
public final class CheckLaunchProgress {
    private static void require(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }

    private static void set(NativeSession session, String name, Object value) throws Exception {
        Field field = NativeSession.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(session, value);
    }

    public static void main(String[] args) throws Exception {
        final ArrayList<Integer> codes = new ArrayList<Integer>();
        final ArrayList<Integer> percentages = new ArrayList<Integer>();
        NativeSession session = new NativeSession(
                Rm443Profile.create(), "/unused.parts", new NativeSession.Progress() {
                    public void checkpoint(int code, int percent) {
                        codes.add(code);
                        percentages.add(percent);
                    }
                });
        Method checkpoint =
                NativeSession.class.getDeclaredMethod("checkpoint", Integer.TYPE, Integer.TYPE);
        checkpoint.setAccessible(true);
        checkpoint.invoke(session, 0x10, -1);
        require(codes.size() == 1 && codes.get(0) == 0x10, "Enabled observer missing");
        set(session, "forged", true);
        checkpoint.invoke(session, 0x20, -1);
        require(codes.size() == 1, "UI callback escaped forged-heap guard");
        set(session, "forged", false);
        checkpoint.invoke(session, 0x20, -1);
        require(codes.size() == 2 && codes.get(1) == 0x20, "Restored callback missing");

        Method report = NativeSession.class.getDeclaredMethod("reportLoading", Integer.TYPE);
        report.setAccessible(true);
        set(session, "transferTotal", 1024 * 1024);
        codes.clear();
        percentages.clear();
        for (int i = 0; i < 1024; i++)
            report.invoke(session, 1024);
        require(!percentages.isEmpty() && percentages.size() < 1024,
                "Transfer callbacks missing or emitted for every chunk");
        int previous = 0;
        for (int percent : percentages) {
            require(percent >= previous && percent <= 100, "Non-monotonic transfer progress");
            previous = percent;
        }
        require(previous == 100, "Completed transfer did not report 100 percent");
        // The default constructor performs no observer work (including division)
        // when used by applications that do not enable launch diagnostics.
        NativeSession quiet = new NativeSession(Rm443Profile.create(), "/unused.bin");
        checkpoint.invoke(quiet, 0x10, -1);
        report.invoke(quiet, 1024);
        System.out.println("PASS: forged-heap callback exclusion, restored callback, "
                + "coarse transfer progress, disabled diagnostics");
    }
}
