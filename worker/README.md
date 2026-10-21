# PairLink Cloudflare 服务端 0.2.0

当前入口：`https://pairlink.lijinmu.com`

Worker 使用 SQLite Durable Object 保存指令并转发 WebSocket 消息，兼容当前 Android 0.2.0 客户端，包括状态、应用、通知、媒体、暂停应用、Shell 和截图。与 Railway 共用 `railway/src/protocol.js`，两种后端保持相同请求和返回格式。

## 客户端切换

双方暂停共享连接，在“连接设置”填写上述 HTTPS 根地址和相同的 API 密钥，保存后重新开启共享。双方设备 ID 和本机权限设置继续使用原值，无需重新编译客户端。首次测试时客户端会刷新服务端版本号，`backend` 返回 `cloudflare`。

服务器中的旧指令和结果不从 Railway 搬迁；本机连接与执行记录仍保留。切换前请等正在执行的指令结束，执行状态不确定的 Shell 不要重复发送。

## 部署

Cloudflare Workers Builds 连接本仓库 `main`，根目录 `/worker`，构建命令 `npm ci && npm test && npm run check`，部署命令 `npx wrangler deploy`。`wrangler.jsonc` 声明 `HUB`、SQLite Durable Object 迁移和 `pairlink.lijinmu.com` 自定义域名。

首次部署需要在 Worker 设置中添加 Secret `PAIRLINK_API_KEY`，或使用 `npx wrangler secret put PAIRLINK_API_KEY`。密钥不写入源码、Wrangler 配置或客户端默认值。缺少 Secret 时所有 `/api/*` 请求返回 503；密钥缺失或不正确时返回 401，WebSocket 握手同样校验。`/health` 为公开健康检查，客户端使用需要密钥的 `/api/health`。

本地验证：

```bash
cd worker
npm ci
npm test
npm run check
```

本地开发可在忽略提交的 `.dev.vars` 中填写 `PAIRLINK_API_KEY`，再运行 `npm run dev`。需要 Node.js 24 或更新版本，测试使用 Miniflare 的真实 Workers 运行时和 SQLite Durable Objects。

## 连接与保留规则

一个私有 PairLink 服务对应一个协调双方手机的 Hub，WebSocket 使用休眠 API 和自动 ping/pong；同一设备的新连接替换旧连接。同一目标同时只接收一个待执行指令，断线时不重放指令。

指令等待结果最多 45 秒，记录最多保留 24 小时和 1000 条；截图原始图片最多 200 KB，以 SQLite 保存，10 分钟后删除图片数据。清理使用 Durable Object Alarm，查询时也检查过期状态。新 Hub 使用 `pairlink-v2` 名称，与历史无鉴权的 Worker 会话隔离。

Railway 代码保留于 `railway/`，需要时可重新切回对应 HTTPS 根地址。
