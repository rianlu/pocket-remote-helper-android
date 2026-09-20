# Pocket Remote 协议（冻结）

本文件是协议的**权威文本**。`pocket-remote-android` 与 `pocket-remote-helper-android` 里的 `docs/PROTOCOL.md` **必须逐字一致**。

改端口、JSON `type`、字段名、路径时：同时改这两个仓库的本文件，再改代码。禁止只改一端、禁止实现时私改。

日常遥控只走本协议（`17880`）。电视端可用本机 adbd 执行 `input`，那不是把 ADB 当遥控协议。第一期不要扫厂商口装包。

---

## 常量

| 名 | 值 |
|---|---|
| 手机 applicationId | `com.pocketremote` |
| 电视 applicationId | `com.pocketremote.helper` |
| 控制/文件 TCP 端口 | `17880` |
| UDP 发现端口 | `17882` |
| NSD 类型 | `_pocketremote._tcp.` |
| NSD 服务名 | `PocketRemote` |
| WebSocket | `ws://<ip>:17880/ws` |
| UDP magic | 8 字节 ASCII `PKTREMT1` |
| 协议版本 `v` | `1` |
| 电视文件根 | `/sdcard/PocketRemote/`（`inbox/`、`apk/`） |

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
| `hello` | 手机→电视 | `{ "token": "", "pin": "", "phoneName": "xxx", "inject": "plugin" }` 首次 token/pin 可空；`inject` 可选 `plugin\|adb` |
| `hello_ok` | 电视→手机 | `{ "token": "...", "tvName": "...", "sdk": 19, "injectOk": true, "injectMode": "plugin" }` |
| `need_pin` | 电视→手机 | `{}` 电视全屏显示 6 位 PIN，60 秒 |
| `error` | 电视→手机 | `{ "code": "AUTH\|INJECT\|TEXT\|FS\|PROTOCOL", "message": "..." }` |
| `key` | 手机→电视 | `{ "action": "click", "code": 19 }` 第一期只要 `click` |
| `text` | 手机→电视 | `{ "text": "..." }` |
| `apps` | 手机→电视 | `{}` |
| `apps_ok` | 电视→手机 | `{ "apps": [ { "name", "pkg", "system": false, "size": 0, "extractable": true } ] }` `size` 为占用字节；`extractable` 为可提取完整 APK |
| `app_open` | 手机→电视 | `{ "pkg": "..." }` |
| `app_uninstall` | 手机→电视 | `{ "pkg": "..." }` |
| `apk_install` | 手机→电视 | `{ "name": "xxx.apk", "path": "Download/xx.apk" }` `path` 优先（公共存储相对路径或绝对路径），否则按 `name` 在 `PocketRemote/apk\|inbox` 查找。仅 `.apk`，拒绝 `..` 与非公共存储。调起系统安装器，不存在回 `error FS` |
| `pointer` | 手机→电视 | `{ "action": "move\|click\|down\|up", "dx": 0, "dy": 0 }` 鼠标相对位移；click 为单击 |
| `info` | 手机→电视 | `{}` |
| `info_ok` | 电视→手机 | `{ "tvName", "manufacturer", "brand", "android", "sdk", "abi", "soc", "cpu", "ip", "mac", "firmware", "hardware", "width", "height", "density", "ramMb", "ramAvailMb", "storageFreeMb", "storageTotalMb", "cacheMb", "injectMode", "injectOk", "helper" }` |
| `clean` | 手机→电视 | `{}` 结束后台应用 |
| `clean_ok` | 电视→手机 | `{ "killed", "ramAvailMb" }` |

没有 `ime.start`。无有效 token 只接受 `hello`，其它回 `error.code=AUTH`。

### 键值（Android KeyEvent）

| 键 | code |
|---|---|
| 上 / 下 / 左 / 右 / 确认 | 19 / 20 / 21 / 22 / 23 |
| 返回 / 主页 / 菜单 | 4 / 3 / 82 |
| 音量 ± / 静音 | 24 / 25 / 164 |
| 0–9 | 7–16 |
| 删除 | 67 |
| 清空 | 28 |
| 设置 | 176 |
| 信号源 | 178 |

电视端音量用 `AudioManager`，不要只靠 key 24/25。

---

## 握手

1. 手机连 `ws://ip:17880/ws`，发 `hello`（可无 token）。
2. 未配对：电视 `need_pin` 并显示 PIN；手机再发 `hello.pin`。
3. PIN 正确：`hello_ok`（含 token）。错误 3 次关闭配对窗，`error AUTH`。
4. 已有有效 token：直接 `hello_ok`。
5. `hello_ok.sdk` 为电视 `Build.VERSION.SDK_INT`。`text` 由电视写入剪贴板并粘贴到当前焦点，不执行 `input text`。
6. `hello_ok.injectOk`：按键注入是否可用。`injectMode` 为当前通道：`plugin`（系统签名插件）、`adb`（本机 adbd + `input`）、`input`（仅 sdk&lt;21 的助手进程 `input keyevent`）、`none`（探测失败，**不是**一种可用遥控模式）。缺省 `injectMode` 时仅看 `injectOk`。
7. `hello.inject`：连接前选定通道。`plugin` 或 `adb` 时本连接只走该通道，按键不改走另一通道，以便分别验证。缺省或无法识别则仍按 plugin → adb → input(4.x) 自动探测。连上后不可改，需断开再 `hello`。

Token 双方持久化。HTTP：`Authorization: Bearer <token>`，否则 401。

---

## HTTP 文件

| 方法 | 路径 | 说明 |
|---|---|---|
| PUT | `/transfer/upload?dir=inbox\|apk&name=` | 请求体原始字节 |
| GET | `/transfer/download?name=` | |
| GET | `/transfer/list` | `{ "files": [ { "name", "dir", "size", "mtime", "path" } ] }` 含 `PocketRemote` 与公共存储扫描到的 `.apk`；`path` 供 `apk_install` |
| GET | `/transfer/icon?path=` | 未安装 APK 的 PNG 图标；`path` 与 `apk_install` 相同 |
| GET | `/apps/icon?pkg=` | PNG 应用图标 |
| GET | `/apps/apk?pkg=` | 该包 APK 字节。系统应用、分体包返回 403 |

只允许 `inbox` 与 `apk`，拒绝 `..`。`apps` 列表只含可打开（LAUNCHER / LEANBACK_LAUNCHER）的应用。

---

## UDP 发现

广播 `255.255.255.255:17882`。payload = `PKTREMT1` + uint16 大端 `17880`。电视单播回同样头部 + UTF-8 设备名。手机监听 17882。

NSD TXT：`id`（电视安装时生成的 UUID）、`sdk`、`v=1`。
