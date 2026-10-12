$ErrorActionPreference = "Stop"
$headers = @{ "X-Request-Id" = "chain-test-001" }
$response = Invoke-WebRequest `
    -Uri "http://127.0.0.1:17080/api/course/integration/member-card" `
    -Headers $headers `
    -UseBasicParsing `
    -TimeoutSec 10

if ($response.StatusCode -ne 200) {
    throw "调用链返回 $($response.StatusCode)"
}
if ($response.Headers["X-Request-Id"] -ne "chain-test-001") {
    throw "响应没有带回请求编号"
}

$payload = $response.Content | ConvertFrom-Json
if ($payload.path -ne "gateway -> flowyoga-course -> flowyoga-member") {
    throw "路径不符合预期: $($payload.path)"
}
if ($payload.courseService -ne "flowyoga-course" -or $payload.memberCard.service -ne "flowyoga-member") {
    throw "服务名称不符合预期"
}
if ($payload.requestId -ne "chain-test-001" -or $payload.memberCard.requestId -ne "chain-test-001") {
    throw "请求编号没有从网关传到会员卡服务"
}
if (-not $payload.memberCard.probe) {
    throw "会员卡服务没有标记探针响应"
}

Write-Host $response.Content
Write-Host "调用链通过"
