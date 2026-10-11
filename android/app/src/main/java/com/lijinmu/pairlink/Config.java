package com.lijinmu.pairlink;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

final class Config {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("pairlink", Context.MODE_PRIVATE); }
    static String own(Context c) {
        String id = prefs(c).getString("own", "");
        if (id.isEmpty()) {
            id = "phone-" + UUID.randomUUID().toString().substring(0, 8);
            prefs(c).edit().putString("own", id).apply();
        }
        return id;
    }
    static String peer(Context c) { return prefs(c).getString("peer", ""); }
    static String server(Context c) { return prefs(c).getString("server", ""); }
    static boolean sharing(Context c) { return prefs(c).getBoolean("sharing", false); }
    static boolean statusNotification(Context c) { return prefs(c).getBoolean("status_notification", true); }
    static Set<String> pauseBlacklist(Context c) {
        return new HashSet<>(prefs(c).getStringSet("pause_blacklist", Collections.emptySet()));
    }
    static boolean pauseBlocked(Context c, String pkg) { return pauseBlacklist(c).contains(pkg); }
    static String nightMode(Context c) {
        String mode = prefs(c).getString("night_mode", "system");
        return "on".equals(mode) || "off".equals(mode) ? mode : "system";
    }
    static boolean shell(Context c) { return sharing(c) && prefs(c).getBoolean("shell", false); }
    static boolean shellAllowed(Context c, String mode, int uid) {
        boolean shizuku = "shizuku".equals(mode);
        return ShellPolicy.allowed(AppControl.mode(c), mode, shell(c),
            !shizuku || ShizukuBridge.granted(), shizuku ? ShizukuBridge.uid() : -1, uid);
    }
    static boolean validId(String s) { return s.matches("[A-Za-z0-9._-]{1,64}"); }
    static String normalize(String s) {
        s = s.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length()-1);
        return s;
    }
    static synchronized void clearLogs(Context c) {
        CommandHistory.clear(c);
    }
    static synchronized void log(Context c, String text) {
        CommandHistory.event(c, text);
    }
}
