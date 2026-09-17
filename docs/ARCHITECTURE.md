# PocketTV 架构说明

这是系统怎么拆、做什么/不做什么，**不是** Material 或 UI 视觉规范。

开源后只从 GitHub 拉取本仓库与 `pockettv-remote-android`。**本文件随仓库走**，不依赖任何本地工作区文档。

协议字段以 [`PROTOCOL.md`](PROTOCOL.md) 为准。本端怎么做见 [`SPEC.md`](SPEC.md)。

---

## 形态

```
手机 App  ── 局域网 ──►  电视助手  ──►  本机按键 / 写文本 / 收文件 / 装包
```

手机不能直接控电视。ADB 不是遥控通道，只可作为用户已开调试时的可选安装手段（第一期不做）。

| 仓库 | 角色 | minSdk |
|---|---|---|
| pockettv-remote-android | 手机客户端 | 21 |
| pockettv-helper-android | 电视助手 | 16 |

发现：NSD → 失败 UDP → 再失败手填 IP。  
文本：用户点手机「键盘」发送，不自动弹、不做输入法、第一期无障碍。  
文件：只到 `/sdcard/PocketTV/inbox|apk`，不是完整文件管理器。

4.1–4.4 中文 `input text` 常失败，提示即可。音量走 `AudioManager`。

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

纯血鸿蒙手机可以当客户端控**安卓电视助手**，协议不变，UI 用 ArkTS 另开 `pockettv-remote-harmony`。第一期不做。
