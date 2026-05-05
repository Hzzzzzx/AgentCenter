# SDD 开发体系横向对比 — 天元 vs 五大参考项目

> 创建日期：2026-05-03  
> 状态：Draft  
> 关联文档：[knowledge-graph-tools-survey.md](knowledge-graph-tools-survey.md)  
> 图谱数据：基于 graphify 对 6 个项目生成的知识图谱分析  
> **图谱总数：14 张，11,000+ 节点，39,000+ 边**

---

## 项目概述

| 项目 | 定位 | 图谱规模 |
|------|------|---------|
| **TianYuan** | SDD + OMO 图验证驱动 | Blueprint Graph（自研） + UI Control Plane |
| **OpenSpec** | 规格管理引擎 | 792n · 1,104e（src/docs/specs） |
| **GSD** | Phase-aware 任务编排 | 1,817n · 2,695e（docs/commands/agents/sdk/core） |
| **Superpowers** | Skill 驱动的 SDD 方法论 | 134n · 169e |
| **ECC** | Agent 性能优化系统 | 394n · 623e（agents/rules），总 2,000+ 文件 |
| **Chrys** | Agent 平台（Python） | 8,274n · 35,339e（src/docs） |

---

## TianYuan 现有 SDD 体系自评

### 已有资产

| 层级 | 机制 | 状态 |
|------|------|------|
| **门控** | Phase -1 Gates（需求澄清 + 方案对比≥2 + 设计审批 + Graph-Verifiability Check） | 🟢 已落地 |
| | Design Read Gate（开工前必须读设计文档决策记录表） | 🟡 格式有，依赖 Agent 自觉 |
| | Design Write Gate（完工后必须回写新决策/纠正/约束到设计文档） | 🟡 同上 |
| | Design Sync 三层防线（开工前读、完工后写、会话收尾检查） | 🟡 依赖手动 |
| **合同验证** | Blueprint Graph — IPC contract + Frontend contract，14 种 node kind + 10+ 种 edge kind | 🟢 **独有优势** |
| **验收** | Final Wave 四维（F1 Plan Compliance / F2 Code Quality / F3 Manual QA / F4 Scope Fidelity，3 个不同 Agent） | 🟢 完整但偏重 |
| **UI 验证** | UI Control Plane（Observe/Act/Verify/Diagnose，tianyuan-ui CLI，14 内置场景） | 🟢 **独有优势** |
| **质量分级** | P0 数据安全 > P1 正确性 > P2 审查要求 > P3 风格偏好 | 🟢 清晰 |
| **执行约束** | AEP 8 项（意图澄清→小步提交→验证优先→透明进展→交接完整→问题升级→保守修改→可逆为上） | 🟢 定义完整 |
| **并行开发** | Worktree 协议 + wt-create/wt-merge，含 workspace 冲突避让 | 🟢 独有 |
| **CI/CD** | Rust test + clippy，pnpm test:run，cargo build --release | 🟡 基础，无 Blueprint gate |

### TianYuan 图谱数据（自研工具，非 graphify 生成）

| 模块 | 说明 |
|------|------|
| **Blueprint Graph** | `scripts/blueprint-graph/` — Plan Markdown → Target Graph → Actual Graph → Diff → Report |
| **UI Control Plane** | `docs/ui-control-plane/` — tianyuan-ui CLI，14 内置场景，permission 门控 |
| **SDD v1.1** | `docs/sdd/sdd-v1-1-graph-verified-development.md` — 完整 9 步闭环 |

---

## 一、全维度横向对比

### 1.1 核心定位

| 维度 | TianYuan | OpenSpec | GSD | Superpowers | ECC |
|------|:---:|:---:|:---:|:---:|:---:|
| 核心理念 | SDD + OMO，图验证驱动 | Spec 是唯一真相源 | Phase-aware 成本最优 | Skill 自动触发，先 Spec 后代码 | Agent 性能系统，全流程自动化 |
| 规模 | 中型（计划/文档/工具链） | 轻量 CLI | 中型引擎 | 轻量 Skill 集 | 重型系统（2000+ 文件） |
| 通用性 | TianYuan 专属 | 任何 AI 编码工具 | GSD CLI 专属 | 4 种 Harness | 6 种 Harness |

### 1.2 需求→落地流程

| 阶段 | TianYuan | OpenSpec | GSD | Superpowers | ECC |
|------|:---|:---|:---|:---|:---|
| 需求澄清 | Phase -1 Gates + 方案对比≥2 | proposal | Discuss Phase | brainstorming skill（自动触发） | AGENTS.md 上下文 |
| 规格化 | Plan Markdown + Blueprint Target Graph | Delta Spec（ADDED/MODIFIED/REMOVED） | Planning Config | Spec 文档分段确认 | Rules 永久生效 |
| 设计 | 设计审批 + Design Gate | Design Artifact（与 Spec 平行） | Plan 阶段内置 | Spec→Plan 精确到文件路径 | architect agent |
| 任务拆分 | Plan 内 task 列表 | Tasks Artifact（含依赖图） | Phase 粗粒度 + 子任务 | **2-5 分钟可执行单元**（最优） | multi-plan agent |
| 执行 | Agent 按 task 执行 | Agent 按 task | 模型按 Phase 路由 → 执行 | **子代理独立执行 + 两阶段审查** | **48 个专业代理委派** |
| 验收 | **Final Wave 四维（3 Agent）** | Archive（Delta 合并到 Spec） | 失败晋级（升更强模型） | TDD + code review | Hooks 运行时强制执行 |
| 迭代 | 决策记录表 + Archive | 新 Change→新 Delta→新 Spec | 线性执行 | Worktree 合并回 main | Instinct 持续学习 |

### 1.3 设计保真（是否偏离原始设计）

| 机制 | TianYuan | OpenSpec | GSD | Superpowers | ECC |
|------|:---:|:---:|:---:|:---:|:---:|
| 合同形式 | Plan Markdown + Blueprint Target | Delta Spec + Schema 校验 | Planning Config | Spec + Plan 任务 | AGENTS.md + Rules |
| **自动偏离检测** | ✅ **Blueprint Graph（独有）** | ❌ | ❌ | ❌ | ❌ |
| UI 层验证 | ✅ **UI Control Plane（独有）** | ❌ | ❌ | ❌ | ❌ |
| 变更追溯 | ✅ 决策记录表 + Archive | ✅ Delta history | ❌ | ❌ | ✅ Session Memory |
| Graph-Verifiability | ✅ 设计阶段预声明 | ❌ | ❌ | ❌ | ❌ |

> **TianYuan 是五个项目中唯一具备「自动化代码-设计差异检测」能力的。** Blueprint Graph + UI Control Plane 形成代码层+UI 层双轨验证闭环。其余四个项目全部依赖人工审查或 Agent 自觉。

### 1.4 质量保证

| 机制 | TianYuan | OpenSpec | GSD | Superpowers | ECC |
|------|:---:|:---:|:---:|:---:|:---:|
| TDD 强制 | ❌（规定但不强制） | ❌ | ❌ | ✅ **强制 RED-GREEN-REFACTOR** | ✅ agent 推荐 |
| Code Review | 🟡 Final Wave 中人工审查 | ❌ | ❌ | ✅ **两阶段（spec 合规+代码质量）** | ✅ **6 种语言专用 reviewer** |
| 安全扫描 | ❌ | ❌ | ❌ | ❌ | ✅ **AgentShield（1282 测试）** |
| 运行时 Hook | ❌ | ❌ | ✅ Phase gate | ✅ 自动触发 | ✅ **8 种 hook 事件** |
| 静态检查 | ✅ lsp_diagnostics | ✅ Schema 校验 | ❌ | ❌ | ✅ Rules lint |
| E2E 测试 | 🟡 4 个 smoke 脚本 | ❌ | ❌ | ❌ | ✅ e2e-runner agent |
| 多 Agent 验收 | ✅ Final Wave 四维 | ❌ | ❌ | ✅ 两阶段 | ✅ 10+ agent |

### 1.5 Token / 资源效率

| 维度 | TianYuan | OpenSpec | GSD | Superpowers | ECC |
|------|:---:|:---:|:---:|:---:|:---:|
| 模型路由 | ❌（Agent 随意用） | ❌（CLI 不涉及） | ✅ **Phase-aware 自动路由** | ❌ | ✅ model-route |
| 上下文管理 | ❌（依赖 Agent 自觉 compact） | N/A | Phase 隔离 | ✅ 战略压缩 skill | ✅ **系统级优化** |
| 并行策略 | 🟡 Worktree 并行开发 | ❌ | ❌ | ✅ 多子代理并行 | ✅ **PM2 + multi-workflow** |
| 启动成本 | 中（Plan + Design Gate） | **零** | 中 | **低** | **高** |
| 持续成本 | 中（Final Wave + Design Sync） | 低 | 中 | 中 | 高（token 大量消耗） |

### 1.6 自动化程度

| 机制 | TianYuan | OpenSpec | GSD | Superpowers | ECC |
|------|:---:|:---:|:---:|:---:|:---:|
| 需求→计划 | 🟡 人工 Plan Agent | 人工 | CI 触发 | ✅ **skill 自动触发** | ✅ **AGENTS.md 自动** |
| 计划→执行 | 🟡 Agent 按 task 手动协调 | Agent 按 task | 自动路由 | ✅ 子代理自动分发 | ✅ 自动委派 |
| 质量门控 | 🟡 Final Wave（人工协调） | ❌ | ✅ Phase gate | ✅ Hook 自动 | ✅ **Hook 自动** |
| 持续学习 | ❌ | ❌ | ❌ | ❌ | ✅ **Instinct 系统** |
| 设计同步 | 🟡 Design Sync 依赖手动 | ❌ | ❌ | ❌ | ❌ |

### 1.7 知识沉淀

| 机制 | TianYuan | OpenSpec | GSD | Superpowers | ECC |
|------|:---:|:---:|:---:|:---:|:---:|
| 设计文档 | ✅ `docs/{module}/` 分层 | ✅ specs/ 目录 | ❌（README 为主） | ✅ skills/ 目录 | ✅ docs/ 763 文件 |
| 决策记录 | ✅ 设计决策记录表 | ❌ | ❌ | ❌ | ✅ 发布说明详细 |
| 知识图谱 | ✅ Blueprint + Graphify | ❌ | ❌ | ❌ | ❌（未建图） |
| 经验学习 | ❌ | ❌ | ❌ | ❌ | ✅ **Instinct 持续学习** |

---

## 二、量化评分

| 维度（满分 10） | TianYuan | OpenSpec | GSD | Superpowers | ECC |
|------|:---:|:---:|:---:|:---:|:---:|
| 理念清晰度 | 8 | 9 | 7 | 9 | 8 |
| 流程完整性 | **9** | 7 | 7 | **9** | **10** |
| 设计保真 | **10** ✨ | 6 | 4 | 6 | 6 |
| 质量保证 | 6 | 4 | 5 | 8 | **10** |
| Token 效率 | 6 | 10 | **9** | 7 | 5 |
| 通用性 | 4 | 8 | 3 | 7 | **10** |
| 学习成本（越高越好学） | 4 | 7 | 5 | 7 | 3 |
| 自动化程度 | 5 | 3 | 6 | 8 | **10** |
| 扩展性 | 7 | 7 | 6 | 8 | **10** |
| 维护成本（越高越易维护） | 6 | 8 | 6 | 8 | 4 |
| **综合** | **6.5** | 6.9 | 5.8 | **7.7** | **7.6** |

> 注：TianYuan 综合分偏低主要被「通用性」和「自动化程度」拖累，这是其专属工具定位决定的，不是缺陷。

---

## 三、TianYuan 客观评价

### ✅ 做得好

| 方面 | 具体内容 | 对比优势 |
|------|---------|---------|
| **设计保真** | Blueprint Graph — 唯一自动契约验证 | 四个参考项目都无法做到 |
| **UI 验证** | UI Control Plane — 四类能力 14 场景，Agent 可编程 | 独有，ECC 有 e2e 但无系统 UI 控制面 |
| **质量分级** | P0 数据安全 > P1 正确性 > P2 审查 > P3 风格 | 比所有参考项目都清晰系统 |
| **并行开发** | Worktree 协议 + 冲突避让 | 独有，ECC 有类似但不系统 |
| **多 Agent 验收** | Final Wave 四维（3 个不同 Agent 交叉验证） | 理念领先，ECC 也没有这么系统的验收体系 |
| **知识融合** | Design Sync 三层防线 — 决策不丢失 | 理念极好，参考项目都没有 |
| **SDD 闭环** | 9 步完整闭环（需求→方案→审批→计划→实现→验证→审查→CI/CD→归档） | 比 GSD/Superpowers 更完整 |

### ❌ 做得不好

| 方面 | 具体问题 | 参考改进方向 |
|------|---------|------------|
| **TDD 不强制** | 只有规定文字，无执行约束 | 从 Superpowers 汲取：agent prompt 中注入"先写测试，否则拒绝执行"的硬规则 |
| **无安全扫描** | 无任何自动化 secrets 检测 | 从 ECC 汲取：git diff 级 secrets 检查 |
| **无 Token 成本管理** | Agent 随意用，无模型路由 | 从 GSD 汲取：task 复杂度分级 + 模型路由表 |
| **Design Sync 全靠自觉** | 三层防线定义好但无自动化检查 | 需要 pre-commit hook 检查未回写决策 |
| **无持续学习** | 每次会话从零开始，无经验沉淀 | 从 ECC 汲取：session 结束自动提取模式 |
| **Smoke 脚本孤立** | 4 个独立 node 脚本，无统一框架 | 需要公共 runner 框架 |

### ⚠️ 过度设计

| 方面 | 问题 | 建议 |
|------|------|------|
| **Final Wave 四维全量** | 每个 Large+ 任务都跑完整四维验收，太重量级 | 按任务级别分级：Quick 跳过，Short 单维度，Large 全量 |
| **Graph-Verifiability 前置** | Phase -1 就要求声明调用链，对 Quick 任务没必要 | Quick/Short 任务豁免，Large/XL 强制执行 |
| **Design Sync 三层全手动** | 三层防线理念好，但全是手工操作产出低 | 保留理念，用自动化脚本替代人工检查 |
| **Blueprint Graph Phase 2** | 路线图中 runtime 语义抽取、SQLite 持久化过于超前 | Phase 1 已足够用，Phase 2 延后至项目成熟期 |

---

## 四、汲取清单

| 优先级 | 来源 | 汲取内容 | 落地方式 | 预期效果 |
|--------|------|---------|---------|---------|
| **P0** | Superpowers | TDD 强制执行 | agent prompt 注入"先写测试否则报错"硬规则 | 每次代码变更前必须有测试 |
| **P0** | Superpowers | 两阶段审查 | 每个 task 自动运行 spec 合规检查 + 代码质量检查 | 减少 Final Wave 人工负担 |
| **P0** | ECC | 基础安全扫描 | `git diff --cached` 检查常见 secrets 模式 | 杜绝 secrets 泄露 |
| **P1** | GSD | Token 成本感知路由 | task 复杂度标记 + agent 模型路由表 | 简单任务不浪费 token |
| **P1** | ECC | 多 Agent review 扩展 | Final Wave 加 security reviewer、performance reviewer | 质量覆盖面更全 |
| **P1** | 自改 | Design Sync 自动化 | pre-commit hook 检查未回写决策 | 不再依赖 Agent 自觉 |
| **P2** | ECC | 持续学习 | session 结束 hook 自动提取模式到 skill | 逐步积累项目经验 |
| **P2** | 自改 | 简化 Final Wave | Quick/Short 任务取消四维验收 | 减轻流程负担 |
| **P2** | ECC/GSD | 上下文管理 | 接入 compaction 提醒 + phase 隔离 | 减少 token 浪费 |
| **P3** | 自留 | Blueprint Graph + UI Control Plane | 保持，不砍 | **核心竞争力，绝不削弱** |
| **P0** | **Chrys** | Code Agent prompt 工程 | 将"do exactly what was asked"+"due diligence"段注入 TianYuan agent prompt | 减少 AI 随意重构、改进一漏十 |
| **P0** | **Chrys** | Plan Agent 计划格式 | 结构化计划模板（路径+行号+已有模式+约束+验证步骤） | 计划可执行性大幅提升 |
| **P1** | **Chrys** | LastWordsGenerator 交棒 | 上下文满时生成结构化进度笔记而非粗糙截断 | 长任务不丢进度 |
| **P1** | **Chrys** | 子代理职责清晰化 | Explore 只读 / Plan 只规划 / General 只执行 | 减少子代理职责混乱 |

---

## 四-A、Chrys 专项分析 — 不同于所有其他项目的方法论

### Chrys 是什么

Chrys 是一个通用 Agent 平台（Python 3.14+，基于 Microsoft Agent Framework），不是 SDD 工具。它没有定义开发流程，但有 5 个 YAML 定义的 Agent Profile（Code、Explore、Plan、QA、General），每个 profile 的 prompt 指令极其精炼。

### Chrys 的开发方法：不定义流程，定义 Agent 行为

与 OpenSpec、GSD、Superpowers、ECC 都不同的是，Chrys **不定义"开发应该怎么走"**。它定义的是 **"Agent 在每个具体操作时应该怎么想"**。

```
其他项目:  需求 → [spec] → [plan] → [execute] → [review]  (流程驱动)
Chrys:    需求 → Agent(Code Profile) → 做正确的事    (行为驱动)
```

### Chrys 的独有优势

#### 1. Code Agent Prompt — 教科书级的工程师行为规范

每条指令都是对 AI 常见毛病的精准对抗：

| AI 常见毛病 | Chrys 的对抗 |
|------------|-------------|
| 顺手重构无关代码 | **"Do exactly what was asked. Do not refactor, rename, or 'improve' beyond scope."** |
| 不看代码就动手改 | **"Read before writing. Understand existing code, patterns, and conventions."** |
| 改了函数不查所有调用点 | **"Due diligence: check all call sites. When you fix a bug, look for the same pattern elsewhere."** |
| 写完了不跑测试/lint | **"Verify your work. Run the project's linter or tests after multi-file edits."** |
| 埋头编码不看全局 | **"Review before finishing. Pause and look back at the full set of changes."** |
| 过度抽象 | **"Prefer simplicity. Three similar lines of code is better than a premature abstraction."** |

#### 2. Plan Agent — 结构化计划格式

Plan agent 的输出不是模糊的"改这里改那里"，而是：

```
- Context: 解决什么问题
- Changes: 每个文件 → 具体改动 → 已有代码引用（含行号）→ 执行顺序约束
- Trade-offs: 多方案时的选择和理由
- Verification: 怎么测试，跑什么命令，检查什么
```

#### 3. LastWordsGenerator — 上下文满时的交棒机制

不是粗暴截断。是生成一份结构化的进度笔记：
- 当前改到哪、为什么
- 已读文件的关键发现（引用精确行号）
- 已做的决策（不重复讨论）
- 剩余子任务排好序
- 死胡同（不重试）

#### 4. 子代理职责清晰

| 子代理 | 权限 | 用途 |
|--------|------|------|
| Explore | 只读 | 深度搜索代码，不占主 agent 上下文 |
| Plan | 只读 | 出结构化实现计划 |
| General | 读写 | 执行独立子任务 |

### Chrys vs TianYuan 的启示

Chrys 揭示了一个关键想法：**与其让 Agent 遵守外部定义的流程，不如把"好工程师的行为规范"直接写进 Agent 的 system prompt。** 这是流程驱动之外的另一条路——行为驱动。

对 TianYuan 的意义：
- Blueprint Graph 验证设计是否偏离 → 这是"事后检查"
- Chrys 风格的 agent prompt → 这是"事前预防"
- **两者互补**：事前减少偏离 + 事后验证剩余偏离

---

## 五、结论

TianYuan 的 SDD 开发体系在所有六个项目中是最独特的：

- **设计保真无人能及**：Blueprint Graph + UI Control Plane 形成双轨自动验证，这是 TianYuan 的核心护城河
- **理念领先但执行偏手动**：Final Wave 四维验收、Design Sync 三层防线理念极好，但目前大量依赖人工/Agent 自觉
- **流程重量偏重**：对 Quick/Short 任务过于繁琐，需要分级简化
- **三个明显短板**：无 TDD 强制、无安全扫描、无 Token 成本控制
- **Chrys 带来的新思路**：与其加更多流程，不如把"好工程师行为规范"写进 Agent prompt——行为驱动补充流程驱动

**不需要推翻重建。** TianYuan 的基础很扎实。需要做的是：**保留核心优势（Blueprint Graph + UI Control Plane + Design Sync），补充执行约束（TDD + 安全 + 成本），减轻流程负担（分级制）。**

---

> 本报告基于 graphify 对 6 个项目生成的 14 张知识图谱数据，以及各项目 README 和核心文档的深入阅读。
