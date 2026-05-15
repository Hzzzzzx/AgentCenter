# Markdown Artifact Review Closure Plan

> 状态：待实施
> 基线：`codex/from-2026-05-14-1026` / `6f2e0416`
> 创建：2026-05-15
> 架构文档：`docs/architecture/MARKDOWN-ARTIFACT-REVIEW-CLOSURE-DESIGN.md`

## 需求边界

### 做

- 按用户 Runtime Skill 设定，把 `runtime-workspace/artifacts/**/*.md` 作为阶段产物。
- 对话框展示每个节点生成的 Markdown 产物卡片。
- `ARTIFACT_REVIEW` 绑定实际 artifact 内容，用户可以先看内容再反馈。
- 第一版只支持 Markdown 预览和 Markdown 快照。

### 不做

- 不支持 PDF / DOCX / 图片 / 二进制预览。
- 不把普通聊天正文、工具日志或运行状态当作产物。
- 不做复杂 diff 编辑器。
- 不扩大到生产级权限和多租户隔离。

## HLD

### 数据流

```text
Skill 写 artifacts/*.md
  -> Bridge 扫描新增/变更 Markdown
  -> artifact 表登记 FILE_SNAPSHOT
  -> workflow node 关联 artifact list
  -> Web 对话流展示 ArtifactStageCard
  -> ARTIFACT_REVIEW 展示 Markdown 预览和反馈框
  -> 用户反馈回灌给 Skill
```

### 权威源

| 信息 | 权威源 |
|------|--------|
| 文件产物 | runtime workspace 的 `artifacts/**/*.md` |
| 产物记录 | `artifact` 表 |
| 节点归属 | `workflow_node_instance_id` |
| 审阅请求 | `confirmation_request.payload_json.artifactIds` |
| 用户反馈 | `confirmation_action.payload_json` + 会话 ledger message |

## LLD / 修改点

### 1. Bridge Markdown Capture

目标文件：

- `agentcenter-bridge/src/main/java/com/agentcenter/bridge/application/WorkflowCommandService.java`
- 建议新增：`agentcenter-bridge/src/main/java/com/agentcenter/bridge/application/artifact/MarkdownArtifactCaptureService.java`
- `agentcenter-bridge/src/main/java/com/agentcenter/bridge/infrastructure/persistence/entity/ArtifactEntity.java`
- `agentcenter-bridge/src/main/resources/mapper/ArtifactMapper.xml`

实施：

- 节点运行前记录 `artifacts/**/*.md` 的 hash snapshot。
- 节点运行后扫描新增/变更 Markdown。
- 保存 artifact：
  - `artifact_type = MARKDOWN`
  - `source_type = FILE_SNAPSHOT`
  - `content = 文件文本快照`
  - `file_path/storage_uri = 真实路径`
  - `workflow_node_instance_id = 当前节点`
- 同一路径再次变化时增加 `version_no`。

### 2. Artifact Review Binding

目标文件：

- `WorkflowCommandService.java`
- `agentcenter-bridge/src/main/java/com/agentcenter/bridge/application/workflow/InteractionMapper.java`
- `agentcenter-bridge/src/main/java/com/agentcenter/bridge/application/ConfirmationService.java`

实施：

- 解析到 `ARTIFACT_REVIEW` 时，先查当前节点 Markdown artifact list。
- 将 artifact ids 写入 confirmation payload。
- 没有 artifact 时不要创建普通 review；创建异常/阻塞确认。
- resolve 时把 `artifactIds / decision / comment / baseVersion` 记录到 action payload。
- resume prompt 追加 Artifact Review Context。

### 3. Node Artifact List API

目标文件：

- `agentcenter-bridge/src/main/java/com/agentcenter/bridge/api/ArtifactController.java`
- `agentcenter-bridge/src/main/java/com/agentcenter/bridge/api/dto/ArtifactDto.java`
- `ArtifactMapper.java` / `ArtifactMapper.xml`

接口：

- `GET /api/workflow-node-instances/{nodeId}/artifacts`
- `GET /api/workflow-instances/{instanceId}/artifacts?groupByNode=true`

第一版 DTO 增加：

- `filePath`
- `sourceType`
- `versionNo`
- `previewStatus`
- `contentHash`

### 4. Web Conversation Artifact Card

目标文件：

- `agentcenter-web/src/views/ConversationWorkbench.vue`
- `agentcenter-web/src/components/conversation/MessageList.vue`
- 建议新增：`agentcenter-web/src/components/conversation/ArtifactStageCard.vue`
- `agentcenter-web/src/api/artifacts.ts`
- `agentcenter-web/src/api/types.ts`

实施：

- 加载当前 workflow node artifact list。
- 在对话 turn 中插入 artifact card。
- card 提供：
  - 文件标题
  - 来源节点
  - 版本
  - 预览按钮
  - 审阅按钮

### 5. Web Artifact Review UI

目标文件：

- `agentcenter-web/src/components/conversation/ConversationInteractionBar.vue`
- `agentcenter-web/src/components/conversation/interactions/InteractionResponseForm.vue`
- `agentcenter-web/src/components/conversation/ArtifactViewer.vue`

实施：

- `ARTIFACT_REVIEW` 分支使用专用布局。
- 显示绑定 artifact 文件名和预览入口。
- 支持通过 / 需要修改。
- 需要修改时 comment 必填。
- 提交 payload 带 artifact ids 和 base version。

## 任务拆分

| 批次 | 内容 | 验证 |
|------|------|------|
| P1 | Bridge Markdown capture + mapper/API 基础 | `./mvnw test` 中 artifact/workflow 用例 |
| P2 | `ARTIFACT_REVIEW` payload 绑定和 resume context | confirmation/workflow integration test |
| P3 | Web artifact card 和 Markdown preview 状态 | `npm run typecheck` / component tests |
| P4 | Web review UI 特化和提交 payload | component tests |
| P5 | PRD/HLD/LLD smoke E2E | Playwright 截图 evidence |

## 验收用例

1. PRD Skill 生成 `artifacts/<work item> 需求整理 (PRD).md` 后，对话中出现 PRD 产物卡片。
2. HLD Skill 生成 `artifacts/<work item> 方案设计 (HLD).md` 后，用户可以打开 Markdown 内容。
3. LLD Skill Round 3 发起 `ARTIFACT_REVIEW` 时，交互框展示 LLD 草稿 Markdown。
4. 用户选择“需要修改”并填写意见后，Bridge resume context 包含 artifact id 和 comment。
5. Skill 更新 Markdown 文件后，artifact version 增加，对话中显示新版本。

## 风险

- 旧数据库里已有 content-only artifact，需要迁移或兼容只读展示。
- 文件 mtime/size 不可靠，建议使用 content hash。
- 多文件输出时主产物选择需稳定：优先 `artifact_title` 匹配，其次最新 Markdown。
- Runtime event 可能早于文件写完，最终仍以节点结束后的文件扫描为准。

## 验证记录

本提交只新增设计和实施计划，未改运行代码。

- typecheck: skip
- tests: skip
- build: skip
- evidence: n/a
