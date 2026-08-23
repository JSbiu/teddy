[CmdletBinding()]
param(
    [string]$TargetDirectory = (Join-Path $PSScriptRoot '..\target')
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$targetRoot = [System.IO.Path]::GetFullPath($TargetDirectory)
$pomPath = Join-Path $projectRoot 'pom.xml'
[xml]$pom = Get-Content -Raw -LiteralPath $pomPath
$version = [string]$pom.project.version
if ([string]::IsNullOrWhiteSpace($version)) {
    throw 'Could not read the Maven project version.'
}

$releaseName = "teddy-$version"
$zipPath = Join-Path $targetRoot "$releaseName-release.zip"
$tarPath = Join-Path $targetRoot "$releaseName-release.tar.gz"
$thinJarPath = Join-Path $targetRoot "$releaseName.jar"
$checksumPath = Join-Path $targetRoot 'SHA256SUMS'
$expectedArtifacts = @($thinJarPath, $tarPath, $zipPath)

foreach ($artifact in @($expectedArtifacts + $checksumPath)) {
    if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) {
        throw "Missing release artifact: $artifact"
    }
}

$checksumEntries = @{}
foreach ($line in Get-Content -LiteralPath $checksumPath) {
    if ($line -notmatch '^([0-9a-fA-F]{64})\s{2}(.+)$') {
        throw "Invalid SHA256SUMS line: $line"
    }
    $checksumEntries[$Matches[2]] = $Matches[1].ToLowerInvariant()
}

foreach ($artifact in $expectedArtifacts) {
    $name = Split-Path -Leaf $artifact
    if (-not $checksumEntries.ContainsKey($name)) {
        throw "SHA256SUMS does not contain $name"
    }
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $artifact).Hash.ToLowerInvariant()
    if ($checksumEntries[$name] -ne $actual) {
        throw "SHA-256 mismatch for $name"
    }
}
if ($checksumEntries.Count -ne $expectedArtifacts.Count) {
    throw 'SHA256SUMS contains unexpected entries.'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
try {
    $zipEntries = @(
        $zip.Entries |
            Where-Object { -not $_.FullName.EndsWith('/') } |
            ForEach-Object { $_.FullName }
    )
} finally {
    $zip.Dispose()
}

$tar = Get-Command tar -ErrorAction SilentlyContinue
if (-not $tar) {
    throw 'tar is required to validate the tar.gz release archive.'
}
$tarEntries = @(
    & $tar.Source -tf $tarPath |
        Where-Object { -not $_.EndsWith('/') } |
        ForEach-Object { $_ -replace '^\./', '' }
)
if ($LASTEXITCODE -ne 0) {
    throw "tar could not list $tarPath"
}

$archiveDifference = Compare-Object -ReferenceObject ($zipEntries | Sort-Object) -DifferenceObject ($tarEntries | Sort-Object)
if ($archiveDifference) {
    throw 'ZIP and tar.gz archives do not contain the same files.'
}

foreach ($entry in $zipEntries) {
    if ($entry -match '(^/|(^|/)\.\.(/|$)|^[A-Za-z]:)') {
        throw "Unsafe archive path: $entry"
    }
    if (-not $entry.StartsWith("$releaseName/")) {
        throw "Archive entry is outside the version root: $entry"
    }
}

$requiredEntries = @(
    "$releaseName/teddy.jar",
    "$releaseName/bin/start.sh",
    "$releaseName/bin/stop.sh",
    "$releaseName/bin/upgrade.sh",
    "$releaseName/bin/rollback.sh",
    "$releaseName/bin/acceptance.sh",
    "$releaseName/conf/application.properties.example",
    "$releaseName/conf/teddy.env.example",
    "$releaseName/conf/teddy.properties.example"
)
foreach ($required in $requiredEntries) {
    if ($required -notin $zipEntries) {
        throw "Release archive is missing $required"
    }
}
if (-not ($zipEntries | Where-Object { $_ -like "$releaseName/lib/*.jar" })) {
    throw 'Release archive contains no runtime dependency JARs.'
}

$forbiddenEntries = @(
    "$releaseName/conf/application.properties",
    "$releaseName/conf/teddy.properties",
    "$releaseName/conf/teddy.env"
)
foreach ($forbidden in $forbiddenEntries) {
    if ($forbidden -in $zipEntries) {
        throw "Release archive contains populated configuration: $forbidden"
    }
}

$tarDetails = @(& $tar.Source -tvf $tarPath)
if ($LASTEXITCODE -ne 0) {
    throw "tar could not inspect permissions in $tarPath"
}
foreach ($script in $requiredEntries | Where-Object { $_ -like '*/bin/*.sh' }) {
    $detail = $tarDetails | Where-Object { $_ -match ([regex]::Escape($script) + '$') }
    if (-not $detail -or $detail -notmatch '^-rwxr-xr-x\s') {
        throw "Expected executable mode 0755 for $script"
    }
}

$tempDirectory = Join-Path $targetRoot ("release-check-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tempDirectory | Out-Null
try {
    [System.IO.Compression.ZipFile]::ExtractToDirectory($zipPath, $tempDirectory)
    $releaseRoot = Join-Path $tempDirectory $releaseName

    $sh = Get-Command sh -ErrorAction SilentlyContinue
    if (-not $sh) {
        throw 'sh is required to validate release scripts.'
    }
    foreach ($scriptName in @('start.sh', 'stop.sh', 'upgrade.sh', 'rollback.sh', 'acceptance.sh')) {
        $scriptPath = Join-Path $releaseRoot "bin\$scriptName"
        $bytes = [System.IO.File]::ReadAllBytes($scriptPath)
        if ($bytes -contains 13) {
            throw "$scriptName contains CRLF line endings."
        }
        & $sh.Source -n $scriptPath
        if ($LASTEXITCODE -ne 0) {
            throw "$scriptName failed shell syntax validation."
        }
    }

    $configurationFiles = @(
        Get-ChildItem -LiteralPath (Join-Path $releaseRoot 'conf') -File
    )
    if ($configurationFiles.Count -ne 3 -or
            ($configurationFiles | Where-Object { $_.Extension -ne '.example' })) {
        throw 'The release conf directory must contain only the three example files.'
    }

    foreach ($configurationFile in $configurationFiles) {
        foreach ($line in Get-Content -LiteralPath $configurationFile.FullName) {
            if ($line -match '^\s*#' -or $line -notmatch '=') {
                continue
            }
            $key, $value = $line -split '=', 2
            if ($key -match '(?i)(password|passwd|secret|token|webhook)' -and
                    -not [string]::IsNullOrWhiteSpace($value)) {
                throw "Potential secret in example configuration key $key"
            }
        }
    }

    $jarPath = Join-Path $releaseRoot 'teddy.jar'
    $jar = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
    try {
        $manifestEntry = $jar.GetEntry('META-INF/MANIFEST.MF')
        if (-not $manifestEntry) {
            throw 'teddy.jar has no manifest.'
        }
        $reader = [System.IO.StreamReader]::new(
            $manifestEntry.Open(), [System.Text.Encoding]::UTF8)
        try {
            $manifest = $reader.ReadToEnd() -replace "\r?\n ", ''
        } finally {
            $reader.Dispose()
        }
    } finally {
        $jar.Dispose()
    }

    if ($manifest -notmatch '(?m)^Main-Class:\s+com\.dbay\.teddy\.Application\s*$') {
        throw 'teddy.jar manifest has an unexpected Main-Class.'
    }
    if ($manifest -notmatch '(?m)^Class-Path:\s+(.+)$') {
        throw 'teddy.jar manifest has no Class-Path.'
    }
    foreach ($classPathEntry in $Matches[1].Trim().Split(' ')) {
        $dependencyPath = Join-Path $releaseRoot ($classPathEntry -replace '/', '\')
        if (-not (Test-Path -LiteralPath $dependencyPath -PathType Leaf)) {
            throw "Manifest dependency is missing from the release: $classPathEntry"
        }
    }
} finally {
    $resolvedTemp = [System.IO.Path]::GetFullPath($tempDirectory)
    $targetPrefix = $targetRoot.TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar
    ) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $resolvedTemp.StartsWith(
            $targetPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove temporary directory outside target: $resolvedTemp"
    }
    Remove-Item -LiteralPath $resolvedTemp -Recurse -Force
}

[PSCustomObject]@{
    Version = $version
    Files = $zipEntries.Count
    Checksums = 'passed'
    ArchiveParity = 'passed'
    ShellScripts = 'passed'
    ExampleConfiguration = 'passed'
    ManifestClasspath = 'passed'
}
