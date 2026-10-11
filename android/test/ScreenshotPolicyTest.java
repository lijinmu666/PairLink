package com.lijinmu.pairlink;

import java.util.Set;

public final class ScreenshotPolicyTest {
    private static String activity(String marker, String pkg) { return marker + ": ActivityRecord{abc u0 " + pkg + "/.Main t42}"; }
    private static String window(String pkg) { return "mCurrentFocus=Window{abc u0 " + pkg + "/.Main}"; }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private interface Attempt { void run() throws Exception; }
    private static void rejects(Attempt attempt) throws Exception {
        try { attempt.run(); } catch (Exception expected) { return; }
        throw new AssertionError("unsafe screenshot should be rejected");
    }
    public static void main(String[] args) throws Exception {
        for (String marker : new String[]{"topResumedActivity", "mTopResumedActivity", "mResumedActivity", "mFocusedActivity"})
            check("com.example.app".equals(ScreenshotPolicy.foreground(activity(marker, "com.example.app"), window("com.example.app"))), "Android foreground variant");
        check("com.example.app".equals(ScreenshotPolicy.foreground(activity("topResumedActivity", "com.example.app") + "\n" + activity("mResumedActivity", "com.example.app"), window("com.example.app"))), "duplicate same activity is safe");
        rejects(() -> ScreenshotPolicy.foreground("topResumedActivity=null", window("com.example.app")));
        rejects(() -> ScreenshotPolicy.foreground(activity("mResumedActivity", "com.example.app"), "mCurrentFocus=null"));
        rejects(() -> ScreenshotPolicy.foreground(activity("mResumedActivity", "com.example.app"), "Permission Denial"));
        rejects(() -> ScreenshotPolicy.foreground(activity("mResumedActivity", "com.example.app"), window("com.android.systemui")));
        rejects(() -> ScreenshotPolicy.foreground(activity("mResumedActivity", "com.example.app") + "\n" + activity("mResumedActivity", "com.example.secret"), window("com.example.app")));
        Set<String> blacklist = Set.of("com.example.secret");
        ScreenshotPolicy.check("com.example.app", blacklist);
        rejects(() -> ScreenshotPolicy.check("com.example.secret", blacklist));
        rejects(() -> ScreenshotPolicy.check(null, blacklist));
        rejects(() -> ScreenshotPolicy.check("", blacklist));
        ScreenshotPolicy.unchanged("com.example.app", "com.example.app", blacklist);
        rejects(() -> ScreenshotPolicy.unchanged("com.example.app", "com.example.secret", blacklist));
        rejects(() -> ScreenshotPolicy.unchanged("com.example.secret", "com.example.app", blacklist));
        rejects(() -> ScreenshotPolicy.unchanged("com.example.app", "com.example.other", blacklist));
        System.out.println("ScreenshotPolicyTest passed");
    }
}
