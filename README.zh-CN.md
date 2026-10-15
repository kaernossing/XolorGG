<p align="center"><img src="artwork/colorgoogle-preview.png" width="112" alt="ColorGoogle 图标"></p>

# ColorGoogle

[English](README.md) | **简体中文**

## 选择你需要的应用

- **只需要双击电源键打开相机：**[ColorCamera 安装说明](docs/COLORCAMERA.zh-CN.md) · [1.0.0 预发布 APK](https://github.com/joeyandu/ColorGoogle/releases/tag/colorcamera-v1.0.0)。独立包名，不要求 Google 应用或日志读取权限，独立版真机测试待完成。
- **需要 Gemini、圈搜和 Google 搜索：**使用下文介绍的 ColorGoogle。已发布稳定版仍为 16.3.0，较新的源码改动另有记录。
- 两款应用可共存安装，但**必须先关闭一款主服务，再激活另一款**，不能同时占用系统拦截器。


**通过熟悉的 ColorOS 操作打开 Gemini 和圈定即搜。** 即使手机黑屏，也可长按电源键唤起 Gemini；长按底部手势条打开圈定即搜；点击现有桌面搜索入口打开 Google 搜索。

[下载 16.3.0](https://github.com/joeyandu/ColorGoogle/releases/tag/v16.3.0) · [安装说明](#安装推荐使用电脑-adb) · [Agent 提示词](#如何使用让-agent-协助安装) · [故障排查](#故障排查) · [修改记录](CHANGELOG.md)

ColorGoogle 是基于 **EvokerUniverse 的 [MindTrigger Assist v16.2.0](https://github.com/evokermc098-coder/MindTriggerAssist/tree/v16.2.0) 修改的开源项目**。圈定即搜调用沿用上游参考 parallelcc 的 [MiCTS](https://github.com/parallelcc/MiCTS) 实现的路径。组合应用继续使用 **GPL-3.0-only**：你可以按许可证学习、修改、编译和再发布；分发 APK 时须满足对应源码等要求。保留上游署名，详见 [LICENSE](LICENSE)、[NOTICE](NOTICE.md) 和[第三方许可](THIRD_PARTY_NOTICES.md)。

本项目为**非官方社区项目**，不是 OPPO 或 Google 产品。名称、图标不代表官方认可，代码开源许可也不授予商标权。

## 功能

| 操作 | 效果 | 设置入口 |
|---|---|---|
| 长按实体电源键，包括完全息屏时 | 打开已配置的 Google/Gemini 助手会话 | 开启电源键触发，将 Google 助手设为 Gemini |
| 长按底部手势条 | 在当前画面打开圈定即搜 | 开启系统长按导航条助手入口和 ColorGoogle 手势触发 |
| 说系统唤醒词，例如“小布小布” | 检测系统语音唤醒事件并转向 Gemini | 开启系统语音唤醒及 ColorGoogle 语音触发 |
| 点击现有 ColorOS 桌面搜索按钮 | 打开 Google 搜索；返回时回到桌面 | **Beta → Open Google from desktop search** |
| ADB 代理退出且本机无线调试可用 | 可选的手机端有限次数自动恢复 | **Beta → Wireless ADB recovery** |

桌面搜索功能是**拦截并重定向现有 ColorOS 桌面入口**，不新增独立小组件、不替换桌面。**不要卸载系统全局搜索**：Android 需要先解析原始 Intent，拦截才能发生。

### 相比 MindTrigger Assist v16.2.0 修改了什么？

| ColorGoogle 阶段 | 修改内容 |
|---|---|
| .1 | 电源键日志直接触发 Gemini，不再等待小布服务日志；按已接受事件计算 2.5 秒去重；补充通知授权与状态区分；名称、图标和默认零延迟 |
| .2–.3 | 四色 G 配绿色飘带；缩小主体，统一自适应、桌面及应用内图标留白 |
| .4 | 增加可选桌面搜索启动前拦截，清理旧系统搜索任务，修复返回时露出旧页面 |
| .5 | 长按改用共享 shell 代理读取日志；验证外部 UID 日志，区分代理和日志状态，不再把自身心跳当作健康证明 |
| .6 | 可选手机端无线配对和有限次数代理恢复；完善进程退出、锁释放及静默启动健康检查 |
| **16.3.0** | 首次公开发布；新安装默认英文、新功能英文提示、通用激活脚本、双语使用/恢复说明及 Agent 提示词 |

沿用原有圈定即搜调用协议，详见[完整修改记录](CHANGELOG.md)和[架构说明](docs/ARCHITECTURE.md)。不包含语音提示词前缀、独立 Google 搜索小组件或公开的外部 Gemini 启动接口。

### 兼容性与限制

- 设备证据来自 **OPPO Find X8 Ultra / PKJ110，Android 16，ColorOS 16.0.10.501**。各版本实测情况见[验证记录](docs/VALIDATION.md)。其他机型、桌面、系统和 Google 版本尚未验证。
- APK 最低要求 **Android 12L / API 32**；能安装不等于功能兼容。
- 需要可用的 Google 服务、Google 应用，以及账号/地区允许使用的 Gemini。先手动打开 Google 和 Gemini，完成首次设置。
- 无需 Root，推荐手边有电脑时使用 ADB；不要求安装 Shizuku。
- 代理成功启动后可以拔掉 USB，但**不等于永久授权，也不保证重启、强行停止、进程被终止后仍然运行**。
- 无线自恢复需要主服务运行、Wi-Fi 已连接、无线调试开启且本机配对有效。它不能自行重新开启无线调试。测试手机曾多次出现无线调试关闭，关闭原因尚未查明。
- 使用 Android/ColorOS 隐藏接口，系统或 Google 更新可能改变行为。不要同时运行桌面搜索拦截与 `am monitor`、`monkey` 或其他 Activity controller。
- 新安装默认**英文**，保留用户已明确选择的语言。上游越南语、印尼语、泰语包仍可选；新增 Beta 和状态提示为英文。中文 README 不代表应用已有中文翻译。

## 操作界面

测试 OPPO 上的 ColorGoogle 16.3.0。桌面搜索和无线自恢复位于 Beta。截图仅展示应用设置，不包含账号或配对资料。

<p><img src="docs/images/desktop-search-16.3.0.png" width="280" alt="Desktop search settings"><img src="docs/images/wireless-recovery-16.3.0.png" width="280" alt="Wireless ADB recovery settings"></p>

## 安装：推荐使用电脑 ADB

> **使用 O+Connect（OPPO 互联）的用户请注意 USB 冲突**
>
> 在我们实测的 Mac 和 OPPO 手机上，O+Connect 后台服务会在连接 USB 时发起 Android 附件模式切换。该过程与手机 ADB 服务重启、ColorGoogle 特权代理退出相关，导致唤醒和搜索功能失效，而应用权限仍然保留。
>
> 只关闭 O+Connect 窗口并不够，它的后台服务仍可能运行。停用后台服务和自动启动后，多次 USB 重连测试未再复现此问题。
>
> 如果遇到类似情况，请检查 O+Connect 后台服务及自启设置，待 USB 连接稳定后，通过 ADB 重新激活 ColorGoogle。停用这些服务会影响 O+Connect 的互联功能；软件更新后也应重新检查。
>
> **Windows 尚未实测，推测可能存在类似冲突，但目前未经证实。** 以上结论仅来自已测试的设备组合，不代表所有电脑和手机都会出现。

### 1. 准备与安装

1. 从 [Release](https://github.com/joeyandu/ColorGoogle/releases/tag/v16.3.0) 下载 `ColorGoogle-16.3.0.apk`、`SHA256SUMS` 和对应源码 ZIP。源码包内含 `tools/activate-agent.sh`。
2. 安装 Google 官方 [SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools)。Mac 已有 Homebrew 时也可执行 `brew install --cask android-platform-tools`；Windows 解压官方包后可直接运行 `adb.exe`。
3. 开启开发者选项和 USB 调试，用数据线连接并允许电脑调试。执行 `adb devices -l`，把所有示例中的 `SERIAL` 替换成目标实体手机序列号。多设备时必须明确选择。
4. 对照 `SHA256SUMS` 核验下载文件：

macOS：
```bash
shasum -a 256 ColorGoogle-16.3.0.apk
adb devices -l
adb -s SERIAL install -r ColorGoogle-16.3.0.apk
```

Windows PowerShell（按实际路径调整）：
```powershell
$adb = "$env:USERPROFILE\Downloads\platform-tools\adb.exe"
Get-FileHash .\ColorGoogle-16.3.0.apk -Algorithm SHA256
& $adb devices -l
& $adb -s SERIAL install -r .\ColorGoogle-16.3.0.apk
```

**先检查旧版本和签名。** 包名仍是 `dev.evoker.homeholdcts`。官方 MindTrigger 或自行签名版本与本项目证书不同，无法直接覆盖。遇到 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` 不要自动卸载：应用禁用数据备份，卸载重装会失去设置和配对资料。先记录设置，再决定是否卸载。使用本项目同一发行密钥的旧 ColorGoogle 可以保留数据升级。详见[编译与签名](SIGNING.md)。

### 2. 手机设置

- 打开 **Google** 和 **Gemini**，登录并选 Gemini 作为移动助手；系统默认数字助理设为 Google。
- 在 ColorOS 导航设置里开启“长按底部手势条唤起系统助手”的选项，具体菜单名称可能不同。
- 给 ColorGoogle **通知**和**悬浮窗**权限，允许后台运行并开启主服务。通知被拒绝不等于服务停止，但会影响通过通知输入配对码。
- 在系统提供的入口允许 ColorGoogle、Google 和 Gemini 后台运行、自启动；可将 ColorGoogle 锁定在最近任务中。保活设置可以减少限制，但不保证永久运行。
- 电源键设为 Assistant/Gemini，手势设为 Circle to Search，延迟 **0 ms**；要保持此对应关系，关闭 **Swap CTS ↔ Google Assistant**。
- 需要息屏唤醒时，开启 Gemini 的**允许锁屏使用**。敏感操作仍可能要求解锁，不需要为本项目额外开启锁屏通话或消息权限。

### 3. 基础授权与启动代理

先读 [ADB 设置和恢复指南](docs/ADB.md)：保存原值、执行最低必要命令、按需添加应用级省电白名单，并了解恢复方法。**授予权限不等于启动代理。** 上游一键设置包含更广泛的系统修改，和这里的基础流程不同。

解压对应源码包，在源码根目录执行：

macOS：
```bash
adb -s SERIAL push tools/activate-agent.sh /data/local/tmp/colorgoogle-activate.sh
adb -s SERIAL shell sh /data/local/tmp/colorgoogle-activate.sh
adb -s SERIAL shell content call --uri content://dev.evoker.homeholdcts.desktopsearch --method status
```

Windows PowerShell：
```powershell
& $adb -s SERIAL push .\tools\activate-agent.sh /data/local/tmp/colorgoogle-activate.sh
& $adb -s SERIAL shell sh /data/local/tmp/colorgoogle-activate.sh
& $adb -s SERIAL shell content call --uri content://dev.evoker.homeholdcts.desktopsearch --method status
```

脚本在 **Android 手机端**运行，仅重启 ColorGoogle 代理，加载已安装 APK，不下载可执行代码。Beta 中的 **Copy ADB activation command** 适用于 Mac/Linux 终端，使用前替换 `SERIAL`。Windows 推荐上述推送脚本方式，避免 PowerShell 和 Android shell 的引号差异。

检查版本、空错误字段和 `wakeActive=true`。这些只证明日志通路健康，**还必须实测功能**。如果使用上游应用 UID 日志读取方式，Android 可能弹出设备日志访问授权，该授权属于会话级别。

## 各项功能怎么用

### 息屏 Gemini 与圈定即搜

1. 收起浮层、让手机完全黑屏，长按实体电源键约 1–2 秒后松开，确认实际出现 Gemini。内部接口返回成功不等于界面已出现。
2. 解锁并打开想搜索的内容，长按底部手势条，在 Google 浮层中圈选或点选内容。
3. 两次测试至少间隔 **2.5 秒**。同一次电源长按的重复日志及后续小布服务日志不会重复调用，也不会延长防抖间隔。

### 语音唤醒：使用系统唤醒词

**在已测试的 ColorOS 手机上说“小布小布”，不要把“Hey Google”当作本项目的唤醒词。** 先确认系统语音唤醒有效，再开启 ColorGoogle 的语音触发。声音由 OEM 唤醒服务识别，ColorGoogle 将该事件转向 Gemini；本项目不自行常驻录音识别，也不能解除 Google Voice Match 的设备资格限制。

不要把系统语音组件全部移除。测试配置保留了 `com.oplus.ovoicemanager`。某些固件需要单独处理抢占入口的小布界面组件，参见 [OEM 冲突与恢复](docs/ADB.md#oem-conflicts-optional)，不要将这台手机的包名套用到所有品牌。

### 桌面搜索

打开 **Beta → Desktop search → Open Google from desktop search**。代理连接后点击桌面搜索按钮，应进入 Google 搜索；按返回应直接回到桌面。关闭该开关恢复系统搜索入口，不影响长按监听。系统全局搜索包仍需保留。

### 可选：无线 ADB 自恢复

1. 保持 Wi-Fi 连接，在系统开发者选项开启 **无线调试**。
2. 允许 ColorGoogle 通知，进入 **Beta → Wireless ADB recovery → Start pairing**。
3. 在系统设置选择“使用配对码配对设备”。保持该窗口打开，下拉并展开 ColorGoogle 通知，回复显示的六位配对码。不要将配对码发到公开 Issue。
4. 返回 ColorGoogle，开启 **Recover automatically when the agent exits**。
5. 检查已配对和就绪状态。修复原因后可点 **Retry recovery**；**Delete local pairing key** 会关闭恢复，之后需重新配对。

每个端点/故障周期最多尝试三次。关闭自动恢复不会终止正在工作的代理。应用被强行停止、Wi-Fi 不可用、配对失效或无线调试关闭时无法恢复。上游 Shizuku 设置入口并不负责自动管理这个自定义代理。

## Gemini 生态用法

Gemini 打开后，可使用 Google 为你的账号、地区和语言提供的连接应用。在 **Gemini → Connected apps** 中按需连接 Google Workspace、Spotify。可能需要账号关联和开启 Google 的 **Keep Activity（保存活动）**。这些是 **Gemini 自身能力，不是 ColorGoogle 新增的服务接口**。

| 应用 | 唤醒 Gemini 后的示例请求 |
|---|---|
| Spotify | “用 Spotify 播放我的每周发现歌单。” |
| Google Tasks | “在 Google Tasks 添加明天买牛奶。” |
| Google 日历 | “在 Google 日历创建明天下午三点的项目讨论活动。” |
| Google Keep | “在 Google Keep 创建购物清单，加入牛奶和面包。” |

执行后到目标应用确认结果。可用性及锁屏确认要求可能变化。官方说明：[连接应用](https://support.google.com/gemini/answer/13695044)、[Spotify/媒体](https://support.google.com/gemini/answer/15300097)、[Tasks](https://support.google.com/gemini/answer/15230285)、[日历](https://support.google.com/gemini/answer/15305236)、[Workspace/Keep](https://support.google.com/gemini/answer/15229592)。

## 如何使用：让 Agent 协助安装

以下提示词可以直接复制给 **Codex、Claude Code 或其他具有终端/设备工具权限的 Agent**。只有聊天能力的模型（例如没有工具环境的 DeepSeek）只能指导，不能直接操作电脑。手机上的授权、锁屏和实体按钮测试仍需你完成；执行前了解系统修改的影响。

### 通用 Agent 提示词
```text
请协助我安装和配置 ColorGoogle：
https://github.com/joeyandu/ColorGoogle

先阅读当前 README、ADB 指南和 Release，再检查电脑系统、ADB 和手机。
列出已连接设备；只有一台实体手机时明确选定它，多台时让我选择。
所有设备命令始终指定该序列号。下载项目正式 Release 的 APK，
核对 SHA256SUMS，检查已有安装和签名兼容性。兼容时保留数据覆盖安装；
签名冲突时不要直接卸载，先解释设置/配对数据损失并等我决定。

修改前保存设置原值，按文档完成基础授权和 ADB 代理启动。
指导我完成通知、悬浮窗、后台运行、自启动、手势入口和 Gemini 锁屏设置。
默认电脑 ADB，不要求 Root 或 Shizuku；不要自动移除系统组件、清除数据、
关闭系统优化。系统级保活修改和无线自恢复先解释影响，再让我选择。

验证实际 Gemini、圈定即搜、系统语音唤醒、桌面搜索及返回行为，
然后让我拔掉 USB 后复测。结合日志和界面判断，不要仅凭权限或通知宣布成功。
失败时停止并保留现场，报告具体步骤和原因。
最后汇总版本、修改的设置、测试结果及恢复方法。
```

### macOS 提示词
```text
我使用 macOS，请协助安装 https://github.com/joeyandu/ColorGoogle。
先阅读当前 README 和 docs/ADB.md。检查 command -v adb 和 adb version。
如果没有 ADB 且已安装 Homebrew，执行 brew install --cask android-platform-tools；
没有 Homebrew 就使用 Google 官方 macOS SDK Platform-Tools，不为此额外安装 Homebrew。
执行 adb devices -l，指导 USB 授权并明确选择手机，多设备时让我选择。
后续所有设备操作使用 adb -s SERIAL，并替换为实际序列号。

下载项目 Release 的 APK、源码包和 SHA256SUMS，用 shasum -a 256 核验。
检查签名兼容性后才用 adb -s SERIAL install -r APK_PATH 覆盖安装。
签名冲突不代表可以卸载，先解释数据损失并等我选择。
保存原设置，只执行文档基础配置；推送 tools/activate-agent.sh，
按指南用 adb shell sh 在手机运行，不执行其他网站的一键脚本。

指导手机权限和后台保留；不 Root，不自动卸载系统组件、清除数据或关闭系统优化。
可选的更广泛修改先说明影响。结合真实界面和日志测试，在我配合下拔线复测。
报告准确错误、变更、结果和对应恢复命令。
```

### Windows PowerShell 提示词
```text
我使用 Windows，请通过 PowerShell 协助安装：
https://github.com/joeyandu/ColorGoogle
先阅读当前 README 和 docs/ADB.md。
使用 Get-Command adb -ErrorAction SilentlyContinue 检查 ADB。
缺少时下载 Google 官方 Windows SDK Platform-Tools 到我的用户目录，
使用 adb.exe 完整路径，不修改全局 PATH 或 PowerShell 执行策略。
使用 & "PATH\adb.exe" devices -l，处理 USB 授权并明确选定手机；
多设备时让我选择，所有设备操作始终加 -s SERIAL。
未发现手机时检查数据线、USB 状态和官方驱动，不安装不明驱动。

下载项目 Release 的 APK、源码包和 SHA256SUMS，使用
Get-FileHash APK_PATH -Algorithm SHA256 核验。
install -r 前检查签名，不能自动卸载或清除数据；冲突时解释后等我决定。
保存设置原值，执行基础授权，推送 tools\activate-agent.sh，
再通过 adb shell sh 在 Android 运行。不要将 Bash 变量、转义和续行照搬到 PowerShell。

指导手机权限和后台保留；不 Root，不自动移除 OEM 组件或关闭系统优化。
可选修改先说明影响。让我测试实体按钮、系统唤醒词，并拔掉 USB 复测。
检查真实界面和日志，最后报告错误、变更、结果及恢复方法。
```

## 故障排查

| 现象 | 检查方法 |
|---|---|
| 连接 USB 后功能失效 | 检查 O+Connect 是否将手机切入 Android 附件模式。关闭窗口后后台服务仍可能运行；检查后台与自启设置，待 USB 稳定后通过 ADB 重新激活 ColorGoogle。已在测试 Mac 上观察到，Windows 尚未实测。 |
| 所有入口都没反应 | 看 Beta 代理状态。READ_LOGS 仍可能已授权，但代理已退出；用电脑脚本重新激活。 |
| 无线恢复不起作用 | 检查 Wi-Fi、无线调试、本机配对和主服务；它不能重新开启已关闭的无线调试。 |
| 桌面搜索正常，长按失效 | 单独检查日志健康状态；`wakeActive=true` 后仍需实体操作验证。 |
| 没有运行通知 | 检查通知权限和通知渠道，不要据此认定服务已停止。 |
| 第一次圈搜失败 | 打开 Google 完成首次设置，确认默认助手，再重试。 |
| 小布也出现了 | 检查 OEM 设置及可选冲突处理，不要卸载全部语音组件。 |
| 返回时露出系统搜索 | 检查当前版本拦截开关和桌面入口；保留全局搜索包。 |
| 更新、重启或强行停止后失效 | 打开 ColorGoogle 检查状态，重新启动代理或恢复无线自恢复条件。 |
| ADB 不显示手机 | 检查授权、线缆和工具使用的 ADB 服务端口，不要直接认定权限被撤销。 |

提交 Issue 时提供机型、Android/ColorOS 版本、应用版本、触发方式、代理状态、自恢复条件和复现步骤。**删除序列号、账号、配对码、令牌和无关日志。** 参见[贡献指南](CONTRIBUTING.md)。

## 编译、架构与隐私

使用 **JDK 17 和 Android SDK 36**。[SIGNING.md](SIGNING.md) 包含 Debug/Release 编译及签名兼容说明，发行私钥不在仓库中。[架构说明](docs/ARCHITECTURE.md) 解释代理、Binder 边界、去重和本机无线恢复；[验证记录](docs/VALIDATION.md) 区分历史证据和当前发行检查。

ColorGoogle 在本机处理触发事件，没有开发者遥测服务。无线恢复通过手机本地 ADB 连接，配对密钥保存在应用私有且不备份的位置。Google 按其政策处理请求。诊断文件可能含设备信息，分享前请检查。参见[隐私和权限说明](TERMS_AND_PRIVACY.md)。

欢迎贡献和设备报告，修改时保留作者、许可和设备适用范围，见 [CONTRIBUTING.md](CONTRIBUTING.md)。

### 16.3.1-rc.1：代理存活候选版（本地验证）

此候选版让授权代理不再随应用通信进程退出。通信中断时暂停拦截和日志传递，
原代理按 1、2、4、8、16、之后每 30 秒的间隔重连；旧回调和排队任务失效，
不补发断线期间的唤醒事件。

“Stop all ColorGoogle features”（停止所有功能）先保存主开关停止意图，再退出
代理，桌面搜索也一起停止。重新启动需要电脑 ADB 或可用的已配对无线 ADB。
系统回收进程不会被当作用户主动停止。

界面区分代理连接状态与无线恢复条件。关闭 Wi-Fi、断网或切换接入点可能使系统
关闭无线调试。已经运行的代理可能继续工作，但退出后，当前恢复实现无法仅靠
移动数据重新启动。重连不是永久授权，也不保证代理跨 ADB 重启或手机重启存活。

电脑与应用中的激活命令统一来源于 `tools/activate-agent.sh`。激活不会停止已有
代理；更新 APK 后需要明确停止旧代理，再激活新版，不卸载、不清除数据。
本地结构化诊断日志按 128 KiB 轮换，每个写入方最多两份，不记录语音、搜索内容
或配对密钥；受保护的 shell 状态查询提供应用诊断日志的有限片段。
此候选版不扩大已验证设备范围；仅用流量和隔夜验收结果与构建、测试结果分开记录。

### 16.3.1-rc.2：USB 重连候选版（本地验证）

本机 O+Connect 后台日志记录了发送 USB 控制请求、OPPO 随后切入 Android
附件模式；手机日志将这些切换与 adbd 重启、代理退出关联起来。只关闭
O+Connect 窗口不会停止后台服务。停用其后台和登录启动项也会影响互联功能，
应保存配置和恢复方式，软件更新后重新检查自启。这是特定设备的诊断，
不意味着每次插 USB 或切换 Wi-Fi 都会结束代理。

本版允许连接结束后安全地重复清除监听器。旧连接仍不能注册新监听器，
旧清理也不能删掉新连接的监听器。关闭主开关仍会停止全部功能。

可选的 **macOS USB 恢复助手**等待 USB 连续稳定 5 秒，仅在主开关开启且
代理进程不存在时调用统一激活脚本。它不替换运行中的代理、不开启主开关，
不更改手机权限。检查应用及代理版本、功能连接状态，每次 USB 连接最多
尝试三次；错误写入有容量限制的本地日志。连接就绪不代表实体唤醒测试通过。
需预先安装 Python 3 和 ADB。先用 `adb -P 5038 devices -l` 确认手机序列号，
在仓库根目录运行：

```sh
python3 tools/install_usb_recovery.py install --serial 你已确认的序列号 --port 5038
python3 tools/install_usb_recovery.py status
# 移除助手及登录启动项，保留诊断日志：
python3 tools/install_usb_recovery.py uninstall
```

助手在 Mac 登录时运行，文件和轮转日志位于
`~/Library/Application Support/ColorGoogle USB Recovery/`。它仅支持对应的候选版；
升级应用时应安装匹配版本的助手。手机离开电脑后，助手无法恢复已经退出的代理。
手机的调试授权确认框需要用户操作。现有仅限 shell 的状态接口支持
`--arg compact`，返回不含日志正文的机器可读状态；未新增导出组件或 AIDL 方法。
长期纯流量存活需要独立验收。本版没有 Windows 常驻恢复助手。


### 16.3.1-rc.3：Android 17 助手诊断（本地候选版）

实测 OPPO 已升级至 ColorOS 17.0.0.104 / Android 17。安装本候选版前，原有 USB
恢复助手已在 OTA 重启后启动代理；用户确认电源键、系统语音、圈搜和桌面搜索可用，
包括拔掉 USB 后的操作。这只是升级后的基线，不代表之前的 Google 助手间歇故障已永久修复。

本候选版保留触发路由、调用方式和 2.5 秒防抖，不重置健康的默认助手连接，
不增加自动重置或替代调用兜底。新增诊断包括：

- 本地请求编号、触发来源与目标、去重结果、预热计数、接口返回和耗时。
  不向诊断文件写入原始系统触发行、语音、搜索文字、配对密钥或屏幕内容。
- 代理异步、有时限地采样 `voiceinteraction`，分别记录 Google 主服务与旧会话。
  旧会话存在不能掩盖主服务断连；重复日志共用一组采样，三秒超时，不保留完整 dump。
- **Beta → Desktop search** 分别显示代理状态、助手连接和最近请求。
  **诊断说明（中文）** 展示同一诊断状态的中文说明，不改变应用原有语言选择。
- **Request accepted; UI appearance unverified（请求已接受；未确认界面是否出现）**
  只表示系统接受调用。接口返回、`mShown=true` 或提示音均不能证明 Gemini 界面已出现。
  超过 30 秒的连接采样显示“未知”，同时显示采样时间间隔。

应用记录仍由 watcher 单一进程写入 `survival.log` 和 `.1`；代理记录位于
`/data/local/tmp/colorgoogle-agent-events.log` 和 `.1`，每份文件不超过 128 KiB。
原有受保护的 shell `status` 查询包含近期应用日志、请求字段和助手采样。
应用内各阶段通过请求编号关联，应用和代理采样通过单调时间戳对照。
最近请求摘要属于当前 watcher 进程；历史会话保留在文件中，直到轮转。
以下命令须替换为已确认的设备；使用自定义 ADB 端口时增加 `-P PORT`：

```sh
adb -s SERIAL shell content call --uri content://dev.evoker.homeholdcts.desktopsearch --method status
adb -s SERIAL shell cat /data/local/tmp/colorgoogle-agent-events.log
```

版本为 `16.3.1-rc.3`，版本号 `16030003`；为此候选版安装 Mac 恢复助手时指定
`--version 16030003`。本地验证不创建公开 Release。普通后台进程结束与系统
`FastClear` 清理属于不同测试：允许使用的终止命令没有实际结束 Google 助手进程时，
必须标记未覆盖，不改用强停、清数据、制造内存压力或反复授权。自然清理后的恢复与
长期复发情况仍须独立验收。

### 16.3.1-rc.4：双击电源键选择目标（本地候选版）

进入 **Beta → Double-press power**，开启 **Customize double-press power**，
选择 **Google Wallet（Google 钱包，初始选项）** 或 **System camera（系统相机）**。
新开关默认关闭。请保留 ColorOS 系统“双击电源键打开钱包”的设置：本功能在系统
启动 OPPO 钱包快捷页面前拦截该明确入口，不根据普通按键日志推算双击，不修改
默认支付应用或 NFC 设置。关闭开关恢复系统原有行为。

解锁时使用 OPPO 相机普通入口，锁屏时使用安全相机入口。Google 钱包使用正式
桌面入口，可能要求解锁。桌面搜索和双击共用一个拦截器，开关独立；不能与其他
活动监视工具共用。主服务开关及代理连接仍控制所有自定义拦截。目标不可用时不能
开启；启动失败会显示错误，不反复启动或尝试其他调用。请求被接受不代表界面已出现。

此版本仅供本机测试，不是公开 Release。两个目标的亮屏、锁屏、熄屏、拔掉 USB
测试必须分别记录，不能据此推定其他设备支持。版本号 `16030004`，Mac 恢复助手
需使用 `--version 16030004`。保留应用数据与签名，英文界面提供中文使用说明按钮。

### 17.0.1：相机查询路径优化（本地版本）

双击电源键的 **System camera（系统相机）** 选项改为通过系统 Binder 查询当前
用户及可用、已导出的 OPPO 相机入口，不再每次启动两个外部查询命令。每次触发
仍重新解析入口。安全相机选择、启动前锁屏复查、800 毫秒去重和 `am start -W`
启动方式保留。接口不支持或权限拒绝时明确报错，不回退到其他命令，不预热相机。

相机请求会写入有容量限制的本地 `CAMERA_TIMING` 日志，记录排队、准备、用户/
锁屏/入口查询、启动等待及总耗时。Android 返回启动状态或计时时才记录，缺失为
未知。ordinal 只是代理会话内的尝试序号，不代表真正冷启动。系统可能在拦截前
已经点亮屏幕，因此 locked 状态不能区分“锁屏亮屏”和“熄屏双击”。

按用户要求，前后对照实测延后，暂不能宣称整体启动速度已实测提升。日志仅留在
手机，沿用每份约 128 KiB、两份轮转的限制，旧记录会被覆盖。计时记录不保留相机
画面、用户内容或启动命令原始输出，不上传，也未设置定时监控。

版本 `17.0.1`、内部版本号 `17000100`，Mac 恢复助手使用 `--version 17000100`。
默认构建使用 Binder 查询；未来对照可通过 `-PcameraQueryMode=shell` 构建
`17.0.1-baseline`，保留相同计时和原查询方式。基线包本轮仅留档，未安装。
两种构建共用内部版本号，替换前必须停止旧代理，安装后重新激活并核对日志 mode，
不能仅凭版本号一致认定代理运行的实现正确。本轮未创建公开 Release。
