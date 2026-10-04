param(
    [string] $RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [Parameter(Mandatory = $true)]
    [string] $ApkPath,
    [Parameter(Mandatory = $true)]
    [string] $OutputDir,
    [Parameter(Mandatory = $true)]
    [string] $BuildToolsDir,
    [Parameter(Mandatory = $true)]
    [string] $PreviewTag,
    [string] $ArtworkApkPath = ''
)

# Verifies the formally signed APKs of one Bridge preview and stages them with SHA256SUMS. A
# preview tag extends the contract release tag with one or more hyphenated segments, for example
# v4.4.0-C17-Preview or v4.4.0-C16-Artwork. It keeps the contract versionCode, so testers can
# install it over the stable release and back.
#
# Pass -ArtworkApkPath to also ship the Dynamic Artwork Provider of the same release; its
# identity comes from contract.previewArtworkProvider and it must be signed by the same release
# certificate. Without it the preview carries the Bridge alone.

$ErrorActionPreference = 'Stop'

function Assert-PreviewAsset {
    param(
        [bool] $Condition,
        [string] $Message
    )
    if (-not $Condition) {
        throw "Preview asset violation: $Message"
    }
}

function Invoke-Checked {
    param(
        [string] $Executable,
        [string[]] $Arguments
    )
    $output = @(& $Executable @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed ($LASTEXITCODE): $Executable $($Arguments -join ' ')"
    }
    return $output
}

function Resolve-AbsolutePath {
    param([string] $Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Path))
}

function Assert-ApkContents {
    param(
        [string] $Apk,
        [object] $Contract,
        [string] $Label
    )
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($Apk)
    try {
        $dexEntries = @($archive.Entries | Where-Object { $_.FullName -match '^classes\d*\.dex$' })
        Assert-PreviewAsset ($dexEntries.Count -gt 0) "$Label APK contains no classes*.dex"
        foreach ($entry in $dexEntries) {
            $stream = $entry.Open()
            $memory = [System.IO.MemoryStream]::new()
            try {
                $stream.CopyTo($memory)
                $dexText = [System.Text.Encoding]::ASCII.GetString($memory.ToArray())
                foreach ($forbiddenValue in @($Contract.forbiddenApkAscii)) {
                    Assert-PreviewAsset (-not $dexText.Contains($forbiddenValue)) "$Label APK contains forbidden runtime string: $forbiddenValue"
                }
            } finally {
                $memory.Dispose()
                $stream.Dispose()
            }
        }
    } finally {
        $archive.Dispose()
    }
}

function Read-ApkBadging {
    param(
        [string] $Aapt2,
        [string] $Apk,
        [string] $Label
    )
    $badging = (Invoke-Checked $Aapt2 @('dump', 'badging', $Apk)) -join [Environment]::NewLine
    $packageMatch = [regex]::Match(
        $badging,
        "package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'"
    )
    Assert-PreviewAsset $packageMatch.Success "$Label APK package metadata unreadable"
    return $packageMatch
}

function Assert-ApkSignedByRelease {
    param(
        [string] $Apksigner,
        [string] $Apk,
        [object] $Contract,
        [string] $Label
    )
    $signerOutput = (Invoke-Checked $Apksigner @('verify', '--verbose', '--print-certs', $Apk)) -join [Environment]::NewLine
    $certificateMatch = [regex]::Match(
        $signerOutput,
        'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]{64})'
    )
    Assert-PreviewAsset $certificateMatch.Success "$Label APK signer certificate unreadable"
    $certificate = $certificateMatch.Groups[1].Value.ToLowerInvariant()
    Assert-PreviewAsset ($certificate -eq ([string]$Contract.releaseCertificateSha256).ToLowerInvariant()) "$Label APK is not signed by the frozen release certificate"
}

$contract = Get-Content -LiteralPath (Join-Path $RepoRoot 'release/bridge-release-contract.json') -Raw |
    ConvertFrom-Json
$tagPattern = '^' + [regex]::Escape([string]$contract.releaseTag) + '(-[0-9A-Za-z]+)+$'
Assert-PreviewAsset ($PreviewTag -cmatch $tagPattern) "tag must extend $($contract.releaseTag) with a hyphenated segment: $PreviewTag"
$expectedVersionName = $PreviewTag.Substring(1)
$assetName = "ColorOS-Live-Lyrics-Bridge-$PreviewTag.apk"

$resolvedApk = Resolve-AbsolutePath $ApkPath
$resolvedOutputDir = Resolve-AbsolutePath $OutputDir
$resolvedBuildTools = Resolve-AbsolutePath $BuildToolsDir
Assert-PreviewAsset (Test-Path -LiteralPath $resolvedApk -PathType Leaf) "missing APK: $resolvedApk"
$resolvedArtworkApk = ''
if (-not [string]::IsNullOrWhiteSpace($ArtworkApkPath)) {
    $resolvedArtworkApk = Resolve-AbsolutePath $ArtworkApkPath
    Assert-PreviewAsset (Test-Path -LiteralPath $resolvedArtworkApk -PathType Leaf) "missing artwork APK: $resolvedArtworkApk"
    Assert-PreviewAsset ($null -ne $contract.previewArtworkProvider) 'contract.previewArtworkProvider is missing'
}
New-Item -ItemType Directory -Path $resolvedOutputDir -Force | Out-Null
Assert-PreviewAsset (@(Get-ChildItem -LiteralPath $resolvedOutputDir -File).Count -eq 0) 'output directory must start empty'

$isWindowsHost = $env:OS -eq 'Windows_NT'
$aapt2 = Join-Path $resolvedBuildTools $(if ($isWindowsHost) { 'aapt2.exe' } else { 'aapt2' })
$apksigner = Join-Path $resolvedBuildTools $(if ($isWindowsHost) { 'apksigner.bat' } else { 'apksigner' })
$zipalign = Join-Path $resolvedBuildTools $(if ($isWindowsHost) { 'zipalign.exe' } else { 'zipalign' })
foreach ($tool in @($aapt2, $apksigner, $zipalign)) {
    Assert-PreviewAsset (Test-Path -LiteralPath $tool -PathType Leaf) "Android build tool is missing: $tool"
}

Assert-ApkContents -Apk $resolvedApk -Contract $contract -Label 'Bridge'
$packageMatch = Read-ApkBadging -Aapt2 $aapt2 -Apk $resolvedApk -Label 'Bridge'
Assert-PreviewAsset ($packageMatch.Groups[1].Value -eq [string]$contract.bridgeApplicationId) 'Bridge applicationId differs from contract'
Assert-PreviewAsset ($packageMatch.Groups[2].Value -eq [string]$contract.versionCode) 'Bridge versionCode differs from contract'
Assert-PreviewAsset ($packageMatch.Groups[3].Value -ceq $expectedVersionName) "Bridge versionName is not $expectedVersionName"
Assert-ApkSignedByRelease -Apksigner $apksigner -Apk $resolvedApk -Contract $contract -Label 'Bridge'
Invoke-Checked $zipalign @('-c', '-P', '16', '4', $resolvedApk) | Out-Null

$targetPath = Join-Path $resolvedOutputDir $assetName
Copy-Item -LiteralPath $resolvedApk -Destination $targetPath
$hash = (Get-FileHash -LiteralPath $targetPath -Algorithm SHA256).Hash.ToLowerInvariant()
$checksumLines = @("$hash  $assetName")
$expectedAssetCount = 2

if (-not [string]::IsNullOrWhiteSpace($resolvedArtworkApk)) {
    $artwork = $contract.previewArtworkProvider
    $artworkAssetName = [string]$artwork.asset
    Assert-PreviewAsset (-not [string]::IsNullOrWhiteSpace($artworkAssetName)) 'artwork asset name is missing from the contract'

    Assert-ApkContents -Apk $resolvedArtworkApk -Contract $contract -Label 'Artwork'
    $artworkMatch = Read-ApkBadging -Aapt2 $aapt2 -Apk $resolvedArtworkApk -Label 'Artwork'
    Assert-PreviewAsset ($artworkMatch.Groups[1].Value -eq [string]$artwork.applicationId) 'Artwork applicationId differs from contract'
    Assert-PreviewAsset ($artworkMatch.Groups[2].Value -eq [string]$artwork.versionCode) 'Artwork versionCode differs from contract'
    Assert-PreviewAsset ($artworkMatch.Groups[3].Value -ceq [string]$artwork.versionName) 'Artwork versionName differs from contract'
    Assert-ApkSignedByRelease -Apksigner $apksigner -Apk $resolvedArtworkApk -Contract $contract -Label 'Artwork'
    Invoke-Checked $zipalign @('-c', '-P', '16', '4', $resolvedArtworkApk) | Out-Null

    $artworkTarget = Join-Path $resolvedOutputDir $artworkAssetName
    Copy-Item -LiteralPath $resolvedArtworkApk -Destination $artworkTarget
    $artworkHash = (Get-FileHash -LiteralPath $artworkTarget -Algorithm SHA256).Hash.ToLowerInvariant()
    $checksumLines += "$artworkHash  $artworkAssetName"
    $expectedAssetCount = 3
}

[System.IO.File]::WriteAllText(
    (Join-Path $resolvedOutputDir ([string]$contract.checksumsAsset)),
    (($checksumLines | Sort-Object) -join [Environment]::NewLine) + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)

$finalAssets = @(Get-ChildItem -LiteralPath $resolvedOutputDir -File)
Assert-PreviewAsset ($finalAssets.Count -eq $expectedAssetCount) "expected $expectedAssetCount preview assets, found $($finalAssets.Count)"
if ($expectedAssetCount -eq 3) {
    Write-Output "Preview assets verified: $assetName (sha256=$hash) and $($contract.previewArtworkProvider.asset) (sha256=$artworkHash)."
} else {
    Write-Output "Preview asset verified: $assetName (sha256=$hash)."
}

