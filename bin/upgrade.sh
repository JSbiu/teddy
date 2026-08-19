#!/bin/sh

set -u

operation=${1:-}
if [ "$#" -gt 1 ]; then
    echo "用法：$0 [--preflight|--rollback-legacy]" >&2
    exit 1
fi
case "$operation" in
    ''|--preflight|--rollback-legacy)
        ;;
    *)
        echo "用法：$0 [--preflight|--rollback-legacy]" >&2
        exit 1
        ;;
esac

fail() {
    echo "错误：$*" >&2
    exit 1
}

case "$(uname)" in
    Linux)
        bin_absolute_path=$(readlink -f "$(dirname "$0")")
        ;;
    *)
        bin_absolute_path=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
        ;;
esac

new_release=$(CDPATH= cd -- "$bin_absolute_path/.." && pwd)
releases_root=$(CDPATH= cd -- "$new_release/.." && pwd)
service_root=${TEDDY_SERVICE_ROOT:-"$(dirname "$releases_root")"}
service_root=$(CDPATH= cd -- "$service_root" && pwd)

case "$new_release/" in
    "$service_root/releases/"*/)
        ;;
    *)
        fail "新版本必须解压到 $service_root/releases/ 下，当前路径为 $new_release"
        ;;
esac

for required_file in teddy.jar bin/start.sh bin/stop.sh; do
    if [ ! -f "$new_release/$required_file" ]; then
        fail "发布目录缺少 $required_file"
    fi
done

for required_script in bin/start.sh bin/stop.sh; do
    if ! sh -n "$new_release/$required_script"; then
        fail "发布目录脚本语法无效：$required_script"
    fi
done

if [ ! -d "$new_release/lib" ]; then
    fail "发布目录缺少 lib 依赖目录"
fi

env_file=${TEDDY_ENV_FILE:-"$service_root/shared/teddy.env"}
if [ ! -f "$env_file" ]; then
    fail "找不到共享环境文件 $env_file"
fi

set -a
. "$env_file"
set +a
export TEDDY_ENV_FILE="$env_file"

teddy_conf_dir=${TEDDY_CONF_DIR:-"$new_release/conf"}
teddy_config_file=${TEDDY_CONFIG_FILE:-"$teddy_conf_dir/teddy.properties"}
spring_config_file=${TEDDY_SPRING_CONFIG_FILE:-"$teddy_conf_dir/application.properties"}

if [ ! -f "$teddy_config_file" ]; then
    fail "找不到 Teddy 配置文件 $teddy_config_file"
fi
if [ ! -f "$spring_config_file" ] && {
    [ -z "${TEDDY_DATASOURCE_URL:-}" ] ||
    [ -z "${TEDDY_DATASOURCE_USERNAME:-}" ] ||
    [ -z "${TEDDY_DATASOURCE_PASSWORD:-}" ];
}; then
    fail "请提供 $spring_config_file 或 TEDDY_DATASOURCE_* 环境变量"
fi

if ! command -v curl >/dev/null 2>&1; then
    fail "找不到 curl，无法执行健康检查"
fi

health_url=${TEDDY_HEALTH_URL:-"http://127.0.0.1:${TEDDY_SERVER_PORT:-18081}/system/health"}
health_timeout=${TEDDY_HEALTH_TIMEOUT:-60}
case "$health_timeout" in
    ''|*[!0-9]*)
        fail "TEDDY_HEALTH_TIMEOUT 必须是非负整数"
        ;;
esac

stop_timeout=${TEDDY_STOP_TIMEOUT:-30}
case "$stop_timeout" in
    ''|*[!0-9]*)
        fail "TEDDY_STOP_TIMEOUT 必须是非负整数"
        ;;
esac

current_link="$service_root/current"
previous_link="$service_root/previous"

if [ -e "$current_link" ] && [ ! -L "$current_link" ]; then
    fail "$current_link 必须是符号链接或不存在"
fi
if [ -e "$previous_link" ] && [ ! -L "$previous_link" ]; then
    fail "$previous_link 必须是符号链接或不存在"
fi

switch_link() {
    target=$1
    link=$2
    temporary_link="${link}.new.$$"
    rm -f "$temporary_link"
    ln -s "$target" "$temporary_link" || return 1
    mv -Tf "$temporary_link" "$link"
}

remove_current_link() {
    if [ -L "$current_link" ]; then
        rm -f "$current_link"
    fi
}

legacy_pidfile="$service_root/bin/teddy.pid"
legacy_pid=

legacy_process_is_teddy() {
    candidate_pid=$1

    if ! kill -0 "$candidate_pid" 2>/dev/null; then
        return 1
    fi

    case "$(uname)" in
        Linux)
            candidate_cmdline="/proc/$candidate_pid/cmdline"
            if [ ! -r "$candidate_cmdline" ]; then
                return 1
            fi
            candidate_command=$(tr '\000' ' ' < "$candidate_cmdline")
            case "$candidate_command" in
                *"$service_root/"*teddy.jar*)
                    ;;
                *)
                    return 1
                    ;;
            esac
            ;;
    esac

    return 0
}

stop_legacy_teddy() {
    if [ ! -f "$legacy_pidfile" ]; then
        echo "错误：旧部署 PID 文件不存在：$legacy_pidfile" >&2
        return 1
    fi

    stop_pid=$(cat "$legacy_pidfile")
    case "$stop_pid" in
        ''|*[!0-9]*)
            echo "错误：旧部署 PID 文件内容无效：$legacy_pidfile" >&2
            return 1
            ;;
    esac

    if [ "$stop_pid" != "$legacy_pid" ]; then
        echo "错误：旧部署 PID 在预检查后发生变化，拒绝停止" >&2
        return 1
    fi
    if ! legacy_process_is_teddy "$stop_pid"; then
        echo "错误：PID=$stop_pid 不再是 $service_root 下的 Teddy 进程" >&2
        return 1
    fi

    printf '%s: 正在安全停止旧 Teddy %s ...\n' "$(hostname)" "$stop_pid"
    if ! kill "$stop_pid"; then
        echo "错误：无法向旧 Teddy PID=$stop_pid 发送 TERM" >&2
        return 1
    fi

    elapsed=0
    while kill -0 "$stop_pid" 2>/dev/null; do
        if [ "$elapsed" -ge "$stop_timeout" ]; then
            echo "错误：旧 Teddy 在 ${stop_timeout} 秒内未停止，保留 PID 文件供排查" >&2
            return 1
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done

    rm -f "$legacy_pidfile"
    echo "旧 Teddy 已停止"
    return 0
}

wait_for_legacy_teddy() {
    elapsed=0
    while [ "$elapsed" -le "$health_timeout" ]; do
        if [ -f "$legacy_pidfile" ]; then
            started_pid=$(cat "$legacy_pidfile")
            case "$started_pid" in
                ''|*[!0-9]*)
                    ;;
                *)
                    if legacy_process_is_teddy "$started_pid"; then
                        sleep 2
                        if legacy_process_is_teddy "$started_pid"; then
                            return 0
                        fi
                    fi
                    ;;
            esac
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done
    return 1
}

wait_for_health() {
    elapsed=0
    while [ "$elapsed" -le "$health_timeout" ]; do
        if curl --fail --silent --show-error --max-time 3 "$health_url" >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done
    return 1
}

if [ "$operation" = "--rollback-legacy" ]; then
    if [ ! -L "$current_link" ]; then
        fail "当前没有可回滚的版本链接"
    fi
    active_release=$(readlink -f "$current_link")
    if [ -z "$active_release" ] || [ "$active_release" != "$new_release" ]; then
        fail "必须从 current 指向的版本执行旧目录回滚"
    fi
    if [ ! -f "$service_root/teddy.jar" ] || [ ! -x "$service_root/bin/start.sh" ]; then
        fail "保留的旧目录部署不完整，无法回滚"
    fi
    if ! sh -n "$service_root/bin/start.sh"; then
        fail "旧部署 bin/start.sh 语法无效，无法回滚"
    fi
    if [ -e "$legacy_pidfile" ]; then
        fail "旧部署 PID 文件仍然存在，请先人工确认：$legacy_pidfile"
    fi

    "$new_release/bin/stop.sh" || fail "当前 Teddy 停止失败，未执行旧目录回滚"
    if ! remove_current_link; then
        "$new_release/bin/start.sh" >/dev/null 2>&1 || true
        fail "current 链接移除失败，已尝试恢复当前版本"
    fi

    legacy_start_result=failed
    if "$service_root/bin/start.sh" && wait_for_legacy_teddy; then
        legacy_start_result=started
    fi
    if [ "$legacy_start_result" = "started" ]; then
        echo "已回滚到旧目录部署：$service_root"
        exit 0
    fi

    echo "旧目录版本启动失败，开始恢复 current 版本" >&2
    if [ -f "$legacy_pidfile" ]; then
        rollback_legacy_pid=$(cat "$legacy_pidfile")
        case "$rollback_legacy_pid" in
            ''|*[!0-9]*)
                ;;
            *)
                if legacy_process_is_teddy "$rollback_legacy_pid"; then
                    legacy_pid=$rollback_legacy_pid
                    stop_legacy_teddy || fail "旧目录进程状态异常，拒绝同时启动 current 版本"
                fi
                ;;
        esac
    fi

    switch_link "$new_release" "$current_link" || fail "无法恢复 current 链接"
    "$new_release/bin/start.sh" || fail "current 版本重新启动失败"
    wait_for_health || fail "current 版本已重启但健康检查失败"
    fail "旧目录回滚失败，已恢复 current 版本"
fi

old_mode=none
old_release=

if [ -L "$current_link" ]; then
    old_release=$(readlink -f "$current_link")
    if [ -z "$old_release" ] || [ ! -d "$old_release" ]; then
        fail "current 链接目标无效"
    fi
    if [ "$old_release" = "$new_release" ]; then
        echo "版本已经激活：$new_release"
        exit 0
    fi
    old_mode=release
    for old_script in bin/start.sh bin/stop.sh; do
        if [ ! -x "$old_release/$old_script" ]; then
            fail "当前版本缺少可执行脚本：$old_script"
        fi
        if ! sh -n "$old_release/$old_script"; then
            fail "当前版本脚本语法无效：$old_script"
        fi
    done
elif [ -f "$service_root/teddy.jar" ] || [ -d "$service_root/bin" ]; then
    if [ "${TEDDY_ALLOW_LEGACY_MIGRATION:-0}" != "1" ]; then
        fail "检测到旧目录部署；首次迁移请确认 PID 后设置 TEDDY_ALLOW_LEGACY_MIGRATION=1"
    fi
    if [ ! -x "$service_root/bin/start.sh" ]; then
        fail "旧部署缺少可执行的 bin/start.sh，无法保证回滚"
    fi
    if ! sh -n "$service_root/bin/start.sh"; then
        fail "旧部署 bin/start.sh 语法无效，无法保证回滚"
    fi
    if [ ! -f "$legacy_pidfile" ]; then
        fail "旧部署缺少 bin/teddy.pid，请人工确认进程后再迁移"
    fi
    legacy_pid=$(cat "$legacy_pidfile")
    case "$legacy_pid" in
        ''|*[!0-9]*)
            fail "旧部署 bin/teddy.pid 内容无效"
            ;;
    esac
    if ! legacy_process_is_teddy "$legacy_pid"; then
        fail "旧部署 PID=$legacy_pid 不是存活的 Teddy 进程，拒绝迁移"
    fi
    old_release="$service_root"
    old_mode=legacy
fi

if [ "$operation" = "--preflight" ]; then
    echo "预检查通过"
    echo "新版本：$new_release"
    echo "当前模式：$old_mode"
    echo "共享环境：$env_file"
    echo "健康检查：$health_url"
    exit 0
fi

case "$old_mode" in
    release)
        "$old_release/bin/stop.sh" || fail "旧 Teddy 停止失败，未切换版本"
        ;;
    legacy)
        stop_legacy_teddy || fail "旧 Teddy 停止失败，未切换版本"
        ;;
esac

switch_link "$new_release" "$current_link" || fail "current 链接切换失败"

if ! "$new_release/bin/start.sh"; then
    start_result=failed
else
    start_result=started
fi

if [ "$start_result" = "started" ] && wait_for_health; then
    if [ "$old_mode" = "release" ]; then
        switch_link "$old_release" "$previous_link" || fail "新版本已启动，但 previous 链接更新失败"
    fi
    echo "升级成功：$new_release"
    echo "健康检查：$health_url"
    exit 0
fi

echo "新版本启动或健康检查失败，开始回滚" >&2
"$new_release/bin/stop.sh" >/dev/null 2>&1 || true

case "$old_mode" in
    release)
        switch_link "$old_release" "$current_link" || fail "无法恢复旧 current 链接"
        "$old_release/bin/start.sh" || fail "旧版本重新启动失败"
        wait_for_health || fail "旧版本已重启但健康检查失败"
        ;;
    legacy)
        remove_current_link
        "$old_release/bin/start.sh" || fail "旧目录版本重新启动失败"
        ;;
    none)
        remove_current_link
        ;;
esac

fail "升级失败，已恢复升级前版本"
