# PairLink v1 协议

连接：`wss://服务器/api/device/ws?device_id=alice`。每个设备只保留最新连接；文本 `ping` 得到 `pong`。

## 提交请求

`POST /api/commands`

```json
{
  "sender_id": "alice",
  "target_id": "bob",
  "action": "shell",
  "params": { "command": "id", "root": false, "timeout_seconds": 20 }
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
| shell | command：1 到 8192 字符；root、shizuku：布尔值，默认 false，不能同时为 true；timeout_seconds：1 到 30，默认 20 |
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

## Shizuku Shell

当 `shizuku: true` 时，接收方必须开启 Shell、开启 Shizuku/Sui 授权开关，并已通过系统授权。与 `root: true` 互斥，不会自动降级或改用其他执行方式。结果增加 `backend`（app/root/shizuku）与 `shizuku_uid`（仅 Shizuku 有效，其他为 -1），原有输出和状态字段保持一致。

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
