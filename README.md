# teddy

Teddy 是一个面向 Spark on YARN 实时任务的轻量级管理平台，提供作业配置、
提交、状态监控、告警和自动重启能力。

第一阶段生产更新流程见 [低中断更新文档](docs/phase1-upgrade.md)。

## 版本管理

- Maven `pom.xml` 是版本号的唯一来源。
- 开发中的版本使用 `MAJOR.MINOR.PATCH-SNAPSHOT`。
- 可部署版本通过 `vMAJOR.MINOR.PATCH` Git 标签标记。
- 每个功能或修复使用独立的 Conventional Commit，并在验证后推送。
- 第一阶段无痛更新完成并通过升级、回滚演练后发布 `v1.1.0`。

## 运行配置

生产配置必须放在发布包之外，仓库和构建产物只保留无敏感值的示例文件：

- `conf/teddy.properties.example`：Spark、YARN、邮件、上传目录和调度周期。
- `conf/application.properties.example`：端口和 MySQL 数据源。

首次部署时，将示例复制到共享配置目录并填写实际值：

```sh
mkdir -p /usr/local/service/teddy-shared/conf
cp conf/teddy.properties.example /usr/local/service/teddy-shared/conf/teddy.properties
cp conf/application.properties.example /usr/local/service/teddy-shared/conf/application.properties
chmod 600 /usr/local/service/teddy-shared/conf/*.properties
```

启动脚本支持以下外部目录变量：

- `TEDDY_CONF_DIR`：共享配置目录。
- `TEDDY_LOG_DIR`：共享日志目录。
- `TEDDY_RUN_DIR`：共享 PID 目录。
- `TEDDY_CONFIG_FILE`：自定义 Teddy properties 文件路径。
- `TEDDY_SPRING_CONFIG_FILE`：自定义 Spring Boot properties 文件路径。

MySQL 配置也可以不写入文件，改用 `TEDDY_DATASOURCE_URL`、
`TEDDY_DATASOURCE_USERNAME` 和 `TEDDY_DATASOURCE_PASSWORD` 环境变量。

## 构建发布包

在 Windows PowerShell 中运行：

```powershell
.\tools\build-release.ps1
```

脚本执行干净构建，并在 `target/` 下生成：

- 带版本号的 thin-JAR。
- 带版本号的 ZIP 和 tar.gz 发布包。
- `SHA256SUMS` 校验清单。

发布包内部仍使用固定名称 `teddy.jar`，因此现有启停脚本不需要感知版本号。
服务器接收文件后应先执行 `sha256sum -c --ignore-missing SHA256SUMS`，
校验选中的发布包通过后再解压。旧版 `sha256sum` 不支持
`--ignore-missing` 时，可以先按文件名筛选校验清单：

```sh
grep -F 'teddy-1.1.0-SNAPSHOT-release.tar.gz' SHA256SUMS \
  | tr -d '\r' \
  | sha256sum -c -
```

## 更新目标

Teddy 是 Spark/YARN 的管理端。第一阶段更新允许 Teddy 管理页面短暂停止，
但不得停止或重新提交 YARN 中已经运行的 Spark application。生产更新必须保留
共享配置、上传目录和日志，并在切换前后核对 ApplicationId。
