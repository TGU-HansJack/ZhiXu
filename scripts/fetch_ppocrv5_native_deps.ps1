$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$dest = Join-Path $root "apps/zhixu-android/src/main/cpp/ppocrv5/third_party"
New-Item -ItemType Directory -Force -Path $dest | Out-Null

$ncnnVersion = "20250916"
$ncnnZipName = "ncnn-$ncnnVersion-android-vulkan.zip"
$ncnnUrl = "https://github.com/Tencent/ncnn/releases/download/$ncnnVersion/$ncnnZipName"

$opencvTag = "v34"
$opencvZipName = "opencv-mobile-4.12.0-android.zip"
$opencvUrl = "https://github.com/nihui/opencv-mobile/releases/download/$opencvTag/$opencvZipName"

function Download-And-Extract($url, $zipName) {
  $zipPath = Join-Path $dest $zipName
  if (-not (Test-Path $zipPath)) {
    Write-Host "Downloading $url"
    Invoke-WebRequest -Uri $url -OutFile $zipPath
  } else {
    Write-Host "Zip exists: $zipPath"
  }

  Write-Host "Extracting $zipName -> $dest"
  Expand-Archive -Force -Path $zipPath -DestinationPath $dest
}

Download-And-Extract $ncnnUrl $ncnnZipName
Download-And-Extract $opencvUrl $opencvZipName

Write-Host "Done. Expected directories:"
Write-Host " - $dest/ncnn-$ncnnVersion-android-vulkan"
Write-Host " - $dest/opencv-mobile-4.12.0-android"

