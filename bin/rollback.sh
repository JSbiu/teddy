#!/bin/sh

set -u

case "$(uname)" in
    Linux)
        bin_absolute_path=$(readlink -f "$(dirname "$0")")
        ;;
    *)
        bin_absolute_path=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
        ;;
esac

current_release=$(CDPATH= cd -- "$bin_absolute_path/.." && pwd)
releases_root=$(CDPATH= cd -- "$current_release/.." && pwd)
service_root=${TEDDY_SERVICE_ROOT:-"$(dirname "$releases_root")"}
service_root=$(CDPATH= cd -- "$service_root" && pwd)
previous_link="$service_root/previous"

if [ -L "$previous_link" ]; then
    previous_release=$(readlink -f "$previous_link")
    if [ -z "$previous_release" ] || [ ! -x "$previous_release/bin/upgrade.sh" ]; then
        echo "错误：previous 版本无效或不支持自动回滚" >&2
        exit 1
    fi

    export TEDDY_SERVICE_ROOT="$service_root"
    exec "$previous_release/bin/upgrade.sh"
fi

if [ -f "$service_root/teddy.jar" ] && [ -x "$service_root/bin/start.sh" ]; then
    export TEDDY_SERVICE_ROOT="$service_root"
    exec "$current_release/bin/upgrade.sh" --rollback-legacy
fi

echo "错误：不存在可回滚的 previous 版本或旧目录部署" >&2
exit 1
