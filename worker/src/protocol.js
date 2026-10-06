export const validId = value => typeof value === 'string' && /^[A-Za-z0-9._-]{1,64}$/.test(value);
export const validCommandId = value => typeof value === 'string' && /^[0-9a-f-]{36}$/.test(value);
export function validateCommand(body) {
  if (!body || !validId(body.sender_id) || !validId(body.target_id) || body.sender_id === body.target_id) return null;
  if (!['get_status', 'get_recent_apps', 'get_notifications', 'get_installed_apps', 'send_notification', 'set_app_suspended', 'shell'].includes(body.action)) return null;
  const params = body.params ?? {};
  if (typeof params !== 'object' || params === null || Array.isArray(params)) return null;
  if (body.action === 'get_status' || body.action === 'get_notifications' || body.action === 'get_installed_apps') return { sender_id: body.sender_id, target_id: body.target_id, action: body.action, params: {} };
  if (body.action === 'get_recent_apps') {
    const hours = params.hours ?? 1;
    if (!Number.isInteger(hours) || hours < 1 || hours > 24) return null;
    return { sender_id: body.sender_id, target_id: body.target_id, action: body.action, params: { hours } };
  }
  if (body.action === 'send_notification') {
    if (typeof params.title !== 'string' || !params.title.trim() || params.title.length > 128 ||
        typeof params.content !== 'string' || !params.content.trim() || params.content.length > 4096) return null;
    return { sender_id: body.sender_id, target_id: body.target_id, action: body.action,
      params: { title: params.title, content: params.content } };
  }
  if (body.action === 'set_app_suspended') {
    if (typeof params.package_name !== 'string' || params.package_name.length > 255 ||
        !/^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+$/.test(params.package_name) ||
        typeof params.suspended !== 'boolean') return null;
    return { sender_id: body.sender_id, target_id: body.target_id, action: body.action,
      params: { package_name: params.package_name, suspended: params.suspended } };
  }
  if (typeof params.command !== 'string' || !params.command.trim() || params.command.length > 8192) return null;
  if (params.root !== undefined && typeof params.root !== 'boolean') return null;
  if (params.shizuku !== undefined && typeof params.shizuku !== 'boolean') return null;
  if (params.root && params.shizuku) return null;
  const timeout_seconds = params.timeout_seconds ?? 20;
  if (!Number.isInteger(timeout_seconds) || timeout_seconds < 1 || timeout_seconds > 30) return null;
  return { sender_id: body.sender_id, target_id: body.target_id, action: body.action,
    params: { command: params.command, root: params.root ?? false, shizuku: params.shizuku ?? false, timeout_seconds } };
}
export function acceptResult(record, deviceId, body, now) {
  if (!record || record.target_id !== deviceId || record.status !== 'pending') return null;
  if (now >= record.deadline_at) return { ...record, status: 'timeout', completed_at: now };
  if (typeof body.ok !== 'boolean') return null;
  const result = body.ok ? (body.result ?? {}) : null;
  const error = body.ok ? null : String(body.error ?? '设备执行失败').slice(0, 1024);
  if (JSON.stringify(result).length > 48000) return null;
  return { ...record, status: body.ok ? 'completed' : 'failed', result, error, completed_at: now };
}
