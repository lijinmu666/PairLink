# PairLink Android 0.2.0 / Worker 0.1.0

供双方主动安装、授权和开启共享的 Android 手机指令项目。一个仓库包含 `android/` 客户端、`railway/` Railway 服务端和保留的 `worker/` Cloudflare 服务端，两个后端沿用相同客户端接口。

Android 0.2.0 将界面迁移为 Compose + Miuix 0.9.3：默认蓝色主题、系统深浅色、按钮、输入框、开关、卡片、进度条及弹窗。已设置白底小猫应用图标，结果页面提供箭头返回；前台服务保留系统要求的通知小图标。新增的通知查询、应用暂停及 Shizuku Shell 需要重新部署仓库中的 Worker。

## 第一版

- 两台手机安装同一 APK，双方设备 ID 互相填写。
- 查看对方是否可连接、设备型号、安卓版本、开机时长、当前屏幕应用、电量和充电状态。
- 查看对方已安装的用户应用。
- 查看最近 24 小时使用过的应用，每个应用显示最近一次打开时间，最多 50 条。
- 查看对方通知中心当前的通知：应用名、包名、标题、正文和通知时间。通知共享默认关闭，需要接收方单独开启并授予系统通知访问权限。
- 向对方发送普通系统通知；接收方单独开启发送授权。
- 单次远程 Shell：普通应用、root 或 Shizuku/Sui 执行；标准输出、错误输出、退出码、超时和截断标记。
- 暂停和恢复指定包名的应用：被操作手机选择设备所有者、Shizuku/Sui 或 root，独立授权开关默认关闭；提供本机恢复入口。
- 本机主动开启 Shell；root 必须单独请求本机 root 管理器授权。关闭 Shell 同时关闭 root。
- 前台通知、暂停共享、WebSocket 心跳与断线重连、DoH 失败时回退系统 DNS。
- 请求结果保留 24 小时后自动清理。第一版没有 MCP、OAuth、API Key 或账号。

## 部署 Railway

部署步骤见 [railway/README.md](railway/README.md)。在 Railway 从本仓库创建服务，Root Directory 设置为 `/railway`，使用 Dockerfile 自动构建；添加挂载至 `/data` 的 Volume，并设置 `DATA_DIR=/data`，健康检查路径 `/health`。保持单实例并关闭自动休眠，然后生成服务域名。双方手机暂停共享，填写同一个 Railway HTTPS 根地址并保存，重新开启共享即可，不需要重新编译 APK。

Cloudflare Worker 完整保留；两个后端的指令记录独立，可通过更换客户端服务器地址切换。

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

Railway 和 Cloudflare Worker 可二选一，不需要同时部署。

## 编译 APK

要求 Android 8.0 或更高版本。使用 JDK 17、Gradle 9.7.1、Android SDK 37.1 和 Build Tools 36.0.0。

仓库包含 Gradle Wrapper 9.7.1，可以直接用支持 AGP 9.3 的 Android Studio 打开 `android/`，等待依赖同步后构建 APK。也包含可选的 GitHub Actions 编译流程。

命令行：

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

## 查看对方当前通知

1. 更新双方 APK，并重新部署 Worker；旧版 Worker 不支持 `get_notifications`。
2. 被查询手机在“共享与授权”中开启“允许对方查看通知”，确认后在系统设置中允许 PairLink 的通知访问。
3. 双方开启共享，查询方点击“查看对方当前通知”，在 Miuix 弹窗中查看结果，等待时保留无限进度条。
4. 关闭通知共享或撤销系统通知访问权限后，新的通知查询会被拒绝。系统授权按钮在已授权时隐藏，返回应用和前台每秒检测时刷新。

读取的是系统当前可提供的通知，不保存本地通知历史，也不定时上传。普通通知、展开文本和消息通知尽量提取文字；图片、自定义布局和系统隐藏的敏感内容可能没有可读取正文。Android 15 及以上可能对验证码通知脱敏。正文每条最多 2048 字符，标题最多 256 字符，结果总量控制在现有协议上限内；超出时明确显示截断及返回条数。已经查询到的结果仍按服务器原有规则保留最多 24 小时，关闭开关不会删除既有结果。

## 设备状态和普通通知

状态查询会返回品牌与型号拼接值，例如 `ZYB ZPD1321)，并显示安卓版本、开机时长和最近记录的前台应用。前台应用和应用使用列表需要使用情况访问权限；没有授权时会显示未知或提示授权。

发送普通通知需要发送方填写标题和内容，被操作手机开启发送授权并授予系统通知权限。通知使用普通重要性，不提供静默或紧急类型。系统关闭通知或权限不足时，请求会失败。

## Shizuku、Sui 与设备所有者

Shizuku/Sui 集成采用官方 API 13.1.5 的 Provider、权限申请和 UserService。安装并启动 Shizuku，或安装并启用 Sui 后，在 PairLink 中开启“允许使用 Shizuku / Sui”，确认并授予权限。状态显示实际 UID：ADB 启动通常为 2000，root/Sui 为 0。Shizuku 服务未启动、权限撤销或本机开关关闭时会拒绝执行，不会自动改用 root。Shell 仍须单独开启“允许对方执行 Shell”，发送方可选择本次使用 Shizuku/Sui，与 root 请求互斥。

设备所有者模式只用于应用暂停接口，不提供任意 Shell。PairLink 自身必须是设备所有者；普通设备管理员或其他应用拥有设备所有者身份并不满足条件。应用提供说明，不能通过普通权限弹窗直接获得此身份。满足系统配置条件时可通过 ADB 设置：

```bash
adb shell dpm set-device-owner com.lijinmu.pairlink/.PairLinkAdminReceiver
```

设备已有账号或其他设备所有者等情况下系统可能拒绝，请按系统实际返回处理；应用不会自动执行此命令。

## 暂停与恢复应用

1. 被操作手机在“应用暂停与恢复”中选择执行方式。设备所有者必须已激活；Shizuku/Sui 必须运行、授权并开启本机开关；root 需要已开启 Shell 并单独授权 root。
2. 被操作手机主动开启“允许对方暂停和恢复应用”，双方共享在线。
3. 发送方填写目标应用包名，点击“暂停对方应用”或“恢复对方应用”，确认后执行；结果仍在 Miuix 弹窗中显示，等待时保留无限进度条。
4. 暂停使用设备所有者的 `setPackagesSuspended` 或当前用户的 `pm suspend`，恢复使用对应接口或 `pm unsuspend`。返回实际暂停状态；系统拒绝、命令失败和未确认状态都会明确提示。
5. 本机保存暂停尝试的包名、执行方式和 Shizuku 身份，以便请求超时或结果丢失时仍可恢复。恢复优先使用记录中的原执行方式；执行身份改变时应先恢复原身份，避免跨暂停来源误报成功。
6. “恢复本机由 PairLink 暂停的应用”不依赖服务器在线或共享开关，可恢复本机记录的暂停/可能已执行请求。此操作仍需要原来的系统权限或特权服务可用。恢复后若仍有其他暂停来源，结果会提示仍处于暂停状态。
7. 关闭暂停授权、关闭共享或切换方式不会自动撤销已发生的暂停。桌面、设备管理员等系统保护应用可能无法暂停；禁止暂停 PairLink 本身，以保留恢复入口。

官方参考：[Shizuku API](https://github.com/RikkaApps/Shizuku-API)、[设备所有者应用暂停接口](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setPackagesSuspended(android.content.ComponentName,%20java.lang.String[],%20boolean))。

## 连接与权限边界

所有指令通过服务器路由，APK 本地检查共享开关和设置的对方 ID。**ID 校验仅用于路由与防止误配，不是身份验证。知道地址和 ID 的人可能冒充发送方、占用设备连接、读取结果或提交命令。开启 root Shell 后这些请求可能以 root 执行。** 这是当前无验证版本的实际行为。

最近使用记录来自安卓使用情况接口，不等于多任务卡片列表。已声明应用查询可见性来读取已安装应用的名称；无法取得名称时显示包名。

普通 Shell 使用 `sh -c`，root 使用 `su -c`，在 APK 自身环境运行；Shizuku/Sui 在 UserService 的 ADB/root 身份进程中使用 `sh -c`，实际权限依赖启动方式。它是一次性命令，不提供交互、stdin、文件传输或终端会话。Shell 超时默认 20 秒，服务端最多等待 45 秒。两路输出各保留前 12000 字节。

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
