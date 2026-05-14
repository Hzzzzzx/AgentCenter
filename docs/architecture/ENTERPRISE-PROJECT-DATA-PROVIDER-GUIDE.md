# 企业项目数据 Provider 接入指南

> 目标：AgentCenter 主干只保留稳定壳子和最小同步契约。企业内部数据源只需要增量提供一个 Provider/Adapter，实现自己的接口调用和字段映射；不要修改 AgentCenter Web、通用同步 Service、Mapper 或公共表结构。

## 主干负责什么

AgentCenter 主干只负责：

- 项目管理页面壳子：项目配置列表、当前上下文表单、保存选择、同步数据。
- 通用同步入口：`/api/project-data-providers/sync`。
- 通用作用域保存：`/api/project-data-providers/active-scope`。
- 通用落库：项目、空间、迭代、工作项、同步历史。
- 首页卡片、工作项列表、标题栏迭代和任务编排按当前作用域刷新。

主干不负责企业内部接口怎么调用、如何鉴权、如何分页、如何做增量同步、如何处理内部权限字段。

## 企业内部负责什么

企业内部只增量实现一个 `ProjectDataProvider`：

```java
@Component
public class EnterpriseProjectDataProvider implements ProjectDataProvider {
    @Override
    public String id() {
        return "enterprise";
    }

    @Override
    public String name() {
        return "企业数据源";
    }

    @Override
    public String description() {
        return "企业内部项目、空间、迭代和工作项同步。";
    }

    @Override
    public ProjectDataSnapshotDto snapshot(ProjectDataScopeSelectionDto selection) {
        // 企业内部自行调用真实数据源，并映射为 ProjectDataSnapshotDto。
    }
}
```

`selection` 是用户在项目管理页保存的当前选择。它只提供主干已保存的作用域信息，企业内部可以用，也可以按自己的策略忽略或降级处理。

## 最小返回契约

Provider 返回 `ProjectDataSnapshotDto`，包含三类数据：

| 数据 | 用途 |
|------|------|
| `ProjectContextDto[]` | 项目、CloudReq 项目、空间、迭代的展示与作用域 |
| `ProjectContextOptionsDto` | 项目管理页下拉可选项 |
| `ProjectProviderWorkItemDto[]` | FE、US、TASK、WORK、BUG、VULN 工作项 |

主干只要求：

- `providerId` 等于 `ProjectDataProvider.id()`。
- 用稳定外部 ID 做隔离键。
- 展示名称只用于 UI，不参与数据隔离。
- 企业特有字段放到 `extraJson`，不要直接改公共表。

## 低冲突约束

企业内部适配建议只新增文件，例如：

```text
agentcenter-bridge/src/main/java/.../provider/enterprise/**
```

避免修改这些主干文件：

- `agentcenter-web/src/views/ProjectContextSettings.vue`
- `agentcenter-web/src/App.vue`
- `ProjectDataProviderController`
- `ProjectDataSyncService`
- `ProjectContextMapper.xml`
- 已发布 DTO 的字段语义

如果确实需要新能力，优先新增 Provider 扩展接口或新增可选字段，避免修改既有字段含义。

## UI 约定

项目管理页展示业务名称：

- CloudReq 项目
- 空间
- 迭代

内部 ID 只作为选项背后的稳定值保存和同步，不直接展示给用户。

## 验收口径

- 切换企业 Provider 后，项目管理页能看到企业项目、空间、迭代。
- 保存选择后，首页卡片和工作项列表按当前项目/空间/迭代刷新。
- 同步数据后，FE、US、TASK、WORK、BUG、VULN 写入本地库。
- 同名项目或同名迭代不会串数据。
- 企业内部适配不需要改前端壳子和通用同步主流程。
