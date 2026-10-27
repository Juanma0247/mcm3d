param(
  [string]$Target,
  [int]$StartPort = 30000,
  [int]$EndPort = 65535,
  [int]$BatchSize = 2000,
  [int]$TimeoutMs = 500
)

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$stateFile = Join-Path $PSScriptRoot '.last-phone'

function Invoke-Adb([string]$Arguments) {
  (cmd /c "`"$adb`" $Arguments 2>&1") -join "`n"
}

function Connect-Endpoint([string]$Endpoint) {
  $result = Invoke-Adb "connect $Endpoint"
  if ($result -notmatch '(connected|already connected) to') { return $false }
  if ((Invoke-Adb "-s $Endpoint get-state").Trim() -ne 'device') { return $false }
  Set-Content -Path $stateFile -Value $Endpoint -Encoding ascii
  Write-Host "Connected: $Endpoint"
  return $true
}

function Find-OpenPorts([string]$HostAddress) {
  $ip = [System.Net.IPAddress]::Parse($HostAddress)
  $open = New-Object System.Collections.Generic.List[int]
  for ($first = $StartPort; $first -le $EndPort; $first += $BatchSize) {
    $last = [math]::Min($first + $BatchSize - 1, $EndPort)
    $probes = foreach ($port in $first..$last) {
      $client = New-Object System.Net.Sockets.TcpClient
      @{ Port = $port; Client = $client; Task = $client.ConnectAsync($ip, $port) }
    }
    Start-Sleep -Milliseconds $TimeoutMs
    foreach ($probe in $probes) {
      if ($probe.Task.Status -eq 'RanToCompletion') { $open.Add($probe.Port) }
      $probe.Client.Close()
    }
  }
  return $open
}

$connected = (Invoke-Adb 'devices') -split "`n" | Where-Object { $_ -match '^\S+\s+device$' }
if ($connected) {
  Write-Host "Already connected: $(($connected -split '\s+')[0])"
  exit 0
}

if ($Target -match ':\d+$' -and (Connect-Endpoint $Target)) { exit 0 }

$mdns = [regex]::Matches((Invoke-Adb 'mdns services'), '_adb-tls-connect\._tcp\.?\s+(\d+\.\d+\.\d+\.\d+:\d+)')
foreach ($match in $mdns) {
  if (Connect-Endpoint $match.Groups[1].Value) { exit 0 }
}

$lastEndpoint = if (Test-Path $stateFile) { (Get-Content $stateFile -TotalCount 1).Trim() } else { '' }
if ($lastEndpoint -and -not $Target -and (Connect-Endpoint $lastEndpoint)) { exit 0 }

$phoneHost = if ($Target) { ($Target -split ':')[0] } elseif ($lastEndpoint) { ($lastEndpoint -split ':')[0] } else { '' }
if (-not $phoneHost) {
  Write-Host 'No known phone address. Run with -Target <ip> or -Target <ip:port>.'
  exit 1
}

Write-Host "Scanning $phoneHost ports $StartPort-$EndPort..."
$ports = Find-OpenPorts $phoneHost
Write-Host "Open ports: $($ports -join ', ')"
foreach ($port in $ports) {
  if (Connect-Endpoint "${phoneHost}:$port") { exit 0 }
}

Write-Host 'Could not connect. Check that wireless debugging is on and the phone IP has not changed.'
exit 1
