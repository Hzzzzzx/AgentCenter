# 天元文档索引

> 天元 (TianYuan) - AI Native Desktop Agent IDE，参考 VSCode 架构

---

## 文档目录

### 设计文档

| 文档                                                                                            | 说明                                                                                                         |
| ----------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| [docs/系统设计.md](系统设计.md)                                                                 | 项目架构总览 (4+1 视图)                                                                                      |
| [docs/API设计.md](API设计.md)                                                                   | REST API 规范设计                                                                                            |
| [docs/日志追踪体系设计.md](日志追踪体系设计.md)                                                 | 日志与追踪系统设计                                                                                           |
| [docs/code-review-tianyuan-vs-vscode.md](code-review-tianyuan-vs-vscode.md)                     | 代码质量审查报告                                                                                             |
| [docs/reference-projects-analysis.md](reference-projects-analysis.md)                           | 参考项目分析与架构建议                                                                                       |
| [docs/ui-layout-spec.md](ui-layout-spec.md)                                                     | UI 布局设计规范                                                                                              |
| [docs/ide-capability-gap-analysis.md](ide-capability-gap-analysis.md)                           | IDE 基础能力差距分析                                                                                         |
| [docs/workbench/fluid-workbench-design.md](workbench/fluid-workbench-design.md)                 | Fluid Workbench 可组合布局设计（Focus / Dock / Multi Chat / Detached Window）                                |
| [docs/workbench/agent-test-navigation-design.md](workbench/agent-test-navigation-design.md)     | Agent 测试导航与内部 UI 控制规范（直达页面、隐藏首页、driver 操控）                                          |
| ~~docs/plans/novel-mode-design.md~~ → [归档](history/2026-04-19-novel-v2-pre-plotpilot-rebase/) | 小说写作模式设计（已归档）                                                                                   |
| [docs/tool-and-permission-design.md](tool-and-permission-design.md)                             | 工具系统与权限体系设计 (22 工具 + 10 层权限链)                                                               |
| [docs/llm/global-model-configuration-redesign.md](llm/global-model-configuration-redesign.md)   | 全局模型配置重设设计（Provider / Model / Defaults / Keychain / 本地发现统一）                                |
| [docs/llm/local-llm-auto-bridge-design.md](llm/local-llm-auto-bridge-design.md)                 | 本地 LLM 自动发现、同步与启动设计（Ollama + LM Studio）                                                      |
| [docs/editor/git-blame-gutter-design.md](editor/git-blame-gutter-design.md)                     | 编辑器 Git Blame Gutter 显示设计                                                                             |
| [docs/sidebar/chevron-icon-standard.md](sidebar/chevron-icon-standard.md)                       | UI 图标规范 — 全局 SVG 图标约束                                                                              |
| [docs/ui-control-plane/ai-ui-control-plane-design.md](ui-control-plane/ai-ui-control-plane-design.md) | AI UI Control Plane 设计（Observe / Act / Verify / Diagnose，支持内置 Agent、CLI、测试与诊断）          |
| [docs/sdd/blueprint-graph-architecture.md](sdd/blueprint-graph-architecture.md)                 | SDD Blueprint Graph 架构基调（Agent/SDD 工具先行，v1.2 支持 frontend contract graph，后续产品化进 TianYuan） |
| [docs/sdd/blueprint-graph-tool-boundaries.md](sdd/blueprint-graph-tool-boundaries.md)           | Blueprint Graph 工具边界与改进方向（支持的 node/edge、API/frontend target 拆分、Agent runtime 语义 waiver） |
| [docs/sdd/sdd-v1-1-graph-verified-development.md](sdd/sdd-v1-1-graph-verified-development.md)   | SDD v1.1 图验证开发规范（适用性规则、计划模板契约、F1/Final Wave/Design Sync 增补、显式非目标）              |

### SDD 改进方案 & 参考调研（v3, 2026-05-03）

| 文档                                                                                                            | 说明                                                                         |
| --------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| [docs/reference/sdd-improvement-plan.md](reference/sdd-improvement-plan.md)                                     | SDD 开发体系改进方案 v3（五级场景 + 精度约束 + 资产维护 + 工具基础设施）      |
| [docs/reference/sdd-evaluation-framework.md](reference/sdd-evaluation-framework.md)                             | 面向 AI 编程时代的全流程评估框架（5 阶段 16 维度）                            |
| [docs/reference/sdd-cross-project-comparison.md](reference/sdd-cross-project-comparison.md)                     | 六大 SDD 项目横向对比（OpenSpec/GSD/Superpowers/ECC/Chrys/TianYuan）       |
| [docs/reference/knowledge-graph-tools-survey.md](reference/knowledge-graph-tools-survey.md)                     | 知识图谱 & 项目理解工具横向调研清单（含 14 张图谱数据）                       |
| [docs/reference/chrys-agent-platform-analysis.md](reference/chrys-agent-platform-analysis.md)                   | Chrys Agent 平台专项分析                                                     |
| [AGENTS.md](../AGENTS.md)                                                                                      | TianYuan Agent 行为合同（场景分级 + 精度约束 + brainstorming + 工具优先级）   |
| [.opencode/prompts/tool-usage-guide.md](../.opencode/prompts/tool-usage-guide.md)                               | Agent 工具使用指南（代码理解工具优先级链）                                    |
| [.opencode/skills/brainstorming-tianyuan/](../.opencode/skills/brainstorming-tianyuan/SKILL.md)                 | TianYuan 专属 brainstorming skill（场景定制版）                               |
| [.sisyphus/cards/schema.json](../.sisyphus/cards/schema.json)                                                  | 审查卡片 JSON Schema                                                        |
| [.sisyphus/pending-L6.md](../.sisyphus/pending-L6.md)                                                          | 紧急修复追踪队列                                                             |
| [scripts/hooks/pre-commit-security.sh](../scripts/hooks/pre-commit-security.sh)                                | Pre-commit 安全扫描脚本                                                      |

### 小说模块设计

| 文档                                                                                                      | 说明                                        |
| --------------------------------------------------------------------------------------------------------- | ------------------------------------------- |
| [docs/novel/summary-pyramid-snapshot-restore-design.md](novel/summary-pyramid-snapshot-restore-design.md) | 金字塔摘要体系 + 快照恢复设计               |
| [docs/novel/plotpilot-1to1-ui-api-contract-plan.md](novel/plotpilot-1to1-ui-api-contract-plan.md)         | PlotPilot 1:1 UI 整改 + 前后端 API 契约计划 |
| [docs/novel/plotpilot-reference-ui-inventory.md](novel/plotpilot-reference-ui-inventory.md)               | PlotPilot 参考原型 UI 元素、按钮、tab、状态清单 |
| [docs/novel/plotpilot-reference-parity-design.md](novel/plotpilot-reference-parity-design.md)             | PlotPilot 参考功能点复刻设计矩阵与冲突决策 |
| [docs/novel/plotpilot-reference-function-matrix.md](novel/plotpilot-reference-function-matrix.md)          | PlotPilot 参考 UI ID 到当前代码、缺口和验收点的功能矩阵 |
| [docs/novel/plotpilot-remaining-strict-parity-plan.md](novel/plotpilot-remaining-strict-parity-plan.md)    | PlotPilot 剩余 22 个部分对齐项的严格复刻计划、百分比口径和执行批次 |
| [docs/novel/novel-module-user-guide.md](novel/novel-module-user-guide.md)                                  | 小说模块功能对齐结论与场景化使用说明 |
| [docs/novel/novel-ui-control-regression-design.md](novel/novel-ui-control-regression-design.md)            | 小说模块 UI Control 回归测试臂、场景分层与稳定锚点设计 |
| [docs/novel/novel-function-ui-contract.md](novel/novel-function-ui-contract.md)                            | 小说模块功能、UI 元素、布局、状态与 UI Control 场景的可测试合同清单 |
| [docs/novel/plotpilot-latest-change-inventory-2026-05-01.md](novel/plotpilot-latest-change-inventory-2026-05-01.md) | PlotPilot 最新目标态与 TianYuan 当前小说模块的功能、布局、接口、存储差距矩阵 |
| [docs/novel/plotpilot-latest-sync-improvement-plan-2026-05-01.md](novel/plotpilot-latest-sync-improvement-plan-2026-05-01.md) | 基于最新目标态差距矩阵的分批同步改进计划 |

### 子模块设计

| 文档                                                                                      | 说明                                                                            |
| ----------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| [docs/memory-system/memory-system-design.md](memory-system/memory-system-design.md)       | 记忆系统 V1 设计（含 Karpathy 启发）                                            |
| [docs/memory-system/memory-system-design-v2.md](memory-system/memory-system-design-v2.md) | 记忆系统 V2 设计（四类正交记忆 + OpenSpace 借鉴）                               |
| [docs/pet/codex-inspired-pet-capability-design.md](pet/codex-inspired-pet-capability-design.md) | 宠物能力增强设计（Codex spritesheet、9 行动作图集、24 表情折叠、TianYuan 管家宠物运行时） |
| [docs/android-remote-mvp/README.md](android-remote-mvp/README.md) | Android Remote MVP 文档包（章程、开发验证、打包分发 CI/CD、调试 runbook） |
| [docs/android-remote-mvp/00-mvp-charter.md](android-remote-mvp/00-mvp-charter.md) | Android 第一方指挥端 MVP 总章程（边界、目录、阶段、Done 标准） |
| [docs/android-remote-mvp/01-development-validation-plan.md](android-remote-mvp/01-development-validation-plan.md) | Android Remote MVP 开发、真机验证和中枢接入计划 |
| [docs/android-remote-mvp/02-packaging-distribution-cicd.md](android-remote-mvp/02-packaging-distribution-cicd.md) | Android Remote 打包、分发、OTA、签名和 CI/CD 流程 |
| [docs/android-remote-mvp/03-debugging-runbook.md](android-remote-mvp/03-debugging-runbook.md) | Android Remote 调试 runbook（网络、hello、日志、升级、CI 失败定位） |
| [docs/zhongshu/README.md](zhongshu/README.md)                                   | 中枢设计包（Run 控制中心、管家提醒、远程通道和共享 Agent Runtime 顶层设计） |
| [docs/zhongshu/00-architecture-charter.md](zhongshu/00-architecture-charter.md) | 中枢 Contract v0.1 顶层章程（中心思想、冻结合同、P0 范围、并发原则） |
| [docs/zhongshu/phase-0-ui-shell-inventory.md](zhongshu/phase-0-ui-shell-inventory.md) | 中枢 Phase 0 UI Shell 盘点（四区布局、运行控制台、代码边界、验收清单） |
| [docs/zhongshu/phase-0-ui-shell-implementation-plan.md](zhongshu/phase-0-ui-shell-implementation-plan.md) | 中枢 Phase 0 UI Shell 实施计划（给 OpenCode 执行的文件范围、批次、验收命令） |
| [docs/zhongshu/01-run-event-fact-contract.md](zhongshu/01-run-event-fact-contract.md) | Run / Event / Fact 事实源合同 |
| [docs/zhongshu/02-capability-tool-contract.md](zhongshu/02-capability-tool-contract.md) | Capability / Tool 合同（模型可见能力、风险、Tool Result） |
| [docs/zhongshu/03-provider-adapter-contract.md](zhongshu/03-provider-adapter-contract.md) | ProviderAdapter 合同（Codex / OpenCode / 未来 Provider 的最小统一接口） |
| [docs/zhongshu/provider-adapter-phase-1-implementation-brief.md](zhongshu/provider-adapter-phase-1-implementation-brief.md) | ProviderAdapter 第一阶段实现简报（SpectrAI/Happy 取舍、OpenCode 优先、事件与权限映射验收） |
| [docs/zhongshu/04-projection-channel-contract.md](zhongshu/04-projection-channel-contract.md) | Projection / Channel 合同（桌面、宠物、远程、飞书的投影和控制边界） |
| [docs/zhongshu/05-session-rail-left-sidebar-design.md](zhongshu/05-session-rail-left-sidebar-design.md) | 中枢左侧会话栏设计（Codex-like 会话导航、项目分组、置顶、无项目会话和 P0 实施计划） |
| [docs/zhongshu/06-orchestration-development-workflow.md](zhongshu/06-orchestration-development-workflow.md) | 中枢一期开发编排设计（model-first playbook、Codex/OpenCode 协作、Markdown 交接文档和实现批次） |
| [docs/zhongshu/permission-and-capability-architecture.md](zhongshu/permission-and-capability-architecture.md) | 中枢权限与能力架构（Capability Registry、PermissionRuntime、ApprovalRuntime、ChannelTrust 统一合同） |
| [docs/expert/chat-core-target-state-design.md](expert/chat-core-target-state-design.md)   | Chat Core / Expert 目标态旧版产品叙事（Intent-first Task System；P0 runtime source of truth 已转向 model-first 设计） |
| [docs/expert/agent-core-runtime-reset-design.md](expert/agent-core-runtime-reset-design.md) | Agent Core Runtime 重置设计（model-first turn、prompt-level intent、tool-first interaction、fact-backed UI） |
| [docs/expert/agent-core-p0-target-design.md](expert/agent-core-p0-target-design.md)       | Agent Core P0 目标设计（P0-01 到 P0-11 的目标架构、参考项目取舍、tool/runtime/UI 边界） |
| [docs/expert/agent-core-p0/README.md](expert/agent-core-p0/README.md)                     | Agent Core P0 详细设计包（每个 P0 维度拆分 API、模块、方法、事件、前端配合和验收） |
| [docs/expert/agent-core-p0/12-conversation-display-contract.md](expert/agent-core-p0/12-conversation-display-contract.md) | Agent Core P0 主对话展示契约（Turn Timeline、thinking/tool/child agent/final answer 的 UI 映射） |
| [docs/expert/agent-core-p0/13-agent-core-p0-completion-contract.md](expert/agent-core-p0/13-agent-core-p0-completion-contract.md) | Agent Core P0 完成合同（TurnPart、per-turn projection、多子 Agent、Replay evidence、P0 Done gate） |
| [docs/expert/agent-core-p0/14-agent-definition-registry.md](expert/agent-core-p0/14-agent-definition-registry.md) | Agent Core P0 Agent Definition Registry 设计（可配置专家、roster snapshot、递归策略、DB 合同） |
| [docs/expert/orchestration-design.md](expert/orchestration-design.md)                     | Expert team runtime 子设计（Wave 调度 + Step 层级）                             |
| [docs/expert/intent-gate/README.md](expert/intent-gate/README.md)                         | Intent Gate 完整设计包（能力总纲、UI/API/后端/持久化/测试清单）                 |
| [docs/expert/intent-gate-design.md](expert/intent-gate-design.md)                         | Intent Gate 意图分类设计（后续升级为 Intent Understanding）                     |

### Qoder 机制研究

| 文档                                                                                                    | 说明              |
| ------------------------------------------------------------------------------------------------------- | ----------------- |
| [docs/qoder-mechanics/01-spec-workflow.md](qoder-mechanics/01-spec-workflow.md)                         | Spec 模式工作流   |
| [docs/qoder-mechanics/02-skills-and-tools.md](qoder-mechanics/02-skills-and-tools.md)                   | Skills 与工具系统 |
| [docs/qoder-mechanics/03-engineering-optimizations.md](qoder-mechanics/03-engineering-optimizations.md) | 九大工程优化策略  |
| [docs/qoder-mechanics/04-hidden-mechanisms.md](qoder-mechanics/04-hidden-mechanisms.md)                 | 隐藏机制详解      |
| [docs/qoder-mechanics/05-boundaries-and-flows.md](qoder-mechanics/05-boundaries-and-flows.md)           | 安全边界与流程    |
| [docs/qoder-mechanics/06-why-it-works.md](qoder-mechanics/06-why-it-works.md)                           | 为什么 Qoder 有效 |

### 参考资料

| 文档                                                                                          | 说明                                |
| --------------------------------------------------------------------------------------------- | ----------------------------------- |
| [docs/reference/README.md](reference/README.md)                                               | 原型与参考资料索引                  |
| [docs/reference/knowledge-base-research-corpus.md](reference/knowledge-base-research-corpus.md) | 知识库、调研流水线与知识资产沉淀参考清单 |
| [docs/reference/agent-core-research-methodology.md](reference/agent-core-research-methodology.md) | Agent Core 调研方法论（横向矩阵、证据标准、调研顺序、输出规范） |
| [docs/reference/agent-core-research-elements.md](reference/agent-core-research-elements.md)   | Agent Core 要素调研清单（P0 核心骨架、P1 能力增强、P2 产品化增强） |
| [docs/reference/agent-core-p0-research/README.md](reference/agent-core-p0-research/README.md) | Agent Core P0 横向调研工作区（评分口径、源码清单、P0 横向证据和 TianYuan 差距初表） |
| [docs/reference/chrys-agent-platform-analysis.md](reference/chrys-agent-platform-analysis.md) | Chrys Agent Platform 调研总结（tool-first、ask_user、sub-agent-as-tool、模型继承） |
| [docs/reference/codex-agent-runtime-analysis.md](reference/codex-agent-runtime-analysis.md)   | Codex Agent Runtime 源码级调研报告（turn loop、tools、request_user_input、approval、agent/team） |
| [docs/reference/claude-code-rev-ts-analysis.md](reference/claude-code-rev-ts-analysis.md)     | Claude Code Rev TS Agent Runtime 调研报告（QueryEngine、tool runtime、Agent tool、compaction、replay、model resolution） |
| [docs/reference/claude-code-rust-analysis.md](reference/claude-code-rust-analysis.md)         | claude-code-rust 复查报告（Rust 重构项目的 tool/agent/plugin 骨架与可复用边界） |
| [docs/reference/opencode-oh-my-opencode-analysis.md](reference/opencode-oh-my-opencode-analysis.md) | OpenCode + Oh My OpenCode 联合调研报告（question-as-tool、task-as-tool、plugin hook、agent/team 编排） |

### 活跃计划（Executing / Approved）

| 文档                                                                                                                                 | 状态          | 说明                                                                                                         |
| ------------------------------------------------------------------------------------------------------------------------------------ | ------------- | ------------------------------------------------------------------------------------------------------------ |
| [plans/2026-04-27-fluid-workbench-global-plan.md](plans/2026-04-27-fluid-workbench-global-plan.md)                                   | Designing     | Fluid Workbench 全局执行计划（可组合布局、多 Chat、独立窗口、Blueprint 适配）                                |
| [plans/2026-04-27-fluid-workbench-focus-mode-plan.md](plans/2026-04-27-fluid-workbench-focus-mode-plan.md)                           | Implemented   | Fluid Workbench Wave 1 子计划（Editor Focus / Chat Focus / Restore Default，frontend Blueprint target）      |
| [plans/2026-04-27-fluid-workbench-view-registry-plan.md](plans/2026-04-27-fluid-workbench-view-registry-plan.md)                     | Implemented   | Fluid Workbench Wave 2 子计划（ViewDefinition / ViewInstanceService / frontend Blueprint target）            |
| [plans/2026-04-27-fluid-workbench-layout-tree-plan.md](plans/2026-04-27-fluid-workbench-layout-tree-plan.md)                         | Implemented   | Fluid Workbench Wave 3a 子计划（LayoutTree schema / migration / frontend Blueprint target）                  |
| [plans/2026-04-27-fluid-workbench-layout-tree-persistence-plan.md](plans/2026-04-27-fluid-workbench-layout-tree-persistence-plan.md) | Implemented   | Fluid Workbench Wave 3b 子计划（LayoutTree persistence bridge / frontend Blueprint target）                  |
| [plans/2026-04-27-fluid-workbench-dock-renderer-plan.md](plans/2026-04-27-fluid-workbench-dock-renderer-plan.md)                     | Implemented   | Fluid Workbench Wave 3c 子计划（read-only DockRenderer / frontend Blueprint target）                         |
| [plans/2026-04-27-fluid-workbench-layout-mutations-plan.md](plans/2026-04-27-fluid-workbench-layout-mutations-plan.md)               | Implemented   | Fluid Workbench Wave 4a 子计划（LayoutTree mutation commands / frontend Blueprint target）                   |
| [plans/2026-04-27-fluid-workbench-state-mutation-bridge-plan.md](plans/2026-04-27-fluid-workbench-state-mutation-bridge-plan.md)     | Implemented   | Fluid Workbench Wave 4b 子计划（WorkbenchState mutation bridge / frontend Blueprint target）                 |
| [plans/2026-04-27-fluid-workbench-dock-drop-controller-plan.md](plans/2026-04-27-fluid-workbench-dock-drop-controller-plan.md)       | Implemented   | Fluid Workbench Wave 4c 子计划（DockDropController intent bridge / frontend Blueprint target）               |
| [plans/2026-04-27-fluid-workbench-ui-drop-zones-plan.md](plans/2026-04-27-fluid-workbench-ui-drop-zones-plan.md)                     | Implemented   | Fluid Workbench Wave 4d 子计划（UI Drop Zones / frontend Blueprint target）                                  |
| [plans/2026-04-27-fluid-workbench-drop-preview-plan.md](plans/2026-04-27-fluid-workbench-drop-preview-plan.md)                       | Implemented   | Fluid Workbench Wave 4e 子计划（Drop Preview Overlay / frontend Blueprint target）                           |
| [plans/2026-04-27-fluid-workbench-handoff-next-agent-plan.md](plans/2026-04-27-fluid-workbench-handoff-next-agent-plan.md)           | Handoff Ready | Fluid Workbench 后续任务交接指导计划（给接手 Agent 的路线、边界、验证要求）                                  |
| [plans/2026-04-29-chat-core-next-hardening-plan.md](plans/2026-04-29-chat-core-next-hardening-plan.md)                               | Done          | Chat Core 目标态后续硬化计划（execution preference、projection、todo runtime、final wave、panorama、UI/E2E） |
| [plans/2026-04-30-ai-ui-control-plane-wave-1-plan.md](plans/2026-04-30-ai-ui-control-plane-wave-1-plan.md)                           | Done          | AI UI Control Plane Wave 1 实施计划（协议、元素注册、snapshot、action、command、layout inspect、diagnose）   |
| [plans/2026-04-30-ai-ui-control-plane-cli-hardening-plan.md](plans/2026-04-30-ai-ui-control-plane-cli-hardening-plan.md)             | Done          | AI UI Control Plane CLI 强化计划（高层 CLI 工作流、stateful setup、元素覆盖、证据摘要）                     |
| [plans/2026-05-01-agent-core-p0-research-plan.md](plans/2026-05-01-agent-core-p0-research-plan.md)                                   | Designing     | Agent Core P0 横向调研执行计划（参考项目评分矩阵、TianYuan 当前差距、可迁移建议）                          |
| [plans/2026-05-01-agent-core-p0-implementation-plan.md](plans/2026-05-01-agent-core-p0-implementation-plan.md)                       | Implementing  | Agent Core P0 重构实施计划（model-first turn、tool-first interaction、fact-backed UI、Blueprint Required）  |
| [plans/2026-05-01-agent-core-p0-batch2-api-blueprint.md](plans/2026-05-01-agent-core-p0-batch2-api-blueprint.md)                     | Implementing  | Agent Core P0 Batch 2 API Blueprint target（ask_user pending answer IPC）                                  |
| [plans/2026-05-01-agent-core-p0-batch2-ui-blueprint.md](plans/2026-05-01-agent-core-p0-batch2-ui-blueprint.md)                       | Implementing  | Agent Core P0 Batch 2 UI Blueprint target（QuestionCard / TaskCreationBlock tool-first projection）         |
| [plans/2026-05-01-agent-core-p0-batch3-ui-blueprint.md](plans/2026-05-01-agent-core-p0-batch3-ui-blueprint.md)                       | Implementing  | Agent Core P0 Batch 3 UI Blueprint target（child Agent facts 到 ExpertTaskCard / AgentTreeNode 投影）       |
| [plans/2026-05-01-agent-core-p0-batch4-steering-blueprint.md](plans/2026-05-01-agent-core-p0-batch4-steering-blueprint.md)           | Implementing  | Agent Core P0 Batch 4 API Blueprint target（active turn steering / cancel IPC 链路）                        |
| [plans/2026-05-01-agent-core-p0-batch4-steering-ui-blueprint.md](plans/2026-05-01-agent-core-p0-batch4-steering-ui-blueprint.md)     | Implementing  | Agent Core P0 Batch 4 UI Blueprint target（ChatInput / chat store / agentTurnClient 分流链路）              |
| [plans/2026-05-01-agent-core-p0-batch5-replay-api-blueprint.md](plans/2026-05-01-agent-core-p0-batch5-replay-api-blueprint.md)       | Implementing  | Agent Core P0 Batch 5 Replay API Blueprint target（agent_events_list / agent_replay_bundle IPC 链路）       |
| [plans/2026-05-02-agent-core-conversation-display-timeline-plan.md](plans/2026-05-02-agent-core-conversation-display-timeline-plan.md) | Designing     | Agent Core 主对话 Turn Timeline 展示重构计划（thinking/tool/child agent/final answer 顺序、UI Control CI、Blueprint frontend-static） |
| [plans/2026-05-02-agent-core-p0-completion-hardening-plan.md](plans/2026-05-02-agent-core-p0-completion-hardening-plan.md) | Design Approved | Agent Core P0 完整落地强化计划（TurnPart、per-turn projection、多子 Agent、permission/replay/panorama/evidence gate） |

### 有价值搁置（Deferred）

| 文档                                                                                                                     | 说明                                                     |
| ------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------- |
| [plans/2026-04-18-butler-system-roadmap.md](plans/2026-04-18-butler-system-roadmap.md)                                   | 管家系统演进路线图（V1 已实现，路线图待续）              |
| [plans/2026-04-18-markdown-workbench-design.md](plans/2026-04-18-markdown-workbench-design.md)                           | Markdown 工作台设计                                      |
| [plans/2026-04-18-markdown-workbench-implementation-plan.md](plans/2026-04-18-markdown-workbench-implementation-plan.md) | Markdown 工作台实施计划                                  |
| [plans/2026-04-19-tool-batch-2-plan.md](plans/2026-04-19-tool-batch-2-plan.md)                                           | 工具系统第二批次（41 工具已实现 41，计划过时但保留参考） |
| [plans/2026-04-20-agent-capability-roadmap.md](plans/2026-04-20-agent-capability-roadmap.md)                             | Agent 能力路线图（部分实现中）                           |

### 次要参考

| 文档                                                                                                 | 说明                      |
| ---------------------------------------------------------------------------------------------------- | ------------------------- |
| [plans/2026-04-12-ide-completion-plan.md](plans/2026-04-12-ide-completion-plan.md)                   | IDE 自动补全计划          |
| [plans/2026-04-20-p2-1-plan-persistence-design.md](plans/2026-04-20-p2-1-plan-persistence-design.md) | 计划持久化设计            |
| [plans/markdown-workbench-smoke.md](plans/markdown-workbench-smoke.md)                               | Markdown 工作台冒烟测试   |
| [plans/display-mockup-before-after.html](plans/display-mockup-before-after.html)                     | 展示层改造前后对比 mockup |

### 运维排查

| 文档                                          | 说明                       |
| --------------------------------------------- | -------------------------- |
| [docs/troubleshooting.md](troubleshooting.md) | 问题排查清单（按症状索引） |

### 历史归档

| 文档                                                                                                                                                                                              | 说明                                                                                                                       |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| [docs/history/](history/)                                                                                                                                                                         | 所有已完成/取消/取代的计划（~50 个文件）                                                                                   |
| [docs/history/2026-04-28-chat-core-target-state-completed/](history/2026-04-28-chat-core-target-state-completed/)                                                                                 | Chat Core / Expert 目标态实施归档（IntentDecision / ExecutionPolicy / ChatProjection 硬切，含 evidence 和 Blueprint diff） |
| [docs/history/2026-04-28-expert-routing-wave-design-superseded/](history/2026-04-28-expert-routing-wave-design-superseded/)                                                                       | Expert routing / wave / legacy replay 旧设计归档（已由 Chat Core 目标态取代）                                              |
| [docs/history/2026-04-19-novel-v2-pre-plotpilot-rebase/](history/2026-04-19-novel-v2-pre-plotpilot-rebase/)                                                                                       | Novel Engine V2 Pre-PlotPilot Rebase 归档（13 个文件）                                                                     |
| [docs/history/2026-04-27-plotpilot-parity-closure-design-superseded.md](history/2026-04-27-plotpilot-parity-closure-design-superseded.md)                                                         | PlotPilot parity closure 旧设计归档（已由删除优先方案取代）                                                                |
| [docs/history/2026-04-27-novel-superseded/2026-04-23-novel-plotpilot-full-parity-execution-plan.md](history/2026-04-27-novel-superseded/2026-04-23-novel-plotpilot-full-parity-execution-plan.md) | Novel PlotPilot 旧全量 parity 计划归档（已由删除优先合同与 early custom cleanup 取代）                                     |
| [docs/history/memory-system-design-v1.md](history/memory-system-design-v1.md)                                                                                                                     | 记忆系统设计 v1（归档）                                                                                                    |
| [docs/history/2026-04-16-novel-module-v1-archive.md](history/2026-04-16-novel-module-v1-archive.md)                                                                                               | Novel Module v1 归档基线                                                                                                   |
| [docs/history/expert-mode-ui-spec.md](history/expert-mode-ui-spec.md)                                                                                                                             | Expert 模式 UI 历史基线与实现同步记录                                                                                      |

> 完整归档列表见 [docs/history/](history/) 目录。2026-04-25 批量归档了 AI Workload、早期 Foundation、Expert Traceability、Display Kernel、Codebase Quality、Butler/Pet/Export/Monitoring/LSP/Task 等已实现计划。

---

## 外部项目归档

> **归档分支**: `archive/external-projects`（不在 main 分支上，已从磁盘移除）

以下外部参考项目已归档到独立 Git 分支，工作目录不再保留：

| 项目                     | 说明                   | 行数      |
| ------------------------ | ---------------------- | --------- |
| swarms-rs                | Rust 多 Agent 框架参考 | ~20K Rust |
| tianyuan-full-nav        | 全导航原型             | ~4K TS    |
| tianyuan-home-dashboard  | 首页仪表盘原型         | ~4K TS    |
| tianyuan-memory-hub      | 记忆中枢原型           | ~0.3K     |
| tianyuan-pipeline-editor | 管道编辑器原型         | ~4K TS    |

**恢复方式**：

```bash
# 查看归档分支内容
git log archive/external-projects --oneline
git ls-tree archive/external-projects archive/external-projects/

# 恢复到工作目录（只读参考）
git checkout archive/external-projects -- archive/
```

> ⚠️ `.qoder/repowiki/`（24.7 万行自动生成文档）已被 `.gitignore` 忽略，不会被提交到仓库。如需查阅，在本地用 Qoder 重新生成即可。

---

## 原型归档

交互原型归档在 [docs/assets/prototypes/](assets/prototypes/) 目录。

| 原型                                                                                                                               | 说明                                                 |
| ---------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------- |
| [2026-04-13-novel-mode-prototype.html](assets/prototypes/2026-04-13-novel-mode-prototype.html)                                     | 小说写作模式高保真原型（3 栏布局 + AI 续写悬浮卡片） |
| [2026-04-24-plotpilot-1to1-ide-embedding-prototype.html](assets/prototypes/2026-04-24-plotpilot-1to1-ide-embedding-prototype.html) | PlotPilot 1:1 嵌入 TianYuan Novel 壳层高保真原型     |
| [archive-2026-04-05/](assets/prototypes/archive-2026-04-05/)                                                                       | 早期原型归档（novel-prototype.html, pet.html）       |

---

## 技术架构

详见 [系统设计.md](系统设计.md) - 包含 4+1 架构视图（逻辑、开发、进程、物理、场景视图）

---

## 文档规范

详见 [docs/CONTRIBUTING.md](CONTRIBUTING.md) — 文档分类、生命周期、命名规则、归档标准。
