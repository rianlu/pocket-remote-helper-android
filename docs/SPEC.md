# Pocket TV 开发参考

自研「手机遥控 + 电视助手」。本文是交给 AI 实现的**唯一规格**：包名、端口、JSON 字段按下表写死，不要另起一套。

**工程：** 当前目录 `pocket-tv-android` 只是和 AI 沟通的工作区，**不推 GitHub**。  
开源推送的是其中两个**独立 Git 仓库**（名称按 GitHub 仓库名，不要叫 `phone`/`tv`）：

```
pocket-tv-android/                    工作区（不推送）
  docs/TV遥控器开发参考.md            规格原文
  pockettv-remote-android/            独立仓库 → GitHub 手机遥控
  pockettv-helper-android/            独立仓库 → GitHub 电视助手
```

| 仓库 | applicationId | minSdk | 角色 |
|---|---|---|---|
| `pockettv-remote-android` | `com.pockettv.phone` | 21 | 手机客户端 |
| `pockettv-helper-android` | `com.pockettv.tv` | 16 | 电视助手 |

各仓库自己 `git init`、自己推送。契约是 §8。鸿蒙以后另开 `pockettv-remote-harmony`。

---

## 1. 产品形态

必须两端。手机不能直接操控电视。

```
手机 ── 局域网 ──► 电视助手 ──► 本机按键 / 写入文本 / 收文件 / 装包
```

| 端 | minSdk | UI | 职责 |
|---|---|---|---|
| 手机 | 21 | Compose 即可 | 发现、遥控、手动发文本、传文件、装包 |
| 电视 | **16（Android 4.1）** | View，禁止 Compose | 被发现、执行指令 |

两端选型不同：手机用现代栈；电视为了 4.1 必须用「还能在 API 16 跑」的库。架构（助手 + NSD/UDP + `input keyevent`）不过时，过时的是悟空那套工程依赖，见下表。

---

## 1.1 悟空那套 vs 现在该用什么

悟空停更很久：Support 库、targetSdk 26、AsyncTask、Otto、ConnectSDK、Paho MQTT、友盟/广告。**遥控原理不用换**；工程栈按两端拆开换。

| 悟空（过时） | 手机（min 21） | 电视（min 16） | 说明 |
|---|---|---|---|
| Java + `android.support.*` | Kotlin + **AndroidX** + Compose | Kotlin 或 Java + **AndroidX**（不用 Compose） | AndroidX 多数组件 minSdk 14/16，能换 |
| targetSdk 26 | **35**（上架要求） | 建议 28–33，按 API 分支处理存储/安装 | 电视 APK 侧载，不必跟 Play 强绑 |
| `AsyncTask` | 协程 `viewModelScope` | `HandlerThread` / `Executor` | AsyncTask 已废弃 |
| Otto EventBus | `StateFlow` / Channel | 接口回调 + Handler | Otto 已死 |
| 自写 UDP 二进制 | JSON **WebSocket** | 同左 | 调试成本低；帧格式自研 |
| ConnectSDK（SSDP/DLNA） | `NsdManager` | `NsdManager` + UDP 兜底 | 第一期不需要 DLNA |
| JmDNS | 一般不必 | NSD 失败再考虑 JmDNS | 4.x 组播不稳时的备选 |
| 手写 HTTP / 老 OkHttp | **OkHttp 4.x** | **OkHttp 3.12.13** 或 `HttpURLConnection` | OkHttp 4 最低 API 21，电视用不上 |
| Eclipse Paho MQTT | 不做 | 不做 | 那是海信云通道，局域网遥控不需要 |
| Apache Http 遗留库 | 禁止 | 禁止 | 系统已删 |
| `file://` 装包 | FileProvider | 4.x 用 `file://`，7.0+ FileProvider | 按 API 分支 |
| 明文 HTTP 全局允许 | 仅局域网 cleartext | 同左 | 用 `networkSecurityConfig` 白名单 |
| Bugly / 友盟 / 一堆广告 | 可选 Firebase/自建，第一期不要 | 不要 | 减小包体、少权限 |
| RxJava 1 | 协程 | 不用响应式也行 | |
| 自定义 ADB 协议栈 | 可选「装助手」时再写 | 同左 | 协议本身没过时，实现用维护中的 adb 库或自写小实现 |

**不要换、现在还成立的：**

- 手机 + 电视助手，而不是长期挂 ADB 当遥控器
- 发现用 NSD，不行再 UDP / 手填 IP
- 老盒子按键用 `input keyevent`（没有更现代的公开 API 能替代第三方在 4.x 上注入）
- 5555 只当可选安装跳板

电视不要上 Compose、OkHttp 4、Ktor、DataStore、WorkManager、Hilt——不是这些不好，是 **API 16 跑不了或体积/DEX 过大**。手机可以全用。

两个仓库各写一份与 §8 逐字相同的常量类（Java + `org.json` 即可），禁止私改字段名。

---

## 2. 结论：这个方案可以

min 4.1 + NSD 发现 + **用户点按钮发文本**（不自动弹键盘、不做输入法、第一期不必无障碍）——作为产品主路径成立。

剩下的限制用提示消化，不要为了中文自动填框去上输入法/无障碍。

| 能力 | 4.1–4.4 (API 16–19) | 5.0+ (API 21+) |
|---|---|---|
| Service + 开机启动 | 可以 | 可以 |
| TCP / WebSocket | 可以 | 可以 |
| NSD 发现 | **API 有，盒子上经常不稳** | 一般可用 |
| 方向键等 | `input keyevent` | 同左 |
| 手动发英文/数字 | `input text` | 同左，更稳 |
| 手动发中文 | 经常失败 → **提示系统过旧** | 优先 `input text`；仍失败再提示 |
| 装 APK | `file://` + `ACTION_VIEW` | 7.0+ 必须 FileProvider |

NSD 在 4.1–4.4 国产盒子上组播经常被阉，**NSD 失败则 UDP 广播，再失败手填 IP**。不是退回 4.0，是给 NSD 留后路。

---

## 3. 范围

**做**

- 发现 / 连接
- 方向键、确认、返回、主页、菜单、音量、数字键
- 手机遥控页一个「键盘」按钮，点开输入，发送到电视当前焦点
- 文件传到 `/sdcard/PocketTV/`
- 安装包：传 APK、系统安装页、已装列表、打开、卸载

**不做**

- 自定义输入法
- 无障碍（第一期不做；不是必须）
- 电视输入框焦点时手机自动弹键盘
- 完整文件管理器、投屏、云端遥控

---

## 4. 发现与连接

日常遥控不走 ADB。ADB / 厂商口只用于可选「装助手」。

1. 电视助手常驻，NSD 宣告 `_pockettv._tcp.`，端口例如 `17880`
2. 手机 `NsdManager.discoverServices`；超时或 0 台则 UDP 广播；再不行手填 IP
3. WebSocket 连上，PIN 换 token
4. 按键走 WebSocket；文件走 HTTP

`CHANGE_WIFI_MULTICAST_STATE` 必须要，否则 NSD/UDP 组播无效。

---

## 5. 调研记录：ADB 与厂商端口

第一次装助手时，市面产品会扫这些口。这是安装通道，不是遥控协议。用户已开网络调试时，可用 ADB 装**自己的**助手。厂商口不作为产品承诺。

### 5.1 网络 ADB

| 口 | 说明 |
|---|---|
| **5555** | 标准 adbd，优先 |
| 5037、5114、7896、1127、30105、31015 | 厂商改口 |

```
TCP IP:5555 → 握手 → push apk → pm install -r → am start 助手 → disconnect
```

### 5.2 厂商私有口（排查用）

| 通道 | 端口 |
|---|---|
| 小米 | 6095、9095 |
| 阿里云 OS | 7890、13510 |
| 康佳 | 自定义 + 8086 |
| 微鲸 | 12321 |
| TCL | 6553、8843（有的会 `start adbd`） |
| 海美迪 / 沙发管家 | 8899 |
| 微视听 | 8051 |
| 乐视 | 8008 |
| 荣耀 / 长虹 | 7766 |
| 海尔 | 49152 |
| 爱奇艺 | 39621 |
| 酷开 | 1980、1988 |
| PPTV | 9101 |
| 海信 | 50000 段 / 36669 |
| 魅族 / 暴风 / 百度 | 11231 / 21367 / 4004 |

助手上线后只连自己的 `17880`。

安装策略：主路径二维码/U 盘手动装；可选 5555（用户勾选已开调试）。

---

## 6. 按键与发文本

### 6.1 按键

| 键 | 数值 |
|---|---|
| 上 / 下 / 左 / 右 / 确认 | 19 / 20 / 21 / 22 / 23 |
| 返回 / 主页 / 菜单 | 4 / 3 / 82 |
| 音量 ± / 静音 | 24 / 25 / 164 |
| 0–9 | 7–16 |

电视注入：

1. `Runtime.exec("input keyevent " + code)`（4.x 盒子上最常见能用）
2. 失败则本机 `127.0.0.1:5555`（仍开着调试时）
3. 再失败：手机提示「按键注入失败，可尝试打开网络调试」

音量用 `AudioManager.adjustStreamVolume`，不发键。

不必无障碍。`performGlobalAction` 是 API 16 有，但要用户开无障碍，第一期不加。

### 6.2 文本（手动按钮）

流程：用户在电视上把光标点进输入框 → 手机点「键盘」→ 打字 → 发送。App **不监听**电视焦点。

```json
{ "type": "text", "payload": { "text": "要搜的内容" } }
```

电视执行 `input text …`（空格按 `input` 命令规则转义）。

- 英文、数字、常见符号：4.1 即可
- 中文：很多 4.1–4.4 的 `input` **不吃 UTF-8** → toast「当前电视系统过旧，中文输入可能无效」
- 发送前若 `Build.VERSION.SDK_INT < 21` 且含非 ASCII，直接提示，仍允许用户强行发送

用户自己负责「电视现在是不是输入框」。发到桌面焦点上会变成乱按键或没反应，提示里写一句即可。

---

## 7. 文件传输 + 安装包

```
/sdcard/PocketTV/inbox/    普通文件
/sdcard/PocketTV/apk/      待安装包
```

| 方法 | 路径 |
|---|---|
| PUT | `/transfer/upload?name=` |
| GET | `/transfer/download?name=` |
| GET | `/transfer/list` |

安装：`ACTION_VIEW` + `application/vnd.android.package-archive`（4.x `file://`，7.0+ FileProvider）。列表/打开/卸载走 `PackageManager` 与系统卸载页。不静默 `pm install`。

---

## 8. 协议（冻结，禁止改名改口）

两个仓库各写常量类，**数值必须与下表一致**。契约在本文。

| 常量 | 值 |
|---|---|
| 手机 applicationId | `com.pockettv.phone` |
| 电视 applicationId | `com.pockettv.tv` |
| 控制/文件 TCP 端口 | `17880` |
| UDP 发现端口 | `17882` |
| NSD 类型 | `_pockettv._tcp.` |
| NSD 服务名 | `PocketTV` |
| WebSocket 路径 | `ws://<ip>:17880/ws` |
| UDP magic | 8 字节 ASCII `PTVDISC1` |
| 协议版本 `v` | `1` |

同一进程内：`17880` 上既接 WebSocket `/ws`，也接 HTTP `/transfer/*`。发现用 `17882`，避免和监听口抢。

### 8.1 帧

每条一行 JSON（WebSocket text frame）：

```json
{ "v": 1, "id": "<uuid>", "type": "<见下表>", "payload": { } }
```

响应原样带回 `id`。`type` 只允许：

| type | 方向 | payload |
|---|---|---|
| `hello` | 手机→电视 | `{ "token": "", "pin": "", "phoneName": "xxx" }` 首次 token/pin 可空 |
| `hello_ok` | 电视→手机 | `{ "token": "...", "tvName": "...", "sdk": 19, "injectOk": true }` |
| `need_pin` | 电视→手机 | `{ }` 电视同时全屏显示 6 位数字 60 秒 |
| `error` | 电视→手机 | `{ "code": "AUTH\|INJECT\|TEXT\|FS\|PROTOCOL", "message": "..." }` |
| `key` | 手机→电视 | `{ "action": "click", "code": 19 }` 第一期只要 `click` |
| `text` | 手机→电视 | `{ "text": "..." }` |
| `apps` | 手机→电视 | `{ }` 要已装列表 |
| `apps_ok` | 电视→手机 | `{ "apps": [ { "name", "pkg", "system": false } ] }` |
| `app_open` | 手机→电视 | `{ "pkg": "..." }` |
| `app_uninstall` | 手机→电视 | `{ "pkg": "..." }` 只调系统卸载页 |

没有 `ime.start`。未带有效 token 的连接：只接受 `hello`，其它一律 `error.code=AUTH`。

### 8.2 握手

1. 手机连 `ws://ip:17880/ws`，发 `hello`（无 token）。
2. 未配对：电视回 `need_pin` 并显示 PIN；手机输入 PIN 再发 `hello.pin`。
3. PIN 正确：电视回 `hello_ok`（含新 token）。PIN 错误 3 次关闭配对窗，回 `error AUTH`。
4. 已配对：`hello.token` 有效则直接 `hello_ok`。
5. `hello_ok.sdk` 给手机判断：`sdk < 21` 且用户输入含非 ASCII 时提示「中文可能无效」，仍可发送。
6. `hello_ok.injectOk`：电视启动时探测过 `input keyevent` 是否成功。

HTTP 传输 Header：`Authorization: Bearer <token>`。无 token → 401。

`GET /transfer/list` 响应：`{ "files": [ { "name", "dir": "inbox|apk", "size", "mtime" } ] }`。  
`PUT /transfer/upload?dir=inbox|apk&name=` 请求体为原始字节。

### 8.3 UDP 发现包

广播到 `255.255.255.255:17882`，payload = `PTVDISC1` + 2 字节端口（17880，大端）。电视收到后单播回同样格式 + UTF-8 设备名。手机也监听 17882。NSD TXT：`id`（安装时生成的 UUID）、`sdk`、`v=1`。

---

## 9. 工程

| 目录 | applicationId | minSdk | UI | 依赖限制 |
|---|---|---|---|---|
| `pockettv-helper-android` | `com.pockettv.tv` | 16 | View | 禁止 Compose、OkHttp 4、Ktor、Hilt、DataStore；Java 8 字节码 |
| `pockettv-remote-android` | `com.pockettv.phone` | 21 | Compose | OkHttp 4、协程均可 |

电视：

- Manifest：`LAUNCHER` + `LEANBACK_LAUNCHER`（`leanback` required=false）
- 权限：`INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`、`CHANGE_WIFI_MULTICAST_STATE`、`RECEIVE_BOOT_COMPLETED`、`WRITE_EXTERNAL_STORAGE`、`FOREGROUND_SERVICE`。不要无障碍、不要输入法
- `RemoteService` 前台服务 + `BootReceiver`。界面：主页（PIN/连接状态）+ 通知
- `input text` 空格转 `%s`。失败回 `error INJECT` 或 `TEXT`

手机：

- 中文 UI。页面：设备列表（NSD/UDP/手填 IP）→ PIN → 遥控板 → 键盘按钮 → 传文件 → 应用列表
- 只许发 §8 的 type

两端：第一期不要做 5555 自动安装；明文 HTTP 仅限 RFC1918。

---

## 9.1 纯血鸿蒙手机控安卓电视（以后，不是第一期）

**能做。** 场景是：纯血鸿蒙（NEXT）手机 → 局域网 → **安卓盒子上的本助手**。不是给鸿蒙电视写助手，也不需要电视装鸿蒙。

手机只发 §8 的 WebSocket/HTTP/UDP，按键仍由安卓助手在电视本机执行。和 Android 手机端是同一个服务器、同一套 JSON。

| | 难度 | 说明 |
|---|---|---|
| 控键 / 发文本 / 传文件 | 低 | 协议不变 |
| 发现 | 中 | 鸿蒙没有 `NsdManager`，用 UDP/手填 IP |
| UI | 中 | ArkTS 重画遥控板 |
| 安装 | 必须另写鸿蒙 App | 纯血鸿蒙不能装 Android 手机 APK |

第一期仍只做：安卓电视助手 + 安卓手机。鸿蒙手机端等协议跑通再开。

---

## 10. 实现顺序

1. 在两个独立仓库里建 application 工程，常量与 §8 对齐  
2. helper 听 `17880`；remote 手填 IP，hello / hello_ok  
3. PIN 配对 + 两端 token 持久化  
4. NSD + UDP `PTVDISC1`  
5. `key` → `input keyevent`（真机）  
6. 手机键盘按钮 → `text` → `input text`  
7. 上传 APK 到 `apk/` 并 `ACTION_VIEW`  
8. `apps` / 打开 / 卸载  

先电视后手机也可以：电视用 `websocat` 验收 hello。主测试：Android 4.2–4.4 盒子 + API 21+ 盒子。

---

## 11. 给 AI 的禁令

- 手机代码只在 `pockettv-remote-android/`，电视代码只在 `pockettv-helper-android/`。工作区根目录不要摊源码。不要在本工作区写鸿蒙。
- 不要引入输入法、无障碍、MQTT、广告 SDK、ConnectSDK。
- 不要改端口、NSD 类型、JSON 字段名（改规格先改本文）。
- 不要用 ADB 当遥控通道；第一期也不要扫厂商口装包。
- 不要在 `pockettv-helper-android` 用 Compose / OkHttp 4。
- 不要做完整文件管理器；只允许 `PocketTV/inbox` 与 `PocketTV/apk`。
- 缺规格时停下来问，不要发明第二套协议。
