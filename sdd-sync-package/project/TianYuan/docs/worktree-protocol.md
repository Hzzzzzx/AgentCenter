# Worktree 并行开发协议

> 被 AGENTS.md 引用的 Worktree 并行开发规范。

## 核心原则

多个会话（session）并行工作时，每个会话在自己的 worktree 分支上独立开发，完成后通过 `wt-merge` 合入 main。单个会话内的任务直接在主目录串行执行。

## 目录结构

```
~/workspace/TianYuan/              ← 主工作目录 (main branch)
~/workspace/TianYuan-wt/           ← 所有 worktree 的父目录
    ├── chat-refactor/             ← git worktree, branch: wt/chat-refactor
    └── fix-black/                 ← git worktree, branch: wt/fix-black
~/workspace/TianYuan-targets/      ← 独立 Rust 编译产物
    ├── chat-refactor/
    └── fix-black/
```

## 工具链

| 命令                         | 用途                          |
| ---------------------------- | ----------------------------- |
| `./scripts/wt-create <name>` | 创建 worktree + 注入配置      |
| `./scripts/wt-merge <name>`  | 合并回 main + 冲突报告 + 清理 |
| `./scripts/wt-list`          | 列出活跃 worktree             |
| `./scripts/wt-clean [name]`  | 清理 worktree                 |

## wt-create 做了什么

1. `git worktree add` 创建独立目录
2. 软链接 `node_modules`（共享主目录，避免重复安装）
3. `.cargo/config.toml` 设置独立 `target-dir`（避免增量编译冲突）
4. 修改 `tauri.conf.json`（`identifier` + `devUrl` 端口）
5. 修改 `vite.config.ts`（`port`）
6. 注册到 `.sisyphus/worktrees.json`

## wt-merge 流程

```
commit worktree 改动
    → rebase main
    → 有冲突 → 输出冲突报告（文件列表 + 冲突内容 + 建议策略）→ 等用户决策
    → 无冲突 → cargo check + pnpm build
    → 编译通过 → merge --ff-only 到 main → 清理 worktree
    → 编译失败 → 保留分支，报告用户
```

用户面对冲突的选项：

| 选项             | 操作                                                                  |
| ---------------- | --------------------------------------------------------------------- |
| A) 手动解决      | `cd` worktree，编辑冲突文件，`git rebase --continue`，重新 `wt-merge` |
| B) 让 Agent 解决 | 告诉编排层读取冲突文件并合并                                          |
| C) 先看 diff     | `cd` worktree，`git diff main...HEAD`                                 |
| D) 丢弃          | `wt-clean <name>`                                                     |

## 端口分配

| 实例       | Vite 端口 | HMR 端口 |
| ---------- | --------- | -------- |
| 主目录     | 1420      | 1421     |
| wt 第 1 个 | 1422      | 1423     |
| wt 第 2 个 | 1424      | 1425     |
| wt 第 N 个 | 1420+2N   | 1421+2N  |

## 调试规则

- **一次只启动一个桌面端实例**
- 可以在不同终端分别跑不同 worktree 的 `pnpm dev:tauri`
- 切换时先关掉当前实例，再启动新的
- worktree 的窗口标题会显示 `[name]` 以区分

## Agent 工作模式

在 worktree 中 Agent 可以：

- ✅ 改代码、创建文件
- ✅ `cargo check` / `cargo test`
- ✅ `pnpm build`
- ✅ `git add` / `git commit`
- ✅ `pnpm dev:tauri`（人工启动）

## 编排层职责

Sisyphus 在调度工作时：

1. 当前只有 1 个会话 → 主目录直接执行，任务串行
2. 需要开新会话并行工作 → 新会话 `wt-create` 创建 worktree
3. 会话完成 → `wt-merge` 合入 main
4. 合并冲突 → 报告用户，等待决策

## 工作区冲突避让协议（P0 数据安全）

**问题**：多个 Agent 会话共享同一主工作目录。当会话 A 在主目录有未提交改动，会话 B 发现这些改动后，不能直接覆盖。

**核心原则：发现他人改动时，避让的是自己，不是别人。**

### 触发条件

在以下操作前，执行 `git status --short` 检查工作区：
- 开始新任务前
- 执行任何 `git checkout` / `git reset` / `git stash` 前
- Final Wave scope cleanup 前

### 判定流程

```
发现 dirty files
  → 区分：哪些是我/我的 subagent 改的，哪些不是我改的
  → 有不是我改的文件？
      YES → 进入避让流程
      NO  → 正常执行（仍需 stash 备份后再清理）
```

### 避让流程

当发现主目录有**其他会话的大量未提交改动**时：

| 步骤 | 操作 | 说明 |
|------|------|------|
| 1 | **暂停当前工作** | 不要动那些文件 |
| 2 | **报告用户** | 列出检测到的非本会话改动，说明情况 |
| 3 | **将本会话改动挪到 worktree** | `wt-create <name> --cross-session`，在独立目录继续工作 |
| 4 | **在 worktree 中完成任务** | 编译、测试、提交都在 worktree 进行 |
| 5 | **`wt-merge` 合入 main** | 等其他会话的改动也提交后再合并 |

**绝不**：`git checkout HEAD --` 丢弃他人改动、`git stash` 覆盖他人的工作、假设 dirty files 都是自己的 scope creep。

### Scope Cleanup Protocol

清理 subagent scope creep 时，必须遵循：

```
1. git diff --stat HEAD -- 列出所有 dirty files
2. 逐个文件确认归属：
   - 我/我的 subagent 改的 → 可以清理
   - 不确定归属的 → 标记为"可疑"，不清理
   - 确认是其他会话改的 → 绝不清理
3. 对确认归属的文件：git stash push -m 'backup: scope cleanup' 先备份
4. 再 git checkout HEAD -- <确认是自己的文件>
5. 验证清理后不影响其他会话的工作
```

### 触发条件

使用 `wt-create` 的 `--scope` 参数判断：

| 参数 | 判断结果 | 原因 |
| ---- | -------- | ---- |
| `--scope single` | SKIP (exit 0) | 单文件改动无需 worktree |
| `--scope module` | SKIP (exit 0) | 单模块内改动，无需 worktree |
| `--scope cross-module` | ASK (exit 10) | 跨模块改动，建议创建 worktree |
| `--scope rust` | ASK (exit 10) | Rust 后端改动，建议创建 worktree（隔离编译缓存） |
| `--cross-session` | CREATE (exit 0) | 跨会话必须隔离 |
| `--force` | CREATE (exit 0) | 强制创建 |

**关键规则**：同会话内的并行任务（same-session parallel tasks）**不创建** worktree，直接在主目录串行执行。

### 判断规则

| 场景 | 处理 |
| ---- | ---- |
| 同会话并行任务（改代码） | 主目录串行执行，不创建 worktree |
| 跨会话并行任务 | 创建 worktree，使用 `--cross-session` |
| 跨模块改动 | ASK，使用 `--force` 确认创建 |
| Rust 后端改动 | ASK，使用 `--force` 确认创建 |
| 不改代码（纯规划/审查） | 主目录 |

### 委派模板（worktree 模式）

```
1. ./scripts/wt-create <name> --scope <scope> [--cross-session]
2. task(category="deep", prompt="... 工作目录: ~/workspace/TianYuan-wt/<name>/ ...")
3. Agent 完成后: ./scripts/wt-merge <name>
4. 有冲突 → 报告用户，等待决策
```

### Dry-run 模式

`./scripts/wt-create <name> --scope <scope> --dry-run` 可预览决策而不实际创建。
