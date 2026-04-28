# Homepage Workbench Baseline Archive

> 归档日期：2026-04-27
> 状态：当前首页初始布局框架基线
> 范围：高保真静态原型、React Demo 首页快照、关键截图证据

## 归档目的

本目录归档的是 AgentCenter 当前首页的工作台布局基线。它用于说明“未来首页从哪个初始框架继续演进”，不是冻结最终内容。

后续可以调整：

- 指标和流程节点名称。
- 对话场景和示例消息。
- 左侧平台能力和工具名称。
- 右侧智能体协作卡片内容。
- 底部最近活动和历史数据。

后续应尽量保持：

- 白色网页端视觉基调。
- 左中右三栏主工作台。
- 中心对话工作台优先。
- 顶部流程面板默认收起且可展开。
- 底部历史面板默认收起且占用较小。
- 左右栏可展开/收起，且不受顶部/底部面板影响。
- 运行事实、流程阶段、协作状态和历史记录能被清晰投影到首页。

## 归档内容

| 文件 | 说明 |
|------|------|
| [static-homepage.html](./static-homepage.html) | `docs/prototype/homepage.html` 在归档时的静态高保真快照 |
| [react-demo-homepage.html](./react-demo-homepage.html) | `agent-center-demo/client/index.html` 在归档时的 React Demo 快照 |
| [evidence/static-prototype-1440.png](./evidence/static-prototype-1440.png) | 静态原型 1440 视口截图 |
| [evidence/react-demo-1440.png](./evidence/react-demo-1440.png) | React Demo 1440 视口截图 |
| [evidence/react-demo-1920.png](./evidence/react-demo-1920.png) | React Demo 1920 视口截图 |
| [evidence/top-workflow-expanded-fixed.png](./evidence/top-workflow-expanded-fixed.png) | 顶部流程面板展开态修复后截图 |
| [evidence/bottom-history-expanded.png](./evidence/bottom-history-expanded.png) | 底部历史面板展开态截图 |
| [evidence/right-collaboration-rich.png](./evidence/right-collaboration-rich.png) | 右侧智能体协作丰富内容截图 |
| [evidence/conversation-scenarios.png](./evidence/conversation-scenarios.png) | 中心对话场景和动态内容截图 |

## 查看方式

静态原型可以直接打开：

```bash
open docs/prototype/archive/homepage-workbench-2026-04-27/static-homepage.html
```

当前活跃 React Demo 仍以项目根目录下的 `agent-center-demo/client/index.html` 为准。归档内的 `react-demo-homepage.html` 仅用于回看当时的快照。

## 与后续架构的关系

这版首页归档对应当前架构讨论中的工作台入口：

- 左侧组织上下文和平台能力。
- 中心承载用户意图、对话、计划和执行反馈。
- 右侧解释当前对象、风险和智能体协作。
- 顶部承载全流程阶段。
- 底部承载执行历史和可追溯记录。

后续 4+1 视图、统一上下文平台、多项目多用户、安全治理和性能并发设计，都应能投影回这个工作台框架。

