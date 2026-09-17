# AGENTS.md

给在本仓库写代码的 AI。产品协议在 `docs/PROTOCOL.md`，本端规格在 `docs/SPEC.md`。先读这两份再改代码。

## 本仓库是什么

GitHub 仓库名：**pockettv-helper-android**  
安卓**电视/盒子**助手（口袋遥控 TV）。手机客户端在独立仓库 `pockettv-remote-android`。

不要在本仓库实现手机 App、不要写鸿蒙。

## 开工前

1. 读 `docs/PROTOCOL.md`（禁止改端口、JSON `type`、字段名）。
2. 读 `docs/SPEC.md`（服务、注入、minSdk 16、验收）。
3. 缺规格就问用户，不要发明第二套协议。

## 技术约束

- minSdk **16**，compileSdk 35，Java 8 字节码
- UI 只用 View；**禁止 Compose**
- HTTP：OkHttp 3.12.x 或 `HttpURLConnection`；**禁止 OkHttp 4**、Ktor
- 禁止 Hilt、DataStore、WorkManager
- 线程用 HandlerThread / Executor，禁止 AsyncTask
- 常量类必须与 PROTOCOL 表逐字一致

## 禁止

- 改 PROTOCOL 中的端口、NSD 类型、magic、JSON 字段
- 输入法、无障碍
- 用 ADB 当遥控通道；第一期不要扫厂商口装包
- 静默 `pm install`（必须走系统安装页）
- MQTT、广告 SDK、ConnectSDK
- 完整文件管理器；路径逃逸出 `/sdcard/PocketTV/`
- 把手机端代码放进本仓库

## 改协议

不要直接改 `docs/PROTOCOL.md` 凑实现。先改工作区 `pocket-tv-android/docs/TV遥控器开发参考.md`，再同步两端 PROTOCOL。

## 完成标准

可先用 `websocat` 打 `ws://<tv>:17880/ws` 验收 hello。按键和发现必须在 Android 4.2–4.4 真盒子上验证，模拟器 NSD 不算。
