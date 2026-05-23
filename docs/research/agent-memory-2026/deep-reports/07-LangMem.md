# LangMem 深度调研报告

> AgentCenter 记忆系统调研 · 开源项目 #7
> 调研日期：2026-05-23
> 报告编号：deep-reports/07-LangMem

---

## 1. Reader Verdict（TL;DR + 使用建议）

**一句话总结**：LangMem 是一个基于认知科学记忆三分法的轻量级记忆框架，内置 prompt 优化能力和结构化提取管线，但在 v0.0.30 的早期版本下生产级稳定性存疑。

**TL;DR**：
- 如果你已经在使用 LangChain/LangGraph 生态，LangMem 是自然的记忆扩展选择
- 如果你需要 prompt 优化能力，LangMem 是少数将此作为一等公民功能的框架
- 如果你需要稳定的生产级记忆系统，v0.0.30 的版本号意味着 high risk

**使用建议**：
| 场景 | 推荐度 | 理由 |
|------|--------|------|
| LangChain 生态集成 | ★★★★★ | 与 LangGraph BaseStore 无缝衔接 |
| Prompt 优化实验 | ★★★★☆ | 三种梯度式优化策略 |
| 生产级记忆系统 | ★★☆☆☆ | 版本 0.0.30，API 不稳定 |
| 多数据源整合 | ★☆☆☆☆ | 主要处理对话数据，无 ingestion 层 |
| 知识图谱构建 | ★☆☆☆☆ | 无内置图存储，纯扁平结构 |
| 时态推理 | ★☆☆☆☆ | 三种记忆类型均无时间维度 |

---

## 2. Framework Profile（框架画像）

| 维度 | 内容 |
|------|------|
| **项目名称** | LangMem |
| **GitHub** | langchain-ai/langmem |
| **Stars** | ~1,464 |
| **License** | MIT |
| **主要语言** | Python |
| **最新版本** | 0.0.30 |
| **生态定位** | LangChain/LangGraph 生态下的记忆框架 |
| **核心特性** | 三类记忆、prompt 优化、trustcall 结构化提取 |

---

## 3. Core Thesis（核心主张）

**Philosophy**：不同类型的记忆需要不同的处理方式——语义记忆（事实）、情景记忆（经历）、程序记忆（技能）它们的形成方式、检索模式和更新频率本就不同，用同一种机制处理是认知上的懒惰。

---

## 4. Theoretical Foundation（理论基础）

LangMem 的理论基础来自认知科学中的人类记忆三分法，这是一个有坚实学术支撑的框架：

**语义记忆（Semantic Memory）** 在认知科学中定义为"对世界知识和事实的存储，与个人经历无关"。工程实现上，这类记忆以结构化文档或键值对形式存储，检索时强调匹配准确性而非上下文相关性。在 LangMem 中，语义记忆对应 Facts 和 Profile 信息。

**情景记忆（Episodic Memory）** 由 Tulving 提出，定义为"对个人经历事件的时间序列表征"。工程实现上，这类记忆需要时间索引和上下文关联能力，每条记录通常包含时间戳、情境和结果。LangMem 的 Episodes 是其具体对应。

**程序记忆（Procedural Memory）** 定义为"对技能和过程的记忆，知道如何做而非知道是什么"。在 LangMem 中以 Prompt rules 形式编码，直接影响 agent 行为策略。

LangMem 的创新不是发明这套理论，而是将其工程化：通过 MemoryManager 的多步提取管线，LLM 能够从对话中自动区分和提取这三种不同类型的记忆。

---

## 5. Memory Model（记忆模型）

### 5.1 记忆类型

| 类型 | 认知科学定义 | LangMem 实现 | 存储形式 | 典型内容 |
|------|-------------|--------------|----------|----------|
| **Semantic** | 世界知识和事实 | Facts/Profile | BaseStore Collection | "用户偏好 Python"、"系统使用 Vue 3" |
| **Episodic** | 个人经历事件 | Episodes | BaseStore Collection | "agent 在 2026-05-20 修复了认证 bug" |
| **Procedural** | 技能和过程 | Prompt Rules | Prompt fragments | "性能问题先查日志再改代码" |

### 5.2 记忆生命周期

```
对话上下文
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│                  MemoryManager 提取管线                   │
│                                                         │
│  1. Retrieve: 从 BaseStore 拉取已有记忆                  │
│  2. Prepare: 组装已有记忆 + 原始 messages                │
│  3. Multi-Step Extraction (trustcall):                   │
│     ├─ Insert: 添加新记忆                                │
│     ├─ Update: 修改已有记忆                              │
│     └─ Delete: 删除过时记忆                             │
│  4. Filter: 区分 internal/external ID，过滤响应          │
└─────────────────────────────────────────────────────────┘
    │
    ▼
BaseStore（InMemory / PostgreSQL）
```

### 5.3 记忆 Consolidation

LangMem 提供两种记忆形成策略：

| 策略 | 触发时机 | 优点 | 缺点 |
|------|----------|------|------|
| **Hot Path（热路径）** | 对话中实时调用 | 零延迟，记忆立即可用 | 每次操作引入 LLM 调用延迟 |
| **Background（后台）** | 对话结束后异步 | 无延迟影响，可深度分析 | 记忆更新有延迟 |

---

## 6. Architecture Deep Dive（架构深度解析）

### 6.1 整体架构（ASCII）

```
┌─────────────────────────────────────────────────────────────┐
│                       LangMem 架构                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────┐         ┌──────────────────────────────┐   │
│  │   Agent     │────────▶│       MemoryManager          │   │
│  │  (Client)   │         │  ┌────────────────────────┐  │   │
│  └──────────────┘         │  │  Multi-Step Extraction │  │   │
│         │                  │  │  ┌────┐ ┌────┐ ┌────┐  │  │   │
│         │                  │  │  │Step│→│Step│→│Step│  │  │   │
│         ▼                  │  │  │ 1  │ │ 2  │ │ N  │  │  │   │
│  ┌──────────────────────────────────────────────────────┐ │   │
│  │                    BaseStore                          │   │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐           │ │   │
│  │  │InMemory │  │Postgres │  │ Others  │           │ │   │
│  │  └─────────┘  └─────────┘  └─────────┘           │ │   │
│  └──────────────────────────────────────────────────────┘ │   │
│                              │                              │   │
│                              ▼                              │   │
│  ┌──────────────────────────────────────────────────────┐  │   │
│  │            Memory Collections                         │  │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐   │  │   │
│  │  │  Semantic   │ │  Episodic    │ │ Procedural  │   │  │   │
│  │  │  Memory     │ │  Memory      │ │  Memory    │   │  │   │
│  │  │ (Facts)     │ │ (Experiences)│ │ (Skills)   │   │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘   │  │   │
│  └──────────────────────────────────────────────────────┘  │   │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 MemoryManager 提取管线

MemoryManager 采用多步循环从对话上下文提取记忆：

```python
class MemoryManager:
    def run(self, messages, existing_memories):
        # Step 1: Retrieve
        retrieved = self.store.get_all()
        
        # Step 2: Prepare
        context = prepare_prompt(retrieved, messages)
        
        # Step 3: Multi-Step Extraction (trustcall)
        # 三元操作：Insert / Update / Delete
        result = trustcall.extract(
            schema=MemorySchema,
            messages=context,
            max_steps=self.max_steps
        )
        
        # Step 4: Filter
        filtered = filter_internal_ids(result)
        return filtered
```

### 6.3 trustcall 结构化提取

trustcall 是 LangMem 记忆提取的底层驱动库，核心创新是将 Pydantic schema 作为 LLM 输出的约束条件：

```python
from pydantic import BaseModel
from trustcall import extract

class MemorySchema(BaseModel):
    semantic_facts: List[str] = []
    episodic_events: List[Episode] = []
    procedural_rules: List[Rule] = []

# 直接得到类型安全的结构化输出
result = extract(schema=MemorySchema, messages=context)
# result.semantic_facts, result.episodic_events, result.procedural_rules
```

### 6.4 Prompt 优化策略

LangMem 内置三种梯度式 prompt 优化策略：

| 策略 | LLM 调用次数 | 适用场景 |
|------|-------------|---------|
| **prompt_memory** | 1 次 | 快速迭代，对质量要求不极端 |
| **metaprompt** | 2-5 次 | 平衡场景，需要系统性优化 |
| **gradient** | 4-10 次 | 深度优化，分离关注点最彻底 |

### 6.5 BaseStore 存储抽象

LangMem 的核心记忆功能完全基于 LangGraph 的 BaseStore 接口：

```python
# BaseStore 接口定义
class BaseStore(ABC):
    def get(self, key: str) -> Optional[Any]: ...
    def set(self, key: str, value: Any) -> None: ...
    def delete(self, key: str) -> None: ...
    def search(self, query: str) -> List[Any]: ...
```

支持的存储后端：

| 后端 | 适用场景 |
|------|----------|
| **InMemory** | 开发调试、测试、单机部署 |
| **PostgreSQL** | 生产环境、需要持久化 |
| **其他 KV 存储** | 任何实现 BaseStore 接口的存储引擎 |

---

## 7. Design Tradeoffs（设计权衡）

| 选择 | 理由 | 牺牲 |
|------|------|------|
| 三类记忆分类 | 认知科学有坚实理论基础，不同类型确实需要不同处理 | 对简单场景过度设计，增加配置复杂度 |
| Hot/Background 双路径 | 模拟"意识"和"潜意识"的分工，满足不同实时性需求 | 两套管线增加系统复杂度 |
| trustcall 结构化提取 | 格式稳定、类型安全、可测试 | 依赖 Pydantic schema，schema 演进需要迁移 |
| 存储无关设计 | BaseStore 抽象避免供应商锁定，支持灵活切换 | 抽象层带来性能开销 |
| Prompt 优化内置 | 记忆系统效果很大程度上取决于 prompt 质量 | prompt 优化本身需要额外 LLM 调用成本 |
| LangGraph 生态整合 | 降低 LangChain 用户的接入成本 | 对非 LangChain 用户形成壁垒 |
| 无内置知识图谱 | 三种记忆类型都是扁平结构，图不是核心需求 | 复杂关系推理场景需要额外扩展 |

---

## 8. Evidence（基准证据）

### 8.1 Benchmark 数据

| 基准 | 分数 | 来源 | 备注 |
|------|------|------|------|
| **LongMemEval** | 无公开分数 | N/A | 框架较新，未提交到 benchmark |
| **LoCoMo** | 无公开分数 | N/A | 同上 |
| **其他基准** | 无公开分数 | N/A | 主要定位是 LangChain 生态扩展 |

### 8.2 非基准证据

| 指标 | 数值 | 说明 |
|------|------|------|
| GitHub Stars | ~1,464 | 小众但活跃的项目 |
| PyPI 下载量 | 持续增长 | 主要来自 LangChain 社区 |
| LangChain 生态整合 | 深度 | 作为官方 memory 扩展推荐 |
| 第三方集成 | 有限 | 社区较小，扩展生态未成熟 |

**结论**：LangMem 是年轻的框架，目前没有公开的记忆任务 benchmark 分数。选择该框架意味着在一个未经大规模验证的 codebase 上进行投入。

---

## 9. Applicability（适用场景）

| 场景 | 适合度 | 原因 |
|------|--------|------|
| **LangChain 生态内记忆管理** | ★★★★★ | 与 LangGraph BaseStore 无缝集成 |
| **Prompt 优化实验** | ★★★★☆ | 三种梯度式优化策略内置 |
| **对话式 agent 记忆** | ★★★★☆ | 三类记忆自然映射对话场景 |
| **短时摘要压缩** | ★★★☆☆ | 内置 token 感知压缩方案 |
| **生产级记忆系统** | ★★☆☆☆ | v0.0.30 版本，早期生产风险高 |
| **多数据源整合** | ★☆☆☆☆ | 无 ingestion 层，主要处理对话 |
| **知识图谱构建** | ★☆☆☆☆ | 无内置图存储，纯扁平结构 |
| **时间推理查询** | ★☆☆☆☆ | 三种记忆类型均无时态维度 |
| **多 agent 共享记忆** | ★★☆☆☆ | BaseStore 支持，但无明确的多 agent 协调机制 |
| **离线部署** | ★★★☆☆ | 依赖 LangChain，可配置 Ollama 但非重点 |

---

## 10. Maturity（成熟度评估）

| 维度 | 评级 | 说明 |
|------|------|------|
| **API 稳定性** | ★★☆☆☆ | v0.0.30，breaking changes 高风险 |
| **文档完善度** | ★★★★☆ | LangChain 官方文档覆盖完整 |
| **社区活跃度** | ★★★☆☆ | 1.4K stars，社区较小但响应及时 |
| **生产部署案例** | ★★☆☆☆ | 公开案例有限，多为实验性使用 |
| **第三方集成** | ★★☆☆☆ | 生态较小，扩展有限 |
| **维护响应速度** | ★★★☆☆ | LangChain 官方维护，但优先级不明 |
| **测试覆盖** | ★★★☆☆ | 单元测试覆盖核心功能 |
| **企业适配度** | ★★★☆☆ | MIT 许可证，但早期版本企业风险较高 |

**综合评级**：★★☆☆☆（早期阶段，生产使用需谨慎）

---

## 11. AgentCenter Implications（对 AgentCenter 的影响）

### 11.1 可借鉴

1. **三类记忆分类思路**：AgentCenter 可以借鉴语义/情景/程序的分类来组织不同类型的记忆。业务规则（语义）、事故调查（情景）、调试流程（程序）是很自然的映射。

2. **Hot/Background 分离架构**：实时代码补全需要低延迟（热路径），知识库构建可以深度分析（冷路径）。这个分离对 AgentCenter 的混合场景很有价值。

3. **prompt 优化作为一等公民**：大多数框架忽视 prompt 质量，LangMem 将其内置是正确方向。AgentCenter 应该考虑类似的 prompt 优化机制。

4. **BaseStore 存储抽象**：存储无关设计避免供应商锁定，AgentCenter 的记忆系统应该借鉴这一原则。

### 11.2 不适合

1. **作为主记忆系统**：v0.0.30 的版本号意味着 API 随时可能 breaking change。AgentCenter 不能在一个不稳定的 foundation 上构建核心功能。

2. **强时态推理需求**：LangMem 的三种记忆类型都没有时间维度。对于业务规则变更追踪等场景，必须选择其他框架。

3. **多源数据 ingestion**：LangMem 专注于对话记忆，没有数据源连接器。对于 AgentCenter 的文档理解需求，需要额外的 ingestion 层。

### 11.3 迁移建议

**如果 AgentCenter 考虑实验性引入 LangMem**：

```
Phase 1（实验）：
- 在受控环境中评估 v0.0.30 的稳定性
- 测试 BaseStore 与现有存储层的适配成本
- 验证 trustcall 结构化提取的质量

Phase 2（验证）：
- ReflectionExecutor 在大规模历史数据上的性能表现
- 多 agent 并发访问 BaseStore 的安全性
- Hot path 延迟对对话体验的影响

Phase 3（决策）：
- 如果稳定性问题在可接受范围内，考虑作为记忆模块候选
- 否则，等待 1.0 版本发布
```

---

## 12. Comparative Scorecard（对比评分卡）

### vs Mem0

| 维度 | LangMem | Mem0 | 胜出 |
|------|---------|------|------|
| **记忆分类** | 三类（语义/情景/程序） | 层级（短期/长期/上皮） | LangMem |
| **结构化提取** | trustcall Pydantic | spaCy NER | LangMem |
| **Prompt 优化** | 内置三种策略 | 无 | LangMem |
| **存储灵活性** | BaseStore 抽象 | Qdrant + PG 固定 | LangMem |
| **成熟度** | v0.0.30 早期 | 更成熟 | Mem0 |
| **社区规模** | 1.4K stars | 更大 | Mem0 |
| **Benchmark 证据** | 无 | LoCoMo ~49% | Mem0 |

### vs Cognee

| 维度 | LangMem | Cognee | 胜出 |
|------|---------|--------|------|
| **设计定位** | 记忆管理 | Data pipeline | 取决于需求 |
| **数据摄入** | 对话为主 | 30+ 数据源 | Cognee |
| **存储架构** | BaseStore 抽象 | Poly-store | 持平 |
| **知识图谱** | 无 | LLM extraction | Cognee |
| **实时性** | 支持实时（hot path） | Batch 为主 | LangMem |
| **成熟度** | 早期 | 较成熟（12K stars） | Cognee |
| **时态推理** | 无 | 无 | 持平 |

### vs Letta/MemGPT

| 维度 | LangMem | Letta/MemGPT | 胜出 |
|------|---------|--------------|------|
| **架构复杂度** | 轻量库 | 完整运行时环境 | LangMem |
| **记忆分层** | 三类（语义/情景/程序） | 三层（Core/Recall/Archival） | 持平 |
| **部署方式** | 库，可嵌入 | 独立服务 | LangMem |
| **生态整合** | LangChain 深度 | 独立生态 | 取决于用户生态 |
| **生产成熟度** | 早期（v0.0.30） | 更成熟 | Letta |
| **Prompt 优化** | 内置三种策略 | 无 | LangMem |

---

## 13. Open Questions（开放问题）

1. **LangMem v0.0.30 距离 production-ready 有多远？** 目前的版本号暗示大量 API 尚未稳定。在正式采用前，需要与维护者确认 roadmap 和 1.0 目标时间表。

2. **ReflectionExecutor 在大规模历史数据上的性能如何？** 异步深度分析在数据量大时可能面临延迟问题。对于 AgentCenter 可能的长期记忆积累场景，这需要实际压测验证。

3. **BaseStore 在多 agent 并发访问下的安全性如何？** 如果多个 agent 同时读写同一个 BaseStore 实例，是否有事务隔离或并发控制？目前文档中没有明确说明。

4. **三种记忆类型的更新策略在实际任务中的效果如何？** 理论上分类清晰，但 LLM 提取的准确率如何？是否会出现类型误判（如把情景误判为语义）？

5. **LangMem 与 AgentCenter 现有 Vue + Java 技术栈的集成成本有多高？** 需要评估是否需要额外的适配层来桥接 Python LangMem 和 Java Bridge。

---

*报告版本：2026-05-23 | AgentCenter 记忆系统调研组*
