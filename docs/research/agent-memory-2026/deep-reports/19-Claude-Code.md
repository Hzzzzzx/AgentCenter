# Claude Code 深度研究报告

> AgentCenter 记忆系统调研 · 编码工具评估 #19
> 调研日期：2026-05-23
> 评级：Tier D（编码工具的记忆能力评估，非记忆框架）

---

## 1. Reader Verdict

**一句话总结**：Claude Code 是一个通过 CLAUDE.md 和 ~/.claude/skills/ 提供项目级和全局级技能配置的 AI 编程工具，当前没有真正的跨会话自动记忆能力，但 Skill 系统比 OpenCode 更丰富。

**核心评价**：Claude Code（Anthropic 官方出品的 CLI 编程工具）的记忆相关能力与 OpenCode 类似，本质上也是**配置文件系统**。CLAUDE.md 作为项目级配置，skills/ 目录作为可复用技能库，两者在功能上与 AGENTS.md 和 OpenCode 的 skills 系统相近。

关键区别在于 Skill 系统的丰富度：Claude Code 提供 37+ 个预置 skills，涵盖 git 操作、代码审查、调试、测试等常见编程任务。projects/ 目录则提供了项目级内存的扩展能力，允许为每个项目维护独立的上下文配置。

**重要提示**：Claude Code 的 auto memory（自动记忆）当前是**空的**。默认情况下，Claude Code 不会自动将对话内容沉淀为记忆。所有「记忆」都需要通过手动编辑 CLAUDE.md 或创建/更新 skills 来实现。

---

## 2. Framework Profile

| 属性 | 值 |
|------|-----|
| **项目名称** | Claude Code |
| **开发组织** | Anthropic |
| **GitHub** | https://github.com/anthropics/claude-code |
| **Stars** | 126,000 |
| **开源状态** | ⚠️ 部分开源（代码可见但非真正 OSS） |
| **许可证** | Proprietary |
| **定位** | AI 编程 CLI 工具 |
| **技术栈** | Node.js + CLI |
| **记忆相关文件** | CLAUDE.md（项目级）+ ~/.claude/skills/（全局）+ projects/ |
| **存储介质** | 文件系统（Markdown/YAML/JSON） |
| **跨会话自动记忆** | 无（auto memory 为空） |
| **Skill 数量** | 37+ |
| **Benchmark 数据** | 无 |
| **与 AgentCenter 契合度** | 低（独立工具，非 AgentCenter 技术栈） |

---

## 3. Core Thesis

**核心论点**：Claude Code 相信**显式上下文优于隐式记忆**。与自组织记忆系统（Mem0、Letta 等）不同，Claude Code 依赖开发者显式声明项目上下文。CLAUDE.md 声明项目结构和技术栈，skills 定义可复用的任务模式，projects/ 为每个项目维护独立配置。

这个设计哲学的优势是**精确可控**：开发者完全知道 Claude Code 使用什么上下文，不会有意外的记忆偏差。劣势是**维护负担**：随着项目增长，需要人工维护配置文件的准确性。

**Skill 生态**：Claude Code 的 Skill 系统比大多数竞品更丰富。37+ 个预置 skills 覆盖了常见的编程任务，每个 skill 都是一个独立的功能单元。这与 OpenCode 的 skill 系统在架构上类似，但内容更丰富。

---

## 4. Theoretical Foundation

Claude Code 的理论基础与 OpenCode 一脉相承：**配置驱动**而非**自组织学习**。这是 Anthropic 的一贯哲学——强调显式控制和可预测性，而非隐式的自动学习。

**projects/ 目录的设计**：每个项目可以有独立的配置上下文。当在某个项目目录下运行 Claude Code 时，会自动加载该项目的 projects/ 配置。这比 OpenCode 的单一日的 AGENTS.md 更灵活，支持多项目隔离。

**Skills 的设计理念**：每个 skill 是一个**任务模板**，包含触发条件、执行动作、上下文格式。不是从使用中学习的动态记忆，而是预定义的静态配置。

---

## 5. Memory Model

**警告：Claude Code 不是记忆框架**，以下描述的是其文件系统中与「记忆」概念相关的部分。

### 5.1 CLAUDE.md（项目级配置）

**位置**：项目根目录下的 CLAUDE.md

**内容**：声明式的项目级配置，与 AGENTS.md 功能相近

**记忆机制**：**无自动更新**。需要手动编辑。

### 5.2 ~/.claude/skills/（全局技能库）

**位置**：~/.claude/skills/

**数量**：37+ 个预置 skill

**内容**：可复用的技能定义，涵盖：
- Git 操作（commit、branch、rebase 等）
- 代码审查（security review、performance review 等）
- 调试（debug、trace 等）
- 测试（test generation、coverage 等）

**记忆机制**：**无动态记忆**。Skills 是预定义的静态配置。

### 5.3 projects/（项目级内存扩展）

**位置**：~/.claude/projects/

**设计**：为每个项目维护独立的配置上下文。不同项目可以有完全不同的配置，互不干扰。

**记忆机制**：**无自动记忆**。需要手动创建和维护每个项目的配置。

### 5.4 Auto Memory（当前为空）

Claude Code 理论上支持 auto memory 功能，但在**默认配置下是禁用的**。这意味着 Claude Code 不会自动从对话历史中提取和存储知识。

---

## 6. Architecture Deep Dive

### 6.1 配置层次结构

```
┌──────────────────────────────────────────────────────────────┐
│              Claude Code 配置层次结构                         │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  全局层（~/.claude/）                                        │
│  ├── skills/          ← 37+ 全局 skills                    │
│  └── projects/        ← 项目独立配置                        │
│                                                               │
│  项目层（项目根目录）                                         │
│  └── CLAUDE.md       ← 项目级配置                           │
│                                                               │
│  会话层（运行时）                                             │
│  └── 临时上下文    ← 仅在当前会话有效                        │
│                                                               │
│  记忆机制：无自动跨会话记忆，所有配置需手动维护               │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

### 6.2 Skill 系统架构

每个 skill 是一个独立的功能单元，结构包括：
- **触发条件**：何时激活这个 skill
- **执行动作**：调用什么工具或操作
- **上下文格式**：如何组织和传递上下文

与 OpenCode 的 skill 系统在架构上类似，但 Claude Code 提供了更丰富的预置 skills。

### 6.3 与 Anthropic API 的关系

Claude Code 通过 Anthropic API 调用 Claude 模型。配置上下文通过 prompt 注入，不是通过 API 级别的记忆参数传递。这意味着 Claude Code 的「记忆」完全依赖于 prompt 中的上下文内容，而非 API 级别的状态管理。

---

## 7. Design Tradeoffs

| 权衡维度 | 选了什么 | 代价 |
|---------|---------|------|
| **记忆方式** | 显式配置（CLAUDE.md） | 无自动记忆，维护负担 |
| **跨会话持久化** | 弱（需手动 projects/） | 会话间不自动传递信息 |
| **多项目支持** | projects/ 目录 | 配置可能重复，难以共享 |
| **Skill 生态** | 37+ 预置 skills | Skills 是静态的，不学习 |
| **Auto memory** | 默认禁用 | 缺乏动态记忆能力 |
| **可预测性** | 高（显式控制） | 灵活性低 |

---

## 8. Evidence

**Auto Memory 状态**：根据 AgentCenter 项目文档和调研，Claude Code 的 auto memory 功能在默认配置下是**空的**。这意味着：
- Claude Code 不会自动从对话历史中提取知识
- 不会自动维护对话摘要或关键信息
- 所有「记忆」都需要手动配置

**Skill 丰富度**：37+ 个预置 skills，覆盖常见编程任务，比大多数竞品更丰富。

**projects/ 目录**：支持为每个项目维护独立配置，这是比 OpenCode 的单一日的 AGENTS.md 更灵活的设计。

---

## 9. Applicability

| 场景 | 适用度 | 说明 |
|------|--------|------|
| **项目上下文声明** | 强 | CLAUDE.md + projects/ 适合声明项目信息 |
| **编程任务执行** | 强 | 37+ skills 覆盖常见任务 |
| **跨会话知识保留** | 弱 | 需要手动维护 projects/ |
| **动态记忆积累** | 无 | 不从对话历史自动提取 |
| **企业知识管理** | 弱 | 无共享记忆池，无权限控制 |
| **多项目管理** | 中 | projects/ 支持项目隔离 |

---

## 10. Maturity

| 维度 | 评级 | 说明 |
|------|------|------|
| **记忆系统成熟度** | 无 | 不是记忆框架 |
| **工具成熟度** | 高 | Anthropic 官方出品 |
| **Skill 生态** | 高 | 37+ 预置 skills |
| **文档完整性** | 高 | 官方文档详细 |
| **企业特性** | 低 | 无多用户、无权限控制 |
| **与 AgentCenter 集成** | 低 | AgentCenter 使用 OpenCode |

---

## 11. AgentCenter Implications

### 11.1 Claude Code 与 AgentCenter 的关系

AgentCenter 的技术栈基于 OpenCode，而非 Claude Code。Claude Code 作为独立工具，不在 AgentCenter 的核心架构中。

### 11.2 可借鉴的设计

**projects/ 目录的多项目隔离**：AgentCenter 可能有类似的多项目隔离需求，可以借鉴这个设计。

**Skill 系统的组织方式**：37+ skills 的目录结构和组织方式值得参考。

### 11.3 设计建议

**如果 AgentCenter 需要跨项目共享上下文**：
- 可以在 Bridge 层维护一个项目配置管理系统
- 每个项目的配置存储在数据库中，而非文件系统
- 通过 API 在调用 OpenCode 时注入项目上下文

**不依赖 Claude Code 的记忆能力**：
- Claude Code 没有真正的自动记忆
- AgentCenter 需要自己的记忆系统来管理企业知识

---

## 12. Comparative Scorecard

| 能力 | Claude Code | OpenCode | Codex | Windsurf | 专用记忆框架 |
|------|-------------|----------|-------|----------|--------------|
| **自动记忆提取** | 无 | 无 | 无 | 无 | 有 |
| **跨会话持久化** | 弱（projects/） | 弱（AGENTS.md） | 弱（AGENTS.md） | 弱（rules） | 强 |
| **语义检索** | 无 | 无 | 无 | 无 | 有 |
| **Skill 丰富度** | 强（37+） | 强（50+） | 弱 | 强 | 中 |
| **多项目支持** | 强（projects/） | 弱 | 弱 | 中 | 中 |
| **企业记忆池** | 无 | 无 | 无 | 无 | 有（部分） |

---

## 13. Open Questions

1. **Claude Code 的 auto memory 何时会真正启用？** 当前为空，但 Anthropic 是否有计划完善这个功能？

2. **projects/ 与 Git 仓库的关系？** 每个项目的配置是存储在 ~/.claude/projects/ 还是在仓库中？团队协作时如何同步？

3. **Skill 系统能否从使用中学习？** 当前是纯静态配置，能否增加某种自适应机制？

4. **Claude Code 与 Claude.ai 的记忆是否打通？** 用户在网页端和 CLI 端的记忆是否共享？

5. **Anthropic 对 AI 编程工具的记忆能力有何长期规划？** Claude Code 的 roadmap 中是否包含更强大的记忆系统？

---

*本报告为 AgentCenter 内部技术调研材料，数据截止日期 2026-05-23。注意：Claude Code 是编码工具，不是记忆框架，其「记忆」能力仅限于配置文件。*
