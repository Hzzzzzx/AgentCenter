// Enums
export type WorkItemType = 'FE' | 'US' | 'TASK' | 'WORK' | 'BUG' | 'VULN'
export type WorkItemStatus = 'BACKLOG' | 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE'
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
export type WorkflowStatus = 'PENDING' | 'RUNNING' | 'BLOCKED' | 'FAILED' | 'COMPLETED' | 'CANCELLED'
export type WorkflowNodeStatus = 'PENDING' | 'RUNNING' | 'WAITING_CONFIRMATION' | 'FAILED' | 'COMPLETED' | 'SKIPPED'
export type ConfirmationStatus = 'PENDING' | 'IN_CONVERSATION' | 'RESOLVED' | 'REJECTED' | 'CANCELLED' | 'EXPIRED'
export type SessionType = 'GENERAL' | 'WORK_ITEM'
export type SessionStatus = 'ACTIVE' | 'ARCHIVED' | 'FAILED'
export type RuntimeEventType = 'STATUS' | 'ASSISTANT_DELTA' | 'SKILL_STARTED' | 'SKILL_COMPLETED' | 'MCP_CALL' | 'PERMISSION_REQUIRED' | 'ERROR' | 'CONFIRMATION_CREATED'
export type ArtifactType = 'MARKDOWN' | 'JSON' | 'PATCH' | 'LOG' | 'REPORT'
export type ConfirmationRequestType = 'CONFIRM' | 'APPROVAL' | 'INPUT_REQUIRED' | 'DECISION' | 'EXCEPTION' | 'PERMISSION'
export type ConfirmationActionType = 'ENTER_SESSION' | 'APPROVE' | 'REJECT' | 'SUPPLEMENT' | 'CHOOSE' | 'RETRY' | 'SKIP'

// DTOs
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
  workflowInstanceId: string | null
  workflowNodeInstanceId: string | null
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
