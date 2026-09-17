# PocketTV Helper（Android 电视端）规格

本仓库只实现**电视/盒子助手**。协议以 [`PROTOCOL.md`](PROTOCOL.md) 为准（须与 remote 仓库同名文件一致）。背景见 [`ARCHITECTURE.md`](ARCHITECTURE.md)。

- 桌面名：口袋遥控 TV
- applicationId：`com.pockettv.tv`
- minSdk **16**（Android 4.1），compileSdk 35
- UI：View，禁止 Compose
- 网络：Java-WebSocket 或等价；HTTP 用 OkHttp **3.12.13** 或 `HttpURLConnection`（禁止 OkHttp 4）
- 线程：`HandlerThread` / `Executor`，禁止 AsyncTask
- 字节码 Java 8

不要在本仓库写手机 App、不要写鸿蒙。

---

## 职责

常驻服务：被发现、WebSocket 收指令、本机注入按键/文本、收文件、调起安装/卸载。

不做：输入法、无障碍、完整文件管理器、ADB 当遥控、静默 `pm install`、MQTT、广告 SDK、Compose、Ktor、Hilt、DataStore、WorkManager。

---

## 组件

- `RemoteService`：前台服务，监听 `17880`（`/ws` + `/transfer/*`）和 `17882` UDP；NSD 宣告
- `BootReceiver`：开机拉起（4.x 需引导自启动白名单）
- 界面最少：主页显示连接状态 / PIN；通知栏「遥控服务运行中」
- Manifest：`LAUNCHER` + `LEANBACK_LAUNCHER`（`leanback` required=false）

权限：`INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`、`CHANGE_WIFI_MULTICAST_STATE`、`RECEIVE_BOOT_COMPLETED`、`WRITE_EXTERNAL_STORAGE`、`FOREGROUND_SERVICE`。不要无障碍、不要 `BIND_INPUT_METHOD`。

明文 HTTP 仅 RFC1918。

---

## 注入

按键：

1. `Runtime.exec("input keyevent " + code)`
2. 失败则本机 `127.0.0.1:5555`（仅当调试仍开着）
3. 再失败：`error INJECT`，`hello_ok.injectOk=false`

文本：`input text`，空格转 `%s`。失败 `error TEXT`。API 16–20 中文常失败，属预期。

音量：`AudioManager.adjustStreamVolume(STREAM_MUSIC, …, FLAG_SHOW_UI)`。

安装 APK：`ACTION_VIEW` + `application/vnd.android.package-archive`。API < 24 可用 `file://`，7.0+ FileProvider。走系统确认页。

打开应用：`getLaunchIntentForPackage`。卸载：`ACTION_DELETE` / `ACTION_UNINSTALL_PACKAGE`。

文件只写 `/sdcard/PocketTV/inbox|apk`，拒绝 `..`。

PIN：6 位数字，60 秒，错误 3 次关闭。Token 与设备 `id`（UUID）持久化。

---

## 验收顺序

1. 听 `17880`，用 `websocat` 完成 hello / hello_ok（可无手机）
2. PIN 与 token 持久化
3. NSD 宣告 + UDP 应答
4. 真机 `input keyevent`
5. `text` → `input text`
6. PUT 上传到 `apk/` 并调起安装
7. `apps` / 打开 / 卸载

主测试：Android 4.2–4.4 盒子 + 一台 API 21+ 盒子。模拟器 NSD 过了不算发现完成。
