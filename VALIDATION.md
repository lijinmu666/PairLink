# 验证记录

2026-10-09：本仓库仅保留 Android 客户端和 Railway 服务端。已清理旧服务端及其交叉测试依赖。

- 开关开启前检查系统通知权限、通知渠道或通知访问权限；返回系统设置后重新核验。
- 特权模式仅在授权成功后保存；失败展示错误弹窗。
- 应用暂停、远程 Shell、黑名单和紧急恢复的授权边界保留。
- 本次将运行 Android release 编译、lint、ShellRunner 检查与 Railway 测试；实际结果以 GitHub Actions 构建日志为准。
- 仍需要手机实测厂商权限页面、root/Shizuku/Dhizuku 授权、通知显示及后台连接。

