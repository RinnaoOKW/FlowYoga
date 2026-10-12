$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
New-Item -ItemType Directory -Force -Path "$root\logs" | Out-Null

Write-Host "启动 Nacos"
docker compose up -d

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

$mvn = (Get-Command mvn.cmd).Source
$modules = @("flowyoga-member", "flowyoga-user", "flowyoga-course", "flowyoga-gateway")
foreach ($module in $modules) {
    Write-Host "启动 $module"
    Start-Process -FilePath $mvn `
        -ArgumentList @("-pl", $module, "-DskipTests", "spring-boot:run") `
        -WorkingDirectory $root `
        -RedirectStandardOutput "$root\logs\$module.out.log" `
        -RedirectStandardError "$root\logs\$module.err.log" `
        -WindowStyle Hidden
}

Write-Host "服务正在启动。就绪后执行 scripts\verify-chain.ps1"
