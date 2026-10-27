param(
  [switch]$Logcat,
  [switch]$NoLaunch
)

$root = Split-Path $PSScriptRoot -Parent
$package = 'cc.juanma.mcm3d'
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine') }
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$adb = Join-Path $sdk 'platform-tools\adb.exe'

$devices = (& $adb devices) -split "`n" | Where-Object { $_ -match '^\S+\s+device\s*$' }
if (-not $devices) {
  Write-Host 'No phone connected. Enable wireless debugging (same Wi-Fi as the PC) or plug in a USB cable.'
  exit 1
}

Push-Location $root
try {
  & .\gradlew.bat :app:installDebug --console=plain
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
  Pop-Location
}

if (-not $NoLaunch) {
  & $adb shell am start -n "$package/.MainActivity" | Out-Null
}

if ($Logcat) {
  Start-Sleep -Seconds 1
  $appPid = "$(& $adb shell pidof -s $package)".Trim()
  if ($appPid) { & $adb logcat --pid=$appPid } else { Write-Host 'App process not found.' }
}
