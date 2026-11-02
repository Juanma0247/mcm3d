param(
  [switch]$Delete
)

$root = Split-Path $PSScriptRoot -Parent
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$remote = '/sdcard/Android/data/cc.juanma.mcm3d/files/sessions'
$local = Join-Path $root 'recordings'

New-Item -ItemType Directory -Force $local | Out-Null
$files = (& $adb shell "ls $remote 2>/dev/null") -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -like 'session_*.csv' }
if (-not $files) {
  Write-Host 'No sessions on the phone.'
  exit 0
}

foreach ($name in $files) {
  & $adb pull "$remote/$name" (Join-Path $local $name) | Out-Null
  if ($LASTEXITCODE -ne 0) { Write-Host "Failed: $name"; continue }
  Write-Host "Pulled $name"
  if ($Delete) { & $adb shell rm "$remote/$name" }
}
