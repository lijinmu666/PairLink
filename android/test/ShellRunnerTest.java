package com.lijinmu.pairlink;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class ShellRunnerTest {
    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
    }
    public static void main(String[] args) throws Exception {
        ShellRunner runner = new ShellRunner();
        ShellRunner.Result output = runner.run("printf hello; printf error >&2; exit 7", false, 2, () -> true);
        check(output.stdout.equals("hello") && output.stderr.equals("error") && output.exitCode == 7, "stdout/stderr/exit");
        boolean denied = false;
        try { runner.run("echo bad", false, 1, () -> false); } catch (Exception e) { denied = true; }
        check(denied, "disabled shell rejected");
        ShellRunner.Result large = runner.run("head -c 15000 /dev/zero", false, 2, () -> true);
        check(large.stdout.length() == ShellRunner.LIMIT && large.truncated, "output truncation without pipe deadlock");
        ShellRunner.Result timeout = runner.run("exec sleep 3", false, 1, () -> true);
        check(timeout.timedOut, "timeout");
        AtomicBoolean allowed = new AtomicBoolean(true);
        AtomicReference<ShellRunner.Result> cancelled = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread task = new Thread(() -> {
            try { cancelled.set(runner.run("exec sleep 5", false, 5, allowed::get)); }
            catch (Throwable e) { failure.set(e); }
        });
        task.start(); Thread.sleep(200); allowed.set(false); runner.cancel(); task.join(3000);
        check(!task.isAlive() && failure.get() == null && cancelled.get().cancelled, "switch cancels process");
        check(runner.run("echo next", false, 1, () -> true).exitCode == 0, "runner recovers after cancellation");
        System.out.println("ShellRunner: 6 checks passed");
    }
}
