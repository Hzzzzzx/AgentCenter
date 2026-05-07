# Prototype

> 当前首页高保真原型
> 最近更新：2026-05-07

## 当前基线

白色网页端三栏工作台首页，作为 AgentCenter 首页的初始布局框架。

基线固定的信息架构和布局骨架：

- 顶部导航条：项目、空间、迭代三级联动筛选，并支持关键字搜索 FE、US、Task、Work、缺陷、漏洞
- 左侧栏：固定入口为首页、看板、工作流；会话列表按通用会话与任务会话组织，任务会话默认折叠
- 中心栏：首页展示任务全景，看板展示状态流转，工作流展示类型状态模型，对话工作台承接任务会话
- 右侧栏：待确认和事项详情；待确认用于承接 skill 阻塞、权限确认、审批和信息补充
- 状态栏：运行状态、工具连接和在线状态
- 本地 OpenCode 对话：通过 `tools/opencode-bridge.mjs` 桥接 `opencode serve`，详见 [opencode-bridge.md](./opencode-bridge.md)

## 启动查看

静态高保真：

```text
docs/prototype/homepage.html
```

本地 OpenCode 桥接：

```bash
cd /Users/hzz/workspace/AgentCenter
node tools/opencode-bridge.mjs --port 4789 --cwd /Users/hzz/workspace/AgentCenter
```

无 OpenCode 环境时的交互验证：

```bash
cd /Users/hzz/workspace/AgentCenter
node tools/opencode-bridge.mjs --mock --port 4789
```

## 活跃文件

| 文件 | 说明 |
|------|------|
| [homepage.html](./homepage.html) | 静态高保真首页原型（当前基线） |
| [HOMEPAGE-VUE-HIGH-FI-GAP.md](./HOMEPAGE-VUE-HIGH-FI-GAP.md) | Vue 实现与首页高保真的差距清单和验收合同 |
| [THEME-SYSTEM-DESIGN.md](./THEME-SYSTEM-DESIGN.md) | Vue 工作台主题切换、配色 token 和 7 套主题方案 |
| [theme-system-highfi-board.png](./theme-system-highfi-board.png) | 主题高保真对照图 |
| [opencode-bridge.md](./opencode-bridge.md) | 本地 OpenCode 桥接说明 |
| [../../tools/opencode-bridge.mjs](../../tools/opencode-bridge.mjs) | 本地 OpenCode bridge 脚本 |
| [../../agent-center-demo/client/index.html](../../agent-center-demo/client/index.html) | React Demo 首页实现 |
| `screenshot-*.png` | 当前 homepage 截图（1440 / full / viewport） |

## 已归档版本

| 版本 | 说明 |
|------|------|
| [homepage-workbench-2026-04-27](./archive/homepage-workbench-2026-04-27/) | 工作台基线归档（静态原型 + React Demo 快照 + 截图证据） |
| [client-demo-2026-04-29](./archive/client-demo-2026-04-29/) | 早期首页视觉探索草稿（a-version 系列、architecture-comparison） |
