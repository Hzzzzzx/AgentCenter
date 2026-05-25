# 1026 Frontend Feature Cards

Status: discussion draft
Source branch/worktree: `/Users/hzz/workspace/AgentCenter`
Source frontend: `agentcenter-web`
Target branch: `codex/opencode-native-base`
Last updated: 2026-05-25

This document turns the 1026 Vue frontend into page-level feature cards. It is
for product discussion: decide which UI shells and function points should be
rebuilt on the OpenCode-native Web base, and which old implementation details
should be discarded.

## How To Use

For each card, mark the desired direction:

- `Keep shell`: the page layout or visible structure should resemble 1026.
- `Keep function`: the capability matters, but the OpenCode-native UI can be
  different.
- `Drop`: do not bring this page/capability forward.
- `Defer`: useful later, but not part of the next phase.

Default migration principle:

```text
Use OpenCode native conversation/file/session UI first.
Extract AgentCenter product semantics from 1026.
Do not port the old Vue/Java Bridge shell directly.
```

## Summary Table

| Card | 1026 page/surface | Main purpose | Initial migration call |
| --- | --- | --- | --- |
| F0 | App Shell | Three-panel workbench frame | Keep function, redesign shell on OpenCode-native |
| F1 | Home Overview | Work item overview and batch workflow start | Keep function, likely redesign |
| F2 | Board View | Kanban by workflow node state | Keep function, defer or compact |
| F3 | Conversation Workbench | Custom chat/workflow console | Keep selected functions, do not keep shell |
| F4 | Right Panel | Confirmation/detail/artifact side panel | Keep function, redesign as native side surface |
| F5 | Workflow Config | Workflow definition and skill-stage mapping | Keep function, defer |
| F6 | Project Context Settings | Project/space/iteration sync and selection | Keep function, redesign as workspace/project settings |
| F7 | Skill Management | Project Skill catalog and lifecycle | Keep function, redesign |
| F8 | MCP Management | Project MCP catalog and health/actions | Keep function, redesign |
| F9 | Runtime Resources | Lightweight runtime Skill scan status | Merge into diagnostics/settings |
| F10 | Runtime Settings | Run mode, debug, batch limits, runtime status | Keep selected settings, simplify |

## F0: App Shell

Source:

- `agentcenter-web/src/App.vue`
- `agentcenter-web/src/components/shell/AppShell.vue`
- `agentcenter-web/src/components/shell/LeftSidebar.vue`
- `agentcenter-web/src/components/shell/RightPanel.vue`

What it shows:

- Fixed title bar, left navigation/session list, central workbench, right panel,
  status bar.
- Left nav includes `首页`, `任务看板`, `任务编排`, plus settings menu entries.
- Right panel can switch between pending confirmations, work item details, and
  artifact preview.

User tasks:

- Move between workbench pages.
- Open general sessions and work-item sessions.
- Inspect selected work item details without leaving the current page.
- Keep confirmations and artifacts visible beside the main work surface.

Data dependencies:

- Session list.
- Selected work item.
- Selected artifact.
- Project context.
- Pending confirmations.

OpenCode-native migration call:

- Keep the product idea of a persistent project/workspace context and side
  detail surface.
- Do not port the three-panel Vue layout wholesale.
- In OpenCode Web, prefer adding a small AgentCenter project/workspace rail or
  panel around native session/file UI.

Decision needed:

- Should AgentCenter have a persistent right panel in the first visible version,
  or should details open as native dialogs/drawers first?

## F1: Home Overview

Source:

- `agentcenter-web/src/views/HomeOverview.vue`
- `agentcenter-web/src/stores/workItems.ts`
- `agentcenter-web/src/stores/workItemWorkflowProjection.ts`

What it shows:

- Work item statistics by type: `FE`, `US`, `TASK`, `WORK`, `BUG`, `VULN`.
- Current workflow stage summary and node completion rate.
- Filters by type/status.
- Paginated work item list.
- Batch start workflow action for selected type.

User tasks:

- See what work exists in the selected project/iteration.
- Find work items by type and status.
- Start one workflow or batch-start initial workflows.
- Enter a work-item conversation.

Data dependencies:

- `GET /work-items`
- `GET /work-items/overview`
- `POST /work-items/:id/start-workflow`
- `POST /work-items/start-workflows`
- Runtime setting: workflow run mode and batch limit.

OpenCode-native migration call:

- Keep function: work item overview is a real AgentCenter product surface.
- Redesign shell: make it a project/workspace landing view, not a replacement
  for OpenCode's native session page.
- Batch start should wait until workflow state and workspace isolation are real.

Decision needed:

- Is the first OpenCode-native landing page a work item dashboard, or should it
  open directly into a selected workspace/session and show work items later?

## F2: Board View

Source:

- `agentcenter-web/src/views/BoardView.vue`
- `agentcenter-web/src/components/workitem/BoardNodeCard.vue`

What it shows:

- Kanban columns by workflow node status:
  `PENDING`, `RUNNING`, `READY`, `WAITING_CONFIRMATION`, `FAILED`,
  `COMPLETED`, `SKIPPED`.
- Cards show work item code/type/title, current phase, dynamic step count,
  recovery count, pending confirmation count, and latest summary.

User tasks:

- Scan where each work item is blocked or running.
- Select a work item to inspect details.

Data dependencies:

- Work item list.
- Workflow projection derived from work item workflow summary.

OpenCode-native migration call:

- Keep function: useful for multi-work-item operations.
- Defer shell: not necessary before workspace entry, conversation stability,
  and resource management are correct.
- Could return as a compact project overview tab.

Decision needed:

- Is kanban important in the first usable demo, or should it wait until
  workflows are running for real?

## F3: Conversation Workbench

Source:

- `agentcenter-web/src/views/ConversationWorkbench.vue`
- `agentcenter-web/src/components/conversation/*`
- `agentcenter-web/src/components/conversation/projection/*`
- `agentcenter-web/src/stores/runtime.ts`
- `agentcenter-web/src/stores/sessions.ts`

What it shows:

- Custom conversation page with work item title, workflow state, message list,
  node control bar, interaction bar, runtime events, prompt debug overlay, and
  composer.
- Custom projection maps messages + runtime events + confirmations into
  assistant turns, steps, tool cards, decision gates, artifacts, and errors.
- Supports cancel/pause, continue current node, workflow restart, artifact
  auto-preview, and confirmation resolution.

User tasks:

- Continue a work-item or general session.
- Intervene in a running workflow.
- Submit answers to confirmation/input/exception prompts.
- Open generated artifacts.
- Debug prompt payloads.

Data dependencies:

- `GET /agent-sessions`
- `POST /agent-sessions`
- `GET /agent-sessions/:id/messages`
- `POST /agent-sessions/:id/messages`
- `POST /agent-sessions/:id/cancel`
- `GET /agent-sessions/:id/events` SSE
- Confirmation APIs.
- Artifact APIs.
- Workflow instance/version APIs.
- Session runtime resource status.

OpenCode-native migration call:

- Do not keep shell: this conflicts with the current strategy to inherit
  OpenCode native conversation/message/tool UI.
- Keep selected functions:
  - confirmation/interaction semantics;
  - artifact auto-open or artifact references;
  - workflow continue/restart semantics;
  - event replay/dedup/final sync lessons;
  - `CONTINUE_CURRENT` idea, but rebuild as native workflow state capsule/tool.
- Drop or defer prompt debug overlay unless debugging native workflow injection.

Decision needed:

- Which 1026 interaction cards should appear inside OpenCode's native message
  stream, and which should be side-panel actions?

## F4: Right Panel

Source:

- `agentcenter-web/src/components/shell/RightPanel.vue`
- `agentcenter-web/src/components/confirmation/ConfirmationPanel.vue`
- `agentcenter-web/src/components/conversation/ArtifactViewer.vue`

What it shows:

- Tabs: `待确认`, `详情`, `产物预览`.
- Confirmation count badge.
- Work item workflow detail timeline.
- Workflow versions.
- Selected artifact preview with expand/collapse behavior.

User tasks:

- Resolve pending confirmations.
- Inspect selected work item's workflow progress.
- Open and review generated artifacts without leaving current context.

Data dependencies:

- Pending confirmations.
- Selected work item and workflow projection.
- Workflow versions.
- Selected artifact.

OpenCode-native migration call:

- Keep function strongly: this is a good AgentCenter addition around native
  OpenCode.
- Redesign shell: likely as a native right-side project panel or drawer.
- Artifact preview should prefer OpenCode's existing file/preview primitives
  where possible.

Decision needed:

- Should pending confirmations be global in a side panel, inline in chat, or
  both?

## F5: Workflow Config

Source:

- `agentcenter-web/src/views/WorkflowConfig.vue`
- `agentcenter-web/src/stores/workflows.ts`
- `agentcenter-web/src/api/workflows.ts`

What it shows:

- Workflow definitions for a project.
- Stage list mapped to Skill names.
- Input policy and output artifact type per stage.
- Dynamic action / event-driven confirmation concepts.
- Mermaid preview of Agent flow.
- Unavailable Skill warnings.

User tasks:

- Edit workflow blueprint stages.
- Map stages to available Skills.
- Save a new workflow definition version.
- Understand how Agent can dynamically add actions during runtime.

Data dependencies:

- `GET /workflow-definitions?projectId=...`
- `PUT /workflow-definitions/:id`
- Skill catalog.

OpenCode-native migration call:

- Keep function, defer implementation.
- This should probably become project/workflow settings after Skill registry
  exists.
- Do not block first visible OpenCode-native experience on this page.

Decision needed:

- Do we expose workflow editing early, or ship fixed workflow templates first?

## F6: Project Context Settings

Source:

- `agentcenter-web/src/views/ProjectContextSettings.vue`
- `agentcenter-web/src/api/projectDataProviders.ts`
- `agentcenter-web/src/stores/runtimeSettings.ts`
- `agentcenter-web/src/types/projectContext.ts`

What it shows:

- Project configuration list.
- Project / CloudeReq project / space / iteration selectors.
- Save current context.
- Sync external project data.
- Add custom project draft.

User tasks:

- Pick the active enterprise project context.
- Sync external work item data.
- Save the context used by home/workflow/resource pages.

Data dependencies:

- Project data provider settings.
- Project data snapshot.
- Active provider and scope.

OpenCode-native migration call:

- Keep function, but rename around product workspace/project concepts.
- This is closely related to user isolation and workspace registry.
- Should not remain just local runtime settings.

Decision needed:

- What is the first visible product entry: `Project`, `Workspace`, or
  `Work Item`?

## F7: Skill Management

Source:

- `agentcenter-web/src/views/SkillManagement.vue`
- `agentcenter-web/src/api/runtimeResources.ts`

What it shows:

- Project Skill metrics: installed, enabled, invalid.
- Search and invalid-only filter.
- Skill list with name, display name, version, source, relative path, updated
  time, description, status.
- Upload Skill ZIP.
- Update Skill ZIP.
- Enable/disable Skill.
- Delete Skill.
- Refresh Skill registry.

User tasks:

- See which Skills are available to the current project.
- Install or update project Skills.
- Disable broken or unwanted Skills.
- Find invalid Skills and recover them.

Data dependencies:

- `GET /projects/:projectId/runtime/skills`
- `GET /projects/:projectId/runtime/skills/catalog`
- `POST /projects/:projectId/runtime/skills/upload`
- `PUT /projects/:projectId/runtime/skills/:skillId/zip`
- `POST /projects/:projectId/runtime/skills/:skillId/enable`
- `POST /projects/:projectId/runtime/skills/:skillId/disable`
- `DELETE /projects/:projectId/runtime/skills/:skillId`
- `POST /projects/:projectId/runtime/skills/refresh`

OpenCode-native migration call:

- Keep function strongly.
- Redesign UI around project/workspace registry and OpenCode-compatible Skill
  sources.
- Fix old pain points explicitly:
  - upload must be durable and refresh-safe;
  - validation result must be visible;
  - enabled state must be project/workspace scoped;
  - workflow runs must snapshot skill version.

Decision needed:

- Should first Skill UI be full management, or read-only catalog + upload only?

## F8: MCP Management

Source:

- `agentcenter-web/src/views/McpManagement.vue`
- `agentcenter-web/src/api/runtimeResources.ts`

What it shows:

- Project MCP metrics: server count, enabled count, total tools, failed count.
- Search by MCP name or health message.
- MCP list with server type, status, tool count, health, last checked time.
- Import config.
- Enable/disable server.
- Test connection.
- Refresh tools.
- Refresh all.

User tasks:

- See project tool connections.
- Import MCP config.
- Enable/disable MCP servers.
- Test health and refresh tool snapshots.

Data dependencies:

- `GET /projects/:projectId/runtime/mcps`
- `POST /projects/:projectId/runtime/mcps/import`
- `POST /projects/:projectId/runtime/mcps/:mcpId/enable`
- `POST /projects/:projectId/runtime/mcps/:mcpId/disable`
- `POST /projects/:projectId/runtime/mcps/:mcpId/test`
- `POST /projects/:projectId/runtime/mcps/:mcpId/refresh-tools`
- `POST /projects/:projectId/runtime/mcps/refresh`

OpenCode-native migration call:

- Keep function strongly.
- Redesign around desired state and reconciler, not one-off UI toggles.
- Project/workspace MCP bindings should feed OpenCode native MCP runtime config.
- User secrets must be referenced, not copied.

Decision needed:

- Should first MCP UI allow enable/disable, or only show configured desired
  state and health?

## F9: Runtime Resources

Source:

- `agentcenter-web/src/views/RuntimeResources.vue`

What it shows:

- Lightweight Skill scan summary.
- Last refresh time.
- `.opencode/skills` scan path.
- Runtime Skill list from local scan.
- Refresh Skill button.

User tasks:

- Check whether runtime sees project Skills.
- Trigger a manual Skill scan.

Data dependencies:

- `GET /projects/:projectId/runtime/skills/catalog`
- `POST /projects/:projectId/runtime/skills/refresh`

OpenCode-native migration call:

- Do not keep as a separate primary page.
- Merge useful diagnostics into Skill management or runtime diagnostics.
- Keep the idea of showing effective runtime resources for a session.

Decision needed:

- Do we need a diagnostics page, or should diagnostics live inside Skill/MCP
  details?

## F10: Runtime Settings

Source:

- `agentcenter-web/src/views/RuntimeSettings.vue`
- `agentcenter-web/src/stores/runtimeSettings.ts`

What it shows:

- Auto-run workflow toggle.
- Prompt Debug panel toggle.
- Batch workflow start limit.
- Project data sync provider selection.
- Current workflow run mode.
- OpenCode Server status, server URL, working directory, workspace alignment.

User tasks:

- Choose manual-confirm vs auto-run workflow behavior.
- Enable/disable prompt debugging.
- Limit batch workflow starts.
- Select external project data provider.
- Check runtime status.

Data dependencies:

- Local storage.
- Project data provider settings.
- `GET /runtime/status`

OpenCode-native migration call:

- Keep selected settings.
- Move runtime status into diagnostics.
- Move project data provider under project/workspace settings.
- Re-evaluate auto-run as workflow policy, not local browser setting.

Decision needed:

- Which settings are user-level, project-level, workspace-level, and session/run
  level?

## Cross-Page Concepts Worth Preserving

These are more important than exact 1026 page layouts:

- Work item identity and type: `FE`, `US`, `TASK`, `WORK`, `BUG`, `VULN`.
- Workflow status and node status projection.
- Pending confirmation model.
- Artifact references and preview.
- Project context: project, space, iteration, external provider.
- Project/workspace Skill catalog.
- Project/workspace MCP catalog and health.
- Runtime event replay/dedup/final sync lessons.
- Workflow resume/continue semantics.

## Cross-Page Concepts To Avoid Porting Directly

- Old Vue three-panel shell as the permanent product frame.
- Custom conversation renderer as the primary chat UI.
- Java Bridge-specific REST/SSE payload assumptions.
- Browser-owned project/runtime state that should belong to server registries.
- Runtime resources as loose local files without project/workspace ownership.

## Suggested Next Discussion Pass

Use these labels when giving feedback:

```text
F1 keep shell / keep function / drop / defer
F3 keep interaction cards, but not chat shell
F7 keep function, redesign as project settings
F8 first version read-only plus health only
```

After that, turn the selected cards into an implementation plan for the
OpenCode-native branch.
