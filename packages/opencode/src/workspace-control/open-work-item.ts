import path from "node:path"
import type * as Permission from "../permission"
import type { WorkspaceControlConfig } from "./config"
import type { WorkspaceControlIdentity } from "./identity"
import type { WorkspaceControlRegistry } from "./registry"
import { ensureRuntimeScope, type RuntimeScope } from "./runtime-scope"
import { safeSegment } from "./segment"

export type OpenWorkItemRequest = {
  projectId?: string
  projectName?: string
  workItemId: string
  workItemTitle?: string
  sessionId: string
  resourceSnapshotId?: string
}

export type OpenWorkItemClientView = {
  tenantId: string
  projectId: string
  workItemId: string
  userId: string
  sessionId: string
  workspaceId: string
  resourceSnapshotId?: string
  allowedRootLabel: string
}

export type OpenWorkItemResult = {
  scope: RuntimeScope
  session: {
    directory: string
    permission: Permission.Ruleset
  }
  client: OpenWorkItemClientView
}

export function controlledSessionPermission(): Permission.Ruleset {
  return [{ permission: "external_directory", pattern: "*", action: "deny" }]
}

export async function openWorkItemWorkspace(input: {
  config: WorkspaceControlConfig
  identity: WorkspaceControlIdentity
  registry: WorkspaceControlRegistry
  request: OpenWorkItemRequest
}): Promise<OpenWorkItemResult> {
  const projectId = safeSegment("projectId", input.request.projectId ?? input.config.devProjectId)
  const workItemId = safeSegment("workItemId", input.request.workItemId)
  const sessionId = safeSegment("sessionId", input.request.sessionId)

  const scope = await ensureRuntimeScope(input.config, {
    tenantId: input.identity.tenantId,
    projectId,
    workItemId,
    userId: input.identity.userId,
    sessionId,
    resourceSnapshotId: input.request.resourceSnapshotId,
  })

  input.registry.upsertProject({
    tenantId: scope.tenantId,
    projectId: scope.projectId,
    name: input.request.projectName ?? scope.projectId,
  })
  input.registry.upsertWorkItem({
    tenantId: scope.tenantId,
    projectId: scope.projectId,
    workItemId: scope.workItemId,
    title: input.request.workItemTitle ?? scope.workItemId,
    status: "open",
  })
  input.registry.bindSession(scope)

  return {
    scope,
    session: {
      directory: scope.allowedRoot,
      permission: controlledSessionPermission(),
    },
    client: {
      tenantId: scope.tenantId,
      projectId: scope.projectId,
      workItemId: scope.workItemId,
      userId: scope.userId,
      sessionId: scope.sessionId,
      workspaceId: scope.workspaceId,
      resourceSnapshotId: scope.resourceSnapshotId,
      allowedRootLabel: path.relative(scope.layout.runtimeRoot, scope.allowedRoot),
    },
  }
}
