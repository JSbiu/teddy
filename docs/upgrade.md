# Teddy 升级与验收

跨版本的升级手册。执行时把 `<版本>` 替换为实际版本号，例如 `1.2.1`。

## 开始前：设置变量

下文用 `$teddy_root` 表示部署根目录（`releases` 与 `shared` 的父目录），`$teddy_release` 表示本次要升级到的版本目录，`$staging` 表示放置发布包的上传暂存目录，`$acceptance_root` 表示本次验收的临时目录。**这些变量必须先导出再执行后续命令**：它们由 shell 在命令执行前展开，未设置时会静默退化成 `/current/bin/acceptance.sh`、`/before` 这类错误路径，而不是报"变量未定义"。

    export teddy_root=<你的部署根目录>
    export teddy_release="$teddy_root/releases/teddy-<版本>"
    export staging=<你的上传暂存目录>
    export acceptance_root=/var/tmp/teddy-<版本>-$(date +%Y%m%d-%H%M%S)
    mkdir -p "$acceptance_root"

`$staging` 只需满足：部署用户可读、**不在 `releases/` 与 `shared/` 之内**。上传工具（`rz`、`scp`、`sftp` 等）落到哪个目录由各自的机制决定，上传前先确认当前目录。

不知道部署根目录时，从运行中的进程反查。进程里的路径形如 `<部署根目录>/releases/teddy-<版本>/teddy.jar`，去掉末尾三级即是根目录：

    ps -ef | grep -F teddy.jar | grep -v grep

设置后先确认部署根目录成立：

    ls "$teddy_root/current/bin/acceptance.sh"

`$teddy_release` 指向新解压的版本目录，要到"发布包"一节解压完成后才存在。

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

把 `teddy-<版本>-release.tar.gz` 与 `SHA256SUMS` 上传到 `$staging`（部署用户可读，且不在 `releases/`、`shared/` 之内），然后在服务器校验选中的包、解压到 `releases/`、确认新版本目录成立：

    cd "$staging"
    grep -F 'teddy-<版本>-release.tar.gz' SHA256SUMS | sha256sum -c -
    umask 022
    tar -xzf teddy-<版本>-release.tar.gz -C "$teddy_root/releases/"
    ls -l "$teddy_release/bin/upgrade.sh"

最后一行权限应为 `-rwxr-xr-x`。若解压前 shell 里留着 077 之类的 `umask`，脚本会变成 700，与包内记录的 0755 不符；此时删掉版本目录、设好 `umask` 重新解压即可。

1.2.1 起的 `SHA256SUMS` 为 LF 行尾，可直接校验。更早的发布包使用 CRLF，需要在管道中加 `| tr -d '\r'` 才能通过。

**升级前后的验收快照都要用新包里的脚本**（`"$teddy_release/bin/acceptance.sh"`），不要用 `$teddy_root/current/bin/acceptance.sh`：`current` 在升级前仍指向旧版本，旧包里的脚本可能有新包已修复的缺陷。1.2.1 之前的脚本在登录时提交的是 `username`，而后端读取 `userName`，因此登录必然被拒——用旧脚本会误判成密码错误。

正式版本号、标签和生产升级须经用户批准。开发产物带 `-SNAPSHOT` 时不得用于生产。

## 升级前只读快照

验收脚本只调用健康接口、登录/退出、任务列表和 `yarn application -status`，不调用任务提交、停止或重启接口。

先准备一个权限为 600、末尾无换行的临时密码文件。**文件内容必须是登录 Teddy 网页时输入的明文密码**，不是共享配置里 `auth.password-hash` 的哈希值——哈希格式为 `pbkdf2-sha256$迭代数$盐$密钥`，长度约 90 字节，误填会让登录一直被拒。

**第 1 步，输入密码。** 执行后光标会停住等你输入，**屏幕上不显示任何字符**，输完按回车：

    read -r -s -p 'Teddy 登录密码: ' TEDDY_ACCEPTANCE_PASSWORD; echo

**第 2 步，写入文件并检查**：

    saved_umask=$(umask); umask 077
    printf '%s' "$TEDDY_ACCEPTANCE_PASSWORD" > /var/tmp/teddy-acceptance-password
    umask "$saved_umask"; unset TEDDY_ACCEPTANCE_PASSWORD
    ls -l /var/tmp/teddy-acceptance-password

最后一行应显示 `-rw-------`，且长度等于你密码的字符数。

**不要把两步整块粘贴**：`read` 从标准输入读取，粘贴进来的后续命令会被当成密码内容吃掉，于是文件里存进的是一串命令文本。

`umask` 用完必须还原：它会持续影响该 shell 后续创建的所有文件，包括解压出来的发布包（会把 `bin/*.sh` 变成 700，而不是包内的 0755）。

采集升级前快照。`TEDDY_EXPECT_APPLICATION_COUNT` 应填最近一次验收确认的基线数量；实际数量已变化时先查清原因再继续：

    TEDDY_AUTH_PASSWORD_FILE=/var/tmp/teddy-acceptance-password \
    TEDDY_EXPECT_APPLICATION_COUNT=<数量> \
    "$teddy_release/bin/acceptance.sh" capture "$acceptance_root/before"

脚本报"登录被拒"时，先确认执行的是新包里的脚本（见"发布包"一节），再怀疑密码或共享配置里的 `auth.username`。

## 预检与升级

    "$teddy_release/bin/upgrade.sh" --preflight
    "$teddy_release/bin/upgrade.sh"

预检失败时不要绕过，先修复共享配置、目录权限或发布包问题再重新运行。

## 升级后验收

    TEDDY_AUTH_PASSWORD_FILE=/var/tmp/teddy-acceptance-password \
    TEDDY_EXPECT_APPLICATION_COUNT=<数量> \
    "$teddy_release/bin/acceptance.sh" capture "$acceptance_root/after"

    TEDDY_EXPECT_RELEASE=teddy-<版本> \
    "$teddy_release/bin/acceptance.sh" compare \
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
