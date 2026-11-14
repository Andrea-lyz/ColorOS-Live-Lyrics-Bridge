param(
    [string] $RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [string] $ProviderRepoRoot = '',
    # Legacy preview path (preview.yml / artwork-provider-candidate.yml) still attaches the
    # pinned Universal and artwork preview APKs and checks their versions against that pinned
    # Provider checkout. Formal releases no longer carry Provider APKs (schema 2, plan §9.1).
    [switch] $PreviewProviders
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

Assert-Contract ($contract.schema -eq 2) 'unsupported bridge contract schema'
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

# Schema 2: Provider and artwork APKs are published by the Provider store
# (ColorOS-Live-Lyrics-Providers provider-store-release.yml), one tag per Provider.
Assert-Contract ($contract.providerDistribution -eq 'provider-store') 'Provider APKs must be distributed by the Provider store'
foreach ($retired in @('providerBundleAsset', 'providerApkCount', 'artworkProviderApkCount')) {
    Assert-Contract ($null -eq $contract.PSObject.Properties[$retired]) "retired suite field must not return: $retired"
}
Assert-Contract ($contract.totalApkCount -eq 1) 'Bridge release must contain only the Bridge APK'
Assert-Contract ($contract.totalReleaseAssetCount -eq ($contract.totalApkCount + 2)) 'asset count must be the Bridge APK, checksums, and manifest'

$previewArtwork = $contract.previewArtworkProvider
Assert-Contract ($null -ne $previewArtwork) 'preview artwork Provider section is missing'
$artworkRepository = [string]$previewArtwork.repository
Assert-Contract ($artworkRepository -match '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') 'preview artwork repository must be owner/name'
Assert-Contract ([string]$previewArtwork.ref -match '^[0-9a-f]{40}$') 'preview artwork Provider ref must be a pinned commit SHA'
Assert-Contract ([string]$previewArtwork.applicationId -eq 'io.github.andrealtb.artwork.am') 'preview artwork applicationId differs'
Assert-Contract ([string]$previewArtwork.versionName -match '^\d+\.\d+\.\d+$') 'preview artwork versionName must be SemVer'
Assert-Contract ([int]$previewArtwork.versionCode -ge 1) 'preview artwork versionCode must be positive'
Assert-Contract ([string]$previewArtwork.asset -eq "ColorOS-Live-Lyrics-Dynamic-Artwork-Provider-v$($previewArtwork.versionName).apk") 'preview artwork asset name differs'
Assert-Contract (-not [string]::IsNullOrWhiteSpace([string]$previewArtwork.module)) 'preview artwork module is missing'
Assert-Contract (-not [string]::IsNullOrWhiteSpace([string]$previewArtwork.buildTask)) 'preview artwork build task is missing'
Assert-Contract (-not [string]::IsNullOrWhiteSpace([string]$previewArtwork.mirrorTask)) 'preview artwork mirror task is missing'

$previewUniversal = $contract.previewUniversalProvider
if ($null -ne $previewUniversal) {
    Assert-Contract ($previewUniversal.repository -eq $previewArtwork.repository -and $previewUniversal.ref -eq $previewArtwork.ref) 'preview Providers must share a pinned repository/commit'
    Assert-Contract ($previewUniversal.module -eq 'universal-provider') 'preview Universal module differs'
    Assert-Contract ($previewUniversal.buildTask -eq ':universal-provider:assembleRelease') 'preview Universal build task differs'
    Assert-Contract ($previewUniversal.testTask -eq ':universal-provider:testDebugUnitTest') 'preview Universal test task differs'
    Assert-Contract ($previewUniversal.applicationId -eq 'io.github.andrealtb.coloroslyrics.provider.universal') 'preview Universal applicationId differs'
    Assert-Contract ($previewUniversal.versionName -match '^\d+\.\d+\.\d+$' -and [int]$previewUniversal.versionCode -gt 0) 'preview Universal version is invalid'
    Assert-Contract ($previewUniversal.asset -eq "ColorOS-Live-Lyrics-Provider-Universal-v$($previewUniversal.versionName).apk") 'preview Universal asset name differs'
    Assert-Contract ((@($previewUniversal.scopes) -join ',') -eq 'system') 'preview Universal scope differs'
    Assert-Contract (@($previewUniversal.previewTags).Count -gt 0) 'preview Universal tag list is empty'
    foreach ($previewTag in @($previewUniversal.previewTags)) {
        Assert-Contract ($previewTag -cmatch ('^' + [regex]::Escape($contract.releaseTag) + '-[A-Za-z0-9-]+-Artwork-Preview[0-9]*$')) 'Universal preview tag must be an artwork preview'
    }
}

$forbiddenApkAscii = @($contract.forbiddenApkAscii)
Assert-Contract ($forbiddenApkAscii.Count -ge 20) 'forbidden APK string set is incomplete'
Assert-Contract (($forbiddenApkAscii | Select-Object -Unique).Count -eq $forbiddenApkAscii.Count) 'forbidden APK strings contain duplicates'
Assert-Contract ($contract.bridgeAsset -eq "ColorOS-Live-Lyrics-Bridge-v$($contract.suiteVersion).apk") 'Bridge asset name differs from suite version'

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
    Assert-Contract ($providerContract.sourceTag -eq $contract.providersSourceTag) 'Provider source tag differs'

    # Identity only: Provider versions are released independently and no longer lock-step
    # with the Bridge version (plan §9.1).
    $providerArtwork = @($providerContract.artworkProviders)
    Assert-Contract ($providerArtwork.Count -eq 1) 'Provider matrix must declare exactly one artwork Provider'
    Assert-Contract ([string]$providerArtwork[0].module -eq [string]$previewArtwork.module) 'artwork Provider module differs between contracts'
    Assert-Contract ([string]$providerArtwork[0].applicationId -eq [string]$previewArtwork.applicationId) 'artwork Provider applicationId differs between contracts'

    if ($PreviewProviders) {
        Assert-Contract ([string]$providerArtwork[0].versionName -eq [string]$previewArtwork.versionName) 'artwork Provider versionName differs between contracts'
        Assert-Contract ([int]$providerArtwork[0].versionCode -eq [int]$previewArtwork.versionCode) 'artwork Provider versionCode differs between contracts'
        Assert-Contract ([string]$providerArtwork[0].asset -eq [string]$previewArtwork.asset) 'artwork Provider asset differs between contracts'
        if ($null -ne $previewUniversal) {
            $providerUniversal = @($providerContract.providers | Where-Object { $_.module -eq $previewUniversal.module })
            Assert-Contract ($providerUniversal.Count -eq 1) 'Provider matrix must contain exactly one Universal module'
            foreach ($property in @('applicationId', 'versionName', 'versionCode')) {
                Assert-Contract ([string]$providerUniversal[0].$property -ceq [string]$previewUniversal.$property) "Universal $property differs between contracts"
            }
            Assert-Contract ((@($providerUniversal[0].scopes) -join ',') -ceq (@($previewUniversal.scopes) -join ',')) 'Universal scope differs between contracts'
        }
    } else {
        Assert-Contract ([string]$providerArtwork[0].distribution -eq 'provider-store') 'artwork Provider must be distributed by the Provider store'
    }
}

Write-Output "Bridge release contract is valid: $($contract.releaseTag), versionCode=$($contract.versionCode), assets=$($contract.totalReleaseAssetCount), providers=$($contract.providerDistribution)."
