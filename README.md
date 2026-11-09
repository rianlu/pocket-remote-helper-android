<p align="center">
  <img src="docs/icon.png" width="128" alt="口袋遥控助手">
</p>

<h1 align="center">口袋遥控助手</h1>

<p align="center">
  安卓电视 / 盒子端。接收手机发来的按键、文字、鼠标和文件。
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" alt="Apache-2.0"></a>
  <a href="https://www.android.com"><img src="https://img.shields.io/badge/platform-Android-3DDC84.svg?logo=android&logoColor=white" alt="Android"></a>
  <a href="https://developer.android.com/guide/topics/manifest/uses-sdk-element#ApiLevels"><img src="https://img.shields.io/badge/API-16%2B-brightgreen.svg" alt="API 16+"></a>
  <a href="https://github.com/rianlu/pocket-remote-helper-android"><img src="https://img.shields.io/badge/version-0.2.8-blue.svg" alt="0.2.8"></a>
  <a href="https://openjdk.org"><img src="https://img.shields.io/badge/Java-8-ED8B00.svg?logo=openjdk&logoColor=white" alt="Java"></a>
</p>

<p align="center">
  手机端请安装 <a href="https://github.com/rianlu/pocket-remote-android"><b>口袋遥控</b></a>
</p>

本仓库只有电视端。助手常驻前台服务，在局域网里宣告自己，主页给出 6 位配对码。手机连上之后，按键由本机注入系统。

## 预览

<p align="center">
  <img src="docs/screenshots/pairing.png" width="720" alt="配对页：6 位配对码、本机地址和连接模式">
</p>

主页显示配对码、`IP:17880` 和当前连接模式。通知栏为「遥控服务运行中」。

## 功能

- NSD / UDP 宣告，供同一局域网里的手机发现。
- WebSocket `17880` 接收控制指令，HTTP 传输文件。
- 开机拉起。部分 Android 4.x 需要加入自启动白名单。
- 按键注入顺序：系统签名插件 → 本机 adbd 的 `input` → 仅 Android 4.x 在进程内调用 `input`。
- 文本写入电视剪贴板。增强模式下接收相对鼠标移动和点击。
- 应用列表、打开、卸载。用户应用可以提取完整 APK。

注入失败时提示打开网络调试，不把它当作一种可用的受限模式。

## 连接模式

手机在连接前选择，连上后本次会话不能更改：

- **增强**（推荐）：固件接受仓库内的 AOSP platform 测试证书时，助手把插件打进 `assets` 并尽量静默安装。之后可以同时使用鼠标和按键。
- **ADB**：电视已打开网络调试。这条通道只能发送按键。

## 不做

输入法、无障碍、完整文件管理器、静默安装用户上传的 APK、把 ADB 当作遥控协议、Compose / OkHttp 4。

## 开始使用

- Android 4.1 及以上电视或盒子
- 与手机在同一局域网
- 安装下面构建出的那一个 APK，打开「口袋遥控助手」

## 构建

```bash
export ANDROID_HOME=/path/to/Android/sdk
./gradlew assembleDebug
```

安装包：`app/build/outputs/apk/debug/app-debug.apk`

插件已经打进 `assets`。需要单独的签名包时：

```bash
./gradlew :plugin:signPlatform
```

产物：`plugin/build/outputs/apk/debug/plugin-platform.apk`

### 协议自检

Mac 连接模拟器必须转发，不能直连模拟器 IP：

```bash
adb forward tcp:17880 tcp:17880
python3 scripts/test_ws.py
```

真机同一 Wi-Fi：`python3 scripts/test_ws.py --host 192.168.1.20`

日志在 `scripts/logs/`。单步命令：`hello` / `pin` / `key` / `text` / `apps`。

## 项目信息

| | |
|---|---|
| applicationId | `com.pocketremote.helper` |
| 插件 | `com.pocketremote.helper.plugin`（无桌面图标） |
| 版本 | 0.2.8（插件 0.2.0） |
| minSdk / targetSdk | 16 / 21 |
| UI | Android View |

| 文档 | 内容 |
|---|---|
| [`docs/PROTOCOL.md`](docs/PROTOCOL.md) | 协议文本，须与手机仓库逐字一致 |
| [`docs/SPEC.md`](docs/SPEC.md) | 本端实现 |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | 形态、注入层与范围 |
| [`plugin/keys/README.md`](plugin/keys/README.md) | AOSP platform 测试证书 |
| [`AGENTS.md`](AGENTS.md) | 贡献约定 |

## 许可证

[Apache License 2.0](LICENSE)
