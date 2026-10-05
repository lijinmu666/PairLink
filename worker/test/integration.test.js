import test from 'node:test';
import assert from 'node:assert/strict';
import { Miniflare } from 'miniflare';
import { fileURLToPath } from 'node:url';

test('Worker 完整流程：离线、连接、指令、权限拒绝、结果与重复回传', async () => {
  const mf = new Miniflare({ modules: true,
    modulesRules: [{ type: 'ESModule', include: ['**/*.js'] }],
    scriptPath: fileURLToPath(new URL('../src/index.js', import.meta.url)),
    compatibilityDate: '2026-08-06',
    durableObjects: { HUB: { className: 'PairHub', useSQLite: true } }
  });
  const post = (path, value) => mf.dispatchFetch('https://test' + path, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(value)
  });
  let socket;
  try {
    const health = await mf.dispatchFetch('https://test/health');
    assert.equal((await health.json()).ok, true);
    const offline = await (await post('/api/commands', { sender_id: 'alice', target_id: 'bob', action: 'get_status' })).json();
    assert.equal(offline.status, 'offline');
    const upgrade = await mf.dispatchFetch('https://test/api/device/ws?device_id=bob', { headers: { upgrade: 'websocket' } });
    assert.equal(upgrade.status, 101);
    socket = upgrade.webSocket; socket.accept();
    const message = new Promise(resolve => socket.addEventListener('message', e => resolve(JSON.parse(e.data)), { once: true }));
    const submitted = await (await post('/api/commands', { sender_id: 'alice', target_id: 'bob', action: 'get_status' })).json();
    assert.equal(submitted.status, 'pending');
    const delivered = await message;
    assert.equal(delivered.command_id, submitted.command_id);
    const collision = await post('/api/commands', { sender_id: 'alice', target_id: 'bob', action: 'get_status' });
    assert.equal(collision.status, 409);
    const spoofed = await post('/api/device/result', { device_id: 'other', command_id: submitted.command_id, ok: true });
    assert.equal(spoofed.status, 409);
    const returned = await post('/api/device/result', { device_id: 'bob', command_id: submitted.command_id, ok: true, result: { battery_percent: 88 } });
    assert.equal(returned.status, 200);
    const result = await (await mf.dispatchFetch('https://test/api/commands/' + submitted.command_id)).json();
    assert.equal(result.status, 'completed'); assert.equal(result.result.battery_percent, 88);
    const duplicate = await post('/api/device/result', { device_id: 'bob', command_id: submitted.command_id, ok: true });
    assert.equal(duplicate.status, 409);
    const shell = await (await post('/api/commands', { sender_id: 'alice', target_id: 'bob', action: 'shell', params: { command: 'id' } })).json();
    await post('/api/device/result', { device_id: 'bob', command_id: shell.command_id, ok: false, error: '对方未开启远程 Shell' });
    const denied = await (await mf.dispatchFetch('https://test/api/commands/' + shell.command_id)).json();
    assert.equal(denied.status, 'failed');
    assert.equal(denied.error, '对方未开启远程 Shell');
  } finally {
    if (socket) socket.close();
    await mf.dispose();
  }
});
