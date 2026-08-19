$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$maven = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $maven) {
    throw 'Maven is unavailable. Add mvn to PATH before building a release.'
}

Push-Location $projectRoot
try {
    & $maven.Source -DskipTests clean package
    if ($LASTEXITCODE -ne 0) {
        throw "Maven exited with code $LASTEXITCODE"
    }

    $targetDirectory = Join-Path $projectRoot 'target'
    $thinJars = @(
        Get-ChildItem -LiteralPath $targetDirectory -File |
            Where-Object { $_.Name -match '^teddy-.+\.jar$' }
    )
    $releaseArchives = @(
        Get-ChildItem -LiteralPath $targetDirectory -File |
            Where-Object { $_.Name -match '^teddy-.+-release\.(zip|tar\.gz)$' }
    )

    if ($thinJars.Count -ne 1) {
        throw "Expected one versioned thin JAR, found $($thinJars.Count)."
    }

    $artifacts = @($thinJars + $releaseArchives) | Sort-Object Name

    if ($releaseArchives.Count -ne 2) {
        throw "Expected ZIP and tar.gz release archives, found $($releaseArchives.Count)."
    }

    $checksumLines = foreach ($artifact in $artifacts) {
        $hash = Get-FileHash -Algorithm SHA256 -LiteralPath $artifact.FullName
        '{0}  {1}' -f $hash.Hash.ToLowerInvariant(), $artifact.Name
    }

    $checksumPath = Join-Path $targetDirectory 'SHA256SUMS'
    [System.IO.File]::WriteAllLines(
        $checksumPath,
        $checksumLines,
        [System.Text.UTF8Encoding]::new($false)
    )

    $artifacts | Select-Object Name, Length, FullName
    Get-Item -LiteralPath $checksumPath | Select-Object Name, Length, FullName
} finally {
    Pop-Location
}
