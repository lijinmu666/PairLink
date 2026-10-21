import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { Miniflare } from 'miniflare';

const root = resolve(fileURLToPath(new URL('../..', import.meta.url)));
const API_KEY = 'local-test-key';
const input = (action = 'get_status', params = {}, target = 'bob') => ({ sender_id: 'alice', target_id: target, action, params });

async function fixture(t, key = API_KEY) {
  const dir = await mkdtemp(join(tmpdir(), 'pairlink-worker-'));
  const options = {
    modules: true, modulesRoot: root, scriptPath: join(root, 'worker/src/index.js'),
    modulesRules: [{ type: 'ESModule', include: ['**/*.js'] }],
    compatibilityDate: '2026-08-06', compatibilityFlags: ['nodejs_compat'],
    durableObjects: { HUB: { className: 'PairHub', useSQLite: true } },
    durableObjectsPersist: dir, bindings: { PAIRLINK_API_KEY: key },
  };
  let mf = new Miniflare(options);
  const sockets = [];
  t.after(async () => {
    for (const ws of sockets) { try { ws.close(); } catch {} }
    await mf.dispose(); await rm(dir, { recursive: true, force: true });
  });
  return {
    async request(path, body, requestKey = key, raw = false) {
      const headers = requestKey === null ? {} : { 'x-api-key': requestKey };
      const response = await mf.dispatchFetch('https://pairlink.test' + path, {
        headers, ...(body === undefined ? {} : { method: 'POST', body: raw ? body : JSON.stringify(body) }),
      });
      return { status: response.status, body: await response.json() };
    },
    async socket(id = 'bob', requestKey = key) {
      const response = await mf.dispatchFetch('https://pairlink.test/api/device/ws?device_id=' + id, {
        headers: { Upgrade: 'websocket', ...(requestKey === null ? {} : { 'x-api-key': requestKey }) },
      });
      if (response.status !== 101) return { status: response.status };
      const ws = response.webSocket; ws.accept(); sockets.push(ws); return ws;
    },
    async restart() { await mf.dispose(); mf = new Miniflare(options); },
  };
}

function message(ws) {
  return new Promise((resolve, reject) => {
    const handler = event => { clearTimeout(timer); resolve(event.data); };
    const timer = setTimeout(() => { ws.removeEventListener('message', handler); reject(new Error('WebSocket message timed out')); }, 5000);
    ws.addEventListener('message', handler, { once: true });
  });
}

test('API authentication covers HTTP and WebSocket, and fails closed without a secret', async t => {
  const f = await fixture(t);
  assert.equal((await f.request('/health', undefined, null)).body.version, '0.2.0');
  assert.equal((await f.request('/api/health', undefined, null)).status, 401);
  assert.equal((await f.request('/api/health', undefined, 'wrong')).status, 401);
  assert.equal((await f.request('/api/commands', input(), null)).status, 401);
  assert.equal((await f.request('/api/device/result', {}, null)).status, 401);
  assert.equal((await f.socket('bob', null)).status, 401);
  assert.equal((await f.socket('bob', 'wrong')).status, 401);
  assert.equal((await f.request('/api/health')).body.backend, 'cloudflare');
  const unconfigured = await fixture(t, '');
  assert.equal((await unconfigured.request('/api/health')).status, 503);
});

test('Android ping and every current command round-trip through hibernatable WebSockets and HTTP results', async t => {
  const f = await fixture(t), ws = await f.socket();
  const pong = message(ws); ws.send('ping'); assert.equal(await pong, 'pong');
  for (const [action, params] of [
    ['get_status', {}], ['get_recent_apps', { hours: 24 }], ['get_installed_apps', {}],
    ['get_notifications', {}], ['get_media', {}], ['send_notification', { title: '测试', content: '普通通知' }],
    ['set_app_suspended', { package_name: 'com.example.app', suspended: true }],
    ['shell', { command: 'whoami', timeout_seconds: 20 }],
  ]) {
    const received = message(ws);
    const sent = await f.request('/api/commands', input(action, params));
    assert.equal(sent.status, 202); assert.equal(sent.body.status, 'pending');
    const command = JSON.parse(await received);
    assert.equal(command.type, 'command'); assert.equal(command.command_id, sent.body.command_id);
    assert.equal(command.action, action);
    assert.equal((await f.request('/api/device/result', { device_id: 'bob', command_id: command.command_id, ok: true, result: { action } })).status, 200);
    const result = await f.request('/api/commands/' + command.command_id);
    assert.equal(result.body.status, 'completed'); assert.equal(result.body.result.action, action);
  }
});

test('only one concurrent command reaches a target; result identity and duplicate checks remain enforced', async t => {
  const f = await fixture(t), ws = await f.socket();
  const received = message(ws);
  const results = await Promise.all([f.request('/api/commands', input()), f.request('/api/commands', input())]);
  assert.deepEqual(results.map(r => r.status).sort(), [202, 409]);
  const command = JSON.parse(await received);
  assert.equal((await f.request('/api/device/result', { device_id: 'alice', command_id: command.command_id, ok: true })).status, 409);
  const ack = message(ws);
  ws.send(JSON.stringify({ type: 'result', command_id: command.command_id, ok: false, error: '未授权' }));
  assert.equal(JSON.parse(await ack).type, 'result_ack');
  assert.equal((await f.request('/api/commands/' + command.command_id)).body.status, 'failed');
  assert.equal((await f.request('/api/device/result', { device_id: 'bob', command_id: command.command_id, ok: true })).status, 409);
});

test('replacement connection survives the old socket closing', async t => {
  const f = await fixture(t), old = await f.socket(), current = await f.socket();
  assert.notEqual(old, current);
  const received = message(current);
  const sent = await f.request('/api/commands', input());
  assert.equal(sent.body.status, 'pending');
  assert.equal(JSON.parse(await received).command_id, sent.body.command_id);
});

test('screenshots larger than the old KV value limit persist in SQLite and can be polled', async t => {
  const f = await fixture(t), ws = await f.socket();
  const received = message(ws);
  const sent = await f.request('/api/commands', input('get_screenshot'));
  await received;
  const bytes = Buffer.alloc(180000); bytes[0] = 0xff; bytes[1] = 0xd8; bytes[bytes.length - 2] = 0xff; bytes[bytes.length - 1] = 0xd9;
  const result = { mime_type: 'image/jpeg', image_base64: bytes.toString('base64'), width: 100, height: 100, foreground_package: 'com.example.app', backend: 'shizuku' };
  assert.equal((await f.request('/api/device/result', { device_id: 'bob', command_id: sent.body.command_id, ok: true, result })).status, 200);
  const saved = await f.request('/api/commands/' + sent.body.command_id);
  assert.equal(saved.body.result.image_base64, result.image_base64);
  assert.ok(saved.body.result.image_expires_at > Date.now());
});

test('offline records survive a runtime restart; malformed and oversized requests are rejected', async t => {
  const f = await fixture(t);
  const offline = await f.request('/api/commands', input());
  assert.equal(offline.body.status, 'offline');
  await f.restart();
  assert.equal((await f.request('/api/commands/' + offline.body.command_id)).body.status, 'offline');
  assert.equal((await f.request('/api/commands', '{', API_KEY, true)).status, 400);
  assert.equal((await f.request('/api/commands', 'x'.repeat(12001), API_KEY, true)).status, 413);
  assert.equal((await f.request('/api/commands', input('unknown'))).status, 400);
});
