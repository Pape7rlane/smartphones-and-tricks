# ppSR Android 独立版

这是无需 Termux 的独立 Android APK 项目。APK 内嵌 Python 3.14、`build/` 中的服务端、
Flask、Requests、Protobuf 和 dataclasses-json，并通过 Android 前台服务运行。

## 云端编译

1. 将整个目录（必须包含 `build/`）推送到 GitHub 仓库。
2. 打开 **Actions > Build standalone Android APK > Run workflow**。
3. 构建完成后下载 `ppSR-Standalone-APKs` artifact。

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
半透明功能按钮。页面标题为 `ppSR`，版本为 `4.5.54`。

- 直接启动/停止 `127.0.0.1:21000` 本地服务。
- 切换开拓者和三月七命途。
- 按命途选角、整个命途加入、手输 ID、删除和清空配队。
- 服务通过 Android 前台通知持续运行。
- 更新 APK 时保留 `persistent.json`、`freesr-data.json` 和 `hotfix.json`。

## PNG 图标和背景

Manifest 和图标切换功能直接读取 `res/drawable-nodpi` 中的图片：

- `icon_default.png`：默认桌面图标。
- `icon_round.png`：圆形图标选项。
- `icon_alt.png`：备用图标选项。
- `background.jpg`：启动界面全屏背景。

替换同名 PNG/JPG 后重新云编译，不需要修改 Java。App 内可在三套已打包 PNG 之间切换。
原 `run.sh` 继续保留，但 APK 不会执行它。
