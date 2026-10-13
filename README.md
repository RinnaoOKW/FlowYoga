# FlowYoga
Yagevil
单门店瑜伽馆管理系统。第一期围绕团体课程和会员次卡，支持管理员、教练和会员三类角色。

第一阶段基础与调用链已完成。第二阶段已实现三个逻辑数据库、18 张业务表、三角色登录，以及管理员会员管理和教练管理：列表、搜索、分页、新增、编辑、启用和停用均接通数据库。跨服务资料保存具备幂等回执和持久化恢复。开卡、排课和完整预约流程按后续迭代实现。

## 文档

| 文档 | 内容 |
| --- | --- |
| [功能清单](docs/01-功能清单.md) | 本期功能、明确不做的内容、验收证据 |
| [业务规则](docs/02-业务规则.md) | 排课冲突、预约取消、缺席扣次、课次占用与核对 |
| [服务边界](docs/03-服务边界.md) | 三个业务服务的数据和调用关系 |
| [开题提纲](docs/04-开题提纲.md) | 可按学校模板展开的开题提纲 |
| [技术验证](docs/05-技术验证.md) | 锁定的版本和调用链验证结果 |
| [数据库设计](docs/06-数据库设计.md) | 三个服务的数据归属、ER 图、字段字典和数据库约束 |
| [登录与接口](docs/07-登录与接口.md) | 三角色页面、演示账号、认证接口和验收方法 |
| [管理员档案管理](docs/08-管理员档案管理.md) | 会员与教练管理、接口、幂等保存与故障恢复 |

## 版本

| 组件 | 版本 |
| --- | --- |
| Java | 21 |
| Spring Boot | 4.0.0 |
| Spring Cloud | 2025.1.0 |
| Spring Cloud Alibaba | 2025.1.0.0 |
| Nacos | 3.1.1 |
| MySQL | 本机 8.0.46，最低 8.0.16 |
| MyBatis Starter | 4.0.0 |
| Vue / Vite | 3 / 7（具体依赖见前端锁文件） |
| Node.js | 20.19+ 或 22.12+；本机 22.13.0 |

这是 Spring Cloud Alibaba 2025.1 在 2026 年 10 月 12 日官方兼容表中的发布组合。

## 本地运行

需要 JDK 21、Maven 3.9、MySQL 和 Node.js。Nacos 可复用已运行的 3.1.1 实例；没有实例时脚本使用 Docker。本机实际使用 Nacos 原生发行包。当前默认 MySQL 路径为 `E:\MySQL Main`，Node 路径为 `F:\node-v22.13.0\node.exe`；换电脑时通过 `-MySqlHome`、`-NodePath` 指定安装位置。

```powershell
.\scripts\start-stage2.ps1
# 已打包、安装前端依赖时：
# .\scripts\start-stage2.ps1 -SkipBuild
.\scripts\verify-database.ps1
.\scripts\verify-chain.ps1
.\scripts\stop-stage2.ps1
```

启动后访问 [会员登录](http://127.0.0.1:17000/login/member)、[教练登录](http://127.0.0.1:17000/login/coach)、[管理员登录](http://127.0.0.1:17000/login/admin)。本地演示账号分别为 `member`、`coach`、`admin`，密码均为 `FlowYoga@123`；只在本地演示模式初始化，已有密码不会被重置。

脚本启动独立 MySQL、四个 JVM 和 Vite，保存 PID、启动时间及实际程序路径。已有启动记录时，先停止再重新启动。停止脚本核对进程身份后才停止本项目进程，不根据端口批量结束进程；MySQL 正常关闭并保留数据，Nacos 保留供复用。

Windows MySQL 数据默认放在 `%LOCALAPPDATA%/FlowYoga/<工作区哈希>/mysql` 的 ASCII 路径中。随机数据库密码、JWT 密钥与数据目录记录在忽略的 `logs/stage2-local.properties`，不要提交或分享；保存项目数据时需一并备份数据目录与本地配置。用户表通过 Flyway 自动迁移，另两个业务库由建表 SQL 初始化。

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
| Vue 前端 | 17000 |
| 项目独立 MySQL | 13306 |
| flowyoga-gateway | 17080 |
| flowyoga-user | 17081 |
| flowyoga-member | 17082 |
| flowyoga-course | 17083 |

## 下一步

继续实现课程与教室管理、次卡套餐、开卡与排课，再实现会员预约、教练到场登记及跨服务课次同步。管理员现在可以管理会员和教练档案。
