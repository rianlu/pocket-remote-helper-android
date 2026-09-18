# Pocket Remote Helper（Android 电视）

安卓电视/盒子助手（口袋遥控助手）。接收 [pocket-remote-android](https://github.com/rianlu/pocket-remote-android) 的按键、文本和文件。

| | |
|---|---|
| GitHub | https://github.com/rianlu/pocket-remote-helper-android |
| applicationId | `com.pocketremote.helper` |
| minSdk | 16（Android 4.1） |
| 协议（权威） | [`docs/PROTOCOL.md`](docs/PROTOCOL.md) |
| 本端规格 | [`docs/SPEC.md`](docs/SPEC.md) |
| 架构说明 | [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) |
| AI 开发说明 | [`AGENTS.md`](AGENTS.md) |

本仓库只含电视端。禁止 Compose / OkHttp 4。

许可证：[Apache License 2.0](LICENSE)。可商用、可修改、可再分发，保留版权与许可证声明即可。

## 构建

```bash
export ANDROID_HOME=/path/to/Android/sdk
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :plugin:signPlatform
# APK: plugin/build/outputs/apk/debug/plugin-platform.apk
```

只装助手：`app/build/outputs/apk/debug/app-debug.apk`。插件已打进 assets，打开助手后会静默安装（无桌面图标）。固件不是 AOSP platform 测试证书时走本机 adbd（需电视已开网络调试）。手机连接页可选增强 / ADB，`hello.inject` 锁定本连接通道；遥控页与电视主页显示当前通道。注入失败时提示按键不可用并引导打开网络调试，不作为「受限模式」提供。

装到盒子后打开「口袋遥控助手」。

**模拟器**（Mac 不能直连模拟器 IP，必须转发）：

```bash
adb forward tcp:17880 tcp:17880
python3 scripts/test_ws.py
# 看电视上的 6 位码，回车前输入即可
# 或：python3 scripts/test_ws.py 672821
```

日志在 `scripts/logs/test_ws-时间.log`，把该文件发出来即可分析。

单步命令仍可用：`hello` / `pin` / `key` / `text` / `apps`。

**真机同一 Wi-Fi**：`python3 scripts/test_ws.py --host 192.168.1.20`
