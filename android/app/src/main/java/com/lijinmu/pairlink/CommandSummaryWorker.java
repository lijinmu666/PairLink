package com.lijinmu.pairlink;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class CommandSummaryWorker extends Worker {
    static final String CHANNEL_ID = "pairlink_command_summary";
    static final String OPEN_HISTORY = "com.lijinmu.pairlink.OPEN_HISTORY";
    static final String FROM = "history_from", TO = "history_to", COUNT = "history_count";
    private static final String WORK = "pairlink-command-summary";
    private static final int NOTIFICATION_ID = 101;
    public CommandSummaryWorker(@NonNull Context c, @NonNull WorkerParameters params) { super(c, params); }
    static boolean enabled(Context c) { return Config.prefs(c).getBoolean("command_summary", false); }
    static int interval(Context c) { return SummaryWindow.interval(Config.prefs(c).getInt("command_summary_minutes", 60)); }
    static void createChannel(Context c) {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "指令定时汇总", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setShowBadge(false);
        c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    private static void schedule(Context c, String token, ExistingPeriodicWorkPolicy policy) {
        int minutes = interval(c);
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(CommandSummaryWorker.class, minutes, TimeUnit.MINUTES)
            .setInitialDelay(minutes, TimeUnit.MINUTES)
            .setInputData(new Data.Builder().putString("token", token).build()).build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(WORK, policy, request);
    }
    static synchronized void setEnabled(Context c, boolean value) {
        SharedPreferences prefs = Config.prefs(c);
        if (value && enabled(c)) { ensureScheduled(c); return; }
        String token = UUID.randomUUID().toString();
        prefs.edit().putBoolean("command_summary", value).putString("command_summary_token", token)
            .putLong("command_summary_since", System.currentTimeMillis()).apply();
        if (value) schedule(c, token, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE);
        else {
            WorkManager.getInstance(c).cancelUniqueWork(WORK);
            c.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
        }
    }
    static synchronized void changeInterval(Context c, int minutes) {
        if (!SummaryWindow.validInterval(minutes) || minutes == interval(c)) return;
        Config.prefs(c).edit().putInt("command_summary_minutes", minutes).apply();
        if (enabled(c)) {
            String token = UUID.randomUUID().toString();
            Config.prefs(c).edit().putString("command_summary_token", token).apply();
            schedule(c, token, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE);
        }
    }
    static synchronized void ensureScheduled(Context c) {
        if (!enabled(c)) { WorkManager.getInstance(c).cancelUniqueWork(WORK); return; }
        String token = Config.prefs(c).getString("command_summary_token", "");
        if (token.isEmpty()) { restartWindow(c); return; }
        schedule(c, token, ExistingPeriodicWorkPolicy.KEEP);
    }
    static synchronized void restartWindow(Context c) {
        String token = UUID.randomUUID().toString();
        Config.prefs(c).edit().putString("command_summary_token", token)
            .putLong("command_summary_since", System.currentTimeMillis()).apply();
        c.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
        if (enabled(c)) schedule(c, token, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE);
    }
    private boolean stillEnabled(Context c, String token) {
        return !isStopped() && enabled(c) && token != null
            && token.equals(Config.prefs(c).getString("command_summary_token", ""));
    }
    @NonNull @Override public Result doWork() {
        Context c = getApplicationContext();
        synchronized (CommandSummaryWorker.class) {
            String token = getInputData().getString("token");
            if (!stillEnabled(c, token)) return Result.success();
            long now = System.currentTimeMillis();
            long previous = Config.prefs(c).getLong("command_summary_since", now);
            if (previous > now || previous <= 0) {
                Config.prefs(c).edit().putLong("command_summary_since", now).apply();
                return Result.success();
            }
            if (!SummaryWindow.due(previous, now, interval(c))) return Result.success();
            try {
                long from = SummaryWindow.from(previous, now);
                long count = CommandHistory.countIncoming(c, from, now);
                if (count == 0) {
                    if (stillEnabled(c, token))
                        Config.prefs(c).edit().putLong("command_summary_since", now).apply();
                    return Result.success();
                }
                createChannel(c);
                NotificationManager manager = c.getSystemService(NotificationManager.class);
                NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
                if (!manager.areNotificationsEnabled() || channel == null || channel.getImportance() == NotificationManager.IMPORTANCE_NONE
                    || (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED))
                    return Result.success(); // Keep the window until notifications become available.
                String text = "对方在过去 " + SummaryWindow.elapsedLabel(from, now) + "内向本设备发起了 " + count + " 次指令，点按查看详情";
                Intent intent = new Intent(c, MainActivity.class).setAction(OPEN_HISTORY)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(FROM, from).putExtra(TO, now).putExtra(COUNT, count);
                PendingIntent open = PendingIntent.getActivity(c, 101, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                Notification notification = new Notification.Builder(c, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("PairLink · 指令汇总").setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
                    .setContentIntent(open).setAutoCancel(true).setCategory(Notification.CATEGORY_STATUS).build();
                if (!stillEnabled(c, token)) return Result.success();
                manager.notify(NOTIFICATION_ID, notification);
                Config.prefs(c).edit().putLong("command_summary_since", now).apply();
                return Result.success();
            } catch (Exception e) { return Result.retry(); }
        }
    }
}
