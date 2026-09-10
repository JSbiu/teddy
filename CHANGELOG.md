# Changelog

本项目遵循 Semantic Versioning，正式版本使用 vMAJOR.MINOR.PATCH Git 标签。

## 1.2.0 - 2026-09-10

### Added

- 增加后端会话鉴权、PBKDF2-SHA256 密码哈希生成工具和安全 Cookie。
- 增加发布包结构、校验和、脚本权限、示例配置和 manifest 的自动自检。
- 增加升级前后只读验收快照及 ApplicationId 集合对比脚本。
- 增加 YARN 状态策略、ResourceManager 故障转移和隔离单元测试。
- 增加页面导航栏的部署版本号显示，取自 JAR manifest 的 `Implementation-Version`。

### Changed

- Maven 默认执行测试，发布构建不再跳过测试。
- YARN 每个任务每轮只获取一次有超时的应用快照。
- 自动重启和告警只处理明确的 FAILED 终态。
- Spark 提交等待 ApplicationId 和 YARN kill 都有可配置上限。
- 任务与 JAR 的变更接口只接受 POST，控制器日志不再输出完整任务配置。
- 页面标题与导航栏品牌名统一为 `Teddy`，去掉历史前缀。

### Fixed

- JAR 上传、删除和 Spark 提交都限制在 lib.home 的直属普通 .jar 文件。
- ResourceManager 暂时不可达时保留上次状态，不再写入伪失败状态。
- 过渡态、成功完成和人工 KILL 不再触发自动重启或故障告警。
- Spark 已启动但数据库落库失败时尝试清理该应用，避免未跟踪任务。
- 自动重启失败会基于原任务正确扣减剩余次数。
- 移除任务配置页硬编码的企业微信机器人地址，改由使用者填写，避免凭据进入仓库。

### Verification

- 全量 Maven 测试 28 项通过，3 项历史环境耦合测试明确跳过。
- 发布包自检通过。
- 隔离升级预检与旧版迁移分派通过；Windows 不支持原生符号链接时回滚分支按设计跳过。
- 验收脚本的快照、相同集合对比和 ApplicationId 变更检测均通过，未连接生产。
- 页面改动（版本号显示、品牌名、移除硬编码地址）仅做静态核对，未在运行环境做端到端验证；发布后需确认导航栏版本号正常显示。
