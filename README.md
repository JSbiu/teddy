# teddy

Teddy 是一个面向 Spark on YARN 实时任务的轻量级管理平台，提供作业配置、提交、状态监控、告警和自动重启能力。

升级、验收与回滚流程见 [升级与验收](docs/upgrade.md)。版本变更与各版本的验收结论见 [CHANGELOG](CHANGELOG.md)；本机约定、当前进度和决策沿革见 `.local/memory.md`。

## 项目结构

Teddy 管理 Spark 作业的 JAR、提交、状态和生命周期，业务流处理代码位于被管理的作业中。本项目使用 Java/Maven、Spring Boot、Spark Launcher、MyBatis 和 MySQL；依赖版本以 `pom.xml` 为准。前端为静态 HTML、jQuery 和 Bootstrap，没有 Node.js 构建步骤；运行脚本面向 Linux。

以下 Java 路径相对 `src/main/java/com/dbay/teddy/`：

| 入口 | 职责 |
| --- | --- |
| `Application.java`、`utils/TeddyConf.java` | 应用启动和外部 Teddy 配置加载 |
| `controller/` | 作业、JAR、登录及系统接口 |
| `security/`、`config/AppConfig.java` | 密码哈希、会话管理及后端接口鉴权 |
| `service/JobService.java` | Spark 提交、停止、重启与数据库记录编排 |
| `service/YarnService.java` | 有超时和故障转移的 ResourceManager 查询 |
| `manager/` | `JobStatePolicy`、`StateRefresher`、`RestartManager`、`AlertManager` 负责状态策略、刷新、重启及告警 |
| `manager/JarResourceManager.java` | `lib.home` 下的 JAR 上传、删除和提交路径校验 |
| `entity/Job.java`、`mapper/JobMapper.java` | 任务模型、`job` 表建表语句与数据访问 |
| `entity/NotifyConfig.java`、`mapper/NotifyConfigMapper.java`、`service/NotifyConfigService.java` | 通知配置模型、`notify_config` 表访问与默认项约束 |

其他入口：`src/main/resources/static/` 保存页面与 AJAX 调用；`src/main/resources/config/application.properties` 保存无凭据的应用默认值；`conf/*.example` 保存部署示例；`src/assembly.xml` 定义发布包；`bin/` 和 `tools/` 分别提供 Linux 运维脚本与本地构建、检查工具。

## 接口与状态约定

- 业务接口使用 `{"state":"success|error","data":...}` 外层；作业和 JAR 的变更接口仅接受 POST。`/job/**`、`/jar/**` 和 `/system/client-config` 由后端会话拦截器保护。
- `/system/health` 不经会话拦截器，直接返回健康信息，正常响应包含 `status`、`database` 和 `version`，不使用业务接口外层。它会调用数据库计数检查，不能作为离线探针。
- 页面版本由 `static/js/teddy-version.js` 读取健康响应的顶层 `version` 并显示在 `#teddy-version`。版本来源是 JAR manifest 的 `Implementation-Version`，未提供 manifest 版本时回退为 `development`。
- Spark 配置采用分号分隔的 `key=value;key=value`。作业的启动参数、ApplicationId 和持久化记录由 `JobService` 协调；上传、删除和启动用到的 JAR 统一经过资源目录校验。
- `JobStatePolicy` 统一处理 YARN 状态：自动重启和故障告警仅针对明确的 `FAILED`；过渡态、成功结束、人工 KILL 和未知状态不能混作故障。查询暂时失败时保留上次状态。
- `/system/notify-config/**` 提供通知配置的查询与变更（变更接口仅接受 POST），全部由后端会话拦截器保护。配置存放在 `notify_config` 表，启动时按需创建；标记为默认的那条供任务配置页"填入默认通知"按钮使用，全局最多一条默认项。

## 版本管理

`pom.xml` 的 `<version>` 是版本号的唯一来源，其他位置不单独维护版本号。

递增判定：

| 变更性质 | 递增位 | 示例 |
| --- | --- | --- |
| 破坏性变更：不兼容的接口、配置格式或数据表结构 | MAJOR | `1.2.0` → `2.0.0` |
| 新增功能、接口、页面或配置项（**需用户明确批准**） | MINOR | `1.2.0` → `1.3.0` |
| 缺陷修复、文档、构建脚本、依赖调整 | PATCH | `1.2.0` → `1.2.1` |

版本状态：

1. 开发期使用 `X.Y.Z-SNAPSHOT`，可自由提交，版本号不承诺稳定。
2. 定版时去掉 `-SNAPSHOT`，提交后打标签 `vX.Y.Z`，再从该提交或标签构建发布包。
3. 定版后为下一个版本开启 `-SNAPSHOT` 开发（如 `1.3.0-SNAPSHOT`）；发布包只从定版提交构建，不从后续 `-SNAPSHOT` 构建。**切换下一位 `-SNAPSHOT` 应在定版包部署完成之后**：构建会清空 `target/`（发布脚本要求其中恰好只有一套产物），过早切换会让下一次构建把待部署的包覆盖掉。
4. 已打标签的版本冻结，不再修改；需要修复时递增 PATCH 发布新版本。

约束：

- 标签格式固定为 `vX.Y.Z`；不为 `-SNAPSHOT` 版本打标签，一个版本号只打一次标签。
- 定版时同步把 CHANGELOG 的 `Unreleased` 段落改为 `## X.Y.Z - YYYY-MM-DD`。
- 页面显示的版本来自 JAR manifest 的 `Implementation-Version`，只有重新构建发布后才会变化。
- 功能和修复使用范围清晰的 Conventional Commit；Git 操作遵循当前用户授权及适用的 AGENTS.md，不将历史计划视为自动提交或推送的授权。
- **MINOR 递增必须由用户明确批准**；未获批准时按 PATCH 处理。把历史遗留问题补回成本来就该有的体验，属于修复，走 PATCH。
- 版本递增、打标签和生产升级按当前会话明确授权的范围执行。

## 运行配置

生产配置必须位于发布包之外，仓库和构建产物只保留无敏感值的示例：

- conf/teddy.properties.example：Spark、YARN、上传目录、超时、调度和鉴权。
- conf/application.properties.example：端口和 MySQL 数据源。
- conf/teddy.env.example：共享配置、日志、PID 和健康检查路径。

首次部署时，将示例复制到共享配置目录并限制权限。Teddy 配置加载优先级为 `-p` 参数、`TEDDY_CONFIG_FILE` 环境变量、`conf/teddy.properties` 默认路径。MySQL 配置也可以通过 TEDDY_DATASOURCE_URL、TEDDY_DATASOURCE_USERNAME 和 TEDDY_DATASOURCE_PASSWORD 环境变量提供。

1.2.0 不再使用固定用户名、密码或 Token。先在本机生成 PBKDF2-SHA256 哈希：

    .\tools\New-TeddyPasswordHash.ps1

只把哈希写入共享 teddy.properties。不要提交或发送明文密码。

## 构建发布包

在 Windows PowerShell 中运行：

    .\tools\build-release.ps1

脚本执行干净构建和测试，并在 target 目录生成带版本号的 thin-JAR、ZIP、tar.gz 和 SHA256SUMS。随后自动检查：

- ZIP 与 tar.gz 内容一致且目录版本正确。
- bin 下脚本为 LF、shell 语法有效，tar 权限为 0755。
- 发布包只包含三个无密钥示例配置。
- teddy.jar 的 Main-Class 和 Class-Path 与 lib 目录一致。
- SHA-256 校验和全部匹配。

服务器接收文件后必须先按 SHA256SUMS 校验选中的发布包，再解压到 releases。

## 本地验证

在项目根目录执行，先按变更范围选择检查：

| 范围 | 命令与前提 |
| --- | --- |
| 相关后端逻辑 | `mvn -DskipTests=false -Dtest=JobStatePolicyTest test`（按改动替换测试类） |
| 全量隔离单元测试 | `mvn -DskipTests=false test` |
| 编译检查 | `mvn -DskipTests compile`（不代表测试或发布包验证） |
| 完整发布构建及产物自检 | `.\tools\build-release.ps1` |
| 已有产物自检 | `.\tools\Test-ReleaseArtifact.ps1`（需当前版本的发布包和校验和） |
| 升级脚本隔离演练 | `.\tools\test-upgrade.ps1`（需先生成发布 ZIP） |
| 验收脚本隔离演练 | `.\tools\test-acceptance.ps1`（需已有 `target/` 目录） |

发布检查及脚本演练依赖工具链；两个演练脚本使用 Git for Windows 风格的 POSIX 路径及 `sh`，在临时目录中模拟命令和服务。Windows 符号链接能力可能限制回滚演练，未覆盖时须单独说明。

当前隔离测试覆盖会话鉴权、资源目录边界、YARN 查询、状态策略、通知配置和控制器行为。`TaskRepositoryTest`、`EmailTest`、`SchedulerThreadPoolTest` 保留类级 `@Ignore`：分别涉及未隔离上下文、邮件/配置依赖和不退出的线程示例；不能把跳过项算作通过。实际测试数量与结果以本次输出为准。

静态页面变更需同时核对对应接口、鉴权和版本显示，在可用的本地环境操作受影响页面。使用真实配置启动 Teddy 会连接数据库并运行后台管理逻辑，应先准备隔离配置。

## 更新边界

Teddy 是 Spark/YARN 的管理端。生产更新允许 Teddy 管理页面短暂停止，但不得停止或重新提交 YARN 中已经运行的 Spark application。共享配置、上传目录、日志和 PID 必须位于版本目录之外。

升级前后使用 bin/acceptance.sh 采集只读快照并对比 ApplicationId 集合。该脚本不会调用任务提交、停止、重启或 YARN kill。
