#!/bin/sh

set -u

operation=${1:-}
case "$operation" in
    capture)
        if [ "$#" -ne 2 ]; then
            echo "Usage: $0 capture SNAPSHOT_DIRECTORY" >&2
            exit 1
        fi
        ;;
    compare)
        if [ "$#" -ne 3 ]; then
            echo "Usage: $0 compare BEFORE_DIRECTORY AFTER_DIRECTORY" >&2
            exit 1
        fi
        ;;
    *)
        echo "Usage: $0 capture SNAPSHOT_DIRECTORY | compare BEFORE_DIRECTORY AFTER_DIRECTORY" >&2
        exit 1
        ;;
esac

fail() {
    echo "ERROR: $*" >&2
    exit 1
}

get_property() {
    awk -v wanted="$2" '
        {
            pos = index($0, "=")
            if (pos == 0) next
            key = substr($0, 1, pos - 1)
            gsub(/^[ \t]+|[ \t]+$/, "", key)
            if (key == wanted) {
                value = substr($0, pos + 1)
                sub(/^[ \t]+/, "", value)
                sub(/[ \t\r]+$/, "", value)
                print value
                exit
            }
        }
    ' "$1"
}

case "$(uname)" in
    Linux)
        bin_absolute_path=$(readlink -f "$(dirname "$0")")
        ;;
    *)
        bin_absolute_path=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
        ;;
esac
release_root=$(CDPATH= cd -- "$bin_absolute_path/.." && pwd)
releases_root=$(CDPATH= cd -- "$release_root/.." && pwd)
service_root=${TEDDY_SERVICE_ROOT:-"$(dirname "$releases_root")"}
service_root=$(CDPATH= cd -- "$service_root" && pwd)

env_file=${TEDDY_ENV_FILE:-"$service_root/shared/teddy.env"}
if [ -f "$env_file" ]; then
    set -a
    . "$env_file"
    set +a
fi

if [ "$operation" = "compare" ]; then
    before_directory=$2
    after_directory=$3
    [ -d "$before_directory" ] || fail "before snapshot does not exist: $before_directory"
    [ -d "$after_directory" ] || fail "after snapshot does not exist: $after_directory"
    [ -f "$before_directory/application-ids.txt" ] ||
        fail "before snapshot has no application-ids.txt"
    [ -f "$after_directory/application-ids.txt" ] ||
        fail "after snapshot has no application-ids.txt"

    if ! cmp -s "$before_directory/application-ids.txt" "$after_directory/application-ids.txt"; then
        echo "ApplicationId mismatch:" >&2
        diff -u "$before_directory/application-ids.txt" "$after_directory/application-ids.txt" >&2 || true
        exit 1
    fi
    if ! grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' "$after_directory/health.json"; then
        fail "after snapshot is not healthy"
    fi

    expected_release=${TEDDY_EXPECT_RELEASE:-}
    if [ -n "$expected_release" ]; then
        actual_release=$(sed -n 's/^release=//p' "$after_directory/metadata.txt")
        if [ "$(basename "$actual_release")" != "$expected_release" ]; then
            fail "expected release $expected_release but found $actual_release"
        fi
    fi

    application_count=$(wc -l < "$after_directory/application-ids.txt" | tr -d ' ')
    echo "Acceptance comparison passed"
    echo "ApplicationIds unchanged: $application_count"
    echo "After release: $(sed -n 's/^release=//p' "$after_directory/metadata.txt")"
    exit 0
fi

snapshot_directory=$2
if [ -e "$snapshot_directory" ]; then
    fail "snapshot path already exists: $snapshot_directory"
fi

for command_name in curl grep sed sort uniq wc mktemp; do
    command -v "$command_name" >/dev/null 2>&1 ||
        fail "required command is unavailable: $command_name"
done

teddy_conf_dir=${TEDDY_CONF_DIR:-"$service_root/shared/conf"}
teddy_config_file=${TEDDY_CONFIG_FILE:-"$teddy_conf_dir/teddy.properties"}
[ -f "$teddy_config_file" ] || fail "Teddy configuration is unavailable: $teddy_config_file"

health_url=${TEDDY_HEALTH_URL:-"http://127.0.0.1:${TEDDY_SERVER_PORT:-18081}/system/health"}
base_url=${TEDDY_BASE_URL:-$(printf '%s' "$health_url" | sed 's#/system/health$##')}
auth_username=${TEDDY_AUTH_USERNAME:-$(get_property "$teddy_config_file" auth.username)}
skip_login=${TEDDY_SKIP_LOGIN:-0}
case "$skip_login" in
    0|1) ;;
    *) fail "TEDDY_SKIP_LOGIN must be 0 or 1" ;;
esac
if [ "$skip_login" -eq 0 ]; then
    [ -n "$auth_username" ] || fail "authentication username is unavailable"
fi

cookie_file=${TEDDY_COOKIE_FILE:-}
owned_cookie=0
password_file=
owned_password=0

# 提前注册，使登录失败等中途退出也能清掉临时文件。
cleanup() {
    if [ "$owned_password" -eq 1 ]; then
        rm -f "$password_file"
    fi
    if [ "$owned_cookie" -eq 1 ]; then
        curl --silent --show-error --cookie "$cookie_file"             -X POST "$base_url/teddy/logout" >/dev/null 2>&1 || true
        rm -f "$cookie_file"
    fi
}
trap cleanup EXIT HUP INT TERM

if [ "$skip_login" -eq 1 ]; then
    cookie_file=
elif [ -z "$cookie_file" ]; then
    password_file=${TEDDY_AUTH_PASSWORD_FILE:-}
    password_value=${TEDDY_AUTH_PASSWORD:-}
    if [ -n "$password_file" ]; then
        [ -f "$password_file" ] ||
            fail "password file does not exist: $password_file"
    elif [ -n "$password_value" ]; then
        # 先落到仅本人可读的临时文件，再按原方式交给 curl 的 --data-urlencode @file，
        # 免得明文出现在 curl 的命令行参数或子进程环境里。
        password_file=$(mktemp "${TMPDIR:-/tmp}/teddy-acceptance-password.XXXXXX")
        chmod 600 "$password_file"
        printf '%s' "$password_value" >"$password_file"
        owned_password=1
        unset TEDDY_AUTH_PASSWORD password_value
    else
        fail "set TEDDY_AUTH_PASSWORD_FILE or TEDDY_AUTH_PASSWORD (or TEDDY_COOKIE_FILE)"
    fi
    cookie_file=$(mktemp "${TMPDIR:-/tmp}/teddy-acceptance-cookie.XXXXXX")
    chmod 600 "$cookie_file"
    owned_cookie=1

    login_response=$(curl --fail --silent --show-error         --cookie-jar "$cookie_file"         --data-urlencode "userName=$auth_username"         --data-urlencode "password@$password_file"         "$base_url/teddy/login") ||
        fail "Teddy login request failed"
    printf '%s' "$login_response" |
        grep -Eq '"state"[[:space:]]*:[[:space:]]*"success"' ||
        fail "Teddy login was rejected"
else
    [ -f "$cookie_file" ] || fail "cookie file does not exist"
fi

health_response=$(curl --fail --silent --show-error "$health_url") ||
    fail "Teddy health check failed"
printf '%s' "$health_response" |
    grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' ||
    fail "Teddy health response is not UP"

if [ -n "$cookie_file" ]; then
    jobs_response=$(curl --fail --silent --show-error     --cookie "$cookie_file" "$base_url/job/list?page=1&size=500") ||
        fail "Teddy job list request failed"
else
    jobs_response=$(curl --fail --silent --show-error     "$base_url/job/list?page=1&size=500") ||
        fail "Teddy job list request failed"
fi
printf '%s' "$jobs_response" |
    grep -Eq '"state"[[:space:]]*:[[:space:]]*"success"' ||
    fail "Teddy job list response is not successful"

application_ids=$(printf '%s' "$jobs_response" |
    grep -Eo '"appId"[[:space:]]*:[[:space:]]*"application_[0-9]+_[0-9]+"' |
    sed -n 's/.*"\(application_[0-9][0-9_]*\)".*/\1/p' |
    sort)

expected_count=${TEDDY_EXPECT_APPLICATION_COUNT:-}
application_count=$(printf '%s\n' "$application_ids" |
    sed '/^$/d' |
    wc -l |
    tr -d ' ')
if [ -n "$expected_count" ] && [ "$application_count" -ne "$expected_count" ]; then
    fail "expected $expected_count applications but found $application_count"
fi

duplicate_ids=$(printf '%s\n' "$application_ids" |
    sed '/^$/d' |
    uniq -d)
[ -z "$duplicate_ids" ] || fail "duplicate ApplicationIds were returned"

teddy_run_dir=${TEDDY_RUN_DIR:-"$service_root/shared/run"}
pidfile="$teddy_run_dir/teddy.pid"
[ -f "$pidfile" ] || fail "Teddy PID file is unavailable: $pidfile"
pid=$(cat "$pidfile")
case "$pid" in
    ''|*[!0-9]*)
        fail "Teddy PID file is invalid"
        ;;
esac
kill -0 "$pid" 2>/dev/null || fail "Teddy process $pid is not running"

active_release=$release_root
if [ -L "$service_root/current" ]; then
    active_release=$(readlink -f "$service_root/current")
fi

mkdir -p "$snapshot_directory"
chmod 700 "$snapshot_directory"
printf '%s\n' "$health_response" > "$snapshot_directory/health.json"
printf '%s\n' "$application_ids" |
    sed '/^$/d' > "$snapshot_directory/application-ids.txt"
{
    printf 'captured_at_utc=%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    printf 'release=%s\n' "$active_release"
    printf 'pid=%s\n' "$pid"
    printf 'application_count=%s\n' "$application_count"
} > "$snapshot_directory/metadata.txt"
chmod 600 "$snapshot_directory/health.json"     "$snapshot_directory/application-ids.txt"     "$snapshot_directory/metadata.txt"

if [ "${TEDDY_SKIP_YARN_STATUS:-0}" != "1" ] && [ "$application_count" -gt 0 ]; then
    yarn_command=$(get_property "$teddy_config_file" yarn.command)
    yarn_command=${yarn_command:-yarn}
    command -v "$yarn_command" >/dev/null 2>&1 ||
        fail "YARN command is unavailable: $yarn_command"
    mkdir -p "$snapshot_directory/yarn-status"
    chmod 700 "$snapshot_directory/yarn-status"
    while IFS= read -r app_id; do
        [ -n "$app_id" ] || continue
        "$yarn_command" application -status "$app_id"             > "$snapshot_directory/yarn-status/$app_id.txt" 2>&1 ||
            fail "YARN status query failed for $app_id"
        chmod 600 "$snapshot_directory/yarn-status/$app_id.txt"
    done < "$snapshot_directory/application-ids.txt"
fi

echo "Acceptance snapshot captured: $snapshot_directory"
echo "Release: $active_release"
echo "ApplicationIds: $application_count"
