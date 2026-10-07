package com.lijinmu.pairlink;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class NotificationReaderService extends NotificationListenerService {
    private static volatile NotificationReaderService connected;

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        connected = this;
    }
    @Override public void onListenerDisconnected() {
        if (connected == this) connected = null;
        super.onListenerDisconnected();
    }
    @Override public void onDestroy() {
        if (connected == this) connected = null;
        super.onDestroy();
    }
    static boolean hasAccess(Context c) {
        ComponentName component = new ComponentName(c, NotificationReaderService.class);
        if (Build.VERSION.SDK_INT >= 27) {
            NotificationManager manager = c.getSystemService(NotificationManager.class);
            return manager != null && manager.isNotificationListenerAccessGranted(component);
        }
        String enabled = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        for (String name : enabled.split(":")) {
            if (component.equals(ComponentName.unflattenFromString(name))) return true;
        }
        return false;
    }
    static void ensureConnected(Context c) {
        if (connected != null || !hasAccess(c) || (!Config.prefs(c).getBoolean("notifications", false) && !Config.prefs(c).getBoolean("media", false))) return;
        try { requestRebind(new ComponentName(c, NotificationReaderService.class)); }
        catch (RuntimeException ignored) {}
    }
    static void checkAllowed(Context c) throws Exception {
        if (!Config.sharing(c)) throw new Exception("对方已关闭共享");
        if (!Config.prefs(c).getBoolean("notifications", false)) throw new Exception("对方未开启通知共享");
        if (!hasAccess(c)) throw new Exception("对方尚未授予通知访问权限");
    }
    static JSONObject current(Context c) throws Exception {
        checkAllowed(c);
        FutureTask<StatusBarNotification[]> read = new FutureTask<>(() -> {
            checkAllowed(c);
            NotificationReaderService listener = connected;
            if (listener == null) {
                ensureConnected(c);
                throw new Exception("对方通知服务尚未连接，请打开对方应用后稍后重试");
            }
            StatusBarNotification[] active = listener.getActiveNotifications();
            if (active == null) throw new Exception("系统暂时无法提供通知，请稍后重试");
            return active;
        });
        Handler main = new Handler(Looper.getMainLooper());
        if (Looper.myLooper() == Looper.getMainLooper()) read.run();
        else main.post(read);
        StatusBarNotification[] active;
        try { active = read.get(5, TimeUnit.SECONDS); }
        catch (TimeoutException e) { throw new Exception("读取对方通知超时，请稍后重试"); }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new Exception("读取对方通知失败", cause);
        } finally { main.removeCallbacks(read); read.cancel(false); }
        Arrays.sort(active, Comparator.comparingLong(StatusBarNotification::getPostTime).reversed());
        JSONArray notifications = new JSONArray();
        JSONObject result = new JSONObject().put("notifications", notifications).put("total_count", active.length)
            .put("returned_count", 0).put("truncated", false).put("queried_at", System.currentTimeMillis());
        boolean truncated = false;
        for (StatusBarNotification posted : active) {
            Notification n = posted.getNotification();
            Bundle extras = n.extras == null ? Bundle.EMPTY : n.extras;
            String pkg = posted.getPackageName(), name = pkg;
            try { name = c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
            catch (PackageManager.NameNotFoundException ignored) {}
            String title = string(extras.getCharSequence(Notification.EXTRA_TITLE));
            if (title.isEmpty()) title = string(extras.getCharSequence(Notification.EXTRA_TITLE_BIG));
            String body = body(extras);
            boolean contentTruncated = title.length() > 256 || body.length() > 2048 || name.length() > 256;
            JSONObject item = new JSONObject().put("package_name", pkg).put("app_name", clip(name, 256))
                .put("title", clip(title, 256)).put("text", clip(body, 2048)).put("posted_at", posted.getPostTime())
                .put("ongoing", posted.isOngoing()).put("group_summary", (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0)
                .put("content_truncated", contentTruncated);
            notifications.put(item);
            result.put("returned_count", notifications.length());
            if (result.toString().length() > 47000) {
                notifications.remove(notifications.length() - 1);
                result.put("returned_count", notifications.length());
                truncated = true;
                break;
            }
            truncated |= contentTruncated;
        }
        checkAllowed(c);
        return result.put("truncated", truncated);
    }
    private static String body(Bundle extras) {
        String expanded = string(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (!expanded.isEmpty()) return expanded;
        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null && messages.length > 0) {
            StringBuilder text = new StringBuilder();
            for (Parcelable message : messages) {
                if (!(message instanceof Bundle)) continue;
                Bundle entry = (Bundle) message;
                String line = string(entry.getCharSequence("text"));
                if (line.isEmpty()) continue;
                String sender = string(entry.getCharSequence("sender"));
                if (!sender.isEmpty()) line = sender + "：" + line;
                append(text, line);
                if (text.length() > 2048) break;
            }
            if (text.length() > 0) return text.toString();
        }
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null && lines.length > 0) {
            StringBuilder text = new StringBuilder();
            for (CharSequence line : lines) {
                append(text, string(line));
                if (text.length() > 2048) break;
            }
            if (text.length() > 0) return text.toString();
        }
        return string(extras.getCharSequence(Notification.EXTRA_TEXT));
    }
    private static void append(StringBuilder text, String line) {
        if (line.isEmpty()) return;
        if (text.length() > 0) text.append("\n");
        int remaining = 2049 - text.length();
        if (remaining > 0) text.append(line, 0, Math.min(line.length(), remaining));
    }
    private static String string(CharSequence value) { return value == null ? "" : value.toString(); }
    private static String clip(String value, int max) {
        if (value.length() <= max) return value;
        int end = max - 1;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end) + "…";
    }
}
