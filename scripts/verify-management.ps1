param(
    [ValidatePattern('^demo\.[A-Za-z0-9_.-]+$')][string]$MemberLogin = 'demo.xuan',
    [ValidatePattern('^demo\.[A-Za-z0-9_.-]+$')][string]$CoachLogin = 'demo.muyu',
    [string]$DemoPassword = 'FlowYoga@123'
)
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:17080'
$admin = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$script:checks = 0
function Invoke-TestRequest {
    param([string]$Name,[string]$Path,[object]$Session,[string]$Method='GET',[object]$Body=$null,[int]$Expected=200,[switch]$Csrf,[string]$Origin=$base)
    $headers = @{}
    if ($Csrf) {
        $token = Invoke-RestMethod "$base/api/user/auth/csrf" -WebSession $Session -TimeoutSec 15
        $headers[$token.headerName] = $token.token
    }
    # PowerShell 会把请求头保留在 WebRequestSession 中；每次清除，避免“无 CSRF”测试意外沿用旧头。
    $Session.Headers.Clear()
    $arguments = @{ Uri="$Origin$Path"; Method=$Method; WebSession=$Session; Headers=$headers; UseBasicParsing=$true; TimeoutSec=15 }
    if ($null -ne $Body) { $arguments.ContentType='application/json'; $arguments.Body=($Body | ConvertTo-Json -Depth 5 -Compress) }
    $result = $null
    try { $response = Invoke-WebRequest @arguments; $actual=[int]$response.StatusCode; if ($response.Content) { $result=$response.Content | ConvertFrom-Json } }
    catch {
        if ($null -eq $_.Exception.Response) { throw }
        $actual=[int]$_.Exception.Response.StatusCode
        if ($_.ErrorDetails.Message) { try { $result=$_.ErrorDetails.Message | ConvertFrom-Json } catch {} }
    }
    if ($actual -ne $Expected) { throw "$Name：预期 HTTP $Expected，实际 $actual。$($result.message)" }
    $script:checks++; Write-Host "通过：$Name"
    return $result
}
function Find-DemoProfile {
    param([string]$Path,[string]$Login)
    $found=Invoke-TestRequest '按账号搜索档案' "$Path`?keyword=$([Uri]::EscapeDataString($Login))" $admin
    $items=@($found.items | Where-Object { $_.loginName -ceq $Login })
    if ($items.Count -ne 1 -or -not $items[0].id) { throw "请先通过管理页面建立指定 demo.* 演示档案，本脚本不会修改其他账号。" }
    return $items[0]
}
function Set-DemoStatus {
    param([string]$Path,[string]$Login,[string]$Status)
    $person=Find-DemoProfile $Path $Login
    $result=Invoke-TestRequest '账号与档案状态同步' "$Path/$($person.id)/status" $admin 'PATCH' @{requestId=[guid]::NewGuid().ToString();version=$person.version;status=$Status} -Csrf
    if ($result.state -ne 'DONE') { throw '状态修改仍在同步，不能按已完成继续验收。' }
}
Invoke-TestRequest '管理员真实登录' '/api/user/auth/login' $admin 'POST' @{loginName='admin';password='FlowYoga@123';role='ADMIN'} -Csrf | Out-Null
$fixtures = @(
    @{path='/api/member/admin/members';login=$MemberLogin;role='MEMBER';port='17082'},
    @{path='/api/course/admin/coaches';login=$CoachLogin;role='COACH';port='17083'}
)
$original = @{}
try {
    foreach ($fixture in $fixtures) {
        $person=Find-DemoProfile $fixture.path $fixture.login
        $original[$fixture.login]=$person.status
        Invoke-TestRequest '业务服务直接访问也验证管理员会话' $fixture.path $admin -Origin "http://127.0.0.1:$($fixture.port)" | Out-Null
        Invoke-TestRequest '带认证 Cookie 的写入仍要求 CSRF' "$($fixture.path)/$($person.id)/status" $admin 'PATCH' @{requestId=[guid]::NewGuid().ToString();version=$person.version;status='DISABLED'} -Expected 403 | Out-Null
        Set-DemoStatus $fixture.path $fixture.login 'ACTIVE'
        $personSession=New-Object Microsoft.PowerShell.Commands.WebRequestSession
        Invoke-TestRequest '新建账号可用本人身份登录' '/api/user/auth/login' $personSession 'POST' @{loginName=$fixture.login;password=$DemoPassword;role=$fixture.role} -Csrf | Out-Null
        Invoke-TestRequest '本人会话可读取个人资料' '/api/user/auth/me' $personSession | Out-Null
        Invoke-TestRequest '会员或教练不能经网关访问管理接口' $fixture.path $personSession -Expected 403 | Out-Null
        Invoke-TestRequest '会员或教练直接调用服务也被拒绝' $fixture.path $personSession -Expected 403 -Origin "http://127.0.0.1:$($fixture.port)" | Out-Null
        Set-DemoStatus $fixture.path $fixture.login 'DISABLED'
        Invoke-TestRequest '停用后原会话失效' '/api/user/auth/me' $personSession -Expected 401 | Out-Null
        Invoke-TestRequest '停用后不能重新登录' '/api/user/auth/login' $personSession 'POST' @{loginName=$fixture.login;password=$DemoPassword;role=$fixture.role} -Expected 401 -Csrf | Out-Null
        Set-DemoStatus $fixture.path $fixture.login 'ACTIVE'
        Invoke-TestRequest '重新启用不会恢复旧会话' '/api/user/auth/me' $personSession -Expected 401 | Out-Null
        Invoke-TestRequest '重新启用后可建立新会话' '/api/user/auth/login' $personSession 'POST' @{loginName=$fixture.login;password=$DemoPassword;role=$fixture.role} -Csrf | Out-Null
        Invoke-TestRequest '退出后原会话失效' '/api/user/auth/logout' $personSession 'POST' -Expected 204 -Csrf | Out-Null
        Invoke-TestRequest '退出不能再读取资料' '/api/user/auth/me' $personSession -Expected 401 | Out-Null
    }
    $first=Invoke-TestRequest '实际分页第一页' '/api/member/admin/members?size=1&page=1' $admin
    $second=Invoke-TestRequest '实际分页第二页' '/api/member/admin/members?size=1&page=2' $admin
    if ($first.items.Count -ne 1 -or $second.items.Count -ne 1 -or $first.items[0].id -eq $second.items[0].id) { throw '实际分页结果不正确。' }
    Invoke-TestRequest '非法分页参数被拒绝' '/api/member/admin/members?size=1000' $admin -Expected 400 | Out-Null
} finally {
    foreach ($fixture in $fixtures) {
        if ($original.ContainsKey($fixture.login)) { Set-DemoStatus $fixture.path $fixture.login $original[$fixture.login] }
    }
    Invoke-TestRequest '管理员退出' '/api/user/auth/logout' $admin 'POST' -Expected 204 -Csrf | Out-Null
}
Write-Host "管理功能真实接口验证通过：$script:checks 项。两个演示档案已恢复原启停状态。"
