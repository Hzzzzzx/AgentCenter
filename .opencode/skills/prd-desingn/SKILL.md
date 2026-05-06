---
name: prd-desingn
description: Convert an FE requirement into a product requirements Markdown document. Also applies when users say prd-design, PRD 设计, 产品需求设计, or FE 需求转 PRD.
triggers:
  - prd-desingn
  - prd-design
  - PRD设计
  - FE需求转PRD
---

# PRD Design

Use this skill when the input is an FE requirement, such as `FE1234`, and the expected output is a PRD Markdown document.

## Input

The caller should provide:
- FE id, title, description, business background, user scenario, constraints, priority, and current workflow state.
- Optional existing conversation context or acceptance notes.

If details are incomplete, make reasonable assumptions and list them in the PRD. Do not block on questions unless the missing information changes the product direction.

## Output Rules

Return only one Markdown document. Do not include extra explanation before or after the document.

The document must be actionable for the next HLD step and should include stable anchors that later design documents can reference.

## Markdown Template

```markdown
# PRD: <FE id> <title>

## 1. 背景与目标
- 背景:
- 目标:
- 非目标:

## 2. 用户与场景
- 目标用户:
- 核心场景:
- 触发入口:
- 成功结果:

## 3. 需求范围
| 编号 | 需求项 | 说明 | 优先级 | 验收标准 |
| --- | --- | --- | --- | --- |
| PRD-REQ-001 |  |  | Must |  |

## 4. 交互与流程
1. 
2. 
3. 

## 5. 数据与状态
| 对象 | 字段/状态 | 说明 |
| --- | --- | --- |
| FE |  |  |

## 6. 权限与安全
- 身份:
- 权限:
- 审计:
- 风险:

## 7. 性能与可用性
- 性能目标:
- 并发假设:
- 降级策略:

## 8. 验收标准
- 

## 9. 待确认问题
- 

## 10. 给 HLD 的设计输入
- 核心能力:
- 关键流程:
- 关键数据:
- 外部依赖:
```
