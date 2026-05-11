#!/usr/bin/env bash
#
# AgentCenter 一键启动脚本
#
# 用法:
#   ./start.sh              启动所有服务（后台运行）
#   ./start.sh --fg         前台运行（Ctrl+C 停止所有）
#   ./start.sh --stop       停止所有服务
#   ./start.sh --status     查看服务状态
#   ./start.sh --check      仅检查环境，不启动
#   ./start.sh --restart    重启所有服务
#   ./start.sh --dev        开发模式（tmux 保活 + Bridge DevTools）
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BRIDGE_DIR="$SCRIPT_DIR/agentcenter-bridge"
WEB_DIR="$SCRIPT_DIR/agentcenter-web"
LOG_DIR="$SCRIPT_DIR/.logs"
RUNTIME_WS="$SCRIPT_DIR/runtime-workspace"
DB_PATH="$BRIDGE_DIR/data/agentcenter.db"

OPENCODE_PORT=4097
BRIDGE_PORT=8080
WEB_PORT=5173

DEV_TMUX_PREFIX="agentcenter"

# ─── Colors ───────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
fail()  { echo -e "${RED}[FAIL]${NC}  $*"; }

# ─── Environment Checks ──────────────────────────────────

check_java() {
    if command -v java &>/dev/null; then
        local ver
        ver=$(java -version 2>&1 | head -1 | grep -oE '[0-9]+' | head -1)
        if [[ -z "$ver" ]] || [[ "$ver" -lt 17 ]] 2>/dev/null; then
            fail "Java 版本过低或未找到 (需要 17+)"
            return 1
        fi
        ok "Java $(java -version 2>&1 | head -1)"
    else
        fail "未找到 java。安装 JDK 17+: https://adoptium.net/"
        return 1
    fi
}

check_node() {
    if command -v node &>/dev/null; then
        local ver
        ver=$(node -v 2>/dev/null | grep -oE '[0-9]+' | head -1)
        if [[ -z "$ver" ]] || [[ "$ver" -lt 20 ]] 2>/dev/null; then
            fail "Node.js 版本过低或未找到 (需要 20+)"
            return 1
        fi
        ok "Node.js $(node -v)"
    else
        fail "未找到 node。推荐用 nvm 安装: https://github.com/nvm-sh/nvm"
        return 1
    fi
}

check_opencode() {
    if command -v opencode &>/dev/null; then
        ok "opencode $(opencode --version 2>/dev/null || echo '(version unknown)')"
    else
        fail "未找到 opencode CLI。安装: npm install -g opencode-ai"
        return 1
    fi
}

check_maven_settings() {
    local mvn="$BRIDGE_DIR/mvnw"
    if [[ ! -f "$mvn" ]]; then
        fail "未找到 Maven Wrapper: $mvn"
        return 1
    fi

    ok "Maven Wrapper 存在"

    # Maven settings
    local settings_xml="${MAVEN_OPTS##*-s }"
    if [[ -n "${MAVEN_SETTINGS:-}" ]]; then
        ok "检测到自定义 MAVEN_SETTINGS=$MAVEN_SETTINGS"
    fi

    # Enterprise settings file
    local user_settings="$HOME/.m2/settings.xml"
    if [[ -f "$user_settings" ]]; then
        if grep -q '<mirror>' "$user_settings" 2>/dev/null; then
            ok "检测到 Maven 用户 settings.xml 包含 mirror 配置（企业仓库代理）"
        else
            warn "Maven 用户 settings.xml 存在但未配置 mirror。企业环境可能需要配置私有仓库。"
        fi
    else
        warn "未找到 $user_settings"
        warn "  如果你在企业网络内，可能需要:"
        warn "    1. 创建 $user_settings 并配置企业 Maven 仓库 mirror"
        warn "    2. 或设置 MAVEN_SETTINGS 环境变量指向你的 settings.xml"
        warn "    3. 或在 pom.xml 中使用企业 profile"
    fi

    # Windows C: drive check
    local local_repo
    local_repo=$("$mvn" help:evaluate -Dexpression=settings.localRepository -q -DforceStdout 2>/dev/null || echo "")
    if [[ -n "$local_repo" && "$local_repo" == /c/* ]]; then
        warn "Maven 本地仓库在 C 盘: $local_repo"
        warn "  建议在 settings.xml 中配置 <localRepository> 到非系统盘路径"
    fi
}

check_opencode_auth() {
    if command -v opencode &>/dev/null; then
        if ! opencode auth status &>/dev/null 2>&1; then
            warn "opencode 似乎未登录。运行: opencode auth"
            return 0  # warning only, not blocking
        fi
        ok "opencode 已登录"
    fi
}

check_all() {
    echo ""
    echo "━━━ AgentCenter 环境检查 ━━━"
    echo ""
    local rc=0
    check_java       || rc=1
    check_node        || rc=1
    check_opencode    || rc=1
    check_maven_settings
    check_opencode_auth
    echo ""
    if [[ $rc -eq 0 ]]; then
        ok "环境检查全部通过"
    else
        fail "环境检查未通过，请按提示修复后重试"
        return 1
    fi
}

# ─── Service Management ───────────────────────────────────

is_port_listening() {
    lsof -i ":$1" -sTCP:LISTEN &>/dev/null
}

wait_for_port() {
    local port=$1 name=$2 timeout=${3:-30}
    local i=0
    while ! is_port_listening "$port"; do
        ((i += 1))
        if [[ $i -ge $timeout ]]; then
            fail "$name 未在 ${timeout}s 内启动 (port $port)"
            return 1
        fi
        sleep 1
    done
}

wait_for_port_to_close() {
    local port=$1 name=$2 timeout=${3:-15}
    local i=0
    while is_port_listening "$port"; do
        ((i += 1))
        if [[ $i -ge $timeout ]]; then
            fail "$name 未在 ${timeout}s 内停止 (port $port)"
            return 1
        fi
        sleep 1
    done
}

kill_port_listeners() {
    local port=$1
    local pids
    pids=$(lsof -ti ":$port" -sTCP:LISTEN 2>/dev/null || true)
    if [[ -n "$pids" ]]; then
        kill $pids 2>/dev/null || true
    fi
}

wait_for_db_release() {
    local timeout=${1:-20}
    local i=0
    if [[ ! -f "$DB_PATH" ]]; then
        return 0
    fi
    while lsof "$DB_PATH" &>/dev/null; do
        ((i += 1))
        if [[ $i -ge $timeout ]]; then
            fail "SQLite DB 仍被进程占用: $DB_PATH"
            lsof "$DB_PATH" 2>/dev/null || true
            return 1
        fi
        sleep 1
    done
}

stop_all() {
    echo ""
    info "停止所有服务..."
    if command -v tmux &>/dev/null; then
        tmux kill-session -t "$DEV_TMUX_PREFIX-opencode" 2>/dev/null || true
        tmux kill-session -t "$DEV_TMUX_PREFIX-bridge" 2>/dev/null || true
        tmux kill-session -t "$DEV_TMUX_PREFIX-web" 2>/dev/null || true
    fi
    kill_port_listeners "$WEB_PORT"
    kill_port_listeners "$BRIDGE_PORT"
    pkill -f "opencode serve" 2>/dev/null || true

    local rc=0
    wait_for_port_to_close "$WEB_PORT" "Vue 前端" 15 || rc=1
    wait_for_port_to_close "$BRIDGE_PORT" "Java Bridge" 20 || rc=1
    wait_for_port_to_close "$OPENCODE_PORT" "opencode serve" 15 || rc=1
    wait_for_db_release 20 || rc=1
    if [[ $rc -ne 0 ]]; then
        fail "服务未完全停止。请先处理残留进程后再启动，避免 SQLite locked。"
        return $rc
    fi
    ok "所有服务已停止"
}

show_status() {
    echo ""
    echo "━━━ AgentCenter 服务状态 ━━━"
    echo ""
    if is_port_listening $OPENCODE_PORT; then
        ok "opencode serve   :$OPENCODE_PORT (运行中)"
    else
        fail "opencode serve   :$OPENCODE_PORT (未运行)"
    fi
    if is_port_listening $BRIDGE_PORT; then
        ok "Java Bridge      :$BRIDGE_PORT (运行中)"
    else
        fail "Java Bridge      :$BRIDGE_PORT (未运行)"
    fi
    if is_port_listening $WEB_PORT; then
        ok "Vue 前端         :$WEB_PORT (运行中)"
    else
        fail "Vue 前端         :$WEB_PORT (未运行)"
    fi
    echo ""
    info "Runtime workspace: $RUNTIME_WS"
    info "日志目录:         $LOG_DIR/"
}

start_opencode() {
    if is_port_listening $OPENCODE_PORT; then
        ok "opencode serve 已在运行 (port $OPENCODE_PORT)，复用"
        return 0
    fi

    info "启动 opencode serve (port $OPENCODE_PORT)..."
    mkdir -p "$RUNTIME_WS" "$LOG_DIR"
    ensure_runtime_workspace_isolated
    (cd "$RUNTIME_WS" && nohup opencode serve \
        --hostname 127.0.0.1 \
        --port $OPENCODE_PORT \
        --print-logs \
        --log-level WARN \
        > "$LOG_DIR/opencode-serve.log" 2>&1 & echo $! > "$LOG_DIR/opencode-serve.pid")

    if wait_for_port $OPENCODE_PORT "opencode serve" 15; then
        ok "opencode serve 启动成功 (PID $(cat "$LOG_DIR/opencode-serve.pid"))"
    else
        fail "opencode serve 启动失败，日志: $LOG_DIR/opencode-serve.log"
        return 1
    fi
}

ensure_runtime_workspace_isolated() {
    mkdir -p "$RUNTIME_WS"
    if [[ ! -d "$RUNTIME_WS/.git" ]]; then
        git -C "$RUNTIME_WS" init -q
        ok "Runtime workspace 已初始化为独立 git worktree"
    fi
}

start_bridge() {
    if is_port_listening $BRIDGE_PORT; then
        ok "Java Bridge 已在运行 (port $BRIDGE_PORT)，复用"
        return 0
    fi

    info "启动 Java Bridge (port $BRIDGE_PORT)..."
    mkdir -p "$LOG_DIR"
    (cd "$BRIDGE_DIR" && AGENTCENTER_RUNTIME_WORKSPACE="$RUNTIME_WS" nohup ./mvnw spring-boot:run > "$LOG_DIR/bridge.log" 2>&1 &)
    BRIDGE_PID=$(lsof -ti :$BRIDGE_PORT -sTCP:LISTEN 2>/dev/null || echo "")

    if wait_for_port $BRIDGE_PORT "Java Bridge" 60; then
        ok "Java Bridge 启动成功 (PID $(lsof -ti :$BRIDGE_PORT -sTCP:LISTEN 2>/dev/null))"
    else
        fail "Java Bridge 启动失败，日志: $LOG_DIR/bridge.log"
        tail -30 "$LOG_DIR/bridge.log"
        return 1
    fi
}

start_web() {
    if is_port_listening $WEB_PORT; then
        ok "Vue 前端已在运行 (port $WEB_PORT)，复用"
        return 0
    fi

    info "安装前端依赖 (首次)..."
    (cd "$WEB_DIR" && npm install --silent 2>/dev/null || true)

    info "启动 Vue 前端 (port $WEB_PORT)..."
    nohup bash -c "cd '$WEB_DIR' && exec npx vite --host" \
        > "$LOG_DIR/web.log" 2>&1 &
    echo $! > "$LOG_DIR/web.pid"

    if wait_for_port $WEB_PORT "Vue 前端" 20; then
        ok "Vue 前端启动成功 (PID $(cat "$LOG_DIR/web.pid"))"
    else
        fail "Vue 前端启动失败，日志: $LOG_DIR/web.log"
        return 1
    fi
}

start_tmux_service() {
    local session=$1 name=$2 port=$3 command=$4
    if is_port_listening "$port"; then
        ok "$name 已在运行 (port $port)，复用"
        return 0
    fi

    tmux kill-session -t "$session" 2>/dev/null || true
    info "启动 $name (tmux: $session, port $port)..."
    tmux new-session -d -s "$session" "$command"

    if wait_for_port "$port" "$name" 60; then
        ok "$name 启动成功 (PID $(lsof -ti :$port -sTCP:LISTEN 2>/dev/null))"
    else
        fail "$name 启动失败"
        return 1
    fi
}

start_all_dev() {
    check_all || return 1
    if ! command -v tmux &>/dev/null; then
        fail "开发模式需要 tmux。可用 brew install tmux 安装，或改用 ./start.sh --fg"
        return 1
    fi

    echo ""
    info "开发模式启动（tmux 保活 + Bridge DevTools）..."
    echo ""
    mkdir -p "$RUNTIME_WS" "$LOG_DIR"
    ensure_runtime_workspace_isolated

    start_tmux_service "$DEV_TMUX_PREFIX-opencode" "opencode serve" "$OPENCODE_PORT" \
        "cd '$RUNTIME_WS' && opencode serve --hostname 127.0.0.1 --port $OPENCODE_PORT --print-logs --log-level WARN 2>&1 | tee '$LOG_DIR/opencode-serve.log'" || return 1

    start_tmux_service "$DEV_TMUX_PREFIX-bridge" "Java Bridge" "$BRIDGE_PORT" \
        "cd '$BRIDGE_DIR' && AGENTCENTER_RUNTIME_WORKSPACE='$RUNTIME_WS' SPRING_DEVTOOLS_RESTART_ENABLED=true ./mvnw spring-boot:run 2>&1 | tee '$LOG_DIR/bridge.log'" || return 1

    info "安装前端依赖 (首次)..."
    (cd "$WEB_DIR" && npm install --silent 2>/dev/null || true)
    start_tmux_service "$DEV_TMUX_PREFIX-web" "Vue 前端" "$WEB_PORT" \
        "cd '$WEB_DIR' && npx vite --host 2>&1 | tee '$LOG_DIR/web.log'" || return 1

    echo ""
    ok "━━━ 开发模式已启动 ━━━"
    echo ""
    info "前端:  http://localhost:$WEB_PORT"
    info "API:   http://localhost:$BRIDGE_PORT"
    info "AI:    http://localhost:$OPENCODE_PORT"
    info "日志:  tail -f $LOG_DIR/bridge.log"
    info "查看:  tmux attach -t $DEV_TMUX_PREFIX-bridge"
    info "停止:  ./start.sh --stop"
    echo ""
}

start_all() {
    check_all || return 1

    echo ""
    info "启动 AgentCenter 全部服务..."
    echo ""

    mkdir -p "$RUNTIME_WS" "$LOG_DIR"

    start_opencode  || return 1
    start_bridge    || return 1
    start_web       || return 1

    echo ""
    ok "━━━ 所有服务已启动 ━━━"
    echo ""
    info "前端:  http://localhost:$WEB_PORT"
    info "API:   http://localhost:$BRIDGE_PORT"
    info "AI:    http://localhost:$OPENCODE_PORT"
    info "日志:  $LOG_DIR/"
    info "停止:  ./start.sh --stop"
    echo ""
}

start_all_fg() {
    check_all || return 1

    echo ""
    info "前台模式启动（Ctrl+C 停止所有服务）..."
    echo ""
    mkdir -p "$RUNTIME_WS" "$LOG_DIR"

    # Start opencode in background (it doesn't output to stdout usefully)
    start_opencode || return 1

    # Trap Ctrl+C to stop everything
    trap 'echo ""; info "正在停止..."; stop_all; exit 0' INT TERM

    # Start bridge in background
    start_bridge || return 1

    # Start web in foreground (user sees vite output)
    info "启动 Vue 前端 (前台)..."
    (cd "$WEB_DIR" && exec npx vite --host)
}

# ─── Main ─────────────────────────────────────────────────

case "${1:-}" in
    --stop)
        stop_all
        ;;
    --status)
        show_status
        ;;
    --check)
        check_all
        ;;
    --restart)
        stop_all
        sleep 2
        start_all
        ;;
    --dev)
        start_all_dev
        ;;
    --fg)
        start_all_fg
        ;;
    -h|--help|help)
        echo "AgentCenter 启动脚本"
        echo ""
        echo "用法: $0 [选项]"
        echo ""
        echo "  (无参数)    后台启动所有服务"
        echo "  --fg        前台启动（Ctrl+C 停止）"
        echo "  --stop      停止所有服务"
        echo "  --restart   重启所有服务"
        echo "  --dev       开发模式（tmux 保活 + Bridge DevTools）"
        echo "  --status    查看服务状态"
        echo "  --check     仅检查环境"
        echo "  -h          显示帮助"
        ;;
    *)
        start_all
        ;;
esac
