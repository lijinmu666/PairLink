package com.lijinmu.pairlink;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

/** Local audit records; never uploaded by the history or summary feature. */
final class CommandHistory extends SQLiteOpenHelper {
    static final long RETENTION = 24 * 60 * 60 * 1000L;
    static final int MAX_RECORDS = 1000;
    private static CommandHistory instance;
    private final Context context;

    private CommandHistory(Context c) {
        super(c, "command-history.db", null, 1);
        context = c.getApplicationContext();
    }
    private static CommandHistory helper(Context c) {
        if (instance == null) instance = new CommandHistory(c.getApplicationContext());
        return instance;
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE records (id TEXT PRIMARY KEY, at INTEGER NOT NULL, incoming INTEGER NOT NULL, payload TEXT NOT NULL)");
        db.execSQL("CREATE INDEX records_time ON records(at)");
        // Counts survive detailed-record eviction, so a busy interval is not undercounted.
        db.execSQL("CREATE TABLE arrivals (id TEXT PRIMARY KEY, at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX arrivals_time ON arrivals(at)");
        String legacy = Config.prefs(context).getString("logs", "");
        int index = 0;
        long now = System.currentTimeMillis();
        for (String line : legacy.split("\\n")) {
            if (line.trim().isEmpty() || index >= MAX_RECORDS) continue;
            try {
                JSONObject record = new JSONObject().put("kind", "legacy").put("message", line)
                    .put("at", now - index).put("id", "legacy-" + index++);
                insert(db, record, false);
            } catch (Exception e) { Log.w("PairLinkHistory", "Cannot migrate legacy record", e); }
        }
        Config.prefs(context).edit().remove("logs").apply();
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    static String incomingId(String server, String commandId) { return "incoming:" + server + ":" + commandId; }
    private static void insert(SQLiteDatabase db, JSONObject record, boolean incoming) {
        ContentValues values = new ContentValues();
        values.put("id", record.optString("id"));
        values.put("at", record.optLong("at"));
        values.put("incoming", incoming ? 1 : 0);
        values.put("payload", record.toString());
        db.insertWithOnConflict("records", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }
    private static void prune(SQLiteDatabase db, long now) {
        String[] cutoff = { Long.toString(now - RETENTION) };
        db.delete("records", "at < ?", cutoff);
        db.delete("arrivals", "at < ?", cutoff);
        db.execSQL("DELETE FROM records WHERE id NOT IN (SELECT id FROM records ORDER BY at DESC, rowid DESC LIMIT " + MAX_RECORDS + ")");
    }
    private static String limited(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit) + "\n[记录内容已截断]";
    }
    private static JSONObject parameters(JSONObject params) throws Exception {
        JSONObject copy = new JSONObject();
        if (params == null) return copy;
        for (String key : new String[]{"command", "root", "shizuku", "timeout_seconds", "package_name", "suspended", "hours", "title", "content"}) {
            Object value = params.opt(key);
            if (value instanceof String) copy.put(key, limited((String) value, "command".equals(key) ? 8192 : 4096));
            else if (value instanceof Boolean || value instanceof Number) copy.put(key, value);
        }
        return copy;
    }
    private static JSONObject beginRecord(String key, String kind, String server, String sender, String target,
                                         String commandId, String action, JSONObject params) throws Exception {
        return new JSONObject().put("id", key).put("kind", kind).put("at", System.currentTimeMillis())
            .put("server", server).put("sender_id", sender).put("target_id", target)
            .put("command_id", commandId).put("action", action).put("params", parameters(params))
            .put("status", "pending").put("started", false);
    }
    /** False means the same incoming command was already audited; do not execute it again. */
    static synchronized boolean beginIncoming(Context c, String server, JSONObject command) {
        try {
            SQLiteDatabase db = helper(c).getWritableDatabase();
            String key = incomingId(server, command.getString("command_id"));
            db.beginTransaction();
            try {
                prune(db, System.currentTimeMillis());
                JSONObject record = beginRecord(key, "incoming", server, command.optString("sender_id"), command.optString("target_id"),
                    command.getString("command_id"), command.optString("action"), command.optJSONObject("params"));
                ContentValues count = new ContentValues();
                count.put("id", key); count.put("at", record.optLong("at"));
                if (db.insertWithOnConflict("arrivals", null, count, SQLiteDatabase.CONFLICT_IGNORE) == -1) return false;
                insert(db, record, true);
                db.setTransactionSuccessful();
                return true;
            } finally { db.endTransaction(); }
        } catch (Exception e) { Log.w("PairLinkHistory", "Cannot record incoming command", e); return true; }
    }
    static synchronized String beginOutgoing(Context c, String server, String sender, String target, String action, JSONObject params) {
        String key = "outgoing:" + UUID.randomUUID();
        try {
            SQLiteDatabase db = helper(c).getWritableDatabase();
            insert(db, beginRecord(key, "outgoing", server, sender, target, "", action, params), false);
            prune(db, System.currentTimeMillis());
        } catch (Exception e) { Log.w("PairLinkHistory", "Cannot record outgoing command", e); }
        return key;
    }
    private static JSONObject read(SQLiteDatabase db, String id) throws Exception {
        try (Cursor cursor = db.query("records", new String[]{"payload"}, "id = ?", new String[]{id}, null, null, null)) {
            return cursor.moveToFirst() ? new JSONObject(cursor.getString(0)) : null;
        }
    }
    static synchronized void started(Context c, String id, String backend, int uid) {
        try {
            SQLiteDatabase db = helper(c).getWritableDatabase();
            JSONObject record = read(db, id);
            if (record == null) return;
            record.put("started", true).put("status", "running").put("started_at", System.currentTimeMillis()).put("actual_backend", backend);
            if (uid >= 0) record.put("execution_uid", uid);
            insert(db, record, "incoming".equals(record.optString("kind")));
        } catch (Exception e) { Log.w("PairLinkHistory", "Cannot record execution identity", e); }
    }
    static synchronized void finish(Context c, String id, String commandId, String status, JSONObject result, String error) {
        try {
            SQLiteDatabase db = helper(c).getWritableDatabase();
            JSONObject record = read(db, id);
            if (record == null) return;
            if (commandId != null && !commandId.isEmpty()) record.put("command_id", commandId);
            record.put("status", status).put("finished_at", System.currentTimeMillis());
            if (error != null && !error.isEmpty()) record.put("error", limited(error, 4096));
            if (result != null) {
                JSONObject brief = new JSONObject();
                for (String key : new String[]{"backend", "root", "shizuku_uid", "exit_code", "timed_out", "cancelled", "truncated", "suspended", "requested_suspended", "package_name", "app_name", "note", "sent", "title", "sent_at", "queried_at"}) {
                    Object value = result.opt(key);
                    if (value instanceof String) brief.put(key, limited((String) value, 4096));
                    else if (value instanceof Number || value instanceof Boolean) brief.put(key, value);
                }
                for (String stream : new String[]{"stdout", "stderr"}) if (result.has(stream)) {
                    String value = result.optString(stream);
                    brief.put(stream, limited(value, 4000));
                    if (value.length() > 4000) brief.put("history_truncated", true);
                }
                record.put("result", brief);
                if (result.has("backend")) record.put("actual_backend", result.optString("backend"));
                if (result.has("shizuku_uid") && result.optInt("shizuku_uid", -1) >= 0) record.put("execution_uid", result.optInt("shizuku_uid"));
                if ("completed".equals(status)) record.put("started", true);
            }
            insert(db, record, "incoming".equals(record.optString("kind")));
        } catch (Exception e) { Log.w("PairLinkHistory", "Cannot finish audit record", e); }
    }
    static synchronized void delivery(Context c, String id, boolean success, String error) {
        try {
            SQLiteDatabase db = helper(c).getWritableDatabase();
            JSONObject record = read(db, id);
            if (record == null) return;
            record.put("delivery", success ? "已回传" : "回传失败");
            if (!success) record.put("delivery_error", limited(error == null ? "未知错误" : error, 2048));
            insert(db, record, true);
        } catch (Exception e) { Log.w("PairLinkHistory", "Cannot record delivery result", e); }
    }
    static synchronized void event(Context c, String message) {
        try {
            SQLiteDatabase db = helper(c).getWritableDatabase();
            insert(db, new JSONObject().put("id", "event:" + UUID.randomUUID()).put("kind", "event")
                .put("at", System.currentTimeMillis()).put("message", limited(message, 4096)), false);
            prune(db, System.currentTimeMillis());
        } catch (Exception e) { Log.w("PairLinkHistory", "Cannot record connection event", e); }
    }
    static synchronized JSONArray records(Context c, long from, long to, boolean incomingOnly) {
        JSONArray records = new JSONArray();
        SQLiteDatabase db = helper(c).getWritableDatabase();
        prune(db, System.currentTimeMillis());
        String where = "at > ? AND at <= ?" + (incomingOnly ? " AND incoming = 1" : "");
        try (Cursor cursor = db.query("records", new String[]{"payload"}, where,
            new String[]{Long.toString(from), Long.toString(to)}, null, null, "at DESC, rowid DESC", Integer.toString(MAX_RECORDS))) {
            while (cursor.moveToNext()) {
                try { records.put(new JSONObject(cursor.getString(0))); }
                catch (Exception e) { Log.w("PairLinkHistory", "Invalid audit record", e); }
            }
        }
        return records;
    }
    static synchronized long countIncoming(Context c, long from, long to) {
        SQLiteDatabase db = helper(c).getWritableDatabase();
        prune(db, System.currentTimeMillis());
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM arrivals WHERE at > ? AND at <= ?",
            new String[]{Long.toString(from), Long.toString(to)})) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }
    static synchronized void clear(Context c) {
        SQLiteDatabase db = helper(c).getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("records", null, null); db.delete("arrivals", null, null);
            Config.prefs(c).edit().remove("logs").apply();
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
