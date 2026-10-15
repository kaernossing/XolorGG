# ColorCamera

[English](COLORCAMERA.md) | **简体中文**

**只想双击电源键打开系统相机？安装 ColorCamera。**
它是 ColorGoogle 仓库里的独立 APK，有自己的包名、设置和代理，不覆盖 ColorGoogle，不要求 Google、Gemini 或 Shizuku。

[下载 1.0.0 预发布版](https://github.com/joeyandu/ColorGoogle/releases/tag/colorcamera-v1.0.0) · [验证记录](COLORCAMERA-VALIDATION.md) · [ColorGoogle](../README.zh-CN.md)

> **当前为预发布版：独立 APK 的真机验收待完成。** 底层相机路径此前在 ColorGoogle 中经过 OPPO Find X8 Ultra / PKJ110、Android 17 / ColorOS 17.0.0.104 实测，但不能据此认定新拆分 APK 已通过真机验证。按用户要求，本轮未安装到手机、未修改手机设置。其他设备及 Windows 操作尚未实测。

## 功能与原理

系统识别双击电源键、唤醒屏幕并尝试打开钱包快捷页面。用户通过 ADB 激活的 shell 代理严格匹配已验证的系统调用者、action 和组件，阻止这次钱包启动，然后请求打开系统相机。它不通过普通按键日志推算双击。

- 解锁时打开普通 OPPO 相机；锁屏时打开安全相机，不绕过锁屏或私人照片的解锁要求。
- 一个功能、一个开关。关闭后恢复系统原来的双击行为，而不是关闭系统钱包快捷键。
- 不转接长按、语音、手势条或桌面搜索；这些入口仍按系统原有设置工作。
- 不预热相机、不使用麦克风、不读取照片或系统日志，不包含网络客户端、Google 配置或支付/NFC 设置修改。
- 准备阶段使用 Binder 查询，保留 `am start -W`。日志中的“请求已接受”不代表已经独立确认相机界面出现。

## 安装前

1. 使用手机主用户。本版仅适配已观察到的 OPPO 钱包快捷入口和 `com.oplus.camera`。最低 Android 12L/API 32 只表示能安装，不表示兼容所有系统。
2. 保留系统钱包，开启系统的**双击电源键打开钱包**。不要改成系统的双击相机设置：那是另一条入口，不在本版拦截范围内。
3. **先关闭 ColorGoogle 主服务，再激活 ColorCamera。** 两款应用可共存安装，但代理不能同时运行。新代理使用进程生命周期互斥锁，启动脚本也检查旧版 ColorGoogle 代理。检测到其他系统活动监视器时拒绝激活；不要同时使用 `am monitor`、`monkey` 或类似工具。系统没有原子比较并注册控制器的接口，无法完全消除其他工具恰好同时注册造成的竞争。
4. 开启 USB 调试并授权自己的电脑。使用 Google 官方 [Platform-Tools](https://developer.android.com/tools/releases/platform-tools)，不需要 Root 或安装 Shizuku。
5. 实测 Mac/OPPO 组合中，O+Connect 后台 USB 服务可能中断 ADB，关闭窗口并不足够。请阅读 [O+Connect 冲突提示](../README.zh-CN.md#安装推荐使用电脑-adb)。Windows 可能有类似问题，但未经证实。

## 安装与激活

从同一 Release 下载 `ColorCamera-1.0.0.apk`、`activate-camera.sh` 和 `SHA256SUMS`，安装前将两个输出哈希与 SHA256SUMS 对照。签名证书 SHA-256：

```text
e52320218f36db099e6971b0070bba40eec451c1a69f634bb073be26a81f236e
```

可用 Android SDK 的 `apksigner verify --verbose --print-certs ColorCamera-1.0.0.apk` 核验签名。已有 ColorCamera 若签名不同，**不要直接卸载或清数据**，先查明冲突。ColorGoogle 的包名不同，不会被覆盖。

### macOS

在下载文件所在目录执行。先通过 `adb devices -l` 选定手机，再替换 `SELECTED_SERIAL`，多设备时不能猜测。有 Homebrew 可用 `brew install --cask android-platform-tools` 安装，否则使用上面的 Google 官方下载。

```sh
shasum -a 256 ColorCamera-1.0.0.apk activate-camera.sh
adb devices -l
adb -s SELECTED_SERIAL install --no-incremental -r ColorCamera-1.0.0.apk
```

打开 **ColorCamera**，检查相机入口，按说明开启系统快捷键，再开启 **Enable camera shortcut**。按需允许通知；通过后台设置入口允许系统提供的后台运行、自启动选项。然后执行：

```sh
adb -s SELECTED_SERIAL push activate-camera.sh /data/local/tmp/activate-camera.sh
adb -s SELECTED_SERIAL shell sh /data/local/tmp/activate-camera.sh
adb -s SELECTED_SERIAL shell content call --uri content://io.github.joeyandu.colorcamera.bridge --method status
```

预期状态包含 `active=true`、`state=RUNNING`、`version=10000`。如果另一代理正在运行，请到对应应用关闭，而不是直接杀进程。控制器归属不明、相机不可用或 Binder 接口不支持时会拒绝激活并记录原因，不自动改走另一条启动路径。

### Windows PowerShell

下载并解压官方 Platform-Tools，使用完整路径和 PowerShell 调用运算符。在 Release 下载目录执行：

```powershell
$adbExe = 'C:\Android\platform-tools\adb.exe'
$deviceSerial = 'SELECTED_SERIAL'
Get-FileHash .\ColorCamera-1.0.0.apk -Algorithm SHA256
Get-FileHash .\activate-camera.sh -Algorithm SHA256
# 将两个结果与 SHA256SUMS 对照，通过后再继续。
& $adbExe devices -l
& $adbExe -s $deviceSerial install --no-incremental -r .\ColorCamera-1.0.0.apk
# 先打开 ColorCamera，按说明设置并开启开关，再执行下面的命令。
& $adbExe -s $deviceSerial push .\activate-camera.sh /data/local/tmp/activate-camera.sh
& $adbExe -s $deviceSerial shell sh /data/local/tmp/activate-camera.sh
& $adbExe -s $deviceSerial shell content call --uri content://io.github.joeyandu.colorcamera.bridge --method status
```

脚本在 Android 上执行，避免 PowerShell 提前展开 Android shell 变量。这些命令经过检查，但未在 Windows 上运行。如果有意使用非默认 ADB 服务端口，所有命令都应在 `-s` 前加同一个 `-P PORT`。

## 日常使用、停止与恢复

激活后拔掉 USB，分别测试解锁、锁屏亮屏和熄屏双击。退出相机后不应露出旧钱包快捷页面；锁屏相机访问旧照片应要求解锁。不能仅凭进程存在或 `Status: ok` 认定这些测试通过。

开关保存的是用户的使用意图，状态另行显示代理连接是否就绪。拒绝通知会显示“通知受限”，不会误报代理停止。首次安装不会自动开启接管。

关闭开关会取消排队请求、停止拦截并请求代理退出，已经启动的相机不会被强制关闭。应用通信进程消失时代理停止拦截，按 1/2/4/8/16/30 秒间隔重连受保护通道；重新读取开关后才能恢复，不补发断线期间的事件。

手机重启、ADB 服务重启、系统清理或强停可能结束代理。**这不是永久授权。** ColorCamera 1.0.0 不包含无线配对或手机端自动恢复。需要重新打开应用检查开关，通过已授权电脑再次激活；应用后台权限无法让 shell 代理跨越重启。

切回 ColorGoogle 时，先关闭 ColorCamera，等待代理退出，再开启并激活 ColorGoogle。两款应用不会自动修改对方保存的设置。新版 Mac 恢复助手检测到 `colorcamera-agent` 后拒绝恢复 ColorGoogle；旧助手需要通过 `tools/install_usb_recovery.py` 更新或停止。该助手用于 ColorGoogle，不是 ColorCamera 自启服务。

## 日志与隐私

应用的 **Diagnostics** 按钮查看自己的事件日志。代理日志通过 ADB 读取：

```sh
adb -s SELECTED_SERIAL pull /data/local/tmp/colorcamera-events.log
adb -s SELECTED_SERIAL pull /data/local/tmp/colorcamera-events.log.1
adb -s SELECTED_SERIAL pull /data/local/tmp/colorcamera-start.log
```

`.1` 文件仅在轮转后存在。应用与代理每端保留两份、每份最多 128 KiB 的事件日志，只记录会话状态、调用结果和耗时，不记录照片、语音、搜索内容、配对密钥或完整系统日志，不上传任何信息。旧记录会轮转，出现问题后应尽快导出，分享前自行检查。卸载应用不一定删除 `/data/local/tmp` 的 shell 日志；停止代理后可只删除上述日志及对应轮转文件。

## 编译、来源与许可

版本 **1.0.0 / 10000**，包名 `io.github.joeyandu.colorcamera`。`camera` 是独立应用，`shortcut-core` 是共用的生命周期、准确匹配、安全检查、耗时和控制器归属工具，`app` 仍是 ColorGoogle。Java 包名保留上游源码沿革，应用包名和通信 authority 相互独立。

使用 JDK 17、Android SDK 36，参考 `keystore.properties.example` 配置自己的签名，不要提交密钥，然后运行：

```sh
JAVA_HOME=/path/to/jdk17 bash ./gradlew :shortcut-core:testReleaseUnitTest :camera:testReleaseUnitTest :camera:assembleRelease :camera:lintRelease :app:testReleaseUnitTest :app:assembleRelease :app:lintRelease
python3 -m unittest discover -s tools/tests -v
```

APK 只依赖仓库内公共库和 Android 平台，没有加入第三方运行 SDK。JUnit 只用于测试；仓库第三方声明也包含另一款 ColorGoogle 应用使用的依赖。

ColorCamera 基于 ColorGoogle 与 EvokerUniverse 的 MindTrigger Assist v16.2.0，使用 **GPL-3.0-only** 开源。仓库保留 MiCTS 上游署名，但 ColorCamera APK 不包含圈搜调用实现。详见 [LICENSE](../LICENSE)、[NOTICE](../NOTICE.md) 和[第三方声明](../THIRD_PARTY_NOTICES.md)。本项目不是 OPPO 或 Google 官方产品。
