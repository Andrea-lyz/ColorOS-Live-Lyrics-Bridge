param(
    [string] $RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [Parameter(Mandatory = $true)]
    [string] $InputDir,
    [Parameter(Mandatory = $true)]
    [string] $OutputDir,
    [Parameter(Mandatory = $true)]
    [string] $ProviderRepoRoot,
    [Parameter(Mandatory = $true)]
    [string] $BuildToolsDir,
    [Parameter(Mandatory = $true)]
    [string] $Batch,
    [Parameter(Mandatory = $true)]
    [string] $BridgeCommit,
    [Parameter(Mandatory = $true)]
    [string] $ProviderCommit
)

$ErrorActionPreference = 'Stop'

function Assert-ReleaseAsset {
    param(
        [bool] $Condition,
        [string] $Message
    )
    if (-not $Condition) {
        throw "Release asset violation: $Message"
    }
}

function Invoke-Checked {
    param(
        [string] $Executable,
        [string[]] $Arguments
    )
    $output = @(& $Executable @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed ($LASTEXITCODE): $Executable $($Arguments -join ' ')`n$($output -join "`n")"
    }
    return $output
}

function Get-Sha256Lower {
    param([string] $Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Assert-ApkRuntimeStrings {
    param(
        [string] $Path,
        [string] $AssetName,
        [string[]] $ForbiddenValues
    )
    $archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $dexEntries = @($archive.Entries | Where-Object { $_.FullName -match '^classes\d*\.dex$' })
        Assert-ReleaseAsset ($dexEntries.Count -gt 0) "$AssetName contains no classes*.dex"
        foreach ($entry in $dexEntries) {
            $stream = $entry.Open()
            $memory = [System.IO.MemoryStream]::new()
            try {
                $stream.CopyTo($memory)
                $dexText = [System.Text.Encoding]::ASCII.GetString($memory.ToArray())
                foreach ($forbiddenValue in $ForbiddenValues) {
                    Assert-ReleaseAsset (-not $dexText.Contains($forbiddenValue)) "$AssetName contains forbidden runtime string: $forbiddenValue"
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

function Write-Utf8NoBom {
    param(
        [string] $Path,
        [string] $Content
    )
    [System.IO.File]::WriteAllText(
        $Path,
        $Content,
        [System.Text.UTF8Encoding]::new($false)
    )
}

function Resolve-AbsolutePath {
    param([string] $Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $Path))
}

$resolvedInputDir = Resolve-AbsolutePath $InputDir
$resolvedOutputDir = Resolve-AbsolutePath $OutputDir
$resolvedProviderRoot = Resolve-AbsolutePath $ProviderRepoRoot
$resolvedBuildTools = Resolve-AbsolutePath $BuildToolsDir

$bridgeContractPath = Join-Path $RepoRoot 'release\bridge-release-contract.json'
$providerContractPath = Join-Path $resolvedProviderRoot 'release\v5-provider-matrix.json'
$bridgeContract = Get-Content -LiteralPath $bridgeContractPath -Raw | ConvertFrom-Json
$providerContract = Get-Content -LiteralPath $providerContractPath -Raw | ConvertFrom-Json
Add-Type -AssemblyName System.IO.Compression.FileSystem

& (Join-Path $RepoRoot 'scripts\validate-release-contract.ps1') -ProviderRepoRoot $resolvedProviderRoot

Assert-ReleaseAsset (Test-Path -LiteralPath $resolvedInputDir -PathType Container) 'input directory is missing'
New-Item -ItemType Directory -Path $resolvedOutputDir -Force | Out-Null
Assert-ReleaseAsset (@(Get-ChildItem -LiteralPath $resolvedOutputDir -File).Count -eq 0) 'output directory must start empty'

$isWindowsHost = $env:OS -eq 'Windows_NT'
$aapt2 = Join-Path $resolvedBuildTools $(if ($isWindowsHost) { 'aapt2.exe' } else { 'aapt2' })
$apksigner = Join-Path $resolvedBuildTools $(if ($isWindowsHost) { 'apksigner.bat' } else { 'apksigner' })
$zipalign = Join-Path $resolvedBuildTools $(if ($isWindowsHost) { 'zipalign.exe' } else { 'zipalign' })
foreach ($tool in @($aapt2, $apksigner, $zipalign)) {
    Assert-ReleaseAsset (Test-Path -LiteralPath $tool -PathType Leaf) "Android build tool is missing: $tool"
}

$expectedApkNames = @([string]$bridgeContract.bridgeAsset) + @(
    $providerContract.providers | ForEach-Object { [string]$_.asset }
)
$incomingApks = @(Get-ChildItem -LiteralPath $resolvedInputDir -File -Filter '*.apk')
Assert-ReleaseAsset ($incomingApks.Count -eq $bridgeContract.totalApkCount) "expected $($bridgeContract.totalApkCount) incoming APKs, found $($incomingApks.Count)"
Assert-ReleaseAsset (-not (Compare-Object ($incomingApks.Name | Sort-Object) ($expectedApkNames | Sort-Object))) 'incoming APK names differ from contract'

$assetRecords = @()
$providerOutputPaths = @()
$expectedCertificate = ([string]$bridgeContract.releaseCertificateSha256).ToLowerInvariant()

function Verify-And-StageApk {
    param(
        [string] $AssetName,
        [string] $ExpectedApplicationId,
        [string] $ExpectedVersionName,
        [int] $ExpectedVersionCode,
        [string] $Kind,
        [string] $Module
    )

    $sourcePath = Join-Path $resolvedInputDir $AssetName
    Assert-ReleaseAsset (Test-Path -LiteralPath $sourcePath -PathType Leaf) "missing APK: $AssetName"
    Assert-ApkRuntimeStrings $sourcePath $AssetName @($bridgeContract.forbiddenApkAscii)

    $badging = (Invoke-Checked $aapt2 @('dump', 'badging', $sourcePath)) -join "`n"
    $packageMatch = [regex]::Match(
        $badging,
        "package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'"
    )
    Assert-ReleaseAsset $packageMatch.Success "unable to parse package metadata: $AssetName"
    Assert-ReleaseAsset ($packageMatch.Groups[1].Value -eq $ExpectedApplicationId) "$AssetName applicationId differs"
    Assert-ReleaseAsset ($packageMatch.Groups[2].Value -eq [string]$ExpectedVersionCode) "$AssetName versionCode differs"
    Assert-ReleaseAsset ($packageMatch.Groups[3].Value -eq $ExpectedVersionName) "$AssetName versionName differs"

    $signerOutput = (Invoke-Checked $apksigner @('verify', '--verbose', '--print-certs', $sourcePath)) -join "`n"
    $certificateMatch = [regex]::Match(
        $signerOutput,
        'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]{64})'
    )
    Assert-ReleaseAsset $certificateMatch.Success "unable to read signer certificate: $AssetName"
    $certificate = $certificateMatch.Groups[1].Value.ToLowerInvariant()
    Assert-ReleaseAsset ($certificate -eq $expectedCertificate) "$AssetName is not signed by the frozen release certificate"

    Invoke-Checked $zipalign @('-c', '-P', '16', '4', $sourcePath) | Out-Null

    $targetPath = Join-Path $resolvedOutputDir $AssetName
    Copy-Item -LiteralPath $sourcePath -Destination $targetPath
    $target = Get-Item -LiteralPath $targetPath
    $script:assetRecords += [ordered]@{
        type = $Kind
        module = $Module
        name = $AssetName
        applicationId = $ExpectedApplicationId
        versionName = $ExpectedVersionName
        versionCode = $ExpectedVersionCode
        bytes = $target.Length
        sha256 = Get-Sha256Lower $targetPath
        certificateSha256 = $certificate
    }
    return $targetPath
}

$bridgeAssetArguments = @{
    AssetName = [string]$bridgeContract.bridgeAsset
    ExpectedApplicationId = [string]$bridgeContract.bridgeApplicationId
    ExpectedVersionName = [string]$bridgeContract.suiteVersion
    ExpectedVersionCode = [int]$bridgeContract.versionCode
    Kind = 'bridge-apk'
    Module = 'app'
}
Verify-And-StageApk @bridgeAssetArguments | Out-Null

foreach ($provider in @($providerContract.providers)) {
    $providerAssetArguments = @{
        AssetName = [string]$provider.asset
        ExpectedApplicationId = [string]$provider.applicationId
        ExpectedVersionName = [string]$provider.versionName
        ExpectedVersionCode = [int]$provider.versionCode
        Kind = 'provider-apk'
        Module = [string]$provider.module
    }
    $providerOutputPaths += Verify-And-StageApk @providerAssetArguments
}

Assert-ReleaseAsset ($providerOutputPaths.Count -eq $bridgeContract.providerApkCount) 'Provider APK count changed while staging'
$bundlePath = Join-Path $resolvedOutputDir ([string]$bridgeContract.providerBundleAsset)
Compress-Archive -LiteralPath $providerOutputPaths -DestinationPath $bundlePath -CompressionLevel Optimal

$bundle = [System.IO.Compression.ZipFile]::OpenRead($bundlePath)
try {
    $entries = @($bundle.Entries | Where-Object { -not [string]::IsNullOrWhiteSpace($_.Name) })
    Assert-ReleaseAsset ($entries.Count -eq $bridgeContract.providerApkCount) 'Provider bundle entry count differs'
    Assert-ReleaseAsset (-not (Compare-Object ($entries.FullName | Sort-Object) ((@($providerContract.providers).asset) | Sort-Object))) 'Provider bundle entries differ from contract'
    Assert-ReleaseAsset (@($entries | Where-Object { $_.FullName -ne $_.Name }).Count -eq 0) 'Provider bundle must contain top-level APKs only'
} finally {
    $bundle.Dispose()
}

$bundleFile = Get-Item -LiteralPath $bundlePath
$assetRecords += [ordered]@{
    type = 'provider-bundle'
    module = $null
    name = $bundleFile.Name
    applicationId = $null
    versionName = $bridgeContract.suiteVersion
    versionCode = $null
    bytes = $bundleFile.Length
    sha256 = Get-Sha256Lower $bundlePath
    certificateSha256 = $null
}

$manifest = [ordered]@{
    schema = 1
    batch = $Batch
    suiteVersion = $bridgeContract.suiteVersion
    releaseTag = $bridgeContract.releaseTag
    lspTag = $bridgeContract.lspTag
    bridgeCommit = $BridgeCommit
    providerCommit = $ProviderCommit
    providerSourceTag = $bridgeContract.providersSourceTag
    releaseCertificateSha256 = $expectedCertificate
    generatedAtUtc = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
    assets = $assetRecords
}
$manifestPath = Join-Path $resolvedOutputDir ([string]$bridgeContract.assetManifest)
Write-Utf8NoBom $manifestPath ($manifest | ConvertTo-Json -Depth 8)

$checksumTargets = @(
    Get-ChildItem -LiteralPath $resolvedOutputDir -File |
        Sort-Object Name
)
$checksumLines = @(
    $checksumTargets | ForEach-Object {
        "$(Get-Sha256Lower $_.FullName)  $($_.Name)"
    }
)
$checksumsPath = Join-Path $resolvedOutputDir ([string]$bridgeContract.checksumsAsset)
Write-Utf8NoBom $checksumsPath (($checksumLines -join "`n") + "`n")

$finalAssets = @(Get-ChildItem -LiteralPath $resolvedOutputDir -File)
Assert-ReleaseAsset ($finalAssets.Count -eq $bridgeContract.totalReleaseAssetCount) "expected $($bridgeContract.totalReleaseAssetCount) final assets, found $($finalAssets.Count)"
Write-Output "Release batch verified: $Batch, APKs=$($bridgeContract.totalApkCount), assets=$($finalAssets.Count)."
