# 天元 (TianYuan)

> AI Native Desktop Agent IDE - 参考 VSCode 架构的智能 Agent 开发环境

---

## 项目状态

**当前阶段**：Phase 1 - Agent 编排 + IDE 核心布局

**项目目标**：打造 AI Native 的桌面 Agent IDE，参考 VSCode 架构，支持 Agent 编排、记忆系统、可视化调试等核心能力。

## 文档导航

### 核心文档

| 文档                                                                       | 说明                  |
| -------------------------------------------------------------------------- | --------------------- |
| [docs/index.md](docs/index.md)                                             | 文档总索引            |
| [docs/系统设计.md](docs/系统设计.md)                                       | 系统架构设计(4+1视图) |
| [docs/API设计.md](docs/API设计.md)                                         | REST API 规范设计     |
| [docs/memory-system-design.md](docs/memory-system-design.md)               | 记忆系统设计          |
| [docs/日志追踪体系设计.md](docs/日志追踪体系设计.md)                       | 日志追踪体系设计      |
| [docs/reference-projects-analysis.md](docs/reference-projects-analysis.md) | 参考项目分析          |

### Qoder 机制研究

详见 [docs/qoder-mechanics/](docs/qoder-mechanics/) 目录（6篇深度分析）

### 子模块

- [docs/memory-system/](docs/memory-system/) - 记忆系统模块

### 技术栈

- **桌面运行时**: Electron + Rust sidecar
- **前端**: Vue 3 + TypeScript
- **后端**: Rust (sidecar binary, managed by Electron)
- **架构风格**: VSCode Workbench/Panel/Sidebar/Editor 模式

### 开发命令

```bash
# 安装依赖
pnpm install

# 启动开发服务器 (Electron)
pnpm dev:electron

# 构建
pnpm build:electron

# 运行测试
pnpm test:run

# Electron E2E 测试
TIANYUAN_ELECTRON_HIDE_WINDOW=1 pnpm test:e2e:electron

# 打包
pnpm package:electron:dir
```
