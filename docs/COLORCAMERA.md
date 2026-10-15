# ColorCamera

**English** | [简体中文](COLORCAMERA.zh-CN.md)

**Only want to double-press power to open the system camera? Install ColorCamera.**
It is a separate APK in the ColorGoogle repository, with its own package, settings and agent.
It does not install over ColorGoogle, and does not require Google, Gemini or Shizuku.

[Download 1.0.0 preview](https://github.com/joeyandu/ColorGoogle/releases/tag/colorcamera-v1.0.0) · [Validation](COLORCAMERA-VALIDATION.md) · [ColorGoogle](../README.md)

> **Preview: standalone-device acceptance is pending.** The underlying camera route was physically tested in ColorGoogle on OPPO Find X8 Ultra / PKJ110, Android 17 / ColorOS 17.0.0.104. That does not validate this newly separated APK. No phone installation or settings change was performed for this release, at the owner's request. Other devices and Windows execution are untested.

## What it does

ColorOS recognizes the physical double press, wakes the screen and attempts to open its wallet shortcut. A user-activated ADB shell agent matches the verified system caller, action and component, blocks that launch, and requests the system camera instead. It does not infer a double press from ordinary button logs.

- Unlocked: normal OPPO camera. Locked: the secure OPPO camera; no bypass of the lock screen or private photo access.
- One shortcut, one switch. Off restores the original system route; it does **not** disable the system wallet shortcut.
- Long press, voice wake, gesture-bar hold and desktop search are not redirected by this app. Their system behavior remains unchanged.
- No camera prewarming, microphone access, image access, system-log reading, network client, Google setup or payment/NFC changes.
- Camera preparation uses Binder queries, with `am start -W` retained. Local timing logs report request acceptance, **not independently verified UI appearance**.

## Before installing

1. Use the primary phone user. This release specifically targets the observed OPPO wallet shortcut and `com.oplus.camera`; minimum Android 12L/API 32 is an installation floor, not a compatibility promise.
2. Keep the OEM wallet installed and enable the system **double-press power → wallet** shortcut. Do not change it to the system camera shortcut: that is a different route and is not the one intercepted here.
3. **Turn off ColorGoogle's main service before activating ColorCamera.** The two apps can be installed together but their agents must not run together. New agents share a process-lifetime lock; the starter also checks older ColorGoogle agents. Foreign activity monitors are rejected when detected. Do not run `am monitor`, `monkey` or another tool that installs an activity controller. The platform has no compare-and-swap registration API: another tool racing with registration cannot be made atomically safe.
4. Enable USB debugging and authorize your own computer. Use Google's official [Platform-Tools](https://developer.android.com/tools/releases/platform-tools). No root or Shizuku installation is needed.
5. O+Connect background USB services can disrupt ADB on the tested Mac/OPPO combination, even after its window closes. Review the [O+Connect warning](../README.md#installation-computer-adb-recommended). Windows conflicts are possible but unconfirmed.

## Install and activate

Download `ColorCamera-1.0.0.apk`, `activate-camera.sh` and `SHA256SUMS` from the same release. Compare the two printed hashes against SHA256SUMS before installation. The certificate SHA-256 is:

```text
e52320218f36db099e6971b0070bba40eec451c1a69f634bb073be26a81f236e
```

Use Android SDK `apksigner verify --verbose --print-certs ColorCamera-1.0.0.apk` to check the signature. If an existing ColorCamera installation uses a different signer, **do not automatically uninstall or clear data**. Inspect the conflict first. ColorGoogle uses a different application ID and is not replaced.

### macOS

Run from the downloaded files directory. Replace `SELECTED_SERIAL` after reading `adb devices -l`; never guess among multiple devices. An existing Homebrew installation can install Platform-Tools with `brew install --cask android-platform-tools`; otherwise use Google's download above.

```sh
shasum -a 256 ColorCamera-1.0.0.apk activate-camera.sh
adb devices -l
adb -s SELECTED_SERIAL install --no-incremental -r ColorCamera-1.0.0.apk
```

Open **ColorCamera**, check camera entries, follow the system shortcut instructions, and turn on **Enable camera shortcut**. Allow notifications if desired; use the app's background settings link to permit background operation/autostart where available. Then:

```sh
adb -s SELECTED_SERIAL push activate-camera.sh /data/local/tmp/activate-camera.sh
adb -s SELECTED_SERIAL shell sh /data/local/tmp/activate-camera.sh
adb -s SELECTED_SERIAL shell content call --uri content://io.github.joeyandu.colorcamera.bridge --method status
```

The desired output includes `active=true`, `state=RUNNING`, and `version=10000`. If another agent is active, stop it in its own app rather than killing processes. An unknown owner, unavailable camera or unsupported Binder interface causes activation to fail with a diagnostic; there is no alternate launch fallback.

### Windows PowerShell

Download and extract official Platform-Tools. Use a full path and PowerShell's call operator. Run these commands in the release download directory:

```powershell
$adbExe = 'C:\Android\platform-tools\adb.exe'
$deviceSerial = 'SELECTED_SERIAL'
Get-FileHash .\ColorCamera-1.0.0.apk -Algorithm SHA256
Get-FileHash .\activate-camera.sh -Algorithm SHA256
# Compare both values with SHA256SUMS before continuing.
& $adbExe devices -l
& $adbExe -s $deviceSerial install --no-incremental -r .\ColorCamera-1.0.0.apk
# Open ColorCamera, follow the setup guide, and turn on its switch before these commands.
& $adbExe -s $deviceSerial push .\activate-camera.sh /data/local/tmp/activate-camera.sh
& $adbExe -s $deviceSerial shell sh /data/local/tmp/activate-camera.sh
& $adbExe -s $deviceSerial shell content call --uri content://io.github.joeyandu.colorcamera.bridge --method status
```

The script is executed on Android; PowerShell does not expand Android shell variables. These commands have been reviewed but not run on Windows. If you intentionally use a non-default ADB server, include the same `-P PORT` before `-s` in every command.

## Daily use, stopping and recovery

After activation, disconnect USB and try the physical double press in unlocked, locked-screen-on and screen-off states. Back must not reveal the old wallet shortcut. In the locked camera, opening older photos must require unlocking. Do not consider a running process or `Status: ok` proof that these checks passed.

The shortcut switch saves **user intent**, while the status separately reports the agent connection. Notification denial is displayed as restricted notifications, not as agent failure. The switch does not silently turn on on first install.

Turning the switch off cancels queued requests, stops interception and requests agent exit. An already-started camera is not forcibly closed. If the app connection dies, the agent disables interception and retries the protected connection with 1/2/4/8/16/30-second backoff. It reads the saved switch again before resuming; old events are not replayed.

A phone reboot, ADB daemon restart, system termination or force-stop can end the agent. **This is not permanent authorization.** ColorCamera 1.0.0 has no wireless pairing or unattended on-phone recovery. Reopen the app, inspect its switch/status and reactivate from an authorized computer. App background permission cannot preserve a shell agent across reboot.

To return to ColorGoogle: turn ColorCamera off, wait for its agent to disappear, then enable and activate ColorGoogle. Neither app automatically changes the other's saved settings. Updated Mac recovery tooling refuses activation while `colorcamera-agent` is present; older helper installations must be updated from `tools/install_usb_recovery.py` or stopped. The helper is for ColorGoogle, not a ColorCamera auto-start service.

## Diagnostics and privacy

The app's **Diagnostics** button reads its own bounded event log. For shell-agent events:

```sh
adb -s SELECTED_SERIAL pull /data/local/tmp/colorcamera-events.log
adb -s SELECTED_SERIAL pull /data/local/tmp/colorcamera-events.log.1
adb -s SELECTED_SERIAL pull /data/local/tmp/colorcamera-start.log
```

The `.1` file only exists after rotation. Each side retains two event files of at most 128 KiB each. Events contain process/session state, route outcome and timing—not images, audio, searches, pairing keys or full system logs. Nothing is uploaded. Logs can rotate before a later test; copy them soon after reproducing a problem. Review them before sharing. Uninstalling the app does not necessarily remove shell files under `/data/local/tmp`; delete only the three named logs and their rotations if desired, after stopping the agent.

## Build, source and license

Version **1.0.0 / 10000**, package `io.github.joeyandu.colorcamera`. Modules: `camera` is the standalone app; `shortcut-core` contains the shared lifecycle, exact matching, safety checks, timing and ownership utilities; `app` remains ColorGoogle. Java package names preserve upstream source attribution; application IDs and IPC authorities are separate.

With JDK 17 and Android SDK 36, configure your own signing credentials using `keystore.properties.example` (never commit keys), then run:

```sh
JAVA_HOME=/path/to/jdk17 bash ./gradlew :shortcut-core:testReleaseUnitTest :camera:testReleaseUnitTest :camera:assembleRelease :camera:lintRelease :app:testReleaseUnitTest :app:assembleRelease :app:lintRelease
python3 -m unittest discover -s tools/tests -v
```

The APK only depends on the shared project library and Android platform; no third-party runtime SDK is added. JUnit is test-only. Repository-wide notices also describe dependencies of the separate ColorGoogle app.

ColorCamera is open source under **GPL-3.0-only**, derived from ColorGoogle and MindTrigger Assist v16.2.0 by EvokerUniverse. The repository preserves MiCTS upstream attribution, although the ColorCamera APK contains no CTS invocation implementation. See [LICENSE](../LICENSE), [NOTICE](../NOTICE.md) and [third-party notices](../THIRD_PARTY_NOTICES.md). This is not an official OPPO or Google product.
