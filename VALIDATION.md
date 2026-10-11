# 验证记录

2026-10-11（Shell 权限规则源码更新，未编译 APK）：

- 移除接收端 root／Shell 权限开关及发送端权限选择，保留远程 Shell 总开关。
- Shell 跟随接收手机工作模式：无和 Dhizuku 使用普通应用权限，Shizuku / Sui 使用其启动身份，root 使用 root。
- 开启 Shell 时检查并申请所选特权模式；执行期间检查固定模式、总开关、Shizuku 授权和 UID，拒绝跨权限执行或自动降级。
- 旧请求的 root/shizuku 参数不影响新接收端的权限选择；现有 Railway 无需变更。
- Java/Kotlin 语法检查、ShellPolicy 权限／模式切换测试和既有 ShellRunner 测试通过。
- README 和协议已更新；尚未进行 Gradle 编译或手机验证，需双方更新客户端后实测授权申请、执行及模式切换取消。

2026-10-11（源码更新，未编译 APK）：

- 查询页新增连接与执行记录入口；记录卡片可进入详情页。
- 本机 SQLite 保存双向指令、执行方式和结果；定时通知只统计匹配设备 ID 的入站指令，含查询和失败请求，不代表全部成功。
- 新增指令定时通知开关、间隔菜单与通知点按跳转；使用 WorkManager 2.11.2。
- 本次修改的 Java/Kotlin 源码语法检查通过。
- SummaryWindow 测试通过：间隔边界、延迟发送、时间回拨、24 小时保留上限及无效间隔。
- 尚未进行 Gradle 编译或手机验证；需实测通知权限引导、后台周期任务、通知跳转、数据库旧记录迁移和详情展示。
- 下方 2026-10-10 的 APK 构建结果不包含本次功能。

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
