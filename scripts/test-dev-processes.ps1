# 无外部依赖的安全回归：所有进程、Docker、构建调用均使用替身，不结束真实进程。
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\dev-processes.ps1"
$sourceScripts = $PSScriptRoot
$originalLocation = Get-Location
$temporaryParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$fixtureDirectory = Join-Path $temporaryParent ("flowyoga-process-tests-" + [guid]::NewGuid().ToString("N"))
$fixtureRoot = Join-Path $fixtureDirectory "Flow Yoga 测试"
$testContext = @{ Checks = 0; Stopped = @(); Snapshots = @{}; Processes = @{}; FailIds = @(); BuildExitCode = 0; DockerExitCode = 0; DockerCalls = 0; Starts = @(); FailStartAt = 0 }

function Assert-Condition {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "回归失败：$Message" }
    $testContext.Checks++
}

function New-TestRecord {
    param([string]$Module, [int]$ProcessId)
    $startTime = [datetime]::UtcNow
    $jarPath = Join-Path $fixtureRoot "$Module\target\$Module-0.1.0-SNAPSHOT.jar"
    $testContext.Snapshots[$ProcessId] = [pscustomobject]@{ ProcessId = $ProcessId; Name = "java.exe"; CommandLine = '"G:\jdk 21\bin\java.exe" -jar "' + $jarPath + '"' }
    $testContext.Processes[$ProcessId] = [pscustomobject]@{ Id = $ProcessId; StartTime = $startTime; HasExited = $false }
    return [pscustomobject]@{ module = $Module; processId = $ProcessId; startTimeUtcTicks = $startTime.Ticks; jarPath = $jarPath }
}

function Get-CimInstance {
    param([string]$ClassName, [string]$Filter, [string]$ErrorAction)
    $processNumber = [int]([regex]::Match($Filter, '\d+').Value)
    if ($testContext.FailIds -contains $processNumber) { throw "模拟进程信息不可读" }
    return $testContext.Snapshots[$processNumber]
}
function Get-Process {
    param([int]$Id, [string]$ErrorAction)
    return $testContext.Processes[$Id]
}
function Stop-Process {
    param([object]$InputObject, [string]$ErrorAction)
    $testContext.Stopped += $InputObject.Id
}
function Get-Command {
    param([string]$Name, [string]$ErrorAction)
    if ($Name -eq "mvn.cmd") { return [pscustomobject]@{ Source = (Join-Path $fixtureRoot "fake-build.ps1") } }
    if ($Name -eq "java.exe") { return [pscustomobject]@{ Source = "G:\jdk 21\bin\java.exe" } }
    throw "未预期的命令：$Name"
}
function docker {
    $testContext.DockerCalls++
    $global:LASTEXITCODE = $testContext.DockerExitCode
}
function Invoke-WebRequest {
    param([string]$Uri, [switch]$UseBasicParsing, [int]$TimeoutSec)
    return [pscustomobject]@{ StatusCode = 200 }
}
function Start-Process {
    param([string]$FilePath, [string[]]$ArgumentList, [string]$WorkingDirectory,
        [string]$RedirectStandardOutput, [string]$RedirectStandardError, [string]$WindowStyle, [switch]$PassThru)
    Assert-Condition ($WindowStyle -eq "Hidden" -and $PassThru) "后台启动应隐藏窗口并返回进程"
    Assert-Condition ($ArgumentList[0] -eq "-jar") "应直接启动 JVM"
    $processNumber = 20000 + $testContext.Starts.Count
    $startTime = [datetime]::UtcNow
    $testContext.Starts += $processNumber
    if ($testContext.FailStartAt -eq $testContext.Starts.Count) { $startTime = $null }
    return [pscustomobject]@{ Id = $processNumber; StartTime = $startTime }
}

try {
    New-Item -ItemType Directory -Path (Join-Path $fixtureRoot "scripts"), (Join-Path $fixtureRoot "logs") -Force | Out-Null
    foreach ($scriptName in @("start-dev.ps1", "stop-dev.ps1", "dev-processes.ps1")) {
        Copy-Item -LiteralPath (Join-Path $sourceScripts $scriptName) -Destination (Join-Path $fixtureRoot "scripts\$scriptName")
    }
    Set-Content -LiteralPath (Join-Path $fixtureRoot "pom.xml") -Value '<project><version>0.1.0-SNAPSHOT</version></project>'
    Set-Content -LiteralPath (Join-Path $fixtureRoot "fake-build.ps1") -Value '$global:LASTEXITCODE = $testContext.BuildExitCode'
    foreach ($module in $flowYogaModules) {
        New-Item -ItemType Directory -Path (Join-Path $fixtureRoot "$module\target") -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $fixtureRoot "$module\target\$module-0.1.0-SNAPSHOT.jar") -Value "fixture"
    }
    $statePath = Join-Path $fixtureRoot "logs\dev-processes.json"
    $startScript = Join-Path $fixtureRoot "scripts\start-dev.ps1"
    $stopScript = Join-Path $fixtureRoot "scripts\stop-dev.ps1"

    # 没有记录时不扫描端口或进程，也不执行停止。
    & $stopScript
    Assert-Condition ($testContext.Stopped.Count -eq 0) "没有启动记录时不得结束进程"

    $owned = New-TestRecord "flowyoga-member" 11001
    Assert-Condition (Test-FlowYogaProcessIdentity $fixtureRoot $owned $testContext.Snapshots[11001] $testContext.Processes[11001]) "含空格和中文的当前项目 jar 应匹配"
    $foreign = New-TestRecord "flowyoga-gateway" 11002
    $testContext.Snapshots[11002].CommandLine = 'java.exe -jar "' + (Join-Path ($fixtureRoot + "-other") "flowyoga-gateway\target\flowyoga-gateway-0.1.0-SNAPSHOT.jar") + '"'
    Assert-Condition (-not (Test-FlowYogaProcessIdentity $fixtureRoot $foreign $testContext.Snapshots[11002] $testContext.Processes[11002])) "另一工作区的同名服务不得匹配"
    $reused = New-TestRecord "flowyoga-user" 11003
    $testContext.Processes[11003].StartTime = $testContext.Processes[11003].StartTime.AddSeconds(1)
    Assert-Condition (-not (Test-FlowYogaProcessIdentity $fixtureRoot $reused $testContext.Snapshots[11003] $testContext.Processes[11003])) "复用的 PID 不得匹配"
    $unreadable = New-TestRecord "flowyoga-course" 11004
    $testContext.FailIds = @(11004)
    Write-FlowYogaProcessState $statePath $fixtureRoot @($owned, $foreign, $reused, $unreadable)
    $rejected = $false
    try { & $stopScript } catch { $rejected = $true }
    Assert-Condition $rejected "无法读取进程信息时不得报告全部停止成功"
    Assert-Condition ($testContext.Stopped.Count -eq 1 -and $testContext.Stopped[0] -eq 11001) "停止脚本只能结束已经核对的当前项目进程"
    $remainingState = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    Assert-Condition (@($remainingState.processes).Count -eq 1 -and $remainingState.processes[0].processId -eq 11004) "信息读取失败必须保留记录，失效 PID 应清理"

    Write-FlowYogaProcessState $statePath ($fixtureRoot + "-other") @($owned)
    $rejected = $false
    try { & $stopScript } catch { $rejected = $true }
    Assert-Condition ($rejected -and $testContext.Stopped.Count -eq 1) "其他工作区的进程记录必须被拒绝"
    Remove-Item -LiteralPath $statePath

    & $startScript
    $startedState = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    Assert-Condition (@($startedState.processes).Count -eq 4 -and $testContext.Starts.Count -eq 4) "启动后必须记录全部四个 JVM"
    Assert-Condition ($startedState.workspaceRoot -eq $fixtureRoot -and $startedState.processes[0].startTimeUtcTicks -gt 0) "记录必须包含项目路径和启动时间"
    $rejected = $false
    try { & $startScript } catch { $rejected = $true }
    Assert-Condition ($rejected -and $testContext.Starts.Count -eq 4) "重复启动不得覆盖进程记录或再启动服务"
    Remove-Item -LiteralPath $statePath

    $testContext.Starts = @()
    $testContext.DockerCalls = 0
    $testContext.BuildExitCode = 1
    $rejected = $false
    try { & $startScript } catch { $rejected = $true }
    Assert-Condition ($rejected -and $testContext.Starts.Count -eq 0 -and $testContext.DockerCalls -eq 0) "打包失败必须在 Docker 和 JVM 启动前中止"
    $testContext.BuildExitCode = 0
    $testContext.DockerExitCode = 1
    $rejected = $false
    try { & $startScript } catch { $rejected = $true }
    Assert-Condition ($rejected -and $testContext.Starts.Count -eq 0 -and -not (Test-Path -LiteralPath $statePath)) "Docker 失败不得启动 JVM 或留下虚假进程记录"

    $testContext.DockerExitCode = 0
    $testContext.FailStartAt = 3
    $rejected = $false
    try { & $startScript } catch { $rejected = $true }
    $partialState = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    Assert-Condition ($rejected -and @($partialState.processes).Count -eq 2) "部分启动失败时已记录的两个服务仍可停止"
    Assert-Condition ($testContext.Stopped[-1] -eq 20002) "记录失败时只清理本次刚启动且尚未记录的进程"

    Write-Host "进程安全回归通过：$($testContext.Checks) 项检查。没有结束真实进程。"
} finally {
    Set-Location $originalLocation
    # 只删除本次创建的临时目录，先核对绝对路径范围。
    $resolvedFixture = [IO.Path]::GetFullPath($fixtureDirectory)
    $expectedPrefix = $temporaryParent.TrimEnd('\') + '\flowyoga-process-tests-'
    if ($resolvedFixture.StartsWith($expectedPrefix, [StringComparison]::OrdinalIgnoreCase) -and
        (Test-Path -LiteralPath $resolvedFixture)) {
        Remove-Item -LiteralPath $resolvedFixture -Recurse -Force
    }
}
