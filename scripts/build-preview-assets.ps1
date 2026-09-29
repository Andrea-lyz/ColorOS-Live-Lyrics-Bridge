param(
    [string] $RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [Parameter(Mandatory = $true)]
    [string] $ApkPath,
    [Parameter(Mandatory = $true)]
    [string] $OutputDir,
    [Parameter(Mandatory = $true)]
    [string] $BuildToolsDir,
    [Parameter(Mandatory = $true)]
    [string] $PreviewTag
)

# Verifies one formally signed Bridge preview APK and stages it with SHA256SUMS. A preview tag
# extends the contract release tag and ends in -Preview, for example v4.4.0-C17-Preview. It
# keeps the contract versionCode, so testers can install it over the stable release and back.
# Providers are not part of a preview.

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
        throw "Command failed ($LASTEXITCODE): $Executable $($Arguments -join ' ')`n$($output -join "`n")"
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

$contract = Get-Content -LiteralPath (Join-Path $RepoRoot 'release/bridge-release-contract.json') -Raw |
    ConvertFrom-Json
$tagPattern = '^' + [regex]::Escape([string]$contract.releaseTag) + '(-[0-9A-Za-z]+)+-Preview$'
Assert-PreviewAsset ($PreviewTag -cmatch $tagPattern) "tag must extend $($contract.releaseTag) and end in -Preview: $PreviewTag"
$expectedVersionName = $PreviewTag.Substring(1)
$assetName = "ColorOS-Live-Lyrics-Bridge-$PreviewTag.apk"

$resolvedApk = Resolve-AbsolutePath $ApkPath
$resolvedOutputDir = Resolve-AbsolutePath $OutputDir
$resolvedBuildTools = Resolve-AbsolutePath $BuildToolsDir
Assert-PreviewAsset (Test-Path -LiteralPath $resolvedApk -PathType Leaf) "missing APK: $resolvedApk"
New-Item -ItemType Directory -Path $resolvedOutputDir -Force | Out-Null
Assert-PreviewAsset (@(Get-ChildItem -LiteralPath $resolvedOutputDir -File).Count -eq 0) 'output directory must start empty'

$isWindowsHost = $env:OS -eq 'Windows_NT'
$aapt2 = Join-Path $resolvedBuildTools $(if ($isWindowsHost) { 'aapt2.exe' } else { 'aapt2' })
$apksigner = Join-Path $resolvedBuildTools $(if ($isWindowsHost) { 'apksigner.bat' } else { 'apksigner' })
$zipalign = Join-Path $resolvedBuildTools $(if ($isWindowsHost) { 'zipalign.exe' } else { 'zipalign' })
foreach ($tool in @($aapt2, $apksigner, $zipalign)) {
    Assert-PreviewAsset (Test-Path -LiteralPath $tool -PathType Leaf) "Android build tool is missing: $tool"
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($resolvedApk)
try {
    $dexEntries = @($archive.Entries | Where-Object { $_.FullName -match '^classes\d*\.dex$' })
    Assert-PreviewAsset ($dexEntries.Count -gt 0) 'APK contains no classes*.dex'
    foreach ($entry in $dexEntries) {
        $stream = $entry.Open()
        $memory = [System.IO.MemoryStream]::new()
        try {
            $stream.CopyTo($memory)
            $dexText = [System.Text.Encoding]::ASCII.GetString($memory.ToArray())
            foreach ($forbiddenValue in @($contract.forbiddenApkAscii)) {
                Assert-PreviewAsset (-not $dexText.Contains($forbiddenValue)) "APK contains forbidden runtime string: $forbiddenValue"
            }
        } finally {
            $memory.Dispose()
            $stream.Dispose()
        }
    }
} finally {
    $archive.Dispose()
}

$badging = (Invoke-Checked $aapt2 @('dump', 'badging', $resolvedApk)) -join "`n"
$packageMatch = [regex]::Match(
    $badging,
    "package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'"
)
Assert-PreviewAsset $packageMatch.Success 'unable to parse package metadata'
Assert-PreviewAsset ($packageMatch.Groups[1].Value -eq [string]$contract.bridgeApplicationId) 'applicationId differs from contract'
Assert-PreviewAsset ($packageMatch.Groups[2].Value -eq [string]$contract.versionCode) 'versionCode differs from contract'
Assert-PreviewAsset ($packageMatch.Groups[3].Value -ceq $expectedVersionName) "versionName is not $expectedVersionName"

$signerOutput = (Invoke-Checked $apksigner @('verify', '--verbose', '--print-certs', $resolvedApk)) -join "`n"
$certificateMatch = [regex]::Match(
    $signerOutput,
    'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]{64})'
)
Assert-PreviewAsset $certificateMatch.Success 'unable to read signer certificate'
$certificate = $certificateMatch.Groups[1].Value.ToLowerInvariant()
Assert-PreviewAsset ($certificate -eq ([string]$contract.releaseCertificateSha256).ToLowerInvariant()) 'APK is not signed by the frozen release certificate'

Invoke-Checked $zipalign @('-c', '-P', '16', '4', $resolvedApk) | Out-Null

$targetPath = Join-Path $resolvedOutputDir $assetName
Copy-Item -LiteralPath $resolvedApk -Destination $targetPath
$hash = (Get-FileHash -LiteralPath $targetPath -Algorithm SHA256).Hash.ToLowerInvariant()
[System.IO.File]::WriteAllText(
    (Join-Path $resolvedOutputDir ([string]$contract.checksumsAsset)),
    "$hash  $assetName`n",
    [System.Text.UTF8Encoding]::new($false)
)

$finalAssets = @(Get-ChildItem -LiteralPath $resolvedOutputDir -File)
Assert-PreviewAsset ($finalAssets.Count -eq 2) "expected 2 preview assets, found $($finalAssets.Count)"
Write-Output "Preview asset verified: $assetName, versionName=$expectedVersionName, versionCode=$($contract.versionCode), sha256=$hash."
