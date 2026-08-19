# 第一阶段低中断更新

## 目标与边界

第一阶段保证 YARN 中已经运行的 Spark application 不被停止或重新提交。
Teddy 管理页面允许短暂维护窗口，不承诺 HTTP 零中断。

更新脚本只停止和启动 Teddy JVM、切换版本链接并访问健康检查接口；它不会执行
`yarn application -kill`，也不会调用 Teddy 的任务启动、停止或重启接口。

## 目录结构

```text
/usr/local/service/teddy/
├── current -> releases/teddy-1.1.0
├── previous -> releases/teddy-1.0.0
├── releases/
│   └── teddy-1.1.0/
└── shared/
    ├── conf/
    │   ├── application.properties
    │   └── teddy.properties
    ├── logs/
    ├── run/
    └── teddy.env
```

运行配置、日志、PID 和上传的 Spark 业务 JAR 必须位于版本目录之外。业务 JAR
目录由共享 `teddy.properties` 的 `lib.home` 指定。

## 首次迁移准备

1. 构建发布包并随包上传 `SHA256SUMS`。
2. 在服务器上执行 `sha256sum -c --ignore-missing SHA256SUMS`。
3. 创建 `releases/` 和 `shared/{conf,logs,run}` 目录。
4. 参考发布包中的三个 `.example` 文件准备共享配置，并将配置权限限制为 `600`。
5. 仓库历史中出现过的数据库或邮箱凭据应尽快轮换；暂时无法轮换时，必须记录为
   安全风险并限制共享配置权限，不应把凭据复制到命令输出或发布包。
6. 确认旧进程 PID 与 `bin/teddy.pid` 一致。首次迁移由新版本脚本验证该 PID 的
   `/proc` 命令行、发送 `TERM` 并等待退出，不调用旧部署的 `stop.sh`。
7. 保存更新前 `/job/list` 结果或任务监控页中的全部 ApplicationId。

首次迁移时，旧部署仍位于服务根目录。将新包解压到 `releases/` 后执行：

```sh
TEDDY_SERVICE_ROOT=/usr/local/service/teddy \
TEDDY_ALLOW_LEGACY_MIGRATION=1 \
/usr/local/service/teddy/releases/teddy-1.1.0/bin/upgrade.sh --preflight

TEDDY_SERVICE_ROOT=/usr/local/service/teddy \
TEDDY_ALLOW_LEGACY_MIGRATION=1 \
/usr/local/service/teddy/releases/teddy-1.1.0/bin/upgrade.sh
```

如果旧部署缺少有效 PID 文件，脚本会拒绝自动迁移，避免在旧进程仍占用端口时启动
第二个 Teddy 实例。

## 后续更新

后续版本同样先解压到 `releases/`，然后直接运行新版本的升级脚本：

```sh
/usr/local/service/teddy/releases/teddy-1.1.1/bin/upgrade.sh --preflight
/usr/local/service/teddy/releases/teddy-1.1.1/bin/upgrade.sh
```

流程依次执行：停止当前 Teddy、原子切换 `current`、启动新 Teddy、等待数据库健康
检查。新版本在限定时间内未就绪时，脚本会恢复旧链接并重新启动旧版本。

## 回滚

版本化部署之间升级成功后，原版本记录在 `previous` 链接。首次从旧目录迁移时尚无
版本化的 `previous`；只要服务根目录中的旧 JAR、脚本和配置仍然保留，同一个回滚
命令会安全停止当前版本并恢复旧目录部署：

```sh
/usr/local/service/teddy/current/bin/rollback.sh
```

旧版本至少保留到新版本完成一个完整的状态刷新和自动重启扫描周期。首次迁移不要在
验证完成前删除服务根目录中的旧部署；后续更新不要删除 `previous` 指向的目录。

## 验收

- `curl -f http://127.0.0.1:18081/system/health` 返回成功。
- 更新前后的 Spark ApplicationId 集合完全一致。
- YARN 上的 application 启动时间没有变化。
- Teddy 状态刷新恢复，且没有重复告警或重复提交任务。
- `current` 指向新版本；首次迁移的旧目录仍可启动，后续更新的 `previous` 指向可
  启动的旧版本。
