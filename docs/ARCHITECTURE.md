# Pocket Remote 架构说明

这是系统怎么拆、做什么/不做什么，**不是** Material 或 UI 视觉规范。

开源后只从 GitHub 拉取本仓库与 [pocket-remote-android](https://github.com/rianlu/pocket-remote-android)。**本文件随仓库走**，不依赖任何本地工作区文档。

协议字段以 [`PROTOCOL.md`](PROTOCOL.md) 为准。本端怎么做见 [`SPEC.md`](SPEC.md)。

---

## 形态

```
手机 App  ── 局域网 ──►  电视助手  ──►  本机按键 / 写文本 / 收文件 / 装包
```

手机不能直接控电视。ADB 不是遥控通道，只可作为用户已开调试时的可选安装手段（第一期不做）。

普通应用没有 `INJECT_EVENTS`（Android 签名/特权权限），`input keyevent` 不能打进其它窗口。音量走 `AudioManager`。

早期规格把注入写成「4.x 上 input 最常见能用」，并把悟空差异写成 Support/MQTT 过时；**没写**系统插件、`/dev/input`、厂商钥匙插件。那些才是跨机型按键覆盖的来源。

悟空 TV 端（E900V22C 上拆包）不是新 API，是公开能力的产品拼装：

| 层 | 公开出处 | 悟空怎么用 | 我们 |
|---|---|---|---|
| AOSP platform 证书 + `sharedUserId=android.uid.system` | AOSP `build/target/product/security/platform.*` 公开；`INJECT_EVENTS` 为 signature\|privileged | `assets/plugin.apk` → `remoteplugin2`，与固件同一把测试钥匙 | `plugin/`，同一把钥匙 |
| `input` / `injectInputEvent` | 平台命令与隐藏 API | 插件进程内调用 | 插件 + 助手回退 |
| 本机 adbd `127.0.0.1:5555` | `service.adb.tcp.port` | `adb connect 127.0.0.1`、`start adbd` 后 `pm install` / `input` | 助手 `LocalAdb`，未主动 `start adbd` |
| `/dev/input/event*` | 内核输入；开源 EventInjector（PocketMagic，包名仍留在悟空 dex） | `libEventInjector.so` | 未做 |
| 小米/乐视/YunOS/TCL/海信插件或私有口 | 手法同系统签名；**钥匙和协议是各厂/各产品自己攒的** | `xiaomione_plugin.apk`、`letv.remoteplugin2`、`yunos.remoteplugin` 等 | 未做；§厂商端口仍只当装包备查 |

对齐目标：公开层（系统测试钥匙插件、本机 adbd、可写则 `/dev/input`）做全；不把悟空的厂商签名 APK 打进仓库。厂商层要另备对应固件证书，不能靠一颗通用插件。

| 仓库 | 角色 | minSdk |
|---|---|---|
| pocket-remote-android | 手机客户端 | 26 |
| pocket-remote-helper-android | 电视助手 | 16 |

发现：NSD → 失败 UDP → 再失败手填 IP。  
遥控：按键 + 鼠标相对指针（插件叠光标；无插件则鼠标不可用）。  
文本：用户点手机「键盘」发送，不自动弹、不做输入法、第一期无障碍。  
文件：只到 `/sdcard/PocketRemote/inbox|apk`，不是完整文件管理器。

文本由助手/插件写入剪贴板并粘贴到焦点，不走 `input text`（该命令慢且中文会崩）。音量走 `AudioManager`。设置键打开系统设置 Activity。信号源注入 `TV_INPUT`，无电视输入的盒子可无效果。

---

## 栈（相对过时遥控 App）

手机：Kotlin、AndroidX、Compose、协程、OkHttp 4。  
电视：View、Java 8、OkHttp 3.12 或 HttpURLConnection；禁止 Compose / OkHttp 4 / Ktor / Hilt / DataStore。

不要：MQTT、广告 SDK、ConnectSDK、Apache Http 遗留库。

---

## 厂商端口（仅备查，第一期不实现）

装助手时市面产品可能扫这些口。不是遥控协议。

- ADB：5555（优先），以及 5037、5114、7896、1127、30105、31015
- 小米 6095/9095，阿里 7890/13510，康佳+8086，微鲸 12321，TCL 6553/8843，海美迪 8899，VST 8051，乐视 8008，荣耀/长虹 7766，海尔 49152，爱奇艺 39621，酷开 1980/1988，PPTV 9101，海信 36669 等

助手上线后只连 `17880`。

---

## 鸿蒙手机（以后）

纯血鸿蒙手机可以当客户端控**安卓电视助手**，协议不变，UI 用 ArkTS 另开 `pocket-remote-harmony`。第一期不做。
