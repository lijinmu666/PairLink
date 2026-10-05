package com.lijinmu.pairlink;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

final class ShellRunner {
    static final int LIMIT = 12000;
    private Process active;
    static final class Result {
        String stdout, stderr;
        int exitCode;
        boolean timedOut, truncated, cancelled;
    }
    synchronized void cancel() {
        if (active != null) {
            active.destroy();
            active.destroyForcibly();
        }
    }
    Result run(String command, boolean root, int seconds, BooleanSupplier allowed) throws Exception {
        Process process;
        synchronized (this) {
            if (active != null) throw new Exception("已有 Shell 正在运行");
            if (!allowed.getAsBoolean()) throw new Exception("Shell 权限已关闭");
            process = new ProcessBuilder(root ? new String[]{"su", "-c", command} : new String[]{"sh", "-c", command}).start();
            active = process;
        }
        Capture out = new Capture(process.getInputStream()), err = new Capture(process.getErrorStream());
        Thread a = new Thread(out, "ShellOut"), b = new Thread(err, "ShellErr");
        a.setDaemon(true); b.setDaemon(true); a.start(); b.start();
        Result result = new Result();
        try {
            process.getOutputStream().close();
            boolean finished = process.waitFor(seconds, TimeUnit.SECONDS);
            result.timedOut = !finished;
            result.cancelled = !allowed.getAsBoolean();
            if (!finished || result.cancelled) { process.destroy(); process.destroyForcibly(); process.waitFor(1, TimeUnit.SECONDS); }
            result.exitCode = process.isAlive() ? -1 : process.exitValue();
            a.join(400); b.join(400);
            result.stdout = out.text(); result.stderr = err.text();
            result.truncated = out.truncated || err.truncated;
            return result;
        } finally {
            process.destroy();
            synchronized (this) { if (active == process) active = null; }
            try { process.getInputStream().close(); } catch (Exception ignored) {}
            try { process.getErrorStream().close(); } catch (Exception ignored) {}
        }
    }
    private static final class Capture implements Runnable {
        private final InputStream input;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        volatile boolean truncated;
        Capture(InputStream input) { this.input = input; }
        public void run() {
            byte[] buf = new byte[2048];
            try {
                int n;
                while ((n = input.read(buf)) != -1) {
                    synchronized (this) {
                        int count = Math.min(n, LIMIT - bytes.size());
                        if (count > 0) bytes.write(buf, 0, count);
                        if (count < n) truncated = true;
                    }
                }
            } catch (Exception ignored) {}
        }
        synchronized String text() throws Exception { return bytes.toString("UTF-8"); }
    }
}
