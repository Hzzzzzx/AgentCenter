# AgentCenter Homepage OpenCode Bridge

本文件记录当前首页高保真与用户本机 OpenCode 对话的第一版接入方式。它是独立于 TianYuan 的沉淀，只复用 TianYuan 对 OpenCode 的经验。

## 设计原则

- HTML 高保真仍可直接用浏览器打开。
- 本机能力通过轻量 Node 桥接服务提供，默认只监听 `127.0.0.1`。
- 桥接服务参考 TianYuan 的模式：启动长驻 `opencode serve`，创建 OpenCode session，订阅 `/event` SSE，再用 `/session/{id}/prompt_async` 发送后续用户消息。
- OpenCode 输出被归一化成首页可展示的事件：状态、文本增量、reasoning、skill 开始/完成、权限确认、阻塞告警和异常告警。
- 批注中的 skill 指 OpenCode 自身事件里的 tool/skill 调用；高保真只展示和路由，不重新定义 skill 执行协议。

## 启动方式

真实连接本机 OpenCode：

```bash
cd /Users/hzz/workspace/AgentCenter
node tools/opencode-bridge.mjs --port 4789 --cwd /Users/hzz/workspace/AgentCenter
```

没有安装 OpenCode 时，可用 mock 模式验证页面交互和流式展示：

```bash
cd /Users/hzz/workspace/AgentCenter
node tools/opencode-bridge.mjs --mock --port 4789
```

然后打开：

```text
docs/prototype/homepage.html
```

页面默认连接：

```text
http://127.0.0.1:4789
```

如需切换地址，可在浏览器控制台设置：

```js
localStorage.setItem('agentCenterOpenCodeBridgeUrl', 'http://127.0.0.1:4789')
```

## 页面交互

- 首页或看板点击事项后，右侧出现详情。
- 详情中的“进入会话”会创建与该事项一对一的任务会话。
- 对话框发送消息时，页面会创建或复用本地 OpenCode session，并把任务上下文一起发送给 bridge。
- OpenCode 文本输出会流式追加到中间对话区。
- OpenCode tool/skill 执行会显示为执行卡片。
- 如果 skill 执行遇到权限确认、阻塞、异常或失败，右侧“待确认”会展示确认项。
- 点击确认项里的“处理”会进入该事项对应的任务会话，并模拟工作流从当前节点连续执行。

## 事件模型

桥接服务向页面输出 SSE：

```text
GET /api/agentcenter/events?sessionId=acs_xxx
```

主要事件：

- `status`: OpenCode 会话状态变化。
- `assistant_delta`: 模型回复文本增量。
- `reasoning_delta`: reasoning 文本增量。
- `skill_started`: OpenCode skill/tool 开始执行。
- `skill_completed`: OpenCode skill/tool 完成或失败。
- `permission_required`: OpenCode 请求用户确认权限。
- `alert`: 待确认事项。

## 参考经验

来自 TianYuan 的关键经验：

- 不使用一次性 `opencode run`，否则无法保持会话上下文。
- `opencode serve` 要保持长驻，并通过 `x-opencode-directory` 指定工作目录。
- 同一个 OpenCode session 后续消息使用 `prompt_async`。
- `/event` SSE 中的 `message.part.delta`、`message.part.updated`、`permission.asked`、`session.error` 是前端展示的关键事件来源。
- 权限规则建议初期使用 `edit/*/ask`，保证企业内部平台的人机确认边界清晰。
