$ErrorActionPreference='Stop'
$helper=Join-Path $PSScriptRoot '../build/file-lock-inspector-helper.exe'
$fixture=Join-Path $PSScriptRoot '../../../build/native-tests'
New-Item -ItemType Directory -Force -Path $fixture | Out-Null
$run=Join-Path $fixture ([Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $run | Out-Null
$target=Join-Path $run 'classes with spaces.jar'
New-Item -ItemType File -Path $target | Out-Null
$processes=@()
function Query([string]$Path) {
    $lines=& $helper scan $Path $PID
    return @($lines | ForEach-Object { $_ | ConvertFrom-Json })
}
try {
    foreach($index in 1..2) {
        $ready=Join-Path $run "ready$index"
        $arguments=@('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+(Join-Path $PSScriptRoot 'holder.ps1')+'"'),'-Target',('"'+$target+'"'),'-Ready',('"'+$ready+'"'))
        $child=Start-Process powershell.exe -ArgumentList $arguments -WindowStyle Hidden -PassThru
        $processes+=$child
        $deadline=[DateTime]::UtcNow.AddSeconds(10)
        while(!(Test-Path -LiteralPath $ready)) {
            if($child.HasExited -or [DateTime]::UtcNow -gt $deadline){throw 'Holder did not start'}
            Start-Sleep -Milliseconds 100
        }
    }
    $result=Query $target
    $holders=@($result|Where-Object {$_.type -eq 'process' -and $_.pid -in $processes.Id})
    if($holders.Count -ne 2){throw "Expected both holders, got $($holders.Count)"}
    Write-Output 'PASS: multiple holders detected by exact target identity'
    $alias=Join-Path $run (([char]0x4E2D).ToString()+([char]0x6587).ToString()+' alias.jar')
    New-Item -ItemType HardLink -Path $alias -Target $target | Out-Null
    $aliasResult=Query $alias
    if(@($aliasResult|Where-Object {$_.type -eq 'process' -and $_.pid -in $processes.Id}).Count -ne 2){throw 'Hard-link identity match failed'}
    Write-Output 'PASS: Chinese hard-link alias resolves the same file identity'
    $row=$holders[0]
    $mismatch=& $helper terminate $target $PID $row.pid '1' $row.fileId | ConvertFrom-Json
    if($mismatch.status -ne 'identityChanged'){throw 'Stale process identity accepted'}
    $mismatch=& $helper terminate $target $PID $row.pid $row.startTime 'wrong-file-id' | ConvertFrom-Json
    if($mismatch.status -ne 'notConfirmed'){throw 'Stale file identity accepted'}
    Write-Output 'PASS: stale process and file identities rejected'
    $protected=& $helper terminate $target $row.pid $row.pid $row.startTime $row.fileId | ConvertFrom-Json
    if($protected.status -ne 'protected'){throw 'IDE protection failed'}
    Write-Output 'PASS: protected IDE identity rejected'
    $ended=& $helper terminate $target $PID $row.pid $row.startTime $row.fileId | ConvertFrom-Json
    if($ended.status -ne 'terminated'){throw "Termination failed: $($ended.status)"}
    $after=Query $target
    if(@($after|Where-Object {$_.type -eq 'process' -and $_.pid -in $processes.Id}).Count -ne 1){throw 'Did not terminate exactly one holder'}
    Write-Output 'PASS: selected holder exited, other holder remains'
    $missing=Query (Join-Path $run 'missing.jar')
    if($missing[-1].status -ne 'missingFile'){throw 'Missing file status incorrect'}
    $directory=Query $run
    if($directory[-1].status -ne 'invalidPath'){throw 'Directory accepted'}
    Write-Output 'PASS: missing file and directory results distinguished'
} finally {
    foreach($child in $processes) {
        if(!$child.HasExited){$child.Kill();$child.WaitForExit(5000)|Out-Null}
        $child.Dispose()
    }
}
