# File-first Artifact Preview Fix Plan

> 日期：2026-05-18
> 状态：修复方案 / 待实施
> 对应设计：[docs/architecture/FILE-FIRST-ARTIFACT-PREVIEW-DESIGN.md](../../docs/architecture/FILE-FIRST-ARTIFACT-PREVIEW-DESIGN.md)

## 1. 目标

把当前“对话内容 / DB artifact 记录驱动预览”的实现，修复为“文件优先”的产物预览：

- 工作流、任务对话、通用对话中的任何文件创建、修改、覆盖、patch 都能形成产物索引。
- 产物不要求已完成；`IN_PROGRESS`、失败、等待确认、暂停时都可以预览。
- DB 不再是产物真源，只负责索引、版本、状态、审计和 legacy 缓存。
- 前端不再只找 message marker 或单个 `outputArtifactId`，而是展示当前上下文的产物列表。

## 2. 非目标

第一阶段不做以下事情：

- 不引入对象存储或远端文件系统。
- 不做生产级多租户权限模型。
- 不实现所有二进制格式的在线渲染。
- 不把 `.sisyphus/`、数据库记录、对话消息文本当作产品运行数据源。
- 不要求 Runtime 必须发完整 artifact event；Bridge 需要通过 manifest diff 兜底。

## 3. 当前问题拆解

| 问题 | 现象 | 修复方向 |
|------|------|----------|
| 产物源错误 | 主要从 `artifact.content` 或 message marker 读内容 | 文件系统成为真源，DB 存索引 |
| 捕获时机过晚 | 只有节点完成或 message 完成后才创建 artifact | 执行中事件捕获 + 节流扫描 + final scan |
| 覆盖范围不足 | 普通文件修改、patch 目标文件、失败节点半成品不可见 | baseline/final manifest diff 捕获所有新增/修改/删除 |
| 前端入口单一 | 只看 latest artifact candidate | 按 session/node/workflow 查询 artifact list |
| 状态表达不足 | 过大、二进制、缺失、更新中都像“没有内容” | 增加 lifecycle/preview status |
| 审阅绑定不稳 | `ARTIFACT_REVIEW` 可能绑定文本摘要 | 绑定 artifactId + versionId + contentHash |

## 4. GitNexus 影响面

已做影响面分析，实施时需要按风险分层推进：

| 符号 / 模块 | 风险 | 影响面 | 策略 |
|-------------|------|--------|------|
| `WorkflowCommandService.executeSkillOnNode` | CRITICAL | 工作流启动、继续、重试、确认恢复、节点推进主链路 | 不在该方法内做大重构；只添加执行前后 capture hook，并把复杂逻辑下沉到新服务 |
| `ArtifactCaptureService` | MEDIUM | Runtime event dispatch、assistant message projection、既有测试 | 保留 legacy 行为，新增 file-first 入口；避免一次性删除 message marker |
| `ArtifactController` | LOW | 产物详情读取 API | 扩展字段和读取策略，保持旧响应兼容 |
| 前端 `ConversationWorkbench.vue` / `ArtifactViewer.vue` | MEDIUM | 对话工作台产物展示 | 先兼容旧 latest artifact，再引入 artifact list 和状态渲染 |

如果后续 GitNexus 对上述符号返回 HIGH/CRITICAL 新风险，需要先停下来同步风险再继续改动。

## 5. 实施阶段

### Phase 0：测试基线与用例固化

目标：先写出能证明修复有效的用例，避免只修 UI。

后端测试建议：

- `RuntimeFileArtifactServiceTest`
  - 新文件被捕获。
  - 已有文件被修改后产生更新。
  - 空文件形成 artifact，`previewStatus=EMPTY`。
  - 大文件形成 artifact，`previewStatus=TOO_LARGE`。
  - 越界路径不读取，`previewStatus=OUT_OF_WORKSPACE`。
- `WorkflowCommandService` 相关集成测试
  - 节点失败后仍捕获已写文件。
  - 节点 `IN_PROGRESS` 期间 runtime event 触发 artifact update。
- `ArtifactControllerTest`
  - 文件型 artifact 优先读 workspace 文件。
  - legacy content artifact 仍可读。

前端测试建议：

- artifact list 中多个文件都展示。
- `UPDATING`、`TOO_LARGE`、`UNSUPPORTED`、`MISSING` 状态有明确 UI。
- message marker 不存在时，artifact event 仍能生成预览入口。

### Phase 1：数据库与 DTO 扩展

修改范围：

- `agentcenter-bridge/src/main/resources/db/migration/`
- `ArtifactEntity`
- `ArtifactMapper`
- `ArtifactDto`
- 相关 repository / fixture / test

建议新增 migration：

```text
Vxx__extend_artifact_file_preview_metadata.sql
```

字段：

- `source_kind`
- `relative_path`
- `mime_type`
- `size_bytes`
- `content_hash`
- `lifecycle_status`
- `preview_status`
- `latest_version_id`
- `metadata_json`
- `updated_at`

建议新增表：

```text
artifact_version(id, artifact_id, version_no, relative_path, storage_uri,
                 size_bytes, content_hash, lifecycle_status, preview_status,
                 captured_at, metadata_json)
```

兼容要求：

- 旧数据默认 `source_kind=LEGACY_MESSAGE_BLOCK` 或 `WORKFLOW_OUTPUT_LEGACY`。
- 旧 `content` 字段保留，不作为文件型 artifact 的主数据。

### Phase 2：新增文件捕获服务

新增后端服务：

```text
RuntimeFileArtifactService
ArtifactManifestService
ArtifactPreviewPolicy
ArtifactPathPolicy
```

职责：

- `captureBaseline(context)`：记录执行前 manifest。
- `capturePath(context, path, reason)`：Runtime event 驱动的单文件捕获。
- `captureDiff(context, baseline, reason)`：执行中 / 执行后的增量扫描。
- `readPreview(artifactId, versionId?)`：统一预览读取策略。

实现要点：

- 所有路径通过 `RuntimeWorkspace.resolve()` 得到 root 后再做 containment 校验。
- 默认排除 `.git/`、`node_modules/`、`target/`、`dist/`、构建缓存、数据库、密钥文件。
- 对 still-changing 文件设置 `lifecycleStatus=UPDATING`。
- 文件读取失败也 upsert artifact，并写入 `previewStatus`。
- upsert key 使用 `sessionId + workflowNodeInstanceId + relativePath`。

### Phase 3：接入 Runtime 事件与工作流

修改范围：

- `RuntimeEventEnvelopeDispatcher`
- `OpenCodeRuntimeEventTranslator`
- `WorkflowCommandService`
- 相关 runtime event DTO / tests

接入策略：

1. Runtime translator 继续保留 `PROCESS_TRACE kind=artifact`，但 payload 必须携带可解析的 file path、relative path、event id。
2. Dispatcher 收到 file / patch / artifact trace 后调用 `RuntimeFileArtifactService.capturePath(...)`。
3. `WorkflowCommandService` 在节点执行前创建 baseline。
4. 节点执行结束、失败、暂停、等待确认时统一执行 final diff scan。
5. 如果节点长时间运行，增加节流扫描或由 runtime event 触发局部扫描。

重要约束：

- 不要在 `executeSkillOnNode` 内铺开扫描、hash、预览策略；该方法风险为 CRITICAL，只放 hook。
- 不要删除现有 output artifact 逻辑；先改为 legacy fallback，等前端切到 artifact list 后再降权。

### Phase 4：扩展 Artifact API

修改范围：

- `ArtifactController`
- `ArtifactDto`
- `ArtifactMapper`
- `ArtifactService` 或新增 query service

接口：

- `GET /api/artifacts?sessionId=...`
- `GET /api/artifacts?workflowInstanceId=...`
- `GET /api/artifacts?workflowNodeInstanceId=...`
- `GET /api/artifacts/{id}`
- `GET /api/artifacts/{id}?versionId=...`

响应要求：

- list API 返回 metadata，不默认带完整 content。
- detail API 返回 preview content 或降级状态。
- 文件型 artifact 读取文件；legacy artifact 才读取 DB content。
- 所有错误都转成 `previewStatus` 和可展示 message，不返回空白。

### Phase 5：前端产物列表与预览状态

修改范围：

- `agentcenter-web/src/api/artifacts.ts`
- `agentcenter-web/src/types/artifact.ts`
- `ConversationWorkbench.vue`
- `ArtifactViewer.vue`
- `ArtifactEvidenceInline.vue`
- `conversationProjector`
- 相关测试

改造点：

1. 新增 artifact list 查询和事件更新 store。
2. `latestArtifactCandidate` 从“单个 artifactId”改为“当前上下文 artifact list + 用户选择态”。
3. 产物卡片展示多个文件和状态。
4. `ArtifactViewer` 支持：
   - 文本 / Markdown / JSON / 代码。
   - 图片。
   - `UPDATING` 提示。
   - `EMPTY`、`TOO_LARGE`、`UNSUPPORTED`、`MISSING`、`READ_ERROR` 降级。
5. 不再要求 assistant message 内存在 artifact marker 才显示产物。

### Phase 6：ARTIFACT_REVIEW 绑定真实产物

修改范围：

- workflow confirmation payload 生成逻辑。
- 前端确认卡片。
- `ARTIFACT_REVIEW` submit payload。

要求：

- review request 包含 `artifactId`、`versionId`、`contentHash`、`relativePath`。
- 用户确认时提交的是 artifact/version，而不是纯文本摘要。
- 如果文件仍是 `UPDATING`，确认卡显示版本可能继续变化，并允许刷新。
- 如果文件缺失或 hash 变化，确认卡要求重新确认。

### Phase 7：清理 legacy 优先级

在新路径稳定后：

- message marker 捕获保留为兼容，但不作为推荐产物路径。
- `outputArtifactId` 只作为 node primary artifact hint，不再驱动全部预览。
- 文档和提示词中移除“必须在对话内容里输出 artifact block 才能预览”的表述。

## 6. 文件级修改清单

| 文件 / 目录 | 修改说明 |
|-------------|----------|
| `agentcenter-bridge/src/main/resources/db/migration/` | 新增 artifact metadata 和 version migration |
| `agentcenter-bridge/src/main/java/com/agentcenter/bridge/domain/artifact/` | 扩展实体、枚举、版本对象 |
| `agentcenter-bridge/src/main/java/com/agentcenter/bridge/application/ArtifactCaptureService.java` | 降级为 legacy capture，并转调文件捕获服务 |
| `agentcenter-bridge/src/main/java/com/agentcenter/bridge/application/RuntimeFileArtifactService.java` | 新增文件优先捕获服务 |
| `agentcenter-bridge/src/main/java/com/agentcenter/bridge/application/ArtifactPreviewPolicy.java` | 新增预览类型和限制策略 |
| `agentcenter-bridge/src/main/java/com/agentcenter/bridge/runtime/RuntimeWorkspace.java` | 复用并补足安全路径能力 |
| `agentcenter-bridge/src/main/java/com/agentcenter/bridge/application/RuntimeEventEnvelopeDispatcher.java` | Runtime file / patch / artifact event 接入捕获 |
| `agentcenter-bridge/src/main/java/com/agentcenter/bridge/runtime/opencode/OpenCodeRuntimeEventTranslator.java` | 确保 event payload 带 file path / relative path |
| `agentcenter-bridge/src/main/java/com/agentcenter/bridge/application/WorkflowCommandService.java` | 增加 baseline/final scan hook |
| `agentcenter-bridge/src/main/java/com/agentcenter/bridge/web/ArtifactController.java` | 增加 list API 和文件型 preview read |
| `agentcenter-web/src/api/artifacts.ts` | 新增 list/detail/version API |
| `agentcenter-web/src/types/artifact.ts` | 扩展 source/lifecycle/preview/version 字段 |
| `agentcenter-web/src/components/ArtifactViewer.vue` | 多类型预览和降级状态 |
| `agentcenter-web/src/components/ArtifactEvidenceInline.vue` | 多产物卡片和状态展示 |
| `agentcenter-web/src/views/ConversationWorkbench.vue` | 从 latest artifact 切到 artifact list + selection |

## 7. 验证计划

后端：

```bash
cd agentcenter-bridge
./mvnw test
```

前端：

```bash
cd agentcenter-web
npm run typecheck
npm run test
```

集成验证：

1. 启动 Bridge 和 Web。
2. 发起一个 workflow node，让 Runtime 写入 `artifacts/design.md`，写入过程中停顿。
3. 确认前端在节点完成前展示 `UPDATING` 产物并可预览当前内容。
4. 修改同一文件，确认 artifact version 或 updatedAt 变化。
5. 让节点失败，确认失败前写出的文件仍在产物列表。
6. 写入图片、大文件、空文件，确认 UI 显示对应 preview status。
7. 留存 Playwright 截图到 `.sisyphus/evidence/file-first-artifact-preview-*.png`。

## 8. 回滚策略

- 新 schema 字段只扩展不删除，旧接口保持兼容。
- feature flag 可控制前端是否启用 artifact list。
- 如果文件捕获异常，可临时关闭 Runtime event capture 和 diff scan，回退到 legacy message marker / output artifact。
- legacy artifact 数据不迁移删除，避免影响已有会话回放。

## 9. 交付顺序建议

1. 后端 schema + DTO + controller list/read，保持旧逻辑可跑。
2. 新增 `RuntimeFileArtifactService` 和单元测试。
3. 接 Runtime event 捕获。
4. 接 Workflow baseline/final scan。
5. 前端 artifact list 和 viewer 降级态。
6. `ARTIFACT_REVIEW` 绑定 artifact/version。
7. 清理文档、提示词和 legacy 优先级。

## 10. 开放问题

| 问题 | 建议默认值 |
|------|------------|
| 是否展示工作区内所有已存在文件 | 不展示，只展示本次执行新增/修改/删除 |
| 同一路径跨节点修改是否合并 | 后端按节点建 artifact，前端可按路径聚合 |
| 大文件读取阈值 | 第一阶段 2 MB，与当前 controller 行为保持接近 |
| 二进制预览 | 第一阶段 metadata + 打开/下载入口，图片优先支持 |
| 版本是否保存完整内容 | 第一阶段不保存完整快照，只保存 hash/metadata；后续按需要引入 snapshot |

## 11. 完成定义

- 任意 Runtime 文件输出 / 修改能形成 artifact index。
- 前端不依赖对话 marker 即可展示产物。
- 未完成产物可预览，并有 `UPDATING` 状态。
- 不可预览产物不消失，而是显示明确原因。
- 工作流确认绑定真实 artifact/version。
- 后端 Maven test、前端 typecheck/test 通过。
- UI evidence 留存到 `.sisyphus/evidence/`。
