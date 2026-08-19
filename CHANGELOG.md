# Changelog

本项目遵循 Semantic Versioning，正式版本使用 `vMAJOR.MINOR.PATCH` Git 标签。

## 1.1.0 - Unreleased

### Added

- 支持从共享目录加载 Teddy 与 Spring Boot 运行配置。
- 支持通过环境变量提供 MySQL 数据源配置。
- 增加浏览器端 YARN 代理地址配置接口。
- 增加安全的 PID 检查和有超时的停止流程。

### Changed

- 发布包只携带无敏感值的配置示例。
- Linux 启停脚本固定使用 LF 行尾。
