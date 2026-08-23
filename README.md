# teddy

Teddy 是一个面向 Spark on YARN 实时任务的轻量级管理平台，提供作业配置、提交、状态监控、告警和自动重启能力。

生产升级流程见 docs/1.2.0-upgrade.md；首次从旧目录迁移的历史说明见 docs/phase1-upgrade.md。

## 版本管理

- Maven pom.xml 是版本号的唯一来源。
- 开发版本使用 MAJOR.MINOR.PATCH-SNAPSHOT。
- 可部署版本通过 vMAJOR.MINOR.PATCH Git 标签标记。
- 每个功能或修复使用独立的 Conventional Commit，并在验证后推送。
- 正式版本号、标签和生产升级必须由用户批准。

## 运行配置

生产配置必须位于发布包之外，仓库和构建产物只保留无敏感值的示例：

- conf/teddy.properties.example：Spark、YARN、上传目录、超时、调度和鉴权。
- conf/application.properties.example：端口和 MySQL 数据源。
- conf/teddy.env.example：共享配置、日志、PID 和健康检查路径。

首次部署时，将示例复制到共享配置目录并限制权限。MySQL 配置也可以通过 TEDDY_DATASOURCE_URL、TEDDY_DATASOURCE_USERNAME 和 TEDDY_DATASOURCE_PASSWORD 环境变量提供。

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

## 更新边界

Teddy 是 Spark/YARN 的管理端。生产更新允许 Teddy 管理页面短暂停止，但不得停止或重新提交 YARN 中已经运行的 Spark application。共享配置、上传目录、日志和 PID 必须位于版本目录之外。

升级前后使用 bin/acceptance.sh 采集只读快照并对比 ApplicationId 集合。该脚本不会调用任务提交、停止、重启或 YARN kill。
