package com.lijinmu.pairlink;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Bounded binary output; no text decoding or user-supplied command interpolation. */
final class BinaryCommand {
    private Process active;
    synchronized void cancel() {
        if (active != null) { active.destroy(); active.destroyForcibly(); }
    }
    byte[] run(String command, boolean root, int seconds, int limit, BooleanSupplier allowed) throws Exception {
        Process process;
        synchronized (this) {
            if (active != null) throw new Exception("已有截图任务正在执行");
            if (!allowed.getAsBoolean()) throw new Exception("截图授权已关闭或权限已改变");
            process = new ProcessBuilder(root ? new String[]{"su", "-c", command} : new String[]{"sh", "-c", command}).start();
            active = process;
        }
        Capture out = new Capture(process.getInputStream(), limit), err = new Capture(process.getErrorStream(), 4096);
        Thread a = new Thread(out, "ScreenshotOut"), b = new Thread(err, "ScreenshotErr");
        a.setDaemon(true); b.setDaemon(true); a.start(); b.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        try {
            process.getOutputStream().close();
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                if (!allowed.getAsBoolean()) throw new Exception("截图授权已关闭或权限已改变");
                if (System.nanoTime() >= deadline) throw new Exception("截图超时，请检查本机权限后重试");
                if (out.overflow) throw new Exception("截图输出过大，未回传图片");
            }
            a.join(500); b.join(500);
            if (!allowed.getAsBoolean()) throw new Exception("截图授权已关闭或权限已改变");
            if (a.isAlive() || out.overflow || out.failed) throw new Exception("截图输出不完整或过大，未回传图片");
            if (process.exitValue() != 0) throw new Exception("系统拒绝截图或前台应用查询：" + new String(err.bytes(), "UTF-8"));
            return out.bytes();
        } finally {
            process.destroy(); process.destroyForcibly();
            synchronized (this) { if (active == process) active = null; }
            try { process.getInputStream().close(); } catch (Exception ignored) {}
            try { process.getErrorStream().close(); } catch (Exception ignored) {}
        }
    }
    private static final class Capture implements Runnable {
        private final InputStream input;
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        volatile boolean overflow, failed;
        Capture(InputStream input, int limit) { this.input = input; this.limit = limit; }
        @Override public void run() {
            byte[] buffer = new byte[8192];
            try {
                int n;
                while ((n = input.read(buffer)) != -1) synchronized (this) {
                    int count = Math.min(n, limit - bytes.size());
                    if (count > 0) bytes.write(buffer, 0, count);
                    if (count < n) overflow = true;
                }
            } catch (Exception e) { failed = true; }
        }
        synchronized byte[] bytes() { return bytes.toByteArray(); }
    }
}
