# ppSR 4.6.53PS Android

无需 Termux 的 ppSR Android 独立版。APK 内嵌 Python 3.14、原始服务端字节码和运行依赖，
可直接在手机上启动 `127.0.0.1:21000` 服务。

界面参考 [Pape7rlane/paper-airplane](https://github.com/Pape7rlane/paper-airplane)：使用 PNG
APK 图标、全屏背景、服务器状态和半透明功能按钮。

## 功能

- 启动和停止本地服务，退出控制界面后通过 Android 前台服务继续运行。
- 输入、保存和更新服务器密码。
- 切换开拓者与三月七命途。
- 按命途选择角色或加入整个命途。
- 手动输入多个角色 ID、按序号删除、清空配队。
- 内置 Escape Penacony 的 85 张地图数据，可按世界及战斗/探索类型切换出生地图；支持中英文地图名称。
- 首次启动时按 Android 系统语言自动选择 CN/EN，之后可在 App 内手动切换并保存选择。
- 在三套 `res` PNG 桌面图标之间动态切换。
- 更新 APK 时保留 `persistent.json` 和 `freesr-data.json`；版本对应的 `hotfix.json` 会更新。
- 在 App 内直接打开、编辑并保存 misc 目录中的 JSON 文件。
- 在 App 内查看可关闭、可清空的实时服务器日志。

## 存储目录与 SRTools

- 首次启动会请求“所有文件访问”权限，并自动创建
  `/storage/emulated/0/Download/ppSR`；整套服务端直接从该目录运行。
- 启动服务器后，点击“打开 SRTools 完整配置”进入
  `https://srtools.neonteam.dev/1001/detail`。
- 在网站的 **Connect PS** 中保留默认 `RobinSR` 和 `http://localhost:21000`，先点
  **Connect**，配置完成后点 **Sync**。网站会把角色、终结技能量、技能、光锥、遗器、
  怪物和战斗配置完整写入 `Download/ppSR/misc/freesr-data.json`。

## 密码读取与更新逻辑

原服务端没有联网下载密码，也没有在运行中监听密码变化。其校验流程如下：

1. 启动时由 `Manager.init()` 读取 `misc/persistent.json`。
2. `check_access()` 读取 JSON 根级字段 `key`。
3. 服务端使用北京时间 `UTC+8`，从 `2025-01-07 10:00 +0800` 开始计算经过天数。
4. 使用 `经过天数 // 7` 得到当前密码周期，因此密码每 7 天变化一次。
5. 周期编号经过内置密钥的 `HMAC-SHA256`、URL-safe Base64、字母数字过滤、截取前 8 位并转为大写，生成当前有效密码。
6. 保存的 `key` 与当前生成值完全一致时才启动服务器。
7. 密码错误或过期时，必须输入服主发布的新密码并覆盖保存，然后重新校验。

“实时更新”指每次执行校验时都根据当前北京时间重新计算密码，并不是从远程服务器实时拉取。
已经运行的服务不会因进入下一个 7 天周期自动停止；下一次启动时旧密码才会校验失败。

Android 版点击“启动服务器”会先显示密码框：首次必须输入；之后留空可以使用已保存密码；
服主更新密码后输入新密码即可覆盖。APK 随后调用原服务端的 `check_access()`，不会绕过校验。

## PNG 图标与背景

资源位于 `app/src/main/res/drawable-nodpi/`：

- `icon_default.png`：默认 APK 图标。
- `icon_round.png`：圆形图标选项。
- `icon_alt.png`：备用图标选项。
- `background.webp`：启动界面背景。

替换同名 WebP 背景或 PNG 图标后重新构建即可，不需要修改 Java 代码。

## 公告与强制更新

仓库根目录的 [`update.json`](update.json) 是公告和更新配置，可以直接自定义：

- `announcement_title`、`announcement_body`：公告标题和正文。
- `announcement_id`：公告编号；换公告时必须改这个值，用户勾选“不再显示此公告”后，新编号会再次弹出。
- `latest_version_code`：必须与 `app/build.gradle` 的 `versionCode` 一致。已安装旧版本发现更大的编号时，会强制显示更新弹窗。
- `download_url`：夸克网盘 APK 链接，例如 `https://pan.quark.cn/s/e99add200b01`。

APK 每次进入时读取最新配置；网络不可用时使用上次缓存或 APK 内置配置。普通公告可以勾选“不再显示此公告”，强制更新弹窗不能跳过。纯公告调整只需修改并推送根目录 `update.json`，无需重新构建。发布新 APK 时递增 `app/build.gradle` 的 `versionCode` 并云端构建；APK 上传到夸克后，再把 `latest_version_code` 和 `download_url` 更新到新版本。已有版本从此 APK 开始支持远程强制更新；更早的 APK 需要先手动安装一次。

## GitHub Actions 云编译

1. 打开仓库的 **Actions** 页面。
2. 选择 **Build standalone Android APK**。
3. 点击 **Run workflow**。
4. 完成后下载 `ppSR-Standalone-APKs` artifact。

工作流同时生成 debug 和使用 debug key 签名的 release APK。正式发布时应配置自己的 keystore。

更详细的 Android 说明见 [README-Android.md](README-Android.md)。

地图与坐标数据来源：[CAtlas0784/Escape-penacony](https://github.com/CAtlas0784/Escape-penacony)
`main@3c8d5ab`。其中 2 张地图（`planarcadia` 的 Starforster、Moment of Joy）仅在 4.7 Beta
测试端可用，在 4.6.x 正式端会卡在加载界面。
