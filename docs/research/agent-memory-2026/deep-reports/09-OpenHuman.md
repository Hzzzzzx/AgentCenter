# OpenHuman 深度研究报告

> AgentCenter 记忆系统调研 · 开源项目 #9
> 调研日期：2026-05-23
> 评级：Tier C（层级记忆系统）

---

## 1. Reader Verdict

**一句话总结**：OpenHuman 是一个采用 Memory Tree 架构的 local-first 个人 AI 系统，通过确定性层级摘要树替代向量数据库，保留记忆之间的包含关系和层级结构，适合追求人类可读记忆的个人用户。

**核心评价**：OpenHuman 的 Memory Tree 设计是所有调研框架中**最具差异化的创新**。传统记忆系统基于向量数据库，返回扁平化的相似度结果；OpenHuman 用树结构替代向量索引，天然地编码了记忆之间的层级关系。这个设计在认知层面是正确的——代码有层次（package → class → method），组织有层次（公司 → 部门 → 团队），知识有层次（领域 → 子域 → 概念），把这些扁平化成向量丢失的恰恰是最重要的结构信息。三种 tree scope（source/topic/global）的划分、短时记忆的双路召回、确定性处理管线——这些设计组合构成了一套完整且自洽的记忆系统架构。然而 GPL3 许可证和 Rust 技术栈是两个重大的复用障碍。

**适用场景**：追求记忆层级结构的个人用户，需要 Obsidian 集成的知识工作者。

**不适用场景**：需要商业使用的项目（Rust 实现+GPL3 双重门槛），需要实时时态推理的场景。

---

## 2. Framework Profile

| 属性 | 值 |
|------|-----|
| **项目名称** | OpenHuman |
| **开发组织** | tinyhumansai |
| **定位** | Local-first 个人 AI 超级智能系统 |
| **技术栈** | Rust + TypeScript/React + Tauri v2 |
| **许可证** | GNU GPL3 |
| **存储介质** | SQLite + FTS5 + Markdown 文件 |
| **持久化** | 本地（~/.openhuman/） |
| **Benchmark 数据** | 无 |
| **与 AgentCenter 契合度** | 中（设计思想有价值，直接复用受限） |

---

## 3. Core Thesis

**核心论点**：OpenHuman 相信**向量数据库返回扁平结果，而树能保留层次**。一个列表里放着「设计原则、API 网关、缓存策略、订单模块」，你只能做相似度搜索。但一棵树可以按项目分层，可以按业务域分层，可以 drill-down——结构本身就是信息。

OpenHuman 的另一个核心信念是**确定性优于随机性**。从输入到树结构，每一步都是可重现的，不依赖任何随机因素。Chunk ID 采用 SHA256 内容寻址，相同内容必然得到相同 ID。这个设计使得整个系统可审计、可重现——对调试和企业合规至关重要。

**Local-first 价值观**：所有数据存储在本地，不依赖任何云服务。隐私、可靠性、数据主权——这是 local-first 的核心价值主张。

---

## 4. Theoretical Foundation

OpenHuman 的理论基础建立在**认知层级模型**之上。人类记忆不是扁平的相似度列表，而是有层级结构的。回忆「我在 AgentCenter 项目中学到的缓存策略」，你会先想起「AgentCenter」这个顶级主题，再 drill-down 到「缓存策略」这个子主题，最后找到具体的经验教训。这种层级检索是人类认知的自然模式。

**Bucket-Seal 管线**：批处理与即时写入相结合的折中方案。数据经过 Source Adapters → Canonicalizer → Chunker → Content Store → Score Engine → Tree 的完整管线，每一步都是确定性的。

**三段式 Admission**：分数 ≤ 0.15 直接丢弃（不需要 LLM），0.15-0.85 由 LLM extractor 决定（必要时调用 LLM），≥ 0.85 直接 admit（不需要 LLM）。这个设计将 LLM 调用降到最低——极低分和极高分都不需要 LLM 调用。

---

## 5. Memory Model

### 5.1 Memory Tree 架构

**树节点结构**：每个节点包含两部分——原始 chunk 的摘要，以及指向子节点的引用指针。根节点是全局摘要，叶子节点是原始的最小记忆单元。

**三种 Tree Scope**：
- **Source Tree**：每个数据源对应一棵独立的源树，完整保留某个来源的所有记忆
- **Topic Tree**：每个高频实体（人名、项目名、关键词）对应一棵主题树，懒加载策略
- **Global Tree**：每天生成一次全局摘要树，提供整个记忆空间的俯瞰视角

### 5.2 Chunk 生命周期状态机

```
pending_extraction ─┬─→ admitted ─→ buffered ─→ sealed
                    │
                    └─→ dropped
```

- **pending_extraction**：等待评分引擎决定
- **admitted**：通过 admission 评分，进入记忆系统
- **buffered**：暂存缓冲区，等待与同批次或同主题的其他 chunk 一起处理
- **sealed**：最终稳定态，已整合进 Memory Tree
- **dropped**：低价值内容，直接丢弃

### 5.3 STM Recall（短时记忆）

双路召回策略：
- **Arm 1**：基于 FTS5 检索近期 episodic entries
- **Arm 2**：cosine nearest-neighbour，在 segment embeddings 上做向量相似度检索

---

## 6. Architecture Deep Dive

### 6.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        OpenHuman 架构                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐     │
│  │   Source     │    │   Source     │    │   Source     │     │
│  │  Adapters    │    │  Adapters    │    │  Adapters    │     │
│  │  (Gmail,     │    │  (Slack,     │    │  (Docs,      │     │
│  │   Email)     │    │   Discord)   │    │   Files)     │     │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘     │
│         │                   │                   │               │
│         └───────────────────┼───────────────────┘               │
│                             ▼                                    │
│                   ┌──────────────────┐                            │
│                   │  Canonicalizer   │  normalized Markdown      │
│                   │   (标准化器)      │  + provenance metadata    │
│                   └────────┬─────────┘                            │
│                            ▼                                      │
│                   ┌──────────────────┐                            │
│                   │    Chunker       │  deterministic IDs        │
│                   │   (分块器)       │  ≤3k-token bounded        │
│                   └────────┬─────────┘                            │
│                            ▼                                      │
│                   ┌──────────────────┐                            │
│                   │  Content Store   │  atomic .md files on disk  │
│                   │   (内容存储)      │                            │
│                   └────────┬─────────┘                            │
│                            ▼                                      │
│                   ┌──────────────────┐                            │
│                   │   Score Engine   │  signals + embeddings     │
│                   │   (评分引擎)      │  + entity extraction      │
│                   └────────┬─────────┘                            │
│                            ▼                                      │
│         ┌──────────────────┬┴──────────────────┐                  │
│         ▼                  ▼                      ▼                │
│  ┌─────────────┐   ┌─────────────┐     ┌─────────────┐         │
│  │  Source     │   │   Topic     │     │   Global    │         │
│  │   Tree      │   │   Tree      │     │   Tree      │         │
│  │  (源树)     │   │  (主题树)    │     │  (全局树)    │         │
│  └─────────────┘   └─────────────┘     └─────────────┘         │
│                            ▼                                      │
│                   ┌──────────────────┐                            │
│                   │  Retrieval Layer │  6 LLM-callable tools     │
│                   │   (检索层)       │                            │
│                   └──────────────────┘                            │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │           UnifiedMemory Store (SQLite + FTS5 + vectors)  │    │
│  │  documents | kv | graph | query | fts5 | segments |     │    │
│  │  events | profile                                       │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 6.2 检索系统（6种工具）

1. **memory_tree_query_source**：在指定 Source Tree 中检索
2. **memory_tree_query_global**：在 Global Tree 中检索
3. **memory_tree_query_topic**：在指定 Topic Tree 中检索
4. **memory_tree_search_entities**：搜索所有 Topic Tree，查找与某个实体相关的记忆
5. **memory_tree_drill_down**：在已知节点的基础上向下钻取
6. **memory_tree_fetch_leaves**：获取指定树路径下的所有叶子节点

### 6.3 Obsidian 集成

OpenHuman 可以生成一个 Markdown wiki/ 目录，用户直接在 Obsidian 中浏览自己的记忆系统。「AI 与人类共享同一份记忆」——这个理念在所有调研的框架中是独一无二的。

---

## 7. Design Tradeoffs

| 权衡维度 | 选了什么 | 代价 |
|---------|---------|------|
| **树结构** | 层级检索 | 构建和维护复杂度高 |
| **确定性 ID** | SHA256 内容寻址 | 无法处理内容会变化的情况 |
| **三段式 Admission** | 最小化 LLM 调用 | 中间分数段仍有不确定性 |
| **GPL3 许可证** | 要求衍生作品开源 | 商业使用受限 |
| **Rust 实现** | 性能优异 | 贡献门槛高，社区受限 |
| **Desktop-only** | 完整应用 | 无法作为 library 集成 |

---

## 8. Evidence

**Rust 实现**：性能优异，内存安全性高。

**118+ 数据源连接器**：能够持续地、广泛地收集个人信息。

**每 20 分钟自动抓取**：真正成为 living 的记忆系统。

**三种 Tree Scope**：source/topic/global 三套独立的 Memory Tree，互补而非冗余。

**Obsidian 集成**：记忆系统可以直接被 Obsidian 消费，人类可读。

---

## 9. Applicability

| 场景 | 适用度 | 说明 |
|------|--------|------|
| **层级知识管理** | 强 | 树结构天然适合层级检索 |
| **跨源关联** | 强 | Topic Tree 汇聚跨源信息 |
| **个人笔记** | 强 | Obsidian 集成，人类可读 |
| **企业场景** | 弱 | GPL3 + local-first 不适合 |
| **时态推理** | 弱 | 无 bi-temporal 模型 |
| **实时记忆** | 中 | 每 20 分钟抓取，非真正实时 |

---

## 10. Maturity

| 维度 | 评级 | 说明 |
|------|------|------|
| **技术成熟度** | 中 | Rust 实现，架构完整 |
| **生产就绪度** | 低 | 0.54.10，早期 beta |
| **许可证** | 低 | GPL3 限制商业使用 |
| **社区活跃度** | 中 | 25,884 stars（截至 2026-02） |
| **文档完整性** | 中 | 有基础文档，细节有限 |
| **商业可用性** | 低 | GPL3 + desktop-only |

---

## 11. AgentCenter Implications

### 11.1 可借鉴的设计思想

**三种 tree scope 的划分**：source/topic/global 对应 AgentCenter 的需求——按项目 / 按业务实体 / 跨项目全局。这个概念可以直接借鉴，不需要复制代码。

**短时记忆与长时记忆的分离**：STM 双路召回（FTS5 + cosine）和 Memory Tree 的分离，是正确的分层抽象。AgentCenter 也需要类似的分层设计。

**确定性处理管线**：相同输入产生相同树结构，这使得系统可审计，对企业合规至关重要。

### 11.2 不适用的设计

**GPL3 许可证**：任何基于 OpenHuman 开发的衍生作品也必须开源并采用相同许可证。AgentCenter 这样的商业产品无法直接复用。

**Rust 实现**：AgentCenter 的 Bridge 是 Java/Spring Boot，短期内没有切换到 Rust 的计划。

**Desktop-only**：OpenHuman 是完整的桌面应用，不是作为 library 被集成的。

### 11.3 工程实现路径

如果要借鉴 OpenHuman 的架构思路，需要在 Java 生态中重新实现一套 Memory Tree。这不是不可能，但工程量不小——树结构的构建、维护、检索都需要重新设计。

---

## 12. Comparative Scorecard

| 能力 | OpenHuman | Mem0 | Hindsight | Zep | Hermes |
|------|-----------|------|-----------|-----|--------|
| **层级结构** | 强（树） | 无 | 无 | 中（图） | 无 |
| **时态推理** | 弱 | 无 | 中 | 强 | 无 |
| **多源关联** | 强 | 弱 | 弱 | 中 | 弱 |
| **Human-browsable** | 强 | 无 | 无 | 无 | 有 |
| **自学习能力** | 中 | 弱 | 弱 | 弱 | 强 |
| **检索工具** | 强（6种） | 中 | 强（4路） | 强（图） | 中 |
| **Benchmark 得分** | N/A | ~49% | 91.4% | ~64% | N/A |

---

## 13. Open Questions

1. **树结构能否Scale？** 当记忆数量达到数十万节点时，树结构的构建和维护性能如何？有无实测数据？

2. **三种 tree scope 的边界如何设计？** Source/Topic/Global 的划分是否足够通用？是否有遗漏的场景？

3. **Obsidian 集成的价值是否被高估？** 记忆系统同时服务于 AI 和人类是否真的有必要，还是增加了不必要的复杂性？

4. **GPL3 是否真的必要？** local-first 的价值观是否必须通过 GPL3 来强制执行？是否有更灵活的许可证选择？

5. **Rust 的贡献门槛如何解决？** 对于大多数 AI/ML 工程师来说，Rust 的学习曲线太高。如何降低参与门槛？

---

*本报告为 AgentCenter 内部技术调研材料，数据截止日期 2026-05-23。*
