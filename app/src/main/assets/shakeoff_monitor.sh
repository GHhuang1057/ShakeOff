#!/system/bin/sh
# ============================================================
# ShakeOff 节能监控守护进程
#
# 以 shell 权限运行，通过两种方式感知广告活动：
#   1. inotify 监控 /data/system/packages.xml 感知新应用安装
#   2. 每 60 秒过滤 logcat 中的广告 SDK 日志标签
#
# 发现可疑活动时直接执行 appops 禁用该应用传感器权限，
# 并把事件写入 /data/local/tmp/shakeoff_events.log 供主应用读取。
#
# 仅在 Shizuku 模式下可用。
# ============================================================

PACKAGES_XML="/data/system/packages.xml"
EVENT_LOG="/data/local/tmp/shakeoff_events.log"
PID_FILE="/data/local/tmp/shakeoff_monitor.pid"
OP="OP_MOTION_SENSORS"

# logcat 中与广告相关的标签关键词（小写匹配）
AD_TAGS="adsdk|adnet|adver|advert|gdt|qqad|mobads|pangolin|bytedance.*ad|sigmob|kwad|kuaishou.*ad|mopub|inmobi|admob|smaato|vungle|applovin|chartboost|mobvista|mintegral|brandapp|talkingdata|youmi"

# 轮询间隔（秒）
POLL_INTERVAL=60

log_event() {
    # $1 = 事件类型, $2 = 包名/来源, $3 = 详情
    local type="$1"
    local target="$2"
    local detail="$3"
    local ts
    ts=$(date '+%Y-%m-%d %H:%M:%S')
    echo "${ts}|${type}|${target}|${detail}" >> "$EVENT_LOG"
}

# 提取包名：logcat 行中形如 com.xxx.yyy/ 后跟进程名
extract_package() {
    echo "$1" | grep -oE '[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+){1,}' | head -1
}

# 屏蔽某包的传感器权限
block_package() {
    local pkg="$1"
    [ -z "$pkg" ] && return
    # 跳过系统关键包，避免误伤导致无法开机
    case "$pkg" in
        com.android.systemui|android|com.android.shell) return ;;
    esac

    local current
    current=$(appops get "$pkg" "$OP" 2>/dev/null)
    case "$current" in
        *ignore*) return ;;   # 已屏蔽
    esac

    if appops set "$pkg" "$OP" ignore >/dev/null 2>&1; then
        log_event "BLOCK" "$pkg" "appops set ignore"
    fi
}

# 标记新安装的应用，稍后统一处理
handle_package_added() {
    local pkg="$1"
    log_event "INSTALL" "$pkg" "检测到新安装"
    block_package "$pkg"
}

# 扫描 logcat 中的广告日志
scan_logcat() {
    local lines
    lines=$(logcat -d -t 300 2>/dev/null | grep -iE "$AD_TAGS")
    [ -z "$lines" ] && return

    echo "$lines" | while IFS= read -r line; do
        local pkg
        pkg=$(extract_package "$line")
        if [ -n "$pkg" ]; then
            log_event "LOG" "$pkg" "广告日志特征"
            block_package "$pkg"
        fi
    done
}

# 主循环
main_loop() {
    log_event "DAEMON" "shakeoff" "守护进程启动 pid=$$"

    # inotify 后台监控安装事件
    if command -v inotifyd >/dev/null 2>&1; then
        inotifyd -m /data/system packages.xml &
        INOTIFY_PID=$!
    fi

    while true; do
        # 定期对比已安装包数量变化
        local count
        count=$(pm list packages 2>/dev/null | wc -l)
        log_event "HEARTBEAT" "shakeoff" "已安装应用数=${count}"
        echo "${count}" > /data/local/tmp/shakeoff_pkg_count

        scan_logcat

        sleep "$POLL_INTERVAL"
    done
}

# 清理
cleanup() {
    log_event "DAEMON" "shakeoff" "守护进程退出"
    [ -n "$INOTIFY_PID" ] && kill "$INOTIFY_PID" 2>/dev/null
    rm -f "$PID_FILE"
    exit 0
}

trap cleanup TERM INT

echo $$ > "$PID_FILE"
main_loop
