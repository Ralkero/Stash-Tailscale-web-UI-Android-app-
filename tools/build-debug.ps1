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
$jdkRoot = Join-Path $ToolsRoot 'jdk17'
$gradleBat = Join-Path $ToolsRoot 'gradle\bin\gradle.bat'

$env:JAVA_HOME = $jdkRoot
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:Path = "$jdkRoot\bin;$SdkRoot\platform-tools;$env:Path"

if (-not (Test-Path -LiteralPath $gradleBat)) {
    throw "Local Gradle was not found at $gradleBat. Run tools\bootstrap-android-toolchain.ps1 first."
}

Push-Location $projectRoot
try {
    & $gradleBat assembleDebug
    if ($LASTEXITCODE -ne 0) {
        throw "assembleDebug failed with exit code $LASTEXITCODE."
    }
    & $gradleBat lintDebug
    if ($LASTEXITCODE -ne 0) {
        throw "lintDebug failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}
