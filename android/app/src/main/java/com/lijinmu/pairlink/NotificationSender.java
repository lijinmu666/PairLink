package com.lijinmu.pairlink;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONObject;

final class NotificationSender {
    static JSONObject send(Context c, String title, String content) throws Exception {
        title = title == null ? "" : title.trim();
        content = content == null ? "" : content.trim();
        if (title.isEmpty() || title.length() > 128) throw new Exception("通知标题无效");
        if (content.isEmpty() || content.length() > 4096) throw new Exception("通知内容无效");
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            throw new Exception("本机尚未授予通知权限");
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        if (manager == null || !manager.areNotificationsEnabled()) throw new Exception("本机通知已关闭");
        String channelId = "pairlink_messages";
        manager.createNotificationChannel(new NotificationChannel(channelId, "PairLink 消息", NotificationManager.IMPORTANCE_DEFAULT));
        int id = (int)(System.currentTimeMillis() & 0x7fffffff);
        Notification notification = new Notification.Builder(c, channelId).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(content).setStyle(new Notification.BigTextStyle().bigText(content))
            .setAutoCancel(true).build();
        manager.notify(id, notification);
        return new JSONObject().put("sent", true).put("title", title).put("content_length", content.length()).put("sent_at", System.currentTimeMillis());
    }
}