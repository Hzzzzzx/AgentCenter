# 天元 · Agent 实现参考文档集

> 为后续 Agent 功能实现提供 UI/UX 视觉参考和交互原型。

---

## 📁 reference/plans/ — 本次会话创建的原型（2026-04-06）

| 文件                                 | 说明                                                                                                        |
| ------------------------------------ | ----------------------------------------------------------------------------------------------------------- |
| `2026-04-06-tianyuan-prototype.html` | **最终版高保真原型** — 暗色科技风首页，含顶部导航、左侧任务中心（4张任务卡片）、右侧 Agent 面板、底部状态栏 |
| `prototype-v4.png`                   | 最终版原型渲染截图                                                                                          |
| `prototype-v3.png`                   | 中期调试截图                                                                                                |
| `prototype-screenshot.png`           | 早期截图                                                                                                    |

### 原型预览方式

```bash
# 启动 HTTP 服务器
cd docs/reference/plans
python3 -m http.server 8765
# 然后浏览器打开 http://localhost:8765/2026-04-06-tianyuan-prototype.html
```

---

## 📁 docs/plans/ — 其他 Agent 创建的原型

包含：首页仪表盘、工作流编辑器（高保真版/链接式/DAG/大纲+时间线）、管道编辑器、桌面 UI 原型、Agent 团队 DAG 等。

---

## 📄 docs/ 根目录文档

| 文件                                        | 说明                                                                     |
| ------------------------------------------- | ------------------------------------------------------------------------ |
| `memory-system-design.md`                   | 记忆系统设计 — RAG + 长期记忆架构                                        |
| `reference-projects-analysis.md`            | 参考项目分析 — 类似系统调研                                              |
| `reference/agent-core-research-methodology.md` | **Agent Core 调研方法论** — 横向矩阵、证据标准、调研顺序、输出规范 |
| `reference/agent-core-research-elements.md` | **Agent Core 要素调研清单** — P0 核心骨架、P1 能力增强、P2 产品化增强 |
| `reference/agent-core-p0-research/README.md` | **Agent Core P0 横向调研工作区** — 评分口径、源码清单、P0 横向证据和 TianYuan 差距初表 |
| `reference/agent-platform-corpus/README.md` | **Agent Platform Corpus** — Anthropic/OpenAI 官方 Agent 实践资料卡、原则总结和 Zhongshu 设计 takeaways，按 graphify 友好结构组织 |
| `reference/chrys-agent-platform-analysis.md` | **Chrys Agent Platform 调研总结** — tool-first、ask_user、sub-agent-as-tool、模型继承 |
| `reference/codex-agent-runtime-analysis.md` | **Codex Agent Runtime 源码级调研报告** — turn loop、tools、request_user_input、approval、agent/team |
| `reference/claude-code-rev-ts-analysis.md` | **Claude Code Rev TS Agent Runtime 调研报告** — QueryEngine、tool runtime、Agent tool、compaction、replay、model resolution |
| `reference/claude-code-rust-analysis.md`    | **claude-code-rust 复查报告** — Rust 重构项目的 tool/agent/plugin 骨架与可复用边界 |
| `reference/opencode-oh-my-opencode-analysis.md` | **OpenCode + Oh My OpenCode 联合调研报告** — question-as-tool、task-as-tool、plugin hook、agent/team 编排 |
| `reference/qoder-experts-quest-analysis.md` | **Qoder Experts + Quest Mode 深度分析** — 多智能体协作和自主编程设计灵感 |

## 📄 docs/reference/ — 专题调研清单

| 文件                                 | 说明                                                                 |
| ------------------------------------ | -------------------------------------------------------------------- |
| `knowledge-base-research-corpus.md`  | 知识库 / 调研流水线 / 知识资产沉淀参考项目清单与统一评审矩阵         |
| `agent-platform-corpus/README.md`    | Agent 平台官方资料 corpus，供 `/graphify docs/reference/agent-platform-corpus --mode deep` 生成知识图谱 |

---

## 📄 PlotPilot 上游追踪工作文档

| 文件                              | 说明                                                               |
| --------------------------------- | ------------------------------------------------------------------ |
| `plotpilot-upstream-watch.md`     | PlotPilot 上游观察台账，按 commit 区间记录能力变化与结论           |
| `plotpilot-capability-map.md`     | PlotPilot × TianYuan 能力映射表，用于判断 Adopt / Research / Watch |
| `plotpilot-adoption-decisions.md` | PlotPilot 能力吸收决策记录，沉淀正式路线判断                       |

## 📁 docs/qoder-mechanics/ — Qoder 机制详解（6篇）

| 文件                              | 说明                                   |
| --------------------------------- | -------------------------------------- |
| `01-spec-workflow.md`             | Spec 模式 5 阶段工作流                 |
| `02-skills-and-tools.md`          | Skills 体系、MCP 工具、子代理          |
| `03-engineering-optimizations.md` | 九大工程优化策略（文言文）             |
| `04-hidden-mechanisms.md`         | 记忆系统、AGENTS.md、Hooks、Skill 触发 |
| `05-boundaries-and-flows.md`      | 安全边界、Commit/PR 工作流、并行约束   |
| `06-why-it-works.md`              | 为什么 Qoder 让模型更慢、更准、更稳    |

---

## 🎨 设计风格参考

**主题**：暗色科技风（Dark Tech）
**主色调**：深蓝黑 `#050810`
**强调色**：科技蓝 `#00D9FF`、科技绿 `#39FF14`
**字体**：系统字体栈
**图标**：内联 SVG

---

## 🔗 如何使用

1. 用浏览器直接打开任意 `.html` 文件查看原型
2. 参考 `memory-system-design.md` 了解记忆系统架构
3. 参考 `reference-projects-analysis.md` 了解竞品实现方式
4. 所有 HTML 均为**单文件可运行**（内联 CSS + JS），无需构建
