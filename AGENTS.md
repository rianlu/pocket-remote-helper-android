# AGENTS.md

给本仓库写代码的 AI。先读文档再改代码。

| 文件 | 用途 |
|---|---|
| [`docs/PROTOCOL.md`](docs/PROTOCOL.md) | 协议权威文本，须与 `pocket-remote-android` 的同名文件一致 |
| [`docs/SPEC.md`](docs/SPEC.md) | 本仓库（电视端）怎么实现 |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | 形态、范围、技术栈，不是 UI 规范 |

## 本仓库

**pocket-remote-helper-android**：安卓电视/盒子助手（口袋遥控助手）。手机在独立仓库 `pocket-remote-android`。不要在本仓库写手机 App 或鸿蒙。

## 目录结构（禁止再平铺到 `com.pocketremote.helper` 根包）

```
app/src/main/java/com/pocketremote/helper/
  PocketRemoteApp.java          Application
  MainActivity.java         主界面
  RemoteService.java        前台服务入口
  BootReceiver.java         开机启动
  protocol/                 协议常量与 JSON 分发
  inject/                   input / 本机 adbd / 系统插件客户端
inject-api/                 AIDL IPocketInject（助手与插件共用）
plugin/                     系统签名注入 APK（com.pocketremote.helper.plugin）
  net/                      WebSocket+HTTP、UDP、NSD
  store/                    PIN、token、文件、应用列表
  ui/                       主线程状态总线
```

新类必须放进对应子包，不要在根包继续堆业务类。Manifest 里组件用相对名（`.store.FileShareProvider`）。

## 提交说明

- 语言：**中文**。标题一行说清做了什么；必要时正文补原因或范围。
- 不要用英文写 commit message。

## 注释

- 语言：**中文**。
- 每个 **public 类** 必须有一句话类注释（做什么、限制是什么）。
- 非显而易见的方法（注入、路径校验、握手、WebSocket 帧）写方法注释。
- 不要给 getter / 显而易见的 onCreate 写空话注释。
- 不要用英文复述方法名。

## 改协议

只改 `docs/PROTOCOL.md`，并在 **remote 仓库做同样修改**，保持两份文件一致。不要为了省事只改一端。不要发明第二套字段。`protocol/Constants.java` 必须与 PROTOCOL 表逐字一致。

## 技术

- minSdk 16，compileSdk 35，Java 8 字节码
- **禁止 Compose、OkHttp 4、Ktor、Hilt、DataStore、WorkManager、AsyncTask**
- HTTP 服务用自建 ServerSocket（已有 `WsHttpServer`）；不要为客户端再拉 OkHttp 4
- 线程：`HandlerThread` / `Executor` / 具名 `Thread`

## 禁止

- 输入法、无障碍
- ADB 当遥控；第一期不扫厂商口装包
- 静默 `pm install`
- MQTT、广告 SDK、ConnectSDK
- 路径逃逸出 `/sdcard/PocketRemote/`
- 把手机端代码放进本仓库

缺规格就问，不要猜。按键和发现须在 Android 4.2–4.4 真盒子验证；可用 `python3 scripts/test_ws.py` 先验 hello。
