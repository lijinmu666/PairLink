import { DurableObject } from 'cloudflare:workers';
import { Buffer } from 'node:buffer';
import { timingSafeEqual } from 'node:crypto';
import { validId, validCommandId, validateCommand, acceptResult, SCREENSHOT_RESULT_CHARS } from '../../railway/src/protocol.js';
import { CommandStore, DAY } from './store.js';

const health = { ok: true, service: 'PairLink', version: '0.2.0', backend: 'cloudflare' };
const reply = (body, status = 200) => Response.json(body, { status, headers: { 'cache-control': 'no-store' } });

function validKey(expected, provided) {
  if (!expected || typeof provided !== 'string') return false;
  const a = Buffer.from(expected), b = Buffer.from(provided);
  return a.length === b.length && timingSafeEqual(a, b);
}

async function jsonBody(request, maxChars) {
  const limit = maxChars * 4;
  if (Number(request.headers.get('content-length')) > limit)
    throw Object.assign(new Error('body too large'), { status: 413 });
  const reader = request.body?.getReader();
  if (!reader) throw Object.assign(new Error('invalid JSON'), { status: 400 });
  const decoder = new TextDecoder();
  let bytes = 0, text = '';
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      bytes += value.byteLength;
      if (bytes > limit) throw Object.assign(new Error('body too large'), { status: 413 });
      text += decoder.decode(value, { stream: true });
      if (text.length > maxChars) throw Object.assign(new Error('body too large'), { status: 413 });
    }
    text += decoder.decode();
    if (text.length > maxChars) throw Object.assign(new Error('body too large'), { status: 413 });
  } catch (error) {
    await reader.cancel().catch(() => {});
    throw error;
  } finally { reader.releaseLock(); }
  try { return JSON.parse(text); }
  catch { throw Object.assign(new Error('invalid JSON'), { status: 400 }); }
}

export class PairHub extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.store = new CommandStore(ctx.storage.sql);
    ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair('ping', 'pong'));
  }
  armAlarm() {
    const next = this.store.nextAlarm();
    return next === null ? this.ctx.storage.deleteAlarm()
      : this.ctx.storage.setAlarm(Math.max(Date.now() + 1000, next));
  }
  saveResult(id, device, body) {
    const record = this.store.get(id);
    if (!record || record.created_at + DAY <= Date.now()) return false;
    const accepted = acceptResult(record, device, body, Date.now());
    if (!accepted) return false;
    this.store.put(accepted);
    return true;
  }
  async fetch(request) {
    try {
      const url = new URL(request.url), path = url.pathname;
      if (path === '/api/device/ws') {
        const device = url.searchParams.get('device_id');
        if (!validId(device)) return reply({ error: 'invalid device_id' }, 400);
        if (request.headers.get('upgrade')?.toLowerCase() !== 'websocket') return reply({ error: 'websocket required' }, 426);
        for (const old of this.ctx.getWebSockets(device)) {
          old.serializeAttachment({ device_id: device, replaced: true });
          old.close(1000, '设备建立了新连接');
        }
        const pair = new WebSocketPair();
        this.ctx.acceptWebSocket(pair[1], [device]);
        pair[1].serializeAttachment({ device_id: device, replaced: false });
        return new Response(null, { status: 101, webSocket: pair[0] });
      }
      if (path === '/api/commands' && request.method === 'POST') {
        const input = validateCommand(await jsonBody(request, 12000));
        if (!input) return reply({ error: 'invalid command' }, 400);
        this.store.sweep(Date.now());
        // No await between checking the pending command and persisting its replacement.
        if (this.store.busy(input.target_id)) return reply({ error: '对方已有指令正在执行' }, 409);
        if (this.store.count() >= 1000) return reply({ error: '指令记录已满，请稍后重试' }, 429);
        const now = Date.now();
        const record = { ...input, command_id: crypto.randomUUID(), created_at: now, deadline_at: now + 45000, status: 'pending' };
        this.store.put(record);
        const ws = this.ctx.getWebSockets(input.target_id).find(socket => socket.readyState === 1 && !socket.deserializeAttachment()?.replaced);
        let sent = false;
        if (ws) {
          try { ws.send(JSON.stringify({ type: 'command', ...record })); sent = true; }
          catch { ws.close(1011, '连接错误'); }
        }
        if (!sent) {
          record.status = 'offline'; record.completed_at = Date.now(); this.store.put(record);
        }
        await this.armAlarm();
        return reply(record, 202);
      }
      if (path.startsWith('/api/commands/') && request.method === 'GET') {
        const id = path.slice('/api/commands/'.length);
        if (!validCommandId(id)) return reply({ error: 'invalid command_id' }, 400);
        this.store.sweep(Date.now());
        const record = this.store.get(id);
        await this.armAlarm();
        return record ? reply(record) : reply({ error: 'command not found' }, 404);
      }
      if (path === '/api/device/result' && request.method === 'POST') {
        const body = await jsonBody(request, SCREENSHOT_RESULT_CHARS + 1000);
        if (!body || !validId(body.device_id) || !validCommandId(body.command_id)) return reply({ error: 'invalid result' }, 400);
        if (this.store.get(body.command_id)?.action !== 'get_screenshot' && JSON.stringify(body).length > 60000)
          return reply({ error: 'body too large' }, 413);
        if (!this.saveResult(body.command_id, body.device_id, body)) return reply({ error: '指令不存在、已结束或设备不匹配' }, 409);
        await this.armAlarm();
        return reply({ ok: true });
      }
      return reply({ error: 'not found' }, 404);
    } catch (error) {
      if (!error.status) console.error(JSON.stringify({ event: 'request_failed', error: error.name }));
      return reply({ error: error.status ? error.message : 'internal server error' }, error.status ?? 500);
    }
  }
  async webSocketMessage(ws, message) {
    const attachment = ws.deserializeAttachment();
    if (attachment?.replaced || typeof message !== 'string' || message.length > 60000) return;
    let body;
    try { body = JSON.parse(message); } catch { return; }
    if (!body || body.type !== 'result' || !validCommandId(body.command_id)) return;
    if (this.saveResult(body.command_id, attachment?.device_id, body)) {
      await this.armAlarm();
      ws.send(JSON.stringify({ type: 'result_ack', command_id: body.command_id }));
    }
  }
  webSocketClose(ws, code, reason) { ws.close(code, reason); }
  webSocketError(ws) { ws.close(1011, '连接错误'); }
  async alarm() { this.store.sweep(Date.now()); await this.armAlarm(); }
}

export default {
  async fetch(request, env) {
    const path = new URL(request.url).pathname;
    if (path === '/health') return reply(health);
    if (path.startsWith('/api/')) {
      const key = (env.PAIRLINK_API_KEY ?? '').trim();
      if (!key) return reply({ error: 'API key not configured' }, 503);
      if (!validKey(key, request.headers.get('x-api-key'))) return reply({ error: 'invalid API key' }, 401);
      if (path === '/api/health') return reply(health);
      // A fresh namespace name keeps unauthenticated legacy sessions out of the new hub.
      return env.HUB.getByName('pairlink-v2').fetch(request);
    }
    return reply({ service: 'PairLink', health: '/health' });
  },
};
