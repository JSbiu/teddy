$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$targetRoot = (Resolve-Path -LiteralPath (Join-Path $projectRoot 'target')).Path
$shell = Get-Command sh -ErrorAction SilentlyContinue
if (-not $shell) {
    throw 'A POSIX sh implementation is required for the acceptance script test.'
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
    [System.IO.File]::WriteAllText(
        $Path, $content, [System.Text.UTF8Encoding]::new($false))
}

$testRoot = Join-Path $targetRoot ('acceptance-test-' + [Guid]::NewGuid().ToString('N'))
$serviceRoot = Join-Path $testRoot 'service'
$releaseRoot = Join-Path $serviceRoot 'releases\teddy-test'
$releaseBin = Join-Path $releaseRoot 'bin'
$sharedRoot = Join-Path $serviceRoot 'shared'
$sharedConf = Join-Path $sharedRoot 'conf'
$sharedRun = Join-Path $sharedRoot 'run'
$fakeBin = Join-Path $testRoot 'fake-bin'
$stateRoot = Join-Path $testRoot 'state'
$testPid = $null

try {
    New-Item -ItemType Directory -Path $releaseBin,$sharedConf,$sharedRun,$fakeBin,$stateRoot -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $projectRoot 'bin\acceptance.sh') -Destination (Join-Path $releaseBin 'acceptance.sh')

    $posixServiceRoot = Convert-ToPosixPath $serviceRoot
    $posixReleaseRoot = Convert-ToPosixPath $releaseRoot
    $posixFakeBin = Convert-ToPosixPath $fakeBin
    $posixStateRoot = Convert-ToPosixPath $stateRoot
    $posixSharedConf = Convert-ToPosixPath $sharedConf
    $posixSharedRun = Convert-ToPosixPath $sharedRun

    $jobsFile = Join-Path $stateRoot 'jobs.json'
    $passwordFile = Join-Path $stateRoot 'password.txt'
    [System.IO.File]::WriteAllText(
        $passwordFile, 'test-password', [System.Text.UTF8Encoding]::new($false))
    Write-LfFile (Join-Path $sharedConf 'teddy.properties') @(
        'auth.username=test-user',
        'yarn.command=yarn'
    )

    Write-LfFile (Join-Path $fakeBin 'curl') @(
        '#!/bin/sh',
        'url=',
        'for arg in "$@"; do',
        '    case "$arg" in',
        '        http://*|https://*) url=$arg ;;',
        '    esac',
        'done',
        'case "$url" in',
        '    */teddy/login) printf ''%s\n'' ''{"state":"success","data":"success"}'' ;;',
        '    */teddy/logout) printf ''%s\n'' ''{"state":"success","data":"success"}'' ;;',
        '    */system/health) printf ''%s\n'' ''{"status":"UP","database":"UP"}'' ;;',
        '    */job/list*) cat "$TEST_JOBS_FILE" ;;',
        '    *) exit 22 ;;',
        'esac'
    )
    Write-LfFile (Join-Path $fakeBin 'yarn') @(
        '#!/bin/sh',
        'printf ''Application-Id : %s\n'' "$3"',
        'printf ''State : RUNNING\n'''
    )

    $testPid = (& $shell.Source -c 'sleep 120 >/dev/null 2>&1 & echo $!').Trim()
    if ($testPid -notmatch '^\d+$') {
        throw "Expected a numeric test PID, got $testPid"
    }
    Write-LfFile (Join-Path $sharedRun 'teddy.pid') @($testPid)

    $posixJobsFile = Convert-ToPosixPath $jobsFile
    $posixPasswordFile = Convert-ToPosixPath $passwordFile
    $environmentPath = Join-Path $sharedRoot 'teddy.env'
    Write-LfFile $environmentPath @(
        ('PATH=' + $posixFakeBin + ':$PATH'),
        "TEST_JOBS_FILE=$posixJobsFile",
        "TEDDY_CONF_DIR=$posixSharedConf",
        "TEDDY_RUN_DIR=$posixSharedRun",
        'TEDDY_HEALTH_URL=http://127.0.0.1:18081/system/health',
        'TEDDY_BASE_URL=http://127.0.0.1:18081',
        "TEDDY_AUTH_PASSWORD_FILE=$posixPasswordFile",
        'TEDDY_EXPECT_APPLICATION_COUNT=2'
    )

    $scriptPath = "$posixReleaseRoot/bin/acceptance.sh"
    & $shell.Source -c "chmod +x '$scriptPath' '$posixFakeBin/curl' '$posixFakeBin/yarn'"
    if ($LASTEXITCODE -ne 0) {
        throw "chmod exited with code $LASTEXITCODE"
    }

    Write-LfFile $jobsFile @(
        '{"state":"success","data":[{"appId":"application_1_1"},{"appId":"application_1_2"}]}'
    )
    $before = Join-Path $stateRoot 'before'
    $after = Join-Path $stateRoot 'after'
    $changed = Join-Path $stateRoot 'changed'
    $beforePosix = Convert-ToPosixPath $before
    $afterPosix = Convert-ToPosixPath $after
    $changedPosix = Convert-ToPosixPath $changed

    & $shell.Source $scriptPath capture $beforePosix
    if ($LASTEXITCODE -ne 0) {
        throw "before capture exited with code $LASTEXITCODE"
    }
    & $shell.Source $scriptPath capture $afterPosix
    if ($LASTEXITCODE -ne 0) {
        throw "after capture exited with code $LASTEXITCODE"
    }
    & $shell.Source $scriptPath compare $beforePosix $afterPosix
    if ($LASTEXITCODE -ne 0) {
        throw "matching snapshot comparison exited with code $LASTEXITCODE"
    }

    Write-LfFile $jobsFile @(
        '{"state":"success","data":[{"appId":"application_1_1"},{"appId":"application_1_3"}]}'
    )
    & $shell.Source $scriptPath capture $changedPosix
    if ($LASTEXITCODE -ne 0) {
        throw "changed capture exited with code $LASTEXITCODE"
    }
    & $shell.Source -c "'$scriptPath' compare '$beforePosix' '$changedPosix' >/dev/null 2>&1"
    if ($LASTEXITCODE -eq 0) {
        throw 'Acceptance comparison did not detect the ApplicationId change.'
    }

    [PSCustomObject]@{
        Capture = 'passed'
        MatchingComparison = 'passed'
        ChangedApplicationDetection = 'passed'
        ProductionConnections = 'none'
    }
} finally {
    if ($testPid) {
        & $shell.Source -c "kill '$testPid' 2>/dev/null || true"
    }
    if (Test-Path -LiteralPath $testRoot) {
        $resolvedTestRoot = (Resolve-Path -LiteralPath $testRoot).Path
        $targetPrefix = $targetRoot.TrimEnd('\') + '\'
        if (-not $resolvedTestRoot.StartsWith(
                $targetPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to remove unexpected path: $resolvedTestRoot"
        }
        Remove-Item -LiteralPath $resolvedTestRoot -Recurse -Force
    }
}
