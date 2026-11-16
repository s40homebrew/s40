/* SPDX-License-Identifier: MIT */
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Runs the packaged session's actual method lookup against simulated VM records.
 * In particular the code allocation is separate from object RAM, as on RAPU.
 * This host test reads simulated memory without native calls or firmware writes.
 */
public final class CheckHeapBootstrap {
    private static final int BASE = 0x10000, OBJECT = 0x11000, OWNER = 0x12000;
    private static final int TABLE = 0x13000, NATIVE = TABLE + 4, CARRIER = NATIVE + 32;
    private static final int SPACE = CARRIER + 32, SPACE_CODE = 0x24000;
    private final HeapBoosterSession session;
    private final int[] memory = new int[0x20000 / 4];
    private final Method lookup;

    CheckHeapBootstrap(byte[] body, byte[] storage, int code) throws Exception {
        HeapBoosterProfile profile = new HeapBoosterProfile(
                "test", 0x40000, 0x50000, BASE, 0x18000, HeapBoosterProfile.ARMV6_RAPU, 0, 0);
        session = new HeapBoosterSession(profile);
        field("target").set(session, memory);
        field("base").setInt(session, BASE);
        lookup = HeapBoosterSession.class.getDeclaredMethod("findMethods", int.class);
        lookup.setAccessible(true);
        put(OBJECT, OWNER);
        put(OWNER + 36, TABLE);
        put(TABLE, 3);
        put(NATIVE + 20, 0xC10A);
        put(NATIVE + 24, OWNER);
        put(CARRIER + 4, code);
        put(CARRIER + 16, body.length);
        put(CARRIER + 20, 0xA);
        put(CARRIER + 24, OWNER);
        put(CARRIER + 28, 1 << 16);
        for (int i = 0; i < body.length; i++)
            memory[(code - BASE + i) / 4] |= (body[i] & 255) << ((i & 3) * 8);
        put(SPACE + 4, SPACE_CODE);
        put(SPACE + 16, storage.length);
        put(SPACE + 20, 0xA);
        put(SPACE + 24, OWNER);
        put(SPACE + 28, 2 << 16);
        for (int i = 0; i < storage.length; i++)
            memory[(SPACE_CODE - BASE + i) / 4] |= (storage[i] & 255) << ((i & 3) * 8);
    }
    private static Field field(String name) throws Exception {
        Field f = HeapBoosterSession.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
    private void put(int address, int value) {
        memory[(address - BASE) / 4] = value;
    }
    private int run() throws Exception {
        return ((Integer) lookup.invoke(session, OWNER)).intValue();
    }
    private void roundTrip(int bytes) throws Exception {
        int[] before = memory.clone();
        byte[] binary = new byte[bytes];
        for (int i = 0; i < bytes; i++)
            binary[i] = (byte) (i * 17);
        field("codeBackup").set(session, new int[3072]);
        field("binary").set(session, binary);
        field("bindingOffset").setInt(session, bytes - 4);
        Method install = HeapBoosterSession.class.getDeclaredMethod("installPayload", int.class);
        Method restore = HeapBoosterSession.class.getDeclaredMethod("restorePayload");
        install.setAccessible(true);
        restore.setAccessible(true);
        for (int repeat = 0; repeat < 3; repeat++) {
            require(((Boolean) install.invoke(session, 0x12345678)).booleanValue(),
                    "installed payload readback");
            for (int i = 0; i < memory.length; i++) {
                int offset = BASE + i * 4 - (SPACE_CODE + 12);
                int expected = before[i];
                if (offset >= 0 && offset < bytes) {
                    expected = (binary[offset] & 255) | ((binary[offset + 1] & 255) << 8)
                            | ((binary[offset + 2] & 255) << 16) | (binary[offset + 3] << 24);
                    if (offset == bytes - 4)
                        expected = 0x12345678;
                }
                require(memory[i] == expected, "payload write footprint/binding");
            }
            require(((Boolean) restore.invoke(session)).booleanValue(), "payload restore result");
            require(java.util.Arrays.equals(before, memory), "exact payload byte restoration");
            require(!field("payloadChanged").getBoolean(session), "restored state");
            require(((Boolean) restore.invoke(session)).booleanValue(), "repeat restore");
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        byte[] body = Files.readAllBytes(Paths.get(args[0]));
        byte[] storage = Files.readAllBytes(Paths.get(args[1]));
        require(body.length == 114, "packaged carrier bytes");
        require(storage.length == 12304, "packaged payload storage bytes");
        // Model the two observed return-opcode forms; no firmware interpreter is run.
        for (int opcode : new int[] {0xD5, 0xD6}) {
            byte[] quickened = storage.clone();
            quickened[quickened.length - 1] = (byte) opcode;
            CheckHeapBootstrap runtime = new CheckHeapBootstrap(body, quickened, 0x20000);
            require(runtime.run() == 0, "quickened storage recognized");
            runtime.roundTrip(12288);
        }
        for (int code : new int[] {0x16000, 0x20000}) {
            CheckHeapBootstrap test = new CheckHeapBootstrap(body, storage, code);
            require(test.run() == 0, "carrier inside/outside object heap");
            require(test.session.result[52] == NATIVE, "native entry diagnostic");
            require(test.session.result[53] == code, "carrier diagnostic");
            require(field("carrier").getInt(test.session) == code, "selected code pointer");
            require(test.session.result[58] == SPACE_CODE, "payload storage diagnostic");
            test.roundTrip(4);
            test.roundTrip(12288);
        }
        for (int flags : new int[] {0x010A, 0x410A, 0x810A, 0xC10A}) {
            CheckHeapBootstrap test = new CheckHeapBootstrap(body, storage, 0x20000);
            test.put(NATIVE + 20, flags);
            require(test.run() == 0, "native method independent of VM-private state flags");
        }
        for (int flags : new int[] {0xC002, 0xC102, 0xC50A}) {
            CheckHeapBootstrap test = new CheckHeapBootstrap(body, storage, 0x20000);
            test.put(NATIVE + 20, flags);
            require(test.run() == 19, "wrong native/static/abstract access flags");
        }
        // Inject an actual cleanup exception: the host array cannot represent
        // the wrapped index used to repair a forged on-device length. The next
        // execute must preserve diagnostics and never start another chain.
        CheckHeapBootstrap repair = new CheckHeapBootstrap(body, storage, 0x20000);
        field("forged").setBoolean(repair.session, true);
        repair.session.execute(-1, 0);
        require(repair.session.error == 91 && repair.session.needsRestart,
                "cleanup exception requires restart");
        require(repair.session.stage == HeapBoosterSession.CLEANUP, "cleanup stage reported");
        Throwable failure = repair.session.failure;
        int[] failedResult = repair.session.result.clone();
        repair.session.execute(0, 0);
        require(repair.session.failure == failure
                        && java.util.Arrays.equals(failedResult, repair.session.result),
                "retry preserves failure evidence and does not enter native code");
        CheckHeapBootstrap handoff = new CheckHeapBootstrap(body, storage, 0x20000);
        field("resetBound").setBoolean(handoff.session, true);
        field("forged").setBoolean(handoff.session, true);
        handoff.session.finishReset();
        require(field("resetBound").getBoolean(handoff.session)
                        && handoff.session.failure == null && handoff.session.error == 0,
                "reset cannot enter native code while the memory view is forged");
        field("forged").setBoolean(handoff.session, false);
        handoff.session.error = 91;
        handoff.session.finishReset();
        require(field("resetBound").getBoolean(handoff.session)
                        && handoff.session.failure == null,
                "cleanup failure suppresses pending reset");
        handoff.session.error = 0;
        handoff.session.finishReset();
        // No native library exists on the host; this exercises the real
        // post-cleanup entry failure handler without replacing its bytecode.
        require(handoff.session.error == 220 && handoff.session.needsRestart
                        && handoff.session.failure instanceof UnsatisfiedLinkError
                        && !field("resetBound").getBoolean(handoff.session),
                "deferred native failure requires reboot and cannot be retried");
        CheckHeapBootstrap test = new CheckHeapBootstrap(body, storage, 0x20000);
        test.put(0x20000 + 12, 0);
        require(test.run() == 20, "bad native instruction rejected");
        test = new CheckHeapBootstrap(body, storage, 0x20000);
        test.put(CARRIER + 16, 112);
        require(test.run() == 20, "wrong method length rejected");
        test = new CheckHeapBootstrap(body, storage, 0x20000);
        test.put(CARRIER + 24, OWNER + 4);
        require(test.run() == 20, "foreign owner rejected");
        test = new CheckHeapBootstrap(body, storage, 0x20000);
        test.put(NATIVE + 4, 0x40000);
        require(test.run() == 19 && test.session.result[57] == 0x40000,
                "missing native entry distinguished from missing carrier");
        test = new CheckHeapBootstrap(body, storage, 0x20000);
        test.put(TABLE, 4);
        for (int i = 0; i < 32; i += 4)
            test.put(SPACE + 32 + i, test.memory[(CARRIER - BASE + i) / 4]);
        require(test.run() == 14, "ambiguous carrier rejected");
        test = new CheckHeapBootstrap(body, storage, 0x20000);
        test.put(SPACE + 16, 12288);
        require(test.run() == 21, "missing payload storage");
        test = new CheckHeapBootstrap(body, storage, 0x20000);
        test.put(SPACE_CODE + 12300, 0);
        require(test.run() == 21, "malformed payload storage");
        test = new CheckHeapBootstrap(body, storage, 0x20000);
        test.put(TABLE, 4);
        for (int i = 0; i < 32; i += 4)
            test.put(SPACE + 32 + i, test.memory[(SPACE - BASE + i) / 4]);
        require(test.run() == 22, "ambiguous payload storage");
        System.out.println("PASS packaged bootstrap lookup: separate code/object allocations, "
                + "method ownership, carrier/storage geometry, diagnostics, payload write footprint, restoration and reset handoff guards.");
    }
}
