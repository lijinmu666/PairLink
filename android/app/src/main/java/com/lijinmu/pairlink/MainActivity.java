package com.lijinmu.pairlink;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.*;
import org.json.*;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout panel;
    private EditText server, own, peer, script;
    private Switch sharing, shell, root, sendRoot;
    private TextView status, result;
    private Button save;
    private volatile boolean busy;
    private boolean syncing;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(20), dp(24), dp(20), dp(24));
        scroll.addView(panel); setContentView(scroll);
        label("PairLink", 30); label("你的手机，与对方保持连接", 14);
        status = label("共享未开启", 14);
        label("连接设置", 21);
        server = field("服务器地址，例如 https://pairlink.xxx.workers.dev", false);
        server.setText(Config.server(this));
        own = field("自己的设备 ID", false); own.setText(Config.own(this));
        peer = field("对方的设备 ID", false); peer.setText(Config.peer(this));
        save = button("保存连接设置", this::saveSettings);
        sharing = toggle("允许对方查询此手机", Config.sharing(this));
        shell = toggle("允许对方执行 Shell", Config.prefs(this).getBoolean("shell", false));
        root = toggle("允许对方使用 root", Config.prefs(this).getBoolean("root", false));
        sharing.setOnCheckedChangeListener((v, checked) -> {
            if (syncing) return;
            if (checked && !settingsValid()) { setChecked(sharing, false); toast("请先保存有效的连接设置"); return; }
            if (checked && Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                setChecked(sharing, false); requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1); return;
            }
            Config.prefs(this).edit().putBoolean("sharing", checked).apply();
            if (checked) startForegroundService(new Intent(this, LinkService.class));
            else stopService(new Intent(this, LinkService.class));
        });
        shell.setOnCheckedChangeListener((v, checked) -> {
            if (syncing) return;
            if (checked) new AlertDialog.Builder(this).setTitle("开启远程 Shell")
                .setMessage("开启后，对方可以让此 APK 执行命令。第一版服务器没有身份验证，设备 ID 不代表身份。仅在你愿意允许操作时开启。")
                .setNegativeButton("取消", (d, w) -> setChecked(shell, false))
                .setPositiveButton("开启", (d, w) -> { Config.prefs(this).edit().putBoolean("shell", true).apply(); policy(); })
                .setOnCancelListener(d -> setChecked(shell, false)).show();
            else { Config.prefs(this).edit().putBoolean("shell", false).putBoolean("root", false).apply(); setChecked(root, false); policy(); }
        });
        root.setOnCheckedChangeListener((v, checked) -> {
            if (syncing) return;
            if (!checked) { Config.prefs(this).edit().putBoolean("root", false).apply(); policy(); return; }
            setChecked(root, false);
            if (!Config.prefs(this).getBoolean("shell", false)) { toast("请先开启远程 Shell"); return; }
            new AlertDialog.Builder(this).setTitle("授权 root 模式")
                .setMessage("将请求本机 root 管理器授权。成功后，对方可以以 root 权限执行命令。")
                .setNegativeButton("取消", null).setPositiveButton("请求授权", (d, w) -> authorizeRoot()).show();
        });
        button("开启应用使用情况访问权限", () -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        button("测试服务器连接", () -> network.execute(() -> {
            try { JSONObject r = Net.request(this, Config.server(this), "/health", null); show("服务器连接成功\n" + r.toString(2)); }
            catch (Exception e) { show("连接失败：" + e.getMessage()); }
        }));
        label("查询对方", 21);
        button("查看在线状态与电量", () -> send("get_status", new JSONObject()));
        button("查看最近 1 小时使用的应用", () -> { try { send("get_recent_apps", new JSONObject().put("hours", 1)); } catch (Exception ignored) {} });
        label("远程 Shell", 21);
        script = field("输入命令，例如 id 或 pwd", true);
        sendRoot = toggle("本次请求使用对方的 root 权限", false);
        button("执行命令", () -> {
            String command = script.getText().toString();
            if (command.trim().isEmpty()) { toast("请输入命令"); return; }
            new AlertDialog.Builder(this).setTitle("执行到 " + Config.peer(this)).setMessage(command)
                .setNegativeButton("取消", null).setPositiveButton("执行", (d, w) -> {
                    try { send("shell", new JSONObject().put("command", command).put("root", sendRoot.isChecked()).put("timeout_seconds", 20)); }
                    catch (Exception e) { show(e.getMessage()); }
                }).show();
        });
        result = label("等待操作", 15); result.setTextIsSelectable(true); result.setTypeface(Typeface.MONOSPACE);
        button("查看本机连接与执行记录", () -> new AlertDialog.Builder(this).setTitle("本机记录")
            .setMessage(Config.prefs(this).getString("logs", "暂无记录")).setPositiveButton("关闭", null).show());
        label("双方都需要主动安装、配置并开启共享。第一版为单次命令执行，暂不提供交互式终端或 MCP。", 13);
    }
    private void authorizeRoot() {
        network.execute(() -> {
            try {
                ShellRunner.Result r = new ShellRunner().run("id", true, 20, () -> true);
                boolean ok = !r.timedOut && r.exitCode == 0 && r.stdout.contains("uid=0");
                runOnUiThread(() -> {
                    if (ok && Config.prefs(this).getBoolean("shell", false)) {
                        Config.prefs(this).edit().putBoolean("root", true).apply(); setChecked(root, true); toast("root 授权成功");
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
    private void saveSettings() {
        if (Config.sharing(this)) { toast("请先暂停共享再修改连接设置"); return; }
        String s = Config.normalize(server.getText().toString()), a = own.getText().toString().trim(), b = peer.getText().toString().trim();
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
        busy = true; show("正在请求对方……");
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
            finally { busy = false; }
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
    private void show(String s) { runOnUiThread(() -> { if (!isDestroyed()) result.setText(s); }); }
    private void setChecked(Switch v, boolean value) { syncing = true; v.setChecked(value); syncing = false; }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size) { TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(Color.parseColor("#DFEAEF")); v.setPadding(0, dp(8), 0, dp(8)); panel.addView(v); return v; }
    private EditText field(String hint, boolean multi) { EditText v = new EditText(this); v.setHint(hint); v.setTextColor(Color.WHITE); v.setHintTextColor(Color.GRAY); v.setSingleLine(!multi); if (multi) { v.setMinLines(3); v.setGravity(48); } panel.addView(v); return v; }
    private Switch toggle(String title, boolean checked) { Switch v = new Switch(this); v.setText(title); v.setTextColor(Color.WHITE); v.setPadding(0, dp(10), 0, dp(10)); v.setChecked(checked); panel.addView(v); return v; }
    private Button button(String title, Runnable action) { Button v = new Button(this); v.setText(title); v.setOnClickListener(w -> action.run()); panel.addView(v); return v; }
    private final Runnable refresh = new Runnable() { public void run() {
        status.setText(Config.sharing(MainActivity.this) ? Config.prefs(MainActivity.this).getString("connection", "正在连接") : "共享已关闭");
        setChecked(sharing, Config.sharing(MainActivity.this)); handler.postDelayed(this, 1500);
    }};
    @Override public void onResume() { super.onResume(); handler.post(refresh); }
    @Override public void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); network.shutdown(); super.onDestroy(); }
}
