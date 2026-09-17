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

装到盒子后打开「口袋遥控 TV」，记下屏幕上的 IP。本机可先用：

```bash
websocat ws://<电视IP>:17880/ws
```

发送：`{"v":1,"id":"1","type":"hello","payload":{}}`，应收到 `need_pin`，电视会显示 6 位配对码。
