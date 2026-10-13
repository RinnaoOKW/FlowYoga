$ErrorActionPreference = "Stop"
$root = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
. "$PSScriptRoot\stage2-runtime.ps1"
$frontendStatePath = Join-Path $root "logs\frontend-process.json"
if (Test-Path -LiteralPath $frontendStatePath) {
    $record = Get-Content -LiteralPath $frontendStatePath -Raw | ConvertFrom-Json
    $vitePath = Join-Path $root "frontend\node_modules\vite\bin\vite.js"
    if (Test-FlowYogaInfrastructure $record $vitePath "node.exe") {
        $frontendProcess = Get-Process -Id ([int]$record.processId) -ErrorAction Stop
        if ($frontendProcess.StartTime.ToUniversalTime().Ticks -eq [long]$record.startTimeUtcTicks) {
            Stop-Process -InputObject $frontendProcess -ErrorAction Stop
        }
    }
    Remove-Item -LiteralPath $frontendStatePath
}
& "$PSScriptRoot\stop-dev.ps1"
$mysqlStatePath = Join-Path $root "logs\mysql\process.json"
if (Test-Path -LiteralPath $mysqlStatePath) {
    $record = Get-Content -LiteralPath $mysqlStatePath -Raw | ConvertFrom-Json
    $settings = Read-FlowYogaLocalSettings $root
    if ($record.workspaceRoot -ne $root) { throw "MySQL 进程记录不属于当前项目。" }
    if (Test-FlowYogaInfrastructure $record (Join-Path $settings['flowyoga.database.data-root'] "data") "mysqld.exe") {
        $listener = Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object { $_.LocalPort -eq 13306 }
        if (-not $listener -or @($listener | Where-Object { $_.OwningProcess -ne [int]$record.processId }).Count -gt 0) {
            throw "MySQL 监听端口与项目进程记录不符，未关闭任何实例，保留记录供检查。"
        }
        $previousPassword = $env:MYSQL_PWD
        try {
            $env:MYSQL_PWD = $settings['flowyoga.database.root-password']
            & (Join-Path $record.mySqlHome "bin\mysqladmin.exe") --no-defaults --protocol=TCP --host=127.0.0.1 --port=13306 --user=root shutdown
            if ($LASTEXITCODE -ne 0) { throw "MySQL 正常关闭失败，保留进程记录供重试。" }
        } finally { $env:MYSQL_PWD = $previousPassword }
    }
    Remove-Item -LiteralPath $mysqlStatePath
}
Write-Host "第二阶段服务已停止，MySQL 数据保留。Nacos 仍可供后续启动复用。"
