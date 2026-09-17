# PocketTV Helper (Android)

安卓电视/盒子助手（口袋遥控 TV）。接收 [pockettv-remote-android](https://github.com/PLACEHOLDER/pockettv-remote-android) 的按键、文本和文件。

| | |
|---|---|
| applicationId | `com.pockettv.tv` |
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
```

装到盒子后打开「口袋遥控 TV」。

**模拟器**（Mac 不能直连 `10.0.2.15`，必须转发）：

```bash
adb forward tcp:17880 tcp:17880
python3 scripts/test_ws.py hello          # 屏幕出现 6 位配对码
python3 scripts/test_ws.py pin 123456     # 换成屏幕上的数字；脚本会保存 token
python3 scripts/test_ws.py key up         # 之后命令会自动带 token 重新握手
python3 scripts/test_ws.py text hello
python3 scripts/test_ws.py apps
```

**真机同一 Wi-Fi**：

```bash
python3 scripts/test_ws.py --host 192.168.1.20 hello
```
