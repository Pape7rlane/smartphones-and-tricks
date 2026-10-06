# ppSR Android 独立版

这是无需 Termux 的独立 Android APK 项目。APK 内嵌 Python 3.14、`build/` 中的服务端、
Flask、Requests、Protobuf 和 dataclasses-json，并通过 Android 前台服务运行。

## 云端编译

1. 将整个目录（必须包含 `build/`）推送到 GitHub 仓库。
2. 推送到 `ppSR-4.6.51-测试UI`、`main` 或 `master` 后会自动触发
   **Build standalone Android APK**；也可以在 Actions 页面手动运行。
3. 构建完成后下载 `ppSR-Standalone-APKs` artifact，其中同时包含 debug 和 release APK。

## 启动与密码

点击“启动服务器”会打开密码输入框。首次使用必须输入服主提供的密码；之后可以留空继续使用
已保存密码。服主更新密码后，再次输入新密码即可覆盖旧密码。

密码保存在 `/storage/emulated/0/Download/ppSR/misc/persistent.json` 中。Python 启动桥会调用
服务端原有的 `check_access` 校验，密码错误或过期时不会启动服务器。

首次启动必须授予“所有文件访问”权限。App 会自动创建 `Download/ppSR`，把完整服务端安装到
该目录并直接从此处运行；不需要手动创建文件夹。

启动服务器后，点击“打开 SRTools 完整配置”，网站使用默认的 `RobinSR` 和
`http://localhost:21000` 连接。点击 **Sync** 后，角色、终结技能量、技能、光锥、遗器、怪物
和战斗配置会完整保存到 `misc/freesr-data.json`。服务端内置的 `/srtools` 路由支持网站所需的
`POST`、`OPTIONS` 和跨域响应。

## 界面与功能

启动页参考 `Pape7rlane/paper-airplane`：全屏图片背景、暗色遮罩、白色标题、服务器状态和
半透明功能按钮。页面标题为 `ppSR`，版本为 `4.6.51`。

- 直接启动/停止 `127.0.0.1:21000` 本地服务。
- 切换开拓者和三月七命途。
- 按命途选角、整个命途加入、手输 ID、删除和清空配队。
- 原生地图传送器，包含 85 张地图以及中英文名称，并按战斗/探索类型筛选。
- 服务通过 Android 前台通知持续运行。
- 在 App 内实时查看服务器输出，关闭日志窗口不会停止服务。
- 启动日志按模块加载、配置、密码、资源和网络服务分阶段显示；超过 120 秒会自动报错退出。
- 更新 APK 时保留 `persistent.json` 和 `freesr-data.json`；版本对应的 `hotfix.json` 会更新。

## 地图传送

地图传送器已原生集成到 Android 界面，不需要 PowerShell、BAT、Termux 或 Windows
`ppsr.exe`。内置数据包含 8 个世界和 85 张地图，其中有 16 张花萼战斗地图和 69 张探索地图。

使用步骤：

1. 如果服务器正在运行，先点击“停止服务器”。停止操作会结束服务进程，需要重新打开 App。
2. App 会读取 Android 系统语言：中文系统锁定为 CN，其他系统锁定为 EN。两种情况下都会隐藏语言切换按钮，
   并忽略以前保存的手动选择；世界、地图名称和地图说明会跟随系统语言。
3. 在“快捷功能”中点击“地图传送 / Set spawn location”。
4. 选择“全部地图”“可战斗地图（花萼）”或“仅探索地图”。
5. 依次选择世界和地图，确认 Plane、地图层级、坐标及地图说明。
6. 点击“传送”。App 会更新 `misc/persistent.json`，并在同一目录生成
   `persistent.json.bak` 备份。
7. 返回首页并启动服务器，重新进入游戏后即可在新地图出生。

服务器只在启动时读取出生地图，因此运行中不能切图，也不会在当前游戏会话中即时传送。
每次传送都会覆盖上一份 `.bak`；需要恢复时，在服务器停止状态下用文件管理器将
`persistent.json.bak` 恢复为 `persistent.json`。

战斗地图会写入对应的花萼坐标、Group ID、Inst ID、Entity ID 和 Prop ID。探索地图会将
花萼放到地下，减少误触后卡在加载界面的风险。地图选择只修改 `persistent.json` 的 `scene`
对象，不会覆盖密码、配队、角色配置或 `freesr-data.json`。

地图及坐标数据来自
[CAtlas0784/Escape-penacony](https://github.com/CAtlas0784/Escape-penacony)
`main@9acf35a`，对应 ppSR 4.6.5X 数据结构。

## 从原地图仓库更新

这里的“原仓库”是 `CAtlas0784/Escape-penacony`。Android 版只复用其中的
`scenes.json` 地图数据；不要复制 `ppsr_teleport.ps1`、`change_scene.bat`、
`start_ppsr.bat` 或查找/重启 `ppsr.exe` 的代码，因为这些仅适用于 Windows。

在 `smartphones-and-tricks` 仓库根目录执行：

```sh
git ls-remote https://github.com/CAtlas0784/Escape-penacony.git refs/heads/main

map_upstream="$(mktemp -d)"
git clone --depth 1 --branch main \
  https://github.com/CAtlas0784/Escape-penacony.git "$map_upstream"
git -C "$map_upstream" rev-parse HEAD
cp "$map_upstream/scenes.json" app/src/main/assets/scenes.json
```

记下 `rev-parse` 输出的新提交 SHA，然后检查地图结构和数量：

```sh
node -e 'const d=require("./app/src/main/assets/scenes.json"); const s=d.planets.flatMap(p=>p.scenes); const required=["name","name_zh","desc","desc_zh","plane_id","x","y","z","map_layer","calyx_supported"]; const bad=s.filter(x=>required.some(k=>x[k]===undefined)); console.log({worlds:d.planets.length,scenes:s.length,combat:s.filter(x=>x.calyx_supported).length,exploration:s.filter(x=>!x.calyx_supported).length,invalid:bad.length}); if(bad.length) process.exit(1)'

git diff --check
git diff -- app/src/main/assets/scenes.json
```

更新时还必须完成以下事项：

1. 将本文和根目录 `README.md` 中的世界数、地图数、战斗/探索数量改为校验结果。
2. 将两份 README 中的地图来源提交号更新为新上游提交的短 SHA。
3. 如果上游改了字段名称或嵌套结构，同步修改 `SceneCatalog.java`；如果改了写入
   `persistent.json` 的字段，同步修改 `ConfigStore.applyScene()`。
4. 在 `app/build.gradle` 递增 `versionCode`，保证手机可以覆盖安装新版 APK。
5. 提交并推送到 `ppSR-4.6.51-测试UI`，等待 **Build standalone Android APK**
   工作流通过，再下载新的 `ppSR-Standalone-APKs`。
6. 真机至少测试一张探索地图和一张花萼战斗地图，并确认
   `Download/ppSR/misc/persistent.json.bak` 已生成且可以恢复。

如果上游只有 PowerShell、BAT 或 README 变化，而 `scenes.json` 没有变化，则 Android
地图数据不需要更新。不要仅凭上游提交号变化就发布新 APK。

## PNG 图标和背景

Manifest 和图标切换功能直接读取 `res/drawable-nodpi` 中的图片：

- `icon_default.png`：默认桌面图标。
- `icon_round.png`：圆形图标选项。
- `icon_alt.png`：备用图标选项。
- `background.jpg`：启动界面全屏背景。

替换同名 PNG/JPG 后重新云编译，不需要修改 Java。App 内可在三套已打包 PNG 之间切换。
原 `run.sh` 继续保留，但 APK 不会执行它。
