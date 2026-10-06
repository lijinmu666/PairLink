package com.lijinmu.pairlink;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import org.json.JSONObject;
import rikka.shizuku.Shizuku;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

final class ShizukuBridge {
    private static final ReentrantLock runLock = new ReentrantLock();
    private static volatile IPairLinkUserService active;

    static boolean available() {
        try { return Shizuku.pingBinder() && !Shizuku.isPreV11(); }
        catch (RuntimeException e) { return false; }
    }
    static boolean granted() {
        try { return available() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; }
        catch (RuntimeException e) { return false; }
    }
    static int uid() {
        try { return granted() ? Shizuku.getUid() : -1; } catch (RuntimeException e) { return -1; }
    }
    static String status() {
        if (!available()) return "Shizuku / Sui 未运行";
        if (!granted()) return "Shizuku / Sui 未授权";
        try { return "Shizuku / Sui 已授权（UID " + Shizuku.getUid() + "）"; }
        catch (RuntimeException e) { return "Shizuku / Sui 连接已断开"; }
    }
    static void cancel() {
        IPairLinkUserService service = active;
        if (service != null) try { service.cancel(); } catch (Exception ignored) {}
    }
    static ShellRunner.Result run(Context c, String command, int seconds, BooleanSupplier allowed) throws Exception {
        if (!granted()) throw new Exception("Shizuku / Sui 尚未运行或未授权");
        if (!allowed.getAsBoolean()) throw new Exception("特权执行授权已关闭");
        if (!runLock.tryLock()) throw new Exception("已有 Shizuku / Sui 任务正在执行");
        Handler main = new Handler(Looper.getMainLooper());
        CountDownLatch ready = new CountDownLatch(1);
        AtomicBoolean closed = new AtomicBoolean(), cancelled = new AtomicBoolean();
        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(new ComponentName(c, PrivilegedUserService.class))
            .daemon(false).tag("pairlink-" + UUID.randomUUID()).processNameSuffix("privileged").version(1);
        ServiceConnection connection = new ServiceConnection() {
            private IPairLinkUserService bound;
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                if (!closed.get() && binder != null && binder.pingBinder()) {
                    bound = IPairLinkUserService.Stub.asInterface(binder);
                    active = bound;
                }
                ready.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                if (active == bound) active = null;
                ready.countDown();
            }
        };
        ScheduledExecutorService watch = Executors.newSingleThreadScheduledExecutor();
        ExecutorService calls = Executors.newSingleThreadExecutor();
        Runnable bind = () -> {
            if (closed.get()) { ready.countDown(); return; }
            try { Shizuku.bindUserService(args, connection); }
            catch (RuntimeException e) { ready.countDown(); }
        };
        try {
            main.post(bind);
            if (!ready.await(6, TimeUnit.SECONDS) || active == null) throw new Exception("Shizuku / Sui 服务连接失败，请稍后重试");
            if (!allowed.getAsBoolean() || !granted()) throw new Exception("特权执行授权已关闭");
            IPairLinkUserService service = active;
            watch.scheduleWithFixedDelay(() -> {
                if (!allowed.getAsBoolean() || !granted()) {
                    cancelled.set(true);
                    try { service.cancel(); } catch (Exception ignored) {}
                }
            }, 0, 250, TimeUnit.MILLISECONDS);
            Future<String> reply = calls.submit(() -> service.run(command, seconds));
            JSONObject data;
            try { data = new JSONObject(reply.get(seconds + 3L, TimeUnit.SECONDS)); }
            catch (TimeoutException e) { cancelled.set(true); cancel(); throw new Exception("Shizuku / Sui 执行超时，指令可能已执行，请勿直接重复提交"); }
            if (data.has("error")) throw new Exception(data.optString("error"));
            ShellRunner.Result result = new ShellRunner.Result();
            result.stdout = data.optString("stdout"); result.stderr = data.optString("stderr");
            result.exitCode = data.optInt("exit_code", -1); result.timedOut = data.optBoolean("timed_out");
            result.truncated = data.optBoolean("truncated");
            result.cancelled = data.optBoolean("cancelled") || cancelled.get() || !allowed.getAsBoolean() || !granted();
            return result;
        } finally {
            closed.set(true);
            watch.shutdownNow(); calls.shutdownNow();
            main.removeCallbacks(bind);
            cancel(); active = null;
            main.post(() -> {
                try { Shizuku.unbindUserService(args, connection, true); } catch (RuntimeException ignored) {}
            });
            runLock.unlock();
        }
    }
}
