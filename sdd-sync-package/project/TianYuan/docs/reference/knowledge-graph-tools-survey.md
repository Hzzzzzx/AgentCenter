# 知识图谱 & 项目理解工具 横向调研清单

> 状态：Draft
> 创建日期：2026-05-03
> 关联文档：[knowledge-base-research-corpus.md](knowledge-base-research-corpus.md)（统一评审矩阵与模板）
> 用途：追踪知识图谱、代码分析、项目理解工具的克隆、更新与调研进度。

---

## 0. 工具分类框架

本清单聚焦以下四类工具：

| 类别 | 核心问题 | 示例工具方向 |
|------|---------|-------------|
| **静态代码图谱** | 如何把 repo 解析成结构化节点/边、调用链、模块依赖？ | AST/LSP/Compiler-based 图抽取 |
| **LLM 辅助图谱** | 如何在 LLM 帮助下理解代码语义、生成知识资产？ | LLM summarization + graph embedding |
| **运行时图谱** | 如何从执行 trace 构建动态调用图、依赖图？ | 执行追踪 → 图构建 |
| **知识沉淀系统** | 如何把调研结论保存为可检索、可引用的长期资产？ | Capsule / Note / Knowledge Base |

---

## 1. 已有项目（~/workspace 中可直接调研）

| # | 项目名 | 本地路径 | GitHub | 类别 | 当前状态 | 备注 |
|---|--------|---------|--------|------|---------|------|
| 1 | **Graphify** | `~/workspace/graphify/` | `safishamsi/graphify` | LLM 辅助图谱 | 🟡 已有源码，待更新 | TianYuan 已有 graphify-out/ 产物 + 插件 |
| 2 | **OpenSpec** | `~/workspace/OpenSpec/` | `Fission-AI/OpenSpec` | 知识沉淀 | 🟡 已有源码，待更新 | 含 graphify-out/，SDD 规格驱动 |
| 3 | **OpenSpace** | `~/workspace/OpenSpace/` | `HKUDS/OpenSpace` | 知识图谱 | 🟡 已有源码，待更新 | 香港大学，知识图谱 + Agent |
| 4 | **spectrai-community** | `~/workspace/spectrai-community/` | `wei9966/spectrai-community` | 知识沉淀 | 🟡 已有源码，待更新 | Electron 应用 |
| 5 | **codebase-analysis** | `~/workspace/codebase-analysis/` | 本地笔记 | 静态代码图谱 | 🟢 有笔记，无源码 | OpenCode 分析文档，非工具源码 |
| 6 | **GitNexus** | `~/workspace/GitNexus/` | `abhigyanpatwari/GitNexus` | 知识图谱 | 🟡 已有源码，待更新 | Git 仓库知识图谱 |
| 7 | **Lingxi** | `~/workspace/Lingxi/` | `lingxi-agent/Lingxi` | 知识图谱 | 🟡 已有源码，待更新 | LangGraph 框架，Agent + 知识图谱 |
| 8 | **MiroFish** | `~/workspace/MiroFish/` | `666ghj/MiroFish` | LLM 辅助图谱 | 🟡 已有源码，待更新 | 含 graphify-out/ |
| 9 | **TianYuan Blueprint Graph** | `scripts/blueprint-graph/` | 本项目 | 静态代码图谱 | 🟢 已实现 | 自研 IPC/frontend contract 图验证 |
| 10 | **nuwa-skill** | `~/workspace/nuwa-skill/` | `alchaincyf/nuwa-skill` | Agent Skill | 🟢 已更新 | Skill 定义 + 知识注入 |
| 11 | **claude-context** | `~/workspace/claude-context/` | `zilliztech/claude-context` | 语义代码搜索 | 🟢 刚克隆 | Zilliz(Milvus) MCP，代码向量化供 Agent 检索 |
| 12 | **GSD** | `~/workspace/get-shit-done/` | `gsd-build/get-shit-done` | SDD 执行引擎 | 🟢 Updated | Phase-aware 任务编排 + dynamic routing，含 graphify-out/ |
| 13 | **Superpowers** | `~/workspace/superpowers/` | `obra/superpowers` | Agent Skill 市场 | 🟢 Updated | OpenCode skill 体系，含 graphify-out/ |

---

## 2. 待克隆项目（由用户提供 URL）

| # | 项目名 | 提供的 URL | 本地路径 | 类别 | 状态 | 备注 |
|---|--------|-----------|---------|------|------|------|
| 13 | 待补充 | - | - | - | ⏳ Waiting | - |
| 14 | 待补充 | - | - | - | ⏳ Waiting | - |
| 9 | 待补充 | - | - | - | ⏳ Waiting | - |
| 10 | 待补充 | - | - | - | ⏳ Waiting | - |

---

## 3. 状态定义

| 状态 | 含义 |
|------|------|
| ⏳ Waiting | 等待用户提供 URL |
| 🔄 Cloning | 正在 git clone |
| 🟡 Has Source | 已有本地源码，待 git pull 更新 |
| 🟢 Updated | 已 `git pull --ff-only`，源码最新 |
| 📖 Initial Review | 已完成 README / 架构总览阅读 |
| 🔬 Deep Review | 已阅读关键源码、数据模型、流程 |
| 📊 Synthesized | 已完成横向对比与 TianYuan 借鉴结论 |
| ✅ Adopted | 已转化为设计决策或实现任务 |
| ⏸️ Deferred | 有价值但暂不进入近期实现 |
| ❌ Rejected | 明确不适合，已记录原因 |

---

## 4. 克隆/更新协议

对用户提供的每个 URL，按以下步骤操作：

```bash
# 1. 提取项目名
PROJECT_NAME=$(basename "$URL" .git)
TARGET_DIR="$HOME/workspace/$PROJECT_NAME"

# 2. 检查是否已存在
if [ -d "$TARGET_DIR" ]; then
  echo "已存在，执行 git pull..."
  cd "$TARGET_DIR"
  # 先 stash 本地改动（如有）
  if ! git diff --quiet || ! git diff --cached --quiet; then
    git stash push -m "auto-stash before pull $(date +%Y%m%d-%H%M%S)"
  fi
  git pull --ff-only
  echo "更新完成: $(git log -1 --oneline)"
else
  echo "不存在，执行 git clone..."
  git clone "$URL" "$TARGET_DIR"
  echo "克隆完成"
fi

# 3. 更新本清单状态为 🟢 Updated
```

---

## 5. 统一评审矩阵

每个项目统一按以下 15 个维度评审（来自 [knowledge-base-research-corpus.md](knowledge-base-research-corpus.md) §4）：

| 维度 | 要回答的问题 |
|------|-------------|
| 项目定位 | 它解决什么问题？ |
| 核心对象 | 基本资产是什么？node/edge/document/capsule/note？ |
| 采集来源 | 输入来自哪里？代码仓/文件/GitHub/网页？ |
| 解析方式 | AST / LSP / Tree-sitter / embedding / LLM / 规则？ |
| 存储模型 | Markdown / SQLite / 向量库 / 图数据库 / 文件？ |
| 检索方式 | 关键词 / BM25 / 语义 / 混合 / 图查询 / 引用链？ |
| 证据追踪 | 结论能否回溯到源？ |
| 资产生命周期 | 草稿→确认→更新→废弃→归档？ |
| 调研工作流 | ingest→review→synthesize→publish→reuse？ |
| Agent 集成 | CLI / API / MCP / prompt injection / skills？ |
| UI 体验 | 用户如何浏览/编辑/引用知识？ |
| 质量控制 | 来源可信度/置信度/人工确认？ |
| 安全隐私 | 本地优先？离线可用？ |
| TianYuan 借鉴 | 哪些机制适合我们？ |
| 不适合点 | 技术栈风险/复杂度/许可/维护成本？ |

---

## 6. 输出分层

每个项目至少分四层输出（来自 [knowledge-base-research-corpus.md](knowledge-base-research-corpus.md) §5）：

| 层级 | 产物 | 存放位置 |
|------|------|---------|
| Raw Evidence | 链接/commit/源码路径/截图/引用 | `docs/reference/knowledge-graph/evidence/{project}/` |
| Research Notes | 分模块阅读笔记 | `docs/reference/knowledge-graph/notes/{project}.md` |
| Research Summary | 统一矩阵 + 借鉴点/不适合点 | 本清单正文（§7 区域） |
| Knowledge Asset | 设计决策/模式/任务建议 | 按模块回写到对应 design doc |

---

## 7. 调研进度总览

| 项目 | 更新状态 | 调研阶段 | 结论 |
|------|---------|---------|------|
| Graphify | 🟡 待 pull | ⏳ 待开始 | - |
| OpenSpec | 🟡 待 pull | ⏳ 待开始 | - |
| OpenSpace | 🟡 待 pull | ⏳ 待开始 | - |
| spectrai-community | 🟡 待 pull | ⏳ 待开始 | - |
| GitNexus | 🟡 待 pull | ⏳ 待开始 | - |
| Lingxi | 🟡 待 pull | ⏳ 待开始 | - |
| MiroFish | 🟡 待 pull | ⏳ 待开始 | - |
| nuwa-skill | 🟢 已更新 | ⏳ 待开始 | - |
| claude-context | 🟢 刚克隆 | ⏳ 待开始 | - |
| GSD | 🟢 Updated | ⏳ 待开始 | - |
| superpowers | 🟢 Updated | ⏳ 待开始 | - |
| context-parser | ❌ 404 | - | 待确认正确 URL |
| Blueprint Graph (自制) | 🟢 已实现 | 📊 Synthesized | Phase 1 已闭环，Phase 2 产品化路线已定 |
| 待补充 | - | - | - |

---

## 8. 下一步

1. 等用户提供 Git URL → 按 §4 协议克隆/更新
2. 先对已有项目批量 `git pull` 更新源码
3. 按 §6 分层推进调研
4. 每个项目完成 Initial Review 后更新本清单状态

---

> 本清单是活文档。每新增一个项目，在 §2 添加一行，在 §7 更新状态。
