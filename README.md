# AgentCenter

> 企业智能编排平台 — 通过对话驱动企业内部工具流程

---

## 快速启动

### 环境要求

| 依赖 | 最低版本 | 检查命令 | 安装方式 |
|------|---------|---------|---------|
| Java (JDK) | 17 | `java -version` | [Adoptium](https://adoptium.net/) 或 `brew install openjdk@17` |
| Node.js | 20 | `node --version` | 推荐 [nvm](https://github.com/nvm-sh/nvm) |
| opencode CLI | 1.14+ | `opencode --version` | `npm install -g opencode-ai` |
| Git | 任意 | `git --version` | 系统包管理器 |

> **opencode 必须登录过**：`opencode auth`。Bridge 会在首次使用时自动启动 `opencode serve`。

### 一键启动

```bash
./start.sh            # 后台启动所有服务
./start.sh --check    # 先检查环境
./start.sh --status   # 查看运行状态
./start.sh --stop     # 停止所有服务
./start.sh --restart  # 重启
./start.sh --fg       # 前台模式（Ctrl+C 停止）
./start.sh --dev      # 开发模式（tmux 保活 + Bridge DevTools）
```

启动成功后访问 **http://localhost:5173**

### 开发模式与热加载

本地改 Java Bridge 时推荐使用开发模式：

```bash
./start.sh --dev
```

开发模式会把 opencode、Java Bridge、Vue Dev Server 放进固定的 tmux 会话里保活，并启用 Spring Boot DevTools。Java 源码变更后需要让 classpath 更新，常用方式是：

```bash
cd agentcenter-bridge && ./mvnw compile
```

DevTools 检测到编译后的 class 变化后会自动重启 Spring Context；SSE 连接和当前会话 UI 可能短暂断开，刷新页面或重新进入会话即可。停止仍统一使用：

```bash
./start.sh --stop
```

### 手动分步启动

如果不用 `start.sh`，可以手动按顺序启动三个服务：

**第一步：opencode serve（可选，Bridge 会自动启动）**
```bash
opencode serve --hostname 127.0.0.1 --port 4097 --print-logs --log-level WARN
```

**第二步：Java Bridge**
```bash
cd agentcenter-bridge && ./mvnw spring-boot:run
```

**第三步：Vue 前端**
```bash
cd agentcenter-web && npm install && npm run dev
```

### 验证 E2E 对话

1. 打开 http://localhost:5173
2. 进入任意 work item → 点击进入对话工作台
3. 输入消息 → 看到 AI 流式回复（文字逐步出现）

---

## 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| opencode serve | 4097 | AI 引擎，Java Bridge 自动管理 |
| Java Bridge | 8080 | REST API + SSE 推送，前端唯一后端 |
| Vue Dev Server | 5173 | 开发服务器，代理 `/api` → 8080 |

> 浏览器**不会**直接访问 4097。所有请求走 Vue(5173) → Java(8080) → opencode(4097)。

---

## 项目结构

```
AgentCenter/
├── agentcenter-bridge/        # Java Spring Boot 后端
│   ├── pom.xml                # Maven 依赖（Spring Boot 3.4.5, MyBatis, SQLite, Flyway）
│   ├── src/main/resources/
│   │   ├── application.yml    # 端口、opencode 配置、工作目录
│   │   ├── mapper/            # MyBatis XML Mapper
│   │   └── db/migration/      # Flyway 数据库迁移
│   └── src/main/java/         # DDD 分层：Controller → Application → Domain → Infrastructure
│
├── agentcenter-web/           # Vue 3 前端
│   ├── package.json           # Vue 3, Pinia, Vite, Vitest
│   ├── vite.config.ts         # 开发代理配置
│   └── src/
│       ├── views/             # 页面组件（对话工作台、看板、工作流配置等）
│       ├── stores/            # Pinia 状态管理（runtime.ts 负责 SSE 连接）
│       ├── api/               # API 客户端
│       └── components/        # 可复用组件
│
├── agent-center-demo/         # React 首页 Demo（旧版，独立运行）
│
├── docs/
│   ├── architecture/          # 架构文档
│   │   ├── AGENT-RUNTIME-BRIDGE-M1-RUNBOOK.md  # ← 完整的启动指南和故障排查
│   │   ├── ADR-001-OPENCODE-BRIDGE-SSE-REST.md # M1 架构决策记录
│   │   └── ...                # 更多架构和设计文档
│   └── research/              # 行业调研
│
└── AGENTS.md                  # 项目级 Agent 规则（必读）
```

---

## 工作目录配置

opencode serve 的工作目录固定在 **项目根目录下的 `runtime-workspace/`**（自动创建）。所有组件统一使用此目录。

```
AgentCenter/
├── agentcenter-bridge/
├── agentcenter-web/
├── runtime-workspace/          ← opencode 工作目录（自动创建）
│   └── .opencode/
│       ├── skills/             ← 技能文件
│       └── mcp.json            ← MCP 配置
└── start.sh
```

覆盖方式：`export AGENTCENTER_RUNTIME_WORKSPACE=/my/path`

## 企业环境配置

> **重要**：在企业网络内使用时，Maven 可能无法直接访问 Maven Central。请提前配置：

1. **Maven settings.xml**：创建或编辑 `~/.m2/settings.xml`，添加企业仓库 mirror：
   ```xml
   <settings>
     <mirrors>
       <mirror>
         <id>enterprise</id>
         <mirrorOf>*</mirrorOf>
         <url>https://your-company-nexus/repository/maven-public/</url>
       </mirror>
     </mirrors>
   </settings>
   ```

2. **Maven 本地仓库路径**：避免使用 Windows C 盘默认路径。在 `settings.xml` 中配置：
   ```xml
   <localRepository>D:/maven-repo</localRepository>
   ```

3. **自定义 settings.xml 位置**：
   ```bash
   export MAVEN_SETTINGS=/path/to/your/settings.xml
   ```

`./start.sh --check` 会自动检测这些配置并给出提示。

---

## 常见问题

### opencode serve 启动失败（exit code 1）

端口 4097 被占用：

```bash
lsof -i:4097      # 查看占用进程
kill <PID>        # 杀掉后重试
```

### SSE 没有事件返回

Java Bridge 可能用了旧代码，重新编译：

```bash
cd agentcenter-bridge
./mvnw clean compile
./mvnw spring-boot:run
```

确认 opencode serve 在运行：

```bash
curl http://127.0.0.1:4097/path -H 'x-opencode-directory: .'
```

### Vue 前端 API 报 502

Java Bridge 没启动，或没在 8080 端口：

```bash
curl http://localhost:8080/api/agent-sessions
```

### 找不到 opencode 命令

```bash
npm install -g opencode-ai
opencode --version
opencode auth    # 需要登录一次
```

---

## 技术栈

| 模块 | 技术 | 版本 |
|------|------|------|
| Java Bridge | Spring Boot | 3.4.5 |
| 数据库 | SQLite + Flyway | Flyway 8.5.13 |
| ORM | MyBatis | 3.0.4 |
| Vue 前端 | Vue 3 + Pinia + Vite | Node 20+ |
| AI 引擎 | opencode serve | 1.14+ |

---

## 停止所有服务

```bash
pkill -f "opencode serve"
pkill -f "AgentCenterBridgeApplication"
lsof -ti:5173 | xargs kill
```

---

## 更多文档

| 文档 | 说明 |
|------|------|
| [AGENTS.md](./AGENTS.md) | 项目级 Agent 规则和已知陷阱 |
| [M1 Runbook](./docs/architecture/AGENT-RUNTIME-BRIDGE-M1-RUNBOOK.md) | 完整启动指南、curl 测试、架构图 |
| [ADR-001](./docs/architecture/ADR-001-OPENCODE-BRIDGE-SSE-REST.md) | M1 架构决策：为什么用 REST+SSE |
| [docs/README.md](./docs/README.md) | 全部文档索引 |

---

*最后更新：2026-05-10*
