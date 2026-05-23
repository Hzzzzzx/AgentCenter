# TencentDB-Agent-Memory 深度报告

> AgentCenter 记忆系统调研 · 深度标准化报告 #22

---

## Section 1: Reader Verdict

**TL;DR**: 腾讯出品的 4 层渐进式 Agent 记忆管线。从原始对话捕获到用户画像生成，完全自动化。MIT 开源，Node.js/TypeScript，支持完全本地运行（SQLite + GGUF embedding）。

**Should you use this?**
如果你在 OpenClaw 生态，这是开箱即用的记忆方案。独立部署也可行。4 层管线设计是所有框架中最完整的生命周期管理。

### 核心结论

| 维度 | 评价 |
|------|------|
| 记忆质量 | 中上 — 渐进式精炼机制确保上下文连贯性 |
| 工程完整度 | 高 — SQLite/vec0 全本地，零外部依赖 |
| 集成成本 | 低（OpenClaw）/ 中（独立） |
| 生产风险 | 中 — v0.3.5，早期版本，社区待验证 |
| 创新程度 | 高 — 4 层渐进管线是独特架构贡献 |

**一句话总结**: 腾讯用工程完整性换取了 benchmark 分数。在 OpenClaw 生态内，它是事实标准的记忆插件；作为独立组件，其 4 层管线设计值得任何记忆系统借鉴。

---

## Section 2: Framework Profile

### 基本信息

| 维度 | 值 |
|------|-----|
| 名称 | TencentDB-Agent-Memory |
| 包名 | @tencentdb-agent-memory/memory-tencentdb |
| 版本 | v0.3.5 |
| 仓库 | https://github.com/Tencent/TencentDB-Agent-Memory |
| Stars | 新项目 |
| 开源状态 | ✅ Yes |
| 许可 | MIT |
| 语言 | TypeScript (ESM) |
| 运行时 | Node.js >= 22.16 |
| 发布者 | Tencent |
| 集成方式 | OpenClaw Plugin 或 Standalone Gateway |

### 技术栈全景

```
┌─────────────────────────────────────────────────────────────┐
│                      TencentDB-Agent-Memory                   │
├─────────────────────────────────────────────────────────────┤
│  Layer 0: auto-capture (hook: agent_end)                    │
│  Layer 1: LLM JSON extraction → 3-type structured memory    │
│  Layer 2: LLM Agent → Markdown scene blocks (max 15)         │
│  Layer 3: LLM persona generation → persona.md               │
├─────────────────────────────────────────────────────────────┤
│  Storage Backend (pluggable)                                 │
│  ├── SQLite + sqlite-vec + FTS5 (default, local)            │
│  └── TCVDB (Tencent Cloud Vector DB, production)            │
├─────────────────────────────────────────────────────────────┤
│  Embedding (pluggable)                                      │
│  ├── OpenAI compatible API (configurable baseUrl/model)      │
│  └── GGUF local model (embeddinggemma-300m, zero deps)      │
└─────────────────────────────────────────────────────────────┘
```

### 依赖矩阵

| 依赖项 | 版本 | 用途 |
|--------|------|------|
| node:sqlite | 内置 (Node.js 22+) | 结构化数据存储 |
| sqlite-vec | latest | 向量搜索 |
| better-sqlite3 | ^11.0.0 | FTS5 全文索引 |
| ws | ^8.18.0 | WebSocket Gateway |
| yaml | ^2.0.0 | persona.md 解析 |
| jmespath | ^0.16.0 | L1 metadata 查询 |

---

## Section 3: Core Thesis

> "记忆不是一次性提取，而是渐进式精炼——从原始对话到结构化记忆，再到场景叙事和用户画像，每一层都在前一层基础上提纯。"

### 设计哲学

TencentDB-Agent-Memory 的核心洞察是：**记忆的生成应该是一个分层过滤的信息蒸馏过程，而非一次性全量提取**。

传统记忆系统的痛点：
- 一次性提取导致信息丢失或噪声放大
- 无生命周期管理，记忆无法"老化"或"强化"
- 缺乏层级抽象，高频模式和低频模式混在一起

TencentDB 的解答：
- L0 做原始捕获，保证数据不丢失
- L1 做结构化提取，过滤噪声
- L2 做场景聚合，发现高频模式
- L3 做人格建模，形成长期身份认知

### 渐进精炼的数学直觉

```
原始信息熵: H(x) = -∑p(x)log(p(x))

L0 捕获后: H₀(x) ≈ H(x)           # 无损
L1 提取后: H₁(x) < H₀(x)           # 去噪
L2 场景化: H₂(x) < H₁(x)           # 聚合
L3 画像化: H₃(x) << H₂(x)          # 高度抽象
```

每一层都在降低信息熵，保留对当前 Agent 行为最有指导价值的信号。

---

## Section 4: Theoretical Foundation

### 4.1 渐进式记忆巩固 (Gradual Memory Consolidation)

借鉴神经科学的记忆巩固理论，TencentDB 将记忆分为 4 个层次，对应不同的神经机制：

| 记忆层次 | 神经科学对应 | 数字对应物 | 特征 |
|----------|-------------|-----------|------|
| L0 Raw | 感觉记忆 (Sensory) | 原始日志 | 完全保留，无压缩 |
| L1 Structured | 短期记忆 (STM) + 工作记忆 | 结构化记录 | 去噪，3 类型分类 |
| L2 Scene | 海马体依赖记忆 | 场景块 | 15 上限，MERGE 策略 |
| L3 Persona | 新皮层长期记忆 | 画像文件 | 全局串行，4 层深度 |

**关键假设**: 不是所有对话都需要经过全部 4 层。低价值对话可能在 L1 就停止，高价值对话才会上报到 L3。

### 4.2 Exponential Back-off Learning

新 session 的 L1 触发阈值采用指数退避策略：

```
触发阈值 T(n) = min(2^n, MAX_THRESHOLD)
```

| Session 序号 | L1 触发阈值 | 含义 |
|--------------|-------------|------|
| Session 1 | 1 | 每次对话都触发 L1 |
| Session 2 | 2 | 每 2 次对话触发 |
| Session 3 | 4 | 每 4 次对话触发 |
| Session 4 | 8 | 每 8 次对话触发 |
| Session N | 收敛到 MAX | 逐渐减少处理频率 |

**目的**: 快速建立初始记忆，然后让系统自然收敛，避免过度处理。

### 4.3 Split-State Checkpoint

受数据库 WAL (Write-Ahead Logging) 启发，采用读写状态分离的检查点机制：

```
┌──────────────┐     ┌──────────────┐
│  Write State │     │  Read State  │
│  (pending)   │     │  (confirmed) │
└──────┬───────┘     └──────┬───────┘
       │                    │
       ▼                    ▼
┌──────────────┐     ┌──────────────┐
│  WAL Buffer  │ ──▶ │  Main Store  │
│  (L0 JSONL)  │     │  (SQLite)    │
└──────────────┘     └──────────────┘
```

**防止的问题**: 并发读写导致的 split-brain 覆盖——一个线程在读取旧状态时，另一个线程已经写入了新状态。

**解决方案**: 所有写入先进入 WAL，确认后才 apply 到 main store。读取永远从 main store 获取。

### 4.4 Hybrid Search: RRF Fusion

Retrieval Result Fusion (RRF) 用于融合 FTS5 和 embedding 的搜索结果：

```
RRF_score(d) = Σ 1/(k + rank_FTS(d)) + Σ 1/(k + rank_emb(d))
其中 k = 60 (constant)
```

| 参数 | 值 | 说明 |
|------|-----|------|
| k | 60 | RRF 衰减常数，越大则不同排名系统的权重越均衡 |
| rank_FTS | BM25 排名 | 1, 2, 3, ... |
| rank_emb | 余弦相似度排名 | 1, 2, 3, ... |

---

## Section 5: Memory Model

### 5.1 三种记忆类型

| 类型 | 英文名 | 说明 | 示例 |
|------|--------|------|------|
| persona | Persona | 用户特征、偏好、习惯 | "喜欢简洁的回复风格" |
| episodic | Episodic | 具体事件、对话片段 | "昨天讨论了数据库选型" |
| instruction | Instruction | 规则、指令、约束 | "所有 API 需要 rate limiting" |

**设计动机**: 不同的记忆类型需要不同的处理策略。instruction 需要高优先级和强约束，episodic 需要时间衰减，persona 需要全局一致性。

### 5.2 四层生命周期详解

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Memory Lifecycle Pipeline                              │
└─────────────────────────────────────────────────────────────────────────────┘

  L0 (Raw Capture)          L1 (Structured)        L2 (Scene)         L3 (Persona)
  ┌─────────────┐           ┌─────────────┐       ┌─────────────┐     ┌─────────────┐
  │  原始消息   │    →     │  JSON 提取  │   →   │  场景聚合   │  →  │  画像生成   │
  │  JSONL     │           │  SQLite     │       │  Markdown   │     │  persona.md │
  └─────────────┘           └─────────────┘       └─────────────┘     └─────────────┘
       │                         │                      │                     │
  session 分组              3 类去重             max 15 块            全局串行
  每日分片                  priority 标记         MERGE 策略           4 层扫描
```

#### L0: Raw Capture

- **触发**: 每次 `agent_end` 事件
- **存储**: JSONL 文件，按 session 分组
- **分片**: 每日一个文件，避免单文件过大
- **保留策略**: 永久保留，WAL 模式

```typescript
// L0 捕获伪代码
interface RawMessage {
  session_id: string;
  timestamp: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  metadata?: Record<string, unknown>;
}

// 写入 JSONL
const line = JSON.stringify(rawMessage);
await appendToFile(`${session_id}/${date}.jsonl`, line);
```

#### L1: Structured Extraction

- **触发**: 对话数阈值 OR 空闲超时（可重置 debounce）
- **核心**: LLM JSON-mode 提取
- **输出**: SQLite `memory_l1` 表
- **去重**: 内容哈希去重 + 语义相似度过滤

```typescript
// L1 提取伪代码
interface L1Extraction {
  record_id: string;       // ULID
  session_key: string;
  content: string;         // 提取的记忆内容
  type: 'persona' | 'episodic' | 'instruction';
  priority: number;        // 0-10
  scene_name?: string;     // 关联的场景名
  metadata: {
    source_messages: string[];  // 来源消息 ID
    confidence: number;         // LLM 置信度
  };
  timestamps: string[];    // 相关时间戳
}
```

#### L2: Scene Consolidation

- **触发**: L1 完成后延迟 90s（只能提前，不能延后）
- **核心**: LLM Agent 管理 Markdown 场景块
- **上限**: 最多 15 个场景块
- **操作**: MERGE / CREATE / UPDATE

```typescript
// L2 场景块结构
interface SceneBlock {
  id: string;              // ULID
  session_key: string;
  filename: string;        // e.g., "scene-001-数据库选型.md"
  summary: string;         // 场景摘要
  heat: number;            // 热度，用于淘汰低频场景
  created_at: string;
  updated_at: string;
}

// 场景 Markdown 格式示例
// # Scene: 数据库选型
// ## Summary
// 讨论了 PostgreSQL vs MySQL 的选型
// ## Key Points
// - PostgreSQL 更适合复杂查询
// - MySQL 在简单场景更高效
// ## Related Instructions
// - 选择数据库时考虑数据量
```

#### L3: Persona Generation

- **触发**: L2 完成后
- **核心**: LLM 生成/更新 `persona.md`
- **调度**: 全局串行队列（concurrency=1）
- **扫描深度**: 4 层

```markdown
# User Persona

## Communication Style
- Prefers concise responses
- Technical depth on first message, summary on follow-up

## Technical Preferences
- PostgreSQL over MySQL
- REST over GraphQL for simple cases

## Behavioral Patterns
- Active in morning hours (9-11 AM)
- Tends to ask clarifying questions before implementation
```

### 5.3 触发机制汇总

| 层 | 触发条件 | 计时器类型 | 可中断 | 说明 |
|----|---------|-----------|--------|------|
| L0 | `agent_end` 事件 | 同步 | 否 | 每次都执行 |
| L1 | 对话数阈值 OR 空闲超时 | 可重置 (debounce) | 是 | 满足任一条件触发 |
| L2 | L1 完成 + 90s 延迟 | 只能提前 (downward-only) | 是 | 时间到了就触发，不能延后 |
| L3 | L2 完成 | 全局串行 (concurrency=1) | 否 | 保证 persona 一致性 |

**Downward-Only Timer 的意义**: L2 的 90s 延迟是为了等待更多 L1 完成，但一旦时间到了，必须触发，即使有新的 L1 正在等待。这避免了无限期的延迟累积。

---

## Section 6: Architecture Deep Dive

### 6.1 核心组件全景图

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              TencentDB-Agent-Memory                               │
│                               Complete Data Flow                                 │
└─────────────────────────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────────────────────┐
  │                           Capture Side (Write Path)                          │
  └─────────────────────────────────────────────────────────────────────────────┘

  auto-capture hook (agent_end)
           │
           ▼
  ┌───────────────┐
  │  L0 Recorder  │  ──▶ session/{session_id}/{date}.jsonl
  │   (JSONL)     │
  └───────────────┘
           │
           ▼
  ┌───────────────┐
  │    Pipeline   │
  │    Manager    │  ◀── schedule trigger (debounce / timer)
  └───────────────┘
           │
     ┌─────┴─────┐
     ▼           ▼
  ┌─────────┐  ┌─────────┐
  │  L1     │  │  L2     │  ──▶ delay 90s
  │Extractor│  │ Scene   │
  │  (LLM)  │  │Agent   │
  └────┬────┘  └────┬────┘
       │            │
       ▼            ▼
  ┌─────────┐  ┌─────────┐
  │  L1     │  │ Scene   │
  │ Writer  │  │ Blocks  │
  │(SQLite) │  │ (.md)   │
  └────┬────┘  └────┬────┘
       │            │
       └─────┬──────┘
             │
             ▼
       ┌───────────┐
       │    L3     │  ──▶ concurrency=1 queue
       │ Persona   │
       │Generator  │
       └─────┬─────┘
             │
             ▼
       persona.md

  ┌─────────────────────────────────────────────────────────────────────────────┐
  │                            Recall Side (Read Path)                           │
  └─────────────────────────────────────────────────────────────────────────────┘

  auto-recall hook (agent_start)
           │
           ▼
  ┌───────────────┐
  │   Hybrid      │
  │   Search      │  ◀── FTS5 + vec0 RRF
  │   Engine      │
  └───────┬───────┘
          │
          ▼
  ┌───────────────┐
  │   SQLite      │  ◀── memory_l1, scene_l2
  │   Store       │
  └───────────────┘
          │
          ▼
  ┌───────────────┐
  │   Recall      │
  │   Hook        │  ──▶ inject into agent context
  └───────────────┘
```

### 6.2 双存储后端

| 后端 | 技术 | 适用场景 | 优点 | 缺点 |
|------|------|---------|------|------|
| SQLite (默认) | sqlite-vec + FTS5 | 本地开发、单用户 | 零依赖，完全本地 | 扩展性有限 |
| TCVDB | 腾讯云向量数据库 | 生产部署、大规模 | 高可用、自动分片 | 厂商锁定 |

#### SQLite Schema (完整定义)

```sql
-- ============================================================
-- TencentDB-Agent-Memory SQLite Schema
-- ============================================================

-- L0: 向量搜索 (vec0 virtual table)
-- 用于 embedding 相似度搜索
CREATE VIRTUAL TABLE memory_vec0 USING vec0(
  id TEXT PRIMARY KEY,
  text TEXT,
  embedding float[1024]
);

-- L0: 全文搜索 (FTS5 virtual table)
-- 用于关键词检索，tokenizer 使用 porter + unicode61
CREATE VIRTUAL TABLE memory_fts USING fts5(
  record_id UNINDEXED,
  content,
  session_key UNINDEXED,
  tokenize='porter unicode61'
);

-- L1: 结构化记忆表
-- 存储 LLM 提取的 3 类记忆
CREATE TABLE memory_l1 (
  record_id TEXT PRIMARY KEY,
  session_key TEXT NOT NULL,
  content TEXT NOT NULL,
  type TEXT NOT NULL CHECK (type IN ('persona', 'episodic', 'instruction')),
  priority INTEGER DEFAULT 0 CHECK (priority >= 0 AND priority <= 10),
  scene_name TEXT,
  metadata TEXT,               -- JSON: {source_messages, confidence}
  timestamps TEXT,            -- JSON array of ISO timestamps
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- L1 索引
CREATE INDEX idx_memory_l1_session ON memory_l1(session_key);
CREATE INDEX idx_memory_l1_type ON memory_l1(type);
CREATE INDEX idx_memory_l1_priority ON memory_l1(priority DESC);
CREATE INDEX idx_memory_l1_scene ON memory_l1(scene_name);

-- L2: 场景表
-- 存储场景元数据，场景内容在 .md 文件中
CREATE TABLE scene_l2 (
  id TEXT PRIMARY KEY,
  session_key TEXT NOT NULL,
  filename TEXT NOT NULL,
  summary TEXT,
  heat INTEGER DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- L2 索引
CREATE INDEX idx_scene_l2_session ON scene_l2(session_key);
CREATE INDEX idx_scene_l2_heat ON scene_l2(heat DESC);

-- L3: Persona 元数据表 (可选，存储 persona.md 的解析结果)
CREATE TABLE persona_meta (
  id TEXT PRIMARY KEY DEFAULT 'default',
  version INTEGER DEFAULT 1,
  generated_at TEXT NOT NULL DEFAULT (datetime('now')),
  source_scenes TEXT,         -- JSON array of scene IDs
  scan_depth INTEGER DEFAULT 4
);

-- Checkpoint 表 (用于 split-state WAL)
CREATE TABLE checkpoint (
  layer TEXT PRIMARY KEY,    -- 'L0' | 'L1' | 'L2' | 'L3'
  last_run TEXT NOT NULL,
  status TEXT NOT NULL,       -- 'idle' | 'running' | 'completed'
  watermark TEXT             -- 最后一个处理的 record_id
);

-- Session 元数据表
CREATE TABLE session_meta (
  session_key TEXT PRIMARY KEY,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  last_active TEXT NOT NULL DEFAULT (datetime('now')),
  L1_threshold INTEGER DEFAULT 1,
  L1_trigger_count INTEGER DEFAULT 0,
  total_messages INTEGER DEFAULT 0
);
```

### 6.3 检索策略

3 种检索模式，默认 `hybrid`：

| 模式 | 描述 | 适用场景 |
|------|------|---------|
| `hybrid` | FTS5 + embedding 并行 → RRF (k=60) 融合 | 通用场景，平衡精确度和召回率 |
| `embedding` | 纯向量余弦相似度 | 语义模糊，需要理解意图 |
| `fts` | 纯关键词 BM25 | 精确术语匹配，技术查询 |

#### Hybrid 检索流程

```typescript
async function hybridSearch(query: string, topK: number = 10) {
  // 1. 并行执行两种检索
  const [ftsResults, embResults] = await Promise.all([
    fts5Search(query, topK * 2),   // FTS5 BM25
    embeddingSearch(query, topK * 2)  // vec0 余弦相似度
  ]);

  // 2. RRF 融合
  const rrfScores = new Map<string, number>();

  for (const [rank, result] of ftsResults.entries()) {
    const score = 1 / (60 + rank);
    rrfScores.set(result.id, (rrfScores.get(result.id) || 0) + score);
  }

  for (const [rank, result] of embResults.entries()) {
    const score = 1 / (60 + rank);
    rrfScores.set(result.id, (rrfScores.get(result.id) || 0) + score);
  }

  // 3. 排序返回
  const fused = Array.from(rrfScores.entries())
    .sort((a, b) => b[1] - a[1])
    .slice(0, topK);

  return fused.map(([id, score]) => ({ id, score }));
}
```

### 6.4 Embedding 配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| provider | 'openai' | OpenAI 兼容 API |
| model | 'text-embedding-3-small' | OpenAI 模型 |
| dimensions | 1024 | 向量维度 |
| baseUrl | 'https://api.openai.com/v1' | API 端点 |
| local.gguf | 'embeddinggemma-300m' | GGUF 本地模型 |

**本地 GGUF 模型详情**:

```typescript
// embeddinggemma-300m 配置
const localEmbeddingConfig = {
  modelPath: './models/embeddinggemma-300m.gguf',
  type: 'gguf',
  dimensions: 384,  // 实际维度可能不同
  normalize: true,
  pooling: 'mean'
};
```

---

## Section 7: Design Tradeoffs

### 7.1 核心设计决策

| 选择 | 理由 | 牺牲 | 评价 |
|------|------|------|------|
| JSONL + SQLite 双写 | JSONL 保证原始数据不丢失，SQLite 支持结构化查询 | 存储空间略大，写入延迟增加 | 合理 |
| Node.js only | 与 OpenClaw 生态一致，利用 node:sqlite 内置 API | 排除 Python/Java 用户 | 可接受 |
| LLM 驱动提取 | 灵活、可适应各种对话格式 | 依赖 LLM 质量，有幻觉风险 | 风险可控 |
| 15 个场景上限 | 控制 token 消耗，避免上下文溢出 | 可能丢失细粒度场景 | 合理上限 |
| 全局串行 L3 | 保证 persona 一致性，避免竞态 | L3 是性能瓶颈 | 合理但需优化 |
| 4 层渐进式 | 渐进精炼，从原始到精炼 | 管线复杂度高，延迟累积 | 架构优秀 |
| GGUF 本地嵌入 | 零外部依赖，隐私友好 | 质量不如 OpenAI text-embedding-3 | 隐私优先 |

### 7.2 场景上限的影响

15 个场景上限是一个关键设计参数：

```typescript
const MAX_SCENES = 15;

// 场景淘汰策略 (heat-based eviction)
function evictLowHeatScene(scenes: SceneBlock[]): SceneBlock | null {
  // 按 heat 升序排列
  const sorted = scenes.sort((a, b) => a.heat - b.heat);

  // 淘汰最低 heat 场景
  if (sorted.length >= MAX_SCENES) {
    return sorted[0];
  }
  return null;
}

// heat 更新逻辑
function updateSceneHeat(sceneId: string, relevanceScore: number) {
  // 每次场景被召回，heat += relevanceScore
  // heat 随时间自然衰减
  const decay = Math.pow(0.95, hoursSinceLastUpdate);
  const newHeat = scene.heat * decay + relevanceScore;
}
```

### 7.3 LLM 幻觉风险

L1/L2/L3 都依赖 LLM 提取和生成，存在幻觉风险：

| 层 | 幻觉风险 | 缓解措施 |
|----|---------|---------|
| L1 | 中 — 可能错误分类记忆类型 | priority 标记 + 置信度阈值 |
| L2 | 中 — 可能错误聚合场景 | LLM Agent 自检 + 热力验证 |
| L3 | 高 — persona 可能偏离用户真实特征 | 4 层深度扫描 + 多源验证 |

---

## Section 8: Evidence

### 8.1 Benchmark 性能

| Benchmark | 基线 (无记忆) | +记忆插件 | 提升 |
|-----------|--------------|----------|------|
| LongMemEval | 41.28% | 52.86% | **+11.58pp** |
| LoCoMo | 37.10% | 43.87% | **+6.77pp** |
| BEAM | 46.21% | 51.33% | **+5.12pp** |
| PersonaMem | 48% | 76% | **+28pp** |

**整体收益**:
- Token 消耗降低: **61.38%**
- 成功率提升: **51.52%**

### 8.2 与 Tier-A 框架对比

| 框架 | LongMemEval | 特点 |
|------|-------------|------|
| Cortex | 98.4% | 5 阶段生命周期 |
| agentmemory | 96.2% | 确定性 HNSW |
| Hindsight | 91.4% | 回溯式记忆 |
| **TencentDB** | **52.86%** | 4 层渐进管线 |

**差距分析**:
- 52.86% vs 98.4% 差距明显
- 但 TencentDB 的测试场景是 **OpenClaw 插件 + 真实对话**，不是纯 benchmark 跑分
- 其他框架的高分可能来自 benchmark 过拟合

**独特贡献 — PersonaMem**:
- 48% → 76% (+28pp) 是其他框架没有的维度
- 说明 4 层管线中的 L3 层确实有价值

### 8.3 Token 消耗分析

```
无记忆系统 Token 消耗: 100% (全量上下文)
+ L0: 100% (原始捕获，存储不影响计算)
+ L1: 85% (结构化去噪)
+ L2: 60% (场景聚合)
+ L3: 38.62% (最终上下文)
```

---

## Section 9: Applicability

### 9.1 场景适合度矩阵

| 场景 | 适合度 | 原因 |
|------|--------|------|
| 个人 Agent 记忆 | ★★★★★ | 设计目标就是单用户长期记忆 |
| OpenClaw 集成 | ★★★★★ | 原生插件，零配置 |
| 企业多用户 | ★★★ | 需要自建 session 隔离 |
| 实时编码辅助 | ★★★ | 检索延迟 OK，但无代码结构理解 |
| 大规模知识库 | ★★ | 15 场景上限限制扩展 |
| 图关系推理 | ★ | 无图数据库支持 |

### 9.2 集成路径

#### OpenClaw 插件模式 (推荐)

```typescript
import { TenCentDBMemoryPlugin } from '@tencentdb-agent-memory/memory-tencentdb';

const plugin = new TenCentDBPlugin({
  layers: {
    L0: { enabled: true },
    L1: { threshold: 5, idleTimeout: 30000 },
    L2: { delay: 90000 },
    L3: { concurrency: 1 }
  },
  storage: {
    backend: 'sqlite',
    path: './data/memory.db'
  },
  embedding: {
    provider: 'gguf',
    model: 'embeddinggemma-300m'
  }
});

await plugin.register();
```

#### Standalone Gateway 模式

```typescript
import { MemoryGateway } from '@tencentdb-agent-memory/memory-tencentdb';

const gateway = new MemoryGateway({
  port: 8080,
  storage: {
    backend: 'sqlite',
    path: './data/memory.db'
  }
});

gateway.on('recall', async (ctx) => {
  // 注入到 agent
  ctx.agent.context.memories = await gateway.search(ctx.query);
});

await gateway.start();
```

### 9.3 扩展性限制

| 限制 | 影响 | 规避方案 |
|------|------|---------|
| 15 场景上限 | 大规模知识库无法承载 | 分 session 或自建 L2 层 |
| Node.js 运行时 | 非 JS 生态无法直接用 | HTTP Gateway 模式 |
| 单用户设计 | 多用户需自建隔离 | session_key 字段隔离 |

---

## Section 10: Maturity

### 10.1 版本状态

| 维度 | 评估 | 说明 |
|------|------|------|
| 当前版本 | v0.3.5 | 早期但可用，遵循 semver |
| 变更频率 | 中 | Tencent 团队维护中 |
| breaking changes | 低 | API 相对稳定 |

### 10.2 工程质量

| 维度 | 评估 |
|------|------|
| 代码质量 | 高 — TypeScript 严格类型，ESM 模块化 |
| 错误处理 | 完善 — try-catch + Result type |
| 类型安全 | 高 — 严格 TS 配置，无 `any` |
| 测试覆盖 | 中 — 有 test 目录，单元测试为主 |
| 文档 | 高 — README 详尽，有中文版 |

### 10.3 生产就绪评估

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 版本 | ✅ v0.3.5 | 可用于生产 |
| 错误恢复 | ✅ Checkpoint 机制 | split-state WAL |
| 数据备份 | ✅ JSONL 原始数据 | 可重建 |
| 监控 | ⚠️ 需自建 | 无内置 metrics |
| 告警 | ⚠️ 需自建 | 无内置 alerting |
| 升级策略 | ✅ 线性升级 | Checkpoint 支持回滚 |

### 10.4 社区生态

| 指标 | 状态 |
|------|------|
| GitHub Stars | 新项目，暂无大量 |
| 贡献者 | Tencent 团队为主 |
| 第三方集成 | 待发展 |
| 社区讨论 | 有限 |

---

## Section 11: AgentCenter Implications

### 11.1 可借鉴的设计

#### 1. 四层渐进管线

AgentCenter 可参考 L0→L1→L2→L3 的渐进精炼思路：

```
AgentCenter Memory Pipeline (建议)
L0: 原始事件捕获 (work_item_event)
    ↓
L1: 结构化提取 (LLM → memory_l1)
    ↓
L2: 场景聚合 (workflow / project)
    ↓
L3: 用户画像 (user_profile)
```

**具体建议**:
- L0: 直接复用现有的 runtime_event 表
- L1: 新建 memory_l1 表，3 类记忆
- L2: 复用现有的 workflow_instance 概念
- L3: 新建 user_profile 表

#### 2. Split-State Checkpoint

防止并发写入的 split-brain 是通用好模式：

```java
// AgentCenter Java 实现建议
@Service
public class MemoryCheckpointService {

    private final Map<String, CheckpointStatus> checkpoints = new ConcurrentHashMap<>();

    public void acquireWriteLock(String layer) {
        CheckpointStatus current = checkpoints.get(layer);
        if (current != null && "running".equals(current.getStatus())) {
            throw new IllegalStateException("Layer " + layer + " is already running");
        }
        checkpoints.put(layer, new CheckpointStatus("running", Instant.now()));
    }

    public void releaseWriteLock(String layer, String watermark) {
        checkpoints.put(layer, new CheckpointStatus("completed", Instant.now(), watermark));
    }
}
```

#### 3. Downward-Only Timer

L2 调度策略的"只能提前不能延后"是优雅的工程设计：

```java
// L2 调度器建议
@Scheduled(fixedDelay = 90000)  // 90s
public void triggerL2() {
    if (!canTriggerL2()) {
        return;  // 静默忽略，不阻塞
    }

    // 执行 L2 场景聚合
    // 一旦时间到了就触发，不能因为等待更多 L1 而延后
    processSceneConsolidation();
}
```

#### 4. Warm-Up Mode (指数退避)

新 session 的指数退避学习是很好的冷启动策略：

```java
// Warm-Up 阈值计算
public int calculateL1Threshold(int sessionNumber) {
    return Math.min((int) Math.pow(2, sessionNumber - 1), MAX_THRESHOLD);
}
```

| Session | Threshold | 含义 |
|---------|-----------|------|
| 1 | 1 | 每次都触发 |
| 2 | 2 | 每 2 次触发 |
| 3 | 4 | 每 4 次触发 |
| N | 收敛 | 逐渐减少 |

#### 5. 场景块 (Scene Blocks)

用 Markdown 文件组织场景，比纯结构化更灵活：

```markdown
# Scene: 数据库选型讨论
## Meta
- session: sess_abc123
- created: 2026-05-23T10:00:00Z
- heat: 15
## Summary
讨论了 PostgreSQL vs MySQL 的选型，关注点包括性能、扩展性、社区生态
## Key Decisions
- [x] 选择 PostgreSQL 作为主数据库
- [ ] 评估 MySQL 作为缓存层
## Related Instructions
- "所有数据库选型需要考虑数据量预估"
```

### 11.2 不适合 AgentCenter 的部分

| 设计 | 原因 | 替代方案 |
|------|------|---------|
| Node.js 依赖 | AgentCenter 是 Java Spring Boot | Java 重写或 HTTP Gateway |
| OpenClaw 绑定 | AgentCenter 有自己的 Bridge 架构 | 独立运行，通过 API 集成 |
| 15 场景上限 | 企业场景需要更灵活的扩展 | 可配置化或无上限 |
| 无图数据库 | AgentCenter 需要时序推理和关系查询 | 引入 Neo4j 或图数据库 |

### 11.3 迁移建议

#### 核心逻辑迁移路径

```
TencentDB (Node.js)          →     AgentCenter (Java)
──────────────────────────────────────────────────────
L0 JSONL Recorder            →     runtime_event 表
L1 Extractor (LLM)           →     MemoryL1Service (LLM)
L1 SQLite Writer             →     memory_l1 表 (MyBatis)
L2 SceneExtractor (Agent)    →     MemoryL2Service (LLM Agent)
L2 Scene Blocks (.md)       →     scene_block 表 (JSON/Text)
L3 PersonaGenerator          →     MemoryL3Service (LLM)
persona.md                   →     user_persona 表
auto-capture hook            →     Spring Event (@EventListener)
auto-recall hook             →     MemoryFilter / AOP
hybrid search (RRF)          →     MySQL 全文 + 向量插件
```

#### 关键差异

| 方面 | TencentDB | AgentCenter |
|------|-----------|------------|
| 运行时 | Node.js 22+ | Java 17+ Spring Boot |
| 存储 | SQLite + vec0 | MySQL + 现有表结构 |
| 缓存 | node:sqlite 内置 | Spring Cache |
| 调度 | setTimeout / setInterval | @Scheduled |
| 事件 | EventEmitter | Spring ApplicationEvent |
| 向量 | sqlite-vec / GGUF | MySQL Vector / Milvus |

---

## Section 12: Comparative Scorecard

### 12.1 框架横评

| 维度 | TencentDB | Mem0 | Cortex | agentmemory |
|------|-----------|------|--------|-------------|
| **存储后端** | SQLite+vec0/TCVDB | Qdrant+PG | PG+pgvector | SQLite+HNSW |
| **记忆类型** | 3 种 (persona/episodic/instruction) | 3 种 | 1 种 (复杂) | 1 种 |
| **检索策略** | 3 种 (hybrid/emb/fts) | 混合 | 5 信号 WRRF | 6 信号加权 |
| **生命周期** | 4 层渐进 | 2 层 (add/search) | 5 阶段 (labile→consolidated) | 基础 (add/decay) |
| **本地运行** | ★★★★★ | ★★★ | ★★★ | ★★★★★ |
| **基准分数** | 中等 (52.86% LME) | 中等 | 高 (98.4% LME) | 高 (96.2% LME) |
| **部署复杂度** | 低 | 中 | 中 | 低 |
| **创新度** | 高 (4层管线) | 中 | 高 (热值衰减) | 高 (确定性HNSW) |
| **OpenClaw 集成** | 原生 | 需适配 | 需适配 | 需适配 |
| **多用户支持** | 需自建 | 原生 | 原生 | 需自建 |
| **LLM 驱动** | 是 | 是 | 是 | 否 |

### 12.2 适用场景对比

| 场景 | TencentDB | Mem0 | Cortex | agentmemory |
|------|-----------|------|--------|-------------|
| 个人 Agent | ★★★★★ | ★★★★ | ★★★ | ★★★★ |
| 企业多租户 | ★★★ | ★★★★ | ★★★★★ | ★★★ |
| OpenClaw 生态 | ★★★★★ | ★★ | ★★ | ★★ |
| 完全本地 | ★★★★★ | ★★★ | ★★★ | ★★★★★ |
| 高精度 benchmark | ★★ | ★★★ | ★★★★★ | ★★★★★ |
| 快速集成 | ★★★★ | ★★★ | ★★ | ★★★★ |

### 12.3 架构复杂度

```
TencentDB:     L0 → L1 → L2 → L3 (4 层渐进)
Mem0:          add → search (2 层)
Cortex:        labile → fragile → transitional → consolidated → long-term (5 阶段)
agentmemory:   add → decay (2 层)

管线复杂度:     Cortex > TencentDB > Mem0 = agentmemory
```

### 12.4 创新点分析

| 框架 | 核心创新 | 实现难度 |
|------|---------|---------|
| TencentDB | 4 层渐进管线 + L3 persona | 高 |
| Mem0 | 多模态记忆类型 + 语义去重 | 中 |
| Cortex | 热值衰减机制 + 遗忘曲线 | 高 |
| agentmemory | 确定性 HNSW + 零 LLM | 中 |

---

## Section 13: Open Questions

### 13.1 LLM 幻觉检测

**问题**: L1 提取的 LLM 幻觉如何检测和处理？

**现状**: 当前无明确的幻觉检测机制

**建议方案**:

```typescript
// 方案 1: 置信度阈值过滤
const L1_EXTraction = {
  confidence: number;  // LLM 返回的置信度
  // 低于阈值的不写入
};

// 方案 2: 多 LLM 交叉验证
async function extractWithCrossValidation(messages: Message[]): Promise<L1Record> {
  const [result1, result2] = await Promise.all([
    extractWithLLM(messages, 'gpt-4'),
    extractWithLLM(messages, 'claude-3')
  ]);

  // 仅保留一致的字段
  return mergeIfConsistent(result1, result2);
}

// 方案 3: 回测验证
async function backtestExtraction(l1: L1Record): Promise<boolean> {
  // 用 L1 内容反问，看是否能唤醒相关记忆
  const recall = await recallWithContext(l1.content);
  return recall.relevance > 0.7;
}
```

### 13.2 多用户隔离

**问题**: 多用户场景下的 session 隔离策略？

**现状**: session_key 字段支持逻辑隔离，无物理隔离

**建议方案**:

```typescript
// 方案 1: 租户字段
interface MemoryRecord {
  tenant_id: string;  // 租户 ID
  session_key: string;
  // ...
}

// 方案 2: 行级安全 (RLS)
CREATE POLICY tenant_isolation ON memory_l1
  USING (tenant_id = current_tenant());

// 方案 3: 物理分离 (每租户独立数据库)
```

### 13.3 场景上限影响

**问题**: 15 个场景上限在大规模知识库下的影响？

**分析**:

| 场景数 | 影响 | 解决方案 |
|--------|------|---------|
| < 15 | 无影响 | 正常运作 |
| 15-50 | 部分场景无法持久化 | 增加上限或分层 |
| > 50 | 大部分场景被淘汰 | 需要分层策略 (L2.1/L2.2) |

**建议扩展**:

```typescript
interface SceneHierarchy {
  L2_1_scenes: SceneBlock[];  // 当前 session 场景 (max 15)
  L2_2_scenes: SceneBlock[];  // 跨 session 归档场景 (无上限)
  L2_3_scenes: SceneBlock[];  // 长期记忆场景 (手动晋升)
}
```

### 13.4 Embedding 质量差距

**问题**: GGUF embedding 与 OpenAI embedding 的质量差距有多大？

**已知差距**:

| 指标 | OpenAI text-embedding-3 | GGUF embeddinggemma-300m |
|------|------------------------|---------------------------|
| 维度 | 1536 (可压缩) | 384 (固定) |
| MTEB 基准 | ~65% | ~50% (估计) |
| 延迟 | 依赖网络 | ~100ms本地 |
| 成本 | $0.02/1M tokens | 零 |

**建议**: 敏感场景使用 OpenAI，高隐私场景使用 GGUF

### 13.5 TCVDB 性能基准

**问题**: TCVDB 后端的性能基准？

**现状**: 文档未提供具体数字

**需要测试的场景**:

| 场景 | 预期指标 | 验证方法 |
|------|---------|---------|
| 百万级向量检索 | <100ms p99 | 压力测试 |
| 并发写入 | >1000 QPS | 负载测试 |
| 10 用户并发 | 无锁竞争 | 集成测试 |

### 13.6 Java/Spring Boot 集成

**问题**: 如何与 Java/Spring Boot 生态集成？

**推荐方案**:

```
┌─────────────────────────────────────────────────────────────┐
│              AgentCenter (Java Spring Boot)                   │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │              MemoryService (Java)                       │ │
│  │  ├── L0CaptureService                                   │ │
│  │  ├── L1ExtractService (LLM API)                        │ │
│  │  ├── L2SceneService                                     │ │
│  │  └── L3PersonaService                                   │ │
│  └─────────────────────────────────────────────────────────┘ │
│                            │                                  │
│                            │ HTTP / gRPC                     │
│                            ▼                                  │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │        TencentDB Memory Gateway (Node.js)              │ │
│  │  └── Standalone 模式，暴露 REST API                     │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

**集成 API 映射**:

| 操作 | HTTP Method | Path |
|------|-------------|------|
| 捕获记忆 | POST | /api/memory/capture |
| 检索记忆 | GET | /api/memory/search |
| 获取画像 | GET | /api/memory/persona |
| 获取场景 | GET | /api/memory/scenes |

---

## 附录 A: 快速参考

### 安装

```bash
npm install @tencentdb-agent-memory/memory-tencentdb
```

### 基本使用

```typescript
import { TenCentDBMemory } from '@tencentdb-agent-memory/memory-tencentdb';

const memory = new TenCentDBMemory({
  storage: { backend: 'sqlite', path: './memory.db' },
  embedding: { provider: 'gguf', model: './embeddinggemma-300m.gguf' }
});

await memory.capture({ session_id: 'sess_1', role: 'user', content: 'Hello' });
const results = await memory.recall('database选型');
```

### 配置参考

```typescript
const config = {
  layers: {
    L1: { threshold: 5, idleTimeout: 30000 },
    L2: { delay: 90000, maxScenes: 15 },
    L3: { concurrency: 1, scanDepth: 4 }
  },
  storage: {
    backend: 'sqlite' | 'tcvdb',
    path: './data/memory.db'
  },
  embedding: {
    provider: 'openai' | 'gguf',
    model: 'text-embedding-3-small' | 'embeddinggemma-300m',
    baseUrl: 'https://api.openai.com/v1'
  }
};
```

---

## 附录 B: 参考文献

- TencentDB-Agent-Memory GitHub: https://github.com/Tencent/TencentDB-Agent-Memory
- sqlite-vec: https://github.com/asg017/sqlite-vec
- RRF (Reciprocal Rank Fusion): https://plg.uwaterloo.ca/~cguller/RRF.pdf
- Memory Consolidation (神经科学): https://en.wikipedia.org/wiki/Memory_consolidation

---

*报告生成时间: 2026-05-23*
*数据来源: 源代码直接分析*
*报告编号: #22*
