import { WorkspaceControlError } from "./errors"

const SegmentPattern = /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/

export type SegmentKind = "tenantId" | "projectId" | "workItemId" | "userId" | "sessionId" | "snapshotId"

export function safeSegment(kind: SegmentKind, value: string): string {
  if (!SegmentPattern.test(value) || value === "." || value === "..") {
    throw new WorkspaceControlError(
      "invalid_segment",
      `${kind} must be an opaque id segment using letters, numbers, dot, underscore, or dash`,
    )
  }
  return value
}
