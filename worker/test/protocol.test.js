import test from 'node:test';
import assert from 'node:assert/strict';
import { validateCommand, acceptResult } from '../src/protocol.js';
const base = { sender_id: 'alice', target_id: 'bob' };
test('拒绝无效目标和未知指令', () => {
  assert.equal(validateCommand({ ...base, action: 'anything' }), null);
  assert.equal(validateCommand({ ...base, target_id: '../x', action: 'get_status' }), null);
  assert.equal(validateCommand({ ...base, target_id: 'alice', action: 'get_status' }), null);
});
test('Shell 参数受限，root 必须为布尔值', () => {
  const good = { ...base, action: 'shell', params: { command: 'id' } };
  assert.equal(validateCommand(good).params.root, false);
  assert.equal(validateCommand({ ...good, params: { command: 'id', root: 'false' } }), null);
  assert.equal(validateCommand({ ...good, params: { command: 'id', timeout_seconds: 31 } }), null);
  assert.equal(validateCommand({ ...good, params: { command: ' ' } }), null);
});
test('最近应用查询范围为 1 到 24 小时', () => {
  assert.equal(validateCommand({ ...base, action: 'get_recent_apps', params: { hours: 25 } }), null);
  assert.equal(validateCommand({ ...base, action: 'get_recent_apps' }).params.hours, 1);
});
test('结果必须来自目标设备，不能覆盖已经结束的指令', () => {
  const r = { target_id: 'bob', status: 'pending', deadline_at: 100 };
  assert.equal(acceptResult(r, 'alice', { ok: true }, 50), null);
  assert.equal(acceptResult({ ...r, status: 'completed' }, 'bob', { ok: true }, 50), null);
  assert.equal(acceptResult(r, 'bob', { ok: true, result: { battery: 80 } }, 50).status, 'completed');
});
test('过期结果不能恢复超时指令，输出受限', () => {
  const r = { target_id: 'bob', status: 'pending', deadline_at: 100 };
  assert.equal(acceptResult(r, 'bob', { ok: true }, 101).status, 'timeout');
  assert.equal(acceptResult(r, 'bob', { ok: true, result: 'x'.repeat(48001) }, 50), null);
});
test('当前通知查询不接受自定义参数，通知结果通过现有通道回传', () => {
  const command = validateCommand({ ...base, action: 'get_notifications', params: { root: true, history: true } });
  assert.deepEqual(command, { ...base, action: 'get_notifications', params: {} });
  assert.equal(validateCommand({ ...base, action: 'get_notifications', params: [] }), null);
  const result = { notifications: [{ package_name: 'example.chat', app_name: '聊天', title: '消息', text: '你好', posted_at: 42 }],
    total_count: 1, returned_count: 1, truncated: false };
  const pending = { target_id: 'bob', status: 'pending', deadline_at: 100 };
  assert.deepEqual(acceptResult(pending, 'bob', { ok: true, result }, 50).result, result);
  assert.equal(acceptResult(pending, 'alice', { ok: true, result }, 50), null);
  assert.equal(acceptResult(pending, 'bob', { ok: false, error: '对方未开启通知共享' }, 50).status, 'failed');
});
