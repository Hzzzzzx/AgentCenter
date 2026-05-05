---
name: sdd-framework
description: |
  SDD (Specification-Driven Development) 框架技能包。AI Agent 开发流程规范，强调规格是_source of truth，代码服务于规格。
  触发场景：收到功能请求时、任务规划时、代码审查时、跨团队协作时、重构前分析时。
  适用角色：Prometheus(规划者)、Metis(计划审查)、Momus(质量审核)、Atlas(执行者)、Oracle(架构咨询)。
---

# SDD Framework Skill

## 核心理念

**Spec is Source of Truth** — 规格是唯一的事实来源，代码必须服务于规格。
不是"写完代码再补文档"，而是"规格先行，代码验证规格"。

## 级别矩阵

| 级别 | 场景 | 核心要求 |
|:---:|------|---------|
| L0 | typo/常量改名/注释修改 | 改完 → 安全扫描 → 审查卡片，5 分钟内完成 |
| L1 | 小 bug（根因明确，1-3 文件） | L0 + 回归测试 |
| L2 | 小功能（1-5 文件，需求清晰） | brainstorming 澄清 → L1 + TDD 单测 |
| L3 | 中型功能（3-10 文件，有设计选择） | L2 + Plan + Blueprint 验证 + 两阶段审查 |
| L4 | 大型功能（8+ 文件，新模块/跨系统） | L3 + Final Wave 四维验收 + UI Control 全场景 |
| 🚨 | 线上故障 | 先修，安全扫描降为 warning，24h 内补审查卡片 |

## 行为约束（7 条）

| 规则 | 说明 | 适用级别 |
|------|------|:-------:|
| **SCOPE** | 只改 task scope 内的文件。diff 出现范围外文件需报告 scope creep。 | 全部 |
| **READ** | 改任何文件前先读。不了解代码就拒绝执行。 | L2+ |
| **DILIGENCE** | 改了函数/类型/导出后，grep 所有引用点确认兼容。 | L2+ |
| **VERIFY** | 改完跑 lint + 测试。失败不报告完成。 | 全部 |
| **REVIEW** | 完成后 30 秒自查 diff，逐条确认无遗漏。 | L3+ |
| **SIMPLE** | 不引入新抽象/工具函数/类型，除非 task 明确要求。 | 全部 |
| **CASCADE** | 改了初始化检查清理路径。改了清理检查恢复。 | L2+ |

## 需求唤起模式（5 步）

收到功能请求时，**不要直接写代码**：

1. **后退** — 先搞清楚真正要解决什么
2. **追问** — L2+: 2-3 个问题澄清边界：做什么？不做什么？有约束吗？
3. **分段** — 把设计分成可消化的小段，每段确认后再往下
4. **总结** — 用一句话 + 边界清单让用户确认
5. **不做** — 用户确认前不写代码、不画架构、不给方案

**输出格式**：
```
> 我的理解: [一句话]
> 边界: ✅做 / ❌不做
> 需要你确认: [最多 1-2 个问题]
```

## 审查卡片模板

```json
{
  "summary": "做了什么（一句话）",
  "decisions": [{"what": "选了 X 而非 Y", "why": "一行理由"}],
  "changes": [{"file": "path", "desc": "改了什么"}],
  "verification": {"lint":"pass","build":"pass","tests":"n/n","blueprint":"n deviations"}
}
```

L0/L1 简化版只需 summary + changes。

## 代码理解工具优先级

| 优先级 | 工具 | 用途 |
|:-----:|------|------|
| 1 | `semantic_search_nodes` | 语义搜索函数/类在哪 |
| 2 | `get_impact_radius` | 改之前查影响面 |
| 3 | `query_graph` (callers_of) | 追溯调用链 |
| 4 | `get_architecture_overview` | 了解架构 |
| 5 | `glob` | 找文件名匹配 |
| 6 | `grep` | 文本搜索（最后手段） |

**不要用 grep/read 去发现已经可以通过图工具一次拿到的东西。**

## 任务管理规则

| 级别 | 计划要求 |
|:---:|---------|
| L0-L1 | 直接改，不需要 Plan |
| L2+ | 创建 todo list，标记 in_progress → completed |
| L3+ | Plan Markdown + Blueprint Target Graph（人工签署后方可执行） |

## 过程文档 Checkpoint

| 级别 | 需要文档 |
|:---:|---------|
| L0-L1 | 审查卡片（唯一产出） |
| L2 | 审查卡片 + 轻量 Plan（如有） |
| L3+ | Plan + Blueprint diff + 审查卡片 + 决策记录 |

所有文档写入 `docs/{module}/` 或 `.sisyphus/`。会话结束自动执行 Design Sync。

## 语言 Profile 选择

根据实际使用的语言加载对应参考文件：

| 语言 | 参考文件 |
|------|---------|
| TypeScript/React | `references/typescript-profile.md` |
| Java/Kotlin | `references/java-profile.md` |
| 其他语言 | 直接使用本 SKILL.md 的核心规则 |

## 参考文件导航

| 文件 | 何时阅读 |
|------|---------|
| `references/sdd-improvement-plan.md` | 需要完整分级矩阵、资产维护体系时 |
| `references/evaluation-framework.md` | 需要评价框架定义时 |
| `references/cross-project-comparison.md` | 六项目横向对比时 |
| `references/typescript-profile.md` | TypeScript/React 项目时 |
| `references/java-profile.md` | Java/Kotlin 项目时 |
| `references/global-agents-config.md` | Agent 角色配置时 |
