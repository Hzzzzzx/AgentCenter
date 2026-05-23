# Hermes Agent 调研报告

> AgentCenter 记忆系统调研 · 开源项目 #8
> 调研日期：2026-05-23

## 项目概览

Hermes Agent 是由 Nous Research 维护的开源 autonomous AI agent 框架，截至 2026 年 5 月已在 GitHub 获得 163,500+ stars，展现出惊人的社区影响力。该项目采用 Python（88.5%）和 TypeScript（8.5%）混合开发，代码托管于 NousResearch/hermes-agent，协议为 MIT license，当前稳定版本为 v0.14.0（2026-05-16）。

与本系列前几份报告聚焦的专用记忆库不同，Hermes Agent 的定位是**完整的 autonomous agent 框架**，记忆系统仅为其核心能力之一。其设计哲学强调 platform-agnostic：同一套 AIAgent class 可同时服务于 CLI、gateway、ACP、batch 以及 API server 等多种运行场景。项目的另一核心卖点是 70+ 内置工具、28 个工具集以及 18+ LLM provider 的广泛集成能力。

## 架构解析

### 整体架构图（ASCII）

```
┌─────────────────────────────────────────────────────────┐
│                      Hermes Agent                         │
│                   (AIAgent Core)                         │
├─────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │
│  │  Session    │  │ Persistent  │  │     Skill       │  │
│  │  Memory     │  │   Memory    │  │     Memory      │  │
│  │ (FTS5/SQLite│  │  (Files)    │  │   (Auto-learn)  │  │
│  └─────────────┘  └─────────────┘  └─────────────────┘  │
├─────────────────────────────────────────────────────────┤
│              External Memory Providers (×8 plugins)       │
│  Honcho │ OpenViking │ Mem0 │ Hindsight │ Holographic  │
│  RetainDB │ ByteRover │ Supermemory                       │
├─────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │
│  │  70+ Tools  │  │28 Toolsets  │  │ 18+ LLM Providers│ │
│  └─────────────┘  └─────────────┘  └─────────────────┘  │
├─────────────────────────────────────────────────────────┤
│        Multi-Platform Gateways                            │
│  Telegram │ Discord │ Slack │ WhatsApp │ Signal          │
│  钉钉 │ 飞书                                               │
└─────────────────────────────────────────────────────────┘
```

从图中可以看出，Hermes Agent 采用了清晰的三层记忆架构：Session Memory、Persistent Memory 和 Skill Memory，其中 Session Memory 通过 SQLite + FTS5 实现无限历史搜索，Persistent Memory 基于文件系统中的两个 Markdown 文件（MEMORY.md 和 USER.md）存储，而 Skill Memory 则通过自学习循环自动积累。

### 双层内存架构（内置 + 外部 Provider）

Hermes Agent 的内存体系分为两个层次：

**第一层：内置内存系统（Bounded Curated Memory）**。这一层是 Hermes Agent 的默认记忆机制，完全依赖本地文件系统，不依赖任何外部服务。它通过两个固定大小的 Markdown 文件实现，容量有明确上限，属于典型的 bounded curation 策略。

**第二层：外部 Memory Provider 插件生态**。通过实现统一的 MemoryProvider ABC 接口，Hermes Agent 支持同时接入 8 种外部记忆服务。这些 Provider 的共同特点是：任一时刻只能有一个处于激活状态，不支持多个 Provider 并行工作。这种设计简化了状态管理的复杂度，但也限制了灵活性。

两层之间的关系是**替代而非叠加**：启用外部 Provider 时，内置内存系统并非被 дополня（补充），而是被完全替换。Agent 与记忆的交互方式保持不变，但数据的实际存储位置和检索能力取决于当前激活的 Provider。

### MEMORY.md / USER.md 机制

内置 Persistent Memory 的核心就是 `~/.hermes/memories/` 目录下的两个文件：

**MEMORY.md** 存储 Agent 自身的笔记，包括环境事实（如项目路径、工具位置）、团队协作约定（如代码风格规范）、以及从错误中总结的教训。文件大小限制在 2,200 characters（约 800 tokens）。

**USER.md** 存储用户画像，包括个人偏好（如喜欢的沟通方式）、交流风格（如正式/随意）、以及用户对 Agent 的期望（如响应详细程度）。文件大小限制在 1,375 characters（约 500 tokens）。

这两个文件的内容在每个 session 启动时以 frozen snapshot 的形式注入到 system prompt 中，使用特殊分隔符 § 标记边界。注入过程发生在 LLM 推理之前，因此 Agent 在整个 session 中都能感知到这些背景信息。

容量管理机制包含三个关键设计：**容量检查**——写入前验证空间，不足时返回明确错误而非静默截断；**去重检查**——新条目与现有内容比对，避免重复记录；**安全扫描**——敏感信息（如 password、api_key）不会被写入记忆。

### Session Search（FTS5）

Session Memory 层提供了基于 SQLite + FTS5（Full-Text Search 5）的无限历史搜索能力。与传统基于关键词的模糊匹配不同，FTS5 支持多种查询模式：

**discovery 模式**：用于在大量历史会话中发现相关上下文。Agent 可以用自然语言描述想要查找的内容，系统返回匹配度最高的历史片段。

**scroll 模式**：用于按时间顺序浏览某个话题的完整上下文。返回的结果按时间戳排序，可以向上或向下滚动查看。

**browse 模式**：用于随机抽样浏览历史，发现可能被遗漏的有价值信息。

FTS5 的优势在于查询性能——即使会话历史达到数万条，搜索延迟仍能保持在毫秒级。这对于需要频繁回溯历史上下文的复杂任务至关重要。

### 外部内存提供者生态（8个）

以下是 8 个官方支持的 Memory Provider 的对比分析：

| Provider | 存储方式 | 费用 | 核心特色 |
|----------|---------|------|----------|
| **Honcho** | Cloud | 付费 | Dialectical user modeling（辩证用户建模）|
| **OpenViking** | Self-hosted | 免费 | 文件系统层级结构 + 分层加载策略 |
| **Mem0** | Cloud | 付费 | 服务端 LLM extraction（服务端 LLM 提取）|
| **Hindsight** | Cloud/Local | 免费/付费 | 知识图谱 + reflect synthesis（反思综合）|
| **Holographic** | Local | 免费 | HRR algebra + trust scores（信任评分）|
| **RetainDB** | Cloud | $20/月 | Delta compression（增量压缩）|
| **ByteRover** | Local/Cloud | 免费/付费 | Pre-compressed extraction（预压缩提取）|
| **Supermemory** | Cloud | 付费 | Context isolation + session graph（上下文隔离 + 会话图）|

从功能定位来看，这 8 个 Provider 覆盖了从纯本地到纯云端、从免费到付费的各种组合。Holographic 和 OpenViking 作为免费本地方案，适合对数据隐私有严格要求的企业；Mem0 和 Supermemory 作为云端付费方案，适合追求最大便利性的团队；Hindsight 的知识图谱路线则是技术层面的差异化选择。

所有 Provider 必须实现 MemoryProvider ABC 接口，核心方法包括：initialize()（初始化）、system_prompt_block()（生成注入 system prompt 的记忆块）、prefetch()（预取）、sync_turn()（同步对话轮次）、get_tool_schemas()（提供工具 schema）、handle_tool_call()（处理工具调用）以及 shutdown()（优雅关闭）。

### 自学习循环

Hermes Agent 拥有一个在同类框架中相当独特的能力——**自学习循环（Self-Learning Loop）**。这一机制的触发条件包括：完成超过 5 次 tool call 的复杂任务、修复了某个错误、或者发现了更优的工作流程。

一旦触发，Agent 会自动将当前的问题解决策略总结为一条 skill，保存到 Skill Memory 中。这个过程完全自动化，不需要用户干预。积累的 skill 可以在未来的任务中被调用，形成"经验积累-复用"的正向循环。

这一设计解决了一个实际问题：很多框架需要用户手动维护指令库或 best practice 文档，而 Hermes Agent 将这个过程自动化了。当然，自动化也带来了质量控制的风险——错误的经验同样会被学习并复现。

## 核心设计思想

### 为什么限制内存大小？

MEMORY.md（2,200 chars）和 USER.md（1,375 chars）的容量限制看似激进，实际上是一种有意识的 design choice。在 LLM 语境中，context window 是稀缺资源。限制每条记忆的大小，实质上是在确保每条记忆都有足够的 context 来保证 LLM 正确理解和应用这些信息。

如果允许无限膨胀的记忆，LLM 面临的问题不是信息不足，而是信息过载——大量低质量的记忆条目会稀释关键信息，导致 retrieval 的性价比下降。Bounded curation 策略迫使 Agent（和用户）只保留真正重要的信息。

### 子字符串匹配的取舍

记忆的 replace 和 remove 操作基于子字符串匹配（substring matching）而非 ID 或精确路径。这种设计的优势是简单——不需要维护复杂的索引结构，用户可以直观地理解"找到这段文字并替换"的语义。但劣势同样明显：当记忆条目过长或者需要精确匹配时，子字符串匹配的歧义性可能导致非预期的修改。

Hermes Agent 在这里选择了简单性和可理解性的平衡，而非精确性和灵活性的极致。

### 为什么同时只允许一个外部 Provider？

这个设计背后可能的考量是**状态一致性**和**复杂度控制**。如果允许多个 Provider 同时工作，就需要解决数据同步、冲突解决和优先级判定等一系列问题。单一 Provider 模式将这个复杂度降为零——每次激活新 Provider 时，只需清空旧状态并加载新状态即可。

从另一个角度看，这个设计也反映了 Hermes Agent 作为 full agent framework 的定位：它不是一个通用的记忆管理层，而是一个面向特定场景的整体解决方案。平台方在选择 Provider 时通常有明确的使用场景，不需要动态切换。

### 自学习循环的意义

自学习循环是 Hermes Agent 最具差异化的设计。在传统的 agent 框架中，skill 和 tool 通常由开发者预定义，用户无法快速地将新发现的工作流程转化为可复用的能力。Hermes Agent 将这个过程自动化，使得 Agent 在使用过程中不断"成长"。

然而，这一机制的有效性高度依赖于 LLM 的自我反思质量。如果 LLM 无法准确提炼有效经验，或者错误地总结了失败经验，自学习循环反而可能成为错误的放大器。

## 优势分析

**社区影响力与生态成熟度**。163,500+ stars 的规模意味着大量的社区贡献、持续的安全更新和丰富的使用案例。对于企业用户而言，这是一个低风险的选型——项目背后有活跃的维护团队和广泛的社区验证。

**三层记忆架构的完整性**。Session Memory（搜索）+ Persistent Memory（知识）+ Skill Memory（经验）的组合，覆盖了 agent 记忆的三个核心维度，且每一层都有明确的技术实现。

**多样化的 Provider 生态**。8 个 external memory provider 提供了丰富的选择空间，从完全本地免费到云端付费，从简单存储到知识图谱，企业可以根据自身的数据策略和安全要求选择最合适的方案。

**自学习循环的先发优势**。这一机制在同类框架中并不多见，它解决了 agent 在长时间运行中"经验积累"的问题，使得 agent 能够从每次任务执行中学习并改进。

**多平台 gateway 支持**。Telegram、Discord、Slack、WhatsApp、Signal、钉钉、飞书等平台的原生集成，使得 Hermes Agent 可以作为企业统一 agent 平台的底层引擎。

**serverless 部署支持**。通过 Daytona 和 Modal 的官方支持，部署门槛进一步降低。

## 局限与不足

**记忆容量极小**。2,200 + 1,375 chars 的总容量约合 1,300 tokens，对于需要处理大量领域知识的 agent 而言，这个限制几乎是致命的。实际使用中，用户可能需要频繁地在记忆条目的质量和数量之间做取舍。

**全框架而非记忆库**。如果企业的目标仅是增强现有 agent 的记忆能力，引入 Hermes Agent 意味着引入整个框架，复杂度显著上升。内置内存系统虽然可以独立使用，但其 bounded curation 的容量限制使得它无法作为生产级记忆库。

**单一 Provider 限制**。同时只允许一个 external memory provider 活跃，对于需要在不同场景下使用不同存储方案的用户而言，需要在 Provider 之间做非此即彼的选择。

**无内置知识图谱**。尽管 Hindsight 等外部 Provider 支持知识图谱，但 Hermes Agent 内核本身不提供这一能力。对于需要复杂关系推理的场景，需要依赖外部组件。

**无 temporal reasoning 内置支持**。时间相关的推理（如"上周我做了什么"）需要依赖 Session Search 的 FTS5 能力，但 FTS5 本身不包含时间推理逻辑。

**Windows 支持仍在 beta**。对于以 Windows 为主要工作环境的企业，这个限制增加了评估和采用的门槛。

**平台锁定**。虽然核心是 platform-agnostic 的，但整个生态（工具集、Provider 集成、gateway）都围绕 Nous 体系构建，迁移到其他体系的成本较高。

## 与 AgentCenter 的契合度

从架构定位来看，Hermes Agent 是一个 full autonomous agent framework，而 AgentCenter 是一个 enterprise agent orchestration platform。两者的设计目标存在本质差异：Hermes Agent 关注的是让单个 agent 更聪明，而 AgentCenter 关注的是让多个 agent 在企业流程中协同工作。

在记忆系统层面，Hermes Agent 的三层架构（Session / Persistent / Skill Memory）为 AgentCenter 提供了一个可参考的设计范本。尤其是 Skill Memory 的自学习循环机制，在企业场景下有较高的应用价值——不同 agent 可以在执行任务过程中自动积累业务经验。

然而，Hermes Agent 的内置内存系统容量太小，无法直接作为 AgentCenter 的记忆库使用。其 external memory provider 生态反而更具参考价值——如果 AgentCenter 需要实现 memory provider 抽象层，Hermes Agent 的 MemoryProvider ABC 是一个值得研究的接口设计。

值得注意的是，Hermes Agent 的 multi-platform gateway 能力与 AgentCenter 的 Bridge 层在功能上有一定重叠。AgentCenter 目前通过 Java Bridge 对接外部系统，如果未来需要扩展到更多平台（Slack、飞书等），Hermes Agent 的 gateway 集成模式值得借鉴。

从选型角度看，如果 AgentCenter 计划构建统一的 agent 记忆层，Hermes Agent 的"bounded curated memory + external provider plugin"双层模式是一个经过大规模社区验证的可行方案。关键的设计决策——容量限制、单一 Provider、frozen snapshot 注入——都有其内在逻辑，AgentCenter 在设计自己的记忆层时可以这些设计做出有意识的取舍。

## 参考

- GitHub: NousResearch/hermes-agent（163,500+ stars, MIT license, v0.14.0）
- 文档: https://github.com/NousResearch/hermes-agent
- MemoryProvider ABC Interface: Internal implementation in hermes_agent/memory/providers/
- FTS5 Session Search: Internal implementation in hermes_agent/memory/session_search.py
- 自学习循环机制: Internal implementation in hermes_agent/skills/self_learning.py
