$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$tauriCacheDir = Join-Path $env:LOCALAPPDATA "tauri"
$nsisDir = Join-Path $tauriCacheDir "NSIS"
$nsisZipUrl = "https://github.com/tauri-apps/binary-releases/releases/download/nsis-3.11/nsis-3.11.zip"
$nsisZipPath = Join-Path $tauriCacheDir "nsis-3.11.zip"

function Invoke-DownloadWithRetry {
  param(
    [Parameter(Mandatory = $true)][string]$Url,
    [Parameter(Mandatory = $true)][string]$OutFile
  )

  $maxAttempts = 5
  for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
    try {
      Invoke-WebRequest -Uri $Url -OutFile $OutFile -TimeoutSec 300
      return
    } catch {
      if ($attempt -eq $maxAttempts) { throw }
      Write-Host "[exe] Download failed (attempt $attempt/$maxAttempts), retrying..."
      Start-Sleep -Seconds (3 * $attempt)
    }
  }
}

if (-not (Test-Path (Join-Path $nsisDir "makensis.exe"))) {
  Write-Host "[exe] Bootstrapping NSIS to $nsisDir"

  if (Test-Path $nsisDir) {
    Remove-Item -Recurse -Force $nsisDir
  }
  New-Item -ItemType Directory -Path $tauriCacheDir -Force | Out-Null

  Write-Host "[exe] Downloading NSIS..."
  Invoke-DownloadWithRetry -Url $nsisZipUrl -OutFile $nsisZipPath

  $extractDir = Join-Path $tauriCacheDir "nsis-3.11-extract"
  if (Test-Path $extractDir) {
    Remove-Item -Recurse -Force $extractDir
  }
  New-Item -ItemType Directory -Path $extractDir | Out-Null

  Expand-Archive -Path $nsisZipPath -DestinationPath $extractDir -Force
  $rootDir = Get-ChildItem -Directory $extractDir | Select-Object -First 1
  if (-not $rootDir) {
    throw "NSIS archive extraction failed (no root directory): $nsisZipPath"
  }

  New-Item -ItemType Directory -Path $nsisDir -Force | Out-Null
  Copy-Item -Path (Join-Path $rootDir.FullName "*") -Destination $nsisDir -Recurse -Force

  Remove-Item -Recurse -Force $extractDir
}

$nsisPluginDir = Join-Path $tauriCacheDir "NSIS\\Plugins\\x86-unicode"
$nsisUtilsDll = Join-Path $nsisPluginDir "nsis_tauri_utils.dll"

if (-not (Test-Path $nsisUtilsDll)) {
  New-Item -ItemType Directory -Path $nsisPluginDir -Force | Out-Null

  $url = "https://github.com/tauri-apps/nsis-tauri-utils/releases/download/nsis_tauri_utils-v0.5.2/nsis_tauri_utils.dll"
  Write-Host "[exe] Downloading nsis_tauri_utils.dll to $nsisUtilsDll"
  Invoke-DownloadWithRetry -Url $url -OutFile $nsisUtilsDll
}

Write-Host "[exe] Building NSIS installer..."
tauri build
