package com.lijinmu.pairlink;

import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.PowerManager;
import android.util.Base64;
import org.json.JSONObject;
import java.util.Set;
import java.util.function.BooleanSupplier;

final class ScreenshotQueries {
    static boolean ready(Context c, String mode) {
        return "root".equals(mode) || ("shizuku".equals(mode) && ShizukuBridge.granted() && ShizukuBridge.uid() >= 0);
    }
    static boolean allowed(Context c, String mode, int uid, long revision) {
        if (mode == null || !Config.sharing(c) || !Config.prefs(c).getBoolean("screenshots", false) || !mode.equals(AppControl.mode(c))
            || revision != Config.prefs(c).getLong("screenshot_revision", 0) || !ready(c, mode)) return false;
        if ("shizuku".equals(mode) && uid != ShizukuBridge.uid()) return false;
        PowerManager power = c.getSystemService(PowerManager.class);
        KeyguardManager keyguard = c.getSystemService(KeyguardManager.class);
        return power != null && power.isInteractive() && keyguard != null && !keyguard.isKeyguardLocked();
    }
    static JSONObject capture(Context c, ScreenshotEngine engine, String mode, int uid, long revision) throws Exception {
        if (!ready(c, mode)) throw new Exception("屏幕截图需要 root 或已授权的 Shizuku / Sui，请在被截图手机选择特权工作模式");
        BooleanSupplier allowed = () -> allowed(c, mode, uid, revision);
        if (!allowed.getAsBoolean()) throw new Exception("截图未授权、权限已改变，或手机处于息屏／锁屏状态");
        Set<String> blacklist = Config.screenshotBlacklist(c);
        JSONObject result = "shizuku".equals(mode)
            ? ShizukuBridge.screenshot(c, blacklist, allowed) : engine.capture(true, blacklist, allowed);
        if (!allowed.getAsBoolean()) throw new Exception("截图授权已关闭或权限已改变，未回传图片");
        ScreenshotPolicy.check(result.optString("foreground_package"), Config.screenshotBlacklist(c));
        return result.put("backend", mode).put("work_mode", mode).put("shizuku_uid", uid);
    }
    static Bitmap decode(JSONObject result) throws Exception {
        String encoded = result.optString("image_base64");
        if (result.optBoolean("image_expired")) throw new Exception("截图已过期，请重新请求截图");
        if (!"image/jpeg".equals(result.optString("mime_type")) || encoded.isEmpty() || encoded.length() > 266668)
            throw new Exception("对方未返回可用截图");
        byte[] bytes;
        try { bytes = Base64.decode(encoded, Base64.NO_WRAP); } catch (IllegalArgumentException e) { throw new Exception("截图格式无效"); }
        if (bytes.length > ScreenshotPolicy.MAX_JPEG) throw new Exception("截图超过传输上限");
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (!"image/jpeg".equals(bounds.outMimeType) || bounds.outWidth < 1 || bounds.outHeight < 1
            || bounds.outWidth > ScreenshotPolicy.MAX_EDGE || bounds.outHeight > ScreenshotPolicy.MAX_EDGE
            || bounds.outWidth != result.optInt("width") || bounds.outHeight != result.optInt("height"))
            throw new Exception("截图尺寸或格式无效");
        Bitmap image = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (image == null) throw new Exception("截图解码失败");
        return image;
    }
}
