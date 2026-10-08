package com.lijinmu.pairlink;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import com.rosan.dhizuku.api.Dhizuku;
import com.rosan.dhizuku.api.DhizukuBinderWrapper;
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener;
import org.lsposed.hiddenapibypass.HiddenApiBypass;
import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;

final class DhizukuBridge {
    static boolean available(Context c) {
        try { return Dhizuku.init(c.getApplicationContext()); }
        catch (RuntimeException e) { return false; }
    }
    static boolean granted(Context c) {
        try { return available(c) && Dhizuku.isPermissionGranted(); }
        catch (RuntimeException e) { return false; }
    }
    static void request(Context c, DhizukuRequestPermissionListener listener) throws Exception {
        if (!available(c)) throw new Exception("请先安装、激活并启动 Dhizuku");
        Dhizuku.requestPermission(listener);
    }
    private static DevicePolicyManager manager(Context c, ComponentName owner) throws Exception {
        if (Build.VERSION.SDK_INT >= 28) {
            if (!HiddenApiBypass.addHiddenApiExemptions("Landroid/app/admin/DevicePolicyManager;", "Landroid/app/admin/IDevicePolicyManager"))
                throw new Exception("此系统无法访问 Dhizuku 所需的设备策略接口");
        }
        Context ownerContext = c.createPackageContext(owner.getPackageName(), Context.CONTEXT_IGNORE_SECURITY);
        DevicePolicyManager policy = ownerContext.getSystemService(DevicePolicyManager.class);
        if (policy == null) throw new Exception("设备策略服务不可用");
        Field service = DevicePolicyManager.class.getDeclaredField("mService");
        service.setAccessible(true);
        IInterface original = (IInterface) service.get(policy);
        if (original == null) throw new Exception("设备策略 Binder 不可用");
        if (!(original.asBinder() instanceof DhizukuBinderWrapper)) {
            IBinder wrapped = Dhizuku.binderWrapper(original.asBinder());
            Object proxy = Class.forName("android.app.admin.IDevicePolicyManager$Stub")
                .getDeclaredMethod("asInterface", IBinder.class).invoke(null, wrapped);
            service.set(policy, proxy);
        }
        return policy;
    }
    static boolean setSuspended(Context c, String pkg, boolean suspended, BooleanSupplier allowed) throws Exception {
        if (!granted(c) || !allowed.getAsBoolean()) throw new Exception("Dhizuku 未授权或应用暂停授权已关闭");
        ComponentName owner = Dhizuku.getOwnerComponent();
        DevicePolicyManager policy = manager(c, owner);
        if (!allowed.getAsBoolean()) throw new Exception("应用暂停授权已关闭");
        String[] failed = policy.setPackagesSuspended(owner, new String[]{pkg}, suspended);
        if (failed == null || failed.length != 0) throw new Exception("系统拒绝修改此应用的暂停状态：" + pkg);
        return policy.isPackageSuspended(owner, pkg);
    }
}
