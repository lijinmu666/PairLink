import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { WebSocket } from 'ws';
import { createPairLink } from '../src/server.js';
import { DAY } from '../src/store.js';

async function fixture(t, options = {}) {
  const dir = await mkdtemp(join(tmpdir(), 'pairlink-test-'));
  const apiKey = options.apiKey ?? 'test-api-key';
  let app = createPairLink({ databasePath: join(dir, 'commands.sqlite'), ...options, apiKey });
  let address = await app.listen(0, '127.0.0.1');
  const sockets = [];
  t.after(async () => { for (const ws of sockets) ws.terminate(); await app.close(); await rm(dir, { recursive: true, force: true }); });
  return {
    async request(path, body, raw = false, requestKey = apiKey) {
      const headers = {};
      if (requestKey !== null) headers['x-api-key'] = requestKey;
      const request = { headers };
      if (body !== undefined) {
        request.method = 'POST';
        headers['content-type'] = 'application/json';
        request.body = raw ? body : JSON.stringify(body);
      }
      const r = await fetch(`http://127.0.0.1:${address.port}${path}`, request);
      return { status: r.status, body: await r.json() };
    },
    async phone(id = 'bob', socketOptions = {}) {
      const { apiKey: socketKey = apiKey, headers = {}, ...options } = socketOptions;
      const ws = new WebSocket(`ws://127.0.0.1:${address.port}/api/device/ws?device_id=${id}`, {
        ...options,
        headers: { ...headers, ...(socketKey === null ? {} : { 'x-api-key': socketKey }) },
      });
      sockets.push(ws);
      await once(ws, 'open');
      return ws;
    },
    async rejectedPhone(id = 'bob', socketKey = null) {
      const ws = new WebSocket(`ws://127.0.0.1:${address.port}/api/device/ws?device_id=${id}`, {
        headers: socketKey === null ? {} : { 'x-api-key': socketKey },
      });
      sockets.push(ws);
      return new Promise(resolve => {
        ws.once('unexpected-response', (_request, response) => { response.resume(); resolve(response.statusCode); });
        ws.once('open', () => resolve(101));
        ws.once('error', () => {});
      });
    },
    async restart() {
      await app.close();
      app = createPairLink({ databasePath: join(dir, 'commands.sqlite'), ...options, apiKey });
      address = await app.listen(0, '127.0.0.1');
    },
  };
}
const input = (action = 'get_status', params = {}) => ({ sender_id: 'alice', target_id: 'bob', action, params });
const received = ws => once(ws, 'message').then(([data]) => JSON.parse(data.toString()));

test('API key protects HTTP and WebSocket routes while Railway health remains public', async t => {
  const f = await fixture(t);
  const unconfigured = await fixture(t, { apiKey: '' });
  assert.equal((await unconfigured.request('/api/health', undefined, false, null)).status, 503);
  assert.equal((await f.request('/health', undefined, false, null)).status, 200);
  assert.equal((await f.request('/api/health', undefined, false, null)).status, 401);
  assert.equal((await f.request('/api/health', undefined, false, 'wrong-key')).status, 401);
  assert.equal((await f.request('/api/health')).status, 200);
  assert.equal((await f.request('/api/commands', input(), false, null)).status, 401);
  assert.equal((await f.request('/api/commands', input(), false, 'wrong-key')).status, 401);
  assert.equal((await f.request('/api/device/result', { device_id: 'bob', command_id: '00000000-0000-4000-8000-000000000000', ok: true }, false, null)).status, 401);
  assert.equal(await f.rejectedPhone('bob'), 401);
  assert.equal(await f.rejectedPhone('bob', 'wrong-key'), 401);
  const ws = await f.phone();
  assert.equal(ws.readyState, WebSocket.OPEN);
});

test('Android heartbeat and every supported action round-trip via HTTP results', async t => {
  const f = await fixture(t);
  assert.equal((await f.request('/health')).body.ok, true);
  assert.equal((await f.request('/api/health')).body.ok, true);
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


function screenshotResult(size = 90000) {
  const bytes = Buffer.alloc(size, 0x61);
  bytes[0] = 0xff; bytes[1] = 0xd8; bytes[size - 2] = 0xff; bytes[size - 1] = 0xd9;
  return { mime_type: 'image/jpeg', image_base64: bytes.toString('base64'), width: 1080, height: 1920,
    source_width: 1080, source_height: 1920, foreground_package: 'com.example.app',
    backend: 'shizuku', shizuku_uid: 2000, work_mode: 'shizuku', captured_at: 1700000000000, compressed: true };
}

test('screenshot image larger than ordinary result limit round-trips and expires after 10 minutes, including restart', async t => {
  let time = 1700000000000;
  const f = await fixture(t, { now: () => time });
  const ws = await f.phone(); const msg = received(ws);
  const submitted = await f.request('/api/commands', input('get_screenshot', { root: true, blacklist: [] }));
  const command = await msg;
  assert.equal(submitted.status, 202); assert.equal(command.action, 'get_screenshot');
  assert.deepEqual(command.params, {}); // No sender control over local blacklist or capture identity.
  const payload = screenshotResult(200000);
  const body = { device_id: 'bob', command_id: command.command_id, ok: true, result: payload };
  assert.equal((await f.request('/api/device/result', { ...body, device_id: 'alice' })).status, 409);
  assert.equal((await f.request('/api/device/result', body)).status, 200);
  let record = (await f.request('/api/commands/' + command.command_id)).body;
  assert.equal(record.result.image_base64, payload.image_base64);
  assert.equal(record.result.image_expires_at, time + 600000);
  await f.restart();
  time += 599999;
  record = (await f.request('/api/commands/' + command.command_id)).body;
  assert.equal(record.result.image_base64, payload.image_base64);
  time += 1;
  record = (await f.request('/api/commands/' + command.command_id)).body;
  assert.equal(record.result.image_base64, undefined); assert.equal(record.result.image_expired, true);
  assert.equal(record.result.foreground_package, 'com.example.app');
  assert.equal(record.status, 'completed');
  await f.restart();
  assert.equal((await f.request('/api/commands/' + command.command_id)).body.result.image_base64, undefined);
  time += DAY;
  assert.equal((await f.request('/api/commands/' + command.command_id)).status, 404);
});

test('screenshot rejects invalid image, oversized JPEG, missing foreground and wrong backend; ordinary limits remain', async t => {
  const f = await fixture(t); const ws = await f.phone(); const msg = received(ws);
  const submitted = await f.request('/api/commands', input('get_screenshot')); await msg;
  const result = screenshotResult();
  const send = async value => (await f.request('/api/device/result', { device_id: 'bob', command_id: submitted.body.command_id, ok: true, result: value })).status;
  for (const value of [screenshotResult(200001), { ...result, image_base64: 'not base64' },
    { ...result, image_base64: Buffer.from('not jpeg').toString('base64') }, { ...result, foreground_package: '' },
    { ...result, backend: 'dhizuku' }, { ...result, width: 2049 }, { ...result, mime_type: 'image/png' }])
    assert.equal(await send(value), 409);
  assert.equal((await f.request('/api/commands/' + submitted.body.command_id)).body.status, 'pending');
  // Refused captures carry only an error; images are not required for failure results.
  assert.equal((await f.request('/api/device/result', { device_id: 'bob', command_id: submitted.body.command_id,
    ok: false, error: '当前前台应用已加入本机截图黑名单，已拒绝截图' })).status, 200);
  const refused = (await f.request('/api/commands/' + submitted.body.command_id)).body;
  assert.equal(refused.status, 'failed'); assert.equal(refused.result, null);
  const next = received(ws); const normal = await f.request('/api/commands', input()); await next;
  assert.equal((await f.request('/api/device/result', { device_id: 'bob', command_id: normal.body.command_id,
    ok: true, result: { text: 'a'.repeat(48001) } })).status, 409);
});
