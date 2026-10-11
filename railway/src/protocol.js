export const SCREENSHOT_BYTES = 200000;
export const SCREENSHOT_RESULT_CHARS = 300000;
export const SCREENSHOT_TTL = 10 * 60 * 1000;
export const validId = value => typeof value === 'string' && /^[A-Za-z0-9._-]{1,64}$/.test(value);
export const validCommandId = value => typeof value === 'string' && /^[0-9a-f-]{36}$/.test(value);
export function validateCommand(body) {
  if (!body || !validId(body.sender_id) || !validId(body.target_id) || body.sender_id === body.target_id) return null;
  if (!['get_screenshot', 'get_status', 'get_media', 'get_recent_apps', 'get_notifications', 'get_installed_apps', 'send_notification', 'set_app_suspended', 'shell'].includes(body.action)) return null;
  const params = body.params ?? {};
  if (typeof params !== 'object' || params === null || Array.isArray(params)) return null;
  if (body.action === 'get_screenshot' || body.action === 'get_status' || body.action === 'get_media' || body.action === 'get_notifications' || body.action === 'get_installed_apps') return { sender_id: body.sender_id, target_id: body.target_id, action: body.action, params: {} };
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
  if (record.action === 'get_screenshot' && body.ok) {
    if (!result || typeof result !== 'object' || Array.isArray(result) || result.mime_type !== 'image/jpeg'
        || typeof result.image_base64 !== 'string' || result.image_base64.length > Math.ceil(SCREENSHOT_BYTES / 3) * 4
        || !/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(result.image_base64)
        || !Number.isInteger(result.width) || !Number.isInteger(result.height)
        || result.width < 1 || result.height < 1 || result.width > 2048 || result.height > 2048
        || typeof result.foreground_package !== 'string' || result.foreground_package.length > 255
        || !/^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+$/.test(result.foreground_package)
        || !['root', 'shizuku'].includes(result.backend)) return null;
    const jpeg = Buffer.from(result.image_base64, 'base64');
    if (jpeg.length < 4 || jpeg.length > SCREENSHOT_BYTES || jpeg.toString('base64') !== result.image_base64
        || jpeg[0] !== 0xff || jpeg[1] !== 0xd8 || jpeg.at(-2) !== 0xff || jpeg.at(-1) !== 0xd9
        || JSON.stringify(result).length > SCREENSHOT_RESULT_CHARS) return null;
    return { ...record, status: 'completed', result: { ...result, image_expires_at: now + SCREENSHOT_TTL },
      error: null, completed_at: now };
  }
  if (JSON.stringify(result).length > 48000) return null;
  return { ...record, status: body.ok ? 'completed' : 'failed', result, error, completed_at: now };
}
