# AgentCenter Demo

对话驱动的 DevOps 智能中枢演示

## 快速启动

```bash
cd agent-center-demo
npm install
npm start
```

然后访问 http://localhost:4000

## 功能演示

### 气泡墙
左侧气泡墙显示关键指标：
- 今日部署次数
- 运行中服务数量
- 活跃告警数量
- 今日构建数量

### 对话命令

| 命令 | 说明 |
|------|------|
| `部署 user-service v2.4.0 到 test` | 部署指定服务 |
| `有哪些服务在运行` | 查询所有服务 |
| `查看最近部署记录` | 查看部署历史 |
| `检查 user-service` | 健康检查 |
| `回滚 user-service` | 回滚操作 |

### 界面布局

```
┌──────────────┬─────────────────────────────────────┐
│   气泡墙    │           聊天窗口                   │
│  ─────────  │                                     │
│  📦 今日部署 │  你: 部署 user-service v2.4.0 到 test  │
│  🔧 运行服务 │                                     │
│  ⚠️ 活跃告警 │  Bot: 确认部署                      │
│  🔨 今日构建 │  ┌─────────────────────────┐        │
│              │  │ 服务: user-service     │        │
│  ─────────  │  │ 版本: v2.4.0           │        │
│  💡 快捷命令 │  │ 环境: test             │        │
│  🔍 查询服务 │  └─────────────────────────┘        │
│  📜 部署记录 │                                     │
└──────────────┴─────────────────────────────────────┘
```

## 技术栈

- 前端: React 18 (CDN), Socket.io Client
- 后端: Node.js, Express, Socket.io
- 数据: 内存存储 (演示用)

## 项目结构

```
agent-center-demo/
├── package.json
├── server/
│   ├── index.js         # Express + Socket.io 服务器
│   ├── db.js            # 内存数据存储
│   ├── intent-parser.js # 意图解析
│   └── mock-agents.js   # 模拟 Agent
└── client/
    └── index.html       # React SPA
```