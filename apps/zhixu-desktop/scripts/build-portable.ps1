$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$tauriDir = Join-Path $projectRoot "src-tauri"
$releaseDir = Join-Path $tauriDir "target\\release"
$bundleDir = Join-Path $projectRoot "dist-portable"

if (Test-Path $bundleDir) {
  Remove-Item -Recurse -Force $bundleDir
}
New-Item -ItemType Directory -Path $bundleDir | Out-Null

Write-Host "[portable] Building (no bundles)..."
tauri build --no-bundle

if (-not (Test-Path $releaseDir)) {
  throw "Release directory not found: $releaseDir"
}

$exe = Get-ChildItem -Path $releaseDir -Filter *.exe -File |
  Where-Object { $_.Name -notmatch '^(cargo-|rustc-|build-|deps)' } |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1

if (-not $exe) {
  throw "No .exe found in $releaseDir"
}

$packageJson = Get-Content (Join-Path $projectRoot "package.json") -Raw | ConvertFrom-Json
$version = $packageJson.version

$appName = "Zhixu"
$portableRoot = Join-Path $bundleDir $appName
New-Item -ItemType Directory -Path $portableRoot | Out-Null

Copy-Item -Force $exe.FullName (Join-Path $portableRoot "$appName.exe")

$zipName = "$appName-portable-win64-v$version.zip"
$zipPath = Join-Path $bundleDir $zipName

if (Test-Path $zipPath) {
  Remove-Item -Force $zipPath
}

Write-Host "[portable] Zipping to $zipPath"
Compress-Archive -Path (Join-Path $portableRoot "*") -DestinationPath $zipPath -Force

Write-Host "[portable] Done:"
Write-Host "  $zipPath"
