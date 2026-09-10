# Teddy 升级与验收

跨版本的升级手册。执行时把 `<版本>` 替换为实际版本号，例如 `1.2.1`。

## 开始前：设置变量

下文用 `$teddy_root` 表示部署根目录（`releases` 与 `shared` 的父目录），`$acceptance_root` 表示本次验收的临时目录。**这两个变量必须先导出再执行后续命令**：它们由 shell 在命令执行前展开，未设置时会静默退化成 `/current/bin/acceptance.sh`、`/before` 这类错误路径，而不是报"变量未定义"。

    export teddy_root=<你的部署根目录>
    export acceptance_root=/var/tmp/teddy-<版本>-$(date +%Y%m%d-%H%M%S)
    mkdir -p "$acceptance_root"

不知道部署根目录时，从运行中的进程反查。进程里的路径形如 `<部署根目录>/releases/teddy-<版本>/teddy.jar`，去掉末尾三级即是根目录：

    ps -ef | grep -F teddy.jar | grep -v grep

设置后先确认路径成立，再往下走：

    ls "$teddy_root/current/bin/acceptance.sh"

`acceptance.sh` 会从自身位置反推部署根目录，因此 `TEDDY_SERVICE_ROOT` 通常无需设置，`$teddy_root` 只用于拼接脚本路径。

升级脚本只停止和启动 Teddy JVM、切换 `current` / `previous` 链接并检查健康状态，**不会停止或重新提交正在运行的 Spark application**。

## 部署形态

```text
<部署根目录>/
├── current  -> releases/teddy-<版本>
├── previous -> releases/teddy-<上一版本>
├── releases/
│   └── teddy-<版本>/
└── shared/
    ├── conf/          application.properties、teddy.properties
    ├── logs/
    ├── run/
    └── teddy.env
```

运行配置、日志、PID 和上传的业务 JAR 必须位于版本目录之外，共享配置权限限制为 `600`。业务 JAR 目录由共享 `teddy.properties` 的 `lib.home` 指定，且不得位于 `releases` 之内。

## 发布包

本地构建会自动执行测试和发布包自检：

    .\tools\build-release.ps1

上传 `teddy-<版本>-release.tar.gz` 与 `SHA256SUMS` 后，在服务器校验选中的包，再解压到 `releases/`：

    grep -F 'teddy-<版本>-release.tar.gz' SHA256SUMS | sha256sum -c -

1.2.1 起的 `SHA256SUMS` 为 LF 行尾，可直接校验。更早的发布包使用 CRLF，需要在管道中加 `| tr -d '\r'` 才能通过。

正式版本号、标签和生产升级须经用户批准。开发产物带 `-SNAPSHOT` 时不得用于生产。

## 升级前只读快照

验收脚本只调用健康接口、登录/退出、任务列表和 `yarn application -status`，不调用任务提交、停止或重启接口。

先准备一个权限为 600、末尾无换行的临时密码文件，避免明文进入命令历史：

    umask 077
    read -r -s TEDDY_ACCEPTANCE_PASSWORD
    printf '%s' "$TEDDY_ACCEPTANCE_PASSWORD" > /var/tmp/teddy-acceptance-password
    unset TEDDY_ACCEPTANCE_PASSWORD

采集升级前快照。`TEDDY_EXPECT_APPLICATION_COUNT` 应填最近一次验收确认的基线数量；实际数量已变化时先查清原因再继续：

    TEDDY_AUTH_PASSWORD_FILE=/var/tmp/teddy-acceptance-password \
    TEDDY_EXPECT_APPLICATION_COUNT=<数量> \
    $teddy_root/current/bin/acceptance.sh capture "$acceptance_root/before"

## 预检与升级

    $teddy_root/releases/teddy-<版本>/bin/upgrade.sh --preflight
    $teddy_root/releases/teddy-<版本>/bin/upgrade.sh

预检失败时不要绕过，先修复共享配置、目录权限或发布包问题再重新运行。

## 升级后验收

    TEDDY_AUTH_PASSWORD_FILE=/var/tmp/teddy-acceptance-password \
    TEDDY_EXPECT_APPLICATION_COUNT=<数量> \
    $teddy_root/current/bin/acceptance.sh capture "$acceptance_root/after"

    TEDDY_EXPECT_RELEASE=teddy-<版本> \
    $teddy_root/current/bin/acceptance.sh compare \
    "$acceptance_root/before" "$acceptance_root/after"

通过条件：

- 健康接口为 UP，数据库检查为 UP。
- 升级前后的 ApplicationId 排序集合完全一致。
- 每个 ApplicationId 的 YARN 只读状态查询成功，且启动时间没有变化。
- `current` 指向新版本，`previous` 指向可回滚的上一版本。
- 无重复提交、误重启或异常告警。

验收完成后删除临时明文密码文件：

    rm -f /var/tmp/teddy-acceptance-password

## 失败处理与回滚

升级脚本健康检查失败会自动恢复 `previous`。若脚本成功但验收对比失败，保留两个快照和日志，立即执行：

    $teddy_root/current/bin/rollback.sh

回滚后重新采集快照并与 `before` 对比。不要删除 `previous` 指向的版本或旧快照，直到问题定位完成；旧版本至少保留到新版本完成一个完整的状态刷新和自动重启扫描周期。

## 历史：首次从旧目录迁移

1.1.0 之前的部署位于服务根目录而不是 `releases/`。首次迁移需要 `TEDDY_ALLOW_LEGACY_MIGRATION=1`，由脚本校验旧 PID 并发送 `TERM`，不能调用旧部署语法不完整的 `stop.sh`；缺少有效 PID 文件时脚本会拒绝自动迁移，避免第二个实例抢占端口。该环境已在 1.1.0 完成迁移，此参数后续不再需要。
