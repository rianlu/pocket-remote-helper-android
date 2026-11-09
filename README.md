# Pocket Remote Helper

**口袋遥控助手** — 装在安卓电视或盒子上，接收手机遥控。

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84.svg?logo=android&logoColor=white)](https://www.android.com)
[![API](https://img.shields.io/badge/API-16%2B-brightgreen.svg)](https://developer.android.com/guide/topics/manifest/uses-sdk-element#ApiLevels)
[![Version](https://img.shields.io/badge/version-0.2.8-blue.svg)](https://github.com/rianlu/pocket-remote-helper-android)
[![Java](https://img.shields.io/badge/Java-8-ED8B00.svg?logo=openjdk&logoColor=white)](https://openjdk.org)

本仓库是电视端。手机 App 在 [口袋遥控](https://github.com/rianlu/pocket-remote-android)。助手常驻前台服务，接收按键、文字、鼠标和文件，再注入到系统。

| | |
|---|---|
| 桌面名 | 口袋遥控助手 |
| applicationId | `com.pocketremote.helper` |
| 插件 | `com.pocketremote.helper.plugin`（无桌面图标） |
| 版本 | 0.2.8（插件 0.2.0） |
| minSdk / targetSdk | 16（Android 4.1） / 21 |
| UI | Android View（不用 Compose） |
| 控制端口 | WebSocket `17880`，另有 HTTP 传文件 |

## 界面

打开后全屏显示配对码、本机地址和当前连接模式。手机输入这 6 位码即可连上。

<p align="center">
  <img src="docs/screenshots/pairing.png" width="720" alt="口袋遥控助手：配对码、本机 IP 和连接模式">
</p>

## 功能

- NSD / UDP 宣告，让手机在同一局域网里发现这台电视。
- 主页显示连接状态、6 位 PIN 和 `IP:17880`。通知栏显示「遥控服务运行中」。
- 开机拉起。部分 Android 4.x 需要加入自启动白名单。
- 按键注入顺序：系统签名插件 → 本机 adbd 的 `input` → 仅 4.x 在进程内调用 `input`。
- 文本写入电视剪贴板。增强模式下接收相对鼠标移动和点击。
- 提供应用列表、打开、卸载；用户应用可以提取完整 APK。

注入失败时，界面提示打开网络调试，不把它当成一种可用的「受限模式」。

## 连接模式

手机在连接前选择，连上后这一次会话不能改，需要断开才能换：

- **增强**（推荐）：固件接受仓库内的 AOSP platform 测试证书时，助手把插件打进 `assets` 并尽量静默安装。装好后可以鼠标和按键一起用。
- **ADB**：电视已打开网络调试。这条通道只能发按键。

## 不做

输入法、无障碍、完整文件管理器、静默安装用户上传的 APK、把 ADB 当作遥控协议、Compose / OkHttp 4。

## 构建

```bash
export ANDROID_HOME=/path/to/Android/sdk
./gradlew assembleDebug
# 只装这一个 APK：app/build/outputs/apk/debug/app-debug.apk
# 插件已打进 assets；需要单独签名包时：
./gradlew :plugin:signPlatform
# plugin/build/outputs/apk/debug/plugin-platform.apk
```

装到盒子后打开「口袋遥控助手」。

### 协议自检

Mac 连模拟器必须转发，不能直连模拟器 IP：

```bash
adb forward tcp:17880 tcp:17880
python3 scripts/test_ws.py
# 看电视上的 6 位码，回车前输入即可
```

真机同一 Wi-Fi：`python3 scripts/test_ws.py --host 192.168.1.20`

日志在 `scripts/logs/test_ws-时间.log`。单步命令：`hello` / `pin` / `key` / `text` / `apps`。

## 文档

| 文件 | 内容 |
|---|---|
| [`docs/PROTOCOL.md`](docs/PROTOCOL.md) | 协议权威文本（须与手机仓库逐字一致） |
| [`docs/SPEC.md`](docs/SPEC.md) | 本端实现 |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | 形态、注入层与范围 |
| [`plugin/keys/README.md`](plugin/keys/README.md) | AOSP platform 测试证书说明 |
| [`AGENTS.md`](AGENTS.md) | 给贡献者 / AI 的仓库规矩 |

## 许可证

[Apache License 2.0](LICENSE)。可商用、可修改、可再分发，保留版权与许可证声明即可。
