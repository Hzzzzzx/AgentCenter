export type WorkspaceControlErrorCode =
  | "invalid_segment"
  | "invalid_path"
  | "path_escape"
  | "missing_path"
  | "invalid_runtime_root"

export class WorkspaceControlError extends Error {
  readonly code: WorkspaceControlErrorCode

  constructor(code: WorkspaceControlErrorCode, message: string) {
    super(message)
    this.name = "WorkspaceControlError"
    this.code = code
  }
}
