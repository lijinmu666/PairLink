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
import java.util.concurrent.atomic.AtomicReference;
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
    private interface ServiceCall { String run(IPairLinkUserService service) throws Exception; }
    static JSONObject screenshot(Context c, java.util.Set<String> blacklist, BooleanSupplier allowed) throws Exception {
        return invoke(c, 30, allowed, service -> service.screenshot(blacklist.toArray(new String[0])));
    }
    static ShellRunner.Result run(Context c, String command, int seconds, BooleanSupplier allowed) throws Exception {
        JSONObject data = invoke(c, seconds, allowed, service -> service.run(command, seconds));
        ShellRunner.Result result = new ShellRunner.Result();
        result.stdout = data.optString("stdout"); result.stderr = data.optString("stderr");
        result.exitCode = data.optInt("exit_code", -1); result.timedOut = data.optBoolean("timed_out");
        result.truncated = data.optBoolean("truncated"); result.cancelled = data.optBoolean("cancelled");
        return result;
    }
    private static JSONObject invoke(Context c, int seconds, BooleanSupplier allowed, ServiceCall operation) throws Exception {
        if (!granted()) throw new Exception("Shizuku / Sui 尚未运行或未授权");
        if (!allowed.getAsBoolean()) throw new Exception("特权执行授权已关闭");
        if (!runLock.tryLock()) throw new Exception("已有 Shizuku / Sui 任务正在执行");
        Handler main = new Handler(Looper.getMainLooper());
        CountDownLatch ready = new CountDownLatch(1);
        AtomicBoolean closed = new AtomicBoolean(), cancelled = new AtomicBoolean();
        AtomicReference<String> bindError = new AtomicReference<>();
        active = null;
        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(new ComponentName(c, PrivilegedUserService.class))
            .daemon(false).tag("pairlink-" + UUID.randomUUID()).processNameSuffix("privileged").version(2);
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
            catch (RuntimeException e) {
                bindError.set(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                ready.countDown();
            }
        };
        try {
            main.post(bind);
            if (!ready.await(6, TimeUnit.SECONDS) || active == null)
                throw new Exception("Shizuku / Sui 服务连接失败"
                    + (bindError.get() == null ? "，请确认已授权 PairLink" : "：" + bindError.get()));
            if (!allowed.getAsBoolean() || !granted()) throw new Exception("特权执行授权已关闭");
            IPairLinkUserService service = active;
            watch.scheduleWithFixedDelay(() -> {
                if (!allowed.getAsBoolean() || !granted()) {
                    cancelled.set(true);
                    try { service.cancel(); } catch (Exception ignored) {}
                }
            }, 0, 250, TimeUnit.MILLISECONDS);
            Future<String> reply = calls.submit(() -> operation.run(service));
            JSONObject data;
            try { data = new JSONObject(reply.get(seconds + 3L, TimeUnit.SECONDS)); }
            catch (TimeoutException e) {
                cancelled.set(true); cancel();
                throw new Exception("Shizuku / Sui 执行超时，指令可能已执行，请勿直接重复提交");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                String message = cause == null ? e.getMessage() : cause.getMessage();
                throw new Exception("Shizuku / Sui 执行失败：" + (message == null ? "远程服务异常" : message), cause);
            }
            if (data.has("error")) throw new Exception(data.optString("error"));
            boolean revoked = cancelled.get() || !allowed.getAsBoolean() || !granted();
            // Never pass an image over the app/network boundary after cancellation.
            if (data.has("image_base64") && revoked) throw new Exception("截图已取消或授权已改变，未回传图片");
            data.put("cancelled", data.optBoolean("cancelled") || revoked);
            return data;
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
