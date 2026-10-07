package com.lijinmu.pairlink;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import android.content.SharedPreferences;
import org.json.*;
import java.util.concurrent.*;
import rikka.shizuku.Shizuku;

public final class MainActivity extends ComponentActivity {
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final UiState ui = new UiState();
    private volatile boolean busy;
    private boolean awaitingShizuku;
    private int versionTapCount;
    private long lastVersionTap;
    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionResult = (code, result) -> {
        if (code != 200) return;
        runOnUiThread(() -> {
            if (isDestroyed()) return;
            if (awaitingShizuku && result == PackageManager.PERMISSION_GRANTED)
                Config.prefs(this).edit().putBoolean("shizuku", true).apply();
            awaitingShizuku = false;
            refreshState();
            toast(result == PackageManager.PERMISSION_GRANTED ? "Shizuku / Sui 已授权" : "Shizuku / Sui 授权被拒绝");
        });
    };
    private final Handler permissionHandler = new Handler(Looper.getMainLooper());
    private final Runnable permissionRefresh = new Runnable() {
        @Override public void run() {
            refreshAccessPermissions();
            permissionHandler.postDelayed(this, 1000);
        }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener = (prefs, key) -> runOnUiThread(this::refreshState);
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        Shizuku.addRequestPermissionResultListener(shizukuPermissionResult);
        ui.setServer(Config.server(this)); ui.setOwn(Config.own(this)); ui.setPeer(Config.peer(this));
        refreshState();
        Config.prefs(this).registerOnSharedPreferenceChangeListener(preferenceListener);
        PairLinkUi.render(this, ui);
        if (b == null && Config.prefs(this).getBoolean("love_egg_pending", false)) {
            Config.prefs(this).edit().remove("love_egg_pending").apply();
            Toast.makeText(this, "I LOVE U , Starry Sky\u2067", Toast.LENGTH_LONG).show();
        }
    }
    public void tapVersion() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastVersionTap > 1500) versionTapCount = 0;
        lastVersionTap = now;
        if (++versionTapCount >= 5) {
            versionTapCount = 0;
            Config.prefs(this).edit().putBoolean("love_egg_pending", true).apply();
        }
    }
    private void refreshAccessPermissions() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = nm.getNotificationChannel("connection");
        ui.setStatusNotification(nm.areNotificationsEnabled() && (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE));
        ui.setUsageAllowed(DeviceQueries.usageAllowed(this));
        ui.setNotificationAccess(NotificationReaderService.hasAccess(this));
        ui.setPrivilegeStatus(ShizukuBridge.status() + "\n设备所有者：" + (AppControl.isOwner(this) ? "已激活" : "未激活"));
        boolean owner = AppControl.isOwner(this);
        boolean shizuku = Config.prefs(this).getBoolean("shizuku", false) && ShizukuBridge.granted();
        boolean root = Config.prefs(this).getBoolean("root", false);
        String mode = AppControl.mode(this);
        if ("shizuku".equals(mode) && shizuku) ui.setPrivilegeMode("Shizuku");
        else if ("root".equals(mode) && root) ui.setPrivilegeMode("root");
        else if ("device_owner".equals(mode) && owner) ui.setPrivilegeMode("设备所有者");
        else if (root) ui.setPrivilegeMode("root");
        else if (shizuku) ui.setPrivilegeMode("Shizuku");
        else if (owner) ui.setPrivilegeMode("设备所有者");
        else ui.setPrivilegeMode("无特权");
    }
    private void refreshState() {
        refreshAccessPermissions();
        ui.setSecureDns(Config.prefs(this).getBoolean("secure_dns", true));
        ui.setSharing(Config.sharing(this));
        ui.setShell(Config.prefs(this).getBoolean("shell", false));
        ui.setRoot(Config.prefs(this).getBoolean("root", false));
        ui.setNotifications(Config.prefs(this).getBoolean("notifications", false));
        ui.setMedia(Config.prefs(this).getBoolean("media", false));
        ui.setSendNotifications(Config.prefs(this).getBoolean("send_notifications", false));
        ui.setShizuku(Config.prefs(this).getBoolean("shizuku", false));
        ui.setAppControl(Config.prefs(this).getBoolean("app_control", false));
        ui.setAppControlMode(AppControl.mode(this));
        ui.setConnection(Config.sharing(this) ? Config.prefs(this).getString("connection", "正在连接") : "共享已关闭");
    }
    public void changeSecureDns(boolean checked) {
        if (busy) { toast("请等待当前请求完成"); return; }
        Config.prefs(this).edit().putBoolean("secure_dns", checked).apply();
        refreshState();
        if (Config.sharing(this)) startService(new Intent(this, LinkService.class).setAction(LinkService.NETWORK));
    }
    public void changeStatusNotification(boolean checked) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        LinkService.createConnectionChannel(this);
        Intent settings = nm.areNotificationsEnabled()
            ? new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                .putExtra(Settings.EXTRA_CHANNEL_ID, "connection")
            : new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try { startActivity(settings); }
        catch (ActivityNotFoundException e) { toast("请在系统设置中的 PairLink 通知设置里开关共享连接通知"); }
    }
    public void changeSharing(boolean checked) {
        if (checked && !settingsValid()) { toast("请先保存有效的连接设置"); return; }
        if (checked && Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        Config.prefs(this).edit().putBoolean("sharing", checked).apply();
        if (checked) startForegroundService(new Intent(this, LinkService.class));
        else stopService(new Intent(this, LinkService.class));
        refreshState();
    }
    public void changeShell(boolean checked) {
        if (checked) ui.confirm("开启远程 Shell", "开启后，对方可以让此 APK 执行命令。第一版服务器没有身份验证，设备 ID 不代表身份。仅在你愿意允许操作时开启。", "开启", () -> {
            Config.prefs(this).edit().putBoolean("shell", true).apply(); refreshState(); policy();
        });
        else {
            Config.prefs(this).edit().putBoolean("shell", false).putBoolean("root", false).apply(); refreshState(); policy();
        }
    }
    public void changeRoot(boolean checked) {
        if (!checked) { Config.prefs(this).edit().putBoolean("root", false).apply(); refreshState(); policy(); return; }
        if (!Config.prefs(this).getBoolean("shell", false)) { toast("请先开启远程 Shell"); return; }
        ui.confirm("授权 root 模式", "将请求本机 root 管理器授权。成功后，对方可以以 root 权限执行命令。", "请求授权", this::authorizeRoot);
    }
    public void changeShizuku(boolean checked) {
        if (!checked) {
            awaitingShizuku = false;
            Config.prefs(this).edit().putBoolean("shizuku", false).apply();
            refreshState(); policy(); ShizukuBridge.cancel();
            return;
        }
        ui.confirm("授权 Shizuku / Sui", "允许 PairLink 通过 Shizuku / Sui 执行已开启共享的 Shell 和应用暂停请求。实际权限由 Shizuku / Sui 的启动身份决定。", "授权", () -> {
            if (!ShizukuBridge.available()) { toast("请先启动 Shizuku 或安装并启用 Sui"); return; }
            if (ShizukuBridge.granted()) {
                Config.prefs(this).edit().putBoolean("shizuku", true).apply(); refreshState(); return;
            }
            try {
                if (Shizuku.shouldShowRequestPermissionRationale()) { toast("请在 Shizuku / Sui 管理器中为 PairLink 授权"); return; }
                awaitingShizuku = true; Shizuku.requestPermission(200);
            } catch (RuntimeException e) { awaitingShizuku = false; toast("Shizuku / Sui 授权请求失败"); }
        });
    }
    public void selectAppControlMode(String mode) {
        if (!mode.equals("device_owner") && !mode.equals("shizuku") && !mode.equals("root")) return;
        if (mode.equals("device_owner") && !AppControl.isOwner(this)) { ownerInstructions(); return; }
        if (mode.equals("shizuku") && (!ShizukuBridge.granted() || !Config.prefs(this).getBoolean("shizuku", false))) {
            toast("请先开启并授权 Shizuku / Sui"); return;
        }
        if (mode.equals("root") && !Config.prefs(this).getBoolean("root", false)) { toast("请先开启 Shell 并单独授权 root"); return; }
        Config.prefs(this).edit().putString("app_control_mode", mode).apply();
        refreshState(); policy();
    }
    public void ownerInstructions() {
        ui.message("设备所有者", "只有 PairLink 自身成为设备所有者才能使用此模式，普通设备管理员权限不够。\n\n可在满足系统设备所有者配置条件时，通过 ADB 执行：\n\nadb shell dpm set-device-owner com.lijinmu.pairlink/.PairLinkAdminReceiver\n\n已存在其他设备所有者或账号等情况下，系统可能拒绝。应用不会自动激活设备所有者。");
    }
    public void changeAppControl(boolean checked) {
        if (!checked) {
            Config.prefs(this).edit().putBoolean("app_control", false).apply(); refreshState(); policy(); return;
        }
        ui.confirm("允许暂停应用", "对方可以暂停和恢复指定应用。本机选择的执行方式为：" + AppControl.label(AppControl.mode(this))
            + "。恢复本机按钮可在关闭共享后使用。关闭授权不会自动恢复已暂停应用。", "开启", () -> {
            Config.prefs(this).edit().putBoolean("app_control", true).apply(); refreshState();
        });
    }
    public void suspendPeerApp() { changePeerApp(true); }
    public void resumePeerApp() { changePeerApp(false); }
    private void changePeerApp(boolean suspended) {
        String pkg = ui.getTargetPackage().trim();
        if (pkg.length() > 255 || !AppControl.PACKAGE.matcher(pkg).matches()) { toast("请输入有效的应用包名"); return; }
        ui.confirm(suspended ? "暂停对方应用" : "恢复对方应用", pkg, suspended ? "暂停" : "恢复", () -> {
            try { send("set_app_suspended", new JSONObject().put("package_name", pkg).put("suspended", suspended)); }
            catch (Exception e) { show(e.getMessage()); }
        });
    }
    public void restoreLocalApps() {
        if (busy) { toast("请等待当前请求完成"); return; }
        if (AppControl.tracked(this).isEmpty()) { toast("本机没有由 PairLink 记录的暂停应用"); return; }
        ui.confirm("恢复本机应用", "恢复以下由 PairLink 记录的暂停应用：\n" + String.join("\n", AppControl.tracked(this)), "恢复", () -> {
            busy = true; ui.setBusy(true); show("正在恢复本机应用……");
            network.execute(() -> {
                try {
                    JSONArray results = AppControl.restoreLocal(this).getJSONArray("results");
                    runOnUiThread(() -> { if (!isDestroyed()) ui.showRestored(results); });
                } catch (Exception e) { show("恢复失败：" + e.getMessage()); }
                finally { busy = false; runOnUiThread(() -> ui.setBusy(false)); }
            });
        });
    }
    public void changeSendNotifications(boolean checked) {
        if (!checked) {
            Config.prefs(this).edit().putBoolean("send_notifications", false).apply();
            refreshState(); policy();
            return;
        }
        ui.confirm("允许发送普通通知", "开启后，对方可以在你的手机上发送普通系统通知。通知仍受系统通知权限和通知开关控制。", "开启", () -> {
            Config.prefs(this).edit().putBoolean("send_notifications", true).apply();
            refreshState(); policy();
        });
    }
    public void changeNotifications(boolean checked) {
        if (!checked) {
            Config.prefs(this).edit().putBoolean("notifications", false).apply();
            refreshState();
            return;
        }
        ui.confirm("开启通知共享", "开启后，对方可以读取通知中心当前的应用名、标题和正文。需要你在系统设置中单独授予通知访问权限。", "开启", () -> {
            Config.prefs(this).edit().putBoolean("notifications", true).apply();
            refreshState();
            if (!NotificationReaderService.hasAccess(this)) notificationPermission();
            else NotificationReaderService.ensureConnected(this);
        });
    }
    public void notificationPermission() {
        try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
        catch (ActivityNotFoundException e) { toast("此系统未提供通知访问设置，请在系统设置中搜索通知使用权"); }
    }
    public void usagePermission() { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); }
    public void testConnection() {
        if (busy) { toast("请等待当前请求完成"); return; }
        busy = true; ui.setBusy(true); show("正在测试服务器连接……");
        network.execute(() -> {
            try { JSONObject r = Net.request(this, Config.server(this), "/health", null); show("服务器连接成功\n" + r.toString(2) + "\n接收指令需要开启共享并显示在线。"); }
            catch (Exception e) { show("连接失败：" + e.getMessage()); }
            finally { busy = false; runOnUiThread(() -> ui.setBusy(false)); }
        });
    }
    public void queryStatus() { send("get_status", new JSONObject()); }
    public void queryInstalledApps() { send("get_installed_apps", new JSONObject()); }
    public void sendNotification() {
        String title = ui.getNotificationTitle().trim(), content = ui.getNotificationContent().trim();
        if (title.isEmpty() || content.isEmpty()) { toast("请输入通知标题和内容"); return; }
        ui.confirm("发送普通通知到 " + Config.peer(this), title + "\n\n" + content, "发送", () -> {
            try { send("send_notification", new JSONObject().put("title", title).put("content", content)); }
            catch (Exception e) { show(e.getMessage()); }
        });
    }
    public void changeMedia(boolean checked) {
        if (!checked) {
            Config.prefs(this).edit().putBoolean("media", false).apply();
            refreshState();
            return;
        }
        ui.confirm("开启媒体播放共享", "允许对方查询系统媒体面板中的应用、曲目、作者、播放状态和进度。需要通知访问权限，不会录音。", "开启", () -> {
            Config.prefs(this).edit().putBoolean("media", true).apply();
            refreshState();
            if (!NotificationReaderService.hasAccess(this)) notificationPermission();
            else NotificationReaderService.ensureConnected(this);
        });
    }
    public void queryMedia() { send("get_media", new JSONObject()); }
    public void queryNotifications() { send("get_notifications", new JSONObject()); }
    public void queryRecent() {
        try { send("get_recent_apps", new JSONObject().put("hours", 24)); } catch (Exception e) { show(e.getMessage()); }
    }
    public void executeShell() {
        String command = ui.getScript();
        boolean requestRoot = ui.getSendRoot();
        boolean requestShizuku = ui.getSendShizuku();
        if (command.trim().isEmpty()) { toast("请输入命令"); return; }
        ui.confirm("执行到 " + Config.peer(this), command, "执行", () -> {
            try { send("shell", new JSONObject().put("command", command).put("root", requestRoot).put("shizuku", requestShizuku).put("timeout_seconds", 20)); }
            catch (Exception e) { show(e.getMessage()); }
        });
    }
    public void showLogs() {
        String logs = Config.prefs(this).getString("logs", "");
        ui.showRecords(logs, Config.own(this));
    }
    public void clearLogs() {
        Config.clearLogs(this);
        ui.message("本机记录", "记录已清除");
    }
    private void authorizeRoot() {
        network.execute(() -> {
            try {
                ShellRunner.Result r = new ShellRunner().run("id", true, 20, () -> true);
                boolean ok = !r.timedOut && r.exitCode == 0 && r.stdout.contains("uid=0");
                runOnUiThread(() -> {
                    if (ok && Config.prefs(this).getBoolean("shell", false)) {
                        Config.prefs(this).edit().putBoolean("root", true).apply(); refreshState(); toast("root 授权成功");
                    } else toast("root 授权失败或 Shell 已关闭");
                });
            } catch (Exception e) { runOnUiThread(() -> toast("root 不可用：" + e.getMessage())); }
        });
    }
    private void policy() {
        if (Config.sharing(this)) startService(new Intent(this, LinkService.class).setAction(LinkService.POLICY));
    }
    private boolean settingsValid() {
        try {
            okhttp3.HttpUrl url = okhttp3.HttpUrl.get(Config.server(this));
            return "https".equals(url.scheme()) && url.encodedPath().equals("/") && url.query() == null && url.fragment() == null
                && url.username().isEmpty() && url.password().isEmpty() && Config.validId(Config.own(this))
                && Config.validId(Config.peer(this)) && !Config.own(this).equals(Config.peer(this));
        } catch (Exception e) { return false; }
    }
    public void saveSettings() {
        if (Config.sharing(this)) { toast("请先暂停共享再修改连接设置"); return; }
        String s = Config.normalize(ui.getServer()), a = ui.getOwn().trim(), b = ui.getPeer().trim();
        try {
            okhttp3.HttpUrl url = okhttp3.HttpUrl.get(s);
            if (!"https".equals(url.scheme()) || !url.encodedPath().equals("/") || url.query() != null || url.fragment() != null
                || !url.username().isEmpty() || !url.password().isEmpty() || !Config.validId(a) || !Config.validId(b) || a.equals(b)) throw new Exception();
            Config.prefs(this).edit().putString("server", s).putString("own", a).putString("peer", b).apply();
            toast("已保存");
        } catch (Exception e) { toast("填写 HTTPS 根地址；双方 ID 必须不同，只含字母、数字、点、下划线或连字符"); }
    }
    private void send(String action, JSONObject params) {
        if (busy) { toast("请等待当前请求完成"); return; }
        if (!settingsValid()) { toast("请先保存连接设置"); return; }
        busy = true; ui.setBusy(true); show("正在请求对方……");
        String base = Config.server(this), sender = Config.own(this), target = Config.peer(this);
        network.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("sender_id", sender).put("target_id", target).put("action", action).put("params", params);
                JSONObject response = Net.request(this, base, "/api/commands", body);
                String id = response.getString("command_id");
                long deadline = SystemClock.elapsedRealtime() + 50000;
                while ("pending".equals(response.optString("status")) && SystemClock.elapsedRealtime() < deadline) {
                    Thread.sleep(1000);
                    response = Net.request(this, base, "/api/commands/" + id, null);
                }
                if ("completed".equals(response.optString("status"))
                    && ("get_recent_apps".equals(action) || "get_installed_apps".equals(action)
                        || "get_notifications".equals(action) || "get_media".equals(action) || "get_status".equals(action))) {
                    JSONObject result = response.getJSONObject("result");
                    runOnUiThread(() -> { if (!isDestroyed()) ui.showQuery(action, result, target); });
                } else show(format(action, response));
                Config.log(this, "请求 " + action + "：" + response.optString("status"));
            } catch (Exception e) { show("请求失败：" + e.getMessage() + ("shell".equals(action) ? "\n不要自动重发 Shell，命令可能已经执行。" : "")); }
            finally { busy = false; runOnUiThread(() -> ui.setBusy(false)); }
        });
    }
    private String format(String action, JSONObject response) throws Exception {
        String state = response.optString("status");
        if ("offline".equals(state)) return "对方当前离线，请让对方打开应用并开启共享。";
        if ("failed".equals(state)) return "执行失败：" + response.optString("error");
        if (!"completed".equals(state)) return "请求超时或仍在执行。指令 ID：" + response.optString("command_id")
            + ("shell".equals(action) ? "\nShell 可能已经执行，请勿直接重复提交。" : "");
        JSONObject r = response.getJSONObject("result");
        if ("set_app_suspended".equals(action)) return formatAppControl(r);
        if ("send_notification".equals(action)) return "普通通知已发送\n标题：" + r.optString("title");
        if ("shell".equals(action)) return "执行方式：" + ("shizuku".equals(r.optString("backend")) ? "Shizuku / Sui（UID " + r.optInt("shizuku_uid", -1) + "）" : r.optBoolean("root") ? "root" : "普通应用")
            + "\n退出码：" + r.optInt("exit_code") + "\n超时：" + r.optBoolean("timed_out") + "\n取消：" + r.optBoolean("cancelled")
            + "\n输出截断：" + r.optBoolean("truncated") + "\n\n标准输出\n" + r.optString("stdout") + "\n错误输出\n" + r.optString("stderr");
        return r.toString(2);
    }
    private String formatAppControl(JSONObject result) {
        if (result.has("error")) return result.optString("package_name") + "\n恢复失败：" + result.optString("error");
        return result.optString("app_name", result.optString("package_name")) + "\n" + result.optString("package_name")
            + "\n当前状态：" + (result.optBoolean("suspended") ? "已暂停" : "未暂停")
            + "\n执行方式：" + AppControl.label(result.optString("backend")) + "\n" + result.optString("note");
    }
    private void show(String s) { runOnUiThread(() -> { if (!isDestroyed()) ui.showResult(s == null ? "" : s); }); }
    private void toast(String s) { ui.message("提示", s); }
    @Override public void onResume() {
        super.onResume();
        refreshState();
        NotificationReaderService.ensureConnected(this);
        permissionHandler.removeCallbacks(permissionRefresh);
        permissionHandler.postDelayed(permissionRefresh, 1000);
    }
    @Override public void onPause() {
        permissionHandler.removeCallbacks(permissionRefresh);
        super.onPause();
    }
    @Override public void onDestroy() {
        permissionHandler.removeCallbacks(permissionRefresh);
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionResult);
        Config.prefs(this).unregisterOnSharedPreferenceChangeListener(preferenceListener);
        network.shutdown(); super.onDestroy();
    }
}
