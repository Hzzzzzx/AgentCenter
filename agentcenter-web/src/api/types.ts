// Enums
export type WorkItemType = 'FE' | 'US' | 'TASK' | 'WORK' | 'BUG' | 'VULN'
export type WorkItemStatus = 'BACKLOG' | 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE'
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
export type WorkflowStatus = 'PENDING' | 'RUNNING' | 'BLOCKED' | 'FAILED' | 'COMPLETED' | 'CANCELLED'
export type WorkflowNodeStatus = 'PENDING' | 'RUNNING' | 'WAITING_CONFIRMATION' | 'FAILED' | 'COMPLETED' | 'SKIPPED'
export type ConfirmationStatus = 'PENDING' | 'IN_CONVERSATION' | 'RESOLVED' | 'REJECTED' | 'CANCELLED' | 'EXPIRED'
export type SessionType = 'GENERAL' | 'WORK_ITEM'
export type SessionStatus = 'ACTIVE' | 'ARCHIVED' | 'FAILED'
export type RuntimeEventType = 'STATUS' | 'ASSISTANT_DELTA' | 'SKILL_STARTED' | 'SKILL_COMPLETED' | 'MCP_CALL' | 'PERMISSION_REQUIRED' | 'ERROR' | 'CONFIRMATION_CREATED' | 'RESOURCE_REFRESH_STARTED' | 'RESOURCE_REFRESH_COMPLETED' | 'RESOURCE_REFRESH_FAILED' | 'SKILL_INSTALLED' | 'SKILL_UPDATED' | 'SKILL_DELETED' | 'SKILL_ENABLED' | 'SKILL_DISABLED' | 'MCP_ENABLED' | 'MCP_DISABLED' | 'MCP_HEALTH_CHECKED' | 'MCP_TOOLS_REFRESHED'
export type ArtifactType = 'MARKDOWN' | 'JSON' | 'PATCH' | 'LOG' | 'REPORT'
export type ConfirmationRequestType = 'CONFIRM' | 'APPROVAL' | 'INPUT_REQUIRED' | 'DECISION' | 'EXCEPTION' | 'PERMISSION'
export type ConfirmationActionType = 'ENTER_SESSION' | 'APPROVE' | 'REJECT' | 'SUPPLEMENT' | 'CHOOSE' | 'RETRY' | 'SKIP'

// Skill/MCP Management Enums
export type SkillStatus = 'ENABLED' | 'DISABLED' | 'INVALID' | 'UPDATING'
export type SkillSource = 'UPLOAD' | 'LOCAL_SCAN' | 'BUILTIN'
export type SkillVersionStatus = 'ACTIVE' | 'ARCHIVED' | 'FAILED'
export type McpServerType = 'STDIO' | 'HTTP' | 'SSE'
export type McpServerStatus = 'ENABLED' | 'DISABLED' | 'FAILED'
export type McpHealthStatus = 'OK' | 'FAILED' | 'UNKNOWN'
export type McpToolStatus = 'AVAILABLE' | 'UNAVAILABLE'
export type ResourceRefreshStatus = 'IDLE' | 'REFRESHING' | 'FAILED'
export type ResourceAuditAction = 'UPLOAD' | 'UPDATE' | 'DELETE' | 'ENABLE' | 'DISABLE' | 'TEST' | 'REFRESH'
export type ResourceAuditStatus = 'SUCCESS' | 'FAILED'
export type ResourceType = 'SKILL' | 'MCP'

// DTOs
export interface WorkflowSummaryNodeDto {
  id: string
  definitionName: string | null
  skillName: string | null
  status: WorkflowNodeStatus
}

export interface WorkflowSummaryStageDto {
  id: string
  stageKey: string | null
  name: string | null
  skillName: string | null
  status: WorkflowNodeStatus
  dynamicNodeCount: number
  recoveryCount: number
  pendingConfirmationCount: number
  latestSummary: string | null
}

export interface WorkflowSummaryDto {
  instanceId: string
  status: WorkflowStatus
  currentNodeInstanceId: string | null
  currentStageKey?: string | null
  nodes: WorkflowSummaryNodeDto[]
  stages?: WorkflowSummaryStageDto[]
}

export interface WorkItemDto {
  id: string
  code: string
  type: WorkItemType
  title: string
  description: string | null
  status: WorkItemStatus
  priority: Priority
  projectId: string | null
  spaceId: string | null
  iterationId: string | null
  assigneeUserId: string | null
  currentWorkflowInstanceId: string | null
  workflowSummary: WorkflowSummaryDto | null
  createdAt: string
  updatedAt: string
}

export interface CreateWorkItemRequest {
  type: WorkItemType
  title: string
  description?: string
  priority?: Priority
}

export interface WorkflowDefinitionDto {
  id: string
  workItemType: string
  name: string
  versionNo: number
  status: string
  isDefault: boolean
  nodes: WorkflowNodeDefinitionDto[]
}

export interface WorkflowNodeDefinitionDto {
  id: string
  nodeKey: string
  name: string
  orderNo: number
  skillName: string
  inputPolicy: string
  outputArtifactType: string
  requiredConfirmation: boolean
  stageKey?: string | null
  stageGoal?: string | null
  recommendedSkillNamesJson?: string | null
  allowDynamicActions?: boolean
  confirmationPolicy?: string | null
}

export interface UpdateWorkflowDefinitionRequest {
  name: string
  isDefault: boolean
  nodes: UpdateWorkflowNodeDefinitionRequest[]
}

export interface UpdateWorkflowNodeDefinitionRequest {
  nodeKey?: string | null
  name: string
  skillName: string
  inputPolicy: string
  outputArtifactType: ArtifactType
  requiredConfirmation: boolean
  stageKey?: string | null
  stageGoal?: string | null
  recommendedSkillNames?: string[]
  allowDynamicActions?: boolean
  confirmationPolicy?: string | null
}

export interface WorkflowInstanceDto {
  id: string
  workItemId: string
  workflowDefinitionId: string
  status: WorkflowStatus
  currentNodeInstanceId: string | null
  nodes: WorkflowNodeInstanceDto[]
  startedAt: string | null
  completedAt: string | null
}

export interface WorkflowNodeInstanceDto {
  id: string
  nodeDefinitionId: string
  status: WorkflowNodeStatus
  inputArtifactId: string | null
  outputArtifactId: string | null
  agentSessionId: string | null
  startedAt: string | null
  completedAt: string | null
  errorMessage: string | null
  nodeKind?: string | null
  origin?: string | null
  parentNodeInstanceId?: string | null
  stageKey?: string | null
  skillName?: string | null
  summary?: string | null
  reason?: string | null
  sequenceNo?: number | null
}

export interface AgentSessionDto {
  id: string
  sessionType: SessionType
  title: string | null
  workItemId: string | null
  workflowInstanceId: string | null
  runtimeType: string
  status: SessionStatus
  createdAt: string
}

export interface AgentMessageDto {
  id: string
  sessionId: string
  role: string
  content: string | null
  contentFormat: string
  status: string
  seqNo: number
  createdAt: string
}

export interface RuntimeEventDto {
  id: string
  sessionId: string | null
  workItemId: string | null
  workflowInstanceId: string | null
  workflowNodeInstanceId: string | null
  eventType: RuntimeEventType
  eventSource: string
  payloadJson: string | null
  createdAt: string
}

export interface RuntimeSkillDto {
  name: string
  description: string
  relativePath: string
  checksum: string
  updatedAt: string
}

export interface RuntimeSkillRefreshResponse {
  refreshedAt: string
  projectRoot: string
  skillsPath: string
  skillCount: number
  skills: RuntimeSkillDto[]
}

export interface ArtifactDto {
  id: string
  workItemId: string | null
  workflowInstanceId: string | null
  workflowNodeInstanceId: string | null
  artifactType: ArtifactType
  title: string
  content: string | null
  createdAt: string
}

export interface ConfirmationRequestDto {
  id: string
  requestType: ConfirmationRequestType
  status: ConfirmationStatus
  workItemId: string | null
  workItemCode?: string | null
  workItemType?: WorkItemType | null
  workItemTitle?: string | null
  workflowInstanceId: string | null
  workflowNodeInstanceId: string | null
  workflowNodeName?: string | null
  agentSessionId: string | null
  skillName: string | null
  title: string
  content: string | null
  contextSummary: string | null
  optionsJson: string | null
  priority: Priority
  createdAt: string
}

export interface ResolveConfirmationRequest {
  actionType: ConfirmationActionType
  comment?: string
  payload?: Record<string, unknown>
}

export interface StartWorkflowRequest {
  workflowDefinitionId?: string
  mode?: string
}

export interface StartWorkflowResponse {
  workflowInstance: WorkflowInstanceDto
  session: AgentSessionDto | null
  artifacts: ArtifactDto[]
  events: RuntimeEventDto[]
  confirmation: ConfirmationRequestDto | null
}

export interface SendMessageRequest {
  content: string
  contentFormat?: string
}

// ===== Skill/MCP Management DTOs =====

export interface RuntimeSkillDetailDto {
  id: string
  projectId: string
  name: string
  displayName: string | null
  description: string | null
  currentVersionId: string | null
  status: SkillStatus
  source: SkillSource
  relativePath: string
  checksum: string | null
  validationStatus: 'VALID' | 'INVALID'
  validationMessage: string | null
  createdBy: string | null
  createdAt: string
  updatedAt: string
  version: string | null
  referenceCount: number
}

export interface RuntimeSkillVersionDto {
  id: string
  skillId: string
  versionNo: string
  packageChecksum: string
  packageSize: number
  fileCount: number
  installedRelativePath: string
  manifestJson: string | null
  skillMdSummary: string | null
  status: SkillVersionStatus
  createdBy: string | null
  createdAt: string
}

export interface ProjectMcpServerDto {
  id: string
  projectId: string
  name: string
  serverType: McpServerType
  status: McpServerStatus
  configSummary: McpConfigSummaryDto | null
  configChecksum: string | null
  lastHealthStatus: McpHealthStatus
  lastHealthMessage: string | null
  lastCheckedAt: string | null
  createdBy: string | null
  createdAt: string
  updatedAt: string
  toolCount: number
}

export interface McpConfigSummaryDto {
  url?: string
  command?: string
  args?: string[]
  headers?: string[]
  envKeys?: string[]
}

export interface ProjectMcpToolSnapshotDto {
  id: string
  projectId: string
  mcpServerId: string
  toolName: string
  description: string | null
  inputSchemaJson: string | null
  snapshotVersion: number
  status: McpToolStatus
  scannedAt: string
}

export interface RuntimeResourceAuditDto {
  id: string
  projectId: string
  resourceType: ResourceType
  resourceId: string
  action: ResourceAuditAction
  status: ResourceAuditStatus
  summary: string | null
  detailJson: string | null
  createdBy: string | null
  createdAt: string
}

export interface SkillUploadResponse {
  skill: RuntimeSkillDetailDto
  refresh: {
    status: ResourceRefreshStatus
    eventId: string | null
  }
}

export interface SessionRuntimeResourceDto {
  projectId: string
  skillCount: number
  enabledMcpCount: number
  mcpToolCount: number
  lastRefreshedAt: string | null
  reloadRequired: boolean
}

export interface ResourceRefreshEvent {
  projectId: string
  resourceType: ResourceType
  resourceId: string
  resourceName: string
  status: 'SUCCESS' | 'FAILED'
  summary: string
  skillCount: number
  mcpToolCount: number
}
