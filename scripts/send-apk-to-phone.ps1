# Sends the freshly built release APK to the phone over KDE Connect.
#
# Usage (after :app:assembleRelease):
#   .\scripts\send-apk-to-phone.ps1

$ErrorActionPreference = 'Stop'

$cli = Join-Path $env:LOCALAPPDATA 'Microsoft\WindowsApps\kdeconnect-cli.exe'
if (-not (Test-Path $cli)) {
    Write-Host 'kdeconnect-cli not found, skipping' -ForegroundColor Yellow
    exit 0
}

$apk = Join-Path $PSScriptRoot '..\app\build\outputs\apk\release\app-arm64-v8a-release.apk'
if (-not (Test-Path $apk)) {
    Write-Host "APK not found: $apk" -ForegroundColor Yellow
    exit 0
}

$deviceIds = & $cli --list-available --id-only
if (-not $deviceIds) {
    Write-Host 'No reachable KDE Connect device' -ForegroundColor Yellow
    exit 0
}

$deviceId = $deviceIds | Select-Object -First 1
Write-Host "Sending APK to $deviceId ..."
& $cli --device $deviceId --share (Resolve-Path $apk)
