# AgentCenter 记忆数据层设计提案

> AgentCenter 记忆系统调研 · 架构设计专题

---

## 1. 设计目标

AgentCenter 记忆数据层的设计旨在为企业级 Agent 平台提供统一、灵活且高性能的记忆存储与检索能力。当前企业智能编排平台需要处理来自工作台对话、流程编排、代码检查、构建部署等多源异构数据，记忆系统必须能够在保障数据一致性的同时支持多样化的检索场景。

Memory Data Layer 必须达成以下核心目标:

**统一不同存储后端**: Vector DB, Graph DB, Relational DB, File system

记忆系统需要抽象化底层存储差异，使得上层业务逻辑能够无感知地切换存储引擎。企业环境可能基于已有技术栈选择 PostgreSQL 作为主存储，而向量检索可能依赖 Qdrant 或 Milvus，图数据可能使用 Neo4j 或 Apache AGE。设计必须确保这种异构性对业务层完全透明。

**提供一致的 API**: 不管底层存储什么，上层调用方式不变

这是接口设计层面的核心原则。所有记忆操作（添加、查询、更新、删除）都通过统一接口完成，后端适配器负责将通用操作翻译为特定存储引擎的原生操作。这一原则直接决定了系统可维护性和可测试性。

**支持多种记忆类型**: 事实(Fact)、事件(Event)、过程(Procedure)、偏好(Preference)、规则(Rule)

不同类型的记忆具有不同的语义特征和访问模式。事实型记忆需要时序追踪和版本管理，事件型记忆强调时间窗口查询，过程型记忆需要与工作流引擎深度集成，偏好型记忆需要高频更新而规则型记忆则强调一致性校验。

**处理时序推理**: 追踪事实何时有效/失效

企业数据普遍存在时效性约束。供应商变更、项目里程碑、人员调动等信息都需要记录其有效时间窗口。系统必须支持 bi-temporal 查询，既能回答"当前是什么"，也能回答"某个时间点是什么"。

**支持多种检索策略**: 语义搜索、关键词、图遍历、混合

单一检索模式无法满足复杂业务需求。用户可能通过自然语言描述查找记忆（语义搜索），也可能通过标签和时间范围过滤（结构化查询），还可能通过关系网络探索相关记忆（图遍历）。系统需要提供灵活的混合检索能力。

**可插拔**: 添加新存储后端不需要改 API

这是架构演化的关键。当企业需要引入新的存储技术（如新版本向量库或自研存储系统）时，应该只需要实现新的后端适配器，而不需要修改任何业务逻辑或接口契约。

**与现有架构兼容**: AgentCenter 是 Java Spring Boot 3.4.5 + MyBatis + SQLite/PG

记忆数据层必须无缝集成到现有技术栈中，利用已有的数据库连接池、事务管理、Flyway 迁移等基础设施，而不是另起炉灶。这意味着需要遵循现有的 DDD 分层结构和编码规范。

---

## 2. 现有框架的存储抽象分析

当前业界主流 Agent 记忆框架在存储抽象层面各有特色，理解其设计权衡对 AgentCenter 的架构决策具有重要参考价值。

| 框架 | 抽象方式 | 可切换后端 | 设计理念 |
|------|---------|-----------|---------|
| LangChain | VectorStore + BaseStore + Retriever 接口 | 40+ 向量后端 | 模块化组合 |
| LlamaIndex | StorageContext (vector_store + doc_store + index_store) | 80+ 集成 | 明确的存储层分离 |
| Graphiti (Zep) | Driver 抽象 | Neo4j, FalkorDB, Kuzu, Neptune | 图后端可插拔 |
| Mem0 | MemoryClient (add/search) | Qdrant, Pinecone, Chroma | 极简 2-API |
| OpenViking | viking:// 虚拟文件系统 | LanceDB, VikingDB, PG | 文件系统范式 |
| total-agent-memory | 4 运行模式 (ultrafast/fast/balanced/deep) | SQLite + Chroma + FTS5 | 性能分级 |

**LangChain** 采用了经典的模块化设计，通过 VectorStore 接口抽象向量存储，通过 BaseStore 抽象键值存储，通过 Retriever 接口抽象检索逻辑。这种设计的优势在于组合灵活，开发者可以根据需求自由搭配不同组件。缺点是接口层次较多，学习成本相对较高，且不同接口之间缺乏统一的事务语义。

**LlamaIndex** 在存储抽象上更加明确，将存储分为 vector_store（向量索引）、doc_store（文档存储）和 index_store（索引存储）三个独立组件。这种分离设计使得每个组件可以独立演进和优化，但也带来了数据一致性的挑战——三个存储之间需要额外机制保证同步。

**Graphiti** 是 Zep 推出的图结构记忆框架，其核心贡献在于将记忆建模为时间线图结构（Timeline Graph），支持基于 episodes 的事实追踪和基于 entities 的实体消歧。Driver 抽象层支持多种图数据库，这是一个值得借鉴的设计思路。

**Mem0** 代表了另一种设计哲学——极简主义。通过 MemoryClient 的 add 和 search 两个核心 API 覆盖大多数使用场景，复杂性隐藏在客户端内部。这种设计适合快速上手，但对复杂查询场景的支持力度不足。

**OpenViking** 提出了虚拟文件系统范式，将记忆操作映射为文件系统操作（viking:// protocol）。这种抽象在概念上优雅，但在企业级应用中缺乏足够的表达能力来描述复杂的记忆关系。

**total-agent-memory** 的性能分级设计非常有启发性。它定义了 ultrafast、fast、balanced、deep 四种运行模式，分别对应不同的存储组合和检索策略。这一设计考虑了边缘设备到数据中心的多种部署场景，值得 AgentCenter 在演进路线设计中参考。

---

## 3. 企业级数据架构模式参考

企业级记忆系统设计需要借鉴成熟的数据架构模式。这些模式经过大量生产环境验证，能够帮助我们在设计阶段规避潜在风险。

| 模式 | 适用场景 | 代表系统 |
|------|---------|---------|
| CQRS | 读写分离，写入优化 vs 读取优化 | Zep (ingestion vs retrieval) |
| Repository Pattern | 存储后端可替换 | LangChain, LlamaIndex |
| Event Sourcing | 事实变更追踪 | Graphiti (episodes → entities) |
| Tiered Storage | 热数据→温数据→冷数据 | Letta (core → recall → archival) |
| Virtual Filesystem | 统一访问异构存储 | OpenViking |

**CQRS (Command Query Responsibility Segregation)** 模式将写入操作和读取操作分离到不同的模型中。Zep 框架在这一模式下设计了 ingestion pipeline（负责快速写入和预处理）和 retrieval pipeline（负责优化检索性能）。对于记忆系统而言，写入通常是批量预处理操作，而读取是实时交互，这一模式能够显著提升系统整体吞吐量。

**Repository Pattern** 是领域驱动设计（DDD）的核心模式之一。它在领域层和基础设施层之间建立一个抽象接口，使得领域逻辑完全不依赖具体的存储实现。LangChain 和 LlamaIndex 都广泛采用了这一模式，其优势在于支持运行时切换存储后端以及简化单元测试（通过 mock repository）。

**Event Sourcing** 模式将状态变更建模为不可变的事件序列。Graphiti 使用这一模式追踪记忆的演变历史，从 episodes（事件片段）推导 entities（实体）和 facts（事实）。这种设计的查询能力非常强大，能够回答"某个事实是什么时候确立的"以及"某个认知是如何演化的"等问题。代价是系统复杂度较高，需要额外的事件回放机制。

**Tiered Storage** 模式根据数据的访问频率和时效性将数据分配到不同层级的存储中。Letta 框架将记忆分为 core memory（热数据，常驻内存）、recall memory（温数据，支持快速检索）和 archival memory（冷数据，压缩归档）。这一模式对于处理海量记忆数据的企业场景至关重要，能够在成本和性能之间取得平衡。

**Virtual Filesystem** 模式通过统一的路径抽象屏蔽底层存储差异。OpenViking 的 viking:// 协议将不同的存储后端映射为统一的文件系统视图。这一理念在概念上非常优雅，但在处理复杂关系（如时间范围查询、版本冲突）时显得表达能力不足。

---

## 4. AgentCenter 记忆数据层设计

### 4.1 核心接口定义

以下是 AgentCenter 记忆数据层的核心接口设计。接口设计遵循以下原则：语义清晰、职责单一、扩展性强。接口体系分为三层：领域模型（domain model）、查询模型（query model）和存储接口（storage interface）。

```java
// 记忆类型枚举
public enum MemoryType {
    FACT,        // 事实: "供应商从A换成了B"
    EVENT,       // 事件: "2026-05-23 完成了设计评审"
    PROCEDURE,   // 过程: "如何复现Bug #123"
    PREFERENCE,  // 偏好: "用户偏好邮件通知"
    RULE         // 规则: "超过1000元需审批"
}
```

MemoryType 枚举定义了记忆的基本分类。FACT 类型代表客观事实陈述，具有明确的真值属性；EVENT 类型代表有时间标记的事件记录，强调时序性；PROCEDURE 类型代表操作步骤和流程知识，强调可重复性；PREFERENCE 类型代表用户偏好设置，强调可变性；RULE 类型代表业务规则和约束，强调强制性和版本管理。

```java
// 时序区间
public record TemporalInterval(
    Instant validFrom,
    Instant validUntil,   // null = 当前仍有效
    Instant createdAt,
    Instant updatedAt
) {}
```

TemporalInterval 实现了 bi-temporal 数据模型。validFrom 和 validUntil 定义了事实的有效时间窗（valid time），createdAt 和 updatedAt 定义了系统的事务时间窗（transaction time）。这种设计支持两类时间查询：as-of 查询（"截至某个时间点，该事实是什么状态"）和范围查询（"在某段时间内，哪些事实是有效的"）。

```java
// 记忆实体
public record Memory(
    String id,              // ULID
    MemoryType type,
    String content,
    float[] embedding,      // 可选
    Map<String, Object> metadata,
    TemporalInterval temporal,
    List<MemoryRelation> relations,
    int version,
    String source           // 来源: 会话/文档/人工
) {}
```

Memory 是系统的核心聚合根（Aggregate Root）。id 使用 ULID（Universally Unique Lexicographically Sortable Identifier）确保分布式环境下的唯一性和可排序性。content 是记忆的文本内容，embedding 是可选的向量表示（用于语义检索）。metadata 用于存储类型特定的扩展属性。relations 建模记忆之间的图关系。version 支持乐观锁并发控制。source 追踪记忆来源，便于溯源和质量控制。

```java
// 记忆关系
public record MemoryRelation(
    String targetId,
    String relationType,    // DEPENDS_ON, CONTRADICTS, SUPERSEDES, RELATES_TO
    Map<String, Object> properties
) {}
```

MemoryRelation 定义了记忆之间的关系类型。DEPENDS_ON 表示依赖关系（如某个过程依赖某个事实）；CONTRADICTS 表示矛盾关系（如两条相互冲突的事实）；SUPERSEDES 表示替代关系（如新版本规则替代旧版本规则）；RELATES_TO 表示一般性关联。properties 允许为关系附加额外属性，如置信度、来源说明等。

```java
// 搜索查询
public record MemorySearchQuery(
    String text,                    // 自然语言查询
    float[] embedding,              // 可选: 预计算的 embedding
    List<MemoryType> types,         // 过滤类型
    TemporalInterval temporalFilter, // 时间范围
    String scope,                   // 作用域: user/project/global
    int limit,
    float hybridAlpha               // 0=关键词, 0.5=混合, 1=语义
) {}
```

MemorySearchQuery 支持灵活的混合检索。text 和 embedding 分别对应关键词检索和语义检索。types 支持类型过滤。temporalFilter 支持时间范围查询。scope 定义查询的作用域边界。hybridAlpha 参数控制关键词和语义检索的权重比例——0 表示纯关键词模式，1 表示纯语义模式，0.5 表示等权重混合模式。

```java
// 核心存储接口
public interface MemoryStore {
    
    // === 写入 ===
    String add(Memory memory);
    List<String> addBatch(List<Memory> memories);
    
    // === 读取 ===
    Optional<Memory> get(String memoryId);
    List<Memory> search(MemorySearchQuery query);
    
    // === 图遍历 ===
    List<Memory> traverse(String rootId, String relationType, int depth);
    
    // === 更新 ===
    void update(String memoryId, Memory memory);
    void expire(String memoryId, Instant validUntil);
    void addRelation(String sourceId, MemoryRelation relation);
    
    // === 生命周期 ===
    int consolidate(String scope);   // 整合相似记忆
    int archive(Instant before);     // 归档旧记忆
    int purgeExpired();              // 清理失效记忆
    
    // === 统计 ===
    MemoryStats getStats(String scope);
}
```

MemoryStore 接口定义了记忆存储的核心操作集。写入操作返回 ULID 标识符，支持单条和批量两种模式。读取操作支持精确获取和条件搜索。图遍历操作支持沿特定关系类型和深度进行探索。更新操作包含整体更新、时效更新和关系追加三种模式。生命周期操作支持记忆的自动整合、归档和清理。统计操作提供作用域级别的聚合信息。

### 4.2 存储后端适配器

为了实现存储后端可插拔设计，系统定义了三个后端接口。不同后端实现负责将通用操作翻译为特定存储引擎的原生能力。

```java
// 向量存储后端接口
public interface VectorBackend {
    void upsert(String id, float[] embedding, Map<String, Object> metadata);
    List<String> search(float[] query, int limit);
    void delete(String id);
}
```

VectorBackend 抽象了向量存储的核心能力。upsert 操作同时支持插入和更新，简化了业务逻辑。search 操作返回标识符列表，由上层负责反查完整记忆实体。这种设计避免了向量后端和关系后端之间的数据耦合。

```java
// 图存储后端接口
public interface GraphBackend {
    void addNode(String id, String type, Map<String, Object> properties);
    void addEdge(String from, String to, String type, Map<String, Object> properties);
    List<String> traverse(String rootId, String edgeType, int depth);
    List<String> query(String graphQuery);  // Cypher/GQL
}
```

GraphBackend 抽象了图数据库的核心能力。addNode 和 addEdge 分别处理节点和边的创建。traverse 操作支持沿特定边类型和深度进行图遍历。query 操作接受原生图查询语言（如 Cypher 或 GQL），支持复杂的图模式匹配。

```java
// 关系型存储后端接口
public interface RelationalBackend {
    void insert(String table, Map<String, Object> record);
    Optional<Map<String, Object>> findById(String table, String id);
    List<Map<String, Object>> query(String sql, Object... params);
}
```

RelationalBackend 抽象了关系型数据库的核心能力。这是三个后端接口中最基础的一个，提供了 insert、findById 和原生 SQL query 三个操作。高级查询能力（如全文搜索、时间范围查询）通过 query 方法传入原生 SQL 实现，保持最大灵活性。

### 4.3 混合存储协调器

HybridMemoryStore 是整个存储架构的核心组件。它协调多个后端的工作，提供单一 MemoryStore 接口，同时管理后端之间的数据同步和查询融合。

```java
public class HybridMemoryStore implements MemoryStore {
    
    private final RelationalBackend relational;   // 主存储 (SQLite/PG)
    private final VectorBackend vector;           // 向量索引 (可选)
    private final GraphBackend graph;             // 图存储 (可选)
    private final EventBus eventBus;              // 事件总线
    
    @Override
    public String add(Memory memory) {
        // 1. 写入主存储
        String id = relational.insert("memories", toRecord(memory));
        
        // 2. 异步索引向量
        if (vector != null && memory.embedding() != null) {
            eventBus.publish(new VectorIndexEvent(id, memory.embedding()));
        }
        
        // 3. 异步更新图
        if (graph != null) {
            eventBus.publish(new GraphUpdateEvent(id, memory.type(), memory.relations()));
        }
        
        return id;
    }
    
    @Override
    public List<Memory> search(MemorySearchQuery query) {
        List<String> candidateIds = new ArrayList<>();
        
        // 多路并行检索
        if (query.text() != null && vector != null) {
            candidateIds.addAll(vector.search(embed(query.text()), query.limit()));
        }
        if (query.text() != null) {
            candidateIds.addAll(relational.fullTextSearch(query.text(), query.limit()));
        }
        
        // 合并去重 + 重排
        return rerank(candidateIds, query);
    }
}
```

HybridMemoryStore 的设计体现了几个关键原则。首先是主存储优先——RelationalBackend 是必选组件，存储所有记忆的完整数据，其他后端是可选组件，仅用于增强特定检索能力。其次是异步索引——向量索引和图更新通过 EventBus 异步执行，避免阻塞主写入路径。第三是查询融合——多路检索的结果通过融合算法合并，权重由 hybridAlpha 参数控制。

---

## 5. 与 AgentCenter 现有架构集成

### 5.1 现有技术栈分析

AgentCenter 当前基于以下技术栈构建:

- **运行时框架**: Java Spring Boot 3.4.5
- **持久层**: MyBatis + SQLite (开发环境) / PostgreSQL (生产环境)
- **架构模式**: DDD (Domain-Driven Design) 分层
- **数据模型**: 15 张核心表 (work_item, workflow, agent_session, agent_message, artifact 等)

DDD 分层结构将代码划分为 Controller 层（接口适配）、Application 层（业务编排）、Domain 层（领域逻辑）和 Infrastructure 层（技术实现）。记忆数据层需要遵循这一分层规范，同时与现有模块共享数据库连接和事务管理基础设施。

### 5.2 集成方案

新增模块按照 DDD 分层结构组织如下:

**Domain 层**: `com.agentcenter.bridge.domain.memory/`

这一层包含领域模型和仓储接口。领域模型包括 Memory.java, MemoryType.java, MemoryRelation.java, TemporalInterval.java。仓储接口定义 MemoryRepository.java（对应 Infrastructure 层的实现）。

```java
// Domain 层接口
public interface MemoryRepository {
    String save(Memory memory);
    Optional<Memory> findById(String id);
    List<Memory> search(MemorySearchQuery query);
    List<Memory> traverse(String rootId, String relationType, int depth);
    void update(Memory memory);
    void expire(String id, Instant validUntil);
    void addRelation(String sourceId, MemoryRelation relation);
    MemoryStats getStats(String scope, String scopeId);
}
```

**Infrastructure 层**: `com.agentcenter.bridge.infrastructure.persistence.memory/`

这一层包含具体的存储实现。SQLiteMemoryRepository.java 是 Phase 1 的 MVP 实现，基于 MyBatis 操作 SQLite。PostgreSQLMemoryRepository.java 是 Phase 2/3 的实现，利用 PostgreSQL 的 pgvector 扩展提供向量检索能力。

**Application 层**: `com.agentcenter.bridge.application.memory/`

这一层包含业务编排服务。MemoryService.java 协调多个仓储操作和领域服务，提供面向业务用例的高层 API。

### 5.3 数据库迁移

使用 Flyway 管理数据库迁移。新增迁移文件 `V24__create_memory_tables.sql`。

### 5.4 SQLite Schema 设计

以下是 Phase 1 阶段的 SQLite Schema 设计，参考了 total-agent-memory 和 Cortex 的设计经验。

```sql
CREATE TABLE memory (
    id TEXT PRIMARY KEY,              -- ULID
    type TEXT NOT NULL,               -- FACT/EVENT/PROCEDURE/PREFERENCE/RULE
    content TEXT NOT NULL,
    scope TEXT NOT NULL DEFAULT 'project', -- user/project/global
    scope_id TEXT NOT NULL,           -- userId or projectId
    source TEXT,                      -- session/document/manual
    importance REAL DEFAULT 0.5,
    confidence REAL DEFAULT 1.0,
    valid_from TEXT NOT NULL,
    valid_until TEXT,                 -- null = 当前有效
    superseded_by TEXT REFERENCES memory(id),
    embedding BLOB,                   -- float32 packed (可选)
    metadata TEXT DEFAULT '{}',       -- JSON
    access_count INTEGER DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE memory_relation (
    id TEXT PRIMARY KEY,
    source_id TEXT NOT NULL REFERENCES memory(id),
    target_id TEXT NOT NULL REFERENCES memory(id),
    relation_type TEXT NOT NULL,      -- DEPENDS_ON/CONTRADICTS/SUPERSEDES/RELATES_TO
    properties TEXT DEFAULT '{}',
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- FTS5 全文索引
CREATE VIRTUAL TABLE memory_fts USING fts5(content, content=memory, content_rowid=rowid, tokenize='unicode61');

-- 常用查询索引
CREATE INDEX idx_memory_type ON memory(type);
CREATE INDEX idx_memory_scope ON memory(scope, scope_id);
CREATE INDEX idx_memory_valid ON memory(valid_from, valid_until);
CREATE INDEX idx_memory_importance ON memory(importance DESC);
```

Schema 设计的关键决策说明：

**memory 表**: 使用 TEXT 类型的 id 存储 ULID，确保分布式环境下的唯一性和时间可排序性。scope 和 scope_id 字段实现多租户隔离。importance 和 confidence 字段支持记忆重要性排序和置信度评估。superseded_by 字段支持事实的版本更替。embedding 以 BLOB 存储压缩的 float32 向量。

**memory_relation 表**: 显式建模记忆之间的关系，支持图遍历查询。relation_type 使用字符串枚举而非外键约束，保留扩展灵活性。

**memory_fts 虚拟表**: 使用 SQLite 的 FTS5 模块提供全文搜索能力。tokenize 配置为 unicode61，支持 Unicode 字符的正确分词。

---

## 6. API 设计

记忆数据层通过 RESTful API 对外提供服务。API 设计遵循 AgentCenter 现有的接口规范，使用 JSON 作为请求和响应格式。

```java
@RestController
@RequestMapping("/api/memories")
public class MemoryController {
    
    @PostMapping
    Memory addMemory(@RequestBody AddMemoryRequest request);
    
    @GetMapping("/{id}")
    Memory getMemory(@PathVariable String id);
    
    @PostMapping("/search")
    List<Memory> searchMemories(@RequestBody MemorySearchRequest request);
    
    @PostMapping("/{id}/expire")
    void expireMemory(@PathVariable String id, @RequestBody ExpireRequest request);
    
    @GetMapping("/stats")
    MemoryStats getStats(@RequestParam String scope, @RequestParam String scopeId);
}
```

API 端点说明：

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /api/memories | 创建新记忆 |
| GET | /api/memories/{id} | 根据 ID 获取记忆详情 |
| POST | /api/memories/search | 执行混合搜索 |
| POST | /api/memories/{id}/expire | 设置记忆失效时间 |
| GET | /api/memories/stats | 获取统计信息 |

请求和响应数据结构定义：

```java
// 请求: 添加记忆
public record AddMemoryRequest(
    MemoryType type,
    String content,
    Map<String, Object> metadata,
    String scope,
    String scopeId,
    String source
) {}

// 请求: 搜索记忆
public record MemorySearchRequest(
    String text,
    List<MemoryType> types,
    Instant temporalFrom,
    Instant temporalUntil,
    String scope,
    String scopeId,
    int limit,
    float hybridAlpha
) {}

// 请求: 设置失效时间
public record ExpireRequest(
    Instant validUntil
) {}

// 响应: 记忆统计
public record MemoryStats(
    long totalCount,
    Map<MemoryType, Long> countByType,
    Instant oldestMemory,
    Instant newestMemory
) {}
```

---

## 7. 演进路线

记忆数据层的建设采用渐进式演进策略，分阶段交付能力。每个阶段都有明确的交付目标和技术选型，确保在每个迭代都能获得可用的功能。

| 阶段 | 存储 | 检索能力 | 参考框架 |
|------|------|---------|---------|
| **Phase 1 (MVP)** | SQLite + FTS5 | 关键词搜索 + 时间过滤 | total-agent-memory |
| **Phase 2** | SQLite + FTS5 + 向量索引 (Chroma) | 混合检索 (语义+关键词) | agentmemory |
| **Phase 3** | PostgreSQL + pgvector + pg_trgm | 5 信号融合 + 热值衰减 | Cortex |
| **Phase 4** | PostgreSQL + pgvector + Apache AGE (图扩展) | 图遍历 + bi-temporal | Zep/Graphiti |

**Phase 1 (MVP)**: 基于 SQLite + FTS5 实现基础记忆存储和关键词检索。这一阶段目标是快速验证架构设计，建立核心数据模型和接口契约。FTS5 提供基础的全文搜索能力，满足初步的检索需求。

**Phase 2**: 引入 Chroma 作为独立的向量索引服务，实现语义检索能力。这一阶段的关键挑战是多存储之间的数据同步——Chroma 作为独立服务，需要确保与 SQLite 的数据一致性。混合检索能力在这一阶段解锁，hybridAlpha 参数开始发挥作用。

**Phase 3**: 迁移至 PostgreSQL + pgvector，利用数据库原生向量能力简化部署。同时引入 pg_trgm 扩展增强模糊匹配能力。这一阶段增加多信号融合（语义相似度、关键词匹配、时间衰减、重要性评分、访问频率）和热值衰减机制，实现更智能的记忆排序。

**Phase 4**: 引入 Apache AGE 图扩展，实现原生图存储和遍历能力。同时完善 bi-temporal 查询支持。这一阶段的目标是支持复杂的记忆关系网络查询和完整的历史追溯能力。

---

## 8. 关键设计决策

以下是记忆数据层设计过程中的关键架构决策，以及每个决策背后的核心考量。

| 决策 | 选择 | 理由 |
|------|------|------|
| 统一 API vs 分类型 API | 统一 API + 类型扩展 | 降低学习成本，满足高级需求 |
| 存储无关查询 vs 后端特定查询 | 分层：统一查询语言 → Query Planner → 后端翻译 | 兼顾可移植性和性能 |
| Pull vs Push | Pull 为主，SSE Subscription 为扩展 | 查询是主要模式，订阅是增强 |
| 同步 vs 异步 | 异步索引，同步查询 | 查询低延迟，索引可容忍延迟 |
| 一致性策略 | 最终一致 (写入后向量/图异步更新) | 记忆系统可容忍短暂不一致 |

**统一 API vs 分类型 API**: 初期考虑过按记忆类型分别设计 API（FACT API, EVENT API 等），但这会导致调用方需要理解多种接口语义，增加集成复杂度。最终选择统一 API + 类型扩展的设计——单一接口接受 Memory 对象，类型特定的行为通过 metadata 和类型检查实现。这种设计既保持了接口的简洁性，又通过扩展字段保留了类型特定的表达能力。

**存储无关查询 vs 后端特定查询**: 完全的存储无关查询（如 JPQL）虽然提供了最佳的可移植性，但无法利用特定后端的优化能力（如 pgvector 的余弦相似度）。折中方案是采用分层架构——上层使用统一的 MemorySearchQuery 模型，经过 Query Planner 分析后翻译为各后端的原生查询语言。这一设计在保持接口稳定的同时，允许每个后端发挥最大性能。

**Pull vs Push**: 记忆系统的主要使用模式是按需查询（Pull），而非持续监控（Push）。在企业工作台场景中，用户主动发起查询的频率远高于持续监听场景。因此核心接口采用 Pull 模式，SSE 订阅作为可选扩展，用于实时性要求较高的场景（如协作编辑）。

**同步 vs 异步**: 写入操作的核心路径是存储到 RelationalBackend，这是同步完成的以确保数据一致性。向量索引和图更新通过 EventBus 异步执行，不阻塞主写入路径。这是因为查询延迟直接影响用户体验，而向量索引的稍许延迟在可接受范围内。

**一致性策略**: 记忆系统天然具有一定的容错性——短暂的不一致（如新写入的记忆尚未被向量索引完成）不会导致业务错误。基于这一特性，系统采用最终一致性策略，允许向量索引和图更新有短暂的滞后。这种设计显著提升了写入吞吐量，同时通过后台重试机制保证最终一致性。

---

## 附录 A: 术语表

| 术语 | 定义 |
|------|------|
| ULID | Universally Unique Lexicographically Sortable Identifier，分布式唯一 ID 生成算法 |
| bi-temporal | 双时态数据模型，同时追踪有效时间（valid time）和事务时间（transaction time） |
| FTS5 | Full-Text Search 5，SQLite 的全文搜索模块 |
| embedding | 向量化表示，将文本映射为高维稠密向量 |
| hybrid search | 混合搜索，结合多种检索策略（如关键词+语义）提升召回率 |

## 附录 B: 参考资料

| 框架/系统 | 参考价值 |
|-----------|---------|
| LangChain VectorStore | 存储抽象设计 |
| LlamaIndex StorageContext | 多存储协调模式 |
| Graphiti (Zep) | 图结构记忆建模 |
| Mem0 | 极简 API 设计 |
| total-agent-memory | SQLite + FTS5 MVP 方案 |
| Cortex | 多信号融合排序 |
| Letta | Tiered Storage 分层策略 |
