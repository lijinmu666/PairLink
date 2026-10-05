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
| get_status | 空对象 |
| get_recent_apps | hours：1 到 24，默认 1 |
| shell | command：1 到 8192 字符；root：布尔值，默认 false；timeout_seconds：1 到 30，默认 20 |

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

## 查询

`GET /api/commands/{command_id}`。请求方每秒查询，最多等待约 50 秒。

状态：pending、completed、failed、offline、timeout。终态不能被回传覆盖，迟于 deadline_at 的数据不再接受。记录和输出 24 小时后删除。

服务器时间为 Unix 毫秒。APK 时钟明显不准时可能误判指令已经过期。

## 无验证约定

本版所有接口无身份验证。HTTP 回传中的 device_id 和连接中的设备 ID 均为客户端自报，不能证明身份。后续验证应覆盖 WebSocket 建连、提交、查询、回传四条路径。
