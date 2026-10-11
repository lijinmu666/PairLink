package com.lijinmu.pairlink;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BinaryCommandTest {
    private interface Attempt { void run() throws Exception; }
    private static void rejects(Attempt attempt) throws Exception {
        try { attempt.run(); } catch (Exception expected) { return; }
        throw new AssertionError("command should fail");
    }
    public static void main(String[] args) throws Exception {
        BinaryCommand runner = new BinaryCommand();
        byte[] bytes = runner.run("printf '\\211PNG\\r\\n\\032\\n\\000'", false, 2, 100, () -> true);
        if (!Arrays.equals(bytes, new byte[]{(byte)137,80,78,71,13,10,26,10,0})) throw new AssertionError("binary stream was modified");
        rejects(() -> runner.run("printf 123456789", false, 2, 4, () -> true));
        rejects(() -> runner.run("printf forbidden", false, 2, 100, () -> false));
        rejects(() -> runner.run("exit 7", false, 2, 100, () -> true));
        AtomicBoolean allowed = new AtomicBoolean(true);
        Thread revoke = new Thread(() -> { try { Thread.sleep(150); } catch (InterruptedException ignored) {} allowed.set(false); });
        revoke.start();
        long started = System.nanoTime();
        rejects(() -> runner.run("sleep 5", false, 10, 100, allowed::get));
        if ((System.nanoTime() - started) > 2000000000L) throw new AssertionError("permission cancellation too slow");
        revoke.join();
        rejects(() -> runner.run("sleep 3", false, 1, 100, () -> true));
        if (!"ok".equals(new String(runner.run("printf ok", false, 2, 10, () -> true), "UTF-8"))) throw new AssertionError("runner did not recover");
        System.out.println("BinaryCommandTest passed");
    }
}
