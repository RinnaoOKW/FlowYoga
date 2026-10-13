$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
New-Item -ItemType Directory -Force -Path "$root\logs" | Out-Null
. "$PSScriptRoot\dev-processes.ps1"
$statePath = Join-Path $root "logs\dev-processes.json"
if (Test-Path -LiteralPath $statePath) {
    throw "已有启动记录。请先运行 scripts\stop-dev.ps1，再启动服务。"
}

$mvn = (Get-Command mvn.cmd -ErrorAction Stop).Source
$java = (Get-Command java.exe -ErrorAction Stop).Source
$project = [xml](Get-Content -LiteralPath "$root\pom.xml" -Raw)
$version = [string]$project.project.version

Write-Host "打包服务"
& $mvn -B -DskipTests package
if ($LASTEXITCODE -ne 0) {
    throw "Maven 打包失败，未启动服务。请修复构建错误后重试。"
}

Write-Host "启动 Nacos"
docker compose up -d
if ($LASTEXITCODE -ne 0) {
    throw "Nacos 容器启动失败，未启动服务。请查看 Docker 输出。"
}

$deadline = (Get-Date).AddMinutes(3)
$ready = $false
$healthUrls = @(
    "http://127.0.0.1:8080/v3/console/health/readiness",
    "http://127.0.0.1:8848/nacos/actuator/health"
)
while ((Get-Date) -lt $deadline) {
    foreach ($url in $healthUrls) {
        try {
            $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                $ready = $true
                break
            }
        } catch {
            continue
        }
    }
    if ($ready) {
        break
    }
    Write-Host "等待 Nacos"
    Start-Sleep -Seconds 3
}
if (-not $ready) {
    throw "Nacos 在 3 分钟内没有就绪。请查看 docker logs flowyoga-nacos"
}

$processRecords = @()
foreach ($module in $flowYogaModules) {
    $jarPath = Join-Path $root "$module\target\$module-$version.jar"
    if (-not (Test-Path -LiteralPath $jarPath)) {
        throw "找不到 $jarPath。已启动的服务可用 scripts\stop-dev.ps1 停止。"
    }
    Write-Host "启动 $module"
    $serviceProcess = Start-Process -FilePath $java `
        -ArgumentList @("-jar", ('"{0}"' -f $jarPath)) `
        -WorkingDirectory $root `
        -RedirectStandardOutput "$root\logs\$module.out.log" `
        -RedirectStandardError "$root\logs\$module.err.log" `
        -WindowStyle Hidden `
        -PassThru
    try {
        $processRecords += [ordered]@{
            module = $module
            processId = $serviceProcess.Id
            startTimeUtcTicks = $serviceProcess.StartTime.ToUniversalTime().Ticks
            jarPath = $jarPath
        }
        Write-FlowYogaProcessState -Path $statePath -WorkspaceRoot $root -Processes $processRecords
    } catch {
        # 尚未成功记录的进程只能用本次 Start-Process 返回的对象清理。
        Stop-Process -InputObject $serviceProcess -ErrorAction SilentlyContinue
        throw "无法记录 $module 的启动信息。已记录的服务可用 scripts\stop-dev.ps1 停止。$($_.Exception.Message)"
    }
}

Write-Host "服务正在启动。就绪后执行 scripts\verify-chain.ps1"
