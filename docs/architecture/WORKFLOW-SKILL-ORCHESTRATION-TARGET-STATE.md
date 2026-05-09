# Workflow Skill Orchestration Target State

> 状态：目标态设计草案
> 最近更新：2026-05-09
> 适用范围：AgentCenter 工作流编排、Skill 执行、多 Runtime Adapter、对话交互体验

## 背景

当前工作流实现仍带有 M1 阶段的工程化状态机假设：

- 工作流节点执行后，会尝试从 Skill 输出中解析交互点。
- 曾经存在“用户处理交互 = 当前节点完成 = 自动推进下一节点”的语义。
- Prompt 组装主要落在 OpenCode Adapter 内，容易把 AgentCenter 编排规则和 OpenCode 注入方式耦合在一起。
- 用户中途输入、Agent 多轮追问、多个交互同时出现时，节点完成边界不清晰。
- 前端交互更像审批单，而不是 Agent 在当前 Skill 中自然发起的多轮协作。

目标态不是继续强化代码状态机，而是让 AgentCenter 成为跨 Runtime 的 Skill 编排器：负责顺序、上下文、状态协议、交互承接和用户控制；具体节点是否完成、是否需要用户、是否继续执行，由 Agent/Skill 通过统一协议表达。

## 核心原则

1. **Workflow 只负责编排，不替 Agent 做业务判断。**
   工作流定义 Skill 调用顺序、输入输出承接关系和默认推进策略，不硬编码“看到某种文本就完成”。

2. **Skill/Agent 决定当前节点状态。**
   当前节点是否完成、是否需要用户、是否阻塞，由 Agent 在回复中输出统一节点状态协议。

3. **用户交互是当前 Skill 的多轮输入，不是节点完成闸门。**
   用户处理选择、确认、补充信息后，应作为真实 USER 消息回灌当前节点，再调用同一个 Skill 继续执行。

4. **默认手动推进，自动推进必须依赖 Agent 明确判断。**
   手动模式下，`READY_TO_ADVANCE` 只提示用户“进入下一节点”；自动模式下，只有 Agent 明确输出 `READY_TO_ADVANCE` 才进入下一节点。

5. **协议跨 Runtime，注入方式由 Adapter 决定。**
   AgentCenter 定义协议；OpenCode、OpenAI Agent、Claude、本地 Agent 等 Runtime 只负责把协议按自身能力注入。

## 目标分层

### 1. Workflow Definition

只描述静态编排：

- 节点顺序。
- 节点名称和 Skill 名称。
- 输入来源：工作项、上游产物、用户补充、上下文。
- 输出承接：最终 artifact 如何传给下游节点。
- 推进策略：手动 / 自动 / 审批后自动。

Workflow Definition 不包含业务判断 prompt，不决定某个 Skill 的内部交互流程。

### 2. Workflow Runtime Session

描述当前运行态：

- 当前 workflow instance。
- 当前 node instance。
- 当前节点状态。
- 当前节点交互历史。
- 当前节点 Agent 回复历史。
- 上游 artifact。
- 用户中途补充输入。

### 3. AgentCenter Node State Protocol

跨 Runtime 的轻量节点状态协议。Agent 每轮回复末尾应输出一个状态块：

```markdown
<!-- AGENTCENTER_NODE_STATE
status: READY_TO_ADVANCE | NEEDS_USER_INPUT | IN_PROGRESS | BLOCKED
reason: 一句话说明当前状态原因
artifact_title: 如果 READY_TO_ADVANCE，可给出建议产物标题
-->
```

状态语义：

| 状态 | 含义 | 工作流行为 |
|------|------|------------|
| `READY_TO_ADVANCE` | 当前 Skill 已形成最终结果，输入输出充分 | 保存最终 artifact；手动模式提示进入下一节点；自动模式可推进 |
| `NEEDS_USER_INPUT` | 当前 Skill 需要用户补充、选择、确认或授权 | 创建交互请求；当前节点等待用户；用户回答后恢复同一 Skill |
| `IN_PROGRESS` | 当前回复只是阶段性分析、草稿或普通说明 | 不推进、不创建交互；等待用户继续输入或 Agent 继续 |
| `BLOCKED` | 工具、权限、信息、运行时异常导致无法继续 | 创建异常/权限交互；当前节点阻塞 |

如果 Agent 没有输出状态块，默认视为 `IN_PROGRESS`，不得自动推进。

### 4. Interaction Protocol

当状态为 `NEEDS_USER_INPUT` 或 `BLOCKED` 时，Agent 可以在同一个状态块中声明一个或多个交互：

```markdown
<!-- AGENTCENTER_NODE_STATE
status: NEEDS_USER_INPUT
reason: 需要用户选择迁移方案并补充验收标准
interactions:
  - id: HLD-UIP-001
    type: DECISION
    title: 选择迁移方案
    question: 请选择本次 HLD 推荐的推进方案
    selection: single
    options:
      - id: A
        label: 双写
        description: 更安全，成本较高
      - id: B
        label: 直接切换
        description: 成本低，风险较高
    allow_custom: true
    required: true
  - id: HLD-UIP-002
    type: INPUT
    title: 补充验收标准
    question: 请补充上线验收标准
    fields:
      - id: acceptance
        label: 验收标准
        type: textarea
        required: true
-->
```

交互类型目标集合：

| 类型 | 前端控件 | 典型场景 |
|------|----------|----------|
| `ASK_USER` / `INPUT` | 文本框、多字段表单 | 补充背景、目标用户、验收标准 |
| `DECISION` | 单选、多选、分段选择、自定义输入 | 方案选择、范围选择、优先级取舍 |
| `APPROVAL` | 通过 / 退回 + 备注 | 明确审批一个结论或产物 |
| `ARTIFACT_REVIEW` | 产物预览 + 批注 + 通过 / 退回 | PRD/HLD/LLD 产物审阅 |
| `PERMISSION` | 权限说明 + 允许 / 拒绝 | 工具调用、文件访问、外部系统操作 |
| `CUSTOM_FORM` | 动态字段表单 | 多字段补充输入 |
| `RANKING` | 排序控件 | 多方案优先级排序 |
| `SCALE` | 评分/滑杆 | 风险等级、满意度、置信度 |
| `BLOCKER` | 异常说明 + 重试 / 跳过 / 取消 | Runtime 或工具异常 |

短期可以复用 `confirmation_request` 表存储交互，但语义应从“确认单”收敛为“Agent 交互请求”。

## Prompt 与 Runtime 解耦

### 通用协议层

新增通用组件：

- `WorkflowNodeStateProtocol`
  定义状态、交互 schema 和语义。

- `WorkflowNodeInstructionComposer`
  生成 Runtime 无关的节点执行说明。

- `WorkflowPromptComposer`
  组装工作项、节点、上游产物、交互历史和节点状态协议。

### Runtime Adapter 注入方式

不同 Runtime 用不同注入方式：

| Runtime 能力 | 注入方式 |
|--------------|----------|
| `systemPrompt` | 节点状态协议放 system/developer prompt，用户输入单独发送 |
| `userPromptOnly` | 将协议和输入一起放 user prompt，OpenCode 当前属于这一类 |
| `structuredOutput` | 用 JSON schema / function call 表达节点状态和 interactions |
| `metadataInstruction` | 通过 session metadata、配置文件或 runtime instruction 注入 |

OpenCode Adapter 不应拥有业务编排规则，只做 OpenCode 注入适配。

## 节点生命周期目标态

### 启动节点

1. Workflow 选择当前节点和 Skill。
2. Composer 生成当前节点输入：
   - 工作项编号、标题、描述、优先级。
   - 当前节点名称和 Skill。
   - 上游 artifact。
   - 用户补充输入。
   - 当前节点交互回答历史。
   - 节点状态协议。
3. Runtime Adapter 按能力注入。
4. 前端消息流展示一条真实 USER 输入卡片。

### Agent 回复

1. 后端保存普通 Agent 回复。
2. 后端解析 `AGENTCENTER_NODE_STATE`。
3. 根据状态更新节点：
   - `READY_TO_ADVANCE`：保存最终 artifact。
   - `NEEDS_USER_INPUT`：创建一个或多个交互请求。
   - `IN_PROGRESS`：保持当前节点，不推进。
   - `BLOCKED`：创建异常/权限交互，节点阻塞。

### 用户交互

1. Interaction Dock 显示待处理交互。
2. 用户选择、确认、补充、批注或授权。
3. 前端提交结构化 payload。
4. 后端写入真实 USER 消息。
5. 后端将回答追加到当前节点交互历史。
6. 后端恢复当前节点，再调用同一个 Skill。

### 节点完成

节点只在以下情况下完成：

1. Agent 输出 `READY_TO_ADVANCE`。
2. 产物保存成功。
3. 手动模式下，用户确认进入下一节点；自动模式下，策略允许自动推进。

交互处理、普通回复、草稿输出、无状态块输出都不应自动完成节点。

## 用户中途输入语义

如果存在当前运行或等待交互的节点，用户在对话输入框中的普通输入默认归属当前节点：

- “我补充一下...” -> 作为当前节点补充输入。
- “选 A，但是要控制成本” -> 可作为当前节点交互回答或补充。
- “重新跑这一节点” -> 重跑当前节点。
- “进入下一步” -> 如果当前节点 `READY_TO_ADVANCE`，完成并进入下一节点。
- “跳过这个节点” -> 标记跳过并进入下一节点。
- “暂停工作流” -> 停留当前节点，不再自动调度。

短期可以先不做自然语言意图识别，提供明确按钮：

- 继续当前 Skill。
- 进入下一节点。
- 重跑当前节点。
- 跳过当前节点。
- 暂停工作流。

## Interaction Dock 目标体验

交互不应铺在消息流里，也不应像左侧审批单。输入框上方固定一个可折叠 Dock：

```text
┌ 当前需要交互 3 ─────────────────────────── v ┐
│ [选择 · 方案] [输入 · 验收标准] [审批 · 产物] │
│                                                │
│ 选择迁移方案 · HLD-UIP-001                    │
│ Agent 问题：请选择本次 HLD 推荐的推进方案       │
│                                                │
│  ○ A. 双写，更安全，成本较高                    │
│  ○ B. 直接切换，成本低，风险较高                │
│  ○ C. 灰度发布，折中                            │
│                                                │
│  自定义补充 / 备注： [                         ] │
│                                                │
│  [返回给 Agent] [查看上下文]                    │
└────────────────────────────────────────────────┘
[ 输入指令或问题...                         发送 ]
```

行为：

- 多个交互用 tabs 切换。
- 每个 tab 根据 interaction type 渲染专属控件。
- 已处理 tab 显示完成态。
- 自动切到下一个未处理交互。
- 所有必填交互处理完后，批量回灌当前 Skill。
- 用户也可以直接在输入框补充，作为当前节点输入。

建议前端组件：

- `InteractionDock.vue`
- `InteractionTabList.vue`
- `InteractionRenderer.vue`
- `DecisionInteraction.vue`
- `InputInteraction.vue`
- `ApprovalInteraction.vue`
- `ArtifactReviewInteraction.vue`
- `PermissionInteraction.vue`

## 数据模型演进

短期复用 `confirmation_request`：

- `request_type` 映射 interaction type。
- `content` 存 question。
- `context_summary` 存 reason/context。
- `options_json` 从字符串数组升级为结构化 options JSON。
- `resolution_payload_json` 存用户结构化回答。

中期新增或重命名为 `interaction_request`：

| 字段 | 说明 |
|------|------|
| `id` | 交互 ID |
| `workflow_node_instance_id` | 所属节点 |
| `agent_session_id` | 所属会话 |
| `interaction_type` | DECISION / INPUT / APPROVAL 等 |
| `title` | tab 标题 |
| `question` | Agent 问题 |
| `schema_json` | 控件 schema |
| `context_json` | 上下文、关联产物、风险说明 |
| `status` | PENDING / IN_CONVERSATION / RESOLVED / REJECTED |
| `resolution_payload_json` | 用户回答 |
| `created_at` / `updated_at` | 时间 |

## 实施计划

### Phase 1：节点状态协议最小闭环

- 新增 `WorkflowNodeState`。
- 新增 `WorkflowNodeStateParser`。
- 新增 `WorkflowNodeInstructionComposer`。
- Prompt 增加 `AGENTCENTER_NODE_STATE` 协议。
- 后端解析状态块。
- 无状态块默认 `IN_PROGRESS`。
- `READY_TO_ADVANCE` 才保存 artifact。
- `NEEDS_USER_INPUT` 才创建交互。

验收：

- Skill 输出 `IN_PROGRESS` 不推进。
- Skill 输出 `NEEDS_USER_INPUT` 创建交互并停留当前节点。
- 用户回答后继续同一 Skill。
- Skill 输出 `READY_TO_ADVANCE` 后才允许进入下一节点。

### Phase 2：Interaction Dock 重做

- 前端新增 `InteractionDock`。
- 多交互 tab 切换。
- 根据 type 渲染单选、多选、文本、审批、产物审阅。
- optionsJson 支持结构化 options。
- 处理交互后生成真实 USER 消息。

验收：

- 同一节点多个交互可逐个处理。
- DECISION 显示真实选项，而不是只有同意/拒绝。
- INPUT 显示文本/字段输入。
- APPROVAL 只在审批型交互显示通过/退回。

### Phase 3：用户中途输入归属当前节点

- 当前节点运行/等待时，普通输入默认进入当前节点。
- 支持继续当前 Skill。
- 支持进入下一节点、重跑、跳过、暂停。
- 前端输入框根据当前节点状态显示提示。

验收：

- 用户直接输入补充内容后，同一 Skill 收到新上下文。
- 用户明确点击进入下一节点后才推进。

### Phase 4：Runtime 解耦

- 将协议文案从 OpenCode Adapter 移到通用 Composer。
- RuntimeCapabilities 增加 instruction injection 能力。
- OpenCode Adapter 暂时使用 user prompt 注入。
- 其他 Runtime 可使用 system/developer/structured output。

验收：

- OpenCode prompt debug 能看到 userPrompt 中包含协议。
- 新 Runtime 不需要复制 OpenCode prompt 拼装逻辑。

### Phase 5：清理旧 M1 模型

- 弱化 Markdown 交互表解析，仅作为兼容 fallback。
- 将 Confirmation 领域语义收敛到 Interaction。
- 移除“交互确认后自动完成节点”的旧测试假设。
- 自动推进变成显式策略，默认手动确认。

验收：

- 代码中不存在“普通交互 resolve 后直接 complete node”的路径。
- 自动模式只在 `READY_TO_ADVANCE` 下触发。

## 非目标

- 不让前端直接调用 OpenCode。
- 不让工作流配置变成业务 prompt 的硬编码集合。
- 不用代码规则替代 Agent 判断节点是否完成。
- 不要求所有 Runtime 使用同一种 prompt 注入方式。
- 不把每个交互都强制建模成审批。

## 风险与注意事项

- 解析 Markdown 注释块需要容错；解析失败必须默认 `IN_PROGRESS`。
- 自动推进必须可关闭，初期建议默认手动。
- Interaction schema 要向后兼容旧 `optionsJson` 字符串数组。
- 旧测试中“确认后推进下一节点”的断言需要按新语义重写。
- Debug Prompt 面板应继续保留，用于核查协议注入和实际发送内容。

