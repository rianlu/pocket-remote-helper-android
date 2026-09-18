# Pocket Remote Helper

**口袋遥控助手** — Android 电视 / 盒子端。

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84.svg?logo=android&logoColor=white)](https://www.android.com)
[![API](https://img.shields.io/badge/API-16%2B-brightgreen.svg)](https://developer.android.com/guide/topics/manifest/uses-sdk-element#ApiLevels)
[![Version](https://img.shields.io/badge/version-0.2.1-blue.svg)](https://github.com/rianlu/pocket-remote-helper-android)
[![Java](https://img.shields.io/badge/Java-8-ED8B00.svg?logo=openjdk&logoColor=white)](https://openjdk.org)

接收 [口袋遥控](https://github.com/rianlu/pocket-remote-android) 的按键、文本、鼠标和文件。常驻前台服务，本仓库不含手机 App。

| | |
|---|---|
| 桌面名 | 口袋遥控助手 |
| GitHub | https://github.com/rianlu/pocket-remote-helper-android |
| applicationId | `com.pocketremote.helper` |
| 插件 | `com.pocketremote.helper.plugin`（无桌面图标） |
| 版本 | 0.2.1（插件 0.2.0） |
| minSdk / targetSdk | 16（Android 4.1） / 33 |
| UI | Android View（不用 Compose） |
| 配对仓库 | [pocket-remote-android](https://github.com/rianlu/pocket-remote-android) |

## 功能

- NSD / UDP 宣告，WebSocket `17880` + HTTP 传文件
- 开机拉起（部分 4.x 需加入自启动白名单）
- 按键注入：系统签名插件 → 本机 adbd `input` → 仅 4.x 进程内 `input`
- 文本写入电视剪贴板；增强模式下相对鼠标
- 应用列表 / 打开 / 卸载；用户应用可提取完整 APK
- 主页显示连接状态与 PIN；通知栏「遥控服务运行中」

## 不做

输入法、无障碍、完整文件管理器、静默安装用户上传的 APK、ADB 当遥控协议、Compose / OkHttp 4。

## 要求

- Android 4.1 及以上电视或盒子
- 与手机同一局域网
- **增强**（推荐）：固件能接受仓库内 AOSP platform 测试证书，助手会把插件打进 `assets` 并尽量静默安装
- **ADB**：电视已打开网络调试；连上后本会话通道锁定，需断开才能改

注入失败时界面提示打开网络调试，不把它当成一种可用「受限模式」。

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

Mac 连模拟器必须转发（不能直连模拟器 IP）：

```bash
adb forward tcp:17880 tcp:17880
python3 scripts/test_ws.py
# 看电视上的 6 位码，回车前输入即可
# 或：python3 scripts/test_ws.py 672821
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

## 相关仓库

- 手机遥控：[rianlu/pocket-remote-android](https://github.com/rianlu/pocket-remote-android)

## 许可证

[Apache License 2.0](LICENSE)。可商用、可修改、可再分发，保留版权与许可证声明即可。
