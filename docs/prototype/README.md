# Prototype

> 当前首页高保真原型
> 最近更新：2026-05-02

## 当前基线

白色网页端 VS Code 式三栏工作台首页，作为 AgentCenter 首页的初始布局框架。

基线固定的信息架构和布局骨架：

- 顶部导航条：可展开/收起
- 顶部全流程阶段面板：默认收起，展开展示节点详情
- 左侧栏：默认展开会话列表，包含平台导航、通用工具链和智能体状态
- 中心栏：对话工作台，是首页主焦点
- 右侧栏：上下文详情、主动预警、智能体协作
- 底部历史面板：默认收起，展开展示最近活动、全流程历史和执行记录
- 状态栏：运行状态、工具连接和在线状态

## 活跃文件

| 文件 | 说明 |
|------|------|
| [homepage.html](./homepage.html) | 静态高保真首页原型（当前基线） |
| [../../agent-center-demo/client/index.html](../../agent-center-demo/client/index.html) | React Demo 首页实现 |
| `screenshot-*.png` | 当前 homepage 截图（1440 / full / viewport） |

## 已归档版本

| 版本 | 说明 |
|------|------|
| [homepage-workbench-2026-04-27](./archive/homepage-workbench-2026-04-27/) | 工作台基线归档（静态原型 + React Demo 快照 + 截图证据） |
| [client-demo-2026-04-29](./archive/client-demo-2026-04-29/) | 早期首页视觉探索草稿（a-version 系列、architecture-comparison） |
