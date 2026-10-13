function Get-FlowYogaDefaultMySqlRoot {
    param([string]$Root)
    $hash = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = $hash.ComputeHash([Text.Encoding]::UTF8.GetBytes([IO.Path]::GetFullPath($Root).ToLowerInvariant()))
        $suffix = ([BitConverter]::ToString($bytes).Replace('-', '').Substring(0, 12)).ToLowerInvariant()
        return Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) "FlowYoga\$suffix\mysql"
    } finally { $hash.Dispose() }
}

function Read-FlowYogaLocalSettings {
    param([string]$Root)
    $settings = @{}
    $path = Join-Path $Root "logs\stage2-local.properties"
    if (-not (Test-Path -LiteralPath $path)) { throw "缺少本地配置，请先执行 start-mysql.ps1。" }
    foreach ($line in Get-Content -LiteralPath $path -Encoding UTF8) {
        $separator = $line.IndexOf('=')
        if ($separator -gt 0 -and -not $line.StartsWith('#')) {
            $settings[$line.Substring(0, $separator).Trim()] = $line.Substring($separator + 1).Trim()
        }
    }
    return $settings
}

function Invoke-FlowYogaSql {
    param([string]$MySql, [string]$User, [string]$Password, [string]$Sql, [int]$Port = 13306)
    $previousPassword = $env:MYSQL_PWD
    $previousEncoding = $OutputEncoding
    try {
        $env:MYSQL_PWD = $Password
        $OutputEncoding = New-Object System.Text.UTF8Encoding($false)
        $result = $Sql | & $MySql --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$Port" "--user=$User" --default-character-set=utf8mb4 --batch --skip-column-names --connect-timeout=3
        if ($LASTEXITCODE -ne 0) { throw "MySQL 操作失败，请检查数据库日志和当前账号的授权。" }
        return $result
    } finally {
        $env:MYSQL_PWD = $previousPassword
        $OutputEncoding = $previousEncoding
    }
}

function Test-FlowYogaInfrastructure {
    param([object]$Record, [string]$OwnedPath, [string]$ExpectedName)
    $snapshot = Get-CimInstance Win32_Process -Filter "ProcessId = $([int]$Record.processId)" -ErrorAction Stop
    if ($null -eq $snapshot) { return $false }
    $process = Get-Process -Id ([int]$Record.processId) -ErrorAction Stop
    if ($snapshot.Name -ne $ExpectedName -or $process.StartTime.ToUniversalTime().Ticks -ne [long]$Record.startTimeUtcTicks) { return $false }
    $escapedPath = [regex]::Escape([IO.Path]::GetFullPath($OwnedPath))
    return [regex]::IsMatch([string]$snapshot.CommandLine, $escapedPath + '(?:["\s]|$)', [Text.RegularExpressions.RegexOptions]::IgnoreCase)
}
