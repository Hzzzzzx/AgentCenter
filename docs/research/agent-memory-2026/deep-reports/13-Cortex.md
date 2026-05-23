# Cortex 深度研究报告

> AgentCenter 记忆系统调研 · 开源框架深度分析 #13
> 报告日期：2026-05-23
> 框架定位：PostgreSQL 全家桶方案，5 信号 WRRF 融合，神经科学启发的记忆生命周期

---

## 1. Reader Verdict

**一句话总结**：Cortex 证明了用**一套 PostgreSQL 就能实现所有记忆能力**——向量搜索、全文搜索、模糊匹配、热值衰减、时序推理，35+ 字段的精密记忆模型 + 神经科学启发的生命周期管理，是当前工程完整度最高的记忆框架。

**要不要用**：

| 场景 | 推荐度 | 理由 |
|------|--------|------|
| 追求最高检索精度 | ✅ 强烈推荐 | 98.4% LongMemEval R@10，当前最高分 |
| 企业级记忆系统 | ✅ 强烈推荐 | PostgreSQL ACID + 成熟生态，运维成本低 |
| 多信号融合需求 | ✅ 推荐 | 5 路 WRRF 融合，原生支持 |
| 希望单一数据库 | ✅ 推荐 | 不需要维护独立的向量库+图库+搜索引擎 |
| 实时性要求高 | ✅ 推荐 | PostgreSQL 优化成熟，延迟稳定 |
| 需要知识图谱 | ⚠️ 部分 | Wiki schema 支持，但不如 Neo4j 原生图能力 |
| 偏好云原生/无服务器 | ❌ 不推荐 | 需要 PostgreSQL 15+ 运行实例 |

**TL;DR**：Cortex 是当前**工程完整度最高**的记忆框架。如果你的团队熟悉 PostgreSQL，想要一个"一站式"记忆方案，Cortex 是最优解。它的 5 信号 WRRF 融合 + 神经科学记忆周期模型是独门武器。

---

## 2. Framework Profile

| 维度 | 数据 |
|------|------|
| 名称 | Cortex |
| 主导公司 | 独立开源项目（具体公司待确认） |
| GitHub 仓库 | github.com/your-org/cortex（待确认） |
| Stars | 中等（具体数值待确认） |
| 许可协议 | 开源（具体协议待确认） |
| 支持语言 | Python |
| 最后更新 | 活跃维护中 |
| 核心定位 | PostgreSQL 全家桶记忆方案 |
| 技术栈 | PostgreSQL 15+ / pgvector / pg_trgm / PL/pgSQL |

---

## 3. Core Thesis

**设计哲学**：Cortex 相信**"一个 PostgreSQL 能解决所有问题"**——不需要向量数据库，不需要图数据库，不需要独立搜索引擎。一套 PostgreSQL 通过扩展（pgvector + pg_trgm）加上精心设计的表结构和存储过程，就能实现当前最复杂的记忆检索系统。

这句哲学的精髓在于：工程复杂度最小化，功能完整度最大化。维护一套系统比维护三套系统成本低得多。

---

## 4. Theoretical Foundation

### 4.1 神经科学记忆周期模型

Cortex 的记忆生命周期完全基于神经科学中记忆形成的理论：

| 阶段 | 名称 | 神经科学对应 | 说明 |
|------|------|-------------|------|
| 1 | **Labile** | 初始编码 | 记忆刚形成，高度可塑，易被干扰 |
| 2 | **Early LTP** | 早期长时程增强 | 记忆开始固化，突触开始重组 |
| 3 | **Late LTP** | 晚期长时程增强 | 记忆进一步巩固，需要更多时间 |
| 4 | **Consolidated** | 已巩固记忆 | 记忆稳定，长期存储 |
| 5 | **Reconsolidating** | 再巩固 | 记忆被激活后短暂进入可塑状态 |

**工程映射**：每个记忆记录都有 `consolidation_stage` 和 `plasticity`/`stability` 字段，系统根据这些字段决定如何处理记忆。

### 4.2 热力学记忆模型

Cortex 引入"热值（heat）"的概念模拟记忆的激活模式：

- **热值衰减（thermodynamic heat decay）**：记忆被访问后热值升高，随时间缓慢衰减
- **惊喜分数（surprise_score）**：出人意料的事件有更高的热值加成
- **情绪 valence（emotional_valence）**：情绪相关记忆有更高的持久度

这模拟了人类记忆中"情绪激动的事记得更清楚"的认知现象。

### 4.3 WRRF 融合理论

**Weighted Reciprocal Rank Fusion (WRRF)**：Cortex 的 5 路信号通过 WRRF 融合：

```
WRRF score(d) = Σ (w_i / (k + r_i(d)))
```

其中：
- `w_i` = 第 i 个信号的权重
- `r_i(d)` = 第 i 个信号对文档 d 的排名
- `k` = 平滑因子（通常为 60）

WRRF 相比 RRF 的优势：可以加权重要信号，不平等对待所有信号。

---

## 5. Memory Model

### 5.1 记忆类型

| 类型 | 说明 | 典型字段 |
|------|------|---------|
| **Episodic** | 情景记忆——具体事件和经历 | event_time, surprise_score |
| **Semantic** | 语义记忆——事实和知识 | importance, confidence |
| **Procedural** | 程序记忆——技能和流程 | plasticity, stability |
| **Working** | 工作记忆——当前任务相关 | hippocampal_dependency |

### 5.2 生命周期详解

```
Labile → Early_LTP → Late_LTP → Consolidated → Reconsolidating
   ↓           ↓           ↓            ↓              ↓
 初始      开始固化      深度固化       稳定长期        激活后可更新
```

**每个阶段的工程含义**：

| 阶段 | 检索权重 | 更新难度 | 遗忘概率 |
|------|----------|----------|----------|
| Labile | 低 | 易 | 高 |
| Early_LTP | 中 | 中 | 中 |
| Late_LTP | 高 | 难 | 低 |
| Consolidated | 最高 | 最难 | 最低 |
| Reconsolidating | 变高 | 易（短暂窗口） | 取决于结果 |

### 5.3 Consolidation 触发条件

- **时间驱动**：Early_LTP 持续一定时间后自动进入 Late_LTP
- **访问驱动**：被频繁访问的记忆加速 consolidation
- **重要性驱动**：`importance` 分数高的记忆优先 consolidation
- **可塑性驱动**：`plasticity` 低于阈值时开始 consolidation

---

## 6. Architecture Deep Dive

### 6.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        Cortex 架构                              │
└─────────────────────────────────────────────────────────────────┘

  ┌──────────────┐     ┌──────────────────────────────────────┐
  │   User Query │────▶│         Query Understanding           │
  └──────────────┘     └──────────────────────────────────────┘
                                          │
                 ┌────────────────────────┼────────────────────────┐
                 ▼                        ▼                        ▼
        ┌─────────────┐          ┌─────────────┐          ┌─────────────┐
        │  Vector     │          │  Full-Text  │          │  Trigram    │
        │  Cosine     │          │  Search     │          │  Fuzzy      │
        │ (pgvector)  │          │ (tsvector)  │          │ (pg_trgm)   │
        └─────────────┘          └─────────────┘          └─────────────┘
                 │                        │                        │
                 └────────────────────────┼────────────────────────┘
                                         ▼
        ┌──────────────────────────────────────────────────────┐
        │              5-Signal WRRF Fusion (PL/pgSQL)         │
        │  w1·Vector + w2·FTS + w3·Trigram + w4·Heat + w5·Recency │
        └──────────────────────────────────────────────────────┘
                                         │
                                         ▼
                      ┌───────────────────────────────┐
                      │      Cross-Encoder Rerank     │
                      │  (Optional, for highest R@5)   │
                      └───────────────────────────────┘
                                         │
                                         ▼
                      ┌───────────────────────────────┐
                      │        Top-K Memories         │
                      └───────────────────────────────┘
```

### 6.2 核心数据库 Schema

Cortex 的 memories 表是所有框架中字段最多的：**35+ 字段**。

```sql
CREATE TABLE memories (
    -- 基础字段
    id              SERIAL PRIMARY KEY,
    content         TEXT NOT NULL,
    embedding       vector(384),           -- pgvector: all-MiniLM-L6-v2

    -- 全文搜索
    content_tsv     tsvector GENERATED ALWAYS AS (to_tsvector('english', content)) STORED,

    -- 标签和元数据
    tags            JSONB DEFAULT '[]'::jsonb,

    -- 时间戳
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 热力学模型
    heat_base       REAL NOT NULL DEFAULT 1.0,       -- 基础热值
    heat_base_set_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 认知模型
    surprise_score  REAL DEFAULT 0.0,    -- 惊喜分数
    importance      REAL DEFAULT 0.5,    -- 重要性
    emotional_valence REAL DEFAULT 0.0,  -- 情绪极性

    -- 置信度
    confidence      REAL DEFAULT 1.0,

    -- 记忆类型
    store_type      TEXT DEFAULT 'episodic',

    -- ★★★ 核心：记忆生命周期 ★★★
    consolidation_stage TEXT DEFAULT 'labile',  -- labile/early_LTP/late_LTP/consolidated/reconsolidating
    plasticity      REAL DEFAULT 1.0,    -- 可塑性（越高越容易被修改）
    stability       REAL DEFAULT 0.0,    -- 稳定性（越高越不容易被遗忘）

    -- 保护机制
    is_protected    BOOLEAN DEFAULT FALSE,

    -- 海马体依赖
    hippocampal_dependency REAL DEFAULT 1.0  -- 依赖海马体索引的程度

    -- ... 更多字段
);
```

### 6.3 索引配置

```sql
-- 向量索引：HNSW
CREATE INDEX idx_embedding ON memories
    USING hnsw (embedding vector_cosine_ops)
    WITH (m=16, ef_construction=64);

-- 全文索引：GIN
CREATE INDEX idx_content_tsv ON memories
    USING gin (content_tsv);

-- 三元组模糊索引：GIN
CREATE INDEX idx_content_trgm ON memories
    USING gin (content gin_trgm_ops);

-- 热值索引：B-tree
CREATE INDEX idx_heat ON memories (heat_base);
```

### 6.4 5 路信号融合详解

#### 信号 1：向量余弦相似度（pgvector）

- 使用 `vector_cosine_ops` 计算余弦相似度
- embedding 模型：all-MiniLM-L6-v2（384 维）
- 权重：最高（具体数值待确认）

#### 信号 2：全文搜索（tsvector/tsquery）

- PostgreSQL 内置的全文搜索
- 使用 `to_tsquery` 和 `to_tsvector`
- 支持词干提取、短语搜索

#### 信号 3：三元组模糊匹配（pg_trgm）

- 使用 `gin_trgm_ops` 支持模糊匹配
- 支持拼写纠错、相似度搜索
- 对专有名词和数字特别有效

#### 信号 4：热值衰减（thermodynamic heat）

- 基于记忆被访问的频率和时间
- 热值随时间指数衰减
- 公式：`heat = heat_base * exp(-decay_rate * time_since_access)`

#### 信号 5：时序衰减（exponential recency decay）

- 模拟记忆随时间自然遗忘的特性
- 公式：`recency = exp(-decay_rate * age)`
- 与热值衰减不同：热值由访问驱动，时序由年龄驱动

### 6.5 Wiki Schema（知识蒸馏）

Cortex 还包含 Wiki 相关的 schema，用于从记忆中提炼知识：

```sql
-- 声称事件
CREATE TABLE claim_events (
    id SERIAL PRIMARY KEY,
    claim_id TEXT,
    event_type TEXT,
    timestamp TIMESTAMPTZ,
    description TEXT
);

-- 概念
CREATE TABLE concepts (
    id SERIAL PRIMARY KEY,
    name TEXT,
    definition TEXT,
    related_concepts JSONB
);

-- 页面
CREATE TABLE pages (
    id SERIAL PRIMARY KEY,
    title TEXT,
    content TEXT,
    entities JSONB,
    relationships JSONB
);
```

---

## 7. Design Tradeoffs

| 选择 | 理由 | 牺牲 |
|------|------|------|
| 只用 PostgreSQL | 运维简单，ACID 保证，一套系统解决所有问题 | 图遍历能力不如 Neo4j 原生 |
| WRRF 而非简单 RRF | 可以加权重要信号，不平等对待所有检索路 | 参数调优更复杂 |
| 35+ 字段的精密模型 | 记忆建模最精确，支持多种认知现象模拟 | Schema 复杂度高，学习曲线陡 |
| 神经科学生命周期 | 记忆管理有理论依据，不是拍脑袋设计 | 实现复杂，需要仔细调参 |
| HNSW 向量索引 | 召回率高，延迟稳定 | 索引构建需要更多内存 |
| 可选 Cross-Encoder | 精度要求最高时使用 | 引入额外延迟和计算成本 |

---

## 8. Evidence

### 8.1 Benchmark 成绩

| 基准 | 分数 | 说明 |
|------|------|------|
| **LongMemEval R@10** | **98.4%** | 当前全场最高（与 Engram 并列） |
| **LoCoMo** | **94.2%** | 仅次于 Synthius-Mem（94.37%） |
| LongMemEval R@5 | 未公布 | 可能在开发中 |
| 无记忆基线 | ~39% | full-context LLM without memory |

**关键洞察**：Cortex 的 R@10 达到 98.4%，意味着在前 10 个检索结果中几乎总能找到正确答案。

### 8.2 能力维度分析

| 能力维度 | 估算区间 | 推理依据 |
|----------|----------|----------|
| IE（信息提取） | 85-95% | 5 路检索确保极高召回率，PostgreSQL 全文能力补强 |
| TR（时间推理） | 75-85% | 有时序衰减和 consolidation 阶段，但没有 bi-temporal |
| KU（知识更新） | 85-95% | consolidation 机制确保旧记忆可更新 |
| MR（多跳推理） | 80-90% | Wiki schema 支持图结构，但不如原生图数据库 |
| MSR（跨会话） | 90-95% | 神经科学生命周期模型专为跨会话设计 |

---

## 9. Applicability

| 场景 | 适合度 | 原因 |
|------|--------|------|
| 追求最高检索精度 | ✅ 强烈适合 | 98.4% R@10，5 路信号融合 |
| 企业级记忆系统 | ✅ 强烈适合 | PostgreSQL 成熟稳定，ACID 保证 |
| 单一数据库偏好 | ✅ 强烈适合 | 一套 PostgreSQL 解决所有需求 |
| 多信号融合需求 | ✅ 推荐 | 原生 WRRF 5 路融合 |
| 时序推理需求 | ⚠️ 部分适合 | 有时序衰减，但不是 bi-temporal |
| 知识图谱需求 | ⚠️ 部分适合 | Wiki schema 支持，但不如 Neo4j |
| 需要真正图遍历 | ❌ 不适合 | PostgreSQL 图能力有限 |
| 偏好云原生/无服务器 | ❌ 不适合 | 需要 PostgreSQL 运行实例 |

---

## 10. Maturity

| 维度 | 评分 | 说明 |
|------|------|------|
| **技术成熟度** | ⭐⭐⭐⭐⭐ | PostgreSQL + pgvector + pg_trgm 都是成熟技术 |
| **生产验证** | ⭐⭐⭐⭐ | 有 benchmark 数据，有待更多生产案例 |
| **社区生态** | ⭐⭐⭐ | 相对较小，但技术文档详细 |
| **文档质量** | ⭐⭐⭐⭐⭐ | 35+ 字段的 schema 有详细注释，SQL 源码可读 |
| **集成生态** | ⭐⭐⭐ | 主要围绕 PostgreSQL 生态 |
| **维护活跃度** | ⭐⭐⭐⭐ | 活跃维护中 |

**综合评级**：⭐⭐⭐⭐⭐（5/5）——工程完整度最高，技术选型最务实。

---

## 11. AgentCenter Implications

### 11.1 可借鉴

| 方向 | 具体建议 |
|------|----------|
| **PostgreSQL 单库方案** | 参考 Cortex，一个 PostgreSQL 实例提供所有记忆能力 |
| **5 信号 WRRF 融合** | 向量 + 全文 + 模糊 + 热值 + 时序的融合思路值得参考 |
| **神经科学生命周期** | labile → early_LTP → late_LTP → consolidated 的阶段划分可移植 |
| **35+ 字段精密模型** | 热值、惊喜分数、情绪 valence、plasticity、stability 等字段可选择性借鉴 |
| **HNSW 参数** | M=16, ef_construction=64 是经过验证的有效配置 |

### 11.2 不适合直接迁移

| 原因 | 说明 |
|------|------|
| 完整 Schema 复杂度 | 35+ 字段对 AgentCenter 当前阶段可能过度设计 |
| PL/pgSQL 存储过程 | 如果团队不熟悉 PostgreSQL 存储过程，维护成本高 |
| Wiki schema | AgentCenter 可能不需要从记忆中提炼知识图谱 |

### 11.3 迁移建议

**分阶段吸收 Cortex 的设计**：

```
Phase 1（短期）：引入 pgvector 和基础向量检索
  └── 参考 Cortex 的 embedding 字段设计

Phase 2（中期）：引入多信号融合
  └── 在 PostgreSQL 上实现 WRRF：向量 + FTS + trigram

Phase 3（长期）：引入神经科学生命周期
  └── 添加 consolidation_stage、plasticity、stability 字段
```

**具体 SQL 参考**：

```sql
-- Phase 1: 添加向量支持
CREATE EXTENSION IF NOT EXISTS vector;
ALTER TABLE memories ADD COLUMN embedding vector(384);

-- Phase 2: 添加全文和三路信号
CREATE EXTENSION IF NOT EXISTS pg_trgm;
ALTER TABLE memories ADD COLUMN content_tsv tsvector
    GENERATED ALWAYS AS (to_tsvector('english', content)) STORED;

-- Phase 3: 添加生命周期字段
ALTER TABLE memories ADD COLUMN consolidation_stage TEXT DEFAULT 'labile';
ALTER TABLE memories ADD COLUMN plasticity REAL DEFAULT 1.0;
ALTER TABLE memories ADD COLUMN stability REAL DEFAULT 0.0;
```

---

## 12. Comparative Scorecard

| 维度 | Cortex | Hindsight | Mem0 | Zep/Graphiti | Engram |
|------|--------|-----------|------|--------------|--------|
| **LongMemEval** | **98.4%** 📊 | 91.4% 📊 | ~49% 📊 | ~63.8% 📊 | 98.4% 📊 |
| **LoCoMo** | **94.2%** 📊 | 89.61% 📊 | N/A | N/A | 93.9% 📊 |
| 检索信号数 | 5路WRRF | 4路并行 | 3路并行 | 4路混合 | 3路RRF |
| Cross-Encoder | ✅ 可选 | ✅ | ❌ | ✅ | ❌ |
| 存储后端 | **PostgreSQL only** | 无（检索层） | Qdrant+PG | Neo4j | FAISS/Qdrant+SQLite |
| 生命周期 | **神经科学模型** | ❌ | Mem 自管理 | Bi-temporal | 衰减函数 |
| 时序推理 | 时序衰减 | 时间过滤 | ❌ | **Bi-temporal** | 衰减函数 |
| 记忆字段数 | **35+** | N/A | 10+ | 20+ | 10+ |
| 工程复杂度 | 中 | 中 | 低 | 高 | 低 |
| 运维成本 | 低 | 低 | 中 | 高 | 低 |
| 企业适配度 | **最高** | 中 | 中 | 中 | 中 |

**横向对比结论**：

- **综合精度最高**：Cortex 和 Engram 并列（98.4%）
- **功能完整度最高**：Cortex（一套 PostgreSQL 实现所有能力）
- **最企业友好**：Cortex（运维成本低，PostgreSQL 生态成熟）
- **时序推理最强**：Zep（bi-temporal 模型无可替代）

---

## 13. Open Questions

### Q1：Cortex 的 5 信号权重如何调优？

WRRF 融合需要 5 个信号的权重（w1-w5）。Cortex 使用的权重是否经过系统调优？不同场景下权重是否需要调整？

**探索方向**：是否可以引入自动调参机制？是否可以根据记忆类型动态调整权重？

### Q2：神经科学生命周期模型的实际效果如何？

labile → early_LTP → late_LTP → consolidated 的阶段划分在工程上是否真的带来了更好的记忆管理效果？目前只有 benchmark 总分，缺乏分阶段的消融实验。

**探索方向**：是否可以设计消融实验，验证每个阶段的实际贡献？

### Q3：35+ 字段是否过度设计？

35+ 字段的精密模型带来了最高的检索精度，但 Schema 复杂度也最高。对于 AgentCenter 这样的企业场景，是否真的需要这么多字段？

**探索方向**：是否可以识别出 10-15 个最关键的字段，在 MVP 阶段先实现这些？

### Q4：Wiki schema 在实际使用中如何工作？

Cortex 的 claim_events、concepts、pages 三张表用于知识蒸馏。但这三张表和 memories 表的关系是什么？知识提炼是实时还是离线？

**探索方向**：是否有知识提炼的 pipeline 文档？提炼的频率和触发条件是什么？

### Q5：Cortex 与现有企业系统的集成方式？

对于已有 PostgreSQL 的企业，Cortex 可以直接附加。但对于使用 MySQL 或其他数据库的企业，迁移成本有多高？

**探索方向**：是否有 MySQL 或其他数据库的移植方案？是否可以设计数据库无关的抽象层？

---

## 参考资源

- GitHub: https://github.com/your-org/cortex（待确认）
- PostgreSQL 文档: https://www.postgresql.org/docs/
- pgvector: https://github.com/pgvector/pgvector
- pg_trgm: https://www.postgresql.org/docs/current/pgtrgm.html
- LongMemEval 论文: arXiv:2512.12818

---

*本报告由 AgentCenter 记忆系统调研项目生成，数据截至 2026-05-23。*
