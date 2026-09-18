# AOSP platform 测试证书

这是 AOSP 公开的 `platform` 证书（SHA1 `27:19:6E:38:6B:87:5E:76:AD:F7:00:E7:EA:84:E4:C6:EE:E3:3D:FA`），不是密钥。

许多晶晨/电信盒子的 `framework-res` 用同一把钥匙。插件用它签名并声明 `sharedUserId="android.uid.system"`，才能拿到 `INJECT_EVENTS`。

固件不是这把钥匙的盒子会装失败（`SHARED_USER_INCOMPATIBLE`），助手会回退到普通 `input`（通常无效）。
