#!/usr/bin/env pwsh
# Install the latest release APK on a paired/connected ADB device.

$ErrorActionPreference = "Stop"

$apkRel = "app\build\outputs\apk\release\app-release.apk"
$apk = Join-Path $PSScriptRoot ".." | Resolve-Path
$apk = Join-Path $apk $apkRel

if (-not (Test-Path $apk)) {
    Write-Error "APK not found at $apk. Run .\gradlew.bat assembleRelease first."
    exit 1
}

# Pick adb. Order: PATH -> Android SDK in %LOCALAPPDATA%.
$adb = $null
$candidates = @(
    "adb",
    (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"),
    "C:\Users\Guilherme\AppData\Local\Android\Sdk\platform-tools\adb.exe"
)
foreach ($c in $candidates) {
    try {
        $cmd = Get-Command $c -ErrorAction Stop
        $adb = $cmd.Source
        break
    } catch {
        if (Test-Path $c) { $adb = $c; break }
    }
}

if (-not $adb) {
    Write-Error "adb not found. Install the Android SDK platform-tools, or check Android Studio is installed."
    exit 1
}

Write-Host "Using adb: $adb"

$devices = & $adb devices | Select-String -Pattern "\sdevice$" | Measure-Object | Select-Object -ExpandProperty Count
if ($devices -eq 0) {
    Write-Error @"
No ADB device. Pair the watch first:

  $adb pair <watch-ip>:<pair-port>     # one-time, code expires in ~60s
  $adb connect <watch-ip>:5555
  $adb devices                          # confirm 'device' status

NOTE: home Wi-Fi (KPN Box 12b) blocks device-to-device traffic - use phone hotspot.
"@
    exit 1
}

Write-Host "Installing $apk ..."
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) {
    Write-Error "adb install failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "Done. On the watch: long-press current face -> scroll to 'Globetrotting Minimalist' -> tap."
