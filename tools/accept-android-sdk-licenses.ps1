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
$cmdlineLatest = Join-Path $SdkRoot 'cmdline-tools\latest'
$sdkmanager = Join-Path $cmdlineLatest 'bin\sdkmanager.bat'

if (-not (Test-Path -LiteralPath $sdkmanager)) {
    throw "sdkmanager was not found at $sdkmanager. Run tools\bootstrap-android-toolchain.ps1 first."
}
if (-not (Test-Path -LiteralPath (Join-Path $jdkRoot 'bin\java.exe'))) {
    throw "JDK was not found at $jdkRoot. Run tools\bootstrap-android-toolchain.ps1 first."
}

$env:JAVA_HOME = $jdkRoot
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:Path = "$jdkRoot\bin;$cmdlineLatest\bin;$SdkRoot\platform-tools;$env:Path"

Write-Host 'Android SDK license acceptance is a manual legal step.'
Write-Host 'Read the prompt below. Type y only if you accept the license terms.'
& $sdkmanager --sdk_root=$SdkRoot --licenses

