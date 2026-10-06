package com.lijinmu.pairlink;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

final class DeviceQueries {
    static JSONObject status(Context c) throws Exception {
        Intent battery = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int state = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        String manufacturer = Build.MANUFACTURER == null || Build.MANUFACTURER.trim().isEmpty() ? Build.BRAND : Build.MANUFACTURER;
        String model = Build.MODEL == null ? "" : Build.MODEL;
        String deviceModel = ((manufacturer == null ? "" : manufacturer.trim()) + " " + model.trim()).trim();
        JSONObject foreground = currentApp(c);
        return new JSONObject().put("device_id", Config.own(c))
            .put("device_model", deviceModel).put("android_version", Build.VERSION.RELEASE == null ? "" : Build.VERSION.RELEASE)
            .put("android_sdk", Build.VERSION.SDK_INT).put("uptime_ms", SystemClock.elapsedRealtime())
            .put("current_app", foreground).put("battery_percent", scale > 0 ? Math.round(level * 100f / scale) : -1)
            .put("charging", state == BatteryManager.BATTERY_STATUS_CHARGING || state == BatteryManager.BATTERY_STATUS_FULL)
            .put("shell_enabled", Config.shell(c)).put("root_enabled", Config.root(c)).put("queried_at", System.currentTimeMillis());
    }
    static boolean usageAllowed(Context c) {
        AppOpsManager ops = (AppOpsManager)c.getSystemService(Context.APP_OPS_SERVICE);
        return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }
    static JSONObject currentApp(Context c) throws Exception {
        JSONObject unknown = new JSONObject().put("package_name", "").put("app_name", "未知");
        if (!usageAllowed(c)) return unknown.put("permission_required", true);
        UsageStatsManager manager = (UsageStatsManager)c.getSystemService(Context.USAGE_STATS_SERVICE);
        UsageEvents events = manager.queryEvents(System.currentTimeMillis() - 86400000L, System.currentTimeMillis());
        if (events == null) return unknown.put("permission_required", false);
        UsageEvents.Event event = new UsageEvents.Event();
        String pkg = ""; long latest = 0;
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND && event.getPackageName() != null && event.getTimeStamp() >= latest) {
                pkg = event.getPackageName(); latest = event.getTimeStamp();
            }
        }
        if (pkg.isEmpty()) return unknown.put("permission_required", false);
        String name = pkg;
        try { name = c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)).toString(); } catch (Exception ignored) {}
        return new JSONObject().put("package_name", pkg).put("app_name", name).put("observed_at", latest).put("permission_required", false);
    }
    static JSONObject installedApps(Context c) throws Exception {
        JSONArray apps = new JSONArray();
        List<ApplicationInfo> installed = c.getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
        installed.sort((a, b) -> a.packageName.compareToIgnoreCase(b.packageName));
        boolean truncated = false;
        for (ApplicationInfo app : installed) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            String name;
            try { name = c.getPackageManager().getApplicationLabel(app).toString(); } catch (Exception e) { name = app.packageName; }
            apps.put(new JSONObject().put("package_name", app.packageName).put("app_name", name));
            if (apps.toString().length() > 44000) { apps.remove(apps.length() - 1); truncated = true; break; }
        }
        return new JSONObject().put("apps", apps).put("count", apps.length()).put("truncated", truncated).put("queried_at", System.currentTimeMillis());
    }
    static JSONObject recent(Context c, int hours) throws Exception {
        if (!usageAllowed(c)) throw new Exception("对方尚未开启使用情况访问权限");
        long end = System.currentTimeMillis(), start = end - hours * 3600000L;
        UsageStatsManager manager = (UsageStatsManager)c.getSystemService(Context.USAGE_STATS_SERVICE);
        UsageEvents events = manager.queryEvents(start, end);
        if (events == null) throw new Exception("系统暂时无法提供应用使用记录，请解锁手机后重试");
        Map<String, Long> last = new HashMap<>();
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND && event.getPackageName() != null) last.put(event.getPackageName(), event.getTimeStamp());
        }
        List<Map.Entry<String, Long>> items = new ArrayList<>(last.entrySet());
        items.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        JSONArray apps = new JSONArray();
        for (Map.Entry<String, Long> item : items) {
            if (apps.length() >= 50) break;
            String name = item.getKey();
            try { name = c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(name, 0)).toString(); } catch (Exception ignored) {}
            apps.put(new JSONObject().put("package_name", item.getKey()).put("app_name", name).put("last_opened_at", item.getValue()));
        }
        return new JSONObject().put("hours", hours).put("apps", apps).put("queried_at", end);
    }
}