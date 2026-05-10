#!/usr/bin/env bash
# ============================================================
# reset-test-data.sh — 清空并重建 AgentCenter 测试数据
#
# 用法:
#   ./scripts/reset-test-data.sh              # 默认 10 条 FE 任务
#   ./scripts/reset-test-data.sh --count 20   # 指定数量
#   ./scripts/reset-test-data.sh --type FE    # 指定类型（默认 FE）
#   ./scripts/reset-test-data.sh --dry-run    # 只打印 SQL 不执行
#
# 数据库位置: ./data/agentcenter.db (相对于 agentcenter-bridge/)
# ============================================================

set -euo pipefail

# ---- 配置 ----
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BRIDGE_DIR="$(dirname "$SCRIPT_DIR")"
DB_PATH="${BRIDGE_DIR}/data/agentcenter.db"

COUNT=10
TYPE="FE"
DRY_RUN=false

# ---- 参数解析 ----
while [[ $# -gt 0 ]]; do
    case "$1" in
        --count)  COUNT="$2"; shift 2 ;;
        --type)   TYPE="$2";  shift 2 ;;
        --dry-run) DRY_RUN=true; shift ;;
        --db)     DB_PATH="$2"; shift 2 ;;
        -h|--help)
            echo "用法: $0 [--count N] [--type TYPE] [--dry-run] [--db PATH]"
            echo ""
            echo "  --count N      创建 N 条测试数据 (默认 10)"
            echo "  --type TYPE    工作项类型: FE/US/BUG/TASK/WORK/VULN (默认 FE)"
            echo "  --dry-run      只打印 SQL，不执行"
            echo "  --db PATH      指定数据库文件路径"
            exit 0
            ;;
        *) echo "未知参数: $1"; exit 1 ;;
    esac
done

# ---- 校验 ----
if [[ ! -f "$DB_PATH" ]]; then
    echo "❌ 数据库文件不存在: $DB_PATH"
    echo "   先启动一次 Bridge 让 Flyway 创建 schema: cd agentcenter-bridge && ./mvnw spring-boot:run"
    exit 1
fi

if ! command -v sqlite3 &>/dev/null; then
    echo "❌ 缺少 sqlite3 命令，请先安装: brew install sqlite"
    exit 1
fi

# ---- 常量 ----
PROJECT_ID="01DEFAULTPROJECT0000000000001"
USER_ID="01DEFAULTUSER00000000000000001"

# ---- 测试数据池（按类型） ----
get_seed_data() {
    local t="$1"
    case "$t" in
        FE)
            cat <<'FEEDATA'
用户登录页面重构|使用新设计规范重构登录页面，支持手机号/邮箱/SSO 三种登录方式|BACKLOG|HIGH
仪表盘数据可视化|首页仪表盘支持折线图、柱状图、饼图，数据实时刷新|TODO|MEDIUM
表格组件虚拟滚动|大数据量表格支持虚拟滚动，万行数据流畅渲染不卡顿|IN_PROGRESS|URGENT
深色模式全站适配|所有页面支持暗色主题，跟随系统偏好自动切换|BACKLOG|MEDIUM
文件上传组件升级|支持断点续传、拖拽上传、批量上传，限制文件类型和大小|BACKLOG|LOW
权限管理页面开发|RBAC 权限模型前端实现，支持角色配置、菜单权限、数据权限|TODO|HIGH
消息通知中心|站内通知、系统告警、工作流提醒统一入口，支持已读未读标记|BACKLOG|HIGH
国际化多语言支持|接入 i18n 框架，支持中英文切换，提取所有硬编码文案|BACKLOG|MEDIUM
富文本编辑器迁移|从 Quill 迁移到 ProseMirror，支持协同编辑和 Markdown 快捷输入|TODO|HIGH
前端性能监控接入|接入 Web Vitals 指标采集，LCP/FID/CLS 上报，异常链路追踪|BACKLOG|URGENT
FEEDATA
            ;;
        US)
            cat <<'USDATA'
首页加载性能提升|LCP 降到 2s 以内，优化首屏资源加载和渲染路径|TODO|MEDIUM
消息中心订阅设置|用户可配置站内通知、邮件通知和工作流提醒订阅范围|BACKLOG|HIGH
用户画像分析功能|基于用户行为数据生成画像标签，支持自定义维度筛选|BACKLOG|HIGH
多租户数据隔离|租户间数据逻辑隔离，支持租户级配置和权限边界|TODO|URGENT
批量导入导出优化|大数据量 CSV/Excel 导入导出，支持异步任务和进度展示|BACKLOG|MEDIUM
USDATA
            ;;
        BUG)
            cat <<'BUGDATA'
看板拖拽排序异常|拖拽卡片到新列后位置随机跳动，刷新后才恢复正确顺序|IN_PROGRESS|URGENT
会话滚动区域高度异常|长对话无法稳定上下滚动，底部输入框偶发遮挡内容|TODO|URGENT
文件上传偶发 500|上传超过 10MB 文件时概率性返回 500，重试可能成功|BACKLOG|HIGH
日期选择器时区偏移|选择日期后保存到数据库少了 8 小时，涉及时区转换|BACKLOG|HIGH
BUGDATA
            ;;
        TASK)
            cat <<'TASKDATA'
梳理首页查询缓存策略|对首页指标、工作项列表和待确认面板补齐缓存刷新方案|TODO|MEDIUM
编写 Bridge API 集成测试|对核心 CRUD 和工作流启动接口补充集成测试用例|BACKLOG|HIGH
TASKDATA
            ;;
        WORK)
            cat <<'WORKDATA'
接入统一身份认证联调|验证企业 SSO、项目成员权限和审计日志链路|BACKLOG|HIGH
CI/CD 流水线搭建|GitHub Actions 自动构建、测试、部署到测试环境|TODO|MEDIUM
WORKDATA
            ;;
        VULN)
            cat <<'VULNDATA'
运行时会话权限校验缺失|OpenCode 会话桥接需要校验用户对项目与工作项的访问权限|BACKLOG|HIGH
XSS 注入风险|工作项描述字段未转义 HTML，存在存储型 XSS 风险|TODO|URGENT
VULNDATA
            ;;
        *) echo "未知类型: $t" >&2; exit 1 ;;
    esac
}

# ---- 生成 SQL ----
build_sql() {
    local type="$1"
    local count="$2"

    # 1. 清理关联数据（按外键依赖顺序）
    local clear_sql="
-- 清理关联数据
DELETE FROM confirmation_action;
DELETE FROM confirmation_request;
DELETE FROM agent_message;
DELETE FROM agent_session;
DELETE FROM runtime_event;
DELETE FROM artifact;
DELETE FROM workflow_node_instance;
DELETE FROM workflow_instance;
DELETE FROM work_item;
"

    # 2. 从种子数据池取前 N 条
    local insert_lines=""
    local seq=2001
    local i=0

    while IFS='|' read -r title desc status priority; do
        [[ -z "$title" ]] && continue
        [[ $i -ge $count ]] && break

        local code="${type}${seq}"
        local id="01WORKITEM0000000000000${code}"

        insert_lines+="
    ('${id}', '${code}', '${type}', '${title}', '${desc}', '${status}', '${priority}', '${PROJECT_ID}', '${USER_ID}', '${USER_ID}'),"

        ((seq++))
        ((i++))
    done < <(get_seed_data "$type")

    # 去掉末尾逗号
    insert_lines="${insert_lines%,}"

    if [[ $i -eq 0 ]]; then
        echo "❌ 类型 ${type} 的种子数据不足 ${count} 条（只有 $i 条），减少 --count 再试" >&2
        exit 1
    fi

    echo "${clear_sql}
-- 插入 ${i} 条 ${type} 测试数据
INSERT INTO work_item (id, code, type, title, description, status, priority, project_id, owner_user_id, assignee_user_id) VALUES${insert_lines};
"
}

# ---- 主逻辑 ----
SQL=$(build_sql "$TYPE" "$COUNT")

if $DRY_RUN; then
    echo "===== DRY RUN — 以下 SQL 未执行 ====="
    echo "$SQL"
    echo ""
    echo "执行命令: sqlite3 \"$DB_PATH\" \"\$(cat)\""
    exit 0
fi

echo "🔄 重置测试数据: ${COUNT} 条 ${TYPE} 任务"
echo "   数据库: ${DB_PATH}"
echo ""

sqlite3 "$DB_PATH" "$SQL"

echo "✅ 完成! 当前 work_item 表:"
sqlite3 -header -column "$DB_PATH" "SELECT code, type, title, status, priority FROM work_item ORDER BY code;"
echo ""
echo "📊 关联表统计:"
sqlite3 -header -column "$DB_PATH" "
SELECT 'work_item' as tbl, COUNT(*) as cnt FROM work_item
UNION ALL SELECT 'agent_session', COUNT(*) FROM agent_session
UNION ALL SELECT 'workflow_instance', COUNT(*) FROM workflow_instance
UNION ALL SELECT 'confirmation_request', COUNT(*) FROM confirmation_request;
"
