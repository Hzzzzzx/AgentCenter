# Letta/MemGPT 深度研究报告

> AgentCenter 记忆系统调研 · 深度分析 #2
> 报告日期：2026-05-23

---

## 1. Reader Verdict

**TL;DR**

Letta（前身 MemGPT）是一个将大语言模型视为操作系统的多层级记忆管理框架，通过 OS 风格的内存层次结构（Core Memory → Recall Memory → Archival Memory）实现 Agent 的长期记忆与上下文管理。其核心创新在于将操作系统经典的 RAM/Cache/磁盘三层架构映射到 LLM 记忆管理，并赋予 Agent 自主调度记忆的能力。设计理念有扎实的学术基础（peer-reviewed 论文），但工程化程度不如商业产品。

**Should you use this?**

**取决于场景**。对于需要构建完整 Agent 运行时的场景，Letta 是值得考虑的选择——它的三层记忆模型和上下文窗口计算器是经过验证的有效抽象。但对于只想借鉴记忆层设计的团队，Letta 的完整运行时约束过于侵入；建议提取其内存管理思想而非直接集成。对于 AgentCenter 这样的企业编排平台，Letta 的 Agent 中心设计（per-agent 私有记忆）与共享企业记忆池需求存在根本冲突。

---

## 2. Framework Profile

| 维度 | 信息 |
|------|------|
| 名称 | Letta（原 MemGPT） |
| GitHub | https://github.com/letta-ai/letta |
| Stars | 22,900 |
| 许可 | Apache 2.0 |
| 开源状态 | ✅ 开源 |
| 主语言 | Python（运行时）+ TypeScript（SDK） |
| 最后更新 | 2026-05（持续活跃） |
| 发布者 | Letta AI（2026 年获 $10M Seed） |
| 论文 | "MemGPT: Towards LLMs as Operating Systems"（arXiv:2310.08560） |
| 官网 | docs.letta.com |

---

## 3. Core Thesis

**一句话设计哲学**：把 LLM 当操作系统，记忆就是它的 RAM、Cache 和磁盘。

Letta 的核心信念来自一个深刻的类比：传统操作系统的多层内存架构（寄存器 → CPU Cache → RAM → 磁盘）为 LLM 提供了完美的记忆管理模板。如同操作系统在不同内存层级之间智能调度数据一样，Letta 让 Agent 能够在不同的记忆层级之间自主调度信息，从而在有限的上下文窗口内实现近乎无限的"记忆"能力。

更深层的原因在于：LLM 的上下文窗口本质上就是一个受限于成本的有限资源池。操作系统面对的是物理内存的硬性限制，而 LLM 面对的是 token 成本和推理成本的限制。两者的本质都是资源调度问题，因此 OS 的设计模式具有天然的可移植性。

---

## 4. Theoretical Foundation

Letta 的设计融合了以下理论基础：

**操作系统内存管理理论**

三层记忆模型（Core/Recall/Archival）直接映射自 OS 的内存层次结构：

- **Core Memory (RAM)**：始终带电，访问延迟最低，容量最小
- **Recall Memory (Cache)**：介于高速缓存，容量中等，访问需要搜索
- **Archival Memory (磁盘)**：容量最大，访问最慢，需要显式加载

这是计算机体系结构中经过数十年验证的经典设计，Letta 将其创造性地应用到 LLM 上下文管理。

**虚拟内存与上下文切换**

Letta 的 offload 和 recall 机制类似于操作系统的虚拟内存机制：当 RAM（Core Memory）不足时，将不常用的页面（记忆块）换出到磁盘（Archival Memory）；当需要访问已换出的内容时，再将其换入。上下文窗口计算器（Context Window Calculator）充当这一过程的调度器。

**Agent 自主性理论**

Letta 赋予 Agent 自我反思和记忆编辑能力的思想来源于 Agent 系统的"元认知"（metacognition）概念。Agent 不仅要执行任务，还要监控和调节自己的认知过程。这种思想在心理学和认知科学中有深厚根基，被认为是高等认知能力的标志之一。

**LLM as OS 的学术基础**

MemGPT 论文（arXiv:2310.08560）经过同行评审，在学术会议上发表。设计决策背后有系统的理论支撑，包括：

- 上下文窗口作为"受限 RAM"的资源模型
- 基于 LLM 工具调用能力的自主记忆操作
- 记忆管理的"遗忘"模拟

---

## 5. Memory Model

### 记忆层级

| 层级 | OS 对应 | 容量 | 访问延迟 | 存储形式 |
|------|---------|------|----------|----------|
| **Core Memory** | RAM | ~4K-32K tokens | 零（始终在上下文） | Key-Value Block（结构化 JSON） |
| **Recall Memory** | CPU Cache |/work_id | ~50-200ms | 对话历史（向量嵌入） |
| **Archival Memory** | 磁盘 | 无限 | ~200-500ms | 向量数据库（全量存储） |

### 生命周期阶段

```
┌─────────────────────────────────────────────────────────────┐
│                    TOKEN BUDGET TRACKING                     │
│                                                             │
│   System Prompt │ Core Memory │ Tools │ Recent Messages      │
│        [███████] │ [█████████] │ [███] │ [████████████]   │
│                                                             │
│   ⚡ When approaching limit:                                │
│      - Move older messages to Recall Memory                  │
│      - Archive less relevant Core Memory blocks              │
│      - Summarize and compress historical context            │
└─────────────────────────────────────────────────────────────┘
```

### 三层之间的数据流动

**向上流动（Offload）**：

- Core Memory 接近容量 → 最近最少使用的内容被推向 Recall Memory
- Recall Memory 中的旧内容 → 被归档到 Archival Memory
- 被动、被调度驱动的过程

**向下流动（Recall）**：

- Agent 需要某条信息但不在 Core Memory 中
- Agent 通过搜索工具在 Recall Memory 或 Archival Memory 中查找
- 找到后可以显式加载到 Core Memory
- 主动、Agent 驱动的过程

### Agent 自编辑操作

```python
# Core Memory 操作
core_memory_append(block_name="preferences", content="用户喜欢简洁的回复风格")
core_memory_replace(block_name="context", old_content="...", new_content="...")

# 反思操作
memory_rethink(insights="用户最近三次都问过关于报表的问题")

# Archival Memory 操作
archival_memory_insert(content="2024年Q1完成的项目总结")
result = archival_memory_search(query="去年完成的项目有哪些")

# 对话历史检索
results = conversation_search(query="用户之前提到的预算问题")
```

---

## 6. Architecture Deep Dive

### 整体架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                         LLM (Operating System)                       │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │                    AGENT LOOP                                    │  │
│  │   ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐│  │
│  │   │ Observe  │───▶│  Think   │───▶│  Act     │───▶│ Reflect  ││  │
│  │   └──────────┘    └──────────┘    └──────────┘    └──────────┘│  │
│  └────────────────────────────────────────────────────────────────┘  │
│                              │                                       │
│  ┌───────────────────────────┼───────────────────────────────────┐  │
│  │                    MEMORY LAYERS                                │  │
│  │                                                                │  │
│  │   ┌─────────────────────────────────────────────────────────┐  │  │
│  │   │           CORE MEMORY (RAM) - Always in context          │  │  │
│  │   │   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │  │  │
│  │   │   │   person    │  │ preferences │  │  context    │     │  │  │
│  │   │   │   block     │  │   block     │  │   block     │     │  │  │
│  │   │   └─────────────┘  └─────────────┘  └─────────────┘     │  │  │
│  │   │        ▲               ▲               ▲                  │  │  │
│  │   │        │  read/write   │  read/write   │  read/write     │  │  │
│  │   └────────┼───────────────┼───────────────┼────────────────┘  │  │
│  │            │               │               │                    │  │
│  │   ┌────────┴───────────────┴───────────────┴───────────────┐  │  │
│  │   │         RECALL MEMORY (Cache) - Conversation History   │  │  │
│  │   │   ┌─────────────────────────────────────────────────┐  │  │  │
│  │   │   │  [msg_001] [msg_002] [msg_003] ... [msg_N]      │  │  │  │
│  │   │   │         Searchable, paginated                    │  │  │  │
│  │   │   └─────────────────────────────────────────────────┘  │  │  │
│  │   └─────────────────────────────────────────────────────────┘  │  │
│  │                              │                                  │  │
│  │   ┌──────────────────────────┴──────────────────────────────┐  │  │
│  │   │            ARCHIVAL MEMORY (Disk) - Vector DB             │  │  │
│  │   │   ┌─────────────────────────────────────────────────┐    │  │  │
│  │   │   │  [doc_001] [doc_002] [doc_003] ... [doc_N]     │    │  │  │
│  │   │   │       Embedding + Full-text Search              │    │  │  │
│  │   │   └─────────────────────────────────────────────────┘    │  │  │
│  │   └─────────────────────────────────────────────────────────┘  │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │                  CONTEXT WINDOW CALCULATOR                      │  │
│  │   System Prompt │ Core Memory │ Tools │ Recent Messages        │  │
│  │        [███████] │ [█████████] │ [███] │ [████████████]        │  │
│  │              token budget tracking & offload decisions          │  │
│  └────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### 存储架构

| 存储类型 | 技术选型 | 存储内容 |
|----------|----------|----------|
| Core Memory | LLM 上下文（RAM） | Block 结构化 JSON（person/preferences/context 等） |
| Recall Memory | PostgreSQL + 内部向量存储 | 对话历史，embedding 存储 |
| Archival Memory | 外部向量数据库 | 长期知识文档、过往项目经验 |

### 上下文窗口计算器

Context Window Calculator 实时监控各组件 token 消耗：

```
┌──────────────────────────────────────────────────────────────┐
│                   TOKEN BUDGET TRACKING                       │
│                                                               │
│   ┌──────────────────────────────────────────────────────┐    │
│   │  System Prompt:     ██████████░░░░░░░░░░  2,000    │    │
│   │  Core Memory:       ████████████████░░░░  4,000    │    │
│   │  Tools:             ██████░░░░░░░░░░░░░░░  1,200    │    │
│   │  Recent Messages:   ██████████████████░░░  3,800    │    │
│   ├──────────────────────────────────────────────────────┤    │
│   │  TOTAL:             ████████████████████░░  11,000   │    │
│   │  LIMIT: 128,000 ──────────────────────────────────   │    │
│   └──────────────────────────────────────────────────────┘    │
│                                                               │
│   ⚡ When approaching limit:                                   │
│      - Move older messages to Recall Memory                   │
│      - Archive less relevant Core Memory blocks                │
│      - Summarize and compress historical context             │
└──────────────────────────────────────────────────────────────┘
```

### 关键技术组件

**Agent Loop**：Observe → Think → Act → Reflect 的四阶段循环，是 Letta Agent 的核心执行模型。

**Block 结构**：Core Memory 中的记忆以 Key-Value Block 组织，每个 Block 有名称（如 "person"、"preferences"、"context"）。

**工具函数接口**：Agent 与记忆交互的唯一界面，包括 core_memory_append/replace、archival_memory_insert/search、conversation_search 等。

---

## 7. Design Tradeoffs

| 选择 | 理由 | 牺牲 |
|------|------|------|
| **OS 内存管理类比** | OS 的 RAM/Cache/磁盘三层架构经过数十年验证，在成本与性能之间取得平衡；将 LLM 上下文窗口视为受限资源池，资源调度问题具有天然类比性 | OS 设计针对物理内存，LLM 的 token 消耗与语义重要性不完全相关；简单的层级映射可能忽略记忆的语义维度 |
| **Agent 自主记忆管理** | Agent 最清楚当前任务目标、用户需求、什么信息相关；自主管理比开发者手动决定更动态、更个性化 | Agent 可能做出次优的记忆调度决策（过早归档重要信息，或在 Core Memory 中保留过多无关内容）；不确定性难以接受 |
| **上下文窗口计算器** | 实时追踪 token 预算，在接近上限时主动触发记忆操作；保证 Agent 始终有足够上下文空间继续工作 | 计算器调度策略依赖启发式规则；在复杂场景下可能频繁触发不必要的 offload/recall 操作 |
| **Core Memory Block 结构** | 结构化 Key-Value 便于语义分区和精确读写；Block 名称提供语义组织 | 固定结构限制了灵活性；复杂的跨 Block 关系难以表达 |
| **完整 Agent 运行时** | 提供端到端解决方案，减少集成工作量；与 Letta 的深度绑定确保记忆管理与 Agent 行为的一致性 | 过于侵入；AgentCenter 已具备 Agent 运行时基础设施，直接集成会导致架构冲突 |
| **Per-Agent 私有记忆** | 清晰的边界；每个 Agent 有独立的记忆空间；安全性高 | 不适合企业共享记忆场景；跨 Agent 知识共享困难；与 AgentCenter 的共享企业记忆池需求冲突 |
| **无 LLM 查询路径** | archival_memory_search 使用向量检索而非 LLM；降低延迟和成本；确定性输出 | 复杂语义查询不如 LLM 理解能力强；无法处理模糊或多义查询 |
| **Recall Memory 的被动保留** | Agent 无法直接编辑 Recall Memory，只能通过后续对话间接影响；设计简洁 | 无法主动管理长程依赖；重要的历史信息可能被遗忘或难以检索 |

---

## 8. Evidence

### Benchmark 得分

| 基准 | 分数 | 说明 |
|------|------|------|
| LongMemEval | **N/A** | 未发布任何标准化 benchmark 分数 |
| LoCoMo | **N/A** | 未发布 |
| Deep Memory Retrieval (DMR) | **N/A** | 未与 Graphiti 对比测试 |

### 能力维度估算（基于架构推断）

| 能力 | 估算区间 | 推理依据 |
|------|----------|----------|
| IE（信息提取） | 55-65% | 依赖 LLM 工具函数，不做结构化提取；信息提取能力受限于 agent 的判断力 |
| TR（时间推理） | 35-45% | 三层记忆没有时间维度；Core Memory 里的内容没有时间戳 |
| KU（知识更新） | 50-60% | agent 可以 core_memory_replace 更新旧记忆，但更新时机完全由 agent 自主决定 |
| MR（多跳推理） | 55-65% | archival_memory_search 支持搜索，但没有图遍历或 cross-encoder；多跳依赖 agent 多轮工具调用 |
| MSR（跨会话） | **65-75%** | 三层模型理论上为跨会话记忆提供了完整框架；core memory 始终在上下文中，跨会话不丢失 |

> 注：Letta 未发布任何标准化 benchmark 分数，所有估算均为架构推断，可信度为低。

---

## 9. Applicability

| 场景 | 适合度 | 原因 |
|------|--------|------|
| **长期运行的自主 Agent** | ★★★★☆ | 三层模型为长时运行提供了可持续的记忆扩展方案；Agent 可以根据任务进展动态调整记忆优先级 |
| **需要上下文窗口管理的场景** | ★★★★☆ | 上下文窗口计算器实时追踪 token 消耗；在接近上限时主动触发记忆操作 |
| **研究导向的 Agent 系统** | ★★★★☆ | 论文经过同行评审；设计决策背后有系统理论支撑；适合学术研究和实验 |
| **企业级多 Agent 协作** | ★★☆☆☆ | per-agent 私有记忆模型不适合共享企业记忆池；缺乏跨 Agent 检索能力 |
| **需要精确时间推理的场景** | ★★☆☆☆ | 三层记忆没有时间维度；无法区分"什么时候写的"和"内容本身" |
| **结构化数据记忆** | ★★☆☆☆ | 主要针对对话场景设计；导入外部知识通常需要转换为对话格式 |
| **需要图谱推理的场景** | ★★☆☆☆ | 向量检索缺乏结构化知识表示能力；复杂关联关系无法捕获 |

---

## 10. Maturity

| 维度 | 评估 |
|------|------|
| **版本** | v1.x（生产就绪） |
| **代码质量** | 高；Python 代码组织清晰；经过学术评审 |
| **文档** | 完善；docs.letta.com 提供完整 API 文档、架构说明、示例 |
| **测试** | 单元测试 + 集成测试覆盖；CI/CD 流程完善 |
| **社区** | 21K stars；活跃的 GitHub Discussion；学术社区引用较多 |
| **生产就绪** | 中；Apache 2.0 开源版本已用于多个研究和生产环境；但整体更偏研究导向 |
| **学术基础** | 强；arXiv:2310.08560 论文经过同行评审 |
| **技术债务** | 低；代码库年轻；技术选型现代 |
| **安全** | 企业特性（SSO、RBAC）需要商业版本 |

---

## 11. AgentCenter Implications

### 可借鉴的设计（3-5 items）

**1. 三层记忆抽象**

Letta 的 Core/Recall/Archival 三层划分是经过验证的有效抽象。AgentCenter 可以借鉴这一思想，设计适合企业场景的多层级记忆系统。关键是要根据 AgentCenter 的实际场景调整各层的定义和容量——例如，Active Work Item 状态对应 Core Memory，Recent Workflow History 对应 Recall Memory，历史 Archive 对应 Archival Memory。

**2. 记忆操作工具化**

Agent 通过工具函数管理记忆的模式值得借鉴。将记忆的读写搜操作抽象为标准工具函数，Agent 通过工具调用与记忆交互，而非直接操作。这种设计既保持了灵活性又提供了结构约束。AgentCenter 可以设计类似的自定义工具集，暴露给上层 Agent。

**3. 上下文预算监控**

实时监控和调度记忆层的思想对于 AgentCenter 这样的多 Agent 平台尤为重要。可以设计一个全局的上下文预算管理器，追踪所有 Agent 的 token 消耗，避免单个 Agent 过度消耗资源导致整体服务质量下降。

**4. 分层调度策略**

基于访问频率、时间、相关性的自动调度策略可以直接应用到 AgentCenter 的记忆系统中。冷热数据分离、基于访问频率的淘汰等机制对企业场景同样适用。

**5. 自编辑工具函数设计**

Agent 通过 core_memory_append/replace 等工具函数管理记忆的模式，是将记忆操作标准化的良好实践。AgentCenter 可以设计类似的自定义工具集，作为 Agent 与记忆系统交互的统一界面。

### 不适合的部分（2-3 items）

**1. 完整 Agent 运行时约束**

Letta 的完整产品是一个端到端的 Agent 平台，包含 LLM 接口、Agent 循环、状态管理等。AgentCenter 已经具备这些基础设施（Java Bridge + OpenCode Runtime Adapter），直接集成 Letta 会导致架构冲突。更合理的做法是提取其内存管理思想，而非运行时模式。

**2. Per-Agent 记忆隔离**

Letta 的记忆是 Agent 私有的，这与 AgentCenter 的企业多 Agent 协作场景不兼容。AgentCenter 需要的是共享记忆空间、跨 Agent 检索、权限控制的记忆服务，而非 Agent 私有的记忆管理。

**3. 过度的 Agent 自主性**

Letta 让 Agent 全权决定记忆管理（memory_rethink 等操作完全由 Agent 自主触发），在企业场景下这种不确定性难以接受。AgentCenter 更适合采用开发者配置 + Agent 建议的混合模式，保留人工干预和系统级调度能力。

### 迁移建议（practical steps）

1. **提取思想而非集成产品**：明确 Letta 的价值在于其设计思想（三层抽象 + 上下文窗口计算器），而非直接使用其完整运行时

2. **设计共享记忆服务**：将 Letta 的 per-agent 模型改造为共享记忆服务，支持多 Agent 共享、权限控制和审计追踪

3. **实现上下文预算监控**：参考 Letta 的 Context Window Calculator，设计 AgentCenter 全局的 token 预算追踪和调度机制

4. **评估调度策略**：Letta 的 offload/recall 策略依赖启发式规则，AgentCenter 需要根据实际工作负载调优

5. **保留人工干预能力**：不要让 Agent 完全自主管理记忆，系统层面需要保留强制调度和人工干预接口

---

## 12. Comparative Scorecard

| 维度 | Letta/MemGPT | Mem0 | Zep/Graphiti | Hindsight（锚点） |
|------|--------------|------|--------------|------------------|
| **LongMemEval** | N/A（推断~55%） | 49.0% | ~63.8% | **91.4%** |
| **TR（时间推理）** | 35-45% | 35-45% | **80-90%** | 75-85% |
| **MR（多跳推理）** | 55-65% | 45-55% | **70-80%** | **~91%** |
| **KU（知识更新）** | 50-60% | 50-60% | **78-88%** | 80-88% |
| **IE（信息提取）** | 55-65% | 55-65% | 60-70% | **80-90%** |
| **MSR（跨会话）** | **65-75%** | 50-60% | 55-65% | **~91%** |
| **知识图谱** | ❌ 无 | ❌ 无 | ✅ 有 | ❌ 无 |
| **时态模型** | ❌ 无 | ❌ 无 | ✅ bi-temporal | ⚠️ 过滤 |
| **API 简洁度** | ★★★☆☆ | ★★★★★ | ★★★☆☆ | ★★★☆☆ |
| **部署复杂度** | 高 | 低 | 高 | 中 |
| **企业记忆共享** | ❌ 不支持 | ⚠️ 有限 | ⚠️ 有限 | ❌ 不支持 |
| **学术基础** | **强（peer-reviewed）** | 弱 | 中 | 中 |

---

## 13. Open Questions

**1. Letta 的 LongMemEval 表现**

Letta 未发布任何标准化 benchmark 分数。这意味着无法确定其在长程记忆任务上的实际表现。架构推断的 55% MSR 能力是否准确，需要在相同评测环境下复现。具体的未知数：

- 如果 Letta 运行 LongMemEval，具体的子任务得分分布如何
- 三层模型在跨会话记忆任务上的实际表现是否优于架构推断
- 与其他框架相比，Letta 的差异化优势在哪里

**2. Agent 自主记忆管理的可靠性**

Letta 让 Agent 全权决定记忆管理（memory_rethink、core_memory_replace 等操作完全由 Agent 自主触发），这种不确定性在企业场景下难以接受。但具体的失败案例和成功率数据未被公开：

- Agent 在长期任务中做出错误记忆调度决策的频率是多少
- memory_rethink 触发的记忆重组有多少比例是有益的
- 系统是否有机制检测和纠正 Agent 的记忆管理失误

**3. 企业记忆共享的实现路径**

Letta 的设计是 per-agent 私有记忆，但 AgentCenter 需要共享企业记忆池。将 Letta 改造为共享记忆服务的成本和复杂度未被评估：

- 是否可以设计一种"虚拟 Agent 记忆空间"映射到共享存储的机制
- 权限控制如何在共享记忆层实现
- 多 Agent 并发访问同一记忆的冲突解决策略

**4. 上下文窗口计算器的调度策略**

Letta 的 offload/recall 调度策略依赖启发式规则，具体的参数设置和效果未被公开：

- 触发 offload 的阈值是如何确定的
- 不同 LLM 提供商（OpenAI、Anthropic等）的 token 计算方式是否准确
- 调度策略对不同类型的 Agent 任务是否有显著效果差异

**5. 与 AgentCenter 现有架构的集成成本**

Letta 设计为完整 Agent 运行时，AgentCenter 已有 Java Bridge + OpenCode Runtime Adapter 的基础设施。具体的集成挑战：

- 是否可以在不完全引入 Letta 运行时的前提下，复用其三层记忆模型
- 如果要将 Letta 作为独立记忆服务，需要改造多少现有代码
- Letta 的记忆导出格式是否与其他框架兼容

---

*本文档为 AgentCenter 内部技术调研材料，数据截止日期 2026-05-23。Letta 未发布标准化 benchmark 分数，所有估算均为架构推断，可信度为低。*
