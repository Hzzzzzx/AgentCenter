# AgentCenter 当前功能 PRD

> 状态：当前功能 PRD 基线
> 最近更新：2026-05-15
> 功能盘点基线：`6f2e04164541ae51c9e11803dcdfb0fd7d25858d`（2026-05-14 10:26:04 +0800，`docs(project-context): slim enterprise provider guidance`）
> 说明：本文按上述 10:26 commit 的工程状态定义当前功能 PRD；之后 commit 中新增的内容视为后续演进或补充文档。
> 关联文档：[能力地图](./CURRENT-FUNCTIONAL-CAPABILITY-MAP.md) | [细粒度分工](./CURRENT-FUNCTIONAL-WORKBREAKDOWN.md)

## 一、产品目标

AgentCenter 是企业智能编排平台。当前阶段的产品目标不是做一个单纯聊天窗口，而是把企业项目数据、工作项、Agent 工作流、对话、用户确认、Skill/MCP 工具资源和产物审阅串成一个可演示、可分工、可验证的工作台闭环。

当前 PRD 以 M1/M2 交付为边界：

- M1：跑通“项目同步 -> 工作项 -> 启动工作流 -> 对话流式输出 -> 待确认 -> 产物预览”的主链路。
- M2：补齐团队可配置能力，包括项目级工作流、Skill/MCP 管理、历史版本、异常恢复、运行调试和验证证据。

## 二、用户角色

| 角色 | 核心诉求 |
|------|----------|
| 产品负责人 / PM | 看到当前项目下所有 FE/US 等事项，启动需求整理、方案设计、详细设计流程 |
| 技术负责人 | 查看任务处理进度、工作流阶段、产物、风险和需要人工决策的点 |
| 开发人员 | 在任务会话中和 Agent 协作，补充上下文、处理异常、查看产物 |
| QA / 验证人员 | 看到工作流结果、产物、执行历史和可回归证据 |
| 平台管理员 | 管理项目数据源、Skill、MCP、Runtime 资源和运行配置 |

## 三、核心用户旅程

### Journey 1：进入工作台并同步项目数据

1. 用户打开 AgentCenter 工作台。
2. 系统加载当前项目数据源。
3. 用户在运行设置或项目管理中选择 provider、项目、空间、迭代。
4. 用户点击同步数据。
5. 首页、看板、标题栏和工作项列表按当前 scope 刷新。

验收标准：

- 当前项目、空间、迭代来自后端同步源，不使用前端硬编码 mock。
- 切换 provider 后，工作项列表和首页统计跟随变化。
- 同步成功/失败有通知和同步历史。

### Journey 2：查看工作项全景

1. 用户在首页看到 FE/US/TASK/WORK/BUG/VULN 分类统计。
2. 用户按类型和状态筛选工作项。
3. 用户选择某个工作项，右侧面板展示详情和工作流进展。
4. 用户切换到任务看板，从节点状态角度查看事项分布。

验收标准：

- 首页能展示每类事项总数、运行中、待确认、异常、未开始、完成情况。
- 工作项列表支持类型、状态筛选和分页。
- 任务看板按 PENDING/RUNNING/READY/WAITING_CONFIRMATION/FAILED/COMPLETED/SKIPPED 分列。

### Journey 3：启动 Agent 工作流

1. 用户选择一个工作项。
2. 用户点击开始处理。
3. 系统为工作项创建 workflow instance、node instances 和任务会话。
4. 系统按默认或项目级 PRD/HLD/LLD 工作流启动第一个节点。
5. 用户可进入任务会话查看执行过程。

验收标准：

- 已开始或非初始状态的工作项不能重复创建冲突流程。
- 创建后的工作流与工作项、会话、节点正确绑定。
- 工作流模式支持自动运行、人工确认和 start-or-continue。

### Journey 4：任务会话中和 Agent 协作

1. 用户进入通用会话或任务会话。
2. 用户发送消息。
3. 系统持久化 USER 消息。
4. Bridge 调用 Runtime，前端通过 SSE 看到流式输出。
5. Runtime 输出完成后，系统保存最终 ASSISTANT 消息。
6. 用户可以取消运行、继续当前节点、进入下一步、重跑或跳过节点。

验收标准：

- 浏览器只连接 Java Bridge，不直连 OpenCode。
- 流式内容不被历史快照覆盖。
- 刷新后消息历史仍可恢复。
- cancel、retry、skip、continue 有明确成功/失败反馈。

### Journey 5：处理待确认和交互

1. Agent 需要用户输入、决策、审批、权限或异常恢复时，系统生成待确认。
2. 待确认出现在右侧面板，也可出现在会话内交互栏。
3. 用户处理交互，提交结构化 payload。
4. Bridge 将用户处理结果作为真实 USER 消息回灌当前节点。
5. 当前 Skill 继续执行，而不是默认完成节点。

验收标准：

- 支持 CONFIRM、APPROVAL、INPUT_REQUIRED、DECISION、EXCEPTION、PERMISSION。
- 处理待确认后，不误进入错误会话。
- 权限拒绝、允许、always 有不同语义。
- 处理后工作流状态和工作项投影刷新。

### Journey 6：查看和审阅产物

1. Agent 输出 Markdown/JSON/PATCH/REPORT 产物。
2. 系统捕获产物并绑定工作项、工作流、节点、会话。
3. 用户在右侧面板打开产物预览。
4. 后续可扩展为产物审阅、批注和退回修改。

验收标准：

- 产物不只存在聊天文本中，需要有独立 artifact 记录。
- 文件型产物只能从 runtime workspace 安全读取。
- 产物预览支持 Markdown。

### Journey 7：管理 Skill 和 MCP

1. 管理员进入 Skill 管理页，上传、更新、启用、停用、删除或刷新 Skill。
2. 管理员进入 MCP 管理页，导入配置、启用、停用、测试连接、刷新工具。
3. 工作流配置页只能引用当前项目可用 Skill。
4. 会话页能看到当前会话可用资源状态。

验收标准：

- Skill 和 MCP 操作由 Bridge 执行，前端不直接写文件。
- 操作有审计记录。
- Skill/MCP 变更后 Runtime 资源刷新。
- 编排引用不可用 Skill 时保存应失败或提示。

## 四、功能优先级

| 优先级 | 功能 |
|--------|------|
| P0 | 项目数据同步、工作项首页、启动工作流、任务会话、SSE 回复、待确认处理、产物预览 |
| P1 | 工作流配置新版、批量启动、节点重跑/跳过、Prompt Debug、Skill/MCP 管理、异常恢复 |
| P2 | 企业真实 Provider、A Runtime 接入、审计/权限/配额、产物审阅批注、完整 E2E evidence |

## 五、非目标

当前阶段不做：

- 多租户生产级权限体系。
- 复杂 MQ / 分布式工作流引擎替换。
- 前端直接调用 OpenCode。
- Runtime Adapter 作为业务主数据源。
- `.sisyphus/` 作为产品运行态数据源。
- 生产级 Secret Vault 和企业 IAM 深度接入。

## 六、统一验收口径

每个功能包完成时至少提供：

- 功能说明：用户怎么使用。
- HLD 边界：前端、Bridge、Runtime、DB 谁负责什么。
- LLD 范围：改了哪些页面、接口、Service、表或测试。
- 验证证据：前端 typecheck/test/build、Bridge Maven test、必要时 Playwright 截图或 `.sisyphus/evidence/`。
