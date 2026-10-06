# Android 0.2.0 验证记录

日期：2026-10-06（北京时间）。

- GitHub Actions `assembleDebug lintDebug` 通过：[构建记录](https://github.com/lijinmu666/PairLink/actions/runs/37419860034)，源码提交 `671aa59573b66fbc12d67c7013b7763ecf5427bb`。
- 同次构建中的 ShellRunner 检查、Worker 6 项测试及部署 dry-run 均通过。
- 界面源码改为 Compose/Miuix 0.9.3，无 Android View 控件、原生 AlertDialog 或 Toast；不设置自定义应用图标或界面图标。系统权限页面与 root 管理器授权窗口由系统或对应管理器提供。
- 默认蓝色主题、系统深浅色模式；对话框内容可滚动，结果支持选择复制。
- APK ZIP 完整性检查通过，包含 Manifest 和 DEX。SHA-256：`ada8a8be0f3648eb8094044529737aa6e53acc126307118f3775d4364b46a405`。
- 本次 CI 的调试签名与已交付的 0.1.0 不同，无法直接覆盖安装。需先记录服务器地址与双方 ID，卸载旧版后重新安装、配置与授权。
- 尚未在手机或模拟器上验证新界面的实际显示、键盘和弹窗交互。

# 0.1.0 验证记录

日期：2026-10-06（北京时间）。

## 已实际通过

- Android：GitHub Actions 执行 `assembleDebug lintDebug` 成功，生成可安装的调试 APK（Android 8.0 及以上）。[构建记录](https://github.com/lijinmu666/PairLink/actions/runs/37390280738)，源码提交 `640ed4922de13c2df52ce5a46a67e33b8a6ef9d7`。

- Worker：6 项测试通过，包括真实本地 Workers 运行时中的 WebSocket 连接、离线请求、指令发送、忙碌拒绝、结果回传、设备 ID 不匹配拒绝、重复回传拒绝、设备权限拒绝结果，以及参数与期限校验。
- Worker：`wrangler deploy --dry-run` 成功，Durable Object 绑定与脚本打包成功。
- ShellRunner：Java 17 下 6 项检查通过：标准输出/错误输出/退出码、关闭权限拒绝、输出截断、超时、撤销开关取消进程，以及取消后执行器恢复。

## 尚未验证

- 用户已反馈 0.1.0 两台手机连接、最近应用查询，以及 `whoami` 返回 root 正常；前台通知、后台省电限制、网络切换和开关拒绝行为仍需验收。
- 用户已成功部署 Worker 到真实 Cloudflare 账户。
- JVM Shell 测试使用 Linux `sh`，不能替代 Android 的 `sh`/`su` 实测。

## 手机验收顺序

1. 双方配置相反的设备 ID，开启共享，测试状态和电量。
2. 关闭使用情况权限后查询应失败；授权后显示最近使用记录。
3. Shell 默认关闭时远程请求应失败；开启后测试 `id`、`pwd`。
4. 测试向标准错误输出和非零退出码，确认界面区分显示。
5. 单独开启 root 并通过本机管理器授权，运行 `id` 应显示 uid=0；拒绝授权不得开启 root 开关。
6. 执行 `sleep 10` 期间关闭本机 Shell 开关，确认进程被取消；命令自行启动的独立子进程不保证停止。
7. 暂停共享，另一端应提示离线；重新开启后应恢复查询。
8. 在 Wi-Fi/流量切换、锁屏和后台情况下测试重连。
