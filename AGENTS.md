# AGENTS.md — AgentCenter SDD

> 项目级行为合同。全局 AGENTS 只做桥接；在 AgentCenter 内以本文件为准。
> 原则：规格先行、对话澄清、分级执行、前后端/Bridge 证据闭环。

---

## 项目定位

AgentCenter 是企业智能编排平台：通过网页工作台和对话界面编排需求、设计、代码检查、构建部署、测试验证、CICD 等企业工具流程。

当前主要工程边界：

| 模块 | 职责 | 常用验证 |
|------|------|----------|
| `agentcenter-web/` | Vue 3 + Pinia 工作台前端 | `npm run typecheck` / `npm run test` / `npm run build` |
| `agentcenter-bridge/` | Java Spring Boot Bridge，管理事项、工作流、会话、确认项、Runtime Adapter | `./mvnw test` / `./mvnw clean package` |
| `agent-center-demo/` | React/Express 演示与首页 E2E | `npm start` / `npm run test:e2e` |
| `docs/architecture/` | 架构、领域模型、OpenCode Bridge 目标态、验证体系 | 设计审查 + 与实现 diff 对齐 |
| `.sisyphus/` | 开发过程计划、证据、研究与任务状态 | Plan / evidence / handoff；不得作为产品运行数据源 |

核心参考：

- [README.md](README.md)
- [docs/README.md](docs/README.md)
- [docs/architecture/AGENT-RUNTIME-BRIDGE-DEVELOPMENT-BLUEPRINT.md](docs/architecture/AGENT-RUNTIME-BRIDGE-DEVELOPMENT-BLUEPRINT.md)
- [docs/architecture/OPENCODE-BRIDGE-TARGET-STATE.md](docs/architecture/OPENCODE-BRIDGE-TARGET-STATE.md)
- [docs/architecture/AI-NATIVE-DEVELOPMENT.md](docs/architecture/AI-NATIVE-DEVELOPMENT.md)
- [docs/architecture/VERIFICATION-FRAMEWORK.md](docs/architecture/VERIFICATION-FRAMEWORK.md)

---

## SDD 信息分层

PRD / HLD / LLD 是信息分层，不是额外三套重文档。

| 分层 | 回答什么 | AgentCenter 中写什么 |
|------|----------|----------------------|
| PRD / 需求边界 | 做什么？不做什么？验收标准？ | 用户场景、工作台行为、企业流程边界、成功标准 |
| HLD / 概要设计 | 整体怎么设计？模块怎么连？ | Vue Web、Java Bridge、Runtime Adapter、Workflow、Confirmation、SSE/WebSocket 边界 |
| LLD / 实现计划 | 改哪些文件/接口/表/组件？ | Controller/Service/Domain/Mapper、Vue store/component/API、测试点 |
| Verification / 证据 | 怎么证明做对了？ | typecheck/test/build、Maven test、Playwright 截图、`.sisyphus/evidence/` |

工程执行 Source of truth 顺序：

1. 用户确认的需求边界和验收标准
2. `docs/architecture/` 中的目标设计和蓝图
3. `.sisyphus/plans/*.md` 中的任务计划
4. 测试、截图、运行证据
5. 代码实现

产品运行态、服务启动、任务编排、Skill catalog、工作流提示词生成不得读取或依赖 `.sisyphus/`，也不得要求该目录存在。`.sisyphus/` 只服务开发协作、验证证据和 handoff；可执行 Skill 必须来自项目级 Runtime Skill 源（当前为项目工作区 `.opencode/skills`）及 Bridge 的 `runtime_skill` 投影。

---

## 快速分级

| 级别 | 场景 | 需要做什么 |
|------|------|------------|
| L0 | typo、注释、文案、常量名 | 改完 → 快速自查 → 简短汇报 |
| L1 | 小 bug，根因明确，1-3 文件 | L0 + 对应回归测试或说明无法测试原因 |
| L2 | 小功能，1-5 文件，需求清晰 | PRD 边界确认 + 文件级计划 + 单测/组件测试 |
| L3 | 中型功能，3-10 文件，有设计选择 | PRD/HLD/LLD Plan + 架构文档对齐 + 前后端/Bridge 验证 |
| L4 | 大功能，新模块或跨系统 | 完整 PRD/HLD/LLD + 分批计划 + 全链路证据 |
| 重构 | 行为不变，结构调整 | 明确 Expected Contract Changes，验证外部行为不变 |
| 紧急 | 数据丢失、安全、主链路不可用 | 先修，事后 24h 内补记录、回归和原因 |

未标注等级时，Agent 先按影响面临时判断；涉及 Bridge API、数据库、工作流状态、Runtime Adapter、跨端 UI 的任务默认至少 L3。

---

## 行为约束

1. **SCOPE**：只改任务范围内文件；发现 scope creep 必须说明。
2. **READ**：L2+ 改文件前先读相关文档和代码，不了解就先查。
3. **DILIGENCE**：改接口、类型、领域对象、表结构、状态枚举后，查调用点和测试。
4. **VERIFY**：改完跑对应验证；失败不报告完成。
5. **SIMPLE**：不引入新抽象、新框架、新状态层，除非任务明确需要。
6. **CASCADE**：改初始化要看清理；改清理要看恢复；改状态机要看失败/重试。

底线：不提交 secrets；不使用 `as any`、`@ts-ignore`、空 catch、删除失败测试来绕过问题；不覆盖用户未提交改动。

---

## 需求唤起

收到 L2+ 功能请求时，不要直接写代码：

```text
> 我的理解: [一句话]
> 边界: ✅做 / ❌不做
> 需要你确认: [最多 1-2 个问题]
```

用户确认后再进入 PRD/HLD/LLD 计划。L0/L1 可跳过追问，但仍要保持 scope 清晰。

---

## 工具和查找

- 文件名查找优先 `rg --files`。
- 文本搜索优先 `rg`。
- 如果 code-review-graph / 语义图工具可用，优先用图工具查影响面；不可用时用 `rg` + 测试补证。
- 不要把 TianYuan 专属 GitNexus/Blueprint 规则强套到 AgentCenter；AgentCenter 当前以架构蓝图、测试、E2E 和 evidence 为主。

---

## 常用验证命令

按修改范围选择，不要求每次全跑：

```bash
# Vue 工作台
cd agentcenter-web
npm run typecheck
npm run test
npm run build

# Java Bridge
cd agentcenter-bridge
./mvnw test
./mvnw clean package

# React Demo / 首页 E2E
cd agent-center-demo
npm run test:e2e
```

UI 或原型改动必须留下截图或 Playwright evidence 到 `.sisyphus/evidence/`。

---

## 测试数据管理

### 快速重置

当你需要干净的前端测试数据（比如验证看板、工作流启动、对话等 UI 流程），用这个脚本：

```bash
cd agentcenter-bridge

# 默认：清空所有数据 → 插入 10 条 FE 任务
./scripts/reset-test-data.sh

# 指定数量和类型
./scripts/reset-test-data.sh --count 20 --type FE

# 只看会执行什么 SQL，不真正执行
./scripts/reset-test-data.sh --dry-run

# 支持的类型: FE / US / BUG / TASK / WORK / VULN
./scripts/reset-test-data.sh --type BUG --count 5
```

### 脚本做了什么

1. **级联清理**：按外键依赖顺序删除 confirmation_action → confirmation_request → agent_message → agent_session → runtime_event → artifact → workflow_node_instance → workflow_instance → work_item
2. **重新插入**：从内置种子数据池取前 N 条插入 work_item 表
3. **结果验证**：自动打印最终表状态和关联表统计

### 种子数据说明

每种类型内置了不同数量的真实场景测试数据：

| 类型 | 内置条数 | 典型场景 |
|------|----------|----------|
| FE | 10 | 登录重构、虚拟滚动、深色模式、i18n 等 |
| US | 5 | 性能提升、订阅设置、用户画像等 |
| BUG | 4 | 拖拽异常、滚动高度、上传 500 等 |
| TASK | 2 | 缓存策略、集成测试 |
| WORK | 2 | SSO 联调、CI/CD |
| VULN | 2 | 权限校验、XSS |

数据状态覆盖 BACKLOG / TODO / IN_PROGRESS，优先级覆盖 URGENT / HIGH / MEDIUM / LOW。

### 手动 SQL（不推荐，除非脚本不可用）

```bash
sqlite3 agentcenter-bridge/data/agentcenter.db "DELETE FROM confirmation_action; DELETE FROM confirmation_request; DELETE FROM agent_message; DELETE FROM agent_session; DELETE FROM runtime_event; DELETE FROM artifact; DELETE FROM workflow_node_instance; DELETE FROM workflow_instance; DELETE FROM work_item;"
```

### 注意事项

- 脚本会**不可逆删除**所有 work_item 及其关联数据，不要在生产环境使用。
- 重置后需要**重启 Bridge** 才能让 Flyway checksum 校验通过（如果改了 migration 文件的话）。
- 如果只是追加数据不改旧数据，用 `--dry-run` 先看 SQL 再手动执行部分。

---

## 审查卡片

每个任务完成后输出简短审查卡片：

```json
{
  "summary": "做了什么（一句话）",
  "decisions": [{"what": "选了 X 而非 Y", "why": "一行理由"}],
  "changes": [{"file": "path", "desc": "改了什么"}],
  "verification": {"typecheck":"pass/skip", "tests":"pass/skip", "build":"pass/skip", "evidence":"path or n/a"}
}
```

L0/L1 可简化为 summary + changes + verification。

---

## 文档维护

- 新增或修改架构决策时，同步 `docs/architecture/README.md` 或相关架构文档。
- 新增原型/首页基线时，同步 `docs/prototype/README.md`。
- 计划和 evidence 写入 `.sisyphus/`。
- 过时文档不要悄悄删除；移入 archive 或在索引中标注 superseded。

---

## 已知陷阱（Java Bridge + SSE + 外部进程）

> 详细经验见 `.sisyphus/notepads/opencode-bridge-sse-rest/learnings.md`。以下是踩过且容易再犯的坑。

### 0. OpenCode Runtime 工作目录统一管理
所有 Runtime 组件通过 `RuntimeWorkspace.resolve()` 统一解析，固定在**项目根目录下的 `runtime-workspace/`**（从 `user.dir` 向上查找包含 `agentcenter-bridge/` 的目录）。环境变量 `AGENTCENTER_RUNTIME_WORKSPACE` 可覆盖。测试 Skill 放在该目录的 `.opencode/skills/` 下。

### 0.5 企业环境配置
企业内网部署时，Maven 默认配置可能无法访问中央仓库。必须在 `~/.m2/settings.xml` 中配置企业仓库 mirror。Windows 用户避免使用 C 盘默认 Maven 本地仓库路径。`start.sh --check` 会自动检测并提示。Agent 引导用户启动时，先建议运行 `./start.sh --check`。

### 1. SSE 事件的 session ID 必须贯穿始终
事件的发布者（EventSubscriber）和消费者（SseEmitterRegistry）必须用同一个 session ID。适配器内部的 mapping key（如 `acs_xxx`）不能泄漏到事件回调链路——必须用 DB session ID（ULID）。

### 2. 管理外部进程前先探测
`startProcess()` 第一步必须先探测目标端口是否已有服务。不要假设"我是唯一启动者"。否则会端口冲突 → 新进程 exit 1 → 探测到旧进程 → 误判 ready → 循环。

### 3. 前后端 payload 字段要对齐
后端 payload 的 value 字段名（`label`）和前端消费的字段名（`delta`/`text`）必须一致。最简单的做法：前后端共享一份字段名定义，或用 curl 验证实际输出。

### 4. 多跳系统先逐层 curl 再改代码
数据经过 opencode → Java → Vite → 浏览器，任何一环都可能出问题。不要一次性改多处。先用 curl 逐层验证 input/output，确认哪层有问题再改。

### 5. opencode serve API 契约
`prompt_async` 需要 `{"agent":"build","parts":[{"type":"text","text":"..."}]}`，不是 `{"content":"..."}`。SSE 事件中 `part.type` 区分 text（回复）/reasoning（思考）/tool（工具调用），不能统一处理。

---

## 不做的事

- 不把 TianYuan 的 Blueprint Graph、GitNexus 强制门禁直接复制到 AgentCenter。
- 不让前端直接调用 OpenCode；前端只对接 Java Bridge API。
- 不让 Runtime Adapter 成为业务主数据源；AgentCenter 自己拥有事项、工作流、会话、确认项和运行事件。
- 不在没有用户确认的情况下扩大到多租户、MQ、WebSocket 或生产级权限体系。

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **AgentCenter** (11005 symbols, 27257 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/AgentCenter/context` | Codebase overview, check index freshness |
| `gitnexus://repo/AgentCenter/clusters` | All functional areas |
| `gitnexus://repo/AgentCenter/processes` | All execution flows |
| `gitnexus://repo/AgentCenter/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
