# AGENTS.md

给本仓库写代码的 AI。先读文档再改代码。

| 文件 | 用途 |
|---|---|
| [`docs/PROTOCOL.md`](docs/PROTOCOL.md) | 协议权威文本，须与 `pockettv-remote-android` 的同名文件一致 |
| [`docs/SPEC.md`](docs/SPEC.md) | 本仓库（电视端）怎么实现 |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | 形态、范围、技术栈，不是 UI 规范 |

## 本仓库

**pockettv-helper-android**：安卓电视/盒子助手（口袋遥控 TV）。手机在独立仓库 `pockettv-remote-android`。不要在本仓库写手机 App 或鸿蒙。

## 改协议

只改 `docs/PROTOCOL.md`，并在 **remote 仓库做同样修改**，保持两份文件一致。不要为了省事只改一端。不要发明第二套字段。

## 技术

- minSdk 16，compileSdk 35，Java 8 字节码
- **禁止 Compose、OkHttp 4、Ktor、Hilt、DataStore、WorkManager、AsyncTask**
- HTTP：OkHttp 3.12.x 或 HttpURLConnection
- 常量与 PROTOCOL 表逐字一致

## 禁止

- 输入法、无障碍
- ADB 当遥控；第一期不扫厂商口装包
- 静默 `pm install`
- MQTT、广告 SDK、ConnectSDK
- 路径逃逸出 `/sdcard/PocketTV/`
- 把手机端代码放进本仓库

缺规格就问，不要猜。按键和发现须在 Android 4.2–4.4 真盒子验证；可用 `websocat` 先验 hello。
