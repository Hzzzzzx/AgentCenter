import path from "node:path"
import { safeSegment } from "./segment"

export type WorkspaceControlEnv = Record<string, string | undefined>

export type WorkspaceControlConfig = {
  runtimeRoot: string
  devTenantId: string
  devProjectId: string
  devUserId: string
}

export function loadWorkspaceControlConfig(
  env: WorkspaceControlEnv = process.env,
  cwd = process.cwd(),
): WorkspaceControlConfig {
  const runtimeRoot = path.resolve(cwd, env.AGENTCENTER_RUNTIME_ROOT ?? "runtime-workspaces")
  return {
    runtimeRoot,
    devTenantId: safeSegment("tenantId", env.AGENTCENTER_DEV_TENANT_ID ?? "local-tenant"),
    devProjectId: safeSegment("projectId", env.AGENTCENTER_DEV_PROJECT_ID ?? "demo-project"),
    devUserId: safeSegment("userId", env.AGENTCENTER_DEV_USER_ID ?? "local-user"),
  }
}
