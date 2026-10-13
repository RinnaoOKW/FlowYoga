# 启动与停止脚本共用的进程记录。只操作当前工作区明确启动的 JVM。
$flowYogaModules = @("flowyoga-member", "flowyoga-user", "flowyoga-course", "flowyoga-gateway")

function Write-FlowYogaProcessState {
    param([string]$Path, [string]$WorkspaceRoot, [object[]]$Processes)

    $temporaryPath = "$Path.tmp"
    $state = [ordered]@{
        schemaVersion = 1
        workspaceRoot = [IO.Path]::GetFullPath($WorkspaceRoot)
        processes = @($Processes)
    } | ConvertTo-Json -Depth 4
    try {
        Set-Content -LiteralPath $temporaryPath -Value $state -Encoding UTF8
        if (Test-Path -LiteralPath $Path) {
            [IO.File]::Replace($temporaryPath, $Path, [System.Management.Automation.Language.NullString]::Value)
        } else {
            [IO.File]::Move($temporaryPath, $Path)
        }
    } finally {
        if (Test-Path -LiteralPath $temporaryPath) {
            Remove-Item -LiteralPath $temporaryPath
        }
    }
}

function Test-FlowYogaProcessIdentity {
    param([string]$WorkspaceRoot, [object]$Record, [object]$Snapshot, [object]$Process)

    # 读取真实进程信息失败属于结果未知，让停止脚本保留记录供重试。
    $startTimeUtcTicks = $Process.StartTime.ToUniversalTime().Ticks
    try {
        if ($flowYogaModules -notcontains $Record.module -or [int]$Record.processId -le 0) {
            return $false
        }
        if ([int]$Record.processId -ne [int]$Snapshot.ProcessId -or
            [int]$Record.processId -ne [int]$Process.Id -or
            $Snapshot.Name -ne "java.exe" -or $Process.HasExited) {
            return $false
        }
        # PID 被复用时，即使命令相同，也不能结束后来启动的进程。
        if ($startTimeUtcTicks -ne [long]$Record.startTimeUtcTicks) {
            return $false
        }
        $targetDirectory = [IO.Path]::GetFullPath((Join-Path $WorkspaceRoot "$($Record.module)\target"))
        $jarPath = [IO.Path]::GetFullPath([string]$Record.jarPath)
        if (-not [string]::Equals([IO.Path]::GetDirectoryName($jarPath), $targetDirectory,
                [StringComparison]::OrdinalIgnoreCase) -or
            [IO.Path]::GetFileName($jarPath) -notlike "$($Record.module)-*.jar") {
            return $false
        }
        # 校验 java -jar 的实际参数，而不是只查找项目名称或端口。
        $arguments = @([regex]::Matches([string]$Snapshot.CommandLine, '"[^"]*"|\S+') |
            ForEach-Object { $_.Value.Trim('"') })
        $jarOption = [Array]::IndexOf($arguments, "-jar")
        if ($jarOption -lt 0 -or $jarOption + 1 -ge $arguments.Count) {
            return $false
        }
        $runningJar = [IO.Path]::GetFullPath($arguments[$jarOption + 1])
        return [string]::Equals($runningJar, $jarPath, [StringComparison]::OrdinalIgnoreCase)
    } catch {
        return $false
    }
}
