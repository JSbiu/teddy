#!/bin/sh

case "$(uname)" in
    Linux)
        bin_absolute_path=$(readlink -f "$(dirname "$0")")
        ;;
    *)
        bin_absolute_path=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
        ;;
esac

teddy_home=$(CDPATH= cd -- "$bin_absolute_path/.." && pwd)
teddy_conf_dir=${TEDDY_CONF_DIR:-"$teddy_home/conf"}
teddy_log_dir=${TEDDY_LOG_DIR:-"$teddy_home/logs"}
teddy_run_dir=${TEDDY_RUN_DIR:-"$teddy_home/bin"}
teddy_config_file=${TEDDY_CONFIG_FILE:-"$teddy_conf_dir/teddy.properties"}
spring_config_file=${TEDDY_SPRING_CONFIG_FILE:-"$teddy_conf_dir/application.properties"}

export TEDDY_HOME="$teddy_home"
export TEDDY_CONF_DIR="$teddy_conf_dir"
export TEDDY_LOG_DIR="$teddy_log_dir"
export TEDDY_RUN_DIR="$teddy_run_dir"
export TEDDY_CONFIG_FILE="$teddy_config_file"

if [ -z "${JAVA:-}" ]; then
    JAVA=$(command -v java)
fi

if [ -z "${JAVA:-}" ]; then
    echo "错误：找不到 Java，请设置 JAVA 环境变量" >&2
    exit 1
fi

if [ ! -f "$teddy_config_file" ]; then
    echo "错误：找不到 Teddy 配置文件 $teddy_config_file" >&2
    exit 1
fi

if [ ! -f "$spring_config_file" ] && {
    [ -z "${TEDDY_DATASOURCE_URL:-}" ] ||
    [ -z "${TEDDY_DATASOURCE_USERNAME:-}" ] ||
    [ -z "${TEDDY_DATASOURCE_PASSWORD:-}" ];
}; then
    echo "错误：请提供 $spring_config_file 或 TEDDY_DATASOURCE_* 环境变量" >&2
    exit 1
fi

mkdir -p "$teddy_log_dir" "$teddy_run_dir"

pidfile="$teddy_run_dir/teddy.pid"
if [ -f "$pidfile" ]; then
    pid=$(cat "$pidfile")
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        echo "错误：Teddy 已启动，PID=$pid" >&2
        exit 1
    fi
    echo "清理失效的 PID 文件：$pidfile"
    rm -f "$pidfile"
fi

if [ -f "$spring_config_file" ]; then
    "$JAVA" "-Dspring.config.additional-location=file:$spring_config_file" \
        -jar "$teddy_home/teddy.jar" -p "$teddy_config_file" \
        >>"$teddy_log_dir/teddy.log" 2>&1 &
else
    "$JAVA" -jar "$teddy_home/teddy.jar" -p "$teddy_config_file" \
        >>"$teddy_log_dir/teddy.log" 2>&1 &
fi

pid=$!
echo "$pid" > "$pidfile"
echo "Teddy 正在启动，PID=$pid，日志=$teddy_log_dir/teddy.log"
