package com.lijinmu.pairlink;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Process;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AppControl {
    private static final String TRACK = "paused_backend.";
    static final Pattern PACKAGE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+");
    private static final Pattern STATE = Pattern.compile("new suspended state:\\s*(true|false)");
    static String mode(Context c) {
        String mode = Config.prefs(c).getString("app_control_mode", "none");
        return "dhizuku".equals(mode) || "shizuku".equals(mode) || "root".equals(mode) ? mode : "none";
    }
    static String label(String mode) {
        switch (mode) {
            case "none": return "无";
            case "dhizuku": return "Dhizuku";
            case "device_owner": return "旧版设备所有者（已移除）";
            case "shizuku": return "Shizuku / Sui";
            case "root": return "root";
            default: return "未知";
        }
    }
    static List<String> tracked(Context c) {
        List<String> packages = new ArrayList<>();
        for (String key : Config.prefs(c).getAll().keySet()) {
            if (key.startsWith(TRACK)) packages.add(key.substring(TRACK.length()));
        }
        Collections.sort(packages);
        return packages;
    }
    static String backend(Context c, String pkg, boolean suspended) {
        return suspended ? mode(c) : Config.prefs(c).getString(TRACK + pkg, mode(c));
    }
    static boolean authorized(Context c, String backend, boolean local) {
        if (backend == null || (!local && !backend.equals(mode(c)))) return false;
        switch (backend) {
            case "dhizuku": return DhizukuBridge.granted(c);
            case "shizuku": return ShizukuBridge.granted();
            case "root": return true;
            default: return false;
        }
    }
    static JSONObject set(Context c, String pkg, boolean suspended, boolean local, ShellRunner runner) throws Exception {
        if (pkg == null || pkg.length() > 255 || !PACKAGE.matcher(pkg).matches()) throw new Exception("应用包名无效");
        if (suspended && c.getPackageName().equals(pkg)) throw new Exception("不能暂停 PairLink 本身");
        ApplicationInfo app = c.getPackageManager().getApplicationInfo(pkg, 0);
        if (suspended && (app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0)
            throw new Exception("不能暂停系统应用");
        if (suspended && !local && Config.pauseBlocked(c, pkg))
            throw new Exception("此应用已被本机加入暂停黑名单，不能远程暂停");
        SharedPreferences prefs = Config.prefs(c);
        String backend = backend(c, pkg, suspended);
        if ("device_owner".equals(backend))
            throw new Exception("这是旧版设备所有者暂停记录，请使用旧版 PairLink 恢复后再迁移到 Dhizuku");
        BooleanSupplier allowed = () -> {
            if (suspended && !local && Config.pauseBlocked(c, pkg)) return false;
            if (!local && (!Config.sharing(c) || !prefs.getBoolean("app_control", false))) return false;
            return authorized(c, backend, local);
        };
        if (!local && (!Config.sharing(c) || !prefs.getBoolean("app_control", false))) throw new Exception("对方未开启应用暂停授权");
        if (!allowed.getAsBoolean()) throw new Exception(label(backend) + " 未就绪或未授权，请在被操作手机设置");
        if (suspended && prefs.contains(TRACK + pkg) && !backend.equals(prefs.getString(TRACK + pkg, "")))
            throw new Exception("此应用已有其他执行方式的暂停记录，请先恢复后再切换方式");
        if ("shizuku".equals(backend) && prefs.contains("paused_uid." + pkg)
            && prefs.getInt("paused_uid." + pkg, -1) != ShizukuBridge.uid())
            throw new Exception("Shizuku / Sui 的启动身份已改变，请恢复原启动身份后再恢复应用");
        boolean actual;
        if ("dhizuku".equals(backend)) {
            actual = DhizukuBridge.setSuspended(c, pkg, suspended, allowed);
        } else {
            int user = Process.myUid() / 100000;
            String command = "pm " + (suspended ? "suspend" : "unsuspend") + " --user " + user + " " + pkg;
            ShellRunner.Result result = "shizuku".equals(backend)
                ? ShizukuBridge.run(c, command, 10, allowed)
                : runner.run(command, true, 10, allowed);
            if (result.exitCode != 0 || result.timedOut || result.cancelled)
                throw new Exception("修改暂停状态失败或已取消（" + backend + "），状态可能已改变，请检查后再操作。\n标准输出："
                    + result.stdout + "\n错误输出：" + result.stderr);
            Matcher state = STATE.matcher(result.stdout);
            if (!state.find()) throw new Exception("系统未返回可确认的暂停状态，请检查应用后再操作。\n" + result.stdout + "\n" + result.stderr);
            actual = Boolean.parseBoolean(state.group(1));
        }
        if (suspended && !actual) throw new Exception("系统拒绝暂停此应用：" + pkg);
        if (suspended && actual) {
            SharedPreferences.Editor edit = prefs.edit().putString(TRACK + pkg, backend);
            if ("shizuku".equals(backend)) edit.putInt("paused_uid." + pkg, ShizukuBridge.uid());
            edit.apply();
        } else if (!suspended && !actual) prefs.edit().remove(TRACK + pkg).remove("paused_uid." + pkg).apply();
        String name = pkg;
        try { name = c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception ignored) {}
        return new JSONObject().put("package_name", pkg).put("app_name", name).put("requested_suspended", suspended)
            .put("suspended", actual).put("backend", backend)
            .put("note", !suspended && actual ? "已请求恢复，但应用仍被暂停，可能还有其他暂停来源" : "")
            .put("applied_at", System.currentTimeMillis());
    }
    static JSONObject restoreLocal(Context c) throws Exception {
        JSONArray results = new JSONArray();
        ShellRunner runner = new ShellRunner();
        for (String pkg : tracked(c)) {
            try { results.put(set(c, pkg, false, true, runner)); }
            catch (Exception e) { results.put(new JSONObject().put("package_name", pkg).put("error", e.getMessage())); }
        }
        return new JSONObject().put("results", results);
    }
}

