$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$targetRoot = (Resolve-Path -LiteralPath (Join-Path $projectRoot 'target')).Path
$shell = Get-Command sh -ErrorAction SilentlyContinue
if (-not $shell) {
    throw 'A POSIX sh implementation is required for the upgrade script test.'
}

$releaseArchives = @(
    Get-ChildItem -LiteralPath $targetRoot -File |
        Where-Object { $_.Name -match '^teddy-.+-release\.zip$' }
)
if ($releaseArchives.Count -ne 1) {
    throw "Expected one release ZIP in target, found $($releaseArchives.Count)."
}

function Convert-ToPosixPath([string] $Path) {
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if ($fullPath -notmatch '^([A-Za-z]):(.*)$') {
        throw "Expected a Windows drive path, got $fullPath"
    }
    $drive = $matches[1].ToLowerInvariant()
    $remainder = $matches[2].Replace('\', '/')
    return "/$drive$remainder"
}

function Write-LfFile([string] $Path, [string[]] $Lines) {
    $content = ($Lines -join "`n") + "`n"
    [System.IO.File]::WriteAllText($Path, $content, [System.Text.UTF8Encoding]::new($false))
}

function Assert-LinkTarget([string] $Link, [string] $ExpectedTarget) {
    $actualTarget = (& $shell.Source -c "readlink -f '$Link'").Trim()
    if ($LASTEXITCODE -ne 0 -or $actualTarget -ne $ExpectedTarget) {
        throw "Expected $Link to point to $ExpectedTarget, got $actualTarget"
    }
}

$testRoot = Join-Path $targetRoot ('upgrade-test-' + [Guid]::NewGuid().ToString('N'))
$serviceRoot = Join-Path $testRoot 'service'
$releasesRoot = Join-Path $serviceRoot 'releases'
$sharedRoot = Join-Path $serviceRoot 'shared'
$sharedConf = Join-Path $sharedRoot 'conf'
$stateRoot = Join-Path $serviceRoot 'state'
$fakeBin = Join-Path $serviceRoot 'fake-bin'

try {
    New-Item -ItemType Directory -Path $releasesRoot,$sharedConf,(Join-Path $sharedRoot 'logs'),(Join-Path $sharedRoot 'run'),$stateRoot,$fakeBin -Force | Out-Null
    Expand-Archive -LiteralPath $releaseArchives[0].FullName -DestinationPath $releasesRoot

    $firstRelease = Get-ChildItem -LiteralPath $releasesRoot -Directory | Select-Object -First 1
    $secondReleasePath = Join-Path $releasesRoot 'teddy-1.1.1-test'
    Copy-Item -LiteralPath $firstRelease.FullName -Destination $secondReleasePath -Recurse
    $secondRelease = Get-Item -LiteralPath $secondReleasePath

    Copy-Item -LiteralPath (Join-Path $firstRelease.FullName 'conf\teddy.properties.example') -Destination (Join-Path $sharedConf 'teddy.properties')
    Copy-Item -LiteralPath (Join-Path $firstRelease.FullName 'conf\application.properties.example') -Destination (Join-Path $sharedConf 'application.properties')

    $posixServiceRoot = Convert-ToPosixPath $serviceRoot
    $posixStateRoot = Convert-ToPosixPath $stateRoot
    $posixFakeBin = Convert-ToPosixPath $fakeBin
    $firstReleasePosix = Convert-ToPosixPath $firstRelease.FullName
    $secondReleasePosix = Convert-ToPosixPath $secondRelease.FullName

    $fakeStart = @(
        '#!/bin/sh',
        'release=$(basename "$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)")',
        'printf ''%s\n'' "$release" > "$TEST_STATE_DIR/running"'
    )
    $fakeStop = @(
        '#!/bin/sh',
        'rm -f "$TEST_STATE_DIR/running"'
    )
    foreach ($release in @($firstRelease, $secondRelease)) {
        Write-LfFile (Join-Path $release.FullName 'bin\start.sh') $fakeStart
        Write-LfFile (Join-Path $release.FullName 'bin\stop.sh') $fakeStop
    }

    Write-LfFile (Join-Path $fakeBin 'curl') @(
        '#!/bin/sh',
        'running=$(cat "$TEST_STATE_DIR/running" 2>/dev/null || true)',
        'if [ -n "${TEST_FAIL_RELEASE:-}" ] && [ "$running" = "$TEST_FAIL_RELEASE" ]; then',
        '    exit 22',
        'fi',
        '[ -n "$running" ]'
    )

    & $shell.Source -c "chmod +x '$firstReleasePosix/bin/'*.sh '$secondReleasePosix/bin/'*.sh '$posixFakeBin/curl'"
    if ($LASTEXITCODE -ne 0) {
        throw "chmod exited with code $LASTEXITCODE"
    }

    $environmentPath = Join-Path $sharedRoot 'teddy.env'
    $commonEnvironment = @(
        "PATH=$posixFakeBin`:`$PATH",
        "TEST_STATE_DIR=$posixStateRoot",
        "TEDDY_CONF_DIR=$posixServiceRoot/shared/conf",
        "TEDDY_LOG_DIR=$posixServiceRoot/shared/logs",
        "TEDDY_RUN_DIR=$posixServiceRoot/shared/run",
        'TEDDY_HEALTH_URL=http://127.0.0.1:18081/system/health',
        'TEDDY_HEALTH_TIMEOUT=1',
        'TEDDY_STOP_TIMEOUT=1'
    )

    Write-LfFile $environmentPath ($commonEnvironment + "TEST_FAIL_RELEASE=$($secondRelease.Name)")

    & $shell.Source "$firstReleasePosix/bin/upgrade.sh" --preflight
    if ($LASTEXITCODE -ne 0) {
        throw "Upgrade preflight exited with code $LASTEXITCODE"
    }

    $linkProbeTarget = Join-Path $serviceRoot 'link-probe-target'
    $linkProbe = Join-Path $serviceRoot 'link-probe'
    Write-LfFile $linkProbeTarget @('probe')
    $linkProbeTargetPosix = Convert-ToPosixPath $linkProbeTarget
    $linkProbePosix = Convert-ToPosixPath $linkProbe
    $probeCommand = "ln -s '$linkProbeTargetPosix' '$linkProbePosix' && [ -L '$linkProbePosix' ] && [ `"`$(readlink -f '$linkProbePosix')`" = '$linkProbeTargetPosix' ]"
    & $shell.Source -c $probeCommand
    $supportsNativeSymlinks = $LASTEXITCODE -eq 0
    & $shell.Source -c "rm -f '$linkProbePosix' '$linkProbeTargetPosix'"

    if (-not $supportsNativeSymlinks) {
        [PSCustomObject]@{
            Preflight = 'passed'
            InitialActivation = 'skipped: native symlinks unavailable'
            AutomaticRollback = 'skipped: native symlinks unavailable'
            ManualRollback = 'skipped: native symlinks unavailable'
        }
        return
    }

    & $shell.Source "$firstReleasePosix/bin/upgrade.sh"
    if ($LASTEXITCODE -ne 0) {
        throw "Initial activation exited with code $LASTEXITCODE"
    }
    Assert-LinkTarget "$posixServiceRoot/current" $firstReleasePosix

    & $shell.Source "$secondReleasePosix/bin/upgrade.sh"
    if ($LASTEXITCODE -eq 0) {
        throw 'The simulated unhealthy release unexpectedly succeeded.'
    }
    Assert-LinkTarget "$posixServiceRoot/current" $firstReleasePosix
    $runningAfterFailure = (Get-Content -LiteralPath (Join-Path $stateRoot 'running') -Raw).Trim()
    if ($runningAfterFailure -ne $firstRelease.Name) {
        throw "Automatic rollback restored $runningAfterFailure instead of $($firstRelease.Name)."
    }

    Write-LfFile $environmentPath $commonEnvironment
    & $shell.Source "$secondReleasePosix/bin/upgrade.sh"
    if ($LASTEXITCODE -ne 0) {
        throw "Healthy upgrade exited with code $LASTEXITCODE"
    }
    Assert-LinkTarget "$posixServiceRoot/current" $secondReleasePosix
    Assert-LinkTarget "$posixServiceRoot/previous" $firstReleasePosix

    & $shell.Source "$secondReleasePosix/bin/rollback.sh"
    if ($LASTEXITCODE -ne 0) {
        throw "Manual rollback exited with code $LASTEXITCODE"
    }
    Assert-LinkTarget "$posixServiceRoot/current" $firstReleasePosix
    Assert-LinkTarget "$posixServiceRoot/previous" $secondReleasePosix

    [PSCustomObject]@{
        Preflight = 'passed'
        InitialActivation = 'passed'
        AutomaticRollback = 'passed'
        ManualRollback = 'passed'
    }
} finally {
    if (Test-Path -LiteralPath $testRoot) {
        $resolvedTestRoot = (Resolve-Path -LiteralPath $testRoot).Path
        $expectedPrefix = $targetRoot.TrimEnd('\') + '\'
        if (-not $resolvedTestRoot.StartsWith($expectedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to remove unexpected path: $resolvedTestRoot"
        }
        Remove-Item -LiteralPath $resolvedTestRoot -Recurse -Force
    }
}
