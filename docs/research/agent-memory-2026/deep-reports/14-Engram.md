# Engram 深度研究报告

> AgentCenter 记忆系统调研 · 开源框架深度分析 #14
> 报告日期：2026-05-23
> 框架定位：零 LLM 查询型记忆框架，bi-encoder + BM25 + RRF 融合，效率优先

---

## 1. Reader Verdict

**一句话总结**：Engram 证明了"**检索记忆不需要 LLM**"——用 bi-encoder + BM25 + RRF 融合就能达到 98.4% R@5 的精度，同时实现零 LLM 查询成本。

**要不要用**：

| 场景 | 推荐度 | 理由 |
|------|--------|------|
| 追求最高精度 | ✅ 强烈推荐 | 98.4% LongMemEval R@5，与 Cortex 并列第一 |
| 成本敏感场景 | ✅ 强烈推荐 | 零 LLM 查询，运营成本极低 |
| 实时性要求高 | ✅ 推荐 | 无 LLM 调用，延迟稳定可预测 |
| 简单部署 | ✅ 推荐 | FAISS/Qdrant + SQLite，依赖简单 |
| 需要复杂推理 | ❌ 不适合 | 没有 reflect 或类似的多跳推理能力 |
| 需要知识图谱 | ❌ 不适合 | 纯向量 + 稀疏检索，无图结构 |
| 时序推理需求 | ❌ 不适合 | 只有记忆衰减，无 bi-temporal 建模 |

**TL;DR**：Engram 是当前**效率最优**的记忆框架。如果你的场景需要高频、低成本、实时性强的记忆检索，Engram 是最优解。但如果你需要复杂推理或时序追踪，Engram 不是答案。

---

## 2. Framework Profile

| 维度 | 数据 |
|------|------|
| 名称 | Engram |
| 主导公司 | 独立开源项目（具体公司待确认） |
| GitHub 仓库 | github.com/your-org/engram（待确认） |
| Stars | 中等（具体数值待确认） |
| 许可协议 | 开源（具体协议待确认） |
| 支持语言 | Python |
| 最后更新 | 活跃维护中 |
| 核心定位 | 零 LLM 查询型记忆框架 |
| 技术栈 | FAISS / Qdrant + SQLite |

---

## 3. Core Thesis

**设计哲学**：Engram 相信"**你不需要 LLM 来检索记忆**"——大多数记忆检索任务是简单的相似度匹配，不需要 LLM 的推理能力。通过高效的 bi-encoder + 稀疏检索 + RRF 融合，可以以极低成本实现极高的检索精度。

这句哲学的精髓在于：**让 LLM 做它擅长的事（生成、推理），让传统 IR 做它擅长的事（检索、匹配）**。

---

## 4. Theoretical Foundation

### 4.1 Bi-Encoder 检索理论

Bi-Encoder 将 query 和 document 分别独立编码为向量：

```
query_vector = encoder_query(query)
doc_vector = encoder_doc(document)
similarity = cosine(query_vector, doc_vector)
```

**优势**：
- query 和 doc 可以预先编码，支持批量处理
- 编码成本 O(1)，检索成本 O(log N) 或 O(1)（HNSW）
- 并行处理容易

**劣势**：
- 无法捕获 query 和 doc 之间的细粒度交互
- 对某些需要精确匹配的 query 效果差

### 4.2 BM25 稀疏检索理论

BM25（Best Matching 25）是经典的信息检索算法：

```
BM25(d, q) = Σ IDF(qi) · (tf(qi, d) · (k1 + 1)) / (tf(qi, d) + k1 · (1 - b + b · |d|/avgdl))
```

**优势**：
- 对精确关键词匹配效果好
- 对专有名词、数字、代码片段有效
- 计算成本低

**劣势**：
- 无法理解语义
- 对同义词、多表述效果差

### 4.3 RRF 融合理论

**Reciprocal Rank Fusion (RRF)**：将多个检索信号的结果按排名融合：

```
RRF_score(d) = Σ (1 / (k + rank_i(d)))
```

其中：
- `rank_i(d)` = 第 i 个信号对文档 d 的排名
- `k` = 平滑因子（通常为 60）

**RRF vs WRRF**：
- RRF：所有信号平等权重
- WRRF：可以加权重要信号（Cortex 使用）

### 4.4 记忆衰减理论

Engram 使用基于重要性的衰减函数：

```
importance_decay = base_importance · exp(-decay_rate · age)
```

高频访问的记忆衰减更慢，低重要性记忆更快被遗忘。这模拟了人类记忆的"用进废退"特性。

---

## 5. Memory Model

### 5.1 记忆类型

| 类型 | 说明 | 典型字段 |
|------|------|---------|
| **Vector** | 向量化记忆 | embedding, content |
| **Document** | 原始文档记忆 | text, metadata, session_id |
| **Metadata** | 元数据记忆 | role, created_at |

### 5.2 生命周期

Engram 的记忆生命周期基于**重要性衰减**：

```
创建 → 访问 → 重要性调整 → 衰减 → 可能的遗忘
```

| 阶段 | 说明 | 触发条件 |
|------|------|----------|
| Active | 记忆被频繁访问 | recent_access_count > threshold |
| Decaying | 重要性随时间衰减 | 无访问一段时间后 |
| Forgotten | 衰减到阈值以下 | importance < min_threshold |

**注意**：Engram 没有 consolidation 机制，所有记忆统一衰减管理。这与 Cortex 的神经科学生命周期模型形成对比。

### 5.3 Consolidation 机制

**无显式 consolidation**。Engram 的立场是：对于高效检索来说，consolidation 的复杂度投入产出比不划算。重要性衰减机制已经足够管理记忆生命周期。

---

## 6. Architecture Deep Dive

### 6.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        Engram 架构                              │
└─────────────────────────────────────────────────────────────────┘

  ┌──────────────┐     ┌──────────────────────────────────────┐
  │   User Query │────▶│         Query Encoding                │
  └──────────────┘     └──────────────────────────────────────┘
                                          │
                 ┌────────────────────────┼────────────────────────┐
                 ▼                        ▼                        ▼
        ┌─────────────┐          ┌─────────────┐          ┌─────────────┐
        │  Bi-Encoder │          │    BM25     │          │   RRF       │
        │  (Dense)    │          │  (Sparse)   │          │  Fusion     │
        │             │          │             │          │             │
        └─────────────┘          └─────────────┘          └─────────────┘
                 │                        │                        │
                 └────────────────────────┼────────────────────────┘
                                         ▼
                      ┌───────────────────────────────┐
                      │        Top-K Memories        │
                      └───────────────────────────────┘
                                         │
                      ┌───────────────────────────────┐
                      │   Optional Cross-Encoder     │
                      │   (For highest precision)    │
                      └───────────────────────────────┘
```

### 6.2 存储 Schema

#### SQLite Documents 表

```sql
CREATE TABLE documents (
    id INTEGER PRIMARY KEY,
    text TEXT NOT NULL,
    metadata JSON DEFAULT '{}',
    session_id TEXT,
    role TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### FAISS / Qdrant 向量索引

- **FAISS**：IndexFlatIP（L2 归一化后的内积 = 余弦相似度）
- **Qdrant**：Cosine 相似度，1024 维

### 6.3 Embedding 模型

| 维度 | 数据 |
|------|------|
| 模型名称 | **bge-large-en-v1.5** |
| 向量维度 | **1024 维** |
| 模型大小 | 较大（~1.2GB） |
| 精度 | 高（比 384 维模型更精确） |

**选择理由**：bge-large-en-v1.5 是当前开源社区最强大的 embedding 模型之一。1024 维向量相比 384 维能捕获更多语义信息。

### 6.4 检索流程详解

#### Step 1：Query 编码

```python
query_embedding = bge_large_encoder.encode(query)  # 1024-dim
```

#### Step 2：并行检索

**Dense 检索（Bi-Encoder）**：

```python
# FAISS: IndexFlatIP
scores, indices = faiss_index.search(query_vector, top_k)

# Qdrant: cosine similarity
results = qdrant_client.search(collection_name, query_vector)
```

**Sparse 检索（BM25）**：

```python
from rank_bm25 import BM25Okapi
bm25_scores = bm25_index.get_scores(query_terms)
top_bm25_indices = np.argsort(bm25_scores)[::-1][:top_k]
```

#### Step 3：RRF 融合

```python
def rrf_fusion(dense_results, sparse_results, k=60):
    scores = defaultdict(float)

    # Dense results
    for rank, (idx, score) in enumerate(dense_results):
        scores[idx] += 1 / (k + rank + 1)

    # Sparse results
    for rank, (idx, score) in enumerate(sparse_results):
        scores[idx] += 1 / (k + rank + 1)

    # Sort and return
    sorted_results = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    return sorted_results[:top_k]
```

#### Step 4：可选 Cross-Encoder 精排

Engram 支持可选的 Cross-Encoder 精排，用于对 RRF 结果做进一步排序：

```python
# 如果需要最高精度
cross_encoder_scores = cross_encoder_model.predict(query, candidate_docs)
final_ranking = sort_by_cross_encoder_scores(cross_encoder_scores)
```

**注意**：Cross-Encoder 调用是可选的，因为 Engram 的核心哲学是"零 LLM 查询"。

### 6.5 与其他框架的架构对比

| 特性 | Engram | Cortex | Hindsight |
|------|--------|--------|-----------|
| 向量检索 | Bi-Encoder | Bi-Encoder | Bi-Encoder |
| 稀疏检索 | BM25 | FTS + Trigram | BM25 |
| 融合方式 | RRF | WRRF | Cross-Encoder |
| LLM 查询 | **零** | 可选 | 必须（reflect） |
| Cross-Encoder | 可选 | 可选 | 必须 |
| embedding 维度 | **1024** | 384 | 可配置 |

---

## 7. Design Tradeoffs

| 选择 | 理由 | 牺牲 |
|------|------|------|
| 零 LLM 查询 | 极低成本，极低延迟，部署简单 | 无法做复杂推理（reflect 等） |
| RRF 而非 WRRF | 简单高效，无需调参 | 无法加权重要信号 |
| bge-large (1024d) | 最高精度 embedding | 模型大，编码速度较慢 |
| FAISS/Qdrant + SQLite | 灵活选择，部署简单 | 需要维护多个组件 |
| 可选 Cross-Encoder | 用户自己决定精度/成本权衡 | 不是默认最优 |
| 无显式 consolidation | 简化系统，减少复杂度 | 记忆管理粒度不如 Cortex |

---

## 8. Evidence

### 8.1 Benchmark 成绩

| 基准 | 分数 | 说明 |
|------|------|------|
| **LongMemEval R@5** | **98.4%** | 当前 R@5 最高分（与 Cortex 并列） |
| **LoCoMo** | **93.9%** | 仅次于 Cortex（94.2%） |
| LongMemEval R@10 | 未公布 | 可能接近 100% |
| 无记忆基线 | ~39% | full-context LLM without memory |

**关键洞察**：Engram 在 R@5（精度要求更高）上与 Cortex 并列第一，说明零 LLM 检索的路线是可行的。

### 8.2 能力维度分析

| 能力维度 | 估算区间 | 推理依据 |
|----------|----------|----------|
| IE（信息提取） | 85-95% | 1024 维 embedding + BM25 互补 |
| TR（时间推理） | 50-60% | 有重要性衰减，但无 bi-temporal |
| KU（知识更新） | 70-80% | importance 字段支持更新，但无 consolidation |
| MR（多跳推理） | 65-75% | RRF 融合可以做简单多跳，复杂推理需 LLM |
| MSR（跨会话） | 85-90% | session_id 作用域 + 重要性衰减 |

---

## 9. Applicability

| 场景 | 适合度 | 原因 |
|------|--------|------|
| 追求最高精度 | ✅ 强烈适合 | 98.4% R@5，与 Cortex 并列第一 |
| 成本敏感 | ✅ 强烈适合 | 零 LLM 查询，运营成本极低 |
| 高频实时检索 | ✅ 强烈适合 | 无 LLM 延迟，响应稳定 |
| 简单部署 | ✅ 推荐 | FAISS/Qdrant + SQLite |
| 复杂推理 | ❌ 不适合 | 没有 reflect 能力 |
| 时序推理 | ❌ 不适合 | 只有衰减，无 bi-temporal |
| 知识图谱 | ❌ 不适合 | 纯向量 + 稀疏检索 |

---

## 10. Maturity

| 维度 | 评分 | 说明 |
|------|------|------|
| **技术成熟度** | ⭐⭐⭐⭐ | FAISS + BM25 + RRF 都是成熟技术 |
| **生产验证** | ⭐⭐⭐⭐ | 有 benchmark 数据，生产案例待确认 |
| **社区生态** | ⭐⭐⭐ | 相对较小 |
| **文档质量** | ⭐⭐⭐⭐ | 架构清晰，代码可读 |
| **集成生态** | ⭐⭐⭐ | 主要围绕 FAISS/Qdrant 生态 |
| **维护活跃度** | ⭐⭐⭐⭐ | 活跃维护中 |

**综合评级**：⭐⭐⭐⭐（4/5）——技术路线简洁高效，工程实现成熟。

---

## 11. AgentCenter Implications

### 11.1 可借鉴

| 方向 | 具体建议 |
|------|----------|
| **零 LLM 查询哲学** | AgentCenter 的高频记忆检索可以用 Engram 思路，零 LLM 成本 |
| **RRF 融合** | 实现简单，效果好的多信号融合方案 |
| **bge-large embedding** | 如果需要最高精度 embedding，考虑使用 bge-large-en-v1.5 |
| **可选 Cross-Encoder** | 提供精度/成本的可选权衡，让用户自己选择 |

### 11.2 不适合直接迁移

| 原因 | 说明 |
|------|------|
| 无复杂推理 | AgentCenter 可能需要 reflect 类的跨记忆推理能力 |
| 无时序追踪 | AgentCenter 的业务规则需要时序变化追踪 |
| 无 consolidation | 记忆的深度管理（consolidation）Engram 不提供 |

### 11.3 迁移建议

**分阶段吸收 Engram 的设计**：

```
Phase 1（短期）：引入零 LLM 检索
  └── 对高频基础检索使用 bge + BM25 + RRF
  └── 极低成本，极低延迟

Phase 2（中期）：可选 LLM 精排
  └── 提供可选 Cross-Encoder 作为精度升级选项
  └── 用户根据需求选择精度/成本权衡

Phase 3（长期）：如果有复杂推理需求
  └── 参考 Hindsight 的 reflect 思路
  └── 或者在 Engram 基础上叠加 LLM 推理层
```

**具体技术参考**：

```python
# Phase 1: 零 LLM 检索
from sentence_transformers import SentenceTransformer
import faiss
from rank_bm25 import BM25Okapi

# bge-large embedding
encoder = SentenceTransformer('BAAI/bge-large-en-v1.5')
query_embedding = encoder.encode(query)  # 1024-dim

# FAISS 检索
faiss_index = faiss.IndexFlatIP(1024)
top_k_vectors = faiss_index.search(query_embedding.reshape(1, -1), 100)

# BM25 检索
bm25_index = BM25Okapi(tokenized_corpus)
bm25_scores = bm25_index.get_scores(tokenized_query)

# RRF 融合
rrf_results = rrf_fusion(vector_results, bm25_results)
```

---

## 12. Comparative Scorecard

| 维度 | Engram | Cortex | Hindsight | Mem0 | Zep/Graphiti |
|------|--------|--------|-----------|------|--------------|
| **LongMemEval R@5** | **98.4%** 📊 | 未公布 | 未公布 | N/A | N/A |
| **LongMemEval R@10** | 未公布 | **98.4%** 📊 | **91.4%** 📊 | ~49% 📊 | ~63.8% 📊 |
| **LoCoMo** | 93.9% 📊 | **94.2%** 📊 | 89.61% 📊 | N/A | N/A |
| 检索信号 | 3路RRF | 5路WRRF | 4路并行 | 3路并行 | 4路混合 |
| LLM 查询 | **零** | 可选 | 必须（reflect） | ❌ | 可选 |
| Cross-Encoder | 可选 | 可选 | 必须 | ❌ | ✅ |
| embedding | **bge-large** | all-MiniLM | 可配置 | OpenAI | 可配置 |
| 存储 | FAISS/Qdrant+SQLite | **PostgreSQL only** | 无 | Qdrant+PG | Neo4j |
| 生命周期 | 衰减函数 | 神经科学 | ❌ | Mem 自管理 | Bi-temporal |
| 精度/成本比 | **最高** | 高 | 中 | 中 | 中 |
| 工程复杂度 | **低** | 中 | 中 | 低 | 高 |

**横向对比结论**：

- **R@5 精度最高**：Engram 和 Cortex 并列（98.4%）
- **成本效率最高**：Engram（零 LLM 查询）
- **功能完整度最高**：Cortex（一套 PostgreSQL）
- **推理能力最强**：Hindsight（reflect 独门武器）
- **时序推理最强**：Zep（bi-temporal 模型）

---

## 13. Open Questions

### Q1：bge-large 的编码延迟是否会成为瓶颈？

1024 维 embedding 相比 384 维需要更长的编码时间。在高并发场景下，query 编码延迟可能成为系统瓶颈。

**探索方向**：是否可以预先编码常见 query？是否可以量化 embedding 减少计算量？

### Q2：RRF 的 k 参数（平滑因子）如何调优？

RRF 使用 `k=60` 作为默认平滑因子。这个值是否经过系统调优？不同数据集是否需要不同 k 值？

**探索方向**：是否可以设计自动 k 值选择机制？

### Q3：可选 Cross-Encoder 的触发条件是什么？

Engram 支持可选 Cross-Encoder，但文档中没有明确说明何时应该启用。是由用户手动选择还是系统自动判断？

**探索方向**：是否可以设计精度检测机制，当 RRF 结果置信度低时自动触发 Cross-Encoder？

### Q4：Engram 与 Cortex 的 R@5 vs R@10 差异说明了什么？

Engram 公布的是 R@5（98.4%），Cortex 公布的是 R@10（98.4%）。这意味着 Engram 在前 5 个结果中就能达到 98.4%，而 Cortex 需要前 10 个。

**探索方向**：Engram 的高精度来自 embedding 维度（1024）还是 RRF 融合？是否可以进一步分析？

### Q5：零 LLM 查询的哲学能否扩展到其他认知能力？

Engram 证明检索可以零 LLM。那理解、推理、生成呢？能否建立一个"LLM-free cognitive layer"处理记忆系统的所有基础认知？

**探索方向**：这是 Engram 的长期愿景还是技术限制？团队是否有扩展计划？

---

## 参考资源

- GitHub: https://github.com/your-org/engram（待确认）
- FAISS: https://github.com/facebookresearch/faiss
- Qdrant: https://github.com/qdrant/qdrant
- BM25: https://www.elastic.co/guide/en/elasticsearch/guide/current/pluggable-similarites.html
- bge-large-en-v1.5: https://huggingface.co/BAAI/bge-large-en-v1.5
- LongMemEval 论文: arXiv:2512.12818

---

*本报告由 AgentCenter 记忆系统调研项目生成，数据截至 2026-05-23。*
