# PairLink 0.1.0

供双方主动安装、授权和开启共享的 Android 手机指令项目。一个仓库包含 `android/` 客户端与 `worker/` Cloudflare 服务端。

## 第一版

- 两台手机安装同一 APK，双方设备 ID 互相填写。
- 查看对方是否可连接、电量和充电状态。
- 查看最近 1 小时使用过的应用，每个应用显示最近一次打开时间，最多 50 条。
- 单次远程 Shell：标准输出、错误输出、退出码、超时和截断标记。
- 本机主动开启 Shell；root 必须单独请求本机 root 管理器授权。关闭 Shell 同时关闭 root。
- 前台通知、暂停共享、WebSocket 心跳与断线重连、DoH 失败时回退系统 DNS。
- 请求结果保留 24 小时后自动清理。第一版没有 MCP、OAuth、API Key 或账号。

## 部署 Worker

在 Cloudflare 创建 Worker，使用本仓库并将项目根目录指定为 `worker`。构建命令 `npm ci`，部署命令 `npx wrangler deploy`。Durable Object 的绑定与初始化迁移已在 `wrangler.jsonc` 中配置；不需要 D1。

也可以在云电脑运行：

```bash
cd worker
npm ci
npx wrangler login
npm run deploy
```

用 `https://你的Worker地址/health` 检查部署。客户端填写 HTTPS 根地址，不含 `/health`、`/mcp` 或查询参数。

本版只实现 Cloudflare Worker，不包含 Railway 服务端。

## 编译 APK

要求 Android 8.0 或更高版本。使用 JDK 17、Gradle 8.9、Android SDK 35。

仓库包含 Gradle Wrapper 8.9，可以直接用 Android Studio 打开 `android/`，等待依赖同步后构建 APK。也包含可选的 GitHub Actions 编译流程；不要求使用它。

也可以用 Android Studio 打开 `android/`，选用 Gradle 8.9 编译。命令行：

```bash
cd android
./gradlew assembleDebug
```

输出路径：`android/app/build/outputs/apk/debug/app-debug.apk`。调试签名仅用于首版测试；后续更新 APK 应维持同一签名，不同 CI 运行的调试签名可能不同。

## 两台手机设置

1. 两台手机都安装 APK。
2. 填写同一个服务器根地址。
3. 假设 A 的自己的 ID 是 `alice`，对方 ID 填 `bob`；B 的自己的 ID 填 `bob`，对方 ID 填 `alice`。
4. 保存后授予通知权限，再手动开启“允许对方查询此手机”。未保存的文本不会用于连接。
5. 使用“开启应用使用情况访问权限”前往系统设置，给 PairLink 授权。
6. 需要 Shell 的手机开启本机 Shell 开关。root 由被操作手机单独请求授权；普通模式不会自动升级为 root。
7. 在另一台手机查询状态、应用或运行 `id`、`pwd` 等测试命令。
8. 暂停共享会停止服务并尝试终止本机当前 Shell。设置页修改服务器和设备 ID 前需要暂停共享。

应用不隐藏图标、不自动申请 root、不在开机时自动恢复共享。系统回收进程后可能重启前台服务，但强行停止应用后必须手动打开。厂商省电策略仍可能断开连接；按实际手机情况设置允许后台运行。本版没有持续 WakeLock。

## 连接与权限边界

所有指令通过服务器路由，APK 本地检查共享开关和设置的对方 ID。**ID 校验仅用于路由与防止误配，不是身份验证。知道地址和 ID 的人可能冒充发送方、占用设备连接、读取结果或提交命令。开启 root Shell 后这些请求可能以 root 执行。** 这是当前无验证版本的实际行为。

最近使用记录来自安卓使用情况接口，不等于多任务卡片列表。应用名可能因包可见性限制而显示包名；第一版不请求查询所有安装包的权限。

Shell 使用 `sh -c`，root 使用 `su -c`，在 APK 自身环境运行，不等同于 ADB shell。它是一次性命令，不提供交互、stdin、文件传输或终端会话。Shell 超时默认 20 秒，服务端最多等待 45 秒。两路输出各保留前 12000 字节。

超时或关闭开关只能尽力终止执行进程；命令已经造成的修改不会撤回，命令自行启动的独立后台进程也可能继续运行。请求超时、回传失败或退出界面后，不要直接重发有副作用的命令。接收方持久记录近期指令 ID，尽量避免重复执行。

DoH 仅改善 DNS 解析，不保证中国内地的 HTTPS/WebSocket 可达性。暂不涉及域名配置。

## 验证

```bash
cd worker
npm test
npm run check
```

`VALIDATION.md` 记录这次实际执行的验证和仍需手机实测的内容。

接口协议见 `PROTOCOL.md`。以后 MCP 工具可调用相同的提交与结果查询接口，无需重新设计 Android 通道。
