import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { CommandStore, DAY } from '../src/store.js';

test('alarms expire pending commands, remove screenshot bytes after ten minutes and delete records after 24 hours', () => {
  const db = new DatabaseSync(':memory:');
  const sql = { exec(query, ...args) {
    const statement = db.prepare(query);
    if (!statement.columns().length) { statement.run(...args); return []; }
    return statement.all(...args);
  } };
  const store = new CommandStore(sql), now = 1700000000000;
  const record = { command_id: 'example', target_id: 'bob', created_at: now, deadline_at: now + 45000, status: 'pending' };
  store.put(record);
  assert.equal(store.nextAlarm(), now + 45000);
  assert.equal(store.busy('bob'), true);
  store.sweep(now + 45000);
  assert.equal(store.get('example').status, 'timeout'); assert.equal(store.busy('bob'), false);
  store.put({ ...record, status: 'completed', completed_at: now, result: { image_base64: 'test', image_expires_at: now + 600000, width: 100 } });
  assert.equal(store.nextAlarm(), now + 600000);
  store.sweep(now + 600000);
  assert.equal(store.get('example').result.image_base64, undefined);
  assert.equal(store.get('example').result.image_expired, true);
  assert.equal(store.get('example').result.width, 100);
  assert.equal(store.nextAlarm(), now + DAY);
  store.sweep(now + DAY);
  assert.equal(store.count(), 0); assert.equal(store.nextAlarm(), null);
  db.close();
});
