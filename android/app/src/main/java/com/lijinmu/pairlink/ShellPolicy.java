package com.lijinmu.pairlink;

/** Maps local work mode to Shell identity, independently of sender flags. */
final class ShellPolicy {
    static String backend(String mode) {
        if ("root".equals(mode)) return "root";
        if ("shizuku".equals(mode)) return "shizuku";
        return "app"; // none and Dhizuku use the ordinary app process.
    }
    static boolean allowed(String selectedMode, String capturedMode, boolean enabled,
                           boolean shizukuGranted, int currentUid, int capturedUid) {
        if (!enabled || capturedMode == null || !capturedMode.equals(selectedMode)) return false;
        return !"shizuku".equals(capturedMode)
            || (shizukuGranted && capturedUid >= 0 && currentUid == capturedUid);
    }
}
