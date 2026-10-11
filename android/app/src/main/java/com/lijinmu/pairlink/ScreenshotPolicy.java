package com.lijinmu.pairlink;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative default-display foreground check; unknown and multi-window fail closed. */
final class ScreenshotPolicy {
    static final int MAX_JPEG = 200000;
    static final int MAX_EDGE = 2048;
    private static final String PKG = "([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+)";
    private static final Pattern RESUMED = Pattern.compile(
        "(?:topResumedActivity|mTopResumedActivity|mResumedActivity|mFocusedActivity)\\s*[:=]\\s*ActivityRecord\\{[^}\\r\\n]*?\\bu\\d+\\s+" + PKG + "/");
    private static final Pattern FOCUS = Pattern.compile("mCurrentFocus\\s*=\\s*Window\\{[^}\\r\\n]*?\\bu\\d+\\s+" + PKG + "/");
    private static final Pattern FOCUS_FIELD = Pattern.compile("(?m)^\\s*mCurrentFocus\\s*=");
    static boolean hasFocusField(String dump) {
        return dump != null && FOCUS_FIELD.matcher(dump).find();
    }
    static String foreground(String activities, String windows) throws Exception {
        if (!hasFocusField(windows))
            throw new Exception("系统未返回当前焦点窗口，无法确认前台应用，已拒绝截图");
        Set<String> resumed = new HashSet<>();
        Matcher a = RESUMED.matcher(activities);
        while (a.find()) resumed.add(a.group(1));
        Set<String> focused = new HashSet<>();
        Matcher w = FOCUS.matcher(windows);
        while (w.find()) focused.add(w.group(1));
        if (resumed.size() != 1 || focused.size() != 1 || !resumed.equals(focused))
            throw new Exception("无法确认单一前台应用，已拒绝截图\n请退出分屏、悬浮窗口或通知栏后重试");
        return resumed.iterator().next();
    }
    static void check(String pkg, Set<String> blacklist) throws Exception {
        if (pkg == null || pkg.isEmpty()) throw new Exception("无法确认前台应用，已拒绝截图");
        if (blacklist.contains(pkg)) throw new Exception("当前前台应用已加入本机截图黑名单，已拒绝截图");
    }
    static void unchanged(String before, String after, Set<String> blacklist) throws Exception {
        check(before, blacklist); check(after, blacklist);
        if (!before.equals(after)) throw new Exception("截图期间前台应用发生变化，未回传图片，请重试");
    }
}
