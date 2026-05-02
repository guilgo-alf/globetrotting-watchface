#!/usr/bin/env pwsh
# Generate the debug-signing keystore used to sign release APKs.
# Idempotent - exits cleanly if the keystore already exists.

$ErrorActionPreference = "Stop"

$keystorePath = Join-Path $HOME ".android\globetrotting-debug.keystore"

if (Test-Path $keystorePath) {
    Write-Host "Keystore already exists at $keystorePath"
    exit 0
}

# Pick a keytool. Order: PATH -> bundled JDK17 -> Android Studio JBR.
$keytool = $null
$candidates = @(
    "keytool",
    "C:\Users\Guilherme\Tools\jdk-17.0.13+11\bin\keytool.exe",
    "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
)
foreach ($c in $candidates) {
    try {
        $cmd = Get-Command $c -ErrorAction Stop
        $keytool = $cmd.Source
        break
    } catch {
        if (Test-Path $c) { $keytool = $c; break }
    }
}

if (-not $keytool) {
    Write-Error "keytool not found. Install a JDK or set JAVA_HOME, then add `$JAVA_HOME\bin to PATH."
    exit 1
}

$dir = Split-Path -Parent $keystorePath
if (-not (Test-Path $dir)) {
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
}

Write-Host "Using keytool: $keytool"
Write-Host "Creating keystore at $keystorePath ..."

& $keytool -genkey -v `
    -keystore $keystorePath `
    -alias globetrotting `
    -keyalg RSA -keysize 2048 `
    -validity 10000 `
    -storepass globetrottingdev `
    -keypass globetrottingdev `
    -dname "CN=Guilherme Watch Face, O=Personal, C=NL"

if ($LASTEXITCODE -ne 0) {
    Write-Error "keytool failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

Write-Host "Keystore created at $keystorePath"
