---
name: lld-design
description: Convert an FE requirement, PRD, and HLD into a low-level design Markdown document. Applies to LLD 设计, 详细设计, API design, schema design, workflow node design, and implementation planning.
triggers:
  - lld-design
  - LLD设计
  - 详细设计
  - HLD转LLD
---

# LLD Design

Use this skill after `hld-design` has produced an HLD. The goal is to create a low-level design Markdown document that can drive implementation tasks.

## Input

The caller should provide:
- FE id and FE requirement summary.
- PRD Markdown output.
- HLD Markdown output.
- Optional codebase constraints, database choice, frontend framework, backend framework, runtime adapter details, and testing expectations.

If implementation details are undecided, recommend an MVP path first and mark future extension points separately.

## Output Rules

Return only one Markdown document. Do not include extra explanation before or after the document.

The document must be implementation-ready and should avoid vague placeholders. Use concrete names for tables, APIs, DTOs, components, and tests when possible.

## Markdown Template

```markdown
# LLD: <FE id> <title>

## 1. 实现范围
- 本次实现:
- 暂不实现:
- 依赖前置:

## 2. 数据模型
| 表/对象 | 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- | --- |
|  |  |  |  |  |

## 3. 状态机
| 对象 | 状态 | 进入条件 | 退出条件 | 失败处理 |
| --- | --- | --- | --- | --- |
|  |  |  |  |  |

## 4. API 设计
| API | 方法 | 请求 | 响应 | 错误码 |
| --- | --- | --- | --- | --- |
|  |  |  |  |  |

## 5. 后端实现
- Controller:
- Service:
- Mapper/Repository:
- Runtime Adapter:
- 事务边界:

## 6. 前端实现
- 页面/组件:
- Store:
- API Client:
- 交互状态:
- 空状态/错误状态:

## 7. 工作流节点与 Skill 调用
| 节点 | Skill | 输入 | 输出产物 | 是否需要用户确认 |
| --- | --- | --- | --- | --- |
| PRD | prd-desingn | FE 需求 | PRD Markdown | 可选 |
| HLD | hld-design | FE + PRD | HLD Markdown | 可选 |
| LLD | lld-design | FE + PRD + HLD | LLD Markdown | 可选 |

## 8. 测试计划
- 单元测试:
- 集成测试:
- 前端测试:
- 手工验证:

## 9. 迁移与兼容
- SQLite MVP:
- PostgreSQL 演进:
- OpenCode runtime 演进:

## 10. 实施任务拆分
| 任务 | 文件/模块 | 验收方式 |
| --- | --- | --- |
|  |  |  |
```
