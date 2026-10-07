import { createServer } from 'node:http';
import { randomUUID } from 'node:crypto';
import { WebSocket, WebSocketServer } from 'ws';
import { CommandStore, DAY } from './store.js';
import { validId, validCommandId, validateCommand, acceptResult } from './protocol.js';

function reply(res, body, status = 200) {
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
  res.end(JSON.stringify(body));
}

async function jsonBody(req, maxChars) {
  const chunks = [];
  let bytes = 0;
  for await (const chunk of req) {
    bytes += chunk.length;
    if (bytes > maxChars * 4) throw Object.assign(new Error('body too large'), { status: 413 });
    chunks.push(chunk);
  }
  const text = Buffer.concat(chunks).toString('utf8');
  if (text.length > maxChars) throw Object.assign(new Error('body too large'), { status: 413 });
  try { return JSON.parse(text); }
  catch { throw Object.assign(new Error('invalid JSON'), { status: 400 }); }
}

export function createPairLink({ databasePath = './data/pairlink.sqlite', now = Date.now, deadlineMs = 45000, heartbeatMs = 30000 } = {}) {
  const store = new CommandStore(databasePath);
  const devices = new Map();
  const wss = new WebSocketServer({ noServer: true, maxPayload: 240000, perMessageDeflate: false });
  let stopping = false;
  store.sweep(now());

  function saveResult(id, deviceId, body) {
    const record = store.get(id);
    if (!record || record.created_at + DAY <= now()) return false;
    const accepted = acceptResult(record, deviceId, body, now());
    if (!accepted) return false;
    store.put(accepted);
    return true;
  }

  const server = createServer(async (req, res) => {
    try {
      const url = new URL(req.url, 'http://localhost');
      const path = url.pathname;
      if (stopping) return reply(res, { error: 'server stopping' }, 503);
      if (path === '/health') return reply(res, { ok: true, service: 'PairLink', version: '0.1.0', backend: 'railway' });
      if (path === '/api/device/ws') return reply(res, { error: 'websocket required' }, 426);
      if (path === '/api/commands' && req.method === 'POST') {
        const input = validateCommand(await jsonBody(req, 12000));
        if (!input) return reply(res, { error: 'invalid command' }, 400);
        store.sweep(now());
        const records = store.all();
        if (records.some(r => r.target_id === input.target_id && r.status === 'pending'))
          return reply(res, { error: '对方已有指令正在执行' }, 409);
        if (records.length >= 1000) return reply(res, { error: '指令记录已满，请稍后重试' }, 429);
        const time = now();
        const record = { ...input, command_id: randomUUID(), created_at: time, deadline_at: time + deadlineMs, status: 'pending' };
        const ws = devices.get(input.target_id);
        if (!ws || ws.readyState !== WebSocket.OPEN) {
          record.status = 'offline'; record.completed_at = time;
          store.put(record);
          return reply(res, record, 202);
        }
        store.put(record);
        try {
          ws.send(JSON.stringify({ type: 'command', ...record }), error => {
            if (!error || stopping) return;
            if (devices.get(input.target_id) === ws) devices.delete(input.target_id);
            ws.terminate();
          });
        } catch {
          ws.terminate();
          record.status = 'offline'; record.completed_at = now();
          store.put(record);
        }
        return reply(res, record, 202);
      }
      if (path.startsWith('/api/commands/') && req.method === 'GET') {
        const id = path.slice('/api/commands/'.length);
        if (!validCommandId(id)) return reply(res, { error: 'invalid command_id' }, 400);
        store.sweep(now());
        const record = store.get(id);
        return record ? reply(res, record) : reply(res, { error: 'command not found' }, 404);
      }
      if (path === '/api/device/result' && req.method === 'POST') {
        const body = await jsonBody(req, 60000);
        if (!body || !validId(body.device_id) || !validCommandId(body.command_id)) return reply(res, { error: 'invalid result' }, 400);
        if (!saveResult(body.command_id, body.device_id, body)) return reply(res, { error: '指令不存在、已结束或设备不匹配' }, 409);
        return reply(res, { ok: true });
      }
      if (path.startsWith('/api/')) return reply(res, { error: 'not found' }, 404);
      return reply(res, { service: 'PairLink', health: '/health' });
    } catch (error) {
      if (!res.headersSent && !res.destroyed) reply(res, { error: error.status ? error.message : 'internal server error' }, error.status ?? 500);
      if (!error.status) console.error('PairLink request failed:', error.name);
    }
  });
  server.requestTimeout = 20000;
  server.headersTimeout = 15000;

  server.on('upgrade', (req, socket, head) => {
    let url;
    try { url = new URL(req.url, 'http://localhost'); }
    catch { socket.destroy(); return; }
    const id = url.searchParams.get('device_id');
    if (stopping || url.pathname !== '/api/device/ws' || !validId(id)) {
      socket.end('HTTP/1.1 400 Bad Request\r\nConnection: close\r\nContent-Length: 0\r\n\r\n');
      return;
    }
    wss.handleUpgrade(req, socket, head, ws => {
      const old = devices.get(id);
      ws.deviceId = id;
      ws.alive = true;
      devices.set(id, ws);
      if (old) old.terminate();
      wss.emit('connection', ws);
    });
  });

  wss.on('connection', ws => {
    ws.on('pong', () => { ws.alive = true; });
    ws.on('close', () => { if (devices.get(ws.deviceId) === ws) devices.delete(ws.deviceId); });
    ws.on('error', () => ws.terminate());
    ws.on('message', (data, binary) => {
      if (stopping || binary || devices.get(ws.deviceId) !== ws) return;
      const text = data.toString();
      if (text === 'ping') { ws.alive = true; ws.send('pong'); return; }
      if (text.length > 60000) return;
      let body;
      try { body = JSON.parse(text); } catch { return; }
      if (!body || body.type !== 'result' || !validCommandId(body.command_id)) return;
      try {
        if (saveResult(body.command_id, ws.deviceId, body))
          ws.send(JSON.stringify({ type: 'result_ack', command_id: body.command_id }));
      } catch { ws.close(1011, '结果存储失败'); }
    });
  });

  const cleanup = setInterval(() => store.sweep(now()), 1000);
  const heartbeat = setInterval(() => {
    for (const ws of wss.clients) {
      if (!ws.alive) { ws.terminate(); continue; }
      ws.alive = false;
      ws.ping();
    }
  }, heartbeatMs);
  cleanup.unref(); heartbeat.unref();

  return {
    server,
    async listen(port = 0, host = '0.0.0.0') {
      await new Promise((resolve, reject) => {
        server.once('error', reject);
        server.listen(port, host, () => { server.removeListener('error', reject); resolve(); });
      });
      return server.address();
    },
    async close() {
      if (stopping) return;
      stopping = true;
      clearInterval(cleanup); clearInterval(heartbeat);
      for (const ws of wss.clients) ws.terminate();
      await new Promise(resolve => wss.close(resolve));
      if (server.listening) await new Promise(resolve => { server.close(resolve); server.closeAllConnections(); });
      store.close();
    },
  };
}
