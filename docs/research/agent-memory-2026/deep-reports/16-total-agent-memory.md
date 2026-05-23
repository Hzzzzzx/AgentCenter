# total-agent-memory 深度研究报告

> AgentCenter 记忆系统调研 · 框架专项
> 调研日期：2026-05-23
> 框架版本：latest (SQLite + FTS5 + Chroma + FastEmbed)
> LongMemEval 排名：#1 (96.2%，与 agentmemory 并列)

---

## 1. Reader Verdict

**一句话总结**：total-agent-memory 证明了「在 SQLite 里也能搭时序知识图谱」——无需 Neo4j 或专业图数据库，用纯 SQL 的 fact_assertions 表和 kg_at() 函数就能回答「在任何时间点什么为真」。96.2% 的 LongMemEval 成绩与 agentmemory 并列第一，加上 6 路检索融合和 4 种运行时模式，是目前功能最完整的轻量记忆框架。

**核心判断**：如果你同时需要**高质量检索**和**时态推理**，total-agent-memory 是唯一不需要外部图数据库的选择。它的 22 种节点类型覆盖了常见实体，fact_assertions 表的 bi-temporal 设计解决了「规则什么时候变」的问题。代价是技术栈比 agentmemory 复杂（多了 Chroma），且时态推理的正确性依赖于 kg_at() 函数实现的质量。

**适合读者**：需要时态推理能力、但不想运维 Neo4j 的团队。偏好 SQLite-first 技术栈、且愿意接受一定工程复杂度来换取完整功能的企业场景。

---

## 2. Framework Profile

| 维度 | 详情 |
|------|------|
| **框架名称** | total-agent-memory |
| **核心定位** | SQLite-first 时序知识图谱 + 多路检索融合 |
| **技术栈** | Python + SQLite + FTS5 + Chroma + FastEmbed |
| **LongMemEval** | 96.2% (#1 Ranked, 与 agentmemory 并列) |
| **Benchmark 数据来源** | 框架官方/论文 |
| **存储介质** | SQLite + FTS5 + Chroma 向量索引 |
| **向量维度** | 可配置 (FastEmbed) |
| **节点类型** | 22 种 (person, organization, concept, event, location, technology...) |
| **检索信号数** | 6 路 (BM25 + Dense + Fuzzy + Graph + CrossEncoder + MMR) |
| **融合方式** | RRF (Reciprocal Rank Fusion) |
| **运行时模式** | 4 种 (ultrafast/fast/balanced/deep) |
| **许可证** | 开源 |
| **维护状态** | 活跃 |
| **适合场景** | 时态推理 + 高质量检索、轻量部署、无需图数据库 |

---

## 3. Core Thesis

**用纯 SQL 也能搭时序知识图谱——fact_assertions 表的 valid_from/valid_to/superseded_by 三字段让「任何时间点查询」成为可能，无需运维独立的图数据库。**

---

## 4. Theoretical Foundation

### 4.1 时序知识图谱的核心思想

传统的知识图谱回答「X 和 Y 是什么关系」。时序知识图谱回答「在时间 T，X 和 Y 是什么关系」——这是一个更复杂但更符合现实世界的模型。

现实中的事实几乎都有时效：

- 「张三是项目经理」——在 2024 年之前是真的，2024 年之后李四接手了
- 「这个 API 用 Basic Auth」——在 2025-06 之前是真的，之后改成了 OAuth
- 「我们用 PostgreSQL」——在某个版本之前是真的，之后迁移到了新数据库

如果用传统图谱表示，这些矛盾的事实会冲突。用时序图谱表示，它们都是「在某个时间段内为真」的正确描述。

### 4.2 bi-temporal 模型 vs 单时态模型

| 模型 | 字段 | 能回答的问题 |
|------|------|-------------|
| **无时态** | 无时间字段 | 「X 是什么」 |
| **单时态** | valid_at | 「X 在什么时候是真的」 |
| **bi-temporal** | valid_at + invalid_at + system_at | 「X 在什么时候是真的 + 我们什么时候知道的」 |

total-agent-memory 和 Zep 都使用 bi-temporal 模型，区别在于：

- **Zep/Graphiti**：需要 Neo4j，图遍历能力强
- **total-agent-memory**：纯 SQLite，用 SQL 查询实现 kg_at()

### 4.3 kg_at() 函数

kg_at() 是 total-agent-memory 的核心创新——用 SQL 实现时序查询：

```sql
-- 查找在时间 T 为真的所有事实
SELECT n.name, f.predicate, o.name
FROM fact_assertions f
JOIN graph_nodes n ON f.subject_id = n.id
JOIN graph_nodes o ON f.object_id = o.id
WHERE f.valid_from <= :target_time
  AND (f.valid_to IS NULL OR f.valid_to > :target_time)
  AND f.superseded_by IS NULL;  -- 未被替代
```

这个查询可以扩展为更复杂的时序逻辑：

- 「在 T1 到 T2 之间，哪些事实是连续的」
- 「哪些事实被替代了，替代关系是什么」
- 「给定一个时间线，重建当时的事实状态」

### 4.4 6 路检索与 RRF 融合

total-agent-memory 的检索管线与 agentmemory 类似，但有两点不同：

1. **信号更多**：BM25 + Dense(向量) + Fuzzy + Graph + CrossEncoder + MMR（最大边际相关性）
2. **融合方式**：RRF (Reciprocal Rank Fusion) 替代简单加权融合

RRF 的优点是**排名稳健**——不同信号可能给出不同的 top-k 列表，RRF 通过倒数排名融合避免某一路信号主导结果：

```
RRF_score(doc) = Σ 1/(k + rank_i(doc))
-- k 通常为 60
```

---

## 5. Memory Model

### 5.1 节点类型 (22 种)

| 类型 | 说明 | 示例 |
|------|------|------|
| person | 人物 | 张三、项目经理 |
| organization | 组织 | 研发部、财务部 |
| concept | 概念 | 微服务、缓存策略 |
| event | 事件 | 2024技术选型、数据库迁移 |
| location | 地点 | 北京办公室、华东机房 |
| technology | 技术/工具 | PostgreSQL、Kubernetes |
| document | 文档 | PRD、API文档 |
| project | 项目 | AgentCenter、电商平台 |
| task | 任务 | 登录重构、支付集成 |
| decision | 决策 | 采用微服务、选择PostgreSQL |
| requirement | 需求 | 支持多租户、SSO认证 |
| code | 代码实体 | UserService、login() |
| api | API端点 | /api/users、POST /orders |
| policy | 政策/规则 | 审批流程、限流策略 |
| metric | 指标 | QPS、响应时间 |
| vulnerability | 漏洞 | SQL注入、XSS |
| test | 测试 | 单元测试、E2E |
| deployment | 部署 | K8s、灰度发布 |
| team | 团队 | 前端组、后端组 |
| skill | 技能 | Vue3、Java |
| resource | 资源 | GPU、存储 |
| concept_relation | 概念关系 | 继承、实现、依赖 |

### 5.2 生命周期

```
[输入文本]
     ↓
[实体识别] → 22种节点类型之一
     ↓
[消歧/合并] → 已有节点 or 新建节点
     ↓
[事实断言] → valid_from/valid_to/superseded_by
     ↓
[向量编码] → FastEmbed
     ↓
[Chroma HNSW 索引]
     ↓
[6路检索参与排名]
```

### 5.3 记忆巩固 (Consolidation)

total-agent-memory 有两种巩固机制：

1. **自动消歧**：新识别实体与已有实体做模糊匹配，决定是合并还是新建
2. **事实替代**：当新事实与旧事实矛盾时，通过 superseded_by 字段建立替代关系，保留完整历史

这比 agentmemory 的「仅写入」模型更完整，但需要外部触发（LLM 判断新事实是否与旧矛盾）。

---

## 6. Architecture Deep Dive

### 6.1 核心组件

```
┌─────────────────────────────────────────────────────────────┐
│                  total-agent-memory 架构                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │  Text Input  │    │  Entity      │    │   Fact       │  │
│  │  (Raw)       │───▶│  Extraction  │───▶│   Assertion  │  │
│  │              │    │  (22 types)  │    │   (temporal) │  │
│  └──────────────┘    └──────────────┘    └──────┬───────┘  │
│                                                   │          │
│         ┌───────────────────────────────────────┘          │
│         ↓                                                      │
│  ┌──────────────────────────────────────────────────────┐    │
│  │                    SQLite Storage                      │    │
│  │  ┌────────────────┐    ┌────────────────────────┐   │    │
│  │  │  graph_nodes   │    │    fact_assertions      │   │    │
│  │  │  (22 types)    │    │ (valid_from/valid_to/  │   │    │
│  │  │                │    │  superseded_by)          │   │    │
│  │  └────────────────┘    └────────────────────────┘   │    │
│  └──────────────────────────────────────────────────────┘    │
│                              │                               │
│         ┌────────────────────┼────────────────────┐        │
│         ↓                    ↓                    ↓        │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │    FTS5      │    │   Chroma     │    │   Graph      │  │
│  │   (BM25)     │    │  (Dense)     │    │  (Traverse)  │  │
│  └──────────────┘    └──────────────┘    └──────────────┘  │
│         │                    │                    │          │
│         └────────────────────┼────────────────────┘          │
│                              ↓                               │
│                   ┌──────────────────────┐                   │
│                   │   6-Signal RRF      │                   │
│                   │      Fusion           │                   │
│                   │                      │                   │
│                   │ BM25 + Dense + Fuzzy │                   │
│                   │ + Graph + CrossEnc   │                   │
│                   │ + MMR                │                   │
│                   └──────────┬───────────┘                   │
│                              ↓                               │
│                   ┌──────────────────────┐                   │
│                   │    kg_at() Temporal  │                   │
│                   │       Query          │                   │
│                   └──────────────────────┘                   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 6.2 数据流

**写入流程**：

```
1. 原始文本输入
2. LLM 实体识别 → 22种节点类型之一
3. 模糊匹配已有节点 → 决定 merge or create
4. 新事实写入 fact_assertions
   - valid_from = CURRENT_TIMESTAMP
   - valid_to = NULL
   - superseded_by = NULL
5. 如果与旧事实矛盾：
   - 找到旧事实
   - 设置旧事实 superseded_by = 新事实ID
   - 设置旧事实 valid_to = CURRENT_TIMESTAMP
6. FastEmbed 向量编码 → Chroma 索引
```

**检索流程**：

```
1. query 输入
2. 4路并行计算:
   - FTS5 BM25 → lexical
   - Chroma Dense → semantic
   - 模糊匹配 → fuzzy
   - 图遍历 → graph
3. Cross-encoder 重排
4. MMR 多样性保证
5. RRF 融合 → final ranking
6. kg_at() 可选时序过滤 → temporal-aware results
```

### 6.3 存储层 (SQL Schema)

```sql
-- 核心图节点
CREATE TABLE graph_nodes (
    id              TEXT PRIMARY KEY,              -- ULID
    type            TEXT NOT NULL,                  -- 22种节点类型
    name            TEXT,                          -- 节点名称
    content         TEXT,                          -- 节点内容摘要
    properties      JSON DEFAULT '{}',             -- 额外属性
    importance      REAL DEFAULT 0.0,               -- 重要性 0.0-1.0
    first_seen_at   TIMESTAMP,                      -- 首次出现
    last_seen_at   TIMESTAMP,                      -- 最近出现
    mention_count   INTEGER DEFAULT 0               -- 被提及次数
);

-- 时序事实断言
CREATE TABLE fact_assertions (
    id              TEXT PRIMARY KEY,               -- ULID
    subject_id      TEXT REFERENCES graph_nodes(id),-- 主语节点
    predicate       TEXT NOT NULL,                  -- 谓词/关系
    object_id       TEXT REFERENCES graph_nodes(id),-- 宾语节点
    valid_from      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,  -- 生效时间
    valid_to        TIMESTAMP,                       -- 失效时间 (NULL=永久有效)
    superseded_by   TEXT REFERENCES fact_assertions(id)  -- 被谁替代
);

-- 向量缓存 (Chroma 用)
CREATE TABLE embedding_cache (
    node_id         TEXT PRIMARY KEY REFERENCES graph_nodes(id),
    embedding       BLOB,                           -- float32 packed
    model           TEXT,                           -- FastEmbed 模型名
    created_at      TIMESTAMP
);

-- FTS5 全文索引
CREATE VIRTUAL TABLE nodes_fts USING fts5(
    name,
    content,
    content=graph_nodes,
    content_rowid=id
);

-- 索引
CREATE INDEX idx_fact_subject ON fact_assertions(subject_id);
CREATE INDEX idx_fact_object ON fact_assertions(object_id);
CREATE INDEX idx_fact_valid_from ON fact_assertions(valid_from);
CREATE INDEX idx_fact_superseded ON fact_assertions(superseded_by);
```

### 6.4 kg_at() 函数 (时序查询)

```sql
-- 查找在指定时间点为真的所有事实
CREATE FUNCTION kg_at(target_time TIMESTAMP) RETURNS TABLE(
    fact_id TEXT,
    subject_name TEXT,
    predicate TEXT,
    object_name TEXT,
    valid_from TIMESTAMP,
    valid_to TIMESTAMP
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        f.id,
        s.name,
        f.predicate,
        o.name,
        f.valid_from,
        f.valid_to
    FROM fact_assertions f
    JOIN graph_nodes s ON f.subject_id = s.id
    JOIN graph_nodes o ON f.object_id = o.id
    WHERE f.valid_from <= target_time
      AND (f.valid_to IS NULL OR f.valid_to > target_time)
      AND f.superseded_by IS NULL
    ORDER BY f.valid_from DESC;
END;
$$ LANGUAGE plpgsql;
```

---

## 7. Design Tradeoffs

| 选择 | 理由 | 牺牲 |
|------|------|------|
| **SQLite-first** | 无需外部图数据库，运维简单 | 图遍历性能不如专业图数据库（Neo4j） |
| **22 种节点类型** | 覆盖大多数实体类型，消歧有据可依 | 类型体系是封闭的，扩展需要改 schema |
| **RRF 融合** | 排名稳健，避免单信号主导 | 权重不直观，调优比加权融合更难理解 |
| **4 种运行时模式** | ultrafast/deep 适应不同性能需求 | 参数组合多，配置复杂度高 |
| **bi-temporal 设计** | 精确回答「什么时候真的」 | 实现复杂度高，bug 可能导致时序逻辑错误 |
| **Chroma 引入** | 专业的向量检索，比纯 Python HNSW 快 | 多了外部依赖（Chroma） |
| **无 LangChain/LlamaIndex** | 保持独立，不绑定生态 | 集成成本高，需要自己实现接口层 |

---

## 8. Evidence

### 8.1 Benchmark 成绩

| 基准 | 分数 | 排名 | 说明 |
|------|------|------|------|
| **LongMemEval** | **96.2%** | **#1 (并列)** | 与 agentmemory 并列第一 |
| (对比基线) | ~39% | — | 无记忆的全上下文 LLM |
| (对比 Hindsight) | 91.4% | #2 | 此前的 SOTA |
| (对比 Mem0) | ~49% | — | 差距达 47 个百分点 |

### 8.2 分数解读

96.2% 的成绩与 agentmemory 并列第一，但背后的技术路径完全不同：

- **agentmemory**：确定性 HNSW + 6 信号加权融合
- **total-agent-memory**：时序知识图谱 + 6 路 RRF 融合

这说明**两条技术路线都能达到同样的检索质量**，但功能维度不同：

- agentmemory 更适合「检索质量优先、无时态需求」的场景
- total-agent-memory 更适合「同时需要时态推理和检索质量」的场景

### 8.3 关键证据：SQLite 时序知识图谱的可行性

total-agent-memory 证明了不需要 Neo4j 也能实现时序知识图谱的核心功能：

- fact_assertions 表的 3 个时间字段（valid_from, valid_to, superseded_by）
- kg_at() 函数实现「在 T 时刻为真的事实」查询
- 图遍历通过 SQL JOIN 实现

这比 Neo4j 的优势是**运维简单**——一个 SQLite 文件搞定一切，不需要图数据库集群。代价是**图遍历性能**不如 Neo4j（深度遍历时需要多次 JOIN）。

---

## 9. Applicability

| 场景 | 适合度 | 原因 |
|------|--------|------|
| **时态推理需求** | ⭐⭐⭐⭐⭐ | bi-temporal 设计，kg_at() 可回答「任何时间点状态」 |
| **高质量记忆检索** | ⭐⭐⭐⭐⭐ | 6 路 RRF 融合，LongMemEval 96.2% |
| **轻量部署** | ⭐⭐⭐⭐ | SQLite，无需图数据库集群 |
| **企业级知识图谱** | ⭐⭐⭐⭐ | 22 种节点类型，覆盖大多数实体 |
| **关系推理/多跳遍历** | ⭐⭐⭐ | SQL JOIN 可用，深度遍历性能不如 Neo4j |
| **无外部依赖** | ⭐⭐⭐ | 需 Chroma，有外部依赖 |
| **企业级复杂记忆** | ⭐⭐⭐⭐ | 功能完整，但缺少多租户、权限控制 |
| **需要频繁更新的业务规则** | ⭐⭐⭐⭐ | superseded_by 机制支持事实替代 |

---

## 10. Maturity

| 维度 | 评分 | 说明 |
|------|------|------|
| **代码成熟度** | 3.5/5 | 功能完整，但缺少生产级容错和边界处理 |
| **文档完整度** | 3/5 | 核心用法有文档，架构设计文档较少 |
| **社区活跃度** | 3/5 | GitHub stars 适中，维护状态一般 |
| **生产部署案例** | 2/5 | 无公开的大型生产部署记录 |
| **企业特性** | 2.5/5 | 缺少多租户、权限控制、审计日志 |
| **维护持续性** | 3/5 | 活跃维护中，但版本发布不规律 |
| **生态集成** | 2/5 | 无官方 LangChain/LlamaIndex 集成 |
| **基准验证** | 5/5 | LongMemEval #1，有公开可验证数据 |

**综合成熟度**：3.0/5

**定位**：功能最完整的轻量框架，但工程化程度仍有提升空间。时态推理 + 高质量检索的组合在同类中独特。

---

## 11. AgentCenter Implications

### 可借鉴

| 借鉴点 | 说明 | 迁移建议 |
|--------|------|---------|
| **fact_assertions 表设计** | bi-temporal 是时态推理的最小实现，值得直接移植 | 在 AgentCenter SQLite 中创建 fact_assertions 表 |
| **kg_at() 函数** | 用 SQL 实现时序查询，无需外部图数据库 | 移植到 AgentCenter 作为 temporal query API |
| **22 种节点类型** | 覆盖大多数企业实体，可作为 AgentCenter 实体类型的参考 | 适配 AgentCenter 的 8+3 对象模型 |
| **6 路 RRF 融合** | 比简单加权更稳健的融合策略 | 替换现有单一向量检索为 RRF 融合 |
| **4 种运行时模式** | ultrafast/deep 适应不同场景 | 按需引入，平衡延迟和精度 |

### 不适合

| 不适合场景 | 原因 |
|-----------|------|
| **纯检索质量优先** | agentmemory 的确定性 HNSW 更适合（无时态需求时） |
| **需要深度图遍历** | SQLite JOIN 不适合大深度遍历（>5跳） |
| **零外部依赖** | 需要 Chroma，不是纯 SQLite |

### 迁移建议

1. **短期**：将 fact_assertions 表和 kg_at() 函数移植到 AgentCenter，作为时态推理的基础设施
2. **中期**：参考 22 种节点类型扩展 AgentCenter 的实体模型（当前 8+3）
3. **长期**：评估是否需要引入 Chroma 或保持纯 SQLite FTS5——取决于向量检索的性能需求

---

## 12. Comparative Scorecard

| 能力维度 | total-agent-memory | agentmemory | Hindsight | Zep/Graphiti | Mem0 |
|----------|-------------------|-------------|-----------|--------------|------|
| **LongMemEval** | **96.2%** #1 | **96.2%** #1 | 91.4% #2 | ~63.8% | ~49% |
| **时态推理** | ★★★★★ | ★ | ★★ | ★★★★★ | ★ |
| **知识图谱** | ★★★★ | ★ | ★★ | ★★★★★ | ★ |
| **检索质量** | ★★★★★ | ★★★★★ | ★★★★★ | ★★★ | ★★★ |
| **确定性/可审计** | ★★★★ | **★★★★★** | ★★ | ★★★★ | ★★ |
| **部署简洁性** | ★★★★ | **★★★★★** | ★★★★ | ★★ | ★★★ |
| **多信号融合** | ★★★★★ | ★★★★★ | ★★★★★ | ★★★★ | ★★★ |
| **无外部依赖** | ★★★ | **★★★★★** | ★★★★ | ★★ | ★★★ |
| **企业特性** | ★★ | ★★ | ★★ | ★★★ | ★★ |

**关键对比洞察**：

- **total-agent-memory vs agentmemory**：检索质量相同（并列第一），但 total-agent-memory 有时态推理能力，agentmemory 部署更简洁
- **total-agent-memory vs Zep/Graphiti**：功能类似（都有时态推理），但 total-agent-memory 只需 SQLite，Zep 需要 Neo4j
- **两条第一路线**：agentmemory（确定性 HNSW）和 total-agent-memory（时序 KG）都能达到 96.2%，说明检索质量的天花板不在于存储形式，而在于信号融合策略

---

## 13. Open Questions

### 问题 1：kg_at() 函数的实现质量如何保证？

kg_at() 是纯 SQL 实现的时序查询，在简单场景下正确，但**复杂时序逻辑（如跨时间段连续性、区间重叠）可能有问题**。例如：

- 「哪些事实在 T1 到 T2 之间**持续**有效？」
- 「哪些事实被部分重叠的新事实替代了？」

**需要验证**：在复杂时序查询场景下 kg_at() 的正确性和性能。

### 问题 2：22 种节点类型是否足够？

total-agent-memory 的节点类型是预设的封闭集合。AgentCenter 的实体类型（8+3 对象模型）与它不完全重叠：

- AgentCenter：WorkItem, Session, Workflow, Artifact, RuntimeEvent, ConfirmationRequest, AgentMessage...
- total-agent-memory：person, organization, concept, event...

**需要评估**：是否需要扩展节点类型，或设计更灵活的开放类型体系。

### 问题 3：RRF 融合的权重如何调优？

RRF 的参数只有一个（k=60 的平滑因子），不像加权融合可以直接调整各信号权重。这让 RRF 更稳健，但也**更难针对特定场景优化**。

**需要验证**：在 AgentCenter 的代码/文档/工单混合记忆场景下，RRF 是否比加权融合表现更好。

### 问题 4：Chroma 引入的必要性？

total-agent-memory 需要 Chroma 做向量检索。AgentCenter 目前没有向量检索（纯 SQLite FTS5）。引入 Chroma 意味着：

- 额外一个服务需要运维
- 数据需要在 SQLite 和 Chroma 之间同步
- 两套存储的一致性保证

**需要评估**：纯 SQLite FTS5 是否足够，还是必须引入 Chroma。

### 问题 5：fact_assertions 的 supersession 链会无限增长吗？

当一个事实被多次替代时（如规则 A → B → C → D），supersession 链会越来越长。每次时序查询需要遍历整个链来找到「当前有效事实」。

**需要验证**：在长 supersession 链（如 >100 次替代）场景下，查询性能如何，以及是否需要定期合并/归档历史。

---

*本文档由 AgentCenter 记忆系统调研组生成，数据截止日期 2026-05-23。Benchmark 数据来自框架官方发布或论文。*
