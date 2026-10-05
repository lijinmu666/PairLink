import { DurableObject } from 'cloudflare:workers';
import { validId, validCommandId, validateCommand, acceptResult } from './protocol.js';

const DAY = 86400000;
const reply = (body, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' }
});
export class PairHub extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair('ping', 'pong'));
  }
  async armAlarm() {
    const records = await this.ctx.storage.list({ prefix: 'command:' });
    let next = Date.now() + DAY;
    for (const r of records.values()) next = Math.min(next, r.created_at + DAY, r.status === 'pending' ? r.deadline_at : Infinity);
    if (records.size) await this.ctx.storage.setAlarm(Math.max(Date.now() + 1000, next));
    else await this.ctx.storage.deleteAlarm();
  }
  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname === '/api/device/ws') {
      const id = url.searchParams.get('device_id');
      if (!validId(id)) return reply({ error: 'invalid device_id' }, 400);
      if (request.headers.get('upgrade')?.toLowerCase() !== 'websocket') return reply({ error: 'websocket required' }, 426);
      for (const old of this.ctx.getWebSockets(id)) old.close(1000, '设备建立了新连接');
      const pair = new WebSocketPair();
      pair[1].serializeAttachment({ device_id: id });
      this.ctx.acceptWebSocket(pair[1], [id]);
      return new Response(null, { status: 101, webSocket: pair[0] });
    }
    if (url.pathname === '/api/device/result' && request.method === 'POST') {
      const text = await request.text();
      if (text.length > 60000) return reply({ error: 'result too large' }, 413);
      let body;
      try { body = JSON.parse(text); } catch { return reply({ error: 'invalid result' }, 400); }
      if (!body || !validId(body.device_id) || !validCommandId(body.command_id)) return reply({ error: 'invalid result' }, 400);
      const record = await this.ctx.storage.get('command:' + body.command_id);
      const accepted = acceptResult(record, body.device_id, body, Date.now());
      if (!accepted) return reply({ error: '指令不存在、已结束或设备不匹配' }, 409);
      await this.ctx.storage.put('command:' + body.command_id, accepted);
      await this.armAlarm();
      return reply({ ok: true });
    }
    if (url.pathname === '/api/commands' && request.method === 'POST') {
      if (Number(request.headers.get('content-length') ?? 0) > 12000) return reply({ error: 'body too large' }, 413);
      const raw = await request.text();
      if (raw.length > 12000) return reply({ error: 'body too large' }, 413);
      let input;
      try { input = validateCommand(JSON.parse(raw)); } catch { input = null; }
      if (!input) return reply({ error: 'invalid command' }, 400);
      const records = await this.ctx.storage.list({ prefix: 'command:' });
      if ([...records.values()].some(r => r.target_id === input.target_id && r.status === 'pending' && r.deadline_at > Date.now()))
        return reply({ error: '对方已有指令正在执行' }, 409);
      if (records.size >= 1000) return reply({ error: '指令记录已满，请稍后重试' }, 429);
      const command_id = crypto.randomUUID();
      const record = { ...input, command_id, created_at: Date.now(), deadline_at: Date.now() + 45000, status: 'pending' };
      await this.ctx.storage.put('command:' + command_id, record);
      let sent = false;
      for (const ws of this.ctx.getWebSockets(input.target_id)) {
        try { ws.send(JSON.stringify({ type: 'command', ...record })); sent = true; break; } catch {}
      }
      if (!sent) {
        record.status = 'offline'; record.completed_at = Date.now();
        await this.ctx.storage.put('command:' + command_id, record);
      }
      await this.armAlarm();
      return reply(record, 202);
    }
    if (url.pathname.startsWith('/api/commands/') && request.method === 'GET') {
      const id = url.pathname.slice('/api/commands/'.length);
      if (!validCommandId(id)) return reply({ error: 'invalid command_id' }, 400);
      let record = await this.ctx.storage.get('command:' + id);
      if (!record || record.created_at + DAY <= Date.now()) return reply({ error: 'command not found' }, 404);
      if (record.status === 'pending' && record.deadline_at <= Date.now()) {
        record = { ...record, status: 'timeout', completed_at: Date.now() };
        await this.ctx.storage.put('command:' + id, record);
      }
      return reply(record);
    }
    return reply({ error: 'not found' }, 404);
  }
  async webSocketMessage(ws, message) {
    if (typeof message !== 'string' || message.length > 60000) return;
    let body;
    try { body = JSON.parse(message); } catch { return; }
    if (!body || body.type !== 'result' || !validCommandId(body.command_id)) return;
    const record = await this.ctx.storage.get('command:' + body.command_id);
    const accepted = acceptResult(record, ws.deserializeAttachment()?.device_id, body, Date.now());
    if (accepted) {
      await this.ctx.storage.put('command:' + body.command_id, accepted);
      ws.send(JSON.stringify({ type: 'result_ack', command_id: body.command_id }));
      await this.armAlarm();
    }
  }
  webSocketClose(ws, code, reason) { ws.close(code, reason); }
  webSocketError(ws) { ws.close(1011, '连接错误'); }
  async alarm() {
    const now = Date.now();
    const records = await this.ctx.storage.list({ prefix: 'command:' });
    for (const [key, r] of records) {
      if (r.created_at + DAY <= now) await this.ctx.storage.delete(key);
      else if (r.status === 'pending' && r.deadline_at <= now)
        await this.ctx.storage.put(key, { ...r, status: 'timeout', completed_at: now });
    }
    await this.armAlarm();
  }
}
export default {
  async fetch(request, env) {
    const path = new URL(request.url).pathname;
    if (path === '/health') return reply({ ok: true, service: 'PairLink', version: '0.1.0' });
    if (path.startsWith('/api/')) return env.HUB.get(env.HUB.idFromName('pairlink-v1')).fetch(request);
    return reply({ service: 'PairLink', health: '/health' });
  }
};
