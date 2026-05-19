# AGENTS.md — AgentCenter SDD

> 项目级行为合同。协作者和 AI Agent 进入 AgentCenter 后，以本文件为最高项目协议；全局 AGENTS 只做语言和通用安全桥接。
> 目标：规格先行、边界清楚、前后端/Bridge 合同闭环、验证证据可复查。

---

## 0. 项目定位

AgentCenter 是企业智能编排平台，通过网页工作台和对话界面编排需求、设计、代码检查、构建部署、测试验证、CICD 等企业工具流程。

当前技术栈和工程边界：

| 模块 | 技术栈 | 职责 | 常用验证 |
|---|---|---|---|
| `agentcenter-web/` | Vue 3、TypeScript、Pinia、Vite、Vitest | 工作台、看板、会话、工作流配置、SSE 消费 | `npm run typecheck` / `npm run test` / `npm run build` |
| `agentcenter-bridge/` | Java 17、Spring Boot 3、MyBatis、SQLite、Flyway | Work Item、Workflow、Session、Confirmation、Runtime Adapter、REST/SSE | `./mvnw test` / `./mvnw clean package` |
| `runtime-workspace/` | OpenCode runtime workspace | Runtime 技能、MCP 配置、外部进程工作目录 | 不作为业务主数据源 |
| `agent-center-demo/` | React、Express、Playwright | 演示首页和旧版 E2E | `npm run test:e2e` |
| `docs/architecture/` | Markdown 架构文档 | 目标态、ADR、领域模型、验证框架 | 设计审查 + 与实现 diff 对齐 |
| `.sisyphus/` | 开发协作证据 | Plan、evidence、handoff、notepad | 不得被产品运行态依赖 |

核心参考：

- [README.md](README.md)
- [docs/README.md](docs/README.md)
- [docs/architecture/README.md](docs/architecture/README.md)
- [docs/architecture/AGENT-RUNTIME-BRIDGE-DEVELOPMENT-BLUEPRINT.md](docs/architecture/AGENT-RUNTIME-BRIDGE-DEVELOPMENT-BLUEPRINT.md)
- [docs/architecture/OPENCODE-BRIDGE-TARGET-STATE.md](docs/architecture/OPENCODE-BRIDGE-TARGET-STATE.md)
- [docs/architecture/VERIFICATION-FRAMEWORK.md](docs/architecture/VERIFICATION-FRAMEWORK.md)

---

## 1. Source of Truth

执行和审查按以下顺序判定事实来源：

1. 用户确认的需求边界、验收标准和明确不做项。
2. `docs/architecture/` 中仍标记为活跃的目标态、ADR、领域模型和验证框架。
3. `.sisyphus/plans/*.md` 中针对本任务的 PRD/HLD/LLD/Verification 计划。
4. 测试结果、构建日志、curl 输出、Playwright 截图、`.sisyphus/evidence/`。
5. 代码实现。

如果代码与更高层 source of truth 冲突，先报告冲突，再决定是改代码还是更新规格。不要悄悄让代码成为唯一事实来源。

运行态边界：

- 产品运行态、服务启动、任务编排、Skill catalog、工作流提示词生成不得读取或依赖 `.sisyphus/`。
- 前端不得直接调用 OpenCode；浏览器只对接 Java Bridge API。
- Runtime Adapter 不拥有业务主数据；AgentCenter 自己拥有事项、工作流、会话、确认项、运行事件和 artifact。
- 可执行 Runtime Skill 来自 `runtime-workspace/.opencode/skills` 及 Bridge 的 `runtime_skill` 投影，不来自 `.sisyphus/`。

---

## 2. SDD 信息分层

PRD / HLD / LLD 是信息分层，不是额外三套重文档。小任务可写在对话和审查卡片中；L3+ 必须有计划文档。

| 分层 | 回答什么 | AgentCenter 写什么 |
|---|---|---|
| PRD / 需求边界 | 做什么？不做什么？验收标准是什么？ | 用户场景、工作台行为、企业流程边界、成功标准、角色权限假设 |
| HLD / 概要设计 | 整体怎么连？模块边界在哪里？ | Vue Web、Java Bridge、Runtime Adapter、Workflow、Confirmation、REST/SSE、SQLite/Flyway 边界 |
| LLD / 实现计划 | 改哪些文件、接口、表、组件、状态？ | Controller/Service/Domain/Mapper、Vue store/view/component/api、DTO、migration、测试点 |
| Verification / 证据 | 怎么证明做对？ | typecheck/test/build、Maven test/package、curl、SQLite 检查、Playwright 截图、`.sisyphus/evidence/` |

---

## 3. 任务分级

未标注等级时，Agent 先按影响面临时判断；涉及 Bridge API、数据库、工作流状态、Runtime Adapter、SSE、跨端 UI 的任务默认至少 L3。

| 级别 | 场景 | 必须做什么 |
|---|---|---|
| L0 | typo、注释、文案、常量名、无行为变化 | 改完自查 diff，必要时跑轻量检查，输出简短审查卡片 |
| L1 | 小 bug，根因明确，1-3 文件 | L0 + 对应回归测试，无法测试要说明原因 |
| L2 | 小功能，1-5 文件，需求清晰 | PRD 边界确认 + 文件级计划 + 单测/组件测试 |
| L3 | 中型功能，3-10 文件，有设计选择或跨前后端 | PRD/HLD/LLD Plan + 架构文档对齐 + 前后端/Bridge 验证 |
| L4 | 大功能，新模块、跨系统、工作流/Runtime 主链路 | 完整 PRD/HLD/LLD + 分批计划 + 全链路 evidence + 必要的文档更新 |
| 重构 | 行为不变、结构调整 | 声明 Expected Contract Changes，验证外部行为不变 |
| 紧急 | 数据丢失、安全、主链路不可用 | 先修复，保留最小 VERIFY，24h 内补原因、回归、审查卡片 |

L2+ 功能请求先做需求唤起，不直接写代码：

```text
> 我的理解: [一句话]
> 边界: 做 / 不做
> 需要你确认: [最多 1-2 个问题]
```

---

## 4. 协作行为约束

1. **SCOPE**：只改任务范围内文件；发现 scope creep 立刻说明。
2. **READ**：L2+ 改文件前先读相关文档和代码；不了解就先查。
3. **DILIGENCE**：改接口、DTO、领域对象、表结构、状态枚举、事件 payload 后，必须查调用点和测试。
4. **VERIFY**：改完跑对应验证；失败不报告完成。
5. **SIMPLE**：不引入新抽象、新框架、新状态层，除非任务明确需要或已有项目模式支持。
6. **CASCADE**：改初始化要看清理；改清理要看恢复；改状态机要看失败、重试、取消和幂等。
7. **OWNERSHIP**：跨模块改动要在计划或审查卡片中写清影响模块和责任边界。

底线：

- 不提交 secrets、真实 token、私钥、个人 auth/config、运行数据库、日志库。
- 不使用 `as any`、`@ts-ignore`、空 catch、删除失败测试来绕过问题。
- 不覆盖用户未提交改动，不做破坏性 git 操作。
- 不在未确认范围内扩大到多租户、MQ、WebSocket、生产级权限体系或新平台。

---

## 5. 工具和影响面

查找优先级：

1. GitNexus / code-review-graph 等图工具：影响面、调用链、执行流。
2. `rg --files`：找文件。
3. `rg`：找文本。
4. 测试、curl、日志、截图补证。

如果图工具提示 stale，或明显找不到当前存在的 symbol，先刷新索引或记录工具不可用，再 fallback 到 `rg` + 测试。不要把“图工具空结果”当成“没有影响面”的唯一证据。

提交前如果本次改了代码符号、接口或流程，必须做变更范围自查；GitNexus 可用时运行 `gitnexus_detect_changes()` 或对应 CLI 检查。文档-only 变更可说明不适用。

---

## 6. 前后端/Bridge 合同

跨层改动必须把合同写清楚：

| 合同 | 必查项 |
|---|---|
| REST API | URL、method、request/response DTO、错误码、兼容性 |
| SSE/Event | event name、sessionId 来源、payload 字段名、完成/错误事件 |
| Workflow | 节点状态、失败/重试、确认项、artifact 归属 |
| Runtime Adapter | workspace、进程生命周期、端口探测、超时、日志、取消 |
| SQLite/Flyway | migration 顺序、默认值、索引、回滚/兼容策略 |
| Vue State | Pinia store shape、loading/error、重连、空态、可见状态 |

已知高风险点：

- SSE 发布者和消费者必须使用同一个 DB session ID，不要泄漏 adapter 内部 mapping key。
- `opencode serve` API 契约要逐层 curl 验证；不要猜 payload 字段。
- `startProcess()` 先探测端口已有服务，再决定是否启动新进程。
- 前端消费字段名必须与后端 payload 对齐，例如 `label` / `delta` / `text` 不得混用。
- 多跳链路按 opencode -> Java Bridge -> Vite proxy -> browser 逐层定位。

---

## 7. 验证矩阵

按修改范围选择最小充分验证，不要求每次全跑，但审查卡片必须说明选择理由。

| 修改范围 | 建议验证 |
|---|---|
| 文档/规则 | `git diff --check` |
| Vue 类型/API/store/component | `cd agentcenter-web && npm run typecheck`，必要时 `npm run test` |
| Vue 构建或路由/样式大改 | `cd agentcenter-web && npm run build` |
| Java domain/service/controller/mapper | `cd agentcenter-bridge && ./mvnw test` |
| Java 打包/启动链路 | `cd agentcenter-bridge && ./mvnw clean package` |
| DB migration / seed / reset | Maven test + reset 脚本 dry-run 或实际测试库验证 |
| REST/SSE 合同 | Maven test + curl 逐层验证 + 前端消费验证 |
| UI/原型/工作台流程 | 对应测试 + Playwright/浏览器截图，evidence 放 `.sisyphus/evidence/` |
| Demo/E2E | `cd agent-center-demo && npm run test:e2e` |

常用命令：

```bash
cd agentcenter-web && npm run typecheck
cd agentcenter-web && npm run test
cd agentcenter-web && npm run build

cd agentcenter-bridge && ./mvnw test
cd agentcenter-bridge && ./mvnw clean package

cd agent-center-demo && npm run test:e2e
```

---

## 8. 测试数据

需要干净前端测试数据时，用项目脚本，不手写全量删除 SQL：

```bash
cd agentcenter-bridge
./scripts/reset-test-data.sh --dry-run
./scripts/reset-test-data.sh --count 10 --type FE
```

注意：

- 脚本会不可逆删除 work item 及关联数据，只能用于本地/测试环境。
- 修改 Flyway migration 后，通常需要重启 Bridge 再验证 checksum。
- 只需追加数据时，先 `--dry-run`，再选择最小 SQL 或脚本路径。

---

## 9. 文档和证据

详细文档生命周期、归档状态和 Design Sync 流程见 [docs/architecture/DOCUMENTATION-GOVERNANCE.md](docs/architecture/DOCUMENTATION-GOVERNANCE.md)。本节只保留项目级原则。

文档边界：

- `AGENTS.md`：项目级协作协议和 SDD 门禁，所有协作者必读。
- `README.md`：启动、依赖、端口、项目结构。
- `docs/architecture/`：长期架构、ADR、领域模型、目标态、验证体系。
- `docs/prototype/`：原型和 UI 基线。
- `.sisyphus/plans/`：任务计划和 handoff。
- `.sisyphus/evidence/`：验证证据、截图、curl 输出、测试摘要。

维护规则：

- L0/L1 默认轻量归档；若无行为、合同或 UI 变化，审查卡片写 `docs: n/a`。
- L2+ 完成前必须做 Documentation Impact Check，判断是否影响 API、DTO、SSE、DB、状态机、Runtime、UI 或架构决策。
- L3/L4 若改变跨层合同或目标态，必须同步 `docs/architecture/`、`docs/prototype/` 或对应索引。
- 过时文档不要悄悄删除，标注 `Stale` / `Superseded`，再按治理文档移动到 archive。
- evidence 只保存开发验证证据，不作为产品运行数据源。

---

## 10. 审查卡片

每个任务完成后输出审查卡片。L0/L1 可简化为 summary + changes + verification。

```json
{
  "summary": "做了什么（一句话）",
  "decisions": [{"what": "选了 X 而非 Y", "why": "一行理由"}],
  "changes": [{"file": "path", "desc": "改了什么"}],
  "verification": {
    "typecheck": "pass/skip",
    "tests": "pass/skip",
    "build": "pass/skip",
    "evidence": "path or n/a"
  },
  "docs": {
    "impact": "updated/n/a/stale-found",
    "updated": ["path or n/a"],
    "archived": ["path or n/a"],
    "reason": "why this is enough"
  },
  "risks": ["剩余风险或 n/a"]
}
```

---

## 11. 协作者交接

多人协作时，任何 handoff 必须包含：

- 当前任务等级和边界。
- 已读 source of truth。
- 已修改文件和未修改但相关的文件。
- 已跑验证和结果。
- 未完成项、阻塞项、风险。
- 是否存在用户未提交改动或刻意未纳入的变更。

提交建议：

- 使用 Conventional Commits，例如 `docs(sdd): refine project agent protocol`。
- 一个提交只包含一个逻辑主题。
- 提交前确认没有 auth/config/log/db/cache、`.DS_Store`、真实 key。

---

## 12. 不做的事

- 不把 TianYuan 专属角色、Blueprint Graph 强制门禁、`.sisyphus` 状态模型原样复制到 AgentCenter。
- 不让前端直接调用 OpenCode。
- 不让 Runtime Adapter 成为业务主数据源。
- 不为普通功能提前引入 MQ、多租户、生产权限体系或 WebSocket 替代 SSE。
- 不把本地同步包、运行 workspace、缓存或证据误当成产品功能。

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **AgentCenter** (11718 symbols, 28400 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

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
