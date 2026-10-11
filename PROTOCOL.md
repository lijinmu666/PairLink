# PairLink v1 协议

连接：`wss://服务器/api/device/ws?device_id=alice`。每个设备只保留最新连接；文本 `ping` 得到 `pong`。

## 提交请求

`POST /api/commands`

```json
{
  "sender_id": "alice",
  "target_id": "bob",
  "action": "shell",
  "params": { "command": "id", "timeout_seconds": 20 }
}
```

action 可选：

| action | params |
| --- | --- |
| get_status | 空对象；返回设备型号、安卓版本、开机时长、当前屏幕应用、电量和授权状态 |
| get_recent_apps | hours：1 到 24，默认 1；客户端固定查询 24 小时 |
| get_installed_apps | 空对象；返回已安装的用户应用 | 
| send_notification | title：1 到 128 字符；content：1 到 4096 字符；接收方必须开启发送授权 |
| get_notifications | 空对象；接收方必须开启共享、通知共享并授予通知访问权限 |
| shell | command：1 到 8192 字符；timeout_seconds：1 到 30，默认 20；执行权限跟随接收手机的特权工作模式 |
| set_app_suspended | package_name：合法包名，最多 255 字符；suspended：布尔值，true 暂停，false 恢复；执行方式由接收方选择 |

返回 202，包含 `command_id`、`status`、`created_at` 和 `deadline_at`。服务端添加 `type: command` 后转发到目标 WebSocket。一个目标同时只允许一个未过期请求；离线不积压命令。

## 回传

APK 使用 `POST /api/device/result`，不用依赖原 WebSocket 仍在线：

```json
{
  "device_id": "bob",
  "command_id": "服务端生成的UUID",
  "ok": true,
  "result": {
    "stdout": "输出",
    "stderr": "",
    "exit_code": 0,
    "timed_out": false,
    "cancelled": false,
    "truncated": false,
    "root": false
  }
}
```

失败时 `ok: false`，同时返回 `error` 文本。设备也可以通过 WebSocket 发送 `type: result` 以及上述 command_id/ok/result/error 字段，设备 ID 从连接附件取得。

退出码非零仍代表“执行完成”，由结果中的退出码报告程序失败。系统权限、指令格式或本机开关拒绝则使用 `failed`。

## 应用使用情况结果

`get_recent_apps` 保留原有名称、包名与最近打开时间，增加 `usage_duration_ms`（毫秒）和 `duration_known`。统计范围为查询结束时间向前推算的 hours 小时，客户端固定 24 小时。

按前台活动事件累计屏幕亮着时的使用时间，多次使用合并，同一应用的活动重叠不重复计时。查询额外读取窗口前 24 小时事件，用于识别跨越起点的活动；仅累计查询窗口内的部分。关机结束前台活动。检测到活动结束但找不到对应开始记录时，该应用时长返回 null、duration_known 为 false，界面显示“未知”；系统未提供的事件无法完全还原，结果仍可能存在误差。

已知时长从高到低排列，未知时长放在末尾。最多返回 50 个应用，序列化超过 44000 字符时截断，并设置 `truncated`。结果还包含 `window_start` 和 `queried_at`。新客户端查询旧版本设备时，缺少时长显示“未知”。

## 普通通知结果

`send_notification` 在接收方通知权限和发送授权有效时发布普通重要性通知。

```json
{
  "sent": true,
  "title": "提醒",
  "content_length": 2,
  "sent_at": 1791270000000
}
```

## 应用暂停结果

`set_app_suspended` 沿用已有通道；请求中的执行方式字段会被忽略，不能由发送方强制选择 root。

```json
{
  "package_name": "com.example.app",
  "app_name": "应用",
  "requested_suspended": true,
  "suspended": true,
  "backend": "device_owner",
  "note": "",
  "applied_at": 1791270000000
}
```

`backend` 为 `device_owner`、`shizuku` 或 `root`。`suspended` 是执行后确认的实际状态；恢复时若仍被其他来源暂停，状态仍为 true，`note` 会说明。本机暂停共享开关默认关闭；接收方还需满足所选系统权限。系统拒绝、权限不足或未获取实际状态时回传 `failed`。

## Shell 工作模式

接收手机必须开启共享及 Shell 总开关。发送端不提供权限选择，接收端以本机“特权工作模式”为准：无或 Dhizuku 使用普通应用权限；Shizuku / Sui 使用其启动身份；root 使用 `su -c`。Dhizuku 不用于任意 Shell。

Railway 为兼容旧客户端仍接受 root/shizuku 布尔字段（默认 false，不能同时为 true），新客户端不发送，接收端也不据此选择权限。这些字段不能覆盖本机模式。双方需更新 APK；现有 Railway 可直接转发，无需变更。

结果包含 `backend`（app/root/shizuku）、`work_mode`（none/dhizuku/shizuku/root）、`shizuku_uid`（仅 Shizuku 有效，其他为 -1），保留原有 root、输出与状态字段。root 表示使用 root 通道；Shizuku 启动身份用 UID 判断。选择 Shizuku 时授权或服务不可用则失败，不会改用其他权限。执行期间模式、共享开关、Shell 开关或 Shizuku 身份变化会尝试取消执行。

设备状态新增 `work_mode`、`shell_backend` 和 `shizuku_uid`；兼容字段 `root_enabled` 表示共享和 Shell 已开启且本机选择 root，不再代表单独的 root 开关。

## 当前通知结果

`get_notifications` 通过已有的提交、回传和查询通道返回：

```json
{
  "notifications": [{
    "package_name": "example.chat",
    "app_name": "聊天",
    "title": "新消息",
    "text": "你好",
    "posted_at": 1791270000000,
    "ongoing": false,
    "group_summary": false,
    "content_truncated": false
  }],
  "total_count": 1,
  "returned_count": 1,
  "truncated": false,
  "queried_at": 1791270000100
}
```

按通知发布时间倒序排列，包括系统提供的常驻通知和分组摘要；不读取已清除的历史通知，不执行通知点击或清除动作。应用名和标题最多 256 字符，正文最多 2048 字符；序列化结果控制在 47000 字符以内，以兼容原有 48000 字符服务端上限。通知过多时只返回能容纳的部分；`total_count`、`returned_count` 和 `truncated` 表示实际返回情况。空正文代表应用未提供标准文本或系统没有暴露正文。

通知监听服务尚未连接、未授权或已关闭共享时，按原有失败格式回传。通知内容不写入本机连接日志；服务端结果仍按 24 小时清理规则处理。

## 查询

`GET /api/commands/{command_id}`。请求方每秒查询，最多等待约 50 秒。

状态：pending、completed、failed、offline、timeout。终态不能被回传覆盖，迟于 deadline_at 的数据不再接受。记录和输出 24 小时后删除。

服务器时间为 Unix 毫秒。APK 时钟明显不准时可能误判指令已经过期。

## 无验证约定

本版所有接口无身份验证。HTTP 回传中的 device_id 和连接中的设备 ID 均为客户端自报，不能证明身份。后续验证应覆盖 WebSocket 建连、提交、查询、回传四条路径。
