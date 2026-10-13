# FlowYoga
Yagevil
单门店瑜伽馆管理系统。第一期围绕团体课程和会员次卡，支持管理员、教练和会员三类角色。

当前仓库完成的是 2026 年 10 月 12 日至 10 月 25 日这一阶段的基础：业务规则、服务边界、开题提纲，以及“接口工具 → 网关 → 课程服务 → 会员卡服务”的最小调用链。登录、数据库、页面和完整预约流程还没有开始。

## 文档

| 文档 | 内容 |
| --- | --- |
| [功能清单](docs/01-功能清单.md) | 本期功能、明确不做的内容、验收证据 |
| [业务规则](docs/02-业务规则.md) | 排课冲突、预约取消、缺席扣次、课次占用与核对 |
| [服务边界](docs/03-服务边界.md) | 三个业务服务的数据和调用关系 |
| [开题提纲](docs/04-开题提纲.md) | 可按学校模板展开的开题提纲 |
| [技术验证](docs/05-技术验证.md) | 锁定的版本和调用链验证结果 |

## 版本

| 组件 | 版本 |
| --- | --- |
| Java | 21 |
| Spring Boot | 4.0.0 |
| Spring Cloud | 2025.1.0 |
| Spring Cloud Alibaba | 2025.1.0.0 |
| Nacos | 3.1.1 |

这是 Spring Cloud Alibaba 2025.1 在 2026 年 10 月 12 日官方兼容表中的发布组合。

## 本地运行

需要 JDK 21、Maven 3.9 和 Docker。网关使用 17080，Nacos 控制台使用 8080。2026-10-12 的调用链验证里，Nacos 镜像没有拉完，实际用的是同版本 3.1.1 发行包；结果写在技术验证文档里。

```powershell
.\scripts\start-dev.ps1
.\scripts\verify-chain.ps1
.\scripts\stop-dev.ps1
```

启动脚本先执行 Maven 打包，再通过 `java -jar` 启动四个服务，把 JVM 的 PID、启动时间、jar 路径和工作区写入 `logs/dev-processes.json`。已有启动记录时，先运行停止脚本再重新启动；打包或 Docker 启动失败会立即中止。停止脚本必须同时核对工作区、PID、启动时间和实际 jar 参数，匹配后才结束对应进程，不根据端口批量结束进程。

没有记录时停止脚本不会结束任何进程。旧版 `spring-boot:run` 或 IDE 启动的服务请在原终端或 IDE 中停止，再切换到上述启动方式。进程记录位于已忽略的 `logs/` 目录，不提交到 Git。

运行脚本的安全回归检查：

```powershell
.\scripts\test-dev-processes.ps1
```

该检查使用进程、构建和 Docker 替身，验证其他工作区、复用 PID、读取失败、重复启动和部分启动失败的处理，不结束真实进程。

验证请求：

```powershell
Invoke-WebRequest http://127.0.0.1:17080/api/course/integration/member-card -Headers @{"X-Request-Id"="chain-test-001"}
```

返回结果中的课程服务和会员卡服务应带有同一个请求编号。会员卡返回的剩余次数是固定探针数据，不是真实会员卡。

直接访问会员卡服务的内部探针需要带上本地开发令牌：

```powershell
Invoke-WebRequest http://127.0.0.1:17082/internal/member-cards/probe -Headers @{"X-Internal-Token"="dev-internal-token"}
```

`dev-internal-token` 只用于本机开发。Nacos 在本机编排中关闭了鉴权，不能直接暴露到公网。

## 端口

| 服务 | 端口 |
| --- | --- |
| Nacos 控制台 | 8080 |
| Nacos 客户端 | 8848、9848 |
| flowyoga-gateway | 17080 |
| flowyoga-user | 17081 |
| flowyoga-member | 17082 |
| flowyoga-course | 17083 |

## 下一步

10 月 26 日至 11 月 15 日完成架构图、数据库设计、接口约定和三个角色的登录页面。
