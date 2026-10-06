package com.lijinmu.pairlink;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import androidx.activity.ComponentActivity;
import android.content.SharedPreferences;
import org.json.*;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.*;

public final class MainActivity extends ComponentActivity {
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final UiState ui = new UiState();
    private volatile boolean busy;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener = (prefs, key) -> runOnUiThread(this::refreshState);
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ui.setServer(Config.server(this)); ui.setOwn(Config.own(this)); ui.setPeer(Config.peer(this));
        refreshState();
        Config.prefs(this).registerOnSharedPreferenceChangeListener(preferenceListener);
        PairLinkUi.render(this, ui);
    }
    private void refreshState() {
        ui.setSharing(Config.sharing(this));
        ui.setShell(Config.prefs(this).getBoolean("shell", false));
        ui.setRoot(Config.prefs(this).getBoolean("root", false));
        ui.setConnection(Config.sharing(this) ? Config.prefs(this).getString("connection", "正在连接") : "共享已关闭");
    }
    public void changeSharing(boolean checked) {
        if (checked && !settingsValid()) { toast("请先保存有效的连接设置"); return; }
        if (checked && Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1); return;
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
    public void queryRecent() {
        try { send("get_recent_apps", new JSONObject().put("hours", 1)); } catch (Exception e) { show(e.getMessage()); }
    }
    public void executeShell() {
        String command = ui.getScript();
        boolean requestRoot = ui.getSendRoot();
        if (command.trim().isEmpty()) { toast("请输入命令"); return; }
        ui.confirm("执行到 " + Config.peer(this), command, "执行", () -> {
            try { send("shell", new JSONObject().put("command", command).put("root", requestRoot).put("timeout_seconds", 20)); }
            catch (Exception e) { show(e.getMessage()); }
        });
    }
    public void showLogs() { ui.message("本机记录", Config.prefs(this).getString("logs", "暂无记录")); }
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
                show(format(action, response));
                Config.log(this, "请求 " + action + "：" + response.optString("status"));
            } catch (Exception e) { show("请求失败：" + e.getMessage() + "\n不要自动重发 Shell，命令可能已经执行。"); }
            finally { busy = false; runOnUiThread(() -> ui.setBusy(false)); }
        });
    }
    private String format(String action, JSONObject response) throws Exception {
        String state = response.optString("status");
        if ("offline".equals(state)) return "对方当前离线，请让对方打开应用并开启共享。";
        if ("failed".equals(state)) return "执行失败：" + response.optString("error");
        if (!"completed".equals(state)) return "请求超时或仍在执行。指令 ID：" + response.optString("command_id") + "\nShell 可能已经执行，请勿直接重复提交。";
        JSONObject r = response.getJSONObject("result");
        if ("get_status".equals(action)) return "对方在线\n电量：" + r.optInt("battery_percent") + "%\n充电：" + (r.optBoolean("charging") ? "是" : "否")
            + "\nShell：" + (r.optBoolean("shell_enabled") ? "开启" : "关闭") + "\nroot：" + (r.optBoolean("root_enabled") ? "开启" : "关闭");
        if ("shell".equals(action)) return "退出码：" + r.optInt("exit_code") + "\n超时：" + r.optBoolean("timed_out") + "\n取消：" + r.optBoolean("cancelled")
            + "\n输出截断：" + r.optBoolean("truncated") + "\n\n标准输出\n" + r.optString("stdout") + "\n错误输出\n" + r.optString("stderr");
        JSONArray apps = r.getJSONArray("apps");
        StringBuilder text = new StringBuilder("最近使用的应用（每个应用显示最近一次打开时间）\n");
        SimpleDateFormat f = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA);
        for (int i = 0; i < apps.length(); i++) {
            JSONObject item = apps.getJSONObject(i);
            text.append("\n").append(item.getString("app_name")).append("\n").append(item.getString("package_name"))
                .append("\n").append(f.format(new Date(item.getLong("last_opened_at")))).append("\n");
        }
        if (apps.length() == 0) text.append("暂无记录");
        return text.toString();
    }
    private void show(String s) { runOnUiThread(() -> { if (!isDestroyed()) ui.showResult(s == null ? "" : s); }); }
    private void toast(String s) { ui.message("提示", s); }
    @Override public void onResume() { super.onResume(); refreshState(); }
    @Override public void onDestroy() {
        Config.prefs(this).unregisterOnSharedPreferenceChangeListener(preferenceListener);
        network.shutdown(); super.onDestroy();
    }
}
