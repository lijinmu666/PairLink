package com.lijinmu.pairlink;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class MediaQueries {
    static void checkAllowed(Context c) throws Exception {
        if (!Config.sharing(c)) throw new Exception("对方已关闭共享");
        if (!Config.prefs(c).getBoolean("media", false)) throw new Exception("对方未开启媒体播放共享");
        if (!NotificationReaderService.hasAccess(c)) throw new Exception("对方尚未授予通知访问权限");
    }
    static JSONObject current(Context c) throws Exception {
        checkAllowed(c);
        MediaSessionManager manager = c.getSystemService(MediaSessionManager.class);
        if (manager == null) throw new Exception("此系统不支持读取媒体播放信息");
        List<MediaController> controllers;
        try { controllers = manager.getActiveSessions(new ComponentName(c, NotificationReaderService.class)); }
        catch (SecurityException e) { throw new Exception("无法读取媒体播放，请检查本机通知访问权限", e); }
        List<JSONObject> entries = new ArrayList<>();
        int unavailable = 0;
        for (MediaController controller : controllers) {
            try {
                MediaMetadata metadata = controller.getMetadata();
                PlaybackState playback = controller.getPlaybackState();
                String pkg = controller.getPackageName(), name = pkg;
                try { name = c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
                catch (Exception ignored) {}
                String title = text(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                if (title.isEmpty()) title = text(metadata, MediaMetadata.METADATA_KEY_TITLE);
                if (title.isEmpty() && metadata != null) {
                    MediaDescription description = metadata.getDescription();
                    title = description.getTitle() == null ? "" : description.getTitle().toString();
                }
                long duration = metadata == null ? -1 : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
                long position = playback == null ? -1 : playback.getPosition();
                boolean estimated = false;
                if (position >= 0 && playback != null && playback.getState() == PlaybackState.STATE_PLAYING) {
                    long updated = playback.getLastPositionUpdateTime(), elapsed = SystemClock.elapsedRealtime();
                    float speed = playback.getPlaybackSpeed();
                    if (updated > 0 && updated <= elapsed && Float.isFinite(speed)) {
                        double value = position + (elapsed - updated) * (double) speed;
                        position = (long) Math.max(0, Math.min(Long.MAX_VALUE, value));
                        estimated = true;
                    }
                }
                if (position >= 0 && duration > 0) position = Math.min(position, duration);
                entries.add(new JSONObject().put("package_name", clip(pkg)).put("app_name", clip(name))
                    .put("title", clip(title)).put("artist", clip(text(metadata, MediaMetadata.METADATA_KEY_ARTIST)))
                    .put("album", clip(text(metadata, MediaMetadata.METADATA_KEY_ALBUM)))
                    .put("state", state(playback)).put("playing", playback != null && playback.getState() == PlaybackState.STATE_PLAYING)
                    .put("position_ms", position < 0 ? JSONObject.NULL : position)
                    .put("duration_ms", duration <= 0 ? JSONObject.NULL : duration)
                    .put("position_estimated", estimated));
            } catch (RuntimeException e) { unavailable++; }
        }
        entries.sort(Comparator.comparing((JSONObject item) -> !item.optBoolean("playing")));
        JSONArray sessions = new JSONArray();
        JSONObject result = new JSONObject().put("sessions", sessions).put("total_count", controllers.size())
            .put("unavailable_count", unavailable).put("queried_at", System.currentTimeMillis());
        boolean truncated = false;
        for (JSONObject item : entries) {
            if (sessions.length() >= 20) { truncated = true; break; }
            sessions.put(item);
            if (result.toString().length() > 46000) {
                sessions.remove(sessions.length() - 1); truncated = true; break;
            }
        }
        checkAllowed(c);
        return result.put("returned_count", sessions.length()).put("truncated", truncated);
    }
    private static String text(MediaMetadata metadata, String key) {
        if (metadata == null) return "";
        CharSequence value = metadata.getText(key);
        return value == null ? "" : value.toString();
    }
    private static String clip(String value) {
        if (value.length() <= 512) return value;
        int end = 511;
        if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end) + "…";
    }
    private static String state(PlaybackState playback) {
        if (playback == null) return "未知";
        switch (playback.getState()) {
            case PlaybackState.STATE_PLAYING: return "正在播放";
            case PlaybackState.STATE_PAUSED: return "已暂停";
            case PlaybackState.STATE_STOPPED: return "已停止";
            case PlaybackState.STATE_BUFFERING: return "缓冲中";
            case PlaybackState.STATE_CONNECTING: return "正在连接";
            case PlaybackState.STATE_FAST_FORWARDING: return "快进中";
            case PlaybackState.STATE_REWINDING: return "快退中";
            case PlaybackState.STATE_SKIPPING_TO_NEXT: return "切换到下一首";
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS: return "切换到上一首";
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM: return "切换曲目";
            case PlaybackState.STATE_ERROR: return "播放出错";
            default: return "未播放";
        }
    }
}
