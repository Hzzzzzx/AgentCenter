# Graphify 深度研究报告

> AgentCenter 记忆系统调研 · 开源项目 #5
> 调研日期：2026-05-23
> 评级：Tier C（知识提取工具，非记忆框架）

---

## 1. Reader Verdict

**一句话总结**：Graphify 是一个将任意输入转化为知识图谱并输出人类可读 Wiki 的本地工具，适合代码结构分析和多模态知识提取，但不具备持久化记忆能力。

**核心评价**：Graphify 的核心价值在于**知识提取**而非**记忆存储**。它能够从代码、PDF、图片、Markdown 中自动构建知识图谱，并通过 Leiden 社区检测算法发现架构层面的深层联系。71.5 倍的 token 压缩比证明了其提取效率。然而，其内存图谱随进程消失的设计使其本质上是一个**一次性的分析工具**而非**持续运转的记忆系统**。对于 AgentCenter 而言，Graphify 的 Wiki 导出机制和置信度标签设计值得借鉴，但其 CLI 架构和服务化缺失是需要解决的核心工程问题。

**适用场景**：个人开发者或小团队快速建立代码知识索引，需要人类可读的架构文档输出。

**不适用场景**：需要持久化知识库、企业级多用户环境、实时记忆更新的场景。

---

## 2. Framework Profile

| 属性 | 值 |
|------|-----|
| **项目名称** | Graphify |
| **开发组织** | neuronpedia |
| **定位** | 本地知识图谱构建工具 |
| **技术栈** | Python + tree-sitter + NetworkX + graspologic |
| **许可证** | MIT |
| **存储介质** | 内存（NetworkX DiGraph）+ 文件导出 |
| **持久化** | 无（进程结束后图谱消失） |
| **Benchmark 数据** | 无 |
| **与 AgentCenter 契合度** | 中（Wiki 导出机制 + 置信度标签值得借鉴） |

---

## 3. Core Thesis

**核心论点**：Graphify 相信**知识要同时服务于机器和人类**。与大多数只输出 API 可访问格式的记忆框架不同，Graphify 多走了一步——直接输出人类可读的 Wiki 页面。这意味着用户既能通过程序查询「哪些模块依赖 X」，也能让人直接打开 Wiki 浏览器看看「这个系统的结构是什么样的」。

Graphify 的另一个核心信念是**对置信度诚实**。每条关系边都标注了来源类型：EXTRACTED（直接提取，置信度最高）、INFERRED（静态分析推断，存在假阳性可能）、AMBIGUOUS（置信度最低）。这不是细节，而是企业级知识管理的基石——决策者需要知道 AI 的判断有多可靠，不能把推断当事实用。

---

## 4. Theoretical Foundation

Graphify 的理论基础建立在**混合解析策略**之上：确定性规则解析（tree-sitter）与语义理解（LLM）各司其职。

**Tree-sitter 的理论依据**：tree-sitter 提供确定性、线性时间（O(n)）的解析能力，AST 结构化程度高，适合批量处理。相比之下，LLM 解析代码虽然能理解语义，但 token 消耗巨大、延迟高、结果不稳定。Graphify 在代码解析层选择 tree-sitter 保证覆盖率和高效率，仅在图片理解等 tree-sitter 无法处理的模态才调用 LLM。

**Leiden 社区检测的理论依据**：Leiden 算法是 Louvain 的改进版，理论上保证更好的社区划分质量（输出分区更加内聚且连通）。对于大型代码库的数千个节点，Leiden 能有效归并为若干内聚社区。

**跨模态统一表示**：所有模态最终都被归一化为节点和边，进入同一张图。代码提供结构知识，PDF 提供学术引用知识，图片提供视觉概念知识，Markdown 提供文档组织知识。跨模态的边能够在单一图结构中自然呈现。

---

## 5. Memory Model

Graphify **不是记忆框架**，其设计目标是**知识提取**而非**记忆存储**。

**存储架构**：基于 NetworkX 的 DiGraph 是内存中的图数据库，数据随进程存在，进程结束即消失。没有持久化存储，每次运行从原始数据重新构建。

**图数据结构**：
- **节点**：代表实体（类、函数、模块、概念）
- **边**：携带类型标签（EXTRACTED/INFERRED/AMBIGUOUS）和来源信息
- **属性**：每条边携带置信度标签和审计追踪信息

**无状态设计**：Graphify 不维护会话状态，每次运行都是独立的一次性分析。增量更新通过 SHA256 缓存实现（只重新处理变更文件），但不保存历史图谱状态。

---

## 6. Architecture Deep Dive

### 6.1 7阶段处理管线

```
Stage 1: detect → Stage 2: extract → Stage 3: build_graph → 
Stage 4: cluster → Stage 5: analyze → Stage 6: report → Stage 7: export
```

**Stage 1 - detect**：首阶段扫描输入文件集，识别文件类型。代码文件路由到 tree-sitter 解析器，PDF 走引用挖掘流程，图片调用 Claude Vision API，Markdown 走概念抽取。

**Stage 2 - extract**：核心阶段。代码解析分两步：AST 遍历收集结构化实体（classes、functions、imports）+ 调用关系图构建（通过静态分析推断函数调用链，标记为 INFERRED 边）。

**Stage 3 - build_graph**：所有实体和关系汇入 NetworkX DiGraph，每条边携带置信度标签。

**Stage 4 - cluster**：Leiden 算法（graspologic 实现）做社区检测，将数千个节点归并为若干内聚社区。

**Stage 5 - analyze**：分析两个维度——God nodes（高 degree 节点）和其他实体连接最多的概念，以及 Surprising connections（非显而易见的深层联系）。

**Stage 6 - report**：分析结果写入 GRAPH_REPORT.md，包含社区列表、God nodes 中心性分析、ranked 意外连接。

**Stage 7 - export**：最终导出支持 graph.json（结构化图数据）、graph.html（vis.js 交互式可视化）、Obsidian vault 格式、Wiki 格式。

### 6.2 增量更新机制

SHA256 哈希缓存文件状态，仅重新处理变更文件。--watch 模式监听文件系统变化触发增量重建，git hook 集成支持在 commit 后自动同步图谱。

### 6.3 多模态提取策略

Graphify 的多模态处理围绕知识图谱这一统一表示进行。代码提供结构知识，PDF 提供学术引用知识，图片提供视觉概念知识，Markdown 提供文档组织知识。所有模态最终都被归一化为节点和边进入同一张图。

---

## 7. Design Tradeoffs

| 权衡维度 | 选了什么 | 代价 |
|---------|---------|------|
| **解析器选择** | tree-sitter（确定性） | 无法处理无 AST 的动态语言特性 |
| **图数据库** | NetworkX（内存） | 进程结束数据消失，无持久化 |
| **LLM 使用** | 仅图片理解 | token 成本低但语义理解有限 |
| **社区检测** | Leiden（批处理） | 无法实时追踪社区演化 |
| **输出格式** | Wiki + JSON + HTML | 需要二次解析才能被程序消费 |
| **增量更新** | SHA256 文件级缓存 | 无法追踪事实级别的变更历史 |

---

## 8. Evidence

**Token 效率**：71.5 倍的 token 压缩比。用传统方法将 52 个混合文件喂给 LLM 所需的 token，足够 Graphify 构建完整图谱并生成分析报告。

**多模态支持**：支持 Python、JavaScript/TypeScript、Go、Rust、Java、C、C++ 等语言的 AST 解析。PDF 处理采用引用挖掘策略。图片通过 Claude Vision API 理解。

**Wiki 输出**：每个社区对应一个 Markdown 文件，文件名即社区名称。God node 享有独立页面解释其为何连接如此多的实体。

**本地运行**：无需 Neo4j、无需 MySQL、无需任何外部服务。pip install 即可运行。

---

## 9. Applicability

| 场景 | 适用度 | 说明 |
|------|--------|------|
| **代码结构分析** | 强 | Tree-sitter AST + 社区检测，完整覆盖 |
| **架构文档生成** | 强 | Wiki 导出 + God node 分析，自动生成 |
| **多模态知识提取** | 中 | 仅支持 tree-sitter 支持的语言 + 图片 + PDF |
| **企业知识库** | 弱 | 无持久化、无多用户支持 |
| **实时记忆更新** | 无 | CLI 工具，无服务化架构 |
| **Agent 上下文注入** | 弱 | 需要封装层解析 JSON 输出 |

---

## 10. Maturity

| 维度 | 评级 | 说明 |
|------|------|------|
| **技术成熟度** | 中 | MIT 许可证，社区活跃度中等 |
| **生产就绪度** | 低 | CLI 工具，非服务化设计 |
| **多语言支持** | 中 | 主要支持主流编程语言 |
| **企业特性** | 低 | 无访问控制、并发支持、事务概念 |
| **文档完整性** | 高 | README 详细，架构图清晰 |
| **维护活跃度** | 中 | 持续更新，star 数量中等 |

---

## 11. AgentCenter Implications

### 11.1 可借鉴的设计

**置信度标签**：Graphify 对每条关系都标注了「我是怎么知道的」。AgentCenter 的记忆系统应该对每条知识标注来源和可信度，这对企业信任至关重要。

**Wiki 输出**：记忆要 human-browsable，不能只是黑盒 API。用户要能看到 agent「知道」了什么。

**混合解析策略**：确定性解析交给规则系统，语义理解交给 LLM。两种能力各司其职。

### 11.2 工程集成挑战

**进程调用 vs API 调用**：Bridge 需要通过 ProcessBuilder 调用 Graphify CLI，捕获其文件输出。

**输出格式的二次解析**：Bridge 需要一个 JSON 解析器将图数据读入 Java 侧的数据结构。

**长期存储**：Graphify 的 NetworkX 图谱是临时的，AgentCenter 需要维护一个持久化图数据库。

### 11.3 实际使用情况

Graphify 已经在本机安装并实际运行过（/Users/hzz/workspace/graphify）。这本身就是一个重要信号——项目已经通过了「值得花时间研究」的门槛。

---

## 12. Comparative Scorecard

| 能力 | Graphify | Mem0 | Hindsight | Zep | OpenHuman |
|------|----------|------|-----------|-----|-----------|
| **记忆持久化** | ❌ | ✅ | ✅ | ✅ | ✅ |
| **知识图谱** | ✅ | ❌ | ❌ | ✅ | ✅ |
| **多模态支持** | ✅ | ❌ | ❌ | ❌ | ✅ |
| **Human-browsable** | ✅ | ❌ | ❌ | ❌ | ✅ |
| **时态推理** | ❌ | ❌ | ❌ | ✅ | ❌ |
| **实时更新** | ❌ | ✅ | ✅ | ✅ | ✅ |
| **服务化架构** | ❌ | ✅ | ✅ | ✅ | ❌ |
| **Benchmark 得分** | N/A | ~49% | 91.4% | ~64% | N/A |

---

## 13. Open Questions

1. **Graphify 能否作为 AgentCenter 的代码知识提取管道？** 当前是 CLI 工具，需要封装为 Java service 才能集成。

2. **增量图谱更新的正确架构是什么？** SHA256 文件级缓存无法追踪事实级别的变更，需要更细粒度的变更追踪机制。

3. **多模态知识如何与记忆系统融合？** Graphify 的多模态提取是独立管道，与主流记忆框架的向量检索完全不同，如何桥接？

4. **社区演化的追踪方案？** 当前 Leiden 社区检测是一次性的，无法追踪三个月前后的社区划分变化。

5. **Graphify 与向量数据库的边界在哪？** Graphify 的图结构适合关系推理，向量适合语义相似度，两者如何选择或共存？

---

*本报告为 AgentCenter 内部技术调研材料，数据截止日期 2026-05-23。*
