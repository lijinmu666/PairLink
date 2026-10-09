package com.lijinmu.pairlink;

import android.app.*;
import android.content.Intent;
import android.content.Context;
import android.os.*;
import org.json.JSONObject;
import okhttp3.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LinkService extends Service {
    static final String STOP = "stop", POLICY = "policy", NOTIFICATION = "notification";
    private static final String FOREGROUND_START = "foreground_start";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService tasks = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean();
    private final ShellRunner runner = new ShellRunner();
    private final Set<String> seen = new HashSet<>();
    private WebSocket socket;
    private int generation;
    private boolean destroyed;
    private boolean foreground;
    private volatile long lastPong;
    private volatile boolean executingRoot;
    private volatile boolean executingShizuku;
    private volatile boolean executingAppControl;
    private String base;

    @Override public void onCreate() {
        super.onCreate();
        seen.addAll(Config.prefs(this).getStringSet("seen", Collections.emptySet()));
        base = Config.server(this);
        connect();
        handler.postDelayed(heartbeat, 25000);
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && intent.getBooleanExtra(FOREGROUND_START, false)) {
            startForeground(100, notification(Config.prefs(this).getString("connection", "正在连接")));
            foreground = true;
        }
        if (intent != null && STOP.equals(intent.getAction())) {
            Config.prefs(this).edit().putBoolean("sharing", false).apply();
        }
        if (!Config.sharing(this)) { runner.cancel(); ShizukuBridge.cancel(); stopSelf(); return START_NOT_STICKY; }
        updateStatusNotification(Config.prefs(this).getString("connection", "正在连接"));
        if (intent != null && POLICY.equals(intent.getAction())) {
            boolean denied = executingAppControl ? !Config.prefs(this).getBoolean("app_control", false) : !Config.shell(this);
            if (denied || (executingRoot && !Config.root(this)) || (executingShizuku && !Config.shizuku(this))) {
                runner.cancel(); ShizukuBridge.cancel();
            }
        }
        return START_STICKY;
    }
    static void start(Context c, String action) {
        boolean show = Config.statusNotification(c);
        Intent intent = new Intent(c, LinkService.class).setAction(action).putExtra(FOREGROUND_START, show);
        if (show) c.startForegroundService(intent); else c.startService(intent);
    }
    private void updateStatusNotification(String text) {
        if (Config.statusNotification(this)) {
            if (!foreground) {
                startForeground(100, notification(text));
                foreground = true;
            } else getSystemService(NotificationManager.class).notify(100, notification(text));
        } else {
            if (foreground) stopForeground(STOP_FOREGROUND_REMOVE);
            foreground = false;
            getSystemService(NotificationManager.class).cancel(100);
        }
    }
    static void createConnectionChannel(Context c) {
        NotificationChannel channel = new NotificationChannel("connection", "共享连接", NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setShowBadge(false);
        c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    private Notification notification(String state) {
        createConnectionChannel(this);
        PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, LinkService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, "connection").setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("PairLink · " + state).setContentText("允许已设置的对方设备查询 · 点击暂停可停止共享")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null, "暂停共享", stop).build()).build();
    }
    private void state(String text) {
        Config.prefs(this).edit().putString("connection", text).apply();
        updateStatusNotification(text);
    }
    private void connect() {
        if (destroyed || !Config.sharing(this) || socket != null || !base.startsWith("https://")) return;
        int g = ++generation;
        lastPong = SystemClock.elapsedRealtime();
        Request request = new Request.Builder().url("wss://" + base.substring(8) + "/api/device/ws?device_id=" + Config.own(this)).build();
        socket = Net.client().newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response r) { handler.post(() -> { if (g == generation) { state("在线"); Config.log(LinkService.this, "连接成功"); } }); }
            @Override public void onMessage(WebSocket ws, String text) { handler.post(() -> { if (g == generation) receive(text); }); }
            @Override public void onFailure(WebSocket ws, Throwable t, Response r) { handler.post(() -> disconnected(g, t.getClass().getSimpleName())); }
            @Override public void onClosed(WebSocket ws, int code, String reason) { handler.post(() -> disconnected(g, reason)); }
            @Override public void onClosing(WebSocket ws, int code, String reason) { ws.close(code, reason); }
        });
    }
    private void disconnected(int g, String reason) {
        if (destroyed || g != generation) return;
        socket = null;
        state("离线，等待重连");
        Config.log(this, "连接断开：" + reason);
        handler.postDelayed(this::connect, 10000);
    }
    private final Runnable heartbeat = new Runnable() {
        public void run() {
            if (destroyed) return;
            if (socket != null && SystemClock.elapsedRealtime() - lastPong > 75000) {
                generation++; socket.cancel(); socket = null; state("连接超时");
            }
            if (socket == null) connect(); else socket.send("ping");
            handler.postDelayed(this, 25000);
        }
    };
    private void receive(String text) {
        lastPong = SystemClock.elapsedRealtime();
        if ("pong".equals(text)) return;
        try {
            JSONObject command = new JSONObject(text);
            if (!"command".equals(command.optString("type"))) return;
            String id = command.getString("command_id");
            if (!id.matches("[0-9a-f-]{36}")) return;
            if (seen.contains(id)) return;
            if (seen.size() >= 200) seen.clear();
            seen.add(id); Config.prefs(this).edit().putStringSet("seen", new HashSet<>(seen)).apply();
            if (!Config.sharing(this) || !Config.peer(this).equals(command.optString("sender_id")) || !Config.own(this).equals(command.optString("target_id"))) {
                tasks.execute(() -> returnResult(id, null, "共享关闭或发送方不匹配")); return;
            }
            if (!busy.compareAndSet(false, true)) { tasks.execute(() -> returnResult(id, null, "已有任务正在执行")); return; }
            tasks.execute(() -> execute(command));
        } catch (Exception e) { Config.log(this, "收到无效消息"); }
    }
    private void execute(JSONObject c) {
        String id = c.optString("command_id"), action = c.optString("action");
        Config.log(this, "收到指令：" + action);
        try {
            if (!Config.sharing(this)) throw new Exception("共享已关闭");
            if (System.currentTimeMillis() >= c.getLong("deadline_at")) throw new Exception("指令已经过期");
            JSONObject params = c.optJSONObject("params");
            if (params == null) params = new JSONObject();
            JSONObject result;
            switch (action) {
                case "get_status": result = DeviceQueries.status(this); break;
                case "get_installed_apps": result = DeviceQueries.installedApps(this); break;
                case "send_notification":
                    if (!Config.prefs(this).getBoolean("send_notifications", false)) throw new Exception("对方未开启发送普通通知");
                    result = NotificationSender.send(this, params.optString("title"), params.optString("content")); break;
                case "get_recent_apps":
                    int hours = params.optInt("hours", 1);
                    if (hours < 1 || hours > 24) throw new Exception("查询范围无效");
                    result = DeviceQueries.recent(this, hours); break;
                case "get_media": result = MediaQueries.current(this); break;
                case "get_notifications": result = NotificationReaderService.current(this); break;
                case "set_app_suspended":
                    if (!(params.opt("suspended") instanceof Boolean)) throw new Exception("暂停参数无效");
                    String pkg = params.getString("package_name");
                    boolean suspended = params.getBoolean("suspended");
                    String backend = AppControl.backend(this, pkg, suspended);
                    executingAppControl = true;
                    executingRoot = "root".equals(backend);
                    executingShizuku = "shizuku".equals(backend);
                    result = AppControl.set(this, pkg, suspended, false, runner); break;
                case "shell":
                    boolean root = params.optBoolean("root", false);
                    boolean shizuku = params.optBoolean("shizuku", false);
                    if (root && shizuku) throw new Exception("root 和 Shizuku 模式不能同时使用");
                    if (!Config.shell(this)) throw new Exception("对方未开启远程 Shell");
                    if (root && !Config.root(this)) throw new Exception("对方未授权 root 模式");
                    if (shizuku && !Config.shizuku(this)) throw new Exception("对方未开启或未授权 Shizuku / Sui");
                    String script = params.getString("command");
                    int seconds = params.optInt("timeout_seconds", 20);
                    if (script.trim().isEmpty() || script.length() > 8192 || seconds < 1 || seconds > 30) throw new Exception("Shell 参数无效");
                    executingRoot = root;
                    executingShizuku = shizuku;
                    int shizukuUid = shizuku ? ShizukuBridge.uid() : -1;
                    ShellRunner.Result r = shizuku
                        ? ShizukuBridge.run(this, script, seconds, () -> Config.shell(this) && Config.shizuku(this))
                        : runner.run(script, root, seconds, () -> root ? Config.root(this) : Config.shell(this));
                    result = new JSONObject().put("stdout", r.stdout).put("stderr", r.stderr).put("exit_code", r.exitCode)
                        .put("timed_out", r.timedOut).put("cancelled", r.cancelled).put("truncated", r.truncated).put("root", root)
                        .put("backend", shizuku ? "shizuku" : root ? "root" : "app").put("shizuku_uid", shizukuUid);
                    while (result.toString().length() > 47000) {
                        r.stdout = r.stdout.substring(0, r.stdout.length() / 2);
                        r.stderr = r.stderr.substring(0, r.stderr.length() / 2);
                        result.put("stdout", r.stdout).put("stderr", r.stderr).put("truncated", true);
                    }
                    break;
                default: throw new Exception("不支持的指令");
            }
            if ("get_media".equals(action)) MediaQueries.checkAllowed(this);
            if ("get_notifications".equals(action)) NotificationReaderService.checkAllowed(this);
            returnResult(id, result, null);
            Config.log(this, "指令处理完成：" + action);
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null || message.trim().isEmpty()) message = e.getClass().getSimpleName();
            returnResult(id, null, message);
            Config.log(this, "指令失败：" + message);
        }
        finally { executingRoot = false; executingShizuku = false; executingAppControl = false; busy.set(false); }
    }
    private void returnResult(String id, JSONObject result, String error) {
        try {
            JSONObject body = new JSONObject().put("device_id", Config.own(this)).put("command_id", id).put("ok", error == null);
            if (error == null) body.put("result", result); else body.put("error", error);
            Net.request(this, base, "/api/device/result", body);
        } catch (Exception e) { Config.log(this, "结果回传失败：" + e.getMessage()); }
    }
    @Override public void onDestroy() {
        destroyed = true; generation++; handler.removeCallbacksAndMessages(null);
        runner.cancel(); ShizukuBridge.cancel(); tasks.shutdownNow();
        if (socket != null) socket.cancel();
        Config.prefs(this).edit().putString("connection", "共享已停止").apply();
        stopForeground(true); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}

