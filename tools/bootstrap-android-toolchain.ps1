[CmdletBinding()]
param(
    [string]$ToolsRoot,
    [string]$SdkRoot
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $ToolsRoot) {
    $ToolsRoot = Join-Path $projectRoot '.tools'
}
if (-not $SdkRoot) {
    $SdkRoot = Join-Path $projectRoot '.android-sdk'
}
$downloads = Join-Path $ToolsRoot 'downloads'
$jdkRoot = Join-Path $ToolsRoot 'jdk17'
$gradleRoot = Join-Path $ToolsRoot 'gradle'
$cmdlineZip = Join-Path $downloads 'commandlinetools-win.zip'
$jdkZip = Join-Path $downloads 'jdk17.zip'
$gradleZip = Join-Path $downloads 'gradle-bin.zip'

function Ensure-Dir([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
    }
}

function Download-IfMissing([string]$Uri, [string]$OutFile) {
    if (Test-Path -LiteralPath $OutFile) {
        Write-Host "[OK] Already downloaded: $OutFile"
        return
    }
    Write-Host "[INFO] Downloading $Uri"
    Invoke-WebRequest -Uri $Uri -OutFile $OutFile -UseBasicParsing
}

Ensure-Dir $downloads
Ensure-Dir $ToolsRoot
Ensure-Dir $SdkRoot

Download-IfMissing `
    -Uri 'https://dl.google.com/android/repository/commandlinetools-win-14742923_latest.zip' `
    -OutFile $cmdlineZip
Download-IfMissing `
    -Uri 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk' `
    -OutFile $jdkZip
Download-IfMissing `
    -Uri 'https://services.gradle.org/distributions/gradle-8.10.2-bin.zip' `
    -OutFile $gradleZip

if (-not (Test-Path -LiteralPath (Join-Path $jdkRoot 'bin\java.exe'))) {
    Write-Host "[INFO] Extracting JDK"
    $tmpJdk = Join-Path $ToolsRoot 'jdk-extract'
    if (Test-Path -LiteralPath $tmpJdk) { Remove-Item -LiteralPath $tmpJdk -Recurse -Force }
    Expand-Archive -LiteralPath $jdkZip -DestinationPath $tmpJdk -Force
    $extractedJdk = Get-ChildItem -LiteralPath $tmpJdk -Directory | Select-Object -First 1
    if (-not $extractedJdk) { throw 'JDK archive did not contain a root directory.' }
    Move-Item -LiteralPath $extractedJdk.FullName -Destination $jdkRoot -Force
    Remove-Item -LiteralPath $tmpJdk -Recurse -Force
}

if (-not (Test-Path -LiteralPath (Join-Path $gradleRoot 'bin\gradle.bat'))) {
    Write-Host "[INFO] Extracting Gradle"
    $tmpGradle = Join-Path $ToolsRoot 'gradle-extract'
    if (Test-Path -LiteralPath $tmpGradle) { Remove-Item -LiteralPath $tmpGradle -Recurse -Force }
    Expand-Archive -LiteralPath $gradleZip -DestinationPath $tmpGradle -Force
    $extractedGradle = Get-ChildItem -LiteralPath $tmpGradle -Directory | Select-Object -First 1
    if (-not $extractedGradle) { throw 'Gradle archive did not contain a root directory.' }
    Move-Item -LiteralPath $extractedGradle.FullName -Destination $gradleRoot -Force
    Remove-Item -LiteralPath $tmpGradle -Recurse -Force
}

$cmdlineLatest = Join-Path $SdkRoot 'cmdline-tools\latest'
if (-not (Test-Path -LiteralPath (Join-Path $cmdlineLatest 'bin\sdkmanager.bat'))) {
    Write-Host "[INFO] Extracting Android command-line tools"
    $tmpCmdline = Join-Path $ToolsRoot 'cmdline-extract'
    if (Test-Path -LiteralPath $tmpCmdline) { Remove-Item -LiteralPath $tmpCmdline -Recurse -Force }
    Expand-Archive -LiteralPath $cmdlineZip -DestinationPath $tmpCmdline -Force
    Ensure-Dir (Split-Path -Parent $cmdlineLatest)
    Move-Item -LiteralPath (Join-Path $tmpCmdline 'cmdline-tools') -Destination $cmdlineLatest -Force
    Remove-Item -LiteralPath $tmpCmdline -Recurse -Force
}

$env:JAVA_HOME = $jdkRoot
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:Path = "$jdkRoot\bin;$cmdlineLatest\bin;$SdkRoot\platform-tools;$gradleRoot\bin;$env:Path"

Write-Host "[INFO] Installing Android SDK packages. This may prompt for SDK license acceptance."
& (Join-Path $cmdlineLatest 'bin\sdkmanager.bat') --sdk_root=$SdkRoot `
    'platform-tools' `
    'platforms;android-35' `
    'build-tools;35.0.0'

$requiredPaths = @(
    (Join-Path $SdkRoot 'platform-tools\adb.exe'),
    (Join-Path $SdkRoot 'platforms\android-35\android.jar'),
    (Join-Path $SdkRoot 'build-tools\35.0.0\aapt2.exe')
)
$missing = @($requiredPaths | Where-Object { -not (Test-Path -LiteralPath $_) })
if ($missing.Count -gt 0) {
    Write-Host ''
    Write-Host 'NEXT STEP: Accept the Android SDK license, then rerun this bootstrap script.'
    Write-Host "Run: powershell -NoProfile -ExecutionPolicy Bypass -File `"$projectRoot\tools\accept-android-sdk-licenses.ps1`""
    Write-Host ''
    Write-Host 'Missing after SDK install attempt:'
    $missing | ForEach-Object { Write-Host " - $_" }
    exit 2
}

Write-Host "[INFO] Generating Gradle wrapper"
Push-Location $projectRoot
try {
    & (Join-Path $gradleRoot 'bin\gradle.bat') wrapper --gradle-version 8.10.2
}
finally {
    Pop-Location
}

Write-Host "[OK] Android toolchain bootstrap complete."
Write-Host "[INFO] JAVA_HOME=$jdkRoot"
Write-Host "[INFO] ANDROID_HOME=$SdkRoot"
