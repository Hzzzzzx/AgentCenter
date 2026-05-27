import { safeSegment } from "./segment"
import type { WorkspaceControlConfig, WorkspaceControlEnv } from "./config"

export type WorkspaceControlIdentity = {
  tenantId: string
  userId: string
}

export function resolveWorkspaceControlIdentity(input: {
  config: WorkspaceControlConfig
  env?: WorkspaceControlEnv
  headers?: Headers | Record<string, string | undefined>
}): WorkspaceControlIdentity {
  const env = input.env ?? process.env
  const headerUser = readHeader(input.headers, "x-agentcenter-user-id")
  const headerTenant = readHeader(input.headers, "x-agentcenter-tenant-id")

  return {
    tenantId: safeSegment("tenantId", headerTenant ?? env.AGENTCENTER_DEV_TENANT_ID ?? input.config.devTenantId),
    userId: safeSegment("userId", headerUser ?? env.AGENTCENTER_DEV_USER_ID ?? input.config.devUserId),
  }
}

function readHeader(headers: Headers | Record<string, string | undefined> | undefined, name: string) {
  if (!headers) return
  if (headers instanceof Headers) return headers.get(name) ?? undefined
  return headers[name] ?? headers[name.toLowerCase()]
}
