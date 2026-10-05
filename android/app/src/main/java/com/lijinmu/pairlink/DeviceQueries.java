package com.lijinmu.pairlink;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Process;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

final class DeviceQueries {
    static JSONObject status(Context c) throws Exception {
        Intent battery = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int state = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return new JSONObject().put("device_id", Config.own(c)).put("battery_percent", scale > 0 ? Math.round(level * 100f / scale) : -1)
            .put("charging", state == BatteryManager.BATTERY_STATUS_CHARGING || state == BatteryManager.BATTERY_STATUS_FULL)
            .put("shell_enabled", Config.shell(c)).put("root_enabled", Config.root(c)).put("queried_at", System.currentTimeMillis());
    }
    static boolean usageAllowed(Context c) {
        AppOpsManager ops = (AppOpsManager)c.getSystemService(Context.APP_OPS_SERVICE);
        return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
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
            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND && event.getPackageName() != null)
                last.put(event.getPackageName(), event.getTimeStamp());
        }
        List<Map.Entry<String, Long>> items = new ArrayList<>(last.entrySet());
        items.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        JSONArray apps = new JSONArray();
        for (Map.Entry<String, Long> item : items) {
            if (apps.length() >= 50) break;
            String name = item.getKey();
            try { name = c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(name, 0)).toString(); }
            catch (Exception ignored) {}
            apps.put(new JSONObject().put("package_name", item.getKey()).put("app_name", name).put("last_opened_at", item.getValue()));
        }
        return new JSONObject().put("hours", hours).put("apps", apps).put("queried_at", end);
    }
}
