# 验证记录

2026-10-10：已使用仓库最新文案编译 Android release，包含暂停应用预勾选、恢复权限提示、合并后的概览信息、可刷新的服务端版本号及 root 警告弹窗。

- 构建提交：`91209c4f7adc9d4ddd02c8d47768d59a7c6062a1`。
- Android assembleRelease 和 lintRelease 通过。
- ShellRunner 测试通过。
- Railway 测试及源码检查通过。
- release APK 使用原签名密钥；v2、v3 签名验证通过，证书与上一版相同。
- [本次 GitHub Actions 构建](https://github.com/lijinmu666/PairLink/actions/runs/38051064651)。
- 构建后恢复手动触发的编译流程，签名密钥及密码未上传仓库。
- 仍需手机实测概览刷新、应用选择器预勾选、root 确认及各特权方式的暂停与恢复。

2026-10-09：本仓库仅保留 Android 客户端和 Railway 服务端。已清理旧服务端及其交叉测试依赖。

- Android release 编译和 lintRelease 通过。
- ShellRunner 检查通过。
- Railway 测试及源码检查通过。
- 权限开关、特权模式申请、通知渠道检查的 Java/Kotlin 源码语法检查通过。
- [本次 GitHub Actions 构建](https://github.com/lijinmu666/PairLink/actions/runs/37927273895)。
- 仍需要手机实测厂商权限页面、root/Shizuku/Dhizuku 授权、通知显示及后台连接。
