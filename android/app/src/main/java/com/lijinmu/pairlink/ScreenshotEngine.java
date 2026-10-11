package com.lijinmu.pairlink;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Runs in root or Shizuku identity, never writes captures to shared storage. */
final class ScreenshotEngine {
    private final BinaryCommand commands = new BinaryCommand();
    void cancel() { commands.cancel(); }
    private String foreground(boolean root, BooleanSupplier allowed) throws Exception {
        String activities = new String(commands.run("dumpsys activity activities", root, 4, 1024 * 1024, allowed), "UTF-8");
        String windows = new String(commands.run("dumpsys window windows", root, 4, 1024 * 1024, allowed), "UTF-8");
        return ScreenshotPolicy.foreground(activities, windows);
    }
    JSONObject capture(boolean root, Set<String> blacklist, BooleanSupplier allowed) throws Exception {
        final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(28);
        BooleanSupplier current = () -> allowed.getAsBoolean() && System.nanoTime() < deadline;
        String before = foreground(root, current);
        ScreenshotPolicy.check(before, blacklist);
        byte[] png = commands.run("screencap -p", root, 8, 16 * 1024 * 1024, current);
        long capturedAt = System.currentTimeMillis();
        String after = foreground(root, current);
        ScreenshotPolicy.unchanged(before, after, blacklist);
        if (!current.getAsBoolean()) throw new Exception("截图已取消或授权已改变");
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(png, 0, png.length, bounds);
        if (!"image/png".equals(bounds.outMimeType) || bounds.outWidth < 1 || bounds.outHeight < 1
            || bounds.outWidth > 8192 || bounds.outHeight > 8192 || (long) bounds.outWidth * bounds.outHeight > 12000000)
            throw new Exception("系统未返回可用截图或截图尺寸过大");
        Bitmap image = BitmapFactory.decodeByteArray(png, 0, png.length);
        if (image == null) throw new Exception("截图解码失败");
        int originalWidth = image.getWidth(), originalHeight = image.getHeight();
        try {
            int edge = Math.max(originalWidth, originalHeight);
            if (edge > ScreenshotPolicy.MAX_EDGE) {
                Bitmap scaled = Bitmap.createScaledBitmap(image, Math.max(1, originalWidth * ScreenshotPolicy.MAX_EDGE / edge),
                    Math.max(1, originalHeight * ScreenshotPolicy.MAX_EDGE / edge), true);
                image.recycle(); image = scaled;
            }
            byte[] jpeg = null;
            int quality = 90;
            for (int attempt = 0; attempt < 12; attempt++) {
                if (!current.getAsBoolean()) throw new Exception("截图已取消或授权已改变");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                if (!image.compress(Bitmap.CompressFormat.JPEG, quality, bytes)) throw new Exception("截图压缩失败");
                jpeg = bytes.toByteArray();
                if (jpeg.length <= ScreenshotPolicy.MAX_JPEG) break;
                if (quality > 60) quality -= 10;
                else {
                    Bitmap scaled = Bitmap.createScaledBitmap(image, Math.max(1, image.getWidth() * 4 / 5),
                        Math.max(1, image.getHeight() * 4 / 5), true);
                    image.recycle(); image = scaled;
                }
            }
            if (jpeg == null || jpeg.length > ScreenshotPolicy.MAX_JPEG || !current.getAsBoolean())
                throw new Exception("截图过大或授权已改变，未回传图片");
            // Final fresh check after image conversion, before crossing the process boundary.
            ScreenshotPolicy.unchanged(before, foreground(root, current), blacklist);
            return new JSONObject().put("mime_type", "image/jpeg").put("image_base64", Base64.encodeToString(jpeg, Base64.NO_WRAP))
                .put("width", image.getWidth()).put("height", image.getHeight()).put("source_width", originalWidth)
                .put("source_height", originalHeight).put("foreground_package", before).put("captured_at", capturedAt)
                .put("compressed", true);
        } finally { image.recycle(); }
    }
}
