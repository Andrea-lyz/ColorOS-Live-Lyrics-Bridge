param(
    [string] $RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [string] $ProviderRepoRoot = ''
)

$ErrorActionPreference = 'Stop'

function Assert-Contract {
    param(
        [bool] $Condition,
        [string] $Message
    )
    if (-not $Condition) {
        throw "Release contract violation: $Message"
    }
}

$contractPath = Join-Path $RepoRoot 'release\bridge-release-contract.json'
$buildFilePath = Join-Path $RepoRoot 'app\build.gradle.kts'
$scopePath = Join-Path $RepoRoot 'app\src\main\resources\META-INF\xposed\scope.list'

$contract = Get-Content -LiteralPath $contractPath -Raw | ConvertFrom-Json
$buildFile = Get-Content -LiteralPath $buildFilePath -Raw
$scope = @(
    Get-Content -LiteralPath $scopePath |
        ForEach-Object { $_.Trim() } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
)

Assert-Contract ($contract.schema -eq 1) 'unsupported bridge contract schema'
Assert-Contract ($contract.suiteVersion -match '^\d+\.\d+\.\d+$') 'suiteVersion must be SemVer'
Assert-Contract ($contract.releaseTag -eq "v$($contract.suiteVersion)") 'releaseTag must match suiteVersion'
Assert-Contract ($contract.lspTag -eq "$($contract.versionCode)-$($contract.suiteVersion)") 'lspTag must match versionCode and suiteVersion'
Assert-Contract ($buildFile -match ('val defaultVersionName = "' + [regex]::Escape($contract.suiteVersion) + '"')) 'defaultVersionName differs from contract'
Assert-Contract ($buildFile -match ('versionCode = ' + [regex]::Escape([string]$contract.versionCode) + '(\D|$)')) 'versionCode differs from contract'
Assert-Contract ($buildFile -match ('applicationId = "' + [regex]::Escape($contract.bridgeApplicationId) + '"')) 'Bridge applicationId differs from contract'
Assert-Contract ($contract.releaseCertificateSha256 -match '^[0-9a-f]{64}$') 'release certificate SHA-256 must be lowercase hex'
Assert-Contract ($contract.androidBuildToolsVersion -eq '36.0.0') 'Android build-tools version must stay pinned to the locally verified release parser'

$expectedScope = @($contract.bridgeScopes)
Assert-Contract ($scope.Count -eq $expectedScope.Count) 'Bridge scope count differs from contract'
for ($index = 0; $index -lt $expectedScope.Count; $index++) {
    Assert-Contract ($scope[$index] -eq $expectedScope[$index]) "Bridge scope differs at index $index"
}

Assert-Contract ($contract.providerApkCount -eq 12) 'release must contain exactly 12 Provider APKs'
Assert-Contract ($contract.totalApkCount -eq (1 + $contract.providerApkCount)) 'totalApkCount must equal Bridge plus Providers'
Assert-Contract ($contract.totalReleaseAssetCount -eq ($contract.totalApkCount + 3)) 'asset count must include APKs, bundle, checksums, and manifest'
$forbiddenApkAscii = @($contract.forbiddenApkAscii)
Assert-Contract ($forbiddenApkAscii.Count -ge 20) 'forbidden APK string set is incomplete'
Assert-Contract (($forbiddenApkAscii | Select-Object -Unique).Count -eq $forbiddenApkAscii.Count) 'forbidden APK strings contain duplicates'
Assert-Contract ($contract.bridgeAsset -eq "ColorOS-Live-Lyrics-Bridge-v$($contract.suiteVersion).apk") 'Bridge asset name differs from suite version'
Assert-Contract ($contract.providerBundleAsset -eq "ColorOS-Live-Lyrics-Providers-v$($contract.suiteVersion).zip") 'Provider bundle name differs from suite version'

$requiredDocumentation = @(
    'README.md',
    'README.zh-CN.md',
    'docs\PLAYER_INTEGRATION.md',
    'docs\PLAYER_INTEGRATION.zh-CN.md',
    'docs\4.0\MIGRATION-3.8-TO-4.0.md',
    'docs\4.0\MIGRATION-3.8-TO-4.0.zh-CN.md',
    'docs\RELEASE_PROCESS.md',
    ".github\release-notes\$($contract.suiteVersion).md",
    "docs\releases\v$($contract.suiteVersion).md"
)
foreach ($relativePath in $requiredDocumentation) {
    $documentationPath = Join-Path $RepoRoot $relativePath
    Assert-Contract (Test-Path -LiteralPath $documentationPath -PathType Leaf) "required documentation is missing: $relativePath"
    Assert-Contract (-not [string]::IsNullOrWhiteSpace((Get-Content -LiteralPath $documentationPath -Raw))) "required documentation is empty: $relativePath"
}

$publicReleaseDocuments = @(
    'README.md',
    'README.zh-CN.md',
    ".github\release-notes\$($contract.suiteVersion).md",
    'docs\4.0\MIGRATION-3.8-TO-4.0.md',
    'docs\4.0\MIGRATION-3.8-TO-4.0.zh-CN.md'
)
foreach ($relativePath in $publicReleaseDocuments) {
    $content = Get-Content -LiteralPath (Join-Path $RepoRoot $relativePath) -Raw
    Assert-Contract ($content -notmatch '(?i)npatch|non-root') "public release document contains an internal abandoned-route term: $relativePath"
}

if (-not [string]::IsNullOrWhiteSpace($ProviderRepoRoot)) {
    $providerScript = Join-Path $ProviderRepoRoot 'scripts\validate-v5-release-contract.ps1'
    $providerContractPath = Join-Path $ProviderRepoRoot $contract.providerContractPath
    Assert-Contract (Test-Path -LiteralPath $providerScript -PathType Leaf) 'Provider validation script is missing'
    Assert-Contract (Test-Path -LiteralPath $providerContractPath -PathType Leaf) 'Provider contract is missing'

    & $providerScript -RepoRoot $ProviderRepoRoot

    $providerContract = Get-Content -LiteralPath $providerContractPath -Raw | ConvertFrom-Json
    Assert-Contract ($providerContract.suiteVersion -eq $contract.suiteVersion) 'Bridge and Provider suite versions differ'
    Assert-Contract ($providerContract.sourceTag -eq $contract.providersSourceTag) 'Provider source tag differs'
    Assert-Contract (@($providerContract.providers).Count -eq $contract.providerApkCount) 'Provider count differs'
    Assert-Contract ($providerContract.bundleAsset -eq $contract.providerBundleAsset) 'Provider bundle asset differs'
}

Write-Output "Bridge release contract is valid: $($contract.releaseTag), versionCode=$($contract.versionCode), providers=$($contract.providerApkCount)."
