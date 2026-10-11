# 验证记录

2026-10-11（截图焦点查询修复，未编译 APK）：

- 根据设备诊断确认，原 `dumpsys window windows` 输出没有 `mCurrentFocus`，而活动输出的显示屏信息中存在与前台一致的焦点窗口，导致旧查询一直拒绝截图
- 改为优先执行 `dumpsys window displays`；仅在未返回焦点字段时查询旧版 `dumpsys window windows`，显式空焦点或不一致不触发回退
- 缺失焦点字段时给出具体原因；保留单一前台、焦点一致、黑名单、亮屏／解锁、工作模式和前后状态检查
- ScreenshotPolicyTest 通过，覆盖显示屏格式、Unicode Activity 类名、缺失字段、空焦点、不一致和多窗口拒绝
- 本地使用用户诊断输出复现旧查询失败，并验证其中显示屏焦点格式能正确匹配前台；原始诊断文件未提交仓库
- Java 语法检查通过；尚未进行 APK 构建或 Sui 实机截图验证

2026-10-11（界面动画源码更新，未编译 APK）：

- 四个主页、结果／记录详情和应用选择器增加弹簧滑动及淡入淡出，返回采用反向过渡
- 指令定时通知间隔菜单、应用使用权限入口及加载进度条增加展开／收起动画
- 应用选择器及结果列表使用稳定键和列表动画，弹窗由单一顶层节点管理，避免页面过渡时重复弹出
- Kotlin 语法检查通过；检查每页滚动状态及结果页面快照独立，列表筛选保留勾选状态，通知间隔仍由原开关控制
- 添加由既有 Compose BOM 管理的 animation 依赖；未进行 Gradle 编译或手机动画实测
- 上一条 release 构建不包含本次动画更新

2026-10-11（release APK 构建完成）：

- 本次 APK 包含截图与截图黑名单、连接和执行记录详情、定时通知、Shell 跟随工作模式及文案和结果弹窗对齐更新
- 构建提交：`1465c4723c88fed96222a7f3617437a4861e9c13`
- Android assembleRelease、lintRelease、ShellRunner、SummaryWindow、ShellPolicy、ScreenshotPolicy 和 BinaryCommand 检查通过
- Railway 9 项测试与源码检查通过
- 使用原签名密钥，v2／v3 校验通过，证书与上一版相同
- APK SHA-256：`95b7e79b932a72f251f1f2f1d912dd4abf1b7ae85843e8c7c59bccda3dd3a11a`
- [GitHub Actions 构建](https://github.com/lijinmu666/PairLink/actions/runs/38101791326)
- 构建后恢复手动触发流程，密钥和密码未上传仓库
- 尚需手机实测截图前台检测、Shizuku Binder 回传、黑名单及授权撤销，以及定时通知与界面显示

2026-10-11（屏幕截图源码更新，未编译 APK）：

- 添加本机截图授权开关及截图黑名单入口，位于应用暂停开关上方；本机选择器支持系统／用户应用、搜索、多选和保存。
- root 和 Shizuku/Sui 截图；检查单一实时前台应用、焦点窗口、黑名单、亮屏／未锁屏、工作模式、截图授权及 Shizuku UID；检查失败或前台变化时拒绝回传。
- 截图在内存中转换为不超过 200000 字节的 JPEG，经现有 HTTP 通道回传，并显示图片卡片；本机执行历史不保存图片。
- Railway 升级到 0.2.0，截图查询图片 10 分钟过期，元数据仍保留 24 小时。
- ScreenshotPolicyTest 通过：Android 状态格式、未知前台、空焦点、通知栏／窗口不一致、分屏、黑名单命中及截图期间应用变化。
- 既有 ShellRunner 6 项检查通过。
- BinaryCommandTest 通过：二进制流不被修改、输出上限、权限撤销、超时、失败与恢复执行。
- Railway 9 项测试和源码检查通过：包括大图回传、200000 字节边界、无效图片／执行方式、拒绝结果、重启和图片过期。
- Java/Kotlin 语法检查及 AIDL 方法对应检查通过；未进行 Gradle 编译或手机验证，需实测各厂商的前台格式、截图权限、Shizuku Binder 回传及界面显示。

2026-10-11（界面文案与弹窗对齐源码更新，未编译 APK）：

- 移除界面文案、权限说明、错误提示及本机定时通知的句末句号；多句说明使用换行分隔。
- 完成后的执行结果弹窗左对齐，包含测试连接 JSON 和 Shell 输出；普通提示、确认和加载提示仍居中，无限进度条保留。
- 未对 JSON、用户输入或回传原始文本做替换，保持版本号、包名、命令和输出原样。
- Java/Kotlin 语法检查通过；未进行 Gradle 编译或手机验证。

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
