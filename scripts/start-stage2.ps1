param([switch]$SkipBuild, [string]$MySqlHome = "E:\MySQL Main", [string]$NodePath = "F:\node-v22.13.0\node.exe", [string]$MySqlDataRoot = "")
$ErrorActionPreference = "Stop"
$root = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
Set-Location $root
. "$PSScriptRoot\dev-processes.ps1"
. "$PSScriptRoot\stage2-runtime.ps1"
$statePath = Join-Path $root "logs\dev-processes.json"
$frontendStatePath = Join-Path $root "logs\frontend-process.json"
if ((Test-Path -LiteralPath $statePath) -or (Test-Path -LiteralPath $frontendStatePath)) {
    throw "已有服务启动记录，请先执行 scripts\stop-stage2.ps1。"
}
if (-not (Test-Path -LiteralPath $NodePath)) { throw "请用 -NodePath 指定 Node.js 20.19+ 或 22.12+。" }
& "$PSScriptRoot\start-mysql.ps1" -MySqlHome $MySqlHome -MySqlDataRoot $MySqlDataRoot
if (-not $SkipBuild) {
    & (Get-Command mvn.cmd).Source -s "$PSScriptRoot\maven-settings.xml" -B -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "后端打包失败。" }
    $previousPath = $env:PATH
    try {
        $env:PATH = (Split-Path -Parent $NodePath) + ';' + $env:PATH
        Push-Location (Join-Path $root "frontend")
        try {
            & (Join-Path (Split-Path -Parent $NodePath) "npm.cmd") ci --no-audit --no-fund
            if ($LASTEXITCODE -ne 0) { throw "前端依赖安装失败。" }
        } finally { Pop-Location }
    } finally { $env:PATH = $previousPath }
}
# 可复用已经运行的 Nacos；无现有实例时使用 Docker 编排。
try { $nacos = Invoke-WebRequest 'http://127.0.0.1:8848/nacos/actuator/health' -UseBasicParsing -TimeoutSec 3 } catch { $nacos = $null }
if ($null -eq $nacos -or $nacos.StatusCode -ne 200) {
    docker compose up -d nacos
    if ($LASTEXITCODE -ne 0) { throw "Nacos 启动失败，请检查 Docker 或先启动本机 Nacos 3.1.1。" }
    $deadline = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $deadline) {
        try { $nacos = Invoke-WebRequest 'http://127.0.0.1:8848/nacos/actuator/health' -UseBasicParsing -TimeoutSec 3; if ($nacos.StatusCode -eq 200) { break } } catch { }
        Start-Sleep -Seconds 2
    }
    if ($null -eq $nacos -or $nacos.StatusCode -ne 200) { throw "Nacos 尚未就绪。" }
}
$java = (Get-Command java.exe).Source
$version = ([xml](Get-Content -LiteralPath (Join-Path $root "pom.xml") -Raw)).project.version
$records = @()
foreach ($module in $flowYogaModules) {
    $jarPath = Join-Path $root "$module\target\$module-$version.jar"
    if (-not (Test-Path -LiteralPath $jarPath)) { throw "缺少服务 jar，请先打包。" }
    $process = Start-Process -FilePath $java -ArgumentList @('-jar', ('"{0}"' -f $jarPath)) -WorkingDirectory $root `
        -RedirectStandardOutput (Join-Path $root "logs\$module.out.log") -RedirectStandardError (Join-Path $root "logs\$module.err.log") -WindowStyle Hidden -PassThru
    try {
        $records += [ordered]@{ module=$module; processId=$process.Id; startTimeUtcTicks=$process.StartTime.ToUniversalTime().Ticks; jarPath=$jarPath }
        Write-FlowYogaProcessState $statePath $root $records
    } catch { Stop-Process -InputObject $process -ErrorAction SilentlyContinue; throw }
}
$vite = Join-Path $root "frontend\node_modules\vite\bin\vite.js"
if (-not (Test-Path -LiteralPath $vite)) { throw "缺少前端依赖，请先在 frontend 中执行 npm ci。已启动服务可用 stop-stage2.ps1 停止。" }
$frontend = Start-Process -FilePath $NodePath -ArgumentList @(('"{0}"' -f $vite)) -WorkingDirectory (Join-Path $root "frontend") `
    -RedirectStandardOutput (Join-Path $root "logs\frontend.out.log") -RedirectStandardError (Join-Path $root "logs\frontend.err.log") -WindowStyle Hidden -PassThru
try {
    [ordered]@{ processId=$frontend.Id; startTimeUtcTicks=$frontend.StartTime.ToUniversalTime().Ticks; vitePath=$vite } |
        ConvertTo-Json | Set-Content -LiteralPath $frontendStatePath -Encoding UTF8
} catch { Stop-Process -InputObject $frontend -ErrorAction SilentlyContinue; throw }
Write-Host "服务正在启动。登录入口：http://127.0.0.1:17000/login/member"
