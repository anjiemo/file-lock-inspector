param([Parameter(Mandatory=$true)][string]$Target, [Parameter(Mandatory=$true)][string]$Ready)
$ErrorActionPreference='Stop'
$stream=[IO.File]::Open($Target, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
try {
    New-Item -ItemType File -Path $Ready -Force | Out-Null
    while ($true) { Start-Sleep -Milliseconds 200 }
} finally { $stream.Dispose() }
