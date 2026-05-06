import { del, get, post, uploadFile, uploadFilePut } from './client'
import type {
  RuntimeSkillDetailDto,
  SkillUploadResponse,
  ProjectMcpServerDto,
  ProjectMcpToolSnapshotDto,
  RuntimeResourceAuditDto,
  SessionRuntimeResourceDto,
  RuntimeSkillRefreshResponse,
} from './types'

// ===== Skill APIs =====
export const skillApi = {
  list: (projectId: string) => get<RuntimeSkillDetailDto[]>(`/projects/${projectId}/runtime/skills`),
  get: (projectId: string, skillId: string) => get<RuntimeSkillDetailDto>(`/projects/${projectId}/runtime/skills/${skillId}`),
  upload: (projectId: string, file: File) => uploadFile<SkillUploadResponse>(`/projects/${projectId}/runtime/skills/upload`, file),
  updateZip: (projectId: string, skillId: string, file: File) => uploadFilePut<SkillUploadResponse>(`/projects/${projectId}/runtime/skills/${skillId}/zip`, file),
  enable: (projectId: string, skillId: string) => post<RuntimeSkillDetailDto>(`/projects/${projectId}/runtime/skills/${skillId}/enable`),
  disable: (projectId: string, skillId: string) => post<RuntimeSkillDetailDto>(`/projects/${projectId}/runtime/skills/${skillId}/disable`),
  delete: (projectId: string, skillId: string) => del<void>(`/projects/${projectId}/runtime/skills/${skillId}`),
  refresh: (projectId: string) => post<RuntimeSkillRefreshResponse>(`/projects/${projectId}/runtime/skills/refresh`),
  audits: (projectId: string, skillId: string) => get<RuntimeResourceAuditDto[]>(`/projects/${projectId}/runtime/skills/${skillId}/audits`),
}

// ===== MCP APIs =====
export const mcpApi = {
  list: (projectId: string) => get<ProjectMcpServerDto[]>(`/projects/${projectId}/runtime/mcps`),
  get: (projectId: string, mcpId: string) => get<ProjectMcpServerDto>(`/projects/${projectId}/runtime/mcps/${mcpId}`),
  importConfig: (projectId: string) => post<ProjectMcpServerDto[]>(`/projects/${projectId}/runtime/mcps/import`),
  enable: (projectId: string, mcpId: string) => post<ProjectMcpServerDto>(`/projects/${projectId}/runtime/mcps/${mcpId}/enable`),
  disable: (projectId: string, mcpId: string) => post<ProjectMcpServerDto>(`/projects/${projectId}/runtime/mcps/${mcpId}/disable`),
  test: (projectId: string, mcpId: string) => post<ProjectMcpServerDto>(`/projects/${projectId}/runtime/mcps/${mcpId}/test`),
  refreshTools: (projectId: string, mcpId: string) => post<ProjectMcpToolSnapshotDto[]>(`/projects/${projectId}/runtime/mcps/${mcpId}/refresh-tools`),
  refresh: (projectId: string) => post<void>(`/projects/${projectId}/runtime/mcps/refresh`),
  audits: (projectId: string, mcpId: string) => get<RuntimeResourceAuditDto[]>(`/projects/${projectId}/runtime/mcps/${mcpId}/audits`),
}

// ===== Session Resource Status =====
export const sessionResourceApi = {
  getStatus: (sessionId: string) => get<SessionRuntimeResourceDto>(`/agent-sessions/${sessionId}/runtime-resources`),
}

// Keep backward compatibility
export const runtimeResourceApi = {
  listSkills: () => get<RuntimeSkillRefreshResponse>('/runtime/skills'),
  refreshSkills: () => post<RuntimeSkillRefreshResponse>('/runtime/skills/refresh'),
}