import fs from "node:fs/promises"
import path from "node:path"
import { createHash } from "node:crypto"
import type { WorkspaceControlConfig } from "./config"
import { WorkspaceControlError } from "./errors"
import { isPathInside } from "./path-guard"
import { safeSegment } from "./segment"

export type RuntimeScopeInput = {
  tenantId: string
  projectId: string
  workItemId: string
  userId: string
  sessionId: string
  resourceSnapshotId?: string
}

export type RuntimeLayout = {
  runtimeRoot: string
  projectRoot: string
  projectResourcesRoot: string
  projectSkillsRoot: string
  projectMcpRoot: string
  resourceSnapshotsRoot: string
  workItemRoot: string
  workItemStateRoot: string
  workItemArtifactsRoot: string
  workItemCanonicalRoot: string
  userRoot: string
  allowedRoot: string
  sessionsRoot: string
  runsRoot: string
  opencodeRoot: string
}

export type RuntimeScope = RuntimeScopeInput & {
  workspaceId: string
  allowedRoot: string
  layout: RuntimeLayout
}

export async function ensureRuntimeScope(
  config: WorkspaceControlConfig,
  input: RuntimeScopeInput,
): Promise<RuntimeScope> {
  const sanitized = sanitizeRuntimeScopeInput(input)
  const runtimeRoot = await ensureRoot(config.runtimeRoot)
  const layout = buildRuntimeLayout(runtimeRoot, sanitized)

  await Promise.all([
    fs.mkdir(layout.projectSkillsRoot, { recursive: true }),
    fs.mkdir(layout.projectMcpRoot, { recursive: true }),
    fs.mkdir(layout.resourceSnapshotsRoot, { recursive: true }),
    fs.mkdir(layout.workItemStateRoot, { recursive: true }),
    fs.mkdir(layout.workItemArtifactsRoot, { recursive: true }),
    fs.mkdir(layout.workItemCanonicalRoot, { recursive: true }),
    fs.mkdir(layout.allowedRoot, { recursive: true }),
    fs.mkdir(layout.sessionsRoot, { recursive: true }),
    fs.mkdir(layout.runsRoot, { recursive: true }),
    fs.mkdir(layout.opencodeRoot, { recursive: true }),
  ])

  const allowedRoot = await fs.realpath(layout.allowedRoot)
  if (!isPathInside(runtimeRoot, allowedRoot)) {
    throw new WorkspaceControlError("path_escape", "Resolved allowedRoot escaped runtime root")
  }

  return {
    ...sanitized,
    workspaceId: workspaceIdForRuntimeScope(sanitized),
    allowedRoot,
    layout: {
      ...layout,
      runtimeRoot,
      allowedRoot,
    },
  }
}

export function buildRuntimeLayout(runtimeRoot: string, input: RuntimeScopeInput): RuntimeLayout {
  const sanitized = sanitizeRuntimeScopeInput(input)
  const projectRoot = path.join(runtimeRoot, "tenants", sanitized.tenantId, "projects", sanitized.projectId)
  const projectResourcesRoot = path.join(projectRoot, "project-resources")
  const workItemRoot = path.join(projectRoot, "work-items", sanitized.workItemId)
  const userRoot = path.join(workItemRoot, "users", sanitized.userId)
  const allowedRoot = path.join(userRoot, "workspace")

  return {
    runtimeRoot,
    projectRoot,
    projectResourcesRoot,
    projectSkillsRoot: path.join(projectResourcesRoot, "skills"),
    projectMcpRoot: path.join(projectResourcesRoot, "mcp"),
    resourceSnapshotsRoot: path.join(projectResourcesRoot, "snapshots"),
    workItemRoot,
    workItemStateRoot: path.join(workItemRoot, "state"),
    workItemArtifactsRoot: path.join(workItemRoot, "artifacts"),
    workItemCanonicalRoot: path.join(workItemRoot, "canonical"),
    userRoot,
    allowedRoot,
    sessionsRoot: path.join(userRoot, "sessions"),
    runsRoot: path.join(userRoot, "runs"),
    opencodeRoot: path.join(allowedRoot, ".opencode"),
  }
}

export function workspaceIdForRuntimeScope(input: Pick<RuntimeScopeInput, "tenantId" | "projectId" | "workItemId" | "userId">): string {
  const value = [
    safeSegment("tenantId", input.tenantId),
    safeSegment("projectId", input.projectId),
    safeSegment("workItemId", input.workItemId),
    safeSegment("userId", input.userId),
  ].join("\0")
  return `wc_${createHash("sha256").update(value).digest("hex").slice(0, 24)}`
}

function sanitizeRuntimeScopeInput(input: RuntimeScopeInput): RuntimeScopeInput {
  return {
    tenantId: safeSegment("tenantId", input.tenantId),
    projectId: safeSegment("projectId", input.projectId),
    workItemId: safeSegment("workItemId", input.workItemId),
    userId: safeSegment("userId", input.userId),
    sessionId: safeSegment("sessionId", input.sessionId),
    resourceSnapshotId: input.resourceSnapshotId
      ? safeSegment("snapshotId", input.resourceSnapshotId)
      : undefined,
  }
}

async function ensureRoot(root: string): Promise<string> {
  const absolute = path.resolve(root)
  await fs.mkdir(absolute, { recursive: true })
  const realpath = await fs.realpath(absolute)
  if (!path.isAbsolute(realpath)) {
    throw new WorkspaceControlError("invalid_runtime_root", `Runtime root must resolve to an absolute path: ${root}`)
  }
  return realpath
}
