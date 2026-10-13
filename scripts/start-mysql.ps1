param([string]$MySqlHome = "E:\MySQL Main", [string]$MySqlDataRoot = "")
$ErrorActionPreference = "Stop"
$root = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
. "$PSScriptRoot\stage2-runtime.ps1"
$mysql = Join-Path $MySqlHome "bin\mysql.exe"
$mysqld = Join-Path $MySqlHome "bin\mysqld.exe"
if (-not (Test-Path -LiteralPath $mysqld)) { throw "找不到 MySQL，请用 -MySqlHome 指定 MySQL 8.0.16+ 的安装目录。" }
$mysqlRoot = Join-Path $root "logs\mysql"
New-Item -ItemType Directory -Path $mysqlRoot -Force | Out-Null
$settingsPath = Join-Path $root "logs\stage2-local.properties"
if (-not (Test-Path -LiteralPath $settingsPath)) {
    function New-LocalSecret {
        $bytes = New-Object byte[] 32
        $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $generator.GetBytes($bytes); return [Convert]::ToBase64String($bytes) } finally { $generator.Dispose() }
    }
    @(
        '# 本机开发配置，位于忽略的 logs/ 下；不要提交或分享。'
        ('flowyoga.security.jwt-secret=' + (New-LocalSecret))
        ('flowyoga.database.root-password=' + (New-LocalSecret))
        ('flowyoga.database.user-password=' + (New-LocalSecret))
        ('flowyoga.database.member-password=' + (New-LocalSecret))
        ('flowyoga.database.course-password=' + (New-LocalSecret))
        'flowyoga.demo.enabled=true'
    ) | Set-Content -LiteralPath $settingsPath -Encoding UTF8
}
$settings = Read-FlowYogaLocalSettings $root
if (-not $settings.ContainsKey('flowyoga.internal.account-token')) {
    $accountKeyBytes = New-Object byte[] 32
    $accountKeyGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $accountKeyGenerator.GetBytes($accountKeyBytes) } finally { $accountKeyGenerator.Dispose() }
    $settings['flowyoga.internal.account-token'] = [Convert]::ToBase64String($accountKeyBytes)
    ('flowyoga.internal.account-token=' + $settings['flowyoga.internal.account-token']) | Add-Content -LiteralPath $settingsPath -Encoding UTF8
}
if ($settings.ContainsKey('flowyoga.database.data-root')) {
    if ($MySqlDataRoot -and [IO.Path]::GetFullPath($MySqlDataRoot) -ne [IO.Path]::GetFullPath($settings['flowyoga.database.data-root'])) {
        throw "数据目录已固定，改路径需要先迁移数据。"
    }
    $MySqlDataRoot = $settings['flowyoga.database.data-root']
} else {
    if (-not $MySqlDataRoot) { $MySqlDataRoot = Get-FlowYogaDefaultMySqlRoot $root }
    if ($MySqlDataRoot -match '[^\x00-\x7F]') { throw "Windows MySQL 不支持中文数据路径，请用 -MySqlDataRoot 指定 ASCII 路径。" }
    ('flowyoga.database.data-root=' + [IO.Path]::GetFullPath($MySqlDataRoot).Replace('\','/')) |
        Add-Content -LiteralPath $settingsPath -Encoding UTF8
}
$dataDirectory = Join-Path $MySqlDataRoot "data"
$temporaryDirectory = Join-Path $MySqlDataRoot "tmp"
New-Item -ItemType Directory -Path $MySqlDataRoot, $temporaryDirectory -Force | Out-Null
$statePath = Join-Path $mysqlRoot "process.json"
$running = $false
if (Test-Path -LiteralPath $statePath) {
    $record = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    $running = Test-FlowYogaInfrastructure $record $dataDirectory "mysqld.exe"
}
if (-not $running) {
    $listener = Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object { $_.LocalPort -eq 13306 }
    if ($listener) { throw "13306 端口已被未确认归属的进程占用，未启动或修改数据库。" }
    if (-not (Test-Path -LiteralPath (Join-Path $dataDirectory "mysql.ibd"))) {
        & $mysqld --no-defaults --initialize-insecure ("--basedir=$MySqlHome") ("--datadir=$dataDirectory") --console
        if ($LASTEXITCODE -ne 0) { throw "项目独立 MySQL 数据目录初始化失败。" }
    }
    $arguments = @('--no-defaults', '--no-monitor', ('--basedir="{0}"' -f $MySqlHome), ('--datadir="{0}"' -f $dataDirectory),
        '--port=13306', '--bind-address=127.0.0.1', '--mysqlx=OFF',
        ('--tmpdir="{0}"' -f $temporaryDirectory), ('--log-error="{0}"' -f (Join-Path $MySqlDataRoot 'server.log')))
    $process = Start-Process -FilePath $mysqld -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $mysqlRoot "launch.out.log") -RedirectStandardError (Join-Path $mysqlRoot "launch.err.log")
    [ordered]@{ workspaceRoot=$root; processId=$process.Id; startTimeUtcTicks=$process.StartTime.ToUniversalTime().Ticks; dataDirectory=$dataDirectory; mySqlHome=$MySqlHome } |
        ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding UTF8
}
$deadline = (Get-Date).AddSeconds(45)
$ready = $false
$rootPassword = $settings['flowyoga.database.root-password']
$ownedRecord = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
while ((Get-Date) -lt $deadline) {
    $ownedProcess = Get-Process -Id ([int]$ownedRecord.processId) -ErrorAction Stop
    if ($ownedProcess.HasExited -or $ownedProcess.StartTime.ToUniversalTime().Ticks -ne [long]$ownedRecord.startTimeUtcTicks) {
        throw "项目 MySQL 进程已退出或身份改变，未连接或修改其他实例。"
    }
    $ownedListener = Get-NetTCPConnection -State Listen -ErrorAction Stop |
        Where-Object { $_.LocalPort -eq 13306 -and $_.OwningProcess -eq [int]$ownedRecord.processId }
    if (-not $ownedListener) { Start-Sleep -Seconds 2; continue }
    try { Invoke-FlowYogaSql $mysql "root" $rootPassword "SELECT 1;" 2>$null | Out-Null; $ready=$true; break } catch { }
    # 新初始化的本项目实例仅第一次使用空 root 密码，随即设置随机密码。
    try { Invoke-FlowYogaSql $mysql "root" "" "SELECT 1;" 2>$null | Out-Null; $rootPassword=""; $ready=$true; break } catch { }
    Start-Sleep -Seconds 2
}
if (-not $ready) { throw "项目 MySQL 未就绪，请查看 $MySqlDataRoot\server.log。" }
$bootstrap = Get-Content -LiteralPath (Join-Path $root "database\00-databases.sql") -Raw
foreach ($owner in @('user','member','course')) {
    $password = $settings["flowyoga.database.$owner-password"]
    foreach ($hostName in @('127.0.0.1','localhost')) {
        $bootstrap += "`nCREATE USER IF NOT EXISTS 'flowyoga_$owner'@'$hostName' IDENTIFIED BY '$password';"
        $bootstrap += "`nGRANT ALL PRIVILEGES ON flowyoga_$owner.* TO 'flowyoga_$owner'@'$hostName';"
    }
}
if ($rootPassword -eq "") {
    $bootstrap += "`nALTER USER 'root'@'localhost' IDENTIFIED BY '$($settings['flowyoga.database.root-password'])';"
}
Invoke-FlowYogaSql $mysql "root" $rootPassword $bootstrap | Out-Null
Invoke-FlowYogaSql $mysql "flowyoga_member" $settings['flowyoga.database.member-password'] (Get-Content -LiteralPath (Join-Path $root 'database\02-member.sql') -Raw) | Out-Null
Invoke-FlowYogaSql $mysql "flowyoga_course" $settings['flowyoga.database.course-password'] (Get-Content -LiteralPath (Join-Path $root 'database\03-course.sql') -Raw) | Out-Null
Write-Host "项目 MySQL 已就绪：127.0.0.1:13306，三个数据库已建立。用户表将在登录服务启动时由 Flyway 创建。"
