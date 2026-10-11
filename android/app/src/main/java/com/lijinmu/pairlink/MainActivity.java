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
    private boolean awaitingShizukuMode;
    private boolean awaitingDhizuku;
    private volatile int modeRequestGeneration;
    private String pendingPermission;
    private boolean permissionSettingsOpened;
    private boolean pendingAppControl;
    private boolean pendingShell;
    private boolean pendingScreenshot;
    private final DhizukuRequestPermissionListener dhizukuPermissionResult = new DhizukuRequestPermissionListener() {
        @Override public void onRequestPermission(int result) {
            runOnUiThread(() -> {
                if (isDestroyed() || !awaitingDhizuku) return;
                boolean granted = result == PackageManager.PERMISSION_GRANTED && DhizukuBridge.granted(MainActivity.this);
                if (granted)
                    finishPrivilegeMode("dhizuku");
                awaitingDhizuku = false;
                refreshState(); policy();
                if (!granted) { pendingAppControl = false; pendingShell = false; pendingScreenshot = false; ui.message("Dhizuku 授权失败", "权限未授予或服务已停止，请在 Dhizuku 中完成激活、启动并允许 PairLink"); }
            });
        }
    };
    private int versionTapCount;
    private long lastVersionTap;
    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionResult = (code, result) -> {
        if (code != 200) return;
        runOnUiThread(() -> {
            if (isDestroyed() || !awaitingShizukuMode) return;
            boolean granted = result == PackageManager.PERMISSION_GRANTED && ShizukuBridge.granted();
            if (awaitingShizukuMode && granted)
                finishPrivilegeMode("shizuku");
            awaitingShizukuMode = false;
            refreshState(); policy();
            if (!granted) { pendingAppControl = false; pendingShell = false; pendingScreenshot = false; ui.message("Shizuku / Sui 授权失败", "权限未授予或服务已停止，请在 Shizuku / Sui 管理器中允许 PairLink"); }
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
        Config.prefs(this).edit().remove("root").remove("shizuku").apply();
        if (b != null) { pendingPermission = b.getString("pending_permission"); permissionSettingsOpened = pendingPermission != null; }
        Shizuku.addRequestPermissionResultListener(shizukuPermissionResult);
        ui.setServer(Config.server(this)); ui.setOwn(Config.own(this)); ui.setPeer(Config.peer(this));
        refreshState();
        Config.prefs(this).registerOnSharedPreferenceChangeListener(preferenceListener);
        PairLinkUi.render(this, ui);
        CommandSummaryWorker.ensureScheduled(this);
        openHistoryIntent(getIntent());
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
        boolean dhizuku = "dhizuku".equals(AppControl.mode(this)) && dhizukuGranted;
        ui.setPrivilegeStatus(ShizukuBridge.status() + "\nDhizuku：" + (dhizukuGranted ? (dhizuku ? "已授权并选择" : "已授权，未选择") : "未授权或未运行"));
        String mode = AppControl.mode(this);
        if ("shizuku".equals(mode) && ShizukuBridge.granted()) ui.setPrivilegeMode("Shizuku");
        else if ("root".equals(mode)) ui.setPrivilegeMode("root");
        else if ("dhizuku".equals(mode) && dhizukuGranted) ui.setPrivilegeMode("Dhizuku");
        else ui.setPrivilegeMode("无特权");
    }
    private void refreshState() {
        refreshAccessPermissions();
        SharedPreferences prefs = Config.prefs(this);
        ui.setServerVersion(Config.server(this).equals(prefs.getString("server_version_url", ""))
            ? prefs.getString("server_version", "未获取，点击刷新") : "未获取，点击刷新");
        ui.setCommandSummary(CommandSummaryWorker.enabled(this));
        ui.setSummaryMinutes(CommandSummaryWorker.interval(this));
        ui.setNightMode(Config.nightMode(this));
        ui.setDhizuku(Config.prefs(this).getBoolean("dhizuku", false));
        ui.setSharing(Config.sharing(this));
        ui.setShell(Config.prefs(this).getBoolean("shell", false));
        ui.setScreenshots(Config.prefs(this).getBoolean("screenshots", false));
        ui.setScreenshotBlacklistCount(Config.screenshotBlacklist(this).size());
        ui.setUsageSharing(Config.usageSharing(this));
        ui.setNotifications(Config.prefs(this).getBoolean("notifications", false));
        ui.setMedia(Config.prefs(this).getBoolean("media", false));
        ui.setSendNotifications(Config.prefs(this).getBoolean("send_notifications", false));
        ui.setAppControl(Config.prefs(this).getBoolean("app_control", false));
        ui.setAppControlMode(AppControl.mode(this));
        ui.setPauseBlacklistCount(Config.pauseBlacklist(this).size());
        ui.setConnection(Config.sharing(this) ? Config.prefs(this).getString("connection", "正在连接") : "共享已关闭");
    }
    public void changeCommandSummary(boolean checked) {
        if (checked && !ensureNotificationPermission("command_summary", CommandSummaryWorker.CHANNEL_ID)) return;
        try { CommandSummaryWorker.setEnabled(this, checked); refreshState(); }
        catch (Exception e) { ui.message("指令定时通知", "无法设置后台任务：" + e.getMessage()); }
    }
    public void selectSummaryInterval(int minutes) {
        try { CommandSummaryWorker.changeInterval(this, minutes); refreshState(); }
        catch (Exception e) { ui.message("指令定时通知", "无法更新通知间隔：" + e.getMessage()); }
    }
    public void changeStatusNotification(boolean checked) {
        if (checked && !ensureNotificationPermission("status_notification", "connection")) return;
        Config.prefs(this).edit().putBoolean("status_notification", checked).apply();
        refreshState();
        if (Config.sharing(this)) LinkService.start(this, LinkService.NOTIFICATION);
        else if (!checked) getSystemService(NotificationManager.class).cancel(100);
    }
    private boolean notificationAllowed(String channelId) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null || !manager.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) return false;
        NotificationChannel channel = manager.getNotificationChannel(channelId);
        return channel != null && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
    private boolean ensureNotificationPermission(String setting, String channelId) {
        if ("connection".equals(channelId)) LinkService.createConnectionChannel(this);
        else if (CommandSummaryWorker.CHANNEL_ID.equals(channelId)) CommandSummaryWorker.createChannel(this);
        else NotificationSender.createMessageChannel(this);
        if (notificationAllowed(channelId)) return true;
        NotificationManager manager = getSystemService(NotificationManager.class);
        boolean appAllowed = manager != null && manager.areNotificationsEnabled()
            && (Build.VERSION.SDK_INT < 33 || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED);
        Intent intent = new Intent(appAllowed ? Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS : Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        if (appAllowed) intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId);
        return guidePermission(setting, "需要允许 PairLink 的" + (appAllowed ? ("connection".equals(channelId) ? "共享连接通知" : CommandSummaryWorker.CHANNEL_ID.equals(channelId) ? "指令定时汇总通知" : "消息通知") : "系统通知权限"), intent);
    }
    private boolean ensureNotificationAccess(String setting) {
        if (NotificationReaderService.hasAccess(this)) return true;
        return guidePermission(setting, "需要授予 PairLink 通知访问权限", new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }
    private boolean guidePermission(String setting, String message, Intent intent) {
        pendingPermission = setting;
        permissionSettingsOpened = false;
        Config.prefs(this).edit().putBoolean(setting, false).apply();
        refreshState();
        ui.setDialog(null);
        try {
            startActivity(intent);
            permissionSettingsOpened = true;
            permissionToast(message);
        } catch (ActivityNotFoundException e) {
            pendingPermission = null;
            ui.message("无法打开权限设置", message + "\n此系统没有提供对应页面，请在系统设置中手动授权后再开启");
        }
        return false;
    }
    private void permissionToast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private void finishPermissionSettings() {
        if (!permissionSettingsOpened || pendingPermission == null) return;
        String setting = pendingPermission;
        pendingPermission = null;
        permissionSettingsOpened = false;
        boolean granted = "usage_sharing".equals(setting) ? DeviceQueries.usageAllowed(this)
            : ("notifications".equals(setting) || "media".equals(setting))
            ? NotificationReaderService.hasAccess(this)
            : notificationAllowed("send_notifications".equals(setting) ? NotificationSender.CHANNEL_ID
                : "command_summary".equals(setting) ? CommandSummaryWorker.CHANNEL_ID : "connection");
        if (!granted) { permissionToast("所需权限尚未授予，开关保持关闭"); return; }
        switch (setting) {
            case "usage_sharing": changeUsageSharing(true); break;
            case "command_summary": changeCommandSummary(true); break;
            case "status_notification": changeStatusNotification(true); break;
            case "send_notifications": changeSendNotifications(true); break;
            case "notifications": changeNotifications(true); break;
            case "media": changeMedia(true); break;
            case "sharing": changeSharing(true); break;
            default: break;
        }
    }
    public void changeSharing(boolean checked) {
        if (checked && !settingsValid()) { toast("请先保存有效的连接设置"); return; }
        if (checked && Config.statusNotification(this) && !ensureNotificationPermission("sharing", "connection")) return;
        Config.prefs(this).edit().putBoolean("sharing", checked).apply();
        if (checked) LinkService.start(this, null);
        else stopService(new Intent(this, LinkService.class));
        refreshState();
    }
    public void changeShell(boolean checked) {
        if (!checked) {
            pendingShell = false;
            Config.prefs(this).edit().putBoolean("shell", false).apply(); refreshState(); policy();
            return;
        }
        if (busy || awaitingDhizuku || awaitingShizukuMode) { toast("请先完成当前请求或授权"); return; }
        String mode = AppControl.mode(this);
        if ("shizuku".equals(mode) || "root".equals(mode)) requestPrivilegeMode(mode, false, true);
        else confirmShell();
    }
    private void confirmShell() {
        String mode = AppControl.mode(this);
        ui.confirm("开启远程 Shell", "开启后，对方可以让此 APK 执行命令，执行权限跟随本机特权工作模式\n当前执行方式："
            + ("app".equals(ShellPolicy.backend(mode)) ? "普通应用权限" : AppControl.label(mode))
            + "\n第一版服务器没有身份验证，设备 ID 不代表身份\n仅在你愿意允许操作时开启", "开启", () -> {
            if (!mode.equals(AppControl.mode(this)) || ("shizuku".equals(mode) && !ShizukuBridge.granted())) {
                changeShell(true); return;
            }
            Config.prefs(this).edit().putBoolean("shell", true).apply(); refreshState(); policy();
        });
    }
    public void selectNightMode(String mode) {
        if (!"system".equals(mode) && !"on".equals(mode) && !"off".equals(mode)) return;
        Config.prefs(this).edit().putString("night_mode", mode).apply();
        refreshState();
    }
    public void selectAppControlMode(String mode) {
        if (!"root".equals(mode)) { requestPrivilegeMode(mode, false); return; }
        if (busy || awaitingDhizuku || awaitingShizukuMode) {
            toast("请先完成当前请求或授权"); return;
        }
        ui.confirm("警告",
            "root 权限是系统最高权限，使用不当可能造成数据泄露、系统损坏等后果\n\n"
                + "推荐使用 Dhizuku 或者 Shell 权限\n你已被警告使用，请确认理解风险后谨慎使用",
            "哦", () -> requestPrivilegeMode("root", false));
    }
    private void finishPrivilegeMode(String mode) {
        SharedPreferences prefs = Config.prefs(this);
        SharedPreferences.Editor editor = prefs.edit().putString("app_control_mode", mode);
        if (!mode.equals(AppControl.mode(this))) editor.putLong("screenshot_revision", prefs.getLong("screenshot_revision", 0) + 1);
        editor.apply();
        refreshState(); policy();
        boolean enableAppControl = pendingAppControl, enableShell = pendingShell, enableScreenshot = pendingScreenshot;
        pendingAppControl = false; pendingShell = false; pendingScreenshot = false;
        if (enableAppControl) confirmAppControl();
        else if (enableShell) confirmShell();
        else if (enableScreenshot) confirmScreenshot();
    }
    private void requestPrivilegeMode(String mode, boolean enableAppControl) {
        requestPrivilegeMode(mode, enableAppControl, false);
    }
    private void requestPrivilegeMode(String mode, boolean enableAppControl, boolean enableShell) {
        requestPrivilegeMode(mode, enableAppControl, enableShell, false);
    }
    private void requestPrivilegeMode(String mode, boolean enableAppControl, boolean enableShell, boolean enableScreenshot) {
        if (!mode.equals("none") && !mode.equals("dhizuku") && !mode.equals("shizuku") && !mode.equals("root")) return;
        if (busy || awaitingDhizuku || awaitingShizukuMode) { toast("请先完成当前请求或授权"); return; }
        int request = ++modeRequestGeneration;
        pendingAppControl = enableAppControl;
        pendingShell = enableShell;
        pendingScreenshot = enableScreenshot;
        if ("none".equals(mode)) { pendingAppControl = false; pendingShell = false; pendingScreenshot = false; finishPrivilegeMode(mode); return; }
        if ("dhizuku".equals(mode)) {
            if (!DhizukuBridge.available(this)) {
                pendingAppControl = false; pendingShell = false; pendingScreenshot = false;
                ui.message("Dhizuku 授权失败", "请先安装、激活并启动 Dhizuku，然后重新选择此模式");
                return;
            }
            if (DhizukuBridge.granted(this)) { finishPrivilegeMode(mode); return; }
            try {
                awaitingDhizuku = true;
                DhizukuBridge.request(this, dhizukuPermissionResult);
            } catch (Exception e) {
                awaitingDhizuku = false; pendingAppControl = false; pendingShell = false; pendingScreenshot = false;
                ui.message("Dhizuku 授权失败", "无法申请权限：" + e.getMessage());
            }
            return;
        }
        if ("shizuku".equals(mode)) {
            if (!ShizukuBridge.available()) {
                pendingAppControl = false; pendingShell = false; pendingScreenshot = false;
                ui.message("Shizuku / Sui 授权失败", "请先启动 Shizuku 或启用 Sui，然后重新选择此模式");
                return;
            }
            if (ShizukuBridge.granted()) { finishPrivilegeMode(mode); return; }
            try {
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    pendingAppControl = false; pendingShell = false; pendingScreenshot = false;
                    ui.message("Shizuku / Sui 授权失败", "权限已被拒绝，请在 Shizuku / Sui 管理器中允许 PairLink 后重试");
                    return;
                }
                awaitingShizukuMode = true;
                Shizuku.requestPermission(200);
            } catch (RuntimeException e) {
                awaitingShizukuMode = false; pendingAppControl = false; pendingShell = false; pendingScreenshot = false;
                ui.message("Shizuku / Sui 授权失败", "无法申请权限：" + e.getMessage());
            }
            return;
        }
        busy = true; ui.setBusy(true); show("正在申请并验证 root 权限……");
        network.execute(() -> {
            String failure = null;
            try {
                ShellRunner.Result result = new ShellRunner().run("id", true, 20, () -> request == modeRequestGeneration);
                if (result.timedOut || result.cancelled || result.exitCode != 0 || !result.stdout.contains("uid=0"))
                    failure = "root 权限未授予、申请超时或验证失败，请在 root 管理器中允许 PairLink";
            } catch (Exception e) { failure = "无法申请 root 权限：" + e.getMessage(); }
            String error = failure;
            runOnUiThread(() -> {
                busy = false; ui.setBusy(false);
                if (isDestroyed() || request != modeRequestGeneration) return;
                ui.setDialog(null);
                if (error == null) finishPrivilegeMode("root");
                else { pendingAppControl = false; pendingShell = false; pendingScreenshot = false; ui.message("root 授权失败", error); }
            });
        });
    }
    public void changeScreenshots(boolean checked) {
        if (!checked) {
            pendingScreenshot = false;
            SharedPreferences prefs = Config.prefs(this);
            prefs.edit().putBoolean("screenshots", false).putLong("screenshot_revision", prefs.getLong("screenshot_revision", 0) + 1).apply();
            refreshState(); policy(); return;
        }
        String mode = AppControl.mode(this);
        if (!"root".equals(mode) && !"shizuku".equals(mode)) {
            ui.message("截图权限", "请先在特权工作模式中选择 root 或 Shizuku / Sui 并授权\n无和 Dhizuku 模式暂不支持屏幕截图");
            return;
        }
        requestPrivilegeMode(mode, false, false, true);
    }
    private void confirmScreenshot() {
        String mode = AppControl.mode(this);
        ui.confirm("允许对方屏幕截图", "开启后，对方可以请求此手机当前屏幕的截图\n截图前后检查前台应用，命中截图黑名单或无法确认时拒绝\n截图经服务器传给对方，服务器查询结果中的图片最多保留 10 分钟\n息屏或锁屏时不截图", "开启", () -> {
            if (!mode.equals(AppControl.mode(this)) || !ScreenshotQueries.ready(this, mode)) { changeScreenshots(true); return; }
            SharedPreferences prefs = Config.prefs(this);
            prefs.edit().putBoolean("screenshots", true).putLong("screenshot_revision", prefs.getLong("screenshot_revision", 0) + 1).apply();
            refreshState(); policy();
        });
    }
    public void queryScreenshot() { send("get_screenshot", new JSONObject()); }
    public void changeAppControl(boolean checked) {
        if (!checked) {
            pendingAppControl = false;
            Config.prefs(this).edit().putBoolean("app_control", false).apply(); refreshState(); policy(); return;
        }
        String mode = AppControl.mode(this);
        if ("none".equals(mode)) { toast("请先选择特权工作模式"); return; }
        requestPrivilegeMode(mode, true);
    }
    private void confirmAppControl() {
        ui.confirm("允许暂停应用", "对方可以暂停和恢复指定应用\n本机选择的执行方式为：" + AppControl.label(AppControl.mode(this))
            + "\n恢复本机按钮可在关闭共享后使用\n关闭授权不会自动恢复已暂停应用", "开启", () -> {
            if (!AppControl.authorized(this, AppControl.mode(this), false)) { changeAppControl(true); return; }
            Config.prefs(this).edit().putBoolean("app_control", true).apply(); refreshState(); policy();
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
    public void openPauseBlacklist() { openLocalBlacklist(false); }
    public void openScreenshotBlacklist() { openLocalBlacklist(true); }
    private void openLocalBlacklist(boolean screenshot) {
        if (busy) { toast("请等待当前请求完成"); return; }
        AppPickerPage picker = ui.getAppPicker();
        Set<String> chosen = picker != null && picker.getBlacklist() && picker.getScreenshotBlacklist() == screenshot
            ? new HashSet<>(ui.getSelectedPeerApps()) : screenshot ? Config.screenshotBlacklist(this) : Config.pauseBlacklist(this);
        busy = true; ui.setBusy(true); show("正在加载本机应用……");
        network.execute(() -> {
            try {
                JSONArray apps = new JSONArray();
                Set<String> installed = new HashSet<>();
                for (ApplicationInfo app : getPackageManager().getInstalledApplications(0)) {
                    if (!screenshot && (app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) continue;
                    String name = app.packageName;
                    try { name = getPackageManager().getApplicationLabel(app).toString(); } catch (RuntimeException ignored) {}
                    apps.put(new JSONObject().put("package_name", app.packageName).put("app_name", name).put("installed", true));
                    installed.add(app.packageName);
                }
                for (String pkg : chosen) {
                    if (installed.contains(pkg) || (!screenshot && getPackageName().equals(pkg))) continue;
                    apps.put(new JSONObject().put("package_name", pkg).put("app_name", pkg).put("installed", false));
                }
                runOnUiThread(() -> { if (!isDestroyed()) ui.showLocalBlacklist(apps, chosen, Config.own(this), getPackageName(), screenshot); });
            } catch (Exception e) { show("加载本机应用失败：" + e.getMessage()); }
            finally { busy = false; runOnUiThread(() -> ui.setBusy(false)); }
        });
    }
    public void savePauseBlacklist() { saveLocalBlacklist(false); }
    public void saveScreenshotBlacklist() { saveLocalBlacklist(true); }
    private void saveLocalBlacklist(boolean screenshot) {
        if (busy) { toast("请等待当前请求完成"); return; }
        AppPickerPage picker = ui.getAppPicker();
        if (picker == null || !picker.getBlacklist() || picker.getScreenshotBlacklist() != screenshot) return;
        Set<String> selected = new HashSet<>();
        JSONArray apps = ui.selectedAppRequests();
        for (int i = 0; i < apps.length(); i++) {
            String pkg = apps.optJSONObject(i).optString("package_name");
            if ((screenshot || !getPackageName().equals(pkg)) && pkg.length() <= 255 && AppControl.PACKAGE.matcher(pkg).matches()) selected.add(pkg);
        }
        if (screenshot && selected.size() > 2000) { ui.message("截图黑名单", "最多选择 2000 个应用"); return; }
        SharedPreferences prefs = Config.prefs(this);
        SharedPreferences.Editor editor = prefs.edit().putStringSet(screenshot ? "screenshot_blacklist" : "pause_blacklist", selected);
        if (screenshot) editor.putLong("screenshot_revision", prefs.getLong("screenshot_revision", 0) + 1);
        editor.apply();
        ui.clearAppPicker(); refreshState(); policy();
        ui.message(screenshot ? "截图黑名单" : "应用暂停黑名单", "已保存，共 " + selected.size() + " 个应用\n" +
            (screenshot ? "当前前台应用在黑名单时，对方的截图请求会被拒绝" : "对方不能通过应用暂停功能暂停这些应用，仍可恢复"));
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
                            stopped = "上一项状态未确认，后续应用未执行\n请检查后再操作";
                            item.put("status", "uncertain").put("error", "请求超时或仍在执行，指令 ID：" + response.optString("command_id") + "\n请勿直接重复提交");
                        }
                    } catch (Exception e) {
                        stopped = "请求中断，后续应用未执行\n请检查对方状态后再操作";
                        item.put("status", "uncertain").put("error", "请求失败：" + e.getMessage() + "\n该应用可能已经执行，请勿直接重复提交");
                    }
                    results.put(item);
                }
                runOnUiThread(() -> { if (!isDestroyed()) ui.showPeerAppResults(results, picker.getDevice(), suspended); });
            } catch (Exception e) { show("批量操作失败：" + e.getMessage() + "\n部分应用可能已经执行，请检查后再操作"); }
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
        if (!ensureNotificationPermission("send_notifications", NotificationSender.CHANNEL_ID)) return;
        ui.confirm("允许发送普通通知", "开启后，对方可以在你的手机上发送普通系统通知\n通知仍受系统通知权限和通知开关控制", "开启", () -> {
            if (!ensureNotificationPermission("send_notifications", NotificationSender.CHANNEL_ID)) return;
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
        if (!ensureNotificationAccess("notifications")) return;
        ui.confirm("开启通知共享", "开启后，对方可以读取通知中心当前的应用名、标题和正文\n需要你在系统设置中单独授予通知访问权限", "开启", () -> {
            if (!ensureNotificationAccess("notifications")) return;
            Config.prefs(this).edit().putBoolean("notifications", true).apply();
            refreshState();
            NotificationReaderService.ensureConnected(this);
        });
    }
    private boolean ensureUsagePermission() {
        if (DeviceQueries.usageAllowed(this)) return true;
        return guidePermission("usage_sharing", "需要授予 PairLink 应用使用情况访问权限，授权后返回继续开启",
            new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
    }
    public void changeUsageSharing(boolean checked) {
        if (!checked) {
            Config.prefs(this).edit().putBoolean("usage_sharing", false).apply();
            if ("usage_sharing".equals(pendingPermission)) {
                pendingPermission = null; permissionSettingsOpened = false;
            }
            refreshState(); return;
        }
        if (!ensureUsagePermission()) return;
        ui.confirm("允许查看应用使用情况", "开启后，对方可以查询此手机过去 24 小时的应用使用记录和使用时长", "开启", () -> {
            if (!ensureUsagePermission()) return;
            Config.prefs(this).edit().putBoolean("usage_sharing", true).apply();
            refreshState();
        });
    }
    public void usagePermission() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            permissionToast("需要授予 PairLink 应用使用情况访问权限");
        } catch (ActivityNotFoundException e) {
            ui.message("无法打开权限设置", "请在系统设置中手动授予应用使用情况访问权限");
        }
    }
    public void testConnection() { checkServerHealth(true); }
    public void refreshServerVersion() { checkServerHealth(false); }
    private void checkServerHealth(boolean testConnection) {
        if (busy) { toast("请等待当前请求完成"); return; }
        String base = Config.server(this);
        busy = true;
        ui.setBusy(true);
        ui.setServerVersionLoading(true);
        if (testConnection) show("正在测试服务器连接……");
        network.execute(() -> {
            try {
                JSONObject response = Net.request(this, base, "/health", null);
                Object rawVersion = response.opt("version");
                String version = rawVersion instanceof String ? ((String) rawVersion).trim() : "";
                String displayedVersion = version.isEmpty() ? "未提供版本号" : version;
                String resultText = testConnection ? "服务器连接成功\n" + response.toString(2)
                    + "\n接收指令需要开启共享并显示在线" : "";
                runOnUiThread(() -> {
                    if (isDestroyed() || !base.equals(Config.server(this))) return;
                    Config.prefs(this).edit().putString("server_version_url", base)
                        .putString("server_version", displayedVersion).apply();
                    ui.setServerVersion(displayedVersion);
                    if (testConnection) ui.showResult(resultText);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed() || !base.equals(Config.server(this))) return;
                    if (testConnection) ui.showResult("连接失败：" + e.getMessage());
                    else ui.message("服务端版本号", "刷新失败：" + e.getMessage());
                });
            } finally {
                runOnUiThread(() -> {
                    busy = false;
                    if (isDestroyed()) return;
                    ui.setServerVersionLoading(false);
                    ui.setBusy(false);
                });
            }
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
        if (!ensureNotificationAccess("media")) return;
        ui.confirm("开启媒体播放共享", "允许对方查询系统媒体面板中的应用、曲目、作者、播放状态和进度\n需要通知访问权限，不会录音", "开启", () -> {
            if (!ensureNotificationAccess("media")) return;
            Config.prefs(this).edit().putBoolean("media", true).apply();
            refreshState();
            NotificationReaderService.ensureConnected(this);
        });
    }
    public void queryMedia() { send("get_media", new JSONObject()); }
    public void queryNotifications() { send("get_notifications", new JSONObject()); }
    public void queryRecent() {
        try { send("get_recent_apps", new JSONObject().put("hours", 24)); } catch (Exception e) { show(e.getMessage()); }
    }
    public void executeShell() {
        String command = ui.getScript();
        if (command.trim().isEmpty()) { toast("请输入命令"); return; }
        ui.confirm("执行到 " + Config.peer(this), command, "执行", () -> {
            try { send("shell", new JSONObject().put("command", command).put("timeout_seconds", 20)); }
            catch (Exception e) { show(e.getMessage()); }
        });
    }
    public void showLogs() { showHistory(0, System.currentTimeMillis(), false, -1); }
    public void refreshRecords() {
        ResultPage page = ui.getResultPage();
        if (page != null && page.getHistoryIncoming())
            showHistory(page.getHistoryFrom(), page.getHistoryTo(), true, page.getHistoryCount());
        else showLogs();
    }
    private void showHistory(long from, long to, boolean incomingOnly, long count) {
        if (busy) { toast("请等待当前请求完成"); return; }
        busy = true; ui.setBusy(true); show("正在读取本机记录……");
        network.execute(() -> {
            try {
                JSONArray records = CommandHistory.records(this, from, to, incomingOnly);
                String own = Config.own(this);
                runOnUiThread(() -> {
                    if (!isDestroyed()) ui.showRecords(records, own, from, to, incomingOnly, count);
                });
            } catch (Exception e) {
                runOnUiThread(() -> { if (!isDestroyed()) ui.message("本机记录", "读取记录失败：" + e.getMessage()); });
            } finally { busy = false; runOnUiThread(() -> ui.setBusy(false)); }
        });
    }
    private void openHistoryIntent(Intent intent) {
        if (isDestroyed() || intent == null || !CommandSummaryWorker.OPEN_HISTORY.equals(intent.getAction())) return;
        if (busy) { permissionHandler.postDelayed(() -> openHistoryIntent(intent), 500); return; }
        long now = System.currentTimeMillis();
        long to = Math.min(now, intent.getLongExtra(CommandSummaryWorker.TO, now));
        long from = Math.max(0, Math.min(to, intent.getLongExtra(CommandSummaryWorker.FROM, 0)));
        intent.setAction(null); // Returning from settings must not reopen this snapshot.
        ui.clearAppPicker();
        showHistory(from, to, true, Math.max(0, intent.getLongExtra(CommandSummaryWorker.COUNT, 0)));
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openHistoryIntent(intent);
    }
    public void clearLogs() {
        if (busy) { toast("请等待当前请求完成"); return; }
        ui.confirm("清除本机记录", "清除连接与执行记录，并重新开始定时通知的统计", "清除", () -> {
            try {
                synchronized (CommandSummaryWorker.class) {
                    Config.clearLogs(this);
                    CommandSummaryWorker.restartWindow(this);
                }
                showLogs();
            } catch (Exception e) { ui.message("本机记录", "清除失败：" + e.getMessage()); }
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
            boolean connectionChanged = !s.equals(Config.server(this)) || !a.equals(Config.own(this)) || !b.equals(Config.peer(this));
            Config.prefs(this).edit().putString("server", s).putString("own", a).putString("peer", b).apply();
            if (connectionChanged) CommandSummaryWorker.restartWindow(this);
            ui.clearAppPicker();
            toast("已保存");
        } catch (Exception e) { toast("填写服务器 HTTPS 根地址；双方 ID 必须不同，只含字母、数字、点、下划线或连字符"); }
    }
    private void send(String action, JSONObject params) {
        send(action, params, false);
    }
    private JSONObject requestCommand(String base, String sender, String target, String action, JSONObject params) throws Exception {
        String record = CommandHistory.beginOutgoing(this, base, sender, target, action, params);
        String id = "";
        try {
            JSONObject body = new JSONObject().put("sender_id", sender).put("target_id", target).put("action", action).put("params", params);
            JSONObject response = Net.request(this, base, "/api/commands", body);
            id = response.optString("command_id");
            if ("pending".equals(response.optString("status")) && id.isEmpty()) throw new Exception("服务器未返回指令 ID");
            CommandHistory.finish(this, record, id, response.optString("status"), response.optJSONObject("result"), response.optString("error"));
            long deadline = SystemClock.elapsedRealtime() + 50000;
            while ("pending".equals(response.optString("status")) && SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(1000);
                response = Net.request(this, base, "/api/commands/" + id, null);
            }
            String status = response.optString("status");
            CommandHistory.finish(this, record, id, "pending".equals(status) ? "uncertain" : status, response.optJSONObject("result"), response.optString("error"));
            return response;
        } catch (Exception e) {
            CommandHistory.finish(this, record, id, "uncertain", null, "请求中断，执行情况未确认：" + e.getMessage());
            throw e;
        }
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
                } else if ("get_screenshot".equals(action) && "completed".equals(response.optString("status"))) {
                    JSONObject result = response.getJSONObject("result");
                    android.graphics.Bitmap image = ScreenshotQueries.decode(result);
                    runOnUiThread(() -> {
                        if (isDestroyed()) { image.recycle(); return; }
                        if (!base.equals(Config.server(this)) || !sender.equals(Config.own(this)) || !target.equals(Config.peer(this))) {
                            image.recycle(); ui.message("屏幕截图", "连接设置已改变，请重新请求截图"); return;
                        }
                        ui.showScreenshot(result, image, target);
                    });
                } else if ("completed".equals(response.optString("status"))
                    && ("get_recent_apps".equals(action) || "get_installed_apps".equals(action)
                        || "get_notifications".equals(action) || "get_media".equals(action) || "get_status".equals(action))) {
                    JSONObject result = response.getJSONObject("result");
                    runOnUiThread(() -> { if (!isDestroyed()) ui.showQuery(action, result, target); });
                } else show(format(action, response));
            } catch (Exception e) { show("请求失败：" + e.getMessage() + ("shell".equals(action) ? "\n不要自动重发 Shell，命令可能已经执行" : "")); }
            finally { busy = false; runOnUiThread(() -> ui.setBusy(false)); }
        });
    }
    private String format(String action, JSONObject response) throws Exception {
        String state = response.optString("status");
        if ("offline".equals(state)) return "对方当前离线，请让对方打开应用并开启共享";
        if ("failed".equals(state)) return "执行失败：" + response.optString("error");
        if (!"completed".equals(state)) return "请求超时或仍在执行\n指令 ID：" + response.optString("command_id")
            + ("shell".equals(action) ? "\nShell 可能已经执行，请勿直接重复提交" : "");
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
        finishPermissionSettings();
        permissionHandler.removeCallbacks(permissionRefresh);
        permissionHandler.postDelayed(permissionRefresh, 1000);
    }
    @Override public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (pendingPermission != null) out.putString("pending_permission", pendingPermission);
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
