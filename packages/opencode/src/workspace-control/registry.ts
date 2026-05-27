import type { RuntimeScope } from "./runtime-scope"
import { safeSegment } from "./segment"

export type ProjectRecord = {
  tenantId: string
  projectId: string
  name: string
}

export type WorkItemRecord = {
  tenantId: string
  projectId: string
  workItemId: string
  title: string
  status: "open" | "running" | "blocked" | "done" | "archived"
}

export type SessionBindingRecord = {
  tenantId: string
  projectId: string
  workItemId: string
  userId: string
  sessionId: string
  workspaceId: string
  allowedRoot: string
  resourceSnapshotId?: string
  createdAt: number
  updatedAt: number
}

export class WorkspaceControlRegistry {
  private readonly projects = new Map<string, ProjectRecord>()
  private readonly workItems = new Map<string, WorkItemRecord>()
  private readonly sessions = new Map<string, SessionBindingRecord>()

  upsertProject(project: ProjectRecord): ProjectRecord {
    const record = {
      ...project,
      tenantId: safeSegment("tenantId", project.tenantId),
      projectId: safeSegment("projectId", project.projectId),
    }
    this.projects.set(projectKey(record.tenantId, record.projectId), record)
    return record
  }

  upsertWorkItem(workItem: WorkItemRecord): WorkItemRecord {
    const record = {
      ...workItem,
      tenantId: safeSegment("tenantId", workItem.tenantId),
      projectId: safeSegment("projectId", workItem.projectId),
      workItemId: safeSegment("workItemId", workItem.workItemId),
    }
    this.workItems.set(workItemKey(record.tenantId, record.projectId, record.workItemId), record)
    return record
  }

  listWorkItems(input: { tenantId: string; projectId: string }): WorkItemRecord[] {
    const tenantId = safeSegment("tenantId", input.tenantId)
    const projectId = safeSegment("projectId", input.projectId)
    return [...this.workItems.values()].filter(
      (item) => item.tenantId === tenantId && item.projectId === projectId,
    )
  }

  bindSession(scope: RuntimeScope, now = Date.now()): SessionBindingRecord {
    const existing = this.sessions.get(scope.sessionId)
    const record: SessionBindingRecord = {
      tenantId: scope.tenantId,
      projectId: scope.projectId,
      workItemId: scope.workItemId,
      userId: scope.userId,
      sessionId: scope.sessionId,
      workspaceId: scope.workspaceId,
      allowedRoot: scope.allowedRoot,
      resourceSnapshotId: scope.resourceSnapshotId,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
    }
    this.sessions.set(scope.sessionId, record)
    return record
  }

  getSessionBinding(sessionId: string): SessionBindingRecord | undefined {
    return this.sessions.get(safeSegment("sessionId", sessionId))
  }
}

function projectKey(tenantId: string, projectId: string) {
  return `${tenantId}:${projectId}`
}

function workItemKey(tenantId: string, projectId: string, workItemId: string) {
  return `${projectKey(tenantId, projectId)}:${workItemId}`
}
