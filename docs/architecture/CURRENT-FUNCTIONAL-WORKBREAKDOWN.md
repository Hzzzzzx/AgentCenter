# AgentCenter 当前功能细粒度分工表

> 状态：团队分工草案
> 最近更新：2026-05-15
> 功能盘点基线：`6f2e04164541ae51c9e11803dcdfb0fd7d25858d`（2026-05-14 10:26:04 +0800，`docs(project-context): slim enterprise provider guidance`）
> 说明：本文按上述 10:26 commit 的工程状态拆分人工任务；之后 commit 中新增的内容视为后续演进或补充文档。
> 关联：[当前功能能力地图](./CURRENT-FUNCTIONAL-CAPABILITY-MAP.md)

本文档把功能拆到可分派任务包粒度。每个任务包建议有一个主 owner，跨前后端协作时以该 owner 负责验收闭环。

## 标记说明

| 标记 | 含义 |
|------|------|
| P | PRD / 产品边界与验收 |
| A | HLD / 架构边界、接口、数据流 |
| D | LLD / 代码实现、测试、联调 |

## 0. 产品与验收底座

| 编号 | 功能包 | 类型 | 交付内容 | 建议 owner |
|------|--------|------|----------|------------|
| 0.1 | 产品能力地图 | P | 当前能力域、优先级、演示主链路 | PM / 架构 |
| 0.2 | 用户角色与场景 | P | PM、技术负责人、开发、QA、管理员场景 | PM |
| 0.3 | 验收矩阵 | P/A | 每个功能包的完成标准和测试方式 | QA / Tech Lead |
| 0.4 | 演示脚本 | P/D | 从项目同步到产物审阅的 demo path | PM / QA |

## 1. 工作台壳层与全局上下文

| 编号 | 功能包 | 类型 | 交付内容 | 建议 owner |
|------|--------|------|----------|------------|
| 1.1 | 顶部项目上下文 | D | 当前项目、迭代下拉、同步后选项刷新 | 前端 |
| 1.2 | 三栏工作台布局 | D | 左侧栏、中工作区、右侧栏、折叠/扩展 | 前端 |
| 1.3 | 左侧导航 | D | 首页、任务看板、任务编排、设置入口 | 前端 |
| 1.4 | 会话列表 | D | 通用会话、任务会话分组和选中态 | 前端 |
| 1.5 | 右侧详情面板 | D | 待确认、事项详情、产物预览 tab | 前端 |
| 1.6 | 主题系统 | D | 主题切换、token、持久化 | 前端 |
| 1.7 | 通知气泡 | D | 成功/失败/警告通知，按 anchor 展示 | 前端 |

## 2. 项目数据源与工作项池

| 编号 | 功能包 | 类型 | 交付内容 | 建议 owner |
|------|--------|------|----------|------------|
| 2.1 | ProjectDataProvider 契约 | A | Provider、snapshot、sync、scope 字段 | 后端集成 |
| 2.2 | 数据源切换 | D | active provider API 和运行设置页 | 后端 + 前端 |
| 2.3 | 项目数据同步 | D | context / space / iteration / work item 写库 | 后端 |
| 2.4 | 稳定 scope 查询 | A/D | providerId + external project/space/iteration 查询 | 后端 |
| 2.5 | 同步历史 | D | sync history 表、查询、失败信息 | 后端 |
| 2.6 | 企业 Provider 模板 | A/D | 内部系统接入指南和 fixture 示例 | 后端集成 |

## 3. 工作项全景与看板

| 编号 | 功能包 | 类型 | 交付内容 | 建议 owner |
|------|--------|------|----------|------------|
| 3.1 | 统一工作项模型 | A/D | FE/US/TASK/WORK/BUG/VULN 字段和状态 | 后端 |
| 3.2 | 工作项列表与筛选 | D | list API、scope 参数、Pinia store | 前后端 |
| 3.3 | 首页统计卡 | D | 类型统计、运行/等待/异常/未开始聚合 | 前后端 |
| 3.4 | 首页工作项列表 | D | 类型筛选、状态筛选、分页、流程摘要 | 前端 |
| 3.5 | 批量启动 | D | 同类型批量 start workflow、上限配置 | 前后端 |
| 3.6 | 任务看板 | D | 按节点状态分列，点击进入详情 | 前端 |
| 3.7 | 事项详情 | D | 描述、优先级、流程进展、历史版本 | 前端 |

## 4. Agent-first 工作流编排

| 编号 | 功能包 | 类型 | 交付内容 | 建议 owner |
|------|--------|------|----------|------------|
| 4.1 | 默认工作流定义 | A/D | 各事项类型 PRD/HLD/LLD 默认节点 | 后端 |
| 4.2 | 项目级工作流 | A/D | workflow_definition.project_id 隔离 | 后端 |
| 4.3 | 编排配置页 | D | Skill 池、阶段草案、路线图 | 前端 |
| 4.4 | 保存工作流新版 | D | 禁用旧版、插入新版、默认版本切换 | 后端 |
| 4.5 | Skill 可用性校验 | D | 保存前校验 enabled + valid Skill | 后端 |
| 4.6 | 阶段蓝图可解释 | D | PRD/HLD/LLD 路线、输入策略、产物类型展示 | 前端 |

## 5. 工作流执行引擎

| 编号 | 功能包 | 类型 | 交付内容 | 建议 owner |
|------|--------|------|----------|------------|
| 5.1 | 启动工作流 | D | start-workflow API，创建实例、节点、会话 | 后端 |
| 5.2 | 重启工作流 | D | 旧实例历史保留，新实例成为当前版本 | 后端 |
| 5.3 | 执行模式 | A/D | AUTO / MANUAL_CONFIRM / START_OR_CONTINUE | 前后端 |
| 5.4 | 节点生命周期 | A/D | PENDING/RUNNING/READY/WAITING/FAILED/COMPLETED | 后端 |
| 5.5 | Prompt 组装 | D | 工作项、上游产物、交互历史、协议说明 | 后端 |
| 5.6 | 节点状态协议 | A/D | IN_PROGRESS / READY_TO_ADVANCE / NEEDS_USER_INPUT / BLOCKED | 后端 |
| 5.7 | 进入下一步 | D | READY 后 continue，下游节点执行 | 后端 |
| 5.8 | 重跑 / 跳过节点 | D | retry / skip API 和 UI 控件 | 前后端 |
| 5.9 | 上下文锚点 | D | 会话压缩后注入当前节点和上游产物锚点 | 后端 |

## 6. 对话工作台与实时运行

| 编号 | 功能包 | 类型 | 交付内容 | 建议 owner |
|------|--------|------|----------|------------|
| 6.1 | 通用会话 | D | GENERAL session 创建、列表、消息 | 前后端 |
| 6.2 | 任务会话 | D | WORK_ITEM session 绑定事项和工作流 | 前后端 |
| 6.3 | 消息持久化 | D | USER/ASSISTANT/TOOL、seq_no、status | 后端 |
| 6.4 | 发送消息 | D | REST 发送用户输入，异步调用 Runtime | 后端 |
| 6.5 | SSE 事件流 | D | `/api/agent-sessions/{id}/events` 和 EventSource | 前后端 |
| 6.6 | 流式文本投影 | D | delta 合并、去重、最终消息回拉 | 前端 |
| 6.7 | 取消运行 | D | cancel API、Runtime cancel、UI loading | 前后端 |
| 6.8 | Prompt Debug | D | 展示 prompt_async 请求、输入和事件时间线 | 前端 |

## 7. 人在环交互 / 待确认

| 编号 | 功能包 | 类型 | 交付内容 | 建议 owner |
|------|--------|------|----------|------------|
| 7.1 | 待确认列表 | D | 右侧待确认列表、选中、数量 badge | 前端 |
| 7.2 | 进入确认会话 | D | confirmation.enter-session 精准进入任务会话 | 前后端 |
| 7.3 | 决策交互 | D | 单选、多选、自定义输入 | 前端 |
| 7.4 | 输入补充 | D | 文本、多字段表单回灌 Agent | 前后端 |
| 7.5 | 审批交互 | D | 通过、退回、备注 | 前后端 |
| 7.6 | 权限确认 | D | allow / reject / always，同类权限处理 | Runtime + 前端 |
| 7.7 | 异常恢复 | D | 重试、跳过、取消、补充恢复指令 | 前后端 |
| 7.8 | 会话内交互栏 | D | 多交互 tabs，处理后继续当前 Skill | 前端 |

## 8. Runtime 协议与 Provider

| 编号 | 功能包 | 类型 | 交付内容 | 建议 owner |
|------|--------|------|----------|------------|
| 8.1 | RuntimeGateway | A/D | 应用层唯一 Runtime 入口 | Runtime |
| 8.2 | Provider Registry | A/D | 按 RuntimeType 选择 OPENCODE / A_RUNTIME | Runtime |
| 8.3 | 协议信封 | A/D | Command / Event / Ack 类型和字段 | Runtime |
| 8.4 | OpenCode 进程管理 | D | 启动、复用、端口探测、工作目录 | Runtime |
| 8.5 | OpenCode 会话映射 | D | 平台 session 与 runtime_session_id 绑定 | Runtime |
| 8.6 | OpenCode 命令发送 | D | prompt_async、skill run、cancel | Runtime |
| 8.7 | OpenCode SSE 订阅 | D | 消费 `/event`，处理重连和事件归属 | Runtime |
| 8.8 | 事件翻译 | D | OpenCode 原始事件 -> AgentCenter RuntimeEvent | Runtime |
| 8.9 | A Runtime 骨架 | A/D | WebSocket/CommandTransport 接入预留 | Runtime |

## 9. Skill / MCP / 运行资源治理

| 编号 | 功能包 | 类型 | 交付内容 | 建议 owner |
|------|--------|------|----------|------------|
| 9.1 | Skill 列表 | D | 项目 Skill catalog、搜索、状态 | 前后端 |
| 9.2 | Skill 上传 / 更新 | D | ZIP 校验、安装、版本记录 | 后端 |
| 9.3 | Skill 启停删除 | D | enable / disable / delete / audit | 后端 |
| 9.4 | Skill 刷新 | D | 扫描 `.opencode/skills`，通知 Runtime | 后端 |
| 9.5 | MCP 导入 | D | 读取项目 MCP 配置并入库 | 后端 |
| 9.6 | MCP 启停测试 | D | enable / disable / test health | 后端 |
| 9.7 | MCP 工具快照 | D | tool snapshot、schema 摘要 | 后端 |
| 9.8 | 会话资源状态 | D | 当前会话 Skill/MCP 数量和 reloadRequired | 前后端 |

## 10. 产物与证据闭环

| 编号 | 功能包 | 类型 | 交付内容 | 建议 owner |
|------|--------|------|----------|------------|
| 10.1 | 产物捕获 | D | 从 Agent 输出捕获 Markdown/JSON/PATCH/REPORT | 后端 |
| 10.2 | 产物落库 | D | 绑定 work item / workflow / node / session | 后端 |
| 10.3 | 产物预览 | D | 右侧 ArtifactViewer、Markdown 渲染 | 前端 |
| 10.4 | 文件型产物读取 | D | runtime workspace 边界校验和文件读取 | 后端 |
| 10.5 | 产物审阅闭环 | A/D | ARTIFACT_REVIEW 绑定实际产物和处理结果 | 前后端 |

## 11. 启动、验证与运维体验

| 编号 | 功能包 | 类型 | 交付内容 | 建议 owner |
|------|--------|------|----------|------------|
| 11.1 | 一键启动脚本 | D | start / stop / restart / dev / status | DevOps |
| 11.2 | 环境检查 | D | Java、Node、Maven、opencode、auth 检查 | DevOps |
| 11.3 | 数据库迁移验证 | D | Flyway migration test | 后端 QA |
| 11.4 | 测试数据重置 | D | reset-test-data 支持 count/type/dry-run | QA |
| 11.5 | 前端组件测试 | D | Shell、首页、看板、对话、交互、资源页 | 前端 QA |
| 11.6 | 后端集成测试 | D | workflow、session、runtime、confirmation、mapper | 后端 QA |
| 11.7 | UI Evidence | D | 关键页面 Playwright 截图和 evidence 归档 | QA |

## 建议优先级

### P0 主链路

`2.1-2.4 -> 3.1-3.5 -> 4.1-4.5 -> 5.1-5.7 -> 6.1-6.6 -> 7.1-7.4 -> 8.4-8.8 -> 10.1-10.3`

### P1 可配置与恢复

`4.6 -> 5.8-5.9 -> 6.7-6.8 -> 7.5-7.8 -> 9.1-9.8 -> 10.4-10.5`

### P2 企业化与治理

`2.6 -> 8.9 -> 11.x` 以及权限、审计、配额、真实企业 Provider / Runtime 合同测试。
