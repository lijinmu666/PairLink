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
import android.os.PowerManager;
import android.hardware.display.DisplayManager;
import android.view.Display;
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
        Boolean screenOff = screenOff(c);
        JSONObject foreground = Boolean.FALSE.equals(screenOff) ? currentApp(c) : skippedCurrentApp(screenOff);
        Boolean latestScreenOff = screenOff(c);
        if (!Boolean.FALSE.equals(latestScreenOff)) foreground = skippedCurrentApp(latestScreenOff);
        screenOff = latestScreenOff;
        return new JSONObject().put("device_id", Config.own(c))
            .put("device_model", deviceModel).put("android_version", Build.VERSION.RELEASE == null ? "" : Build.VERSION.RELEASE)
            .put("android_sdk", Build.VERSION.SDK_INT).put("uptime_ms", SystemClock.elapsedRealtime())
            .put("screen_off", screenOff == null ? JSONObject.NULL : screenOff)
            .put("current_app", foreground).put("battery_percent", scale > 0 ? Math.round(level * 100f / scale) : -1)
            .put("charging", state == BatteryManager.BATTERY_STATUS_CHARGING || state == BatteryManager.BATTERY_STATUS_FULL)
            .put("shell_enabled", Config.shell(c)).put("work_mode", AppControl.mode(c))
            .put("shell_backend", ShellPolicy.backend(AppControl.mode(c)))
            .put("shizuku_uid", "shizuku".equals(AppControl.mode(c)) ? ShizukuBridge.uid() : -1)
            .put("root_enabled", Config.shell(c) && "root".equals(AppControl.mode(c))).put("queried_at", System.currentTimeMillis());
    }
    static Boolean screenOff(Context c) {
        Boolean interactive = null;
        try {
            PowerManager power = c.getSystemService(PowerManager.class);
            if (power != null) interactive = power.isInteractive();
        } catch (RuntimeException ignored) {}
        if (Boolean.FALSE.equals(interactive)) return true;
        try {
            DisplayManager manager = c.getSystemService(DisplayManager.class);
            Display display = manager == null ? null : manager.getDisplay(Display.DEFAULT_DISPLAY);
            if (display != null) {
                switch (display.getState()) {
                    case Display.STATE_OFF:
                    case Display.STATE_DOZE:
                    case Display.STATE_DOZE_SUSPEND:
                    case Display.STATE_ON_SUSPEND: return true;
                    case Display.STATE_ON:
                    case Display.STATE_VR: return false;
                    default: break;
                }
            }
        } catch (RuntimeException ignored) {}
        return interactive == null ? null : !interactive;
    }
    private static JSONObject skippedCurrentApp(Boolean screenOff) throws Exception {
        return new JSONObject().put("package_name", "").put("app_name", "未知").put("skipped", true)
            .put("skip_reason", Boolean.TRUE.equals(screenOff) ? "息屏中，未查询前台应用" : "屏幕状态未知，未查询前台应用");
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
        AppListAccess.check(c);
        JSONArray apps = new JSONArray();
        Set<String> tracked = new HashSet<>(AppControl.tracked(c));
        List<ApplicationInfo> installed = c.getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
        installed.sort(Comparator.comparing((ApplicationInfo app) -> !pausedByPairLink(app, tracked))
            .thenComparing(app -> app.packageName, String.CASE_INSENSITIVE_ORDER));
        boolean truncated = false;
        for (ApplicationInfo app : installed) {
            if ((app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) continue;
            String name;
            try { name = c.getPackageManager().getApplicationLabel(app).toString(); } catch (Exception e) { name = app.packageName; }
            apps.put(new JSONObject().put("package_name", app.packageName).put("app_name", name)
                .put("paused_by_pairlink", pausedByPairLink(app, tracked)));
            if (apps.toString().length() > 44000) { apps.remove(apps.length() - 1); truncated = true; break; }
        }
        AppListAccess.check(c);
        return new JSONObject().put("apps", apps).put("count", apps.length()).put("truncated", truncated).put("queried_at", System.currentTimeMillis());
    }
    private static boolean pausedByPairLink(ApplicationInfo app, Set<String> tracked) {
        return tracked.contains(app.packageName) && (app.flags & ApplicationInfo.FLAG_SUSPENDED) != 0;
    }
    static JSONObject recent(Context c, int hours) throws Exception {
        if (!usageAllowed(c)) throw new Exception("对方尚未开启使用情况访问权限");
        long end = System.currentTimeMillis(), start = end - hours * 3600000L;
        UsageStatsManager manager = (UsageStatsManager)c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) throw new Exception("系统暂时无法提供应用使用记录");
        UsageEvents events = manager.queryEvents(start - 86400000L, end);
        if (events == null) throw new Exception("系统暂时无法提供应用使用记录，请解锁手机后重试");
        UsageDurationTracker tracker = new UsageDurationTracker(start, end);
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            tracker.accept(event.getEventType(), event.getPackageName(), event.getClassName(), event.getTimeStamp());
        }
        tracker.finish();
        List<Map.Entry<String, UsageDurationTracker.App>> items = new ArrayList<>(tracker.apps.entrySet());
        items.removeIf(item -> !item.getValue().seen);
        items.sort((a, b) -> {
            UsageDurationTracker.App left = a.getValue(), right = b.getValue();
            if (left.unknown != right.unknown) return left.unknown ? 1 : -1;
            int duration = Long.compare(right.duration, left.duration);
            return duration != 0 ? duration : Long.compare(right.lastOpened, left.lastOpened);
        });
        JSONArray apps = new JSONArray();
        boolean truncated = false;
        for (Map.Entry<String, UsageDurationTracker.App> item : items) {
            if (apps.length() >= 50) { truncated = true; break; }
            String pkg = item.getKey(), name = pkg;
            try { name = c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)).toString(); } catch (Exception ignored) {}
            UsageDurationTracker.App usage = item.getValue();
            apps.put(new JSONObject().put("package_name", pkg).put("app_name", name)
                .put("last_opened_at", usage.lastOpened > 0 ? usage.lastOpened : JSONObject.NULL)
                .put("usage_duration_ms", usage.unknown ? JSONObject.NULL : usage.duration)
                .put("duration_known", !usage.unknown));
            if (apps.toString().length() > 44000) {
                apps.remove(apps.length() - 1); truncated = true; break;
            }
        }
        return new JSONObject().put("hours", hours).put("apps", apps).put("truncated", truncated)
            .put("window_start", start).put("queried_at", end);
    }
}

final class UsageDurationTracker {
    static final class App {
        final Set<String> resumed = new HashSet<>();
        long duration, lastOpened;
        boolean seen, unknown;
    }
    final Map<String, App> apps = new HashMap<>();
    private final long start, end;
    private long previous;
    private boolean interactive = true;
    UsageDurationTracker(long start, long end) {
        this.start = start; this.end = end; this.previous = start;
    }
    private void advance(long timestamp) {
        long next = Math.max(previous, Math.min(end, Math.max(start, timestamp)));
        if (interactive && next > previous) {
            for (App app : apps.values()) {
                if (!app.resumed.isEmpty()) {
                    app.seen = true;
                    app.duration += next - previous;
                }
            }
        }
        previous = next;
    }
    void accept(int type, String pkg, String activity, long timestamp) {
        if (timestamp > end) return;
        advance(timestamp);
        if (type == UsageEvents.Event.SCREEN_INTERACTIVE) { interactive = true; return; }
        if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE) { interactive = false; return; }
        if (type == UsageEvents.Event.DEVICE_SHUTDOWN || type == UsageEvents.Event.DEVICE_STARTUP) {
            for (App app : apps.values()) app.resumed.clear();
            interactive = true;
            return;
        }
        if (pkg == null || pkg.isEmpty() || (type != UsageEvents.Event.MOVE_TO_FOREGROUND && type != UsageEvents.Event.MOVE_TO_BACKGROUND && type != UsageEvents.Event.ACTIVITY_STOPPED)) return;
        App app = apps.computeIfAbsent(pkg, ignored -> new App());
        String key = activity == null ? "" : activity;
        if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            app.lastOpened = timestamp;
            app.resumed.add(key);
            if (timestamp >= start) app.seen = true;
        } else {
            boolean matched = app.resumed.remove(key);
            if (type == UsageEvents.Event.MOVE_TO_BACKGROUND && !matched && timestamp >= start) {
                app.seen = true;
                app.unknown = true;
            }
        }
    }
    void finish() { advance(end); }
}

