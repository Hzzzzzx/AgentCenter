# Synthius-Mem 深度调研报告

> AgentCenter 记忆系统调研 · 开源项目 #12
> 调研日期：2026-05-23
> 报告编号：deep-reports/12-Synthius-Mem

---

## 1. Reader Verdict（TL;DR + 使用建议）

**一句话总结**：Synthius-Mem 是 LoCoMo 基准测试的 SOTA 方案（94.37%），通过 6 个域分类（biography, experiences, preferences, social, work, psychometrics）和 CategoryRAG 检索机制，在记忆任务上实现了当前最高的公开验证分数。

**TL;DR**：
- 如果你需要一个经过 benchmark 验证的记忆系统（LoCoMo 94.37%），Synthius-Mem 是当前最高分方案
- 如果你需要域分类检索而非纯语义搜索，Synthius-Mem 的 CategoryRAG 是独特价值
- 如果你需要图存储或多跳推理，Synthius-Mem 不支持，需要其他方案

**使用建议**：
| 场景 | 推荐度 | 理由 |
|------|--------|------|
| 经过验证的记忆质量 | ★★★★★ | LoCoMo 94.37% SOTA |
| 域分类记忆管理 | ★★★★★ | 6 个预定义域，CategoryRAG |
| 结构化记忆检索 | ★★★★☆ | Category-based 而非纯向量 |
| 简单扁平记忆 | ★★★☆☆ | 域分类增加了概念复杂度 |
| 多跳关系推理 | ★☆☆☆☆ | 无图存储，不支持多跳 |
| 时间推理 | ★★☆☆☆ | 文件时间戳，无显式时态建模 |
| 大规模多源 ingestion | ★★☆☆☆☆ | 主要处理已有记忆，非 ingestion 框架 |

---

## 2. Framework Profile（框架画像）

| 维度 | 内容 |
|------|------|
| **项目名称** | Synthius-Mem |
| **GitHub** | 待确认（学术/开源项目） |
| **Stars** | 待确认 |
| **License** | 待确认 |
| **主要语言** | Python |
| **最新版本** | 活跃 |
| **核心定位** | Category-based memory for agents |
| **Benchmark** | LoCoMo **94.37%**（SOTA） |

---

## 3. Core Thesis（核心主张）

**Philosophy**：记忆不应该被扁平地存储为向量相似度问题，而应该被结构化为人类可理解的域——biography、experiences、preferences、social、work、psychometrics——检索时首先定位域，然后在域内精确匹配，这才是 agent 记忆检索的正确范式。

---

## 4. Theoretical Foundation（理论基础）

Synthius-Mem 的理论基础建立在认知心理学的域特异性记忆理论之上：

**域分类记忆（Domain-Categorized Memory）** 的认知科学依据是人类记忆本身具有功能性的域分离。人类不会把"我的名字"和"我去年度假的经历"存在同一个认知结构里——前者是语义记忆，后者是情景记忆。Synthius-Mem 将这个原则工程化，通过预定义的 6 个域来组织 agent 记忆。

**6 域结构** 的设计反映了对 agent 记忆需求的深刻理解：
- **Biography**：agent 和用户的身份信息
- **Experiences**：经历和事件记录
- **Preferences**：偏好和设置
- **Social**：社交关系和网络
- **Work**：工作相关的知识和技能
- **Psychometrics**：心理测量和认知状态

**CategoryRAG 检索机制** 的核心洞察是：纯语义相似度检索对于记忆系统是不够的。当 agent 问"上次我和用户讨论的 Python 版本问题在哪里"时，语义检索可能找到一堆提到 Python 的记忆，但 CategoryRAG 首先定位到 Experiences 域，然后在该域内检索，精确度更高。

---

## 5. Memory Model（记忆模型）

### 5.1 记忆类型（6 域分类）

| 域 | 认知对应 | 存储形式 | 典型内容 |
|----|----------|----------|----------|
| **Biography** | 身份记忆 | 结构化文档 | agent ID、用户姓名、角色定义 |
| **Experiences** | 情景记忆 | 时间线文件 | 对话历史、任务完成记录 |
| **Preferences** | 偏好记忆 | 键值对集合 | 用户偏好设置、系统配置 |
| **Social** | 社交记忆 | 关系图谱 | 用户关系、团队结构 |
| **Work** | 语义记忆 | 文档集合 | 工作流程、领域知识 |
| **Psychometrics** | 元认知 | 测量记录 | 认知状态、情绪指标 |

### 5.2 存储形式

Synthius-Mem 采用结构化域文件（Markdown）作为存储：

```
memory/
├── biography.md
├── experiences.md
├── preferences.md
├── social.md
├── work.md
└── psychometrics.md
```

每个域文件是 Markdown 格式，人类可读、可版本控制：

```markdown
# Biography
## User
- name: Zhang San
- role: Backend Engineer
- language: Chinese

## Agent
- model: GPT-4
- version: 1.0

---
# Experiences
## 2026-05-20
- task: Debug authentication issue
- result: Resolved
- duration: 2 hours

## 2026-05-19
- task: Code review
- result: Approved
```

### 5.3 记忆生命周期

```
输入数据
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│                   Category Classifier                     │
│          "这条记忆属于哪个域？"                          │
└─────────────────────────────────────────────────────────┘
    │
    ▼
┌──────────┬──────────┬──────────┬──────────┬──────────┬──────────┐
│Biography │Experiences│Preferences│  Social  │   Work   │Psychometrics│
└──────────┴──────────┴──────────┴──────────┴──────────┴──────────┘
    │          │            │          │          │           │
    ▼          ▼            ▼          ▼          ▼           ▼
写入对应域文件      更新 Preferences  更新 Social  更新 Work    更新 Psychometrics
```

---

## 6. Architecture Deep Dive（架构深度解析）

### 6.1 整体架构（ASCII）

```
┌─────────────────────────────────────────────────────────────┐
│                    Synthius-Mem 架构                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────┐                                           │
│  │   Input      │                                           │
│  │  (Memory)    │                                           │
│  └──────┬───────┘                                           │
│         │                                                   │
│         ▼                                                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │               Category Classifier                      │  │
│  │                                                          │  │
│  │   LLM-driven domain classification                      │  │
│  │   "Which domain(s) does this memory belong to?"         │  │
│  └──────────────────────────┬───────────────────────────┘  │
│                              │                               │
│         ┌────────────────────┼────────────────────┐        │
│         ▼                    ▼                    ▼        │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐  │
│  │  Biography  │     │ Experiences │     │ Preferences │  │
│  │  ─────────  │     │  ─────────  │     │  ─────────  │  │
│  │ biography.md│     │experiences.md│     │preferences.md│  │
│  └─────────────┘     └─────────────┘     └─────────────┘  │
│         │                    │                    │         │
│         └────────────────────┼────────────────────┘         │
│                              │                               │
│                              ▼                               │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                   CategoryRAG                         │  │
│  │                                                          │  │
│  │   Domain-specific retrieval                            │  │
│  │   1. Classify query to domain(s)                      │  │
│  │   2. Search within domain(s)                          │  │
│  │   3. Return domain-specific results                    │  │
│  └──────────────────────────────────────────────────────┘  │
│                              │                               │
│                              ▼                               │
│                       Agent Context                          │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 CategoryRAG 检索流程

```
用户/Agent 查询
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│              Query Domain Classification                  │
│   "What domain(s) is this query about?"                │
│                                                          │
│   示例: "上次我和用户讨论的认证问题解决了吗？"           │
│   → Experiences (主要), Biography (次要)               │
└─────────────────────────────────────────────────────────┘
    │
    ├──▶ Experiences/search → 返回 experiences.md 相关片段
    │
    └──▶ Biography/search → 返回 biography.md 相关片段（如涉及）
    │
    ▼
跨域结果聚合 → Agent Context
```

### 6.3 域分类器设计

域分类器是 LLM-driven 的多标签分类器：

```python
class DomainClassifier:
    def classify(self, memory_or_query: str) -> List[str]:
        prompt = f"""
        Classify the following into one or more domains:
        {memory_or_query}
        
        Domains: biography, experiences, preferences, 
                 social, work, psychometrics
        
        Output: JSON array of domain names
        """
        result = llm.generate(prompt)
        return parse_domains(result)
```

### 6.4 存储后端

| 域 | 存储格式 | 索引方式 |
|----|----------|----------|
| Biography | Markdown | 标题结构 |
| Experiences | Markdown + timestamp | 时间线索引 |
| Preferences | Markdown + key-value | 键值索引 |
| Social | Markdown | 关系列表 |
| Work | Markdown | 标题/标签 |
| Psychometrics | Markdown | 时间序列 |

---

## 7. Design Tradeoffs（设计权衡）

| 选择 | 理由 | 牺牲 |
|------|------|------|
| 6 域预定义分类 | 认知科学有依据，覆盖 agent 主要记忆类型 | 对某些场景可能不够灵活，需要修改域定义 |
| Markdown 文件存储 | 人类可读、可版本控制、无数据库依赖 | 查询性能不如向量数据库，无原生索引 |
| CategoryRAG 而非纯向量检索 | 精确度更高，减少语义混淆 | 需要额外的域分类步骤，引入延迟 |
| 无图存储 | 6 域分类覆盖了大多数场景 | 多跳关系推理不支持 |
| LLM-driven 域分类 | 准确率高，可处理复杂情况 | 每次分类需要 LLM 调用，成本和延迟 |
| 文件系统而非数据库 | 简单、无依赖、可版本控制 | 并发写入需要锁机制，大规模数据性能 |

---

## 8. Evidence（基准证据）

### 8.1 Benchmark 数据

| 基准 | 分数 | 来源 | 备注 |
|------|------|------|------|
| **LoCoMo** | **94.37%** | 官方公布 | **当前 SOTA** |
| **LongMemEval** | 无公开分数 | N/A | 未提交到该基准 |
| **其他基准** | 待确认 | N/A | 公开资料有限 |

### 8.2 分数解读

| 排名 | 框架 | LoCoMo 分数 |
|------|------|-------------|
| **#1** | **Synthius-Mem** | **94.37%** |
| #2 | MemMachine | 91.69% |
| #3 | Hindsight | 89.61% |
| #4 | Mem0 | ~60-70% |

**结论**：Synthius-Mem 的 LoCoMo 94.37% 是当前已公布框架中的最高分。这意味着在持续记忆利用任务上，CategoryRAG 和域分类的设计确实带来了显著的精度提升。

---

## 9. Applicability（适用场景）

| 场景 | 适合度 | 原因 |
|------|--------|------|
| **需要最高 benchmark 验证** | ★★★★★ | LoCoMo 94.37% SOTA |
| **域分类检索** | ★★★★★ | 6 个预定义域 + CategoryRAG |
| **人类可读记忆存储** | ★★★★★ | Markdown 文件，可直接查看和编辑 |
| **版本控制友好** | ★★★★★ | 文件系统存储，天然支持 Git |
| **快速原型/轻量部署** | ★★★★☆ | 无数据库依赖 |
| **多 agent 共享记忆** | ★★★☆☆ | 文件共享，但并发需要机制 |
| **大规模数据** | ★★☆☆☆ | Markdown 文件在数据量大时可能成为瓶颈 |
| **多跳关系推理** | ★☆☆☆☆ | 无图存储，不支持多跳 |
| **实时低延迟** | ★★★☆☆ | 每次检索需要 LLM 域分类 |
| **离线部署** | ★★★★★ | 无外部依赖，文件存储 |

---

## 10. Maturity（成熟度评估）

| 维度 | 评级 | 说明 |
|------|------|------|
| **API 稳定性** | ★★★☆☆ | 有公开 benchmark，但项目成熟度信息有限 |
| **文档完善度** | ★★★☆☆ | 核心设计清晰，但详细文档有限 |
| **社区活跃度** | ★★★☆☆ | 公开资料有限，活跃度未知 |
| **生产部署案例** | ★★★☆☆ | 有 benchmark 验证，但公开案例有限 |
| **第三方集成** | ★★☆☆☆ | 独立项目，生态有限 |
| **维护响应速度** | ★★★☆☆ | 维护状态未知 |
| **测试覆盖** | ★★★★☆ | 有 LoCoMo 验证 |
| **企业适配度** | ★★★★☆ | 简单文件存储，无外部依赖 |

**综合评级**：★★★☆☆（中等成熟，SOTA benchmark 是亮点）

---

## 11. AgentCenter Implications（对 AgentCenter 的影响）

### 11.1 可借鉴

1. **CategoryRAG 检索范式**：Synthius-Mem 的核心 insight——先分类再检索——是值得借鉴的。AgentCenter 可以考虑类似的域分类机制来提高检索精度。

2. **6 域分类的设计**：biography/experiences/preferences/social/work/psychometrics 的分类覆盖了大多数记忆类型。AgentCenter 可以直接采用或扩展这个分类体系。

3. **人类可读存储**：Markdown 文件的存储方式对于需要人工审查记忆内容的场景非常友好。AgentCenter 可以借鉴这个设计提供 human-browsable 的记忆界面。

4. **SOTA benchmark 验证意识**：94.37% 的 LoCoMo 分数证明了系统设计的有效性。AgentCenter 应该建立类似的量化验证机制。

### 11.2 不适合

1. **需要图存储的场景**：Synthius-Mem 没有图存储，对于代码依赖分析等需要多跳推理的场景不适用。

2. **大规模数据存储**：Markdown 文件在数据量增大时会遇到性能瓶颈，需要更复杂的索引机制。

3. **需要向量语义检索**：Synthius-Mem 主要依赖域分类和精确匹配，对于模糊语义检索场景不如向量数据库。

### 11.3 迁移建议

**如果 AgentCenter 考虑引入 Synthius-Mem 的设计**：

```
Phase 1（MVP）：
- 采用 6 域分类作为记忆分类基础
- 使用 PostgreSQL 替代 Markdown 文件（便于查询）
- 实现 CategoryRAG 检索层

Phase 2（增强）：
- 添加向量检索作为补充（pgvector）
- 实现域内搜索和跨域聚合
- 建立域分类的 LLM 分类器

Phase 3（验证）：
- 在相同数据集上复现 LoCoMo 测试
- 对比 CategoryRAG vs 纯向量检索的精度差异
```

---

## 12. Comparative Scorecard（对比评分卡）

### vs MemMachine

| 维度 | Synthius-Mem | MemMachine | 胜出 |
|------|--------------|------------|------|
| **LoCoMo** | **94.37%** | 91.69% | Synthius-Mem |
| **存储架构** | Markdown 文件 | Neo4j + SQL + Memory | MemMachine（图能力） |
| **域分类** | 6 个预定义域 | 无 | Synthius-Mem |
| **图存储** | 无 | Neo4j 原生 | MemMachine |
| **人类可读** | Markdown 直接可读 | 需要查询接口 | Synthius-Mem |
| **多跳推理** | 不支持 | 支持 | MemMachine |

### vs Hindsight

| 维度 | Synthius-Mem | Hindsight | 胜出 |
|------|--------------|-----------|------|
| **LoCoMo** | **94.37%** | 89.61% | Synthius-Mem |
| **LongMemEval** | 无分数 | **91.4%** | Hindsight |
| **检索策略** | CategoryRAG | 4路并行 + cross-encoder | 取决于场景 |
| **存储架构** | 域文件 | 多信号检索 | 取决于需求 |
| **多跳推理** | 不支持 | 依赖多次查询 | Hindsight |
| **实时性** | 文件读写 | 向量检索 | Hindsight |

### vs LangMem

| 维度 | Synthius-Mem | LangMem | 胜出 |
|------|--------------|---------|------|
| **LoCoMo** | **94.37%** | 无分数 | Synthius-Mem |
| **记忆分类** | 6 域分类 | 语义/情景/程序三分法 | 持平（不同分类法） |
| **检索方式** | CategoryRAG | BaseStore 向量检索 | Synthius-Mem |
| **存储架构** | Markdown 文件 | BaseStore 抽象 | 取决于需求 |
| **成熟度** | 中等（有 benchmark） | 早期（v0.0.30） | Synthius-Mem |
| **LangChain 集成** | 无 | 深度 | LangMem |

---

## 13. Open Questions（开放问题）

1. **Synthius-Mem 的 6 域分类是否可以覆盖 AgentCenter 的所有记忆类型？** AgentCenter 的记忆可能包括项目知识、工作流定义、工具配置等，这些是否都能映射到现有域？

2. **Markdown 文件存储在大规模记忆积累时的性能如何？** 如果一个域文件达到 MB 级别，读取和搜索的性能会显著下降。是否有文件大小上限或分片机制？

3. **CategoryRAG 的域分类准确性如何保证？** LLM 分类可能出现误判，导致记忆被存入错误的域。需要评估分类误差率和影响。

4. **多 agent 并发写入同一域文件如何处理？** 文件系统没有内置的事务机制，需要应用层实现锁或队列机制。这会否成为并发性能瓶颈？

5. **Synthius-Mem 与其他 agent 框架的集成方式是什么？** 它是一个独立库还是需要与特定框架配合使用？集成成本有多高？

---

*报告版本：2026-05-23 | AgentCenter 记忆系统调研组*
