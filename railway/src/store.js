import { DatabaseSync } from 'node:sqlite';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

export const DAY = 86400000;

export class CommandStore {
  constructor(path) {
    if (path !== ':memory:') mkdirSync(dirname(path), { recursive: true });
    this.db = new DatabaseSync(path);
    this.db.exec('PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000; CREATE TABLE IF NOT EXISTS commands (id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, body TEXT NOT NULL)');
    this.read = this.db.prepare('SELECT body FROM commands WHERE id = ?');
    this.write = this.db.prepare('INSERT INTO commands (id, created_at, body) VALUES (?, ?, ?) ON CONFLICT(id) DO UPDATE SET body=excluded.body');
    this.list = this.db.prepare('SELECT body FROM commands');
    this.expire = this.db.prepare('DELETE FROM commands WHERE created_at <= ?');
  }
  get(id) {
    const row = this.read.get(id);
    return row ? JSON.parse(row.body) : null;
  }
  put(record) { this.write.run(record.command_id, record.created_at, JSON.stringify(record)); }
  all() { return this.list.all().map(row => JSON.parse(row.body)); }
  sweep(now) {
    this.expire.run(now - DAY);
    for (const record of this.all()) {
      if (record.status === 'pending' && record.deadline_at <= now)
        this.put({ ...record, status: 'timeout', completed_at: now });
    }
  }
  close() { this.db.close(); }
}
