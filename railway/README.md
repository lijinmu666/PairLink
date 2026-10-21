# PairLink Railway 服务端

PairLink 的服务端，部署在 Railway。Node.js 24 + WebSocket + SQLite；单个服务实例，不需要另建数据库服务。

## Railway 部署

1. New Project → Deploy from GitHub repo，选择 `lijinmu666/PairLink`。
2. 服务 Settings → Source，Root Directory 设置为 `/railway`，保存后重新部署。
3. 使用目录中的 Dockerfile 自动构建，启动命令已包含在 Dockerfile 中；不用编译 Android。
4. 给这个服务添加 Volume，挂载到 `/data`；服务变量添加 `DATA_DIR=/data` 和 `PAIRLINK_API_KEY`。API 密钥只在 Railway 环境变量中保存，不放进仓库；两台客户端连接设置中填写同一个密钥。
5. Healthcheck Path 设置为 `/health`。保持单实例（1 replica），关闭 Serverless/自动休眠。设备长连接必须连到同一个进程，不能多实例运行。
6. Settings → Networking → Generate Domain，获得 HTTPS 地址。端口使用 Railway 自动提供的 `PORT`，程序监听 `0.0.0.0`。
7. 打开 `https://生成的域名/health`，应返回 `ok: true`、`backend: railway`。

两台手机先暂停共享，再把服务器地址改成同一个 Railway HTTPS 根地址（不带 `/health`），保存并重新开启共享。客户端使用系统 DNS，可直接测试连接。双方的 ID 和授权开关不需要更改。

服务重启时手机会重连；尚未完成的指令不自动重发，最多等待 45 秒后超时，避免重复执行 Shell。

## 本地运行

要求 Node.js 24：

```bash
cd railway
npm ci
npm run check
npm test
npm start
```

默认端口 3000，数据目录 `./data`。可使用 `PORT` 和 `DATA_DIR` 更改。协议验证代码位于 `src/protocol.js`。

## 接口与保留规则

- `GET /health`：Railway 健康检查，不需要 API 密钥
- `GET /api/health`：客户端连接测试，需要 API 密钥
- `GET /api/device/ws?device_id=...`：WebSocket，文本 `ping` → `pong`
- `POST /api/commands`：提交指令，返回指令 ID 和状态
- `GET /api/commands/:id`：查询执行结果
- `POST /api/device/result`：安卓设备回传结果
- WebSocket `result` 消息和 `result_ack` 同样保留

所有现有指令均支持，包括普通通知、当前通知查询、应用暂停和恢复、root/Shizuku Shell。执行由接收手机完成，服务器不执行手机的 Shell。

同一目标设备只能有一条待完成指令；超时为 45 秒，指令和结果保留 24 小时，最多保存 1000 条。SQLite 放到 Volume 后可跨重启保存，在线连接不持久化。SQLite 清理删除的是记录，数据库文件可能保留可复用的空间。

所有 `/api/*` HTTP 接口和设备 WebSocket 握手都要求 `X-API-Key`，缺失或不匹配时返回 401。部署启动时必须配置 `PAIRLINK_API_KEY`；`/health` 只用于 Railway 健康检查，公开返回服务状态，客户端通过受保护的 `/api/health` 测试密钥。设备 ID 仍是客户端填写的路由标识，共享密钥持有者可以使用任意设备 ID。日志不打印密钥、Shell 命令、通知正文或结果内容。

## 本次验证

10 项集成测试和语法检查通过，覆盖 API 密钥对 HTTP 和 WebSocket 的校验、未配置密钥时拒绝业务请求、Railway 健康检查可用，以及现有指令往返、心跳、拒绝非法和重复结果、重启持久化、超时与清理、连接检测和截图传输。尚未在你的 Railway 账号实机验证安卓客户端。



## 屏幕截图（0.2.0）

新版接收 `get_screenshot`，通过 HTTP 结果通道回传最多 200000 字节的 JPEG。截图结果最多 300000 个 JSON 字符，普通结果仍最多 48000。图片在完成后 10 分钟从查询结果中移除，元数据仍按 24 小时清理；重启不延长保留时间。服务端不决定截图权限或黑名单，接收手机在执行前后检查。双方需使用新版 APK，原服务端需要更新部署。
