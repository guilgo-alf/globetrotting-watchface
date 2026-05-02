# Diagnose — Windows build gotchas

One-page triage for things that bite when building this project on Windows.
Symptoms first; fixes after.

---

## "Unable to establish loopback connection" / AF_UNIX

**Symptom**
```
java.io.IOException: Unable to establish loopback connection
  ...
Caused by: java.net.SocketException: Invalid argument: connect
  at java.base/sun.nio.ch.UnixDomainSockets.connect0(Native Method)
```
Hits during *any* Gradle command (even `gradle --version` works but `gradle tasks`
doesn't), because Gradle's worker IPC uses a `Selector` which on Windows
internally creates an `AF_UNIX` Pipe.

**Root cause**
Java NIO uses `AF_UNIX` socket files inside `java.io.tmpdir` for selector
wakeup pipes. On a small subset of Windows configurations the `connect()` to
the bound socket fails with EINVAL — usually due to a third-party filesystem
filter driver (some AV products), Sandbox/Hyper-V container context, or
junction points in the user-profile temp tree.

**Fix — try in order**

1. **Move tmpdir off the user profile.**
   ```powershell
   New-Item -ItemType Directory -Path C:\GBuild\tmp -Force | Out-Null
   $env:GRADLE_OPTS = "-Djava.io.tmpdir=C:\GBuild\tmp"
   ```
   Then re-run the build. If this fixes it, persist by adding to
   `gradle.properties`:
   ```properties
   org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 -Djava.io.tmpdir=C:\\GBuild\\tmp
   ```

2. **Check Defender / corporate AV.** Real-time scanning on the temp folder
   can intercept the AF_UNIX connect. Add an exclusion for
   `%USERPROFILE%\AppData\Local\Temp` and the project folder. Re-test.

3. **Verify the AF_UNIX driver is running:**
   ```powershell
   sc query afunix
   ```
   Should report `STATE: 4 RUNNING`. If not, AF_UNIX is disabled on this
   machine — you'll need a JDK that doesn't require it (none of the modern
   ones), or fix the driver state via `sc start afunix`.

4. **Last resort: build via Android Studio's bundled Gradle integration.**
   AS uses the Tooling API, which has different IPC semantics and sometimes
   sidesteps the bug. Open the project, `File -> Sync Project with Gradle Files`,
   then `Build -> Build Bundle(s) / APK(s) -> Build APK(s)`.

---

## `JAVA_HOME is not set`

**Symptom**
```
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
```
Surfaces from `gradlew.bat`, `apksigner.bat`, `d8.bat`, etc.

**Fix**
Either set the env var permanently:
```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME",
    "C:\Program Files\Android\Android Studio\jbr", "User")
```
…or just for this session:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```
The bundled JBR ships with Android Studio at the path above. Any JDK 17+
will do; AGP 8.7 needs at least 17.

`bootstrap.ps1` also auto-detects common JDK locations so you usually don't
need to touch this.

---

## `INSTALL_FAILED_VERSION_DOWNGRADE`

**Symptom**
```
adb: failed to install app-release.apk: Failure
  [INSTALL_FAILED_VERSION_DOWNGRADE]
```

**Fix**
The new build's `versionCode` is lower than the installed one. This project
auto-increments `versionCode` from build timestamp, so it normally only happens
if you switch branches, restore an older APK, or change machines (different
clock).
```powershell
adb uninstall com.guil.globetrotting
.\scripts\install.ps1
```

---

## `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

**Symptom**
```
adb: failed to install ... [INSTALL_FAILED_UPDATE_INCOMPATIBLE]
```

**Fix**
Keystore mismatch — the version on the watch was signed by a different key.
```powershell
adb uninstall com.guil.globetrotting
.\scripts\install.ps1
```

---

## "The system cannot find the path specified" / MAX_PATH

**Symptom**
Build fails partway through with file paths over ~260 chars failing to open,
typically inside `app\build\intermediates\` deep trees.

**Fix**
Enable long path support system-wide (one-time, requires admin PowerShell):
```powershell
New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem" `
                 -Name "LongPathsEnabled" -Value 1 -PropertyType DWORD -Force
```
Reboot. Or move the project to a shorter base path:
```powershell
git clone <repo> C:\gw   # instead of C:\Users\Guilherme\Projects\…
```

---

## Locked file errors during build

**Symptom**
```
java.io.IOException: Could not delete ... R.jar (the file is in use by another process)
```
or
```
> Could not start dex archive
```

**Fix**
Defender real-time scan is holding the file. Add a process exclusion:
- `%LOCALAPPDATA%\Android\Sdk\build-tools\<version>\d8.bat`
- `%LOCALAPPDATA%\Android\Sdk\build-tools\<version>\aapt2.exe`
- the JDK's `java.exe`

Or temporarily exclude the project root:
```powershell
Add-MpPreference -ExclusionPath "C:\Users\Guilherme\Projects\globetrotting-watchface"
```
(Requires admin.)

---

## Gradle daemon weirdness — wipe and retry

**Symptom**
Builds fail with bizarre errors after a cancelled build, JDK version change, or
machine sleep. Often: `unable to start the daemon process`, `cannot lock cache`,
or compilation suddenly fails on code that just compiled.

**Fix — nuke cached state:**
```powershell
# Stop any lingering daemons.
.\gradlew.bat --stop

# Wipe project-local Gradle state.
Remove-Item -Recurse -Force .gradle, app\build -ErrorAction SilentlyContinue

# Wipe the user-level Gradle cache (more aggressive — re-downloads dependencies).
Remove-Item -Recurse -Force "$HOME\.gradle\caches"
```
Re-run `bootstrap.ps1`.

---

## Watch face doesn't appear in the picker after install

**Symptom**
APK installs fine, no error in `adb logcat`, but long-press carousel doesn't
list "Globetrotting Minimalist".

**Checks**
```powershell
adb shell pm list packages | Select-String globetrotting
adb shell dumpsys package com.guil.globetrotting | Select-String -Context 1,5 "service"
adb logcat -d | Select-String -Pattern "globetrotting|WatchFace" | Select-Object -Last 20
```
Should see the WatchFaceService registered with `BIND_WALLPAPER` permission and
the `WATCH_FACE` category. If not, the manifest entry is wrong; check
`app\src\main\AndroidManifest.xml`.

---

## Compass marker doesn't move (later sessions)

**Symptom**
Watch face displays correctly but the compass dot is stationary.

**Fix**
Check sensor registration in logcat:
```powershell
adb logcat -d | Select-String "Sensor|rotation" | Select-Object -Last 30
```
If no rotation-vector events: the watch's magnetometer needs calibration. Wave
the watch in a figure-8 a few times. If still nothing, the sensor was never
registered — check `CompassSensorManager.start()` is actually called in
`onVisibilityChanged`.
