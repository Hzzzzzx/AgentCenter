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
| `.sisyphus/` | 计划、证据、研究与任务状态 | Plan / evidence / handoff |

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

Source of truth 顺序：

1. 用户确认的需求边界和验收标准
2. `docs/architecture/` 中的目标设计和蓝图
3. `.sisyphus/plans/*.md` 中的任务计划
4. 测试、截图、运行证据
5. 代码实现

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
