param(
    [string]$StashUrl = "http://100.102.126.109:9999",
    [string]$FfmpegPath = "C:\ffmpeg\bin\ffmpeg.exe",
    [string]$FfprobePath = "C:\ffmpeg\bin\ffprobe.exe",
    [ValidateSet("LOW", "STANDARD", "STANDARD_HD", "FULL_HD", "FOUR_K", "ORIGINAL")]
    [string]$MaxResolution = "STANDARD_HD",
    [string[]]$GenerateSceneId = @(),
    [switch]$GenerateAll,
    [switch]$ForceTranscodes,
    [switch]$Apply
)

$ErrorActionPreference = "Stop"

function Invoke-StashGraphQL {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Query,
        [hashtable]$Variables = @{}
    )

    $body = @{
        query = $Query
        variables = $Variables
    } | ConvertTo-Json -Depth 12 -Compress

    $response = Invoke-RestMethod `
        -Uri "$($StashUrl.TrimEnd('/'))/graphql" `
        -Method Post `
        -ContentType "application/json" `
        -Body $body `
        -TimeoutSec 60

    if ($response.errors) {
        $response.errors | ConvertTo-Json -Depth 8
        throw "Stash GraphQL returned errors."
    }

    return $response.data
}

function Assert-File {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required file not found: $Path"
    }
}

Assert-File $FfmpegPath
Assert-File $FfprobePath

$current = Invoke-StashGraphQL -Query @"
query {
  configuration {
    general {
      ffmpegPath
      ffprobePath
      transcodeHardwareAcceleration
      maxTranscodeSize
      maxStreamingTranscodeSize
      generatedPath
    }
  }
}
"@

Write-Host "[INFO] Current Stash transcode settings:"
$current.configuration.general | Format-List

$configureMutation = @"
mutation ConfigureMobileTranscodes(`$input: ConfigGeneralInput!) {
  configureGeneral(input: `$input) {
    ffmpegPath
    ffprobePath
    transcodeHardwareAcceleration
    maxTranscodeSize
    maxStreamingTranscodeSize
    generatedPath
  }
}
"@

$defaultMutation = @"
mutation ConfigureGenerateDefaults(`$input: ConfigDefaultSettingsInput!) {
  configureDefaults(input: `$input) {
    generate {
      transcodes
    }
  }
}
"@

$configInput = @{
    ffmpegPath = $FfmpegPath
    ffprobePath = $FfprobePath
    transcodeHardwareAcceleration = $true
    maxTranscodeSize = $MaxResolution
    maxStreamingTranscodeSize = $MaxResolution
}

$defaultsInput = @{
    generate = @{
        transcodes = $true
        forceTranscodes = [bool]$ForceTranscodes
    }
}

if (-not $Apply) {
    Write-Host "[DRY-RUN] Would configure Stash with:"
    $configInput | Format-List
    Write-Host "[DRY-RUN] Would set metadata generation defaults:"
    $defaultsInput.generate | Format-List
    if ($GenerateSceneId.Count -gt 0 -or $GenerateAll) {
        Write-Host "[DRY-RUN] Would queue native Stash transcode generation."
    }
    Write-Host "[DRY-RUN] Re-run with -Apply to make changes."
    exit 0
}

$updated = Invoke-StashGraphQL -Query $configureMutation -Variables @{ input = $configInput }
Write-Host "[OK] Updated Stash transcode settings:"
$updated.configureGeneral | Format-List

$defaults = Invoke-StashGraphQL -Query $defaultMutation -Variables @{ input = $defaultsInput }
Write-Host "[OK] Updated Stash generation defaults:"
$defaults.configureDefaults.generate | Format-List

if ($GenerateAll -and $GenerateSceneId.Count -gt 0) {
    throw "Use either -GenerateAll or -GenerateSceneId, not both."
}

if ($GenerateAll -or $GenerateSceneId.Count -gt 0) {
    $generateMutation = @"
mutation GenerateMobileTranscodes(`$input: GenerateMetadataInput!) {
  metadataGenerate(input: `$input)
}
"@

    $generateInput = @{
        transcodes = $true
        forceTranscodes = [bool]$ForceTranscodes
    }

    if ($GenerateSceneId.Count -gt 0) {
        $generateInput.sceneIDs = $GenerateSceneId
    }

    $job = Invoke-StashGraphQL -Query $generateMutation -Variables @{ input = $generateInput }
    Write-Host "[OK] Queued Stash native transcode job: $($job.metadataGenerate)"
} else {
    Write-Host "[INFO] No transcode generation job queued. Use -GenerateSceneId <id> for a small test or -GenerateAll for the whole library."
}
