# OpenCode 深度研究报告

> AgentCenter 记忆系统调研 · 编码工具评估 #18
> 调研日期：2026-05-23
> 评级：Tier D（编码工具的记忆能力评估，非记忆框架）

---

## 1. Reader Verdict

**一句话总结**：OpenCode 是一个配置驱动的 AI 编程工具，通过 AGENTS.md 文件和 ~/.config/opencode/skills/ 目录提供项目级和全局级的技能配置，当前**没有跨会话自动记忆能力**。

**核心评价**：OpenCode 的记忆相关能力本质上是**配置文件系统**而非**记忆框架**。AGENTS.md 作为项目级配置，skills/ 目录作为可复用技能库，两者都是静态的、文件驱动的配置。OpenCode 并不维护一个动态的记忆存储，也不提供检索 API。每次会话开始时，OpenCode 从文件系统加载 AGENTS.md 和 skills，但**不会自动将对话内容沉淀为记忆**。如果用户希望 OpenCode 记住某些信息，必须通过手动编辑 AGENTS.md 或创建新的 skill 文件来实现。这与 Mem0、Letta 等专门的记忆框架有本质区别。

**重要提示**：OpenCode 的设计哲学是「配置即记忆」——你通过编写 AGENTS.md 来告诉 OpenCode 怎么工作，而不是让它自动从历史中学习。这是一种**声明式**的方法，与大多数记忆框架的**自组织**方法截然不同。

---

## 2. Framework Profile

| 属性 | 值 |
|------|-----|
| **项目名称** | OpenCode |
| **开发组织** | opencode-ai |
| **GitHub** | https://github.com/opencode-ai/opencode |
| **Stars** | 12,700 |
| **开源状态** | ✅ Yes（已 Archived → charmbracelet/crush） |
| **许可证** | MIT |
| **定位** | AI 编程工具 / CLI |
| **技术栈** | Node.js + CLI |
| **记忆相关文件** | AGENTS.md（项目级）+ ~/.config/opencode/skills/（全局） |
| **存储介质** | 文件系统（Markdown/YAML/JSON） |
| **跨会话自动记忆** | 无 |
| **Benchmark 数据** | 无 |
| **与 AgentCenter 契合度** | 中（AgentCenter Bridge 基于 OpenCode，需要深入理解） |

---

## 3. Core Thesis

**核心论点**：OpenCode 相信**记忆应该显式声明，而非隐式积累**。与其让 AI 从历史对话中自动提取记忆（可能提取错误或遗漏），不如让人类直接编写 AGENTS.md 来声明项目的关键信息。

这个设计哲学的优势是**可控性**：人类完全掌控 OpenCode 知道什么、不知道什么。没有隐式的自动记忆，所有知识都是显式的配置文件。

劣势是**维护成本**：随着项目增长，AGENTS.md 可能变得臃肿，且需要人工维护记忆内容的准确性。

**Skill 系统**：OpenCode 的 skills 目录是一个可复用技能库。每个 skill 是一个独立文件，包含触发条件、执行动作、上下文格式等定义。这本质上是一个**插件系统**，而非记忆系统。

---

## 4. Theoretical Foundation

OpenCode 的理论基础建立在**配置驱动**而非**自组织**之上。大多数记忆框架（Mem0、Hindsight 等）假设 AI 应该自动从历史中学习和积累记忆。OpenCode 的哲学不同——它认为 AI 编程工具的「记忆」应该来自**显式配置**而非**隐式学习**。

这个哲学背后可能有几个考量：
1. **准确性**：自动提取的记忆可能有误，显式配置确保准确性
2. **可控性**：开发者完全掌控 AI 知道什么
3. **可审计性**：所有「记忆」都是文件，可以 git 追踪变更
4. **简洁性**：不需要复杂的记忆提取和检索管线

---

## 5. Memory Model

**警告：OpenCode 不是记忆框架**，以下描述的是其文件系统中与「记忆」概念相关的部分。

### 5.1 AGENTS.md（项目级配置）

**位置**：项目根目录下的 AGENTS.md

**内容**：声明式的项目级配置，包含：
- 项目的技术栈和目录结构
- 编码规范和风格指南
- 已知的工作流程和模式
- 项目特定的上下文信息

**记忆机制**：**无自动更新**。AGENTS.md 是静态文件，OpenCode 不会自动向其中添加内容。用户必须手动编辑。

### 5.2 Skills 目录（全局技能库）

**位置**：~/.config/opencode/skills/

**数量**：50+ 个预置 skill

**内容**：可复用的技能定义，包含：
- 技能名称和描述
- 触发条件（如何激活这个技能）
- 执行动作（调用什么工具、发送什么提示）
- 上下文格式（如何组织和传递上下文）

**记忆机制**：**无动态记忆**。Skills 是预定义的静态配置，不是从使用中自动生成的经验。

### 5.3 Session 上下文

**会话级上下文**：每次 OpenCode 会话开始时，从 AGENTS.md 和 skills/ 加载配置，构建初始上下文。这个上下文在会话期间有效，**会话结束后不保留**。

---

## 6. Architecture Deep Dive

### 6.1 配置文件加载流程

```
┌──────────────────────────────────────────────────────────────┐
│                 OpenCode 配置加载流程                          │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  1. 扫描项目目录，查找 AGENTS.md                              │
│                    ↓                                          │
│  2. 读取 ~/.config/opencode/skills/ 下的所有 skill 文件       │
│                    ↓                                          │
│  3. 根据会话类型和用户指令，匹配适用的 skills                  │
│                    ↓                                          │
│  4. 合并 AGENTS.md + 匹配的 skills，构建初始上下文            │
│                    ↓                                          │
│  5. 将上下文注入 LLM，开始会话                               │
│                                                               │
│  会话期间：新增信息不会自动写入 AGENTS.md                      │
│  会话结束：所有上下文丢失，不跨会话保留                        │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

### 6.2 Skill 系统架构

Skill 本质上是一个**条件触发的上下文模板**：

```yaml
# skill 文件结构（推测）
name: code-review
trigger:
  keywords: [review, PR, pull request]
  file_patterns: ["*.ts", "*.js"]
actions:
  - type: prompt
    template: |
      请审查以下代码，关注：
      1. 潜在的 bug
      2. 性能问题
      3. 安全漏洞
context:
  format: diff
```

### 6.3 AgentCenter 中的使用

AgentCenter 的 Java Bridge 通过 `opencode serve` API 与 OpenCode 交互：
- 工作目录固定在 `runtime-workspace/`（项目根目录下的 `runtime-workspace/`）
- 通过 SSE 推送实现流式响应
- 上下文通过 API 请求传入，不是从记忆系统自动获取

---

## 7. Design Tradeoffs

| 权衡维度 | 选了什么 | 代价 |
|---------|---------|------|
| **记忆方式** | 显式配置（AGENTS.md） | 无自动记忆，需要人工维护 |
| **跨会话持久化** | 文件系统（需手动编辑） | 会话间不自动传递信息 |
| **技能复用** | Skill 系统（插件化） | Skills 是静态的，不从使用中学习 |
| **可控性** | 人类完全掌控 | 维护成本随项目增长而增加 |
| **可审计性** | Git 可追踪所有变更 | 配置膨胀时难以管理 |
| **隐私** | 本地文件系统 | 不支持共享企业记忆池 |

---

## 8. Evidence

**当前状态验证**：
- OpenCode **没有**自动记忆 API
- **没有**跨会话自动检索机制
- **没有**向量嵌入或语义搜索
- **没有**从对话历史自动提取知识的管线

**用户必须手动编辑 AGENTS.md** 来让 OpenCode「记住」新信息。这与 Mem0 的 `add()` API 或 Hindsight 的 4 路检索形成鲜明对比。

**Skill 系统的实际用途**：用于定义特定任务的执行模板（如 code review、git 操作等），不是用于存储和检索记忆。

---

## 9. Applicability

| 场景 | 适用度 | 说明 |
|------|--------|------|
| **项目级上下文声明** | 强 | AGENTS.md 适合声明项目基本信息 |
| **编码规范传达** | 强 | Skills 可定义编码检查规则 |
| **跨会话知识保留** | 弱 | 需要手动维护，不自动 |
| **动态记忆积累** | 无 | 不从对话历史自动提取知识 |
| **企业知识管理** | 弱 | 无共享记忆池，无权限控制 |
| **复杂推理** | 弱 | 配置驱动，非自组织 |

---

## 10. Maturity

| 维度 | 评级 | 说明 |
|------|------|------|
| **记忆系统成熟度** | 无 | 不是记忆框架 |
| **工具成熟度** | 高 | AI 编程工具，能力强 |
| **文档完整性** | 高 | 有 SKILL.md 格式定义 |
| **企业特性** | 低 | 无多用户、无权限控制 |
| **与 AgentCenter 集成** | 高 | AgentCenter Bridge 基于 OpenCode |

---

## 11. AgentCenter Implications

### 11.1 OpenCode 在 AgentCenter 中的角色

AgentCenter 的 Java Bridge 是**OpenCode 的消费者**，不是 OpenCode 的记忆系统。Bridge 通过 `opencode serve` API 调用 OpenCode，传入任务上下文，接收流式响应。

**关键理解**：AgentCenter 需要自己的记忆系统来存储和管理企业知识，OpenCode 只负责执行具体的编程任务。两者是**互补关系**，不是替代关系。

### 11.2 能否利用 OpenCode 的 Skill 系统？

**潜力**：Skill 系统可以用于定义 AgentCenter 特有的技能模板，如「审查 PR」「生成测试用例」「更新文档」等。

**限制**：Skill 是静态配置，不具备动态记忆能力。AgentCenter 的企业知识（供应商规则、审批流程、人员关系）无法通过 Skill 系统管理。

### 11.3 设计建议

如果 AgentCenter 需要让 OpenCode 记住某些信息：
1. **通过 API 传入上下文**：在每次 API 调用时通过 prompt 传入需要的上下文
2. **维护一个上下文生成器**：Bridge 根据记忆系统中的内容，动态生成每次调用的 prompt
3. **不依赖 OpenCode 的自动记忆**：OpenCode 没有这个能力

---

## 12. Comparative Scorecard

| 能力 | OpenCode | Claude Code | Codex | Windsurf | 专用记忆框架 |
|------|----------|-------------|-------|----------|--------------|
| **自动记忆提取** | 无 | 无 | 无 | 无 | 有 |
| **跨会话持久化** | 弱（需手动） | 弱（需手动） | 弱（需手动） | 弱（需手动） | 强 |
| **语义检索** | 无 | 无 | 无 | 无 | 有 |
| **技能复用** | 强（50+ skills） | 强（37+ skills） | 弱 | 强（rules） | 中 |
| **项目上下文** | AGENTS.md | CLAUDE.md | AGENTS.md | .windsurf/rules | 多样 |
| **企业记忆池** | 无 | 无 | 无 | 无 | 有（部分框架） |

---

## 13. Open Questions

1. **OpenCode 会增加自动记忆能力吗？** 作为一个活跃开发的工具，OpenCode 的 roadmap 中是否有记忆系统的规划？

2. **AgentCenter 的记忆系统如何与 OpenCode 协同？** Bridge 需要在每次 API 调用时注入上下文，这个上下文生成的最佳实践是什么？

3. **Skill 系统能否扩展为记忆存储？** 如果把每个 skill 看作一个「记忆单元」，能否通过某种机制让 skills 从使用中学习？

4. **多 OpenCode 实例的上下文共享？** 当 AgentCenter 同时运行多个任务时，是否需要某种共享的上下文机制？

5. **OpenCode 的 skill 系统与 AgentCenter 的 workflow 系统如何区分？** 两者都可能定义任务模板，边界在哪里？

---

*本报告为 AgentCenter 内部技术调研材料，数据截止日期 2026-05-23。注意：OpenCode 是编码工具，不是记忆框架，其「记忆」能力仅限于配置文件。*
