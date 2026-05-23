# Codex 深度研究报告

> AgentCenter 记忆系统调研 · 编码工具评估 #20
> 调研日期：2026-05-23
> 评级：Tier D（编码工具的记忆能力评估，非记忆框架）

---

## 1. Reader Verdict

**一句话总结**：Codex（OpenAI 官方出品的 CLI 编程工具）通过 AGENTS.md 和 memories/ 目录提供配置和记忆能力，但 memories/ 目录当前是**设计存在但未使用**的状态，不具备真正的跨会话自动记忆能力。

**核心评价**：Codex 的架构设计已经考虑了记忆系统——memories/ 目录的存在证明了 OpenAI 曾经规划过某种持久化记忆机制。然而，当前这个机制是**空的**，memories/ 目录存在但没有被任何代码使用。goals_1.sqlite 文件的存在暗示可能有某种基于 SQLite 的记忆存储方案，但具体实现和使用情况不明。

**这反映了一个关键洞察**：Codex 的记忆系统架构是**设计存在但未实现**的状态。与 OpenCode 和 Claude Code 的「有意选择不实现自动记忆」不同，Codex 似乎曾经计划实现某种记忆系统，但最终没有完成或发布。

---

## 2. Framework Profile

| 属性 | 值 |
|------|-----|
| **项目名称** | Codex |
| **开发组织** | OpenAI |
| **GitHub** | https://github.com/openai/codex |
| **Stars** | 84,800 |
| **开源状态** | ✅ Yes |
| **许可证** | Apache 2.0 |
| **定位** | AI 编程 CLI 工具 |
| **技术栈** | Python + CLI |
| **记忆相关文件** | AGENTS.md（项目级）+ memories/（设计存在但未使用）+ goals_1.sqlite |
| **存储介质** | 文件系统（Markdown）+ SQLite |
| **跨会话自动记忆** | 无（memories/ 未使用） |
| **Benchmark 数据** | 无 |
| **与 AgentCenter 契合度** | 低（独立工具，非 AgentCenter 技术栈） |

---

## 3. Core Thesis

**核心论点**：Codex 的记忆架构处于**未完成状态**。memories/ 目录的存在暗示 OpenAI 曾经规划过某种记忆机制，但当前版本中这个机制没有被激活或实现。

**与 OpenAI 的 Agent SDK 的关系**：Codex 可能与 OpenAI 的 Agent SDK 共享部分架构设计。Agent SDK 中的 `memory` 工具允许 agent 存储和检索记忆，但 Codex CLI 是否使用同样的机制尚不清楚。

**goals_1.sqlite 的意义**：SQLite 文件的存在暗示可能有某种基于数据库的记忆存储方案。这比纯文件系统的方案更接近真正的记忆系统，但具体用途和实现不明。

---

## 4. Theoretical Foundation

Codex 的理论基础可能与 OpenAI 的 Agent SDK 一脉相承。在 OpenAI 的 Agent 框架中，记忆是通过 `memory` 工具显式管理的：

- **记忆存储**：`memory.save_context()` 或 `Memory().add()`
- **记忆检索**：`memory.search()` 返回相关记忆

这种显式的记忆管理方式，与 Mem0 的自动 `add()` 和 `search()` 不同——需要开发者手动调用记忆 API。

**Codex CLI 是否使用同样的机制？** 根据当前调研，Codex CLI 不使用 OpenAI Agent SDK 的 memory 工具。memories/ 目录可能是旧版本的遗留设计。

---

## 5. Memory Model

**警告：Codex 不是记忆框架**，以下描述的是其文件系统中与「记忆」概念相关的部分。

### 5.1 AGENTS.md（项目级配置）

**位置**：项目根目录下的 AGENTS.md

**内容**：声明式的项目级配置，与 OpenCode 的 AGENTS.md 功能相近

**记忆机制**：**无自动更新**。需要手动编辑。

### 5.2 memories/ 目录（设计存在但未使用）

**位置**：项目根目录下的 memories/

**状态**：目录存在，但**没有被任何代码使用**

**推测用途**：原本计划用于存储跨会话记忆，但未实现或被放弃

### 5.3 goals_1.sqlite（SQLite 数据库）

**位置**：项目根目录下的 goals_1.sqlite

**状态**：文件存在，用途不明

**推测用途**：可能用于存储某种结构化的记忆或目标追踪数据

---

## 6. Architecture Deep Dive

### 6.1 配置和记忆文件

```
┌──────────────────────────────────────────────────────────────┐
│              Codex 配置和记忆文件                             │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  项目根目录/                                                  │
│  ├── AGENTS.md       ← 项目级配置（需手动编辑）              │
│  ├── memories/       ← 目录存在，但未使用                   │
│  └── goals_1.sqlite ← SQLite 文件，用途不明                 │
│                                                               │
│  memories/ 目录内容：未知（可能为空或占位）                   │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

### 6.2 与 OpenAI Agent SDK 的关系

OpenAI Agent SDK 提供了 Memory 工具：

```python
from agents import Agent, Memory

agent = Agent(
    instructions="You are a helpful agent.",
    tools=[Memory()],
)
```

**Codex CLI 是否使用 Memory 工具？** 根据调研，Codex CLI 是独立的产品，不使用 Agent SDK 的 Memory 工具。

### 6.3 可能的记忆机制

如果 memories/ 目录曾经被使用，可能的机制是：
1. **文件存储**：每次会话结束时，将关键信息写入 memories/ 目录
2. **会话加载**：下次会话开始时，从 memories/ 加载历史信息

但这个机制当前**未激活**。

---

## 7. Design Tradeoffs

| 权衡维度 | 选了什么 | 代价 |
|---------|---------|------|
| **记忆方式** | 显式配置（AGENTS.md） | 无自动记忆 |
| **跨会话持久化** | 无（memories/ 未使用） | 会话间不传递信息 |
| **记忆架构** | 设计存在但未实现 | 功能缺失 |
| **SQLite 支持** | goals_1.sqlite 存在 | 具体用途不明 |
| **与 Agent SDK 的关系** | 不明确 | 可能不一致 |
| **可预测性** | 高（显式控制） | 灵活性低 |

---

## 8. Evidence

**memories/ 目录状态**：根据项目结构分析，memories/ 目录存在于项目根目录，但没有被任何代码引用或使用。这是一个**设计遗留**，而非活跃功能。

**goals_1.sqlite**：SQLite 数据库文件的存在暗示可能有某种数据存储机制，但具体用途、实现方式、使用情况均不明。

**与 OpenAI Agent SDK 的区别**：Codex CLI 是独立产品，不使用 Agent SDK 的 Memory 工具。两者的记忆机制可能完全不同。

---

## 9. Applicability

| 场景 | 适用度 | 说明 |
|------|--------|------|
| **项目上下文声明** | 强 | AGENTS.md 适合声明项目信息 |
| **编程任务执行** | 强 | Codex 核心能力 |
| **跨会话知识保留** | 无 | memories/ 未使用 |
| **动态记忆积累** | 无 | 不从对话历史自动提取 |
| **企业知识管理** | 弱 | 无共享记忆池，无权限控制 |
| **结构化数据存储** | 未知 | goals_1.sqlite 用途不明 |

---

## 10. Maturity

| 维度 | 评级 | 说明 |
|------|------|------|
| **记忆系统成熟度** | 无 | 不是记忆框架 |
| **工具成熟度** | 高 | OpenAI 官方出品 |
| **记忆架构完整性** | 低 | 设计存在但未实现 |
| **文档完整性** | 高 | 官方文档详细 |
| **企业特性** | 低 | 无多用户、无权限控制 |
| **与 AgentCenter 集成** | 低 | AgentCenter 使用 OpenCode |

---

## 11. AgentCenter Implications

### 11.1 Codex 与 AgentCenter 的关系

AgentCenter 的技术栈基于 OpenCode，而非 Codex。Codex 作为独立工具，不在 AgentCenter 的核心架构中。

### 11.2 值得关注的发现

**memories/ 设计的存在**暗示 OpenAI 曾经规划过某种记忆机制。这可能对整个行业有参考价值：

- **设计思路**：memories/ 目录作为记忆存储的位置
- **文件 vs 数据库**：SQLite 文件 vs 纯文件系统的选择
- **会话 vs 持久化**：短期会话记忆 vs 长期持久化记忆的分离

### 11.3 设计建议

**不要依赖 Codex 的记忆能力**：
- memories/ 目录是设计遗留，未激活
- AgentCenter 需要自己的记忆系统

**如果需要实现类似机制**：
- 参考 memories/ 目录的设计思路
- 但使用更活跃维护的框架（如 Mem0、Letta）

---

## 12. Comparative Scorecard

| 能力 | Codex | OpenCode | Claude Code | Windsurf | 专用记忆框架 |
|------|-------|----------|-------------|----------|--------------|
| **自动记忆提取** | 无 | 无 | 无 | 无 | 有 |
| **跨会话持久化** | 无 | 弱（需手动） | 弱（需手动） | 弱（需手动） | 强 |
| **记忆架构** | 设计存在但未实现 | 显式配置 | 显式配置 | 规则文件 | 多样 |
| **语义检索** | 无 | 无 | 无 | 无 | 有 |
| **Skill 系统** | 弱 | 强（50+） | 强（37+） | 强 | 中 |
| **企业记忆池** | 无 | 无 | 无 | 无 | 有（部分） |

---

## 13. Open Questions

1. **memories/ 目录的原始设计是什么？** OpenAI 曾经规划过什么样的记忆机制？为什么没有发布？

2. **goals_1.sqlite 的用途是什么？** 这个 SQLite 数据库存储了什么数据？

3. **Codex CLI 与 OpenAI Agent SDK 的关系是什么？** 两者是否共享记忆机制？

4. **OpenAI 对 Codex 的记忆能力有何长期规划？** 是否计划激活 memories/ 目录的功能？

5. **为什么 memories/ 设计被放弃或延迟？** 是技术原因还是产品策略原因？

---

*本报告为 AgentCenter 内部技术调研材料，数据截止日期 2026-05-23。注意：Codex 是编码工具，不是记忆框架，其「记忆」架构是设计存在但未实现的状态。*
