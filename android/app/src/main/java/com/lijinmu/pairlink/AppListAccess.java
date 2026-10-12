package com.lijinmu.pairlink;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;

/** Only request the vendor permission when its system provider is known. */
final class AppListAccess {
    static final String PERMISSION = "com.android.permission.GET_INSTALLED_APPS";
    static final int REQUEST = 701;
    static PermissionInfo permission(Context c) {
        try {
            PermissionInfo info = c.getPackageManager().getPermissionInfo(PERMISSION, 0);
            return "com.lbe.security.miui".equals(info.packageName) ? info : null;
        } catch (PackageManager.NameNotFoundException e) { return null; }
    }
    static boolean needed(Context c) {
        return permission(c) != null && c.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED;
    }
    static boolean runtime(Context c) {
        PermissionInfo info = permission(c);
        return info != null && (info.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE) == PermissionInfo.PROTECTION_DANGEROUS;
    }
    static void check(Context c) throws Exception {
        if (needed(c)) throw new Exception("本机未授予应用列表访问权限，请在本机 PairLink 设置中申请授权后重试");
    }
}
