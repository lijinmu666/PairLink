import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { mkdtemp, rm, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { WebSocket } from 'ws';
import { createPairLink } from '../src/server.js';
import { DAY } from '../src/store.js';

async function fixture(t, options = {}) {
  const dir = await mkdtemp(join(tmpdir(), 'pairlink-test-'));
  let app = createPairLink({ databasePath: join(dir, 'commands.sqlite'), ...options });
  let address = await app.listen(0, '127.0.0.1');
  const sockets = [];
  t.after(async () => { for (const ws of sockets) ws.terminate(); await app.close(); await rm(dir, { recursive: true, force: true }); });
  return {
    async request(path, body, raw = false) {
      const r = await fetch(`http://127.0.0.1:${address.port}${path}`, body === undefined ? {} : {
        method: 'POST', headers: { 'content-type': 'application/json' }, body: raw ? body : JSON.stringify(body),
      });
      return { status: r.status, body: await r.json() };
    },
    async phone(id = 'bob', options = {}) {
      const ws = new WebSocket(`ws://127.0.0.1:${address.port}/api/device/ws?device_id=${id}`, options);
      sockets.push(ws);
      await once(ws, 'open');
      return ws;
    },
    async restart() {
      await app.close();
      app = createPairLink({ databasePath: join(dir, 'commands.sqlite'), ...options });
      address = await app.listen(0, '127.0.0.1');
    },
  };
}
const input = (action = 'get_status', params = {}) => ({ sender_id: 'alice', target_id: 'bob', action, params });
const received = ws => once(ws, 'message').then(([data]) => JSON.parse(data.toString()));

test('Railway and Worker use identical protocol validation', async () => {
  assert.equal(await readFile(new URL('../src/protocol.js', import.meta.url), 'utf8'),
    await readFile(new URL('../../worker/src/protocol.js', import.meta.url), 'utf8'));
});

test('Android heartbeat and every supported action round-trip via HTTP results', async t => {
  const f = await fixture(t);
  assert.equal((await f.request('/health')).body.ok, true);
  const ws = await f.phone();
  const pong = once(ws, 'message'); ws.send('ping');
  assert.equal((await pong)[0].toString(), 'pong');
  for (const [action, params] of [
    ['get_status', {}], ['get_recent_apps', { hours: 24 }], ['get_installed_apps', {}],
    ['get_notifications', {}], ['get_media', {}], ['send_notification', { title: '你好', content: '测试通知' }],
    ['set_app_suspended', { package_name: 'com.example.app', suspended: true }],
    ['shell', { command: 'whoami', root: true, timeout_seconds: 20 }],
  ]) {
    const message = received(ws);
    const submitted = await f.request('/api/commands', input(action, params));
    assert.equal(submitted.status, 202); assert.equal(submitted.body.status, 'pending');
    const command = await message;
    assert.equal(command.type, 'command'); assert.equal(command.action, action);
    assert.equal(command.command_id, submitted.body.command_id);
    assert.equal((await f.request('/api/device/result', { device_id: 'bob', command_id: command.command_id, ok: true, result: { action, text: '成功' } })).status, 200);
    const result = await f.request('/api/commands/' + command.command_id);
    assert.equal(result.body.status, 'completed'); assert.equal(result.body.result.action, action);
  }
});

test('rejects busy target, mismatched device and duplicate result; accepts WebSocket failure result', async t => {
  const f = await fixture(t); const ws = await f.phone(); const msg = received(ws);
  const submitted = await f.request('/api/commands', input('shell', { command: 'pwd' }));
  await msg;
  assert.equal((await f.request('/api/commands', input())).status, 409);
  const id = submitted.body.command_id;
  assert.equal((await f.request('/api/device/result', { device_id: 'alice', command_id: id, ok: true })).status, 409);
  const ack = received(ws);
  ws.send(JSON.stringify({ type: 'result', command_id: id, ok: false, error: '授权已关闭' }));
  assert.equal((await ack).type, 'result_ack');
  const record = (await f.request('/api/commands/' + id)).body;
  assert.equal(record.status, 'failed'); assert.equal(record.error, '授权已关闭');
  assert.equal((await f.request('/api/device/result', { device_id: 'bob', command_id: id, ok: true })).status, 409);
});

test('replacement socket stays online after old socket closes', async t => {
  const f = await fixture(t); const old = await f.phone(); const oldClosed = once(old, 'close');
  const current = await f.phone(); await oldClosed;
  const msg = received(current);
  const submitted = await f.request('/api/commands', input());
  assert.equal(submitted.body.status, 'pending'); assert.equal((await msg).command_id, submitted.body.command_id);
});

test('offline, deadline expiry, 24-hour deletion and SQLite restart persistence', async t => {
  let time = 1700000000000;
  const f = await fixture(t, { now: () => time });
  const offline = await f.request('/api/commands', input());
  assert.equal(offline.body.status, 'offline');
  const ws = await f.phone(); const msg = received(ws);
  const submitted = await f.request('/api/commands', input()); await msg;
  await f.restart();
  assert.equal((await f.request('/api/commands/' + submitted.body.command_id)).body.status, 'pending');
  time += 45001;
  assert.equal((await f.request('/api/commands/' + submitted.body.command_id)).body.status, 'timeout');
  assert.equal((await f.request('/api/device/result', { device_id: 'bob', command_id: submitted.body.command_id, ok: true })).status, 409);
  time += DAY;
  assert.equal((await f.request('/api/commands/' + offline.body.command_id)).status, 404);
  assert.equal((await f.request('/api/commands/' + submitted.body.command_id)).status, 404);
});

test('completed results survive restart without being re-executed', async t => {
  const f = await fixture(t); const ws = await f.phone(); const msg = received(ws);
  const submitted = await f.request('/api/commands', input()); await msg;
  await f.request('/api/device/result', { device_id: 'bob', command_id: submitted.body.command_id, ok: true, result: { device_model: 'ZYB ZPD1321' } });
  await f.restart();
  const result = (await f.request('/api/commands/' + submitted.body.command_id)).body;
  assert.equal(result.status, 'completed'); assert.equal(result.result.device_model, 'ZYB ZPD1321');
  assert.equal((await f.request('/api/commands', input())).body.status, 'offline');
});

test('invalid inputs, malformed JSON and oversized payloads are rejected', async t => {
  const f = await fixture(t);
  assert.equal((await f.request('/api/commands', '{', true)).status, 400);
  assert.equal((await f.request('/api/commands', input('unknown'))).status, 400);
  assert.equal((await f.request('/api/commands', input('get_recent_apps', { hours: 25 }))).status, 400);
  assert.equal((await f.request('/api/commands', input('shell', { command: 'id', root: true, shizuku: true }))).status, 400);
  assert.equal((await f.request('/api/commands', JSON.stringify({ pad: 'a'.repeat(12001) }), true)).status, 413);
  assert.equal((await f.request('/api/commands/not-a-command')).status, 400);
  assert.equal((await f.request('/api/device/ws?device_id=bob')).status, 426);
});

test('server detects an unresponsive WebSocket and marks new commands offline', async t => {
  const f = await fixture(t, { heartbeatMs: 40 });
  const ws = await f.phone('bob', { autoPong: false });
  await once(ws, 'close');
  assert.equal((await f.request('/api/commands', input())).body.status, 'offline');
});
