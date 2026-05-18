# Blueprint Graph 工具边界与改进方向

> 状态：Living Document
> 创建日期：2026-05-01
> 背景：Agent Core P0 Batch 4 steering/cancel 实施中，Blueprint Graph 暴露出 domain 混用与 Rust 内部语义抽取不足的问题。

## 结论

Blueprint Graph 当前适合作为 **结构化契约验证工具**，不适合作为 Agent runtime 语义正确性的唯一证据。

正确用法：

```text
Plan target graph
  -> Blueprint Graph 验证静态可抽取结构
  -> Rust/TS tests 验证运行时语义
  -> scenario/UI-control/manual evidence 验证 LLM 行为、UI 体验、replay 质量
```

错误用法：

```text
Blueprint Graph 0 errors
  -> 推断 Agent 中途 steering、cancel token、DB replay、LLM prompt 注入一定正确
```

## 当前支持的图元素

从 2026-05-01 工具报错和现有 extractor 行为看，当前 `GraphArtifactV1` 支持以下 node/edge。

### Node Kinds

| kind | 适合表达 | 注意 |
| --- | --- | --- |
| `TSEntry` | TS 函数入口，例如 `agentTurnStart` | 主要用于 `tauriInvoke` 入口 |
| `TauriCommand` | IPC 命令名，例如 `agent_turn_start` | source_path 通常为空，symbol 是命令名 |
| `RustHandler` | `#[tauri::command]` Rust handler | 当前更可靠地覆盖 command handler，而非任意 Rust 函数/struct |
| `TSModule` | TS 模块文件 | 用于静态 import 层级 |
| `VueComponent` | Vue SFC 组件 | `check-frontend` domain 下更合适 |
| `Store` | store/composable 中被识别出的 store 入口 | 识别依赖当前 frontend extractor 规则 |
| `Composable` | Vue composable | 适合 UI contract |
| `Test` | 测试文件 | 适合声明某链路有测试覆盖 |
| `ViewDefinition` | Workbench view registration | 适合 Fluid Workbench 类任务 |
| `LayoutProfile` | Workbench layout profile | 适合布局 contract |

不支持但我们误用过：

| kind | 现状 | 替代 |
| --- | --- | --- |
| `RustModule` | 不支持 | 先用 prose waiver + Rust tests；若是 command 用 `RustHandler` |
| `RustStruct` / `RustFunction` | 不支持 | 后续扩展 Rust extractor |
| `AgentRuntime` / `EventStore` / `Persistence` | 不支持专用 kind | 先拆成可抽取 IPC/frontend target，语义用测试补证 |

### Edge Kinds

| edge kind | 适合表达 |
| --- | --- |
| `TS_INVOKE_IPC` | TS 入口调用 Tauri IPC |
| `IPC_HANDLED_BY` | IPC 命令由 Rust command handler 处理 |
| `IMPORTS` | 静态 import |
| `RENDERS` | Vue 组件渲染子组件 |
| `USES_STORE` | 组件/模块使用 store |
| `REGISTERS_VIEW` | 注册视图 |
| `OPENS_VIEW` | 打开视图 |
| `MUTATES_LAYOUT` | 布局变更 |
| `TESTED_BY` | 目标被测试覆盖 |

不支持但我们误用过：

| edge kind | 现状 | 替代 |
| --- | --- | --- |
| `CALLS` | 不支持 | 若是静态 import 用 `IMPORTS`；运行时调用用测试/审查补证 |
| `PERSISTS_TO` | 不支持 | DB/replay 行为用 repository tests / integration tests |
| `EMITS_EVENT` | 没有稳定支持 | 当前用事件类型测试 + frontend listener tests |
| `INJECTS_PROMPT` | 不支持 | 用 prompt/context assembly 单测 |
| `CANCELS_TOKEN` | 不支持 | 用 runtime 单测 |

## 两个命令的边界

### `pnpm blueprint-graph:check`

适合：

- TS `tauriInvoke(...)` 到 Tauri command 的 IPC 链。
- Tauri command 是否在 Rust `generate_handler!` 中注册。
- Rust command handler 是否存在。

不适合：

- Vue SFC / frontend store 的完整 UI contract。
- Rust 内部 runtime 调用链，例如 `agent_turn_steer -> AgentTurnRuntimeState::steer`。
- DB/replay、cancel token、prompt 注入等运行时语义。

建议 target：

```text
TSEntry -> TauriCommand -> RustHandler
```

### `pnpm blueprint-graph:check-frontend`

适合：

- Vue Component / TSModule / Store / Composable / Test 的静态关系。
- `IMPORTS`、`RENDERS`、`USES_STORE`、`TESTED_BY`。

不适合：

- Tauri command / Rust handler 链路。
- 后端 runtime / DB / replay。
- 运行时状态分支，例如“active turn 存在时走 steer，否则 start”。

建议 target：

```text
VueComponent -> Store/TSModule -> Test
```

## Target Graph 写法规则

1. **按 domain 拆文件**
   - API/IPC target：只放 `TSEntry -> TauriCommand -> RustHandler`。
   - Frontend target：只放 `VueComponent/TSModule/Store/Test` 静态关系。
   - Agent runtime semantic target：暂时不要硬塞进 Blueprint block，写 waiver + tests。

2. **不要在同一个 `check-frontend` target 里放 Rust node**
   - `check-frontend` actual graph 不抽 Rust，所以必然报 missing。

3. **不要在 `check` target 里要求 Vue/Store 细节**
   - `check` 是 IPC/Rust 方向更可靠；Vue/Store 应走 `check-frontend`。

4. **只用支持的 kind/edge**
   - 不要写 `RustModule`、`CALLS` 等未支持元素。

5. **Blueprint 结果不能替代行为测试**
   - `0 errors` 只能说明目标结构存在，不说明行为正确。

## Agent Core P0 当前必须写 waiver 的部分

| 维度 | Blueprint 当前不能验证 | 必须补的证据 |
| --- | --- | --- |
| Turn runtime | active turn 生命周期、terminal state 清理 | Rust 单测 / integration scenario |
| Steering | pending steering 是否进入下一次模型请求 | QueryLoop / ContextAssembly 单测，必要时 mock LLM |
| Cancel / interrupt | cancel token 是否打断当前 tool/child/turn | Rust runtime 单测 |
| Tool runtime | tool result 是否正确回到模型上下文 | Rust tool cycle tests |
| Child agent | child cancel 是否不误杀 parent | AgentRuntime / turn_runtime tests |
| DB/replay | `turn_steer` / `turn_cancelled` 是否可 replay | repository / restore tests |
| LLM 行为 | 模型是否根据 steering 改变计划 | scenario/manual evidence |
| UI 体验 | 文本是否不溢出、状态是否符合预期 | frontend tests + Playwright/截图/manual |
| Desktop UI control | Tauri WebView 是否可打开目标 view、关键元素是否可见、运行时错误是否为空 | UI control CI smoke / screenshot / manual |

## UI Control CI 的边界

UI control CI 属于 **scenario evidence**，不是 Blueprint Graph 的替代品。

适合验证：

- 真实 Tauri app 能启动，WebView bridge / Tauri invoke / UI control server 可用。
- 能执行 `ui_command` 打开目标 view，并断言 `chat.panel`、`chat.input` 等关键元素可见。
- 能检查 `noRuntimeErrors`、基础布局和交互入口。
- 能配合真实 IPC / 模型 smoke 产出端到端证据文件。
- 能补证真实桌面 route，例如 `ChatInput` 发送后由 `agent_turn_start` 驱动，并在当前聊天 tab 渲染/点击 `ask_user` QuestionCard。

不适合单独证明：

- Rust 内部 Agent runtime 调用链。
- LLM 是否一定会做出正确判断。
- 直接 IPC smoke 本身不等价于用户从当前聊天 tab 输入并点击问题卡；若要证明 UI route，必须增加真实输入框/按钮/卡片点击场景。
- replay / pending request 的重启恢复语义。

## 这次暴露的问题记录

### 2026-05-01 Agent Core P0 Batch 4

尝试把以下内容放进同一个 Batch 4 Blueprint target：

- `agent_turn_active / steer / cancel` IPC 链路
- `ChatInput.vue -> stream.ts -> agentTurnClient.ts` frontend 静态链路
- `agent_turn_steer -> AgentTurnRuntimeState` Rust 内部语义链路
- `query_loop -> AgentTurnRuntimeState` prompt/steering 消费链路

结果：

- `RustModule` / `CALLS` 不受 `GraphArtifactV1` 支持。
- `check` 能匹配 IPC 节点，但不适合验证 Vue/Store 和 Rust 内部语义。
- `check-frontend` 不抽 Tauri/Rust，所以混入 IPC/Rust target 会大量 missing。

已采用的修正方向：

1. `docs/plans/2026-05-01-agent-core-p0-batch4-steering-blueprint.md`：只验证 `agentTurnActive/Steer/Cancel -> IPC -> RustHandler`，evidence 为 `report.agent-core-p0-batch4-steering-api.md`。
2. `docs/plans/2026-05-01-agent-core-p0-batch4-steering-ui-blueprint.md`：只验证 `ChatInput/stream/agentTurnClient` 的 frontend 静态关系，evidence 为 `report.agent-core-p0-batch4-steering-ui.md`。
3. Rust 内部语义写入 Batch 4 implementation plan 的 waiver，并用 `cargo test turn_runtime --lib`、`pnpm test -- agent-turn-routing expert-timeline` 补证。

## 后续工具改进路线

| 优先级 | 改进项 | 目标 |
| --- | --- | --- |
| P0 | 明确 CLI domain 校验 | target 中混入当前命令不可抽取的 node kind 时，给出“请拆分 API/frontend target”的诊断 |
| P0 | 支持 Rust internal graph 基础抽取 | 识别 Rust `fn`、`struct impl`、method call、module import |
| P0 | 支持 event contract graph | 识别 Rust emit / TS listen / payload type / aggregator handler |
| P1 | 支持 persistence graph | 识别 repository insert/list、migration、restore projection |
| P1 | 支持 semantic evidence hooks | target edge 可声明由哪个 test/scenario 覆盖，而不是要求静态 extractor 识别 |
| P1 | 改善 node id 规范化 | source_path / symbol / id 的匹配规则更稳定，减少 target 写法猜测 |
| P2 | 可视化 diff | 把 target/actual/diff 画成可点击图，帮助审查 |

## 设计决策记录

| 决策点 | 选项 | 最终选择 | 原因 |
| --- | --- | --- | --- |
| Blueprint 是否作为 Agent runtime 唯一 gate | A) 是 / B) 否，作为结构证据之一 | B | 当前不能验证 LLM、runtime state、DB/replay、cancel token 等语义 |
| Target 是否混合 API/frontend/Rust semantic | A) 混合一个 block / B) 按 domain 拆分 | B | `check` 与 `check-frontend` actual graph 覆盖范围不同 |
| Rust 内部语义如何验证 | A) 写进 Blueprint 硬 gate / B) waiver + Rust tests | B | 当前 `RustModule` / `CALLS` 不支持，硬写会产生伪失败 |
| Agent Core P0 Batch 4 后续 target | A) 一个大 target / B) API target + UI target + semantic tests | B | 可验证边界清晰，避免把工具缺口误判为代码缺口 |
| UI control CI 归类 | A) 并入 Blueprint Graph / B) 作为 scenario evidence | B | UI control 验证真实桌面交互入口和运行时健康，不负责静态结构抽取或 Agent 语义证明 |
| direct IPC 与可见 UI smoke 分工 | A) 只保留 direct IPC / B) direct IPC 验证 runtime，visible UI smoke 验证用户入口 | B | 临时 session 的 IPC 调用不能证明当前聊天 tab 绑定；真实 UI 场景负责覆盖输入框、发送按钮和 QuestionCard 点击 |
