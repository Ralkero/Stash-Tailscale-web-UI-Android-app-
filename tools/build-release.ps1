[CmdletBinding()]
param(
    [string]$ToolsRoot,
    [string]$SdkRoot,
    [string]$KeystorePath,
    [string]$KeyAlias = 'stash-wrapper'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $ToolsRoot) {
    $ToolsRoot = Join-Path $projectRoot '.tools'
}
if (-not $SdkRoot) {
    $SdkRoot = Join-Path $projectRoot '.android-sdk'
}
if (-not $KeystorePath) {
    $KeystorePath = Join-Path $projectRoot 'signing\stash-wrapper-release.jks'
}

$jdkRoot = Join-Path $ToolsRoot 'jdk17'
$gradleBat = Join-Path $ToolsRoot 'gradle\bin\gradle.bat'
$keytool = Join-Path $jdkRoot 'bin\keytool.exe'

if (-not (Test-Path -LiteralPath $gradleBat)) {
    throw "Local Gradle was not found at $gradleBat. Run tools\bootstrap-android-toolchain.ps1 first."
}
if (-not (Test-Path -LiteralPath $keytool)) {
    throw "keytool was not found at $keytool. Run tools\bootstrap-android-toolchain.ps1 first."
}

$env:JAVA_HOME = $jdkRoot
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:Path = "$jdkRoot\bin;$SdkRoot\platform-tools;$env:Path"

function Read-PlainSecret([string]$Prompt) {
    $secure = Read-Host -Prompt $Prompt -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

$keystoreDir = Split-Path -Parent $KeystorePath
if (-not (Test-Path -LiteralPath $keystoreDir)) {
    New-Item -ItemType Directory -Path $keystoreDir -Force | Out-Null
}

if (-not (Test-Path -LiteralPath $KeystorePath)) {
    Write-Host "Creating release keystore at $KeystorePath"
    $storePass = Read-PlainSecret 'New keystore password'
    $keyPass = Read-PlainSecret 'New key password'
    if ($storePass.Length -lt 6 -or $keyPass.Length -lt 6) {
        throw 'Keystore and key passwords must be at least 6 characters.'
    }
    & $keytool -genkeypair `
        -v `
        -keystore $KeystorePath `
        -storepass $storePass `
        -keypass $keyPass `
        -alias $KeyAlias `
        -keyalg RSA `
        -keysize 4096 `
        -validity 10000 `
        -dname 'CN=Private Stash Wrapper, OU=Private, O=Local, L=Local, S=Local, C=US'
    if ($LASTEXITCODE -ne 0) {
        throw "keytool failed with exit code $LASTEXITCODE."
    }
}
else {
    Write-Host "Using existing release keystore at $KeystorePath"
    $storePass = Read-PlainSecret 'Keystore password'
    $keyPass = Read-PlainSecret 'Key password'
}

$env:STASH_WRAPPER_KEYSTORE_FILE = $KeystorePath
$env:STASH_WRAPPER_KEY_ALIAS = $KeyAlias
$env:STASH_WRAPPER_KEYSTORE_PASSWORD = $storePass
$env:STASH_WRAPPER_KEY_PASSWORD = $keyPass

Push-Location $projectRoot
try {
    & $gradleBat assembleRelease
    if ($LASTEXITCODE -ne 0) {
        throw "assembleRelease failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
    Remove-Item Env:\STASH_WRAPPER_KEYSTORE_FILE -ErrorAction SilentlyContinue
    Remove-Item Env:\STASH_WRAPPER_KEY_ALIAS -ErrorAction SilentlyContinue
    Remove-Item Env:\STASH_WRAPPER_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\STASH_WRAPPER_KEY_PASSWORD -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host "Release APK: $projectRoot\app\build\outputs\apk\release\app-release.apk"

