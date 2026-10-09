package com.lijinmu.pairlink;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.os.*;
import android.provider.Settings;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import android.content.SharedPreferences;
import org.json.*;
import java.util.concurrent.*;
import java.util.Set;
import java.util.HashSet;
import rikka.shizuku.Shizuku;
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener;

public final class MainActivity extends ComponentActivity {
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final UiState ui = new UiState();
    private volatile boolean busy;
    private boolean awaitingShizuku;
    private boolean awaitingDhizuku;
    private final DhizukuRequestPermissionListener dhizukuPermissionResult = new DhizukuRequestPermissionListener() {
        @Override public void onRequestPermission(int result) {
            runOnUiThread(() -> {
                if (isDestroyed() || !awaitingDhizuku) return;
                boolean granted = result == PackageManager.PERMISSION_GRANTED && DhizukuBridge.granted(MainActivity.this);
                if (granted)
                    Config.prefs(MainActivity.this).edit().putBoolean("dhizuku", true).putString("app_control_mode", "dhizuku").apply();
                awaitingDhizuku = false;
                refreshState(); policy();
                toast(granted ? "Dhizuku 已授权" : "Dhizuku 未授权或服务已停止");
            });
        }
    };
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
        ui.setStatusNotification(Config.statusNotification(this));
        ui.setUsageAllowed(DeviceQueries.usageAllowed(this));
        ui.setNotificationAccess(NotificationReaderService.hasAccess(this));
        boolean dhizukuGranted = DhizukuBridge.granted(this);
        boolean dhizuku = Config.prefs(this).getBoolean("dhizuku", false) && dhizukuGranted;
        ui.setPrivilegeStatus(ShizukuBridge.status() + "\nDhizuku：" + (dhizukuGranted ? (dhizuku ? "已授权并开启" : "已授权，未开启") : "未授权或未运行"));
        boolean shizuku = Config.prefs(this).getBoolean("shizuku", false) && ShizukuBridge.granted();
        boolean root = Config.prefs(this).getBoolean("root", false);
        String mode = AppControl.mode(this);
        if ("shizuku".equals(mode) && shizuku) ui.setPrivilegeMode("Shizuku");
        else if ("root".equals(mode) && root) ui.setPrivilegeMode("root");
        else if ("dhizuku".equals(mode) && dhizuku) ui.setPrivilegeMode("Dhizuku");
        else if (root) ui.setPrivilegeMode("root");
        else if (shizuku) ui.setPrivilegeMode("Shizuku");
        else if (dhizuku) ui.setPrivilegeMode("Dhizuku");
        else ui.setPrivilegeMode("无特权");
    }
    private void refreshState() {
        refreshAccessPermissions();
        ui.setNightMode(Config.nightMode(this));
        ui.setDhizuku(Config.prefs(this).getBoolean("dhizuku", false));
        ui.setSharing(Config.sharing(this));
        ui.setShell(Config.prefs(this).getBoolean("shell", false));
        ui.setRoot(Config.prefs(this).getBoolean("root", false));
        ui.setNotifications(Config.prefs(this).getBoolean("notifications", false));
        ui.setMedia(Config.prefs(this).getBoolean("media", false));
        ui.setSendNotifications(Config.prefs(this).getBoolean("send_notifications", false));
        ui.setShizuku(Config.prefs(this).getBoolean("shizuku", false));
        ui.setAppControl(Config.prefs(this).getBoolean("app_control", false));
        ui.setAppControlMode(AppControl.mode(this));
        ui.setPauseBlacklistCount(Config.pauseBlacklist(this).size());
        ui.setConnection(Config.sharing(this) ? Config.prefs(this).getString("connection", "正在连接") : "共享已关闭");
    }
    public void changeStatusNotification(boolean checked) {
        Config.prefs(this).edit().putBoolean("status_notification", checked).apply();
        refreshState();
        if (checked) requestNotificationPermission();
        if (Config.sharing(this)) LinkService.start(this, LinkService.NOTIFICATION);
        else if (!checked) getSystemService(NotificationManager.class).cancel(100);
    }
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
    }
    public void changeSharing(boolean checked) {
        if (checked && !settingsValid()) { toast("请先保存有效的连接设置"); return; }
        if (checked && Config.statusNotification(this)) requestNotificationPermission();
        Config.prefs(this).edit().putBoolean("sharing", checked).apply();
        if (checked) LinkService.start(this, null);
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
    public void selectNightMode(String mode) {
        if (!"system".equals(mode) && !"on".equals(mode) && !"off".equals(mode)) return;
        Config.prefs(this).edit().putString("night_mode", mode).apply();
        refreshState();
    }
    public void changeDhizuku(boolean checked) {
        if (!checked) {
            awaitingDhizuku = false;
            Config.prefs(this).edit().putBoolean("dhizuku", false).apply();
            refreshState(); policy(); return;
        }
        ui.confirm("授权 Dhizuku", "允许 PairLink 通过 Dhizuku 暂停和恢复应用，不用于 Shell。请先在 Dhizuku 中完成激活，再单独授予 PairLink 权限。", "授权", () -> {
            if (!DhizukuBridge.available(this)) { toast("请先安装、激活并启动 Dhizuku"); return; }
            if (DhizukuBridge.granted(this)) {
                Config.prefs(this).edit().putBoolean("dhizuku", true).putString("app_control_mode", "dhizuku").apply();
                refreshState(); policy(); return;
            }
            if (awaitingDhizuku) { toast("请先完成 Dhizuku 授权"); return; }
            try {
                awaitingDhizuku = true;
                DhizukuBridge.request(this, dhizukuPermissionResult);
            } catch (Exception e) {
                awaitingDhizuku = false;
                show("Dhizuku 授权请求失败：" + e.getMessage());
            }
        });
    }
    public void selectAppControlMode(String mode) {
        if (!mode.equals("dhizuku") && !mode.equals("shizuku") && !mode.equals("root")) return;
        if (mode.equals("dhizuku") && (!DhizukuBridge.granted(this) || !Config.prefs(this).getBoolean("dhizuku", false))) {
            changeDhizuku(true); return;
        }
        if (mode.equals("shizuku") && (!ShizukuBridge.granted() || !Config.prefs(this).getBoolean("shizuku", false))) {
            toast("请先开启并授权 Shizuku / Sui"); return;
        }
        if (mode.equals("root") && !Config.prefs(this).getBoolean("root", false)) { toast("请先开启 Shell 并单独授权 root"); return; }
        Config.prefs(this).edit().putString("app_control_mode", mode).apply();
        refreshState(); policy();
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
        if (busy) { toast("请等待当前请求完成"); return; }
        AppPickerPage picker = ui.getAppPicker();
        if (picker == null) { openAppPicker(); return; }
        if (picker.getBlacklist()) { toast("请从操作页选择对方应用"); return; }
        if (!pickerMatches(picker)) { ui.clearAppPicker(); toast("连接设置已更改，请重新获取对方应用列表"); return; }
        JSONArray selected = ui.selectedAppRequests();
        if (selected.length() == 0) { toast("请先选择应用"); return; }
        StringBuilder names = new StringBuilder("对方设备：").append(picker.getDevice()).append("\n");
        for (int i = 0; i < selected.length(); i++) {
            JSONObject item = selected.optJSONObject(i);
            names.append("\n").append(item.optString("app_name")).append("\n").append(item.optString("package_name")).append("\n");
        }
        ui.confirm((suspended ? "暂停" : "恢复") + "所选 " + selected.length() + " 个应用", names.toString(), suspended ? "暂停" : "恢复",
            () -> executePeerApps(picker, selected, suspended));
    }
    public void openAppPicker() { send("get_installed_apps", new JSONObject(), true); }
    private boolean pickerMatches(AppPickerPage picker) {
        return !picker.getBlacklist() && picker.getServer().equals(Config.server(this)) && picker.getSender().equals(Config.own(this))
            && picker.getDevice().equals(Config.peer(this));
    }
    public void openPauseBlacklist() {
        if (busy) { toast("请等待当前请求完成"); return; }
        AppPickerPage picker = ui.getAppPicker();
        Set<String> chosen = picker != null && picker.getBlacklist()
            ? new HashSet<>(ui.getSelectedPeerApps()) : Config.pauseBlacklist(this);
        busy = true; ui.setBusy(true); show("正在加载本机应用……");
        network.execute(() -> {
            try {
                JSONArray apps = new JSONArray();
                Set<String> installed = new HashSet<>();
                for (ApplicationInfo app : getPackageManager().getInstalledApplications(0)) {
                    if ((app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) continue;
                    String name = app.packageName;
                    try { name = getPackageManager().getApplicationLabel(app).toString(); } catch (RuntimeException ignored) {}
                    apps.put(new JSONObject().put("package_name", app.packageName).put("app_name", name).put("installed", true));
                    installed.add(app.packageName);
                }
                for (String pkg : chosen) {
                    if (installed.contains(pkg) || getPackageName().equals(pkg)) continue;
                    apps.put(new JSONObject().put("package_name", pkg).put("app_name", pkg).put("installed", false));
                }
                runOnUiThread(() -> { if (!isDestroyed()) ui.showPauseBlacklist(apps, chosen, Config.own(this), getPackageName()); });
            } catch (Exception e) { show("加载本机应用失败：" + e.getMessage()); }
            finally { busy = false; runOnUiThread(() -> ui.setBusy(false)); }
        });
    }
    public void savePauseBlacklist() {
        if (busy) { toast("请等待当前请求完成"); return; }
        AppPickerPage picker = ui.getAppPicker();
        if (picker == null || !picker.getBlacklist()) return;
        Set<String> selected = new HashSet<>();
        JSONArray apps = ui.selectedAppRequests();
        for (int i = 0; i < apps.length(); i++) {
            String pkg = apps.optJSONObject(i).optString("package_name");
            if (!getPackageName().equals(pkg) && pkg.length() <= 255 && AppControl.PACKAGE.matcher(pkg).matches()) selected.add(pkg);
        }
        Config.prefs(this).edit().putStringSet("pause_blacklist", selected).apply();
        ui.clearAppPicker(); refreshState();
        ui.message("应用暂停黑名单", "已保存，共 " + selected.size() + " 个应用。对方不能通过应用暂停功能暂停这些应用，仍可恢复。");
    }
    private void executePeerApps(AppPickerPage picker, JSONArray selected, boolean suspended) {
        if (busy) { toast("请等待当前请求完成"); return; }
        if (!settingsValid() || !pickerMatches(picker)) { ui.clearAppPicker(); toast("连接设置已更改，请重新选择应用"); return; }
        busy = true; ui.setBusy(true); show("正在处理所选应用……");
        network.execute(() -> {
            JSONArray results = new JSONArray();
            String stopped = null;
            try {
                for (int i = 0; i < selected.length(); i++) {
                    JSONObject app = selected.getJSONObject(i);
                    String pkg = app.getString("package_name");
                    JSONObject item = new JSONObject().put("package_name", pkg).put("app_name", app.optString("app_name", pkg));
                    if (stopped != null) {
                        results.put(item.put("status", "not_executed").put("error", stopped));
                        continue;
                    }
                    if (!pickerMatches(picker)) {
                        stopped = "连接设置已更改，后续应用未执行";
                        results.put(item.put("status", "not_executed").put("error", stopped));
                        continue;
                    }
                    show("正在" + (suspended ? "暂停" : "恢复") + " " + (i + 1) + " / " + selected.length() + "\n" + app.optString("app_name", pkg) + "\n" + pkg);
                    try {
                        JSONObject response = requestCommand(picker.getServer(), picker.getSender(), picker.getDevice(), "set_app_suspended",
                            new JSONObject().put("package_name", pkg).put("suspended", suspended));
                        String status = response.optString("status");
                        if ("completed".equals(status)) {
                            JSONObject result = response.getJSONObject("result");
                            if (!pkg.equals(result.optString("package_name"))) throw new Exception("返回的应用与请求不一致");
                            item.put("status", "completed").put("result", result);
                        } else if ("failed".equals(status)) {
                            item.put("status", "failed").put("error", response.optString("error", "对方执行失败"));
                        } else if ("offline".equals(status)) {
                            stopped = "对方已离线，后续应用未执行";
                            item.put("status", "not_executed").put("error", "对方当前离线");
                        } else {
                            stopped = "上一项状态未确认，后续应用未执行。请检查后再操作";
                            item.put("status", "uncertain").put("error", "请求超时或仍在执行，指令 ID：" + response.optString("command_id") + "。请勿直接重复提交");
                        }
                        Config.log(this, (suspended ? "暂停 " : "恢复 ") + pkg + "：" + item.optString("status"));
                    } catch (Exception e) {
                        stopped = "请求中断，后续应用未执行。请检查对方状态后再操作";
                        item.put("status", "uncertain").put("error", "请求失败：" + e.getMessage() + "。该应用可能已经执行，请勿直接重复提交");
                    }
                    results.put(item);
                }
                runOnUiThread(() -> { if (!isDestroyed()) ui.showPeerAppResults(results, picker.getDevice(), suspended); });
            } catch (Exception e) { show("批量操作失败：" + e.getMessage() + "。部分应用可能已经执行，请检查后再操作"); }
            finally { busy = false; runOnUiThread(() -> ui.setBusy(false)); }
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
        if (Config.sharing(this)) LinkService.start(this, LinkService.POLICY);
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
        if (busy) { toast("请等待当前请求完成"); return; }
        if (Config.sharing(this)) { toast("请先暂停共享再修改连接设置"); return; }
        String s = Config.normalize(ui.getServer()), a = ui.getOwn().trim(), b = ui.getPeer().trim();
        try {
            okhttp3.HttpUrl url = okhttp3.HttpUrl.get(s);
            if (!"https".equals(url.scheme()) || !url.encodedPath().equals("/") || url.query() != null || url.fragment() != null
                || !url.username().isEmpty() || !url.password().isEmpty() || !Config.validId(a) || !Config.validId(b) || a.equals(b)) throw new Exception();
            Config.prefs(this).edit().putString("server", s).putString("own", a).putString("peer", b).apply();
            ui.clearAppPicker();
            toast("已保存");
        } catch (Exception e) { toast("填写 HTTPS 根地址；双方 ID 必须不同，只含字母、数字、点、下划线或连字符"); }
    }
    private void send(String action, JSONObject params) {
        send(action, params, false);
    }
    private JSONObject requestCommand(String base, String sender, String target, String action, JSONObject params) throws Exception {
        JSONObject body = new JSONObject().put("sender_id", sender).put("target_id", target).put("action", action).put("params", params);
        JSONObject response = Net.request(this, base, "/api/commands", body);
        String id = response.optString("command_id");
        if ("pending".equals(response.optString("status")) && id.isEmpty()) throw new Exception("服务器未返回指令 ID");
        long deadline = SystemClock.elapsedRealtime() + 50000;
        while ("pending".equals(response.optString("status")) && SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(1000);
            response = Net.request(this, base, "/api/commands/" + id, null);
        }
        return response;
    }
    private void send(String action, JSONObject params, boolean chooseApps) {
        if (busy) { toast("请等待当前请求完成"); return; }
        if (!settingsValid()) { toast("请先保存连接设置"); return; }
        busy = true; ui.setBusy(true); show("正在请求对方……");
        String base = Config.server(this), sender = Config.own(this), target = Config.peer(this);
        network.execute(() -> {
            try {
                JSONObject response = requestCommand(base, sender, target, action, params);
                if (chooseApps && "completed".equals(response.optString("status"))) {
                    JSONObject result = response.getJSONObject("result");
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        if (!base.equals(Config.server(this)) || !sender.equals(Config.own(this)) || !target.equals(Config.peer(this))) {
                            ui.clearAppPicker(); ui.message("应用选择器", "连接设置已更改，请重新获取应用列表"); return;
                        }
                        ui.showAppPicker(result, base, sender, target, getPackageName());
                    });
                } else if ("completed".equals(response.optString("status"))
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

