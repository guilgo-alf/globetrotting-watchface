#!/usr/bin/env pwsh
# One-shot bootstrap: verify environment, generate keystore, build release APK.
# Idempotent - re-run any time. Stops at the first hard failure with a clear message.

$ErrorActionPreference = "Stop"

function Section($title) {
    Write-Host ""
    Write-Host "=== $title ===" -ForegroundColor Cyan
}

function Fail($msg) {
    Write-Host ""
    Write-Error $msg
    exit 1
}

# ---- 1. Java ---------------------------------------------------------------
Section "Java"

$javaHome = $env:JAVA_HOME
if (-not $javaHome -or -not (Test-Path (Join-Path $javaHome "bin\java.exe"))) {
    $candidates = @(
        "C:\Users\Guilherme\Tools\jdk-17.0.13+11",
        "C:\Program Files\Android\Android Studio\jbr",
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Microsoft\jdk-17"
    )
    foreach ($c in $candidates) {
        if (Test-Path (Join-Path $c "bin\java.exe")) {
            $javaHome = $c
            $env:JAVA_HOME = $javaHome
            Write-Host "JAVA_HOME not set - using $javaHome"
            break
        }
        # Adoptium installs a versioned subfolder
        if (Test-Path $c) {
            $sub = Get-ChildItem $c -Directory -ErrorAction SilentlyContinue |
                Where-Object { Test-Path (Join-Path $_.FullName "bin\java.exe") } |
                Select-Object -First 1
            if ($sub) { $javaHome = $sub.FullName; $env:JAVA_HOME = $javaHome; break }
        }
    }
}

if (-not $javaHome -or -not (Test-Path (Join-Path $javaHome "bin\java.exe"))) {
    Fail "No JDK found. Install JDK 17+ (Microsoft Build of OpenJDK or Adoptium) and re-run, or set JAVA_HOME."
}

$env:PATH = "$javaHome\bin;${env:PATH}"

# Read version from the JDK's release file. Avoids `java -version 2>&1`, which
# Windows PowerShell 5.1 wraps in NativeCommandError records that trip $ErrorActionPreference=Stop.
$javaVer = '(unknown)'
$releaseFile = Join-Path $javaHome 'release'
if (Test-Path $releaseFile) {
    $line = Get-Content $releaseFile | Where-Object { $_ -match '^JAVA_VERSION=' } | Select-Object -First 1
    if ($line) { $javaVer = ($line -replace '^JAVA_VERSION=', '' -replace '"', '').Trim() }
}
Write-Host "JAVA_HOME = $javaHome"
Write-Host "java       $javaVer"

# ---- 2. Android SDK --------------------------------------------------------
Section "Android SDK"

$sdkDir = $null
if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) {
    $sdkDir = $env:ANDROID_HOME
} elseif ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) {
    $sdkDir = $env:ANDROID_SDK_ROOT
} else {
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA "Android\Sdk"),
        "C:\Users\Guilherme\AppData\Local\Android\Sdk",
        "C:\Android\Sdk"
    )
    foreach ($c in $candidates) {
        if (Test-Path (Join-Path $c "platform-tools\adb.exe")) { $sdkDir = $c; break }
    }
}

if (-not $sdkDir) {
    Fail "Android SDK not found. Install Android Studio (it bundles the SDK at %LOCALAPPDATA%\Android\Sdk)."
}

$env:ANDROID_HOME = $sdkDir
$env:ANDROID_SDK_ROOT = $sdkDir
Write-Host "ANDROID_HOME = $sdkDir"

# Ensure local.properties matches; Gradle will use this even when env vars are set.
$localProps = Join-Path $PSScriptRoot "local.properties"
$sdkEsc = $sdkDir -replace '\\', '\\' -replace ':', '\:'
$localPropsContent = "sdk.dir=$sdkEsc`n"
$existing = if (Test-Path $localProps) { Get-Content $localProps -Raw } else { "" }
if ($existing.Trim() -ne $localPropsContent.Trim()) {
    Set-Content -Path $localProps -Value $localPropsContent -Encoding ASCII -NoNewline
    Write-Host "Wrote $localProps"
}

# ---- 3. Keystore -----------------------------------------------------------
Section "Keystore"

$keystorePath = Join-Path $HOME ".android\globetrotting-debug.keystore"
if (Test-Path $keystorePath) {
    Write-Host "Keystore present at $keystorePath"
} else {
    & (Join-Path $PSScriptRoot "scripts\make-keystore.ps1")
    if ($LASTEXITCODE -ne 0) { Fail "Keystore generation failed." }
}

# ---- 4. Build --------------------------------------------------------------
Section "Build (gradlew assembleRelease)"

Push-Location $PSScriptRoot
try {
    if (-not (Test-Path ".\gradlew.bat")) { Fail "gradlew.bat missing - project is corrupted." }
    & .\gradlew.bat assembleRelease --no-daemon
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "If the failure looks like 'Unable to establish loopback connection',"
        Write-Host "see docs\diagnose.md for the AF_UNIX / tmpdir workaround."
        Fail "Build failed (exit $LASTEXITCODE)."
    }
} finally {
    Pop-Location
}

# ---- 5. Report -------------------------------------------------------------
Section "Output"

$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\release\app-release.apk"
if (Test-Path $apk) {
    $size = (Get-Item $apk).Length
    Write-Host "APK at $apk"
    Write-Host "Size:  $([math]::Round($size / 1024, 1)) KB"
    Write-Host ""
    Write-Host "Next: pair watch over ADB, then run .\scripts\install.ps1"
} else {
    Fail "Build said success but APK is missing at $apk. Check build output above."
}
