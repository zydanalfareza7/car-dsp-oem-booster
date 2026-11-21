<#
.SYNOPSIS
    Automated One-Click APK Builder for Car DSP OEM Sound Restorer.
    Downloads portable Gradle & Android Command-Line SDK if not installed, then compiles the signed APK.
#>
$ErrorActionPreference = "Stop"

$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectDir

$ToolsDir = Join-Path $ProjectDir ".build-tools"
$AndroidSdkDir = Join-Path $ToolsDir "android-sdk"
$GradleDir = Join-Path $ToolsDir "gradle-8.7"

New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null

# 1. Check or Download Portable Gradle 8.7
$GradleBin = Join-Path $GradleDir "bin\gradle.bat"
if (-not (Test-Path $GradleBin)) {
    Write-Host "[1/3] Downloading Portable Gradle 8.7..." -ForegroundColor Cyan
    $GradleZip = Join-Path $ToolsDir "gradle.zip"
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-8.7-bin.zip" -OutFile $GradleZip
    Expand-Archive -Path $GradleZip -DestinationPath $ToolsDir -Force
    Remove-Item $GradleZip -Force
}

# 2. Check or Download Portable Android Command-Line SDK
if (-not (Test-Path (Join-Path $AndroidSdkDir "platforms\android-34"))) {
    Write-Host "[2/3] Downloading Android Command-Line SDK (API 34)..." -ForegroundColor Cyan
    $CmdToolsZip = Join-Path $ToolsDir "cmdline-tools.zip"
    Invoke-WebRequest -Uri "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -OutFile $CmdToolsZip
    $CmdExtract = Join-Path $AndroidSdkDir "cmdline-tools"
    New-Item -ItemType Directory -Force -Path $CmdExtract | Out-Null
    Expand-Archive -Path $CmdToolsZip -DestinationPath $CmdExtract -Force
    if (Test-Path (Join-Path $CmdExtract "latest")) { Remove-Item (Join-Path $CmdExtract "latest") -Recurse -Force }
    Rename-Item -Path (Join-Path $CmdExtract "cmdline-tools") -NewName "latest"
    Remove-Item $CmdToolsZip -Force

    $SdkManager = Join-Path $CmdExtract "latest\bin\sdkmanager.bat"
    Write-Host "Accepting Android SDK licenses and installing build-tools;34.0.0 & platforms;android-34..." -ForegroundColor Cyan
    "y`ny`ny`ny`ny`ny`ny`n" | & $SdkManager --licenses | Out-Null
    & $SdkManager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
}

$env:ANDROID_HOME = $AndroidSdkDir
$env:ANDROID_SDK_ROOT = $AndroidSdkDir

# 3. Compile Ready-to-Install Signed APK
Write-Host "[3/3] Building Car DSP OEM Restorer APK..." -ForegroundColor Green
& $GradleBin assembleRelease --no-daemon

$OutputApk = Join-Path $ProjectDir "app\build\outputs\apk\release\app-release.apk"
$DestApk = Join-Path $ProjectDir "Car-DSP-OEM-Restorer.apk"
if (Test-Path $OutputApk) {
    Copy-Item $OutputApk $DestApk -Force
    Write-Host "`n========================================================" -ForegroundColor Green
    Write-Host "SUKSES! File APK siap di-install ke Head Unit Android:" -ForegroundColor Green
    Write-Host $DestApk -ForegroundColor Yellow
    Write-Host "========================================================" -ForegroundColor Green
}
