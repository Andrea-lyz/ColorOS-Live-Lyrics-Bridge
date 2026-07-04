param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArgs = @()
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$sourceRepoRoot = Split-Path -Parent $scriptDir
$repoRoot = $sourceRepoRoot
$workspaceRoot = Split-Path -Parent $sourceRepoRoot
$androidSdkDirForBuild = ''

function Test-JavaHome21 {
    param([string] $Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $false
    }
    $javaExe = Join-Path $Path 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $javaExe -PathType Leaf)) {
        return $false
    }

    $releaseFile = Join-Path $Path 'release'
    if (Test-Path -LiteralPath $releaseFile -PathType Leaf) {
        $releaseText = Get-Content -LiteralPath $releaseFile -Raw
        return $releaseText -match 'JAVA_VERSION="21(?:\.|")'
    }

    $versionText = & $javaExe -version 2>&1 | Out-String
    return $versionText -match 'version "21(?:\.|")'
}

function Add-JavaHomeCandidate {
    param(
        [System.Collections.Generic.List[string]] $Candidates,
        [string] $Path
    )

    if ([string]::IsNullOrWhiteSpace($Path) -or
            -not (Test-Path -LiteralPath $Path -PathType Container)) {
        return
    }

    if (Test-Path -LiteralPath (Join-Path $Path 'bin\java.exe') -PathType Leaf) {
        [void] $Candidates.Add($Path)
    }

    Get-ChildItem -LiteralPath $Path -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '(?:jdk|temurin|java).*(?:21)' -or $_.Name -match '21(?:\.|$)' } |
        Sort-Object Name -Descending |
        ForEach-Object { [void] $Candidates.Add($_.FullName) }
}

function Resolve-JavaHome21 {
    $candidates = [System.Collections.Generic.List[string]]::new()
    Add-JavaHomeCandidate $candidates $env:SALT_LYRIC_JAVA_HOME
    Add-JavaHomeCandidate $candidates (Join-Path $HOME '.jdks\temurin-21')
    Add-JavaHomeCandidate $candidates (Join-Path $env:USERPROFILE '.jdks\temurin-21')
    Add-JavaHomeCandidate $candidates (Join-Path $env:ProgramFiles 'Eclipse Adoptium')
    Add-JavaHomeCandidate $candidates (Join-Path $env:ProgramFiles 'Java')
    Add-JavaHomeCandidate $candidates $env:JAVA_HOME

    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (Test-JavaHome21 $candidate) {
            return $candidate
        }
    }
    return $null
}

function Test-FileReadable {
    param([string] $Path)

    if ([string]::IsNullOrWhiteSpace($Path) -or
            -not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $false
    }
    try {
        $stream = [System.IO.File]::Open(
            $Path,
            [System.IO.FileMode]::Open,
            [System.IO.FileAccess]::Read,
            [System.IO.FileShare]::ReadWrite)
        $stream.Close()
        return $true
    } catch {
        return $false
    }
}

function Test-FileReadWrite {
    param([string] $Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $false
    }
    try {
        $mode = if (Test-Path -LiteralPath $Path -PathType Leaf) {
            [System.IO.FileMode]::Open
        } else {
            [System.IO.FileMode]::OpenOrCreate
        }
        $stream = [System.IO.File]::Open(
            $Path,
            $mode,
            [System.IO.FileAccess]::ReadWrite,
            [System.IO.FileShare]::ReadWrite)
        $stream.Close()
        return $true
    } catch {
        return $false
    }
}

function Test-GradleUserHomeUsable {
    param([string] $Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $false
    }
    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        return $true
    }

    $probeDir = Join-Path $Path '.tmp'
    New-Item -ItemType Directory -Force -Path $probeDir | Out-Null
    $probeFile = Join-Path $probeDir 'write-probe.lock'
    if (-not (Test-FileReadWrite $probeFile)) {
        return $false
    }
    Remove-Item -LiteralPath $probeFile -Force -ErrorAction SilentlyContinue

    $knownLockFiles = @()
    $knownLockFiles += Get-ChildItem -LiteralPath (Join-Path $Path 'wrapper') `
        -Recurse -Filter '*.lck' -File -ErrorAction SilentlyContinue |
        Select-Object -First 8 -ExpandProperty FullName
    $knownLockFiles += Get-ChildItem -LiteralPath (Join-Path $Path 'native') `
        -Recurse -Filter '*.lock' -File -ErrorAction SilentlyContinue |
        Select-Object -First 8 -ExpandProperty FullName

    foreach ($lockFile in $knownLockFiles) {
        if (-not (Test-FileReadWrite $lockFile)) {
            return $false
        }
    }
    return $true
}

function Copy-GradleCacheIfMissing {
    param(
        [string] $Source,
        [string] $Destination
    )

    if (-not (Test-Path -LiteralPath $Source -PathType Container)) {
        return
    }
    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    & robocopy $Source $Destination /E /XF *.lock *.lck /XD daemon workers .tmp `
        /R:1 /W:1 /NFL /NDL /NJH /NJS /NP | Out-Null
    $exitCode = $LASTEXITCODE
    if ($exitCode -gt 7) {
        throw "Failed to copy Gradle cache from $Source to $Destination, robocopy exit $exitCode"
    }
}

function Initialize-GradleUserHome {
    param(
        [string] $DefaultPath,
        [string] $SeedPath
    )

    if ((Test-Path -LiteralPath $DefaultPath -PathType Container) -and
            (Test-GradleUserHomeUsable $DefaultPath)) {
        return $DefaultPath
    }

    $fallback = Join-Path $env:TEMP 'salt-lyric-gradle-user-home'
    New-Item -ItemType Directory -Force -Path $fallback | Out-Null
    $copySource = if (Test-Path -LiteralPath $SeedPath -PathType Container) {
        $SeedPath
    } else {
        $DefaultPath
    }
    Copy-GradleCacheIfMissing (Join-Path $copySource 'wrapper') (Join-Path $fallback 'wrapper')
    Copy-GradleCacheIfMissing (Join-Path $copySource 'caches') (Join-Path $fallback 'caches')
    Write-Host "Using writable Gradle user home: $fallback"
    return $fallback
}

function Get-CompileSdkVersion {
    $buildFile = Join-Path $repoRoot 'app\build.gradle.kts'
    if (Test-Path -LiteralPath $buildFile -PathType Leaf) {
        $content = Get-Content -LiteralPath $buildFile -Raw
        if ($content -match 'compileSdk\s*=\s*(\d+)') {
            return $Matches[1]
        }
    }
    return '35'
}

function Unescape-LocalPropertiesPath {
    param([string] $Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return ''
    }
    return $Value.Trim().Replace('\:', ':').Replace('/', '\')
}

function Escape-LocalPropertiesPath {
    param([string] $Value)

    return $Value.Replace('\', '/').Replace(':', '\:')
}

function Set-LocalPropertiesSdkDir {
    param(
        [string] $LocalPropertiesPath,
        [string] $SdkDir
    )

    try {
        Set-Content -LiteralPath $LocalPropertiesPath `
            -Value ("sdk.dir=" + (Escape-LocalPropertiesPath $SdkDir)) `
            -NoNewline
        return $true
    } catch {
        return $false
    }
}

function Initialize-ProjectOverlay {
    $overlay = Join-Path $env:TEMP 'salt-lyric-project-overlay'
    $resolvedTemp = [System.IO.Path]::GetFullPath($env:TEMP)
    $resolvedOverlay = [System.IO.Path]::GetFullPath($overlay)
    if (-not $resolvedOverlay.StartsWith($resolvedTemp, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to mirror project outside temp: $resolvedOverlay"
    }

    New-Item -ItemType Directory -Force -Path $overlay | Out-Null
    & robocopy $sourceRepoRoot $overlay /MIR `
        /XD .git .gradle .gradle-local-build .gradle-user-home .codex-backups backups `
            _anchors _debug_frames build `
        /XF local.properties *.apk logcat.log `
        /R:1 /W:1 /NFL /NDL /NJH /NJS /NP | Out-Null
    $exitCode = $LASTEXITCODE
    if ($exitCode -gt 7) {
        throw "Failed to mirror project to $overlay, robocopy exit $exitCode"
    }
    Write-Host "Using writable project overlay: $overlay"
    return $overlay
}

function Sync-AppBuildOutputsFromOverlay {
    param(
        [string] $OverlayRoot,
        [string] $DestinationRoot
    )

    if ([string]::IsNullOrWhiteSpace($OverlayRoot) -or
            [string]::IsNullOrWhiteSpace($DestinationRoot) -or
            [System.IO.Path]::GetFullPath($OverlayRoot).Equals(
                [System.IO.Path]::GetFullPath($DestinationRoot),
                [System.StringComparison]::OrdinalIgnoreCase)) {
        return
    }

    $sourceOutputs = Join-Path $OverlayRoot 'app\build\outputs'
    if (-not (Test-Path -LiteralPath $sourceOutputs -PathType Container)) {
        return
    }

    $destinationOutputs = Join-Path $DestinationRoot 'app\build\outputs'
    $resolvedDestinationRoot = [System.IO.Path]::GetFullPath($DestinationRoot)
    $resolvedDestinationOutputs = [System.IO.Path]::GetFullPath($destinationOutputs)
    if (-not $resolvedDestinationOutputs.StartsWith(
            $resolvedDestinationRoot,
            [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to sync build outputs outside repository: $resolvedDestinationOutputs"
    }

    New-Item -ItemType Directory -Force -Path $destinationOutputs | Out-Null
    Get-ChildItem -LiteralPath $sourceOutputs -Force | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $destinationOutputs -Recurse -Force
    }
    Write-Host "Synced app build outputs to: $destinationOutputs"
}

function Get-LocalSdkDir {
    param([string] $LocalPropertiesPath)

    if (-not (Test-Path -LiteralPath $LocalPropertiesPath -PathType Leaf)) {
        return ''
    }
    foreach ($line in Get-Content -LiteralPath $LocalPropertiesPath) {
        if ($line -match '^\s*sdk\.dir\s*=\s*(.+?)\s*$') {
            return Unescape-LocalPropertiesPath $Matches[1]
        }
    }
    return ''
}

function Get-FreeAsciiDrive {
    param([string[]] $Exclude = @())

    $driveCandidates = @('X:', 'Y:', 'Z:', 'W:', 'V:', 'U:')
    foreach ($candidate in $driveCandidates) {
        if ($Exclude -contains $candidate) {
            continue
        }
        if (-not (Test-Path "${candidate}\")) {
            return $candidate
        }
    }
    return $null
}

function Use-ReadableAndroidSdkForBuild {
    $localPropertiesPath = Join-Path $repoRoot 'local.properties'
    $compileSdk = Get-CompileSdkVersion
    $currentSdk = Get-LocalSdkDir $localPropertiesPath
    $currentJar = if ([string]::IsNullOrWhiteSpace($currentSdk)) {
        ''
    } else {
        Join-Path $currentSdk "platforms\android-$compileSdk\android.jar"
    }

    $workspaceSdk = if ([string]::IsNullOrWhiteSpace($script:androidSdkDirForBuild)) {
        Join-Path $workspaceRoot 'android-sdk'
    } else {
        $script:androidSdkDirForBuild
    }
    $workspaceJar = Join-Path $workspaceSdk "platforms\android-$compileSdk\android.jar"
    if ((Test-FileReadable $workspaceJar) -and
            [string]::IsNullOrWhiteSpace($env:SALT_LYRIC_USE_CONFIGURED_SDK)) {
        $existed = Test-Path -LiteralPath $localPropertiesPath -PathType Leaf
        $originalContent = if ($existed) {
            Get-Content -LiteralPath $localPropertiesPath -Raw
        } else {
            ''
        }

        if (-not (Set-LocalPropertiesSdkDir $localPropertiesPath $workspaceSdk)) {
            $script:repoRoot = Initialize-ProjectOverlay
            $localPropertiesPath = Join-Path $script:repoRoot 'local.properties'
            Set-LocalPropertiesSdkDir $localPropertiesPath $workspaceSdk | Out-Null
            return @{
                Path = $localPropertiesPath
                Restore = $false
                Existed = $true
                Content = ''
            }
        }
        Write-Host "Using workspace Android SDK for this build: $workspaceSdk"
        return @{
            Path = $localPropertiesPath
            Restore = $true
            Existed = $existed
            Content = $originalContent
        }
    }

    if (Test-FileReadable $currentJar) {
        return @{
            Path = $localPropertiesPath
            Restore = $false
            Existed = Test-Path -LiteralPath $localPropertiesPath -PathType Leaf
            Content = ''
        }
    }

    if (-not (Test-FileReadable $workspaceJar)) {
        return @{
            Path = $localPropertiesPath
            Restore = $false
            Existed = Test-Path -LiteralPath $localPropertiesPath -PathType Leaf
            Content = ''
        }
    }

    $existed = Test-Path -LiteralPath $localPropertiesPath -PathType Leaf
    $originalContent = if ($existed) {
        Get-Content -LiteralPath $localPropertiesPath -Raw
    } else {
        ''
    }

    if (-not (Set-LocalPropertiesSdkDir $localPropertiesPath $workspaceSdk)) {
        $script:repoRoot = Initialize-ProjectOverlay
        $localPropertiesPath = Join-Path $script:repoRoot 'local.properties'
        Set-LocalPropertiesSdkDir $localPropertiesPath $workspaceSdk | Out-Null
        return @{
            Path = $localPropertiesPath
            Restore = $false
            Existed = $true
            Content = ''
        }
    }
    Write-Host "Using workspace Android SDK for this build: $workspaceSdk"
    return @{
        Path = $localPropertiesPath
        Restore = $true
        Existed = $existed
        Content = $originalContent
    }
}

$javaHome = Resolve-JavaHome21
if ([string]::IsNullOrWhiteSpace($javaHome)) {
    throw 'JDK 21 was not found. Set SALT_LYRIC_JAVA_HOME to a JDK 21 installation.'
}
$env:JAVA_HOME = $javaHome
$env:PATH = (Join-Path $javaHome 'bin') + [System.IO.Path]::PathSeparator + $env:PATH

$candidates = @()
if (-not [string]::IsNullOrWhiteSpace($env:SALT_LYRIC_ASCII_DRIVE)) {
    $requestedDrive = $env:SALT_LYRIC_ASCII_DRIVE.Trim().TrimEnd('\')
    if ($requestedDrive.Length -eq 1) {
        $requestedDrive = "${requestedDrive}:"
    }
    $candidates += $requestedDrive.ToUpperInvariant()
}
$candidates += @('X:', 'Y:', 'Z:', 'W:', 'V:')

$drive = $null
foreach ($candidate in $candidates) {
    if ($candidate -notmatch '^[A-Z]:$') {
        continue
    }
    if (-not (Test-Path "${candidate}\")) {
        $drive = $candidate
        break
    }
}

if ($null -eq $drive) {
    throw 'No free drive letter is available for the temporary ASCII Gradle path.'
}

$driveRoot = "${drive}\"
$mapped = $false
$sdkDrive = $null
$sdkMapped = $false
$pushed = $false
$exitCode = 0
$sdkOverride = $null

try {
    $physicalWorkspaceSdk = Join-Path $workspaceRoot 'android-sdk'
    $compileSdk = Get-CompileSdkVersion
    $physicalWorkspaceJar = Join-Path $physicalWorkspaceSdk "platforms\android-$compileSdk\android.jar"
    if (Test-FileReadable $physicalWorkspaceJar) {
        $sdkDrive = Get-FreeAsciiDrive @($drive)
        if ($null -ne $sdkDrive) {
            & subst $sdkDrive $physicalWorkspaceSdk
            $sdkMapped = $true
            $script:androidSdkDirForBuild = "${sdkDrive}\"
        }
    }

    $sdkOverride = Use-ReadableAndroidSdkForBuild

    & subst $drive $repoRoot
    $mapped = $true

    if (-not (Test-Path $driveRoot)) {
        throw "Failed to create temporary ASCII Gradle path at $driveRoot"
    }

    Remove-Item Env:SALT_LYRIC_BUILD_DIR -ErrorAction SilentlyContinue
    $env:GRADLE_USER_HOME = Initialize-GradleUserHome `
        (Join-Path $driveRoot '.gradle-user-home') `
        (Join-Path $sourceRepoRoot '.gradle-user-home')

    if (($GradleArgs -notcontains '--daemon') -and ($GradleArgs -notcontains '--no-daemon')) {
        $GradleArgs += '--no-daemon'
    }
    if ($GradleArgs -notcontains '--project-cache-dir') {
        $projectCacheDir = Join-Path $driveRoot '.gradle\project-cache'
        New-Item -ItemType Directory -Force -Path $projectCacheDir | Out-Null
        $GradleArgs += @('--project-cache-dir', $projectCacheDir)
    }

    Push-Location $driveRoot
    $pushed = $true
    & .\gradlew.bat @GradleArgs
    $exitCode = $LASTEXITCODE
    if ($exitCode -eq 0) {
        Sync-AppBuildOutputsFromOverlay $repoRoot $sourceRepoRoot
    }
} finally {
    if ($pushed) {
        Pop-Location
    }
    if ($mapped) {
        & subst $drive /d
    }
    if ($sdkMapped) {
        & subst $sdkDrive /d
    }
    if ($sdkOverride -ne $null -and $sdkOverride.Restore) {
        if ($sdkOverride.Existed) {
            Set-Content -LiteralPath $sdkOverride.Path -Value $sdkOverride.Content -NoNewline
        } else {
            Remove-Item -LiteralPath $sdkOverride.Path -Force -ErrorAction SilentlyContinue
        }
    }
}

exit $exitCode
