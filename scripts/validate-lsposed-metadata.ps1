param(
    [string] $RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'

$metadataDir = Join-Path $RepoRoot '.github\lsposed'
$repositoryScopePath = Join-Path $metadataDir 'SCOPE'
$apkScopePath = Join-Path $RepoRoot 'app\src\main\resources\META-INF\xposed\scope.list'

foreach ($name in @('README.md', 'SUMMARY', 'SOURCE_URL', 'SCOPE')) {
    $path = Join-Path $metadataDir $name
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing LSPosed metadata file: $path"
    }
    if ([string]::IsNullOrWhiteSpace((Get-Content -LiteralPath $path -Raw))) {
        throw "LSPosed metadata file is empty: $path"
    }
}

try {
    $parsedScope = Get-Content -LiteralPath $repositoryScopePath -Raw |
        ConvertFrom-Json
} catch {
    throw "LSPosed SCOPE must be a valid JSON array: $($_.Exception.Message)"
}

if ($parsedScope -isnot [System.Array]) {
    throw 'LSPosed SCOPE must be a JSON array.'
}
$repositoryScope = @($parsedScope)

if ($repositoryScope.Count -eq 0) {
    throw 'LSPosed SCOPE must contain at least one package.'
}
if (@($repositoryScope | Where-Object { $_ -isnot [string] -or [string]::IsNullOrWhiteSpace($_) }).Count -gt 0) {
    throw 'Every LSPosed SCOPE entry must be a non-empty string.'
}
if (@($repositoryScope | Select-Object -Unique).Count -ne $repositoryScope.Count) {
    throw 'LSPosed SCOPE contains duplicate entries.'
}

$apkScope = @(
    Get-Content -LiteralPath $apkScopePath |
        ForEach-Object { $_.Trim() } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
)

if ($repositoryScope.Count -ne $apkScope.Count) {
    throw "LSPosed SCOPE count differs from APK scope.list."
}
for ($index = 0; $index -lt $apkScope.Count; $index++) {
    if ($repositoryScope[$index] -ne $apkScope[$index]) {
        throw "LSPosed SCOPE differs from APK scope.list at index ${index}: '$($repositoryScope[$index])' != '$($apkScope[$index])'."
    }
}

$sourceUrl = (Get-Content -LiteralPath (Join-Path $metadataDir 'SOURCE_URL') -Raw).Trim()
if ($sourceUrl -notmatch '^https://github\.com/[^/]+/[^/]+/?$') {
    throw "SOURCE_URL must be a GitHub repository URL: $sourceUrl"
}

Write-Output "LSPosed metadata is valid. Scope: $($repositoryScope -join ', ')"
