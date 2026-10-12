$ErrorActionPreference = "Continue"
$ports = 17080, 17081, 17082, 17083
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $ports -contains $_.LocalPort } |
    Select-Object -ExpandProperty OwningProcess -Unique |
    ForEach-Object {
        Write-Host "停止进程 $_"
        Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
    }
Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
    Where-Object { $_.CommandLine -match "flowyoga-(gateway|user|member|course)" } |
    ForEach-Object {
        Write-Host "停止进程 $($_.ProcessId)"
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }
Write-Host "如需同时停止容器里的 Nacos，执行 docker compose stop。本机发行包启动的 Nacos 不会被这个脚本停止。"
