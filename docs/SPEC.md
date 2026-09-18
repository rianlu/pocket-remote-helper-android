# Pocket Remote Helper（Android 电视端）规格

本仓库只实现**电视/盒子助手**。协议以 [`PROTOCOL.md`](PROTOCOL.md) 为准（须与 remote 仓库同名文件一致）。背景见 [`ARCHITECTURE.md`](ARCHITECTURE.md)。

- 桌面名：口袋遥控助手
- applicationId：`com.pocketremote.helper`
- minSdk **16**（Android 4.1），compileSdk 35
- UI：View，禁止 Compose
- 网络：Java-WebSocket 或等价；HTTP 用 OkHttp **3.12.13** 或 `HttpURLConnection`（禁止 OkHttp 4）
- 线程：`HandlerThread` / `Executor`，禁止 AsyncTask
- 字节码 Java 8

不要在本仓库写手机 App、不要写鸿蒙。

---

## 职责

常驻服务：被发现、WebSocket 收指令、本机注入按键/文本/鼠标、收文件、调起安装/卸载。

不做：输入法、无障碍、完整文件管理器、ADB 当遥控、静默 `pm install`、MQTT、广告 SDK、Compose、Ktor、Hilt、DataStore、WorkManager。

---

## 组件

- `RemoteService`：前台服务，监听 `17880`（`/ws` + `/transfer/*`）和 `17882` UDP；NSD 宣告。WebSocket 不设读超时，须应答 ping（opcode 9）为 pong。
- `BootReceiver`：开机拉起（4.x 需引导自启动白名单）
- 界面最少：主页显示连接状态 / PIN；通知栏「遥控服务运行中」
- Manifest：`LAUNCHER` + `LEANBACK_LAUNCHER`（`leanback` required=false）

权限：`INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`、`CHANGE_WIFI_MULTICAST_STATE`、`RECEIVE_BOOT_COMPLETED`、`WRITE_EXTERNAL_STORAGE`、`FOREGROUND_SERVICE`。不要无障碍、不要 `BIND_INPUT_METHOD`。

明文 HTTP 仅 RFC1918。

---

## 注入

按键（覆盖要对齐市面助手，不拷贝其 APK）：

1. 系统签名插件 `com.pocketremote.helper.plugin`（`sharedUserId=android.uid.system`），经 AIDL 注入 → `injectMode=plugin`
2. 否则本机 adbd（`127.0.0.1:5555` 等常见口）以 shell 执行 `input`；尽力 `setprop service.adb.tcp.port` / `start adbd`（普通应用常失败）→ `injectMode=adb`。ADB 复用一条 interactive shell，按键不再每次 TCP 握手。
3. 仅 Android 4.x（sdk &lt; 21）把助手进程 `input` 算作可用 → `injectMode=input`。5.0+ 即使 `input` 退出码为 0 也不标为可用。
4. 再失败：`hello_ok.injectOk=false`，`injectMode=none`。这不是可用遥控模式，只表示探测失败；5.0+ 普通 `input` 不能打进其它窗口。

手机遥控协议仍只走 `17880`。`hello.inject=plugin|adb` 时本连接锁定该通道（按键/文本/鼠标不改走另一通道）；缺省仍按上表自动探测。`hello_ok.injectMode` 给两端界面显示当前通道。用户需在电视上打开网络调试后，无插件的盒子才能走 adb。

用户只装助手一个 APK。插件打在 `assets/plugin.apk`，启动时尽力静默 `pm install`（本机 adbd 或 `pm`）。插件无桌面图标。AOSP platform 测试证书装不上时助手仍可配对。禁止静默安装用户上传的 APK。厂商私有口 / 厂商钥匙插件未实现，见 ARCHITECTURE。

文本：写入系统剪贴板并注入粘贴（`KEYCODE_PASTE`，失败则 Ctrl+V）。中英文与 Chrome 输入框均走此路径。会覆盖电视当前剪贴板。失败 `error TEXT`。

鼠标：`pointer` 相对位移 + 单击。插件叠一层可见光标（多数盒子不画系统指针），移动发 `SOURCE_MOUSE` hover，单击在光标处注入触控点击。无系统插件时鼠标模式不可用。

设置：收到 `KEYCODE_SETTINGS`(176) 时 `startActivity(Settings.ACTION_SETTINGS)`（失败再试 `TV_SETTINGS` / 设备信息），不依赖盒子处理该键。

信号源：注入 `KEYCODE_TV_INPUT`(178)。智能电视通常切换 HDMI/输入源；机顶盒等无电视输入的设备可能无效果。

音量：`AudioManager.adjustStreamVolume(STREAM_MUSIC, …, FLAG_SHOW_UI)`。

安装 APK：`ACTION_VIEW` + `application/vnd.android.package-archive`。API < 24 可用 `file://`，7.0+ FileProvider。走系统确认页。

打开应用：LAUNCHER 或 Leanback。列表只含可打开的应用，带占用大小（能读到的 APK/数据/缓存，否则退回 APK 文件体积）与 `extractable`。图标 `GET /apps/icon`。提取 APK `GET /apps/apk` 仅用户应用且无分体包（必要时经本机 adbd 拷出）；系统应用与分体包拒绝，避免半包损坏。卸载：`ACTION_DELETE`，系统应用不调起。

文件只写 `/sdcard/PocketRemote/inbox|apk`，拒绝 `..`。

设备信息：`info` 回 `info_ok`（含 SoC `ro.board.platform`、`/proc/cpuinfo` 的 CPU）。`clean` 只结束后台应用（`killBackgroundProcesses` + `am kill-all`），不删缓存、不删文件。

PIN：6 位数字，60 秒，错误 3 次关闭。Token 与设备 `id`（UUID）持久化。

---

## 验收顺序

1. 听 `17880`，用 `websocat` 完成 hello / hello_ok（可无手机）
2. PIN 与 token 持久化
3. NSD 宣告 + UDP 应答
4. 真机方向键；设置键打开系统设置页
5. `text` → 剪贴板粘贴
6. 增强模式下 `pointer` 出现光标并可点击
7. PUT 上传到 `apk/` 并调起安装
8. `apps` / 打开 / 卸载

主测试：Android 4.2–4.4 盒子 + 一台 API 21+ 盒子。模拟器 NSD 过了不算发现完成。
