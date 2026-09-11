param([string]$ZigPath = $env:ZIG_EXE)
$ErrorActionPreference = 'Stop'
if (!$ZigPath) {
    $ZigPath = Join-Path $env:LOCALAPPDATA 'FileLockInspector\toolchains\zig-x86_64-windows-0.14.1\zig.exe'
}
if (!(Test-Path -LiteralPath $ZigPath)) {
    throw 'Install Zig 0.14.1 and set ZIG_EXE to its zig.exe path. See docs/en/build.md.'
}
$outputDir = Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
& $ZigPath c++ -target x86_64-windows-gnu -std=c++20 -O2 -static -municode (Join-Path $PSScriptRoot 'src/main.cpp') -o (Join-Path $outputDir 'file-lock-inspector-helper.exe') -ladvapi32 -lpsapi
if ($LASTEXITCODE -ne 0) { throw "Native build failed: $LASTEXITCODE" }
