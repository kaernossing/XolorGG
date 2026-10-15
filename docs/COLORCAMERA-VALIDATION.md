# ColorCamera 1.0.0 preview — validation / 验证记录

Date / 日期: 2026-10-15. Package: `io.github.joeyandu.colorcamera`, versionCode `10000`.

## Scope / 范围

No installation, phone setting changes or physical-button operations were performed for this standalone release, at the owner's request. A working ColorGoogle camera route is its source, not proof of standalone APK acceptance. Existing ColorGoogle phone state and the installed Mac helper were left untouched; the helper conflict fix is in the distributed source.

按用户要求，本次没有安装手机、修改手机设置或操作实体按键。独立版来自已有 ColorGoogle 相机路径，但不将其旧实测当作独立 APK 已验收。手机现有 ColorGoogle 和本机已安装的 Mac 助手均未改动；互斥修复包含在发布源码中。

## Automated checks / 自动检查

- 75 Java unit tests: 36 ColorGoogle, 37 shared core, 2 ColorCamera packaging; all pass.
- 11 Python USB recovery helper tests; all pass, including active ColorCamera preventing ColorGoogle activation.
- Safety tests cover unknown/changing lock state, normal/secure entry choice, other-user rejection, unavailable target, cancellation at preparation boundaries, accepted request without display timing, exact OEM request matching, deduplication, stale callbacks, reconnect/stop races and foreign controller ownership.
- Both signed APK builds and Lint complete. No new Lint errors; existing ColorGoogle baseline excludes six upstream errors. Remaining warnings are not claimed to be fixed (see build reports).
- Static source sanity, XML/build identity and permission checks; signature and archive/hash checks recorded with the release artifacts. Clean-source rebuild of both APKs and all 75 Java tests passed; The initial working/clean APKs differed only in AGP Git metadata; their code/resources matched. APK byte-for-byte reproducibility is not claimed. The distributed APK is the verified clean-source build.
- ColorCamera manifest contains only foreground-service, special-use foreground-service and notification permissions. No READ_LOGS, overlay, INTERNET, CAMERA, microphone or media permissions.

Java 共 75 项测试，Python 共 11 项测试通过。安全检查覆盖未知/变化锁屏状态、安全相机选择、其他用户拒绝、目标缺失、排队取消、接受请求但无界面耗时、准确匹配、去重、旧回调、重连/停止竞争及控制器归属。两款 APK 均编译并完成 Lint；ColorGoogle 仍保留六项上游错误基线和既有警告，不宣称无警告。签名和校验值由发布文件记录，干净源码重新构建两款 APK 并运行 75 项 Java 测试通过，不宣称两次 APK 逐字节可复现，发布使用已验证的干净源码构建产物。

## Pending physical acceptance / 待真机验收

| Check / 项目 | Status / 状态 |
|---|---|
| Standalone first launch, opt-in, notification denial / 首次启动、主动开启、拒绝通知 | Pending / 待测 |
| Exact OEM callback blocked, Back never exposes old wallet / 阻止原钱包并验证返回 | Pending / 待测 |
| Camera unlocked, locked-screen-on, screen off / 三种屏幕状态 | Pending / 待测 |
| Secure camera requires unlock for old photos / 锁屏相册解锁要求 | Pending / 待测 |
| Off restores original shortcut / 关闭恢复原行为 | Pending / 待测 |
| App process death, reconnect, explicit stop / 应用死亡、重连、主动停止 | Pending / 待测 |
| Switching between installed ColorGoogle and ColorCamera / 两款应用切换 | Pending / 待测 |
| USB disconnected; Wi-Fi off; repeated network changes / 拔线、关闭 Wi-Fi、切网 | Pending / 待测 |
| Overnight survival and reboot/re-activation / 长期存活和重启恢复 | Pending / 待测 |
| Windows commands / Windows 命令执行 | Reviewed only / 仅检查文本 |

A process being alive or a request being accepted is not physical acceptance. Hidden APIs and activity-controller dump formats may change. Foreign tools can race with controller registration; no atomic takeover prevention is claimed. This preview will remain marked as such until actual standalone-device results justify promoting it.

不能以进程存在或请求接受代替实测。隐藏接口和控制器状态格式可能随系统变化；无法宣称原子阻止外部工具抢占。独立版完成实际验收前保持预发布状态。

## Review / 自检

The implementation keeps one shell-agent connection, uses the existing route/lifecycle primitives, shares camera safety/timing code, and has no alternate launch fallback. Two apps do not silently stop or reconfigure each other. Command execution stays off the system callback; app stop persists intent before requesting shutdown. No old trigger is replayed after reconnection. An already-issued launch cannot be retroactively cancelled.

实现只保留一个代理通信通道，复用路由/生命周期和相机安全计时代码；不自动改走另一启动方式。两款应用不偷偷停止或配置对方。系统回调不执行启动命令，应用先保存关闭意图再停止代理，重连不补发旧事件。已经发出的启动无法事后撤回。
