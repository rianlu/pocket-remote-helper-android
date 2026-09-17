# PocketTV 协议（冻结）

本文件是协议的**权威文本**。`pockettv-remote-android` 与 `pockettv-helper-android` 里的 `docs/PROTOCOL.md` **必须逐字一致**。

改端口、JSON `type`、字段名、路径时：同时改这两个仓库的本文件，再改代码。禁止只改一端、禁止实现时私改。

日常遥控只走本协议，不用 ADB 当遥控通道。第一期不要扫厂商口装包。

---

## 常量

| 名 | 值 |
|---|---|
| 手机 applicationId | `com.pockettv.phone` |
| 电视 applicationId | `com.pockettv.tv` |
| 控制/文件 TCP 端口 | `17880` |
| UDP 发现端口 | `17882` |
| NSD 类型 | `_pockettv._tcp.` |
| NSD 服务名 | `PocketTV` |
| WebSocket | `ws://<ip>:17880/ws` |
| UDP magic | 8 字节 ASCII `PTVDISC1` |
| 协议版本 `v` | `1` |
| 电视文件根 | `/sdcard/PocketTV/`（`inbox/`、`apk/`） |

电视在 `17880` 同时提供 WebSocket `/ws` 与 HTTP `/transfer/*`。发现只用 `17882`。

---

## 帧

WebSocket **text** 帧，每条一个 JSON：

```json
{ "v": 1, "id": "<uuid>", "type": "<见下表>", "payload": {} }
```

响应带回同一 `id`。`type` 仅允许：

| type | 方向 | payload |
|---|---|---|
| `hello` | 手机→电视 | `{ "token": "", "pin": "", "phoneName": "xxx" }` 首次 token/pin 可空 |
| `hello_ok` | 电视→手机 | `{ "token": "...", "tvName": "...", "sdk": 19, "injectOk": true }` |
| `need_pin` | 电视→手机 | `{}` 电视全屏显示 6 位 PIN，60 秒 |
| `error` | 电视→手机 | `{ "code": "AUTH\|INJECT\|TEXT\|FS\|PROTOCOL", "message": "..." }` |
| `key` | 手机→电视 | `{ "action": "click", "code": 19 }` 第一期只要 `click` |
| `text` | 手机→电视 | `{ "text": "..." }` |
| `apps` | 手机→电视 | `{}` |
| `apps_ok` | 电视→手机 | `{ "apps": [ { "name", "pkg", "system": false } ] }` |
| `app_open` | 手机→电视 | `{ "pkg": "..." }` |
| `app_uninstall` | 手机→电视 | `{ "pkg": "..." }` |

没有 `ime.start`。无有效 token 只接受 `hello`，其它回 `error.code=AUTH`。

### 键值（Android KeyEvent）

| 键 | code |
|---|---|
| 上 / 下 / 左 / 右 / 确认 | 19 / 20 / 21 / 22 / 23 |
| 返回 / 主页 / 菜单 | 4 / 3 / 82 |
| 音量 ± / 静音 | 24 / 25 / 164 |
| 0–9 | 7–16 |

电视端音量用 `AudioManager`，不要只靠 key 24/25。

---

## 握手

1. 手机连 `ws://ip:17880/ws`，发 `hello`（可无 token）。
2. 未配对：电视 `need_pin` 并显示 PIN；手机再发 `hello.pin`。
3. PIN 正确：`hello_ok`（含 token）。错误 3 次关闭配对窗，`error AUTH`。
4. 已有有效 token：直接 `hello_ok`。
5. `hello_ok.sdk` 为电视 `Build.VERSION.SDK_INT`。手机若 `sdk < 21` 且文本含非 ASCII，提示中文可能无效，仍可发送。
6. `hello_ok.injectOk`：电视启动时探测 `input keyevent`。

Token 双方持久化。HTTP：`Authorization: Bearer <token>`，否则 401。

---

## HTTP 文件

| 方法 | 路径 | 说明 |
|---|---|---|
| PUT | `/transfer/upload?dir=inbox\|apk&name=` | 请求体原始字节 |
| GET | `/transfer/download?name=` | |
| GET | `/transfer/list` | `{ "files": [ { "name", "dir": "inbox\|apk", "size", "mtime" } ] }` |

只允许 `inbox` 与 `apk`，拒绝 `..`。

---

## UDP 发现

广播 `255.255.255.255:17882`。payload = `PTVDISC1` + uint16 大端 `17880`。电视单播回同样头部 + UTF-8 设备名。手机监听 17882。

NSD TXT：`id`（电视安装时生成的 UUID）、`sdk`、`v=1`。
