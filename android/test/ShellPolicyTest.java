package com.lijinmu.pairlink;

public final class ShellPolicyTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check("app".equals(ShellPolicy.backend("none")), "none must use ordinary app");
        check("app".equals(ShellPolicy.backend("dhizuku")), "Dhizuku must not elevate Shell");
        check("root".equals(ShellPolicy.backend("root")), "root must use su");
        check("shizuku".equals(ShellPolicy.backend("shizuku")), "Shizuku must use its own process");
        for (String mode : new String[]{"none", "dhizuku", "root"}) {
            check(ShellPolicy.allowed(mode, mode, true, false, -1, -1), "non-Shizuku must not require its grant");
            check(!ShellPolicy.allowed(mode, mode, false, true, 0, 0), "Shell/sharing total switch must block");
        }
        check(!ShellPolicy.allowed("dhizuku", "none", true, true, -1, -1), "switching even ordinary modes cancels old task");
        check(!ShellPolicy.allowed("root", "shizuku", true, true, 0, 0), "same UID must not bypass mode change");
        check(!ShellPolicy.allowed("none", "root", true, true, -1, -1), "downgrading cancels root task");
        check(!ShellPolicy.allowed("root", "none", true, true, -1, -1), "upgrading must not change running task identity");
        for (int uid : new int[]{0, 2000}) {
            check(ShellPolicy.allowed("shizuku", "shizuku", true, true, uid, uid), "Sui/root and ADB Shell identities supported");
            check(!ShellPolicy.allowed("shizuku", "shizuku", true, false, uid, uid), "revoked Shizuku grant blocks");
            check(!ShellPolicy.allowed("shizuku", "shizuku", true, true, uid == 0 ? 2000 : 0, uid), "changed launch identity blocks");
        }
        check(!ShellPolicy.allowed("shizuku", "shizuku", true, true, -1, -1), "unknown UID blocks");
        check(!ShellPolicy.allowed("none", null, true, true, -1, -1), "missing captured mode blocks");
        System.out.println("ShellPolicyTest passed");
    }
}
