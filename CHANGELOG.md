# 提交与变更记录

整理范围：初始化至 `2eb1418d17f0a38e98c7576b24999a99b105904b`，共 92 次提交，时间按北京时间显示

## 最新待验证变更

- 页面切换、结果页返回、菜单展开收起及列表筛选动画
- 修复现代 Android 截图焦点查询：优先读取显示屏焦点，保留黑名单与权限检查
- 增加“允许对方查看应用使用情况”开关，默认关闭；权限按钮移至开关下面
- 移除 Shell 输入与权限提示之间多余间距
- 保留最近手动修改的权限说明、确认按钮和错误提示

## 功能演进

| 阶段 | 主要变化 |
| --- | --- |
| 初版 | Android 客户端与服务端连接、远程查询和 Shell |
| Miuix 界面 | 默认蓝色主题、加载弹窗、卡片结果和四个主页 |
| 查询功能 | 24 小时应用使用时长、通知、媒体、设备型号和息屏状态 |
| 应用控制 | Shizuku / Sui、Dhizuku、root，应用选择器、暂停黑名单与恢复 |
| Railway | 添加 Railway 后切换为唯一服务端，移除旧 Worker 和安全 DNS |
| 权限与记录 | 开启时检查权限、概览版本刷新、指令详情与定时汇总通知 |
| 最新功能 | Shell 跟随接收端工作模式、屏幕截图与截图黑名单、过渡动画与使用情况授权 |

## 完整提交索引

提交按用途归类，每组按时间从新到旧排列，原提交链接可查看具体差异

### 功能、界面与修复

| 北京时间 | 提交 | 内容 |
| --- | --- | --- |
| 2026-10-11 12:32 | [2eb1418d](https://github.com/lijinmu666/PairLink/commit/2eb1418d17f0a38e98c7576b24999a99b105904b) | 更新 MainActivity.java |
| 2026-10-11 12:12 | [61c2f3f0](https://github.com/lijinmu666/PairLink/commit/61c2f3f07e616a157439ce110504f62e5a1cf8fa) | Remove extra spacing above Shell permission hint |
| 2026-10-11 12:06 | [ea39335d](https://github.com/lijinmu666/PairLink/commit/ea39335ddb226d5cd3d536890c617ca1e1d52dc9) | Add consent switch for remote app usage queries |
| 2026-10-11 10:22 | [32e48435](https://github.com/lijinmu666/PairLink/commit/32e484351f62e3671a9df3ce8e9346d2a02b5b64) | Read screenshot focus from display dumps on modern Android |
| 2026-10-11 09:50 | [6eb442a3](https://github.com/lijinmu666/PairLink/commit/6eb442a30b875fa4997bda91b2c7bd3ccbdcc952) | Add smooth page, disclosure and list transitions |
| 2026-10-11 09:18 | [d9d3a28b](https://github.com/lijinmu666/PairLink/commit/d9d3a28b6fed3240fedd0d549051959f17179d8b) | Add consented remote screenshots with a local app blacklist |
| 2026-10-11 08:59 | [480e130e](https://github.com/lijinmu666/PairLink/commit/480e130e32597973db3035eb2959d40d1d917042) | Remove full stops from app copy and left-align callback results |
| 2026-10-11 08:47 | [55766dc8](https://github.com/lijinmu666/PairLink/commit/55766dc8ef7b005c99142190600c1e328029d9de) | Make remote Shell follow the receiver's privileged work mode |
| 2026-10-11 00:46 | [801ee4d0](https://github.com/lijinmu666/PairLink/commit/801ee4d028b8d3839a959428228b7c1c214af607) | 查询页提供连接与指令详情记录，并新增本机定时通知汇总 |
| 2026-10-10 20:08 | [456de69a](https://github.com/lijinmu666/PairLink/commit/456de69adcaf11b9c53b6735aa8e38500e98a0eb) | 更新 MainActivity.java |
| 2026-10-10 19:50 | [c2d69fa6](https://github.com/lijinmu666/PairLink/commit/c2d69fa6496cd3b81aca93a389f179e653f49824) | 选择 root 工作模式前显示风险警告并要求确认 |
| 2026-10-10 19:45 | [a613573c](https://github.com/lijinmu666/PairLink/commit/a613573c4f85549eb18c3c394161f022335bb0d7) | 合并概览设备信息并支持点击刷新服务端版本号 |
| 2026-10-10 19:32 | [45a4256c](https://github.com/lijinmu666/PairLink/commit/45a4256c07c225d973d159601678e233c105e08c) | 在特权工作模式下提示应用恢复需保留原暂停权限身份 |
| 2026-10-10 19:17 | [141aff0d](https://github.com/lijinmu666/PairLink/commit/141aff0d646b465a437c6483b88db5c8bc61cd17) | 应用选择器预勾选由 PairLink 记录且仍暂停的对方应用 |
| 2026-10-09 19:41 | [f9a87deb](https://github.com/lijinmu666/PairLink/commit/f9a87deb3c0625efdba8bbbe0ec06cc7d2f6c65e) | 更新 PairLinkUi.kt |
| 2026-10-09 19:31 | [12bdfcdc](https://github.com/lijinmu666/PairLink/commit/12bdfcdc460c37a6511ae0566abbdb7a2f199ed6) | 重排设置页面，合并权限板块并独立特权工作模式与指令授权 |
| 2026-10-09 18:22 | [5cc65b11](https://github.com/lijinmu666/PairLink/commit/5cc65b11b14f6b4e6354bbc62a14a1f45c2de0df) | 缺少应用名显示棍母，支持应用内切换状态通知，弹窗正文居中 |
| 2026-10-08 23:51 | [df2dc182](https://github.com/lijinmu666/PairLink/commit/df2dc18290ef247e27a48f22a34cb6f013516003) | 修复应用选择器暂停确认弹窗和加载进度无法显示 |
| 2026-10-08 20:05 | [f94732db](https://github.com/lijinmu666/PairLink/commit/f94732db0e4ab248604b82fe9c13e5a7b5e4935b) | 添加本机应用暂停黑名单及搜索多选管理入口 |
| 2026-10-08 19:57 | [1bc8a164](https://github.com/lijinmu666/PairLink/commit/1bc8a164f335164fc95ea4efc54d126dfb371c92) | 添加对方应用选择器，支持搜索、多选及逐项暂停恢复 |
| 2026-10-08 19:41 | [9070b9e2](https://github.com/lijinmu666/PairLink/commit/9070b9e2497d36d998b14cf68d2cfdbcc75ef6d4) | 设备状态增加息屏信息，息屏时跳过前台应用查询 |
| 2026-10-08 19:28 | [3ade1105](https://github.com/lijinmu666/PairLink/commit/3ade1105d4d2defa46a555d926c7870817e8b1fd) | 接入 Dhizuku 替代设备所有者，保护系统应用并添加夜间模式菜单 |
| 2026-10-08 07:17 | [86f52930](https://github.com/lijinmu666/PairLink/commit/86f52930ae148f0487138d63f81ecbfcf6119a54) | 移除安全 DNS 功能，统一使用系统 DNS |
| 2026-10-07 17:31 | [08d4af0e](https://github.com/lijinmu666/PairLink/commit/08d4af0edca52de9e917710208b27ab7779dd678) | 修正应用图标文件扩展名以兼容Release资源处理 |
| 2026-10-07 17:10 | [b1a904da](https://github.com/lijinmu666/PairLink/commit/b1a904da1196a5b7b8cc4b4ea7caa1de84425fdf) | 新增系统媒体播放查询与独立授权并兼容两个后端 |
| 2026-10-07 14:15 | [ddf3c563](https://github.com/lijinmu666/PairLink/commit/ddf3c5630459a8ba204834e7a52d66bb903d8a52) | 新增兼容安卓客户端的Railway服务端并保留Cloudflare Worker |
| 2026-10-07 09:37 | [da9ebf33](https://github.com/lijinmu666/PairLink/commit/da9ebf33dc0587fbbfd0c547a555eba9d6f0a5fc) | 将客户端版本号移至概览本机信息并保留彩蛋触发 |
| 2026-10-07 09:30 | [77f86e80](https://github.com/lijinmu666/PairLink/commit/77f86e806735fe4e75a54eef6541274077e7657d) | 设置页新增安全DNS与后台状态通知开关 |
| 2026-10-07 03:08 | [ba80ca10](https://github.com/lijinmu666/PairLink/commit/ba80ca10802f001ba1a57a8ed5d18abe3b5b367c) | 修复自适应图标白色背景资源引用 |
| 2026-10-07 03:04 | [4e764550](https://github.com/lijinmu666/PairLink/commit/4e764550334547ca55a7ecf398ef17ac465c12c1) | 使用白底小猫表情图片作为 PairLink 应用图标 |
| 2026-10-07 02:55 | [21a20ab4](https://github.com/lijinmu666/PairLink/commit/21a20ab42e4e9d67e8d7739a03452b09493d4ba9) | 连续点击版本号五次后下次启动显示一次表白彩蛋 |
| 2026-10-07 02:55 | [9984b5d6](https://github.com/lijinmu666/PairLink/commit/9984b5d6efb6a781e2d243e9642cf90fb4f25532) | 设置页新增可点击的版本号卡片 |
| 2026-10-07 02:47 | [f8e93def](https://github.com/lijinmu666/PairLink/commit/f8e93def7621ae3ebb751540b37d961af97b5d41) | 更新 PairLinkUi.kt |
| 2026-10-07 02:15 | [3acede10](https://github.com/lijinmu666/PairLink/commit/3acede1012bf503626a094f7d21d88b5c43d5250) | 更新概览权限模式并随授权状态刷新 |
| 2026-10-07 02:15 | [a2e549a2](https://github.com/lijinmu666/PairLink/commit/a2e549a24464c6c2ddee09e53a7a24924ba60b0d) | 新增概览分页并展示本机状态与基本信息 |
| 2026-10-07 02:05 | [6864d34d](https://github.com/lijinmu666/PairLink/commit/6864d34db97885d4f9cf685464b3066ec8fae429) | 更新 PairLinkUi.kt |
| 2026-10-07 01:54 | [13f1cff5](https://github.com/lijinmu666/PairLink/commit/13f1cff5b1cc97c15662650cf51856a3e7e84ef7) | 删除设置页底部共享与功能说明 |
| 2026-10-07 01:53 | [4ccab21a](https://github.com/lijinmu666/PairLink/commit/4ccab21af5cd25ca9567d744975a4dd1cd73082a) | 将连接合并到设置并调整为查询操作设置三个分页 |
| 2026-10-07 01:51 | [8ee9687b](https://github.com/lijinmu666/PairLink/commit/8ee9687b990f69d8673f8dc0ccd3c58ffe3dad34) | 主页面顶部标题固定显示 PairLink |
| 2026-10-07 01:48 | [7883a113](https://github.com/lijinmu666/PairLink/commit/7883a1139570592e6111c16a4e6c4f4d1c131c9e) | 顶部大小标题统一跟随当前页面 |
| 2026-10-07 01:33 | [fac0ac03](https://github.com/lijinmu666/PairLink/commit/fac0ac0320d48d098ffadb3bb3501358d18a39ca) | 功能分为连接、查询、操作和设置四个页面 |
| 2026-10-07 01:22 | [de78fe73](https://github.com/lijinmu666/PairLink/commit/de78fe73e74b0fb1c07c649c6c7161818c9457e5) | 结果页返回按钮改为箭头图标 |
| 2026-10-07 01:12 | [7ef21601](https://github.com/lijinmu666/PairLink/commit/7ef21601841669646aadd3d06c451197d4bdbdd9) | 查询完成后进入结构化结果页，错误和 Shell 输出保留弹窗 |
| 2026-10-07 01:12 | [20a8a007](https://github.com/lijinmu666/PairLink/commit/20a8a007c558d8f6ca99633351345d38f99ea520) | 查询结果使用 Miuix 卡片页面，保留加载弹窗与返回导航 |
| 2026-10-07 00:44 | [8db7096e](https://github.com/lijinmu666/PairLink/commit/8db7096ebe50d4391ae6a12bfd557579675596ca) | 应用暂停执行方式改为 Miuix 下拉菜单 |
| 2026-10-07 00:43 | [4bc5d32a](https://github.com/lijinmu666/PairLink/commit/4bc5d32aa6ea6817ff2bb7a2851df6b5641a94ba) | 显示应用使用时长与最近打开时间 |
| 2026-10-07 00:43 | [93b66b1c](https://github.com/lijinmu666/PairLink/commit/93b66b1cfa653dcb833ebae4ae7379faa6bd54d1) | 增加滚动24小时应用前台使用时长 |
| 2026-10-06 23:06 | [83d34717](https://github.com/lijinmu666/PairLink/commit/83d3471745883c8d94c877a7dd493fd8f0be5c4d) | 确保远程指令失败时返回明确错误 |
| 2026-10-06 23:06 | [98c97871](https://github.com/lijinmu666/PairLink/commit/98c97871d7524cb554e22e4142aef1b1f25f57e3) | 补充 Shizuku 失败原因并避免复用失效服务 |
| 2026-10-06 23:06 | [23830ab0](https://github.com/lijinmu666/PairLink/commit/23830ab04d5bce627b343c31c69b3faa243ea6ff) | 仅在应用暂停确认成功后记录恢复项 |
| 2026-10-06 22:11 | [897dc6d8](https://github.com/lijinmu666/PairLink/commit/897dc6d839a0d93cd2aef6ec93082365812780d7) | 消息通知使用声音和顶部横幅，不绕过静音与勿扰 |
| 2026-10-06 17:37 | [afd1d0ae](https://github.com/lijinmu666/PairLink/commit/afd1d0ae3bb16ddc09f0ff164cb3464aacdd7c89) | 标注已安装应用查询权限用途 |
| 2026-10-06 17:22 | [7fd73189](https://github.com/lijinmu666/PairLink/commit/7fd7318945e17ff9857dc302c3e9d2e8b732d795) | 补齐设备信息、用户应用查询和普通通知 |
| 2026-10-06 16:36 | [a2998b65](https://github.com/lijinmu666/PairLink/commit/a2998b65b6e1888478a2de4bdc941495eade8493) | 兼容 Shizuku Sui 与设备所有者，新增应用暂停和恢复 |
| 2026-10-06 16:24 | [32d7b0b8](https://github.com/lijinmu666/PairLink/commit/32d7b0b8f07e85b8b8deb7e90718c66b57242308) | 新增对方当前通知查询，支持独立共享开关、系统授权和弹窗结果 |
| 2026-10-06 16:17 | [3e5cd25b](https://github.com/lijinmu666/PairLink/commit/3e5cd25b27b7ba6ef6b7a97954994871ae99977c) | 应用使用记录扩展至24小时，补充应用可见性以读取名称 |
| 2026-10-06 16:09 | [a7762038](https://github.com/lijinmu666/PairLink/commit/a776203858573a25158c7ff1a1b642777f4b2766) | 按授权状态显示权限入口，将本机记录移至连接设置并支持清除 |
| 2026-10-06 15:44 | [c89e0987](https://github.com/lijinmu666/PairLink/commit/c89e09875458b96e73400600f559506721c8e2d0) | Show execution results in Miuix dialog with indeterminate progress |
| 2026-10-06 13:35 | [6b82e166](https://github.com/lijinmu666/PairLink/commit/6b82e1668f673994e63dbe0b714433cf01886fa7) | 界面迁移至 Miuix：默认蓝色主题，替换全部控件与弹窗，移除自定义图标（0.2.0） |
| 2026-10-06 07:22 | [b20ee71c](https://github.com/lijinmu666/PairLink/commit/b20ee71c165b7dfd88e91df5d09438da18ff88df) | 添加第一版安卓客户端、Worker 服务端与部署说明 |

### 构建、签名与验证

| 北京时间 | 提交 | 内容 |
| --- | --- | --- |
| 2026-10-11 09:30 | [fd6a92d4](https://github.com/lijinmu666/PairLink/commit/fd6a92d4ff7f9edde53439d8fe3e9c56d7fa9032) | Record successful release checks and restore manual builds |
| 2026-10-11 09:25 | [1465c472](https://github.com/lijinmu666/PairLink/commit/1465c4723c88fed96222a7f3617437a4861e9c13) | Build current signed release artifact |
| 2026-10-10 20:15 | [63d268ac](https://github.com/lijinmu666/PairLink/commit/63d268aca804bae2388596574bf81214d5a37429) | 记录最新 release 验证结果并恢复手动编译流程 |
| 2026-10-10 20:11 | [91209c4f](https://github.com/lijinmu666/PairLink/commit/91209c4f7adc9d4ddd02c8d47768d59a7c6062a1) | 编译最新 Android release 并验证概览与权限提示 |
| 2026-10-09 20:04 | [97ae6496](https://github.com/lijinmu666/PairLink/commit/97ae64964f8832875b5ed6092e8b2978419334b2) | 记录 release 构建通过并恢复手动编译工作流 |
| 2026-10-09 20:00 | [1292c505](https://github.com/lijinmu666/PairLink/commit/1292c50551aea49631dd79e3c2fe7a3470f6a7a9) | 开关检查系统权限并自动引导，特权模式直接申请，移除 Worker 并构建 release |
| 2026-10-08 21:16 | [f9d4df5b](https://github.com/lijinmu666/PairLink/commit/f9d4df5bdd02f2140c2c21cfe8076b6128d53383) | Use JDK 21 for Dhizuku and restore manual build trigger |
| 2026-10-08 21:15 | [d7ccf31a](https://github.com/lijinmu666/PairLink/commit/d7ccf31a0e131903ce51ea68494e791f742e33aa) | Fix Dhizuku bridge lint with scoped hidden API exemption annotation |
| 2026-10-08 21:10 | [2a9f6410](https://github.com/lijinmu666/PairLink/commit/2a9f6410144053d96f62b455ebc8575044ea6aae) | 编译最新版 release，升级构建环境至 JDK 21 |
| 2026-10-07 17:35 | [84e5d1d1](https://github.com/lijinmu666/PairLink/commit/84e5d1d1f18972abf1e51a4dbbb5e2e17c7b210d) | Restore manual build workflow after release build |
| 2026-10-07 17:28 | [cb896eb6](https://github.com/lijinmu666/PairLink/commit/cb896eb688a7c39a42cd7de50884a031200c6faf) | 编译媒体查询版本Release并准备离线签名 |
| 2026-10-07 10:12 | [2dbf15b0](https://github.com/lijinmu666/PairLink/commit/2dbf15b0b5c1feaf745147cfdf7bc62ddbcd8379) | 编译完成后恢复手动触发 |
| 2026-10-07 10:09 | [a98c09f0](https://github.com/lijinmu666/PairLink/commit/a98c09f0df63b87e90d04fcc977f1a542c880ee1) | 编译安全DNS开关与概览客户端版本号更新 |
| 2026-10-07 03:11 | [55b14d90](https://github.com/lijinmu666/PairLink/commit/55b14d909a451add643056b8d91320a6e9a8a744) | 编译完成后恢复手动触发 |
| 2026-10-07 03:06 | [63dc2799](https://github.com/lijinmu666/PairLink/commit/63dc27995ae1d7b89089672973b09f9f09c20bfc) | 编译包含小猫图标与表白彩蛋的版本 |
| 2026-10-07 02:21 | [171cf273](https://github.com/lijinmu666/PairLink/commit/171cf2737e41c686eebc92192d484c7824140287) | 编译完成后恢复手动触发 |
| 2026-10-07 02:18 | [99c0cf8b](https://github.com/lijinmu666/PairLink/commit/99c0cf8bef10b7237e08e4bea0dee51eb712f96c) | 编译最新概览与四分页版本 |
| 2026-10-07 01:39 | [b7aaa3d6](https://github.com/lijinmu666/PairLink/commit/b7aaa3d6ed5d8a440fb255da805e3b777b687fa4) | 分页版本编译通过，恢复手动构建 |
| 2026-10-07 01:36 | [7d9789d7](https://github.com/lijinmu666/PairLink/commit/7d9789d7ba607ac5fc808f2356027e681be66333) | 编译最新分页界面与箭头返回按钮 |
| 2026-10-07 01:15 | [9867db9f](https://github.com/lijinmu666/PairLink/commit/9867db9f9677a1d477ba0045ff7abda958f82410) | 本次编译完成，恢复手动构建 |
| 2026-10-07 01:12 | [9b8eb1dd](https://github.com/lijinmu666/PairLink/commit/9b8eb1dd9645bc1f218625ad2a40f2268adbf4f6) | 编译卡片结果页面版本 |
| 2026-10-06 22:11 | [64788b0f](https://github.com/lijinmu666/PairLink/commit/64788b0fe98017eaa0584c70ea672ab10c820759) | 恢复手动编译，源码更新不自动构建 |
| 2026-10-06 17:35 | [f40cf8f9](https://github.com/lijinmu666/PairLink/commit/f40cf8f926101d44d45a566da4ca78ab87ef8e6b) | 修复最近应用结果截断标记编译错误 |
| 2026-10-06 17:32 | [27e3067c](https://github.com/lijinmu666/PairLink/commit/27e3067cc8ef5c678c87c5fb367701cf146646c3) | 启用主分支推送自动编译 |
| 2026-10-06 13:45 | [e7eb9d2d](https://github.com/lijinmu666/PairLink/commit/e7eb9d2d5c71b07ece3c411f5d2419eb10dbd644) | 记录 Miuix 版编译、Lint、安装包校验及覆盖安装限制 |
| 2026-10-06 13:41 | [671aa595](https://github.com/lijinmu666/PairLink/commit/671aa59573b66fbc12d67c7013b7763ecf5427bb) | 适配官方 SDK 37.1 包与新版 minor API 编译配置 |
| 2026-10-06 13:37 | [adf28b2b](https://github.com/lijinmu666/PairLink/commit/adf28b2bce151aa794359139113c3ff765aabf33) | 构建使用可下载的 Android SDK 36 |
| 2026-10-06 07:48 | [6274503f](https://github.com/lijinmu666/PairLink/commit/6274503fdba69220a99498304c1cf276b3a03513) | Record successful Android APK build and lint checks |
| 2026-10-06 07:44 | [640ed492](https://github.com/lijinmu666/PairLink/commit/640ed4922de13c2df52ce5a46a67e33b8a6ef9d7) | Fix Android CI setup by avoiding obsolete SDK tools package |

### 文档与协议

| 北京时间 | 提交 | 内容 |
| --- | --- | --- |
| 2026-10-08 21:16 | [272de0dc](https://github.com/lijinmu666/PairLink/commit/272de0dcc0267cf18cef9dd842355df69fc2f6a4) | Document JDK 21 requirement for Android builds |
| 2026-10-07 00:44 | [0256a373](https://github.com/lijinmu666/PairLink/commit/0256a3732da11c4e98698c39430d3853e29dd97b) | 补充应用使用时长结果协议 |

### 初始化

| 北京时间 | 提交 | 内容 |
| --- | --- | --- |
| 2026-10-06 07:21 | [eb839fd8](https://github.com/lijinmu666/PairLink/commit/eb839fd8b84b4061b6b0a5b2adcc1f810c341302) | 初始化 PairLink 第一版源码 |
