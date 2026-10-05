package com.lijinmu.pairlink;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

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
    static boolean shell(Context c) { return sharing(c) && prefs(c).getBoolean("shell", false); }
    static boolean root(Context c) { return shell(c) && prefs(c).getBoolean("root", false); }
    static boolean validId(String s) { return s.matches("[A-Za-z0-9._-]{1,64}"); }
    static String normalize(String s) {
        s = s.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length()-1);
        return s;
    }
    static synchronized void log(Context c, String text) {
        String old = prefs(c).getString("logs", "");
        String next = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.CHINA).format(new java.util.Date()) + " " + text + "\n" + old;
        prefs(c).edit().putString("logs", next.substring(0, Math.min(12000, next.length()))).apply();
    }
}
