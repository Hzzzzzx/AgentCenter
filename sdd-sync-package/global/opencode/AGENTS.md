# Global Agent Rules

> 这是全局轻量规则。具体项目的 `AGENTS.md` / `CLAUDE.md` / README 工作协议优先；不要把某个项目的工具、角色或目录结构强行套到其他项目。

## 语言

- 默认使用中文与用户沟通，除非用户要求其他语言或项目文档明确要求。

## 项目协议优先

1. 进入项目后，先读取并遵守项目级 `AGENTS.md`、`CLAUDE.md`、README 或等价工作协议。
2. 项目级协议优先于全局偏好；系统/安全/权限规则永远最高优先级。
3. 如果项目没有明确协议，采用轻量默认流程：理解需求 → 控制范围 → 修改前读上下文 → 实施 → 验证 → 简洁汇报。

## 通用工程约束

- 只改任务范围内的文件；发现 scope creep 要说明。
- 改文件前先理解相关上下文；不确定时先问或先查。
- 简单任务走轻流程，复杂任务先澄清边界和计划。
- 不做破坏性 git 操作，不覆盖用户未提交改动。
- 改完尽量运行对应 lint / test / build；不能运行时说明原因。
- 不提交 secrets，不使用 `as any`、`@ts-ignore`、空 catch 或删除失败测试来绕过问题。

## TianYuan 项目桥接

在 `$HOME/workspace/TianYuan` 中，遵守项目级 `AGENTS.md` 的 TianYuan SDD：

- L0-L4 / 重构 / 紧急协议决定流程轻重。
- PRD / HLD / LLD / Verification 作为信息分层骨架。
- GitNexus、graphify、Blueprint Graph、UI Control、审查卡片按项目规则使用。
- TianYuan 专属角色、`.sisyphus`、工具名和文档链接只在 TianYuan 项目内生效。
