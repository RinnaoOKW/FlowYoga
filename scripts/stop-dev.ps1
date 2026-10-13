$ErrorActionPreference = "Stop"
$root = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
. "$PSScriptRoot\dev-processes.ps1"
$statePath = Join-Path $root "logs\dev-processes.json"

if (-not (Test-Path -LiteralPath $statePath)) {
    Write-Host "没有当前工作区的启动记录，不结束任何进程。"
    return
}
$state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
if ($state.schemaVersion -ne 1 -or
    -not [string]::Equals([IO.Path]::GetFullPath([string]$state.workspaceRoot), $root,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw "启动记录不属于当前工作区，不结束任何进程。"
}

$remaining = @()
foreach ($record in $state.processes) {
    try {
        $snapshot = Get-CimInstance Win32_Process -Filter "ProcessId = $([int]$record.processId)" -ErrorAction Stop
        if ($null -eq $snapshot) {
            Write-Host "$($record.module) 已退出。"
            continue
        }
        $serviceProcess = Get-Process -Id ([int]$record.processId) -ErrorAction Stop
        if (-not (Test-FlowYogaProcessIdentity -WorkspaceRoot $root -Record $record `
                -Snapshot $snapshot -Process $serviceProcess)) {
            Write-Warning "PID $($record.processId) 与启动记录不符，跳过该进程并清除过期记录。"
            continue
        }
        Write-Host "停止 $($record.module)，PID $($record.processId)"
        Stop-Process -InputObject $serviceProcess -ErrorAction Stop
    } catch {
        Write-Warning "无法确认或停止 $($record.module)：$($_.Exception.Message)。保留记录供重试。"
        $remaining += $record
    }
}
if ($remaining.Count -gt 0) {
    Write-FlowYogaProcessState -Path $statePath -WorkspaceRoot $root -Processes $remaining
    throw "仍有 $($remaining.Count) 个进程未确认停止，启动记录已保留，请查看上述原因后重试。"
} else {
    Remove-Item -LiteralPath $statePath
}
Write-Host "如需同时停止容器里的 Nacos，执行 docker compose stop。本机发行包启动的 Nacos 不会被这个脚本停止。"
