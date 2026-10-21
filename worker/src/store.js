import { SCREENSHOT_TTL } from '../../railway/src/protocol.js';

export const DAY = 86400000;

// A private PairLink deployment coordinates its two phones in one SQLite-backed hub.
export class CommandStore {
  constructor(sql) {
    this.sql = sql;
    sql.exec(`CREATE TABLE IF NOT EXISTS commands_v2 (
      id TEXT PRIMARY KEY, target_id TEXT NOT NULL, created_at INTEGER NOT NULL,
      deadline_at INTEGER NOT NULL, status TEXT NOT NULL, image_expires_at INTEGER, body TEXT NOT NULL
    )`);
    sql.exec('CREATE INDEX IF NOT EXISTS commands_v2_target_status ON commands_v2(target_id, status)');
  }
  get(id) {
    const row = [...this.sql.exec('SELECT body FROM commands_v2 WHERE id = ?', id)][0];
    return row ? JSON.parse(row.body) : null;
  }
  put(record) {
    const expiry = record.result?.image_base64
      ? (record.result.image_expires_at ?? record.completed_at + SCREENSHOT_TTL) : null;
    this.sql.exec(`INSERT INTO commands_v2 (id, target_id, created_at, deadline_at, status, image_expires_at, body)
      VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET
      status=excluded.status, image_expires_at=excluded.image_expires_at, body=excluded.body`,
    record.command_id, record.target_id, record.created_at, record.deadline_at, record.status, expiry, JSON.stringify(record));
  }
  count() { return [...this.sql.exec('SELECT COUNT(*) AS count FROM commands_v2')][0].count; }
  busy(target) {
    return [...this.sql.exec("SELECT id FROM commands_v2 WHERE target_id = ? AND status = 'pending' LIMIT 1", target)].length > 0;
  }
  sweep(now) {
    this.sql.exec('DELETE FROM commands_v2 WHERE created_at <= ?', now - DAY);
    const due = [...this.sql.exec(`SELECT body FROM commands_v2
      WHERE (status = 'pending' AND deadline_at <= ?) OR image_expires_at <= ?`, now, now)];
    for (const row of due) {
      let record = JSON.parse(row.body);
      if (record.status === 'pending' && record.deadline_at <= now)
        record = { ...record, status: 'timeout', completed_at: now };
      if (record.result?.image_base64 && (record.result.image_expires_at ?? record.completed_at + SCREENSHOT_TTL) <= now) {
        const { image_base64: _image, ...metadata } = record.result;
        record = { ...record, result: { ...metadata, image_expired: true } };
      }
      this.put(record);
    }
  }
  nextAlarm() {
    const row = [...this.sql.exec(`SELECT MIN(MIN(created_at + ?,
      CASE WHEN status = 'pending' THEN deadline_at ELSE 9007199254740991 END,
      COALESCE(image_expires_at, 9007199254740991))) AS next FROM commands_v2`, DAY)][0];
    return row.next;
  }
}
