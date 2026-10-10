package com.lijinmu.pairlink;

import java.util.Locale;

/** Clock and retention handling shared by scheduling, notifications and tests. */
final class SummaryWindow {
    static final int[] INTERVALS = {15, 30, 60, 120, 360, 1440};
    static boolean validInterval(int minutes) {
        for (int value : INTERVALS) if (value == minutes) return true;
        return false;
    }
    static int interval(int minutes) { return validInterval(minutes) ? minutes : 60; }
    static String label(int minutes) {
        return minutes < 60 ? minutes + " 分钟" : minutes / 60 + " 小时";
    }
    static long from(long previous, long now) {
        long oldest = Math.max(0, now - 24 * 60 * 60 * 1000L);
        return Math.max(oldest, Math.min(previous, now));
    }
    static boolean due(long previous, long now, int minutes) {
        return previous > 0 && now >= previous && now - previous >= interval(minutes) * 60000L;
    }
    static String elapsedLabel(long from, long to) {
        long minutes = Math.max(1, (to - from) / 60000);
        if (minutes % 60 == 0) return minutes / 60 + " 小时";
        if (minutes < 60) return minutes + " 分钟";
        return String.format(Locale.CHINA, "%d 小时 %d 分钟", minutes / 60, minutes % 60);
    }
}
