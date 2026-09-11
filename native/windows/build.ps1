param([string]$ZigPath = $env:ZIG_EXE)
$ErrorActionPreference = 'Stop'
if (!$ZigPath) {
    $ZigPath = Join-Path $env:LOCALAPPDATA 'FileLockInspector\toolchains\zig-x86_64-windows-0.14.1\zig.exe'
}
if (!(Test-Path -LiteralPath $ZigPath)) {
    throw 'Install Zig 0.14.1 and set ZIG_EXE to its zig.exe path. See docs/en/build.md.'
}
$outputDir = Join-Path $PSScriptRoot 'build'
$versionFile = Join-Path $PSScriptRoot '../../version.properties'
$versionLines = @(Get-Content -LiteralPath $versionFile | Where-Object { $_ -match '^version=' })
if ($versionLines.Count -ne 1 -or $versionLines[0] -notmatch '^version=([0-9]+\.[0-9]+\.[0-9]+(?:[.-][A-Za-z0-9.-]+)?)$') {
    throw 'version.properties must contain one valid version entry.'
}
$pluginVersion = $Matches[1]
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
& $ZigPath c++ -target x86_64-windows-gnu -std=c++20 -O2 -static -municode "-DFILE_LOCK_INSPECTOR_VERSION=$pluginVersion" (Join-Path $PSScriptRoot 'src/main.cpp') -o (Join-Path $outputDir 'file-lock-inspector-helper.exe') -ladvapi32 -lpsapi
if ($LASTEXITCODE -ne 0) { throw "Native build failed: $LASTEXITCODE" }
