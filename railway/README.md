# PairLink Railway 服务端

与 `../worker/` 的 Cloudflare Worker 并存，安卓客户端接口相同。Node.js 24 + WebSocket + SQLite；单个服务实例，不需要另建数据库服务。

## Railway 部署

1. New Project → Deploy from GitHub repo，选择 `lijinmu666/PairLink`。
2. 服务 Settings → Source，Root Directory 设置为 `/railway`，保存后重新部署。
3. 使用目录中的 Dockerfile 自动构建，启动命令已包含在 Dockerfile 中；不用 Wrangler，也不用编译 Android。
4. 给这个服务添加 Volume，挂载到 `/data`；服务变量添加 `DATA_DIR=/data`。SQLite 和 WAL 文件都放在这个目录。未挂载 Volume 时，重部署可能丢失结果记录。
5. Healthcheck Path 设置为 `/health`。保持单实例（1 replica），关闭 Serverless/自动休眠。设备长连接必须连到同一个进程，不能多实例运行。
6. Settings → Networking → Generate Domain，获得 HTTPS 地址。端口使用 Railway 自动提供的 `PORT`，程序监听 `0.0.0.0`。
7. 打开 `https://生成的域名/health`，应返回 `ok: true`、`backend: railway`。

两台手机先暂停共享，再把服务器地址改成同一个 Railway HTTPS 根地址（不带 `/health`），保存并重新开启共享。客户端使用系统 DNS，可直接测试连接。双方的 ID 和授权开关不需要更改。

保留的 Cloudflare Worker 仍按原来的方式部署。两个后端的数据相互独立，切换地址不会迁移旧指令或结果。服务重启时手机会重连；尚未完成的指令不自动重发，最多等待 45 秒后超时，避免重复执行 Shell。

## 本地运行

要求 Node.js 24：

```bash
cd railway
npm ci
npm run check
npm test
npm start
```

默认端口 3000，数据目录 `./data`。可使用 `PORT` 和 `DATA_DIR` 更改。协议验证代码与 Worker 保持一致，测试会检查两份文件是否相同。

## 接口与保留规则

- `GET /health`
- `GET /api/device/ws?device_id=...`：WebSocket，文本 `ping` → `pong`
- `POST /api/commands`：提交指令，返回指令 ID 和状态
- `GET /api/commands/:id`：查询执行结果
- `POST /api/device/result`：安卓设备回传结果
- WebSocket `result` 消息和 `result_ack` 同样保留

所有现有指令均支持，包括普通通知、当前通知查询、应用暂停和恢复、root/Shizuku Shell。执行由接收手机完成，服务器不执行手机的 Shell。

同一目标设备只能有一条待完成指令；超时为 45 秒，指令和结果保留 24 小时，最多保存 1000 条。SQLite 放到 Volume 后可跨重启保存，在线连接不持久化。SQLite 清理删除的是记录，数据库文件可能保留可复用的空间。

与现有 Worker 一样，此版本没有账号、OAuth 或 API Key，设备 ID 只用于路由，不构成身份验证。日志不打印 Shell 命令、通知正文或结果内容。

## 本次验证

8 项集成测试和语法检查通过：协议一致性、7 种指令往返、心跳、忙碌/错误设备/重复结果拒绝、同 ID 新连接替换、重启持久化、45 秒超时及 24 小时清理、无响应连接检测、无效与超大请求拒绝。尚未在你的 Railway 账号部署，也未测试中国内地手机直连。

