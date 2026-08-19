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
teddy_run_dir=${TEDDY_RUN_DIR:-"$teddy_home/bin"}
pidfile="$teddy_run_dir/teddy.pid"

if [ ! -f "$pidfile" ]; then
    echo "Teddy 已停止"
    exit 0
fi

pid=$(cat "$pidfile")
case "$pid" in
    ''|*[!0-9]*)
        echo "错误：PID 文件内容无效：$pidfile" >&2
        exit 1
        ;;
esac

if ! kill -0 "$pid" 2>/dev/null; then
    echo "清理失效的 PID 文件：$pidfile"
    rm -f "$pidfile"
    exit 0
fi

printf '%s: 正在停止 Teddy %s ...\n' "$(hostname)" "$pid"
kill "$pid"

stop_timeout=${TEDDY_STOP_TIMEOUT:-30}
elapsed=0
while kill -0 "$pid" 2>/dev/null; do
    if [ "$elapsed" -ge "$stop_timeout" ]; then
        echo "错误：Teddy 在 ${stop_timeout} 秒内未停止，保留 PID 文件供排查" >&2
        exit 1
    fi
    sleep 1
    elapsed=$((elapsed + 1))
done

rm -f "$pidfile"
echo "Teddy 已停止"
