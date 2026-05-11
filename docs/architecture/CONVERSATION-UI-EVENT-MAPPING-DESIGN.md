# Conversation UI Event Mapping Design

> 状态：M1 已实施基线 / 持续演进
> 最近更新：2026-05-11
> 目标读者：AgentCenter 前端对话 UI、Runtime Translator、OpenCode/Codex Provider 实现者

---

## 1. 设计结论

AgentCenter 的对话 UI 不应该把 Agent 回复渲染成一堆并列卡片，也不应该按事件类型粗暴聚类成“工具调用组 / 思考组 / 结果组”。目标形态是：

```text
主回答：像 Codex 一样的文本优先展示，默认直接可读
执行过程：按真实发生顺序展开，工具、证据、决策挂在产生它们的步骤下面
调试视图：保留原始事件、payload、复制能力，但不污染主对话
```

核心规则：

```text
Raw Runtime Events
  -> 用 seqNo / createdAt / arrivalIndex / id 还原顺序
  -> 只合并同一对象生命周期
  -> 通过因果关系挂到父步骤
  -> 投影成主回答、执行过程、待交互、调试详情
```

允许合并的是“同一个东西的不同阶段”，例如同一个 `toolCallId` 的 start / delta / result，同一个 `partId` 的文本增量，同一个 `confirmationId` 的待确认生命周期。禁止把不同工具、不同文件、不同决策只因为类型相同就聚到一起。

---

## 2. 为什么要改

当前展示的问题不是单纯“样式丑”，而是信息结构错了：

- 主对话过窄，真正有用的 Agent 回复被挤进卡片和局部容器。
- 工具调用被聚合成一组后，用户看不出它在第几步发生、为什么发生、导致了什么结果。
- 待交互项重复展示事项编号、节点名、PRD 等上层标题已有信息，干扰用户判断。
- OpenCode / Codex 的事件语义被压平为 `SKILL_STARTED`、`SKILL_COMPLETED`、`PROCESS_TRACE` 后，前端只能展示“事件”，不能展示“工作过程”。
- Prompt Debug 能看到部分 prompt，但还不足以解释 Agent 到底回了什么、UI 为什么这样展示。

所以整改目标是：让用户先读到回答，再按需要展开过程；让过程保留真实顺序；让工具、产物、确认项都能解释“它属于哪一步”。

---

## 3. 当前代码状态

### 3.1 已具备的基础

- Bridge 已有 Runtime Event 持久化和 `seqNo` 机制，历史回放可以具备稳定顺序。
- 前端 `MessageList.vue` 已能把消息和 Runtime Event 混排。
- Prompt Debug 浮窗已经能展示 prompt_async 请求、部分 runtime 信息，并具备继续扩展为调试检查器的入口。
- `conversation-layered-turn-hifi.html` 原型已经验证了“文本主回答 + 有序执行步骤 + 步骤内挂工具/证据/决策”的方向。

### 3.2 主要缺口

- `OpenCodeRuntimeEventTranslator` 当前主要识别 `text`、`reasoning`、`tool`、`status`、`permission`、`error`，对 file / patch / snapshot / retry / compaction / subtask / agent handoff 等事件缺少结构化映射。
- `LegacyRuntimeEventBridge` 会把统一事件降级到旧枚举，容易丢失 OpenCode 原始语义和父子关系。
- `MessageList.vue` 当前会构造 `toolGroups`，并可能按工具名兜底聚合。这个策略在重复调用同名工具时会丢失因果顺序。
- `runtime.ts` 目前更像流式状态同步层，没有形成可复用的 conversation projection。
- 待交互栏的标题、tab、正文仍存在重复上层上下文的问题，需要按“问题是什么 / 选项是什么 / 为什么需要用户”重写信息层级。

---

## 4. 不可破坏的映射规则

### 4.1 允许合并

只允许合并同一对象生命周期：

| 对象 | 合并键 | 示例 |
|------|--------|------|
| 文本片段 | `messageId + partId` | text delta -> final text |
| 推理摘要 | `messageId + partId` | reasoning delta -> public summary |
| 工具调用 | `toolCallId` | started -> output delta -> completed |
| 权限/确认 | `confirmationId` / `permissionId` | asked -> selected -> resolved |
| 产物/文件 | `artifactId` / `filePath + revision` | created -> diff -> saved |
| 子任务/Agent | `agentRunId` / `subtaskId` | spawned -> running -> completed |

### 4.2 禁止聚类

以下聚合会丢失因果关系，不允许作为主对话结构：

- 全局“工具调用组”。
- 全局“证据组”。
- 全局“思考摘要组”。
- 全局“待确认组”脱离触发步骤。
- 只按 `eventType` 聚合。
- 没有 `toolCallId` 时按工具名合并多个调用。
- 把 Agent 的最终文本回复拆成多个卡片气泡。

---

## 5. 目标 Projection Model

前端应该先把 Runtime Event 归一化成稳定的投影视图，再交给组件渲染。

```ts
type ConversationTurnProjection = {
  turnId: string
  sessionId: string
  status: 'running' | 'waiting_input' | 'completed' | 'failed'
  answer: AnswerProjection
  steps: ExecutionStep[]
  pendingInteraction?: InteractionProjection
  debugRefs: RawEventRef[]
}

type AnswerProjection = {
  role: 'assistant'
  text: string
  sources?: ArtifactRef[]
  generatedByStepIds: string[]
}

type ExecutionStep = {
  id: string
  order: number
  kind:
    | 'context'
    | 'reasoning'
    | 'tool'
    | 'artifact'
    | 'decision'
    | 'status'
    | 'error'
    | 'subtask'
  title: string
  summary?: string
  status: 'pending' | 'running' | 'completed' | 'failed' | 'waiting_input'
  parentStepId?: string
  startedAt?: string
  completedAt?: string
  parts: StepPart[]
  rawEventRefs: RawEventRef[]
}

type StepPart =
  | TextPart
  | ReasoningSummaryPart
  | ToolInvocationPart
  | ArtifactEvidencePart
  | DecisionGatePart
  | StatusPart
  | ErrorPart
  | RawEventPart
```

### 5.1 ToolInvocationPart

```ts
type ToolInvocationPart = {
  type: 'tool'
  toolCallId: string
  rawName: string
  displayName: string
  category?: 'read' | 'search' | 'list' | 'command' | 'skill' | 'tool'
  status: 'running' | 'completed' | 'failed'
  inputSummary?: string
  outputSummary?: string
  artifactRefs?: ArtifactRef[]
  rawPayloadRef: RawEventRef
  defaultExpanded: boolean
}
```

工具不是主层级；工具是某一步采取的动作。只有短结果、失败、权限拦截、产物生成类工具默认展开，其余默认折叠。

`displayName` 必须是用户能读懂的动作摘要，而不是底层事件名。例如：

| 原始工具 | 用户可见文案 |
|----------|--------------|
| `read` / `read_file` | `读取文件 MessageList.vue` |
| `grep` / `rg` / `search` | `搜索代码 ASSISTANT_DELTA` |
| `ls` / `glob` | `查看目录 components/conversation` |
| `bash` / `exec` | `执行命令 npm run typecheck` |
| workflow Skill | `调用 hld-design` |

折叠摘要应该汇总动作类型，例如 `已处理 3 个步骤 · 读取 1 个文件 · 搜索 1 次 · 调用 1 个 Skill`。不要把 `running`、`completed`、单独 token 或同名 Skill 重复渲染成多行。

### 5.2 DecisionGatePart

```ts
type DecisionGatePart = {
  type: 'decision'
  confirmationId: string
  question: string
  prompt?: string
  options: DecisionOption[]
  status: 'waiting' | 'submitted' | 'resolved'
  defaultExpanded: true
}
```

确认项的 UI 文案只回答：

- 现在要用户决定什么？
- 推荐选项是什么？
- 每个选项的后果是什么？
- 提交后会进入哪个步骤？

不要重复事项编号、节点名、PRD/HLD 这类上层标题已经展示的信息。

---

## 6. 顺序与因果

### 6.1 排序键

Runtime Event 的展示顺序必须稳定：

```text
seqNo ASC
createdAt ASC
arrivalIndex ASC
id ASC
```

- 历史回放优先使用 Bridge 下发的 `seqNo`。
- 实时流中如果事件尚未落库，可用前端接收顺序生成 `arrivalIndex`。
- 相同时间戳不能作为唯一排序依据。

### 6.2 因果挂载

因果关系优先级：

1. 显式父子字段：`parentStepId`、`parentEventId`、`messageId`、`partId`、`toolCallId`、`confirmationId`。
2. Runtime 提供的上下文：`workflowNodeId`、`operationId`、`agentRunId`、`subtaskId`。
3. 顺序兜底：把没有父级的事件挂到最近的 running step。
4. 最后兜底：创建一个独立步骤，但仍保留真实位置，不移到全局桶。

### 6.3 待交互归属

待交互不是全局尾部卡片。`confirmationId` 对应的交互必须按创建事件的时间戳归属到触发它的 Agent 回合：

```text
Agent 输出/步骤
  -> interaction request marker
  -> 对话内联交互卡
后续用户输入
  -> 新的用户消息
```

如果后续已经有新的用户消息，未处理的交互卡仍停留在创建它的回合下方，不允许被重新移动到会话底部，也不允许压到用户输入之后导致顺序错乱。

---

## 7. OpenCode / Codex 映射

### 7.1 OpenCode

| OpenCode 语义 | AgentCenter Projection | UI 行为 |
|---------------|------------------------|---------|
| `message.updated` | user/assistant message metadata | 更新 turn / message 边界，不单独占一行 |
| text part delta | `AnswerProjection.text` 或 `TextPart` | 主回答文本流式更新 |
| reasoning part | `ReasoningSummaryPart` | 默认折叠，只展示公开摘要 |
| tool part | `ToolInvocationPart` | 挂到当前步骤，按 `toolCallId` 合并生命周期 |
| permission / input required | `DecisionGatePart` | 挂到触发步骤，等待时展开 |
| status / idle / running | `StatusPart` | 作为状态，不制造噪音消息 |
| file / patch / artifact | `ArtifactEvidencePart` | 挂到生成步骤，展示摘要和 diff 入口 |
| error | `ErrorPart` | 展开，保留原始 payload 复制入口 |

### 7.2 Codex 桌面 UI 可观察语义

| Codex 语义 | AgentCenter Projection | UI 行为 |
|------------|------------------------|---------|
| assistant final | `AnswerProjection` | 文本优先，不包卡片 |
| commentary update | `StatusPart` / `TextPart` | 可作为过程摘要，不替代最终回答 |
| tool call / result | `ToolInvocationPart` | 按真实执行步骤挂载 |
| apply_patch / file edits | `ArtifactEvidencePart` | 显示文件、diff、验证结果 |
| subagent | `subtask` step | 展开可见任务、状态、结果 |
| private analysis | 不展示 | 只展示可公开摘要或 runtime 明确返回内容 |

---

## 8. UI 渲染合同

### 8.1 主对话

- Assistant 主回答是正文，不是气泡卡片。
- 正文宽度应该优先保证可读性，不被交互面板挤压成窄列。
- 只有用户消息、确认项、错误等需要明确边界的内容才使用卡片或面板。

### 8.2 执行过程

- “执行过程”默认可折叠，展示步骤数量和当前状态。
- 展开后按真实顺序显示编号步骤。
- 每个步骤内部可以包含工具、产物、状态、确认项。
- 工具输出默认折叠；失败、权限、短输出、产物摘要可以默认展开。
- 运行中的步骤可以有轻微呼吸反馈，但不使用强烈颜色，也不展示“等待 Runtime 返回”等底层字样。

### 8.3 待交互

- 待交互卡放在触发它的 Agent 回合下方；全局输入区只保留继续补充输入能力。
- 待交互 tab 放在标题区域，便于切换多个问题。
- tab 文案使用 `问题 1 / 问题 2 / 问题 3` 和短标题，不重复事项编号与节点名。
- 正文区域聚焦问题、选项、推荐理由和提交动作。

### 8.4 Prompt Debug

Prompt Debug 是调试器，不是主 UI：

- 展示本轮 prompt_async 请求。
- 展示所有 Agent 回复段，包括 text、reasoning summary、tool、status、permission、error、artifact。
- 每段显示“作用注释”：事件类型、UI 映射、为什么展示/折叠。
- 每段可一键复制；整体也可复制。
- 支持放大全屏。

---

## 9. 原型基线

当前高保真原型：

- [conversation-layered-turn-hifi.html](../../agentcenter-web/public/prototypes/conversation-layered-turn-hifi.html)

关键视觉方向：

- 主回答使用文本优先布局。
- 执行过程按步骤编号，不按工具类型聚合。
- 工具、证据、决策挂在对应步骤下面。
- 右侧映射说明明确写出“禁止不恰当聚类”。

---

## 10. 验收标准

实现后至少满足以下检查：

1. Agent 最终回答在主对话中以文本形式出现，不被拆成多张卡片。
2. 工具调用按照真实发生顺序展示，且挂在触发它的执行步骤下面。
3. 同名工具连续调用两次，如果 `toolCallId` 不同，必须展示为两个调用。
4. 待确认项不重复事项编号、节点名、PRD/HLD 等上层标题信息。
5. 待确认 tab 放在标题区域，可清楚切换多个问题。
6. 产物、diff、文件证据挂在生成它们的步骤下面。
7. 历史回放和实时流展示顺序一致。
8. Prompt Debug 能复制每段 Agent 回复和原始 payload。
9. 错误、权限、等待用户输入不会被隐藏在折叠工具组里。
10. 前端组件测试覆盖 repeated same-name tool calls、out-of-order timestamp、waiting input、error event。
11. `ASSISTANT_DELTA` / `ASSISTANT_COMPLETED` 只能进入主回答流式文本，不得变成执行步骤。
12. read/search/list/command/skill 必须展示为可理解动作摘要，且完成后可折叠到一行摘要。
13. 产物系统消息中的 `FE2001 详细设计 (LLD).md` 这类带空格文件名必须可点击跳转产物。
14. 待交互卡必须保持在触发回合中，后续用户输入不得改变它的相对位置。

---

## 11. 与现有设计的关系

本设计补充 [AGENT-RUNTIME-PROTOCOL-LAYER-DESIGN.md](AGENT-RUNTIME-PROTOCOL-LAYER-DESIGN.md)：

- Runtime Protocol 负责定义“事件从哪里来、怎么标准化”。
- 本文负责定义“事件如何投影成用户能读懂的对话 UI”。
- Bridge 仍是主数据源，前端不直接调用 OpenCode。
- Prompt Debug 可以展示更多原始事件，但主对话只展示经过 projection 的结果。
