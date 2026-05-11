/**
 * Conversation UI Event Mapping — Projection Model
 *
 * This file defines the stable contract between raw runtime events and the UI.
 * The projector transforms messages + runtime events + confirmations into
 * ConversationTurnProjection[], which is what Vue components render.
 *
 * Design reference: docs/architecture/CONVERSATION-UI-EVENT-MAPPING-DESIGN.md
 *
 * Rules:
 * - Only merge same-object lifecycles (same toolCallId, confirmationId, messageId+partId).
 * - NEVER group by eventType or tool name.
 * - Sort by seqNo → createdAt → arrivalIndex → id.
 * - Tools/evidence/decisions hang under the steps that produced them.
 */

// ─── Turn-level types ────────────────────────────────────────────────

export type TurnStatus = 'running' | 'waiting_input' | 'completed' | 'failed'

export interface ConversationTurnProjection {
  turnId: string
  sessionId: string
  anchorMessageId?: string
  anchorCreatedAt?: string
  status: TurnStatus
  answer: AnswerProjection
  steps: ExecutionStep[]
  displayItems: ConversationDisplayItem[]
  currentAction?: CurrentActionProjection
  pendingInteraction?: InteractionProjection
  debugRefs: RawEventRef[]
}

export interface AnswerProjection {
  role: 'assistant'
  text: string
  /** Sources/artifacts referenced in the answer */
  sources?: ArtifactRef[]
  /** Which execution steps contributed text to this answer */
  generatedByStepIds: string[]
  /** Whether this answer is still streaming */
  streaming?: boolean
}

export interface CurrentActionProjection {
  label: string
  detail?: string
}

// ─── Ordered turn display items ──────────────────────────────────────

export type ConversationDisplayItem =
  | AgentActivityDisplayItem
  | AssistantMessageDisplayItem
  | InteractionRequestDisplayItem

export interface AgentActivityDisplayItem {
  type: 'agent-activity'
  id: string
  steps: ExecutionStep[]
  status: TurnStatus
  currentAction?: CurrentActionProjection
  collapsedByDefault: boolean
}

export interface AssistantMessageDisplayItem {
  type: 'assistant-message'
  id: string
  answer: AnswerProjection
}

export interface InteractionRequestDisplayItem {
  type: 'interaction-request'
  id: string
  interaction: InteractionProjection
}

// ─── Execution step ──────────────────────────────────────────────────

export type StepKind =
  | 'context'
  | 'reasoning'
  | 'tool'
  | 'artifact'
  | 'decision'
  | 'status'
  | 'error'
  | 'subtask'

export type StepStatus = 'pending' | 'running' | 'completed' | 'failed' | 'waiting_input'

export interface ExecutionStep {
  id: string
  order: number
  kind: StepKind
  title: string
  summary?: string
  status: StepStatus
  parentStepId?: string
  startedAt?: string
  completedAt?: string
  parts: StepPart[]
  rawEventRefs: RawEventRef[]
}

// ─── Step parts (discriminated union) ────────────────────────────────

export type StepPart =
  | TextPart
  | ReasoningSummaryPart
  | ToolInvocationPart
  | ArtifactEvidencePart
  | DecisionGatePart
  | StatusPart
  | ErrorPart
  | RawEventPart

export interface TextPart {
  type: 'text'
  text: string
  messageId?: string
  partId?: string
  rawEventRef?: RawEventRef
}

export interface ReasoningSummaryPart {
  type: 'reasoning'
  summary: string
  messageId?: string
  partId?: string
  defaultExpanded: false
  rawEventRef?: RawEventRef
}

export interface ToolInvocationPart {
  type: 'tool'
  toolCallId: string
  rawName: string
  displayName: string
  category?: 'read' | 'search' | 'list' | 'command' | 'skill' | 'tool'
  status: 'running' | 'completed' | 'failed'
  inputSummary?: string
  outputSummary?: string
  artifactRefs?: ArtifactRef[]
  rawPayloadRef: RawEventRef
  /** Short output, failure, permission, or artifact-producing tools default expanded */
  defaultExpanded: boolean
}

export interface ArtifactEvidencePart {
  type: 'artifact'
  artifactId?: string
  filePath?: string
  title: string
  summary?: string
  diffAvailable?: boolean
  artifactRef?: ArtifactRef
  rawEventRef?: RawEventRef
}

export interface DecisionGatePart {
  type: 'decision'
  confirmationId: string
  question: string
  prompt?: string
  options: DecisionOption[]
  recommended?: string
  status: 'waiting' | 'submitted' | 'resolved'
  requestType?: string
  interactionType?: string
  defaultExpanded: true
  rawEventRef?: RawEventRef
}

export interface StatusPart {
  type: 'status'
  label: string
  detail?: string
  rawEventRef?: RawEventRef
}

export interface ErrorPart {
  type: 'error'
  message: string
  detail?: string
  /** Errors are always expanded */
  defaultExpanded: true
  rawEventRef?: RawEventRef
}

export interface RawEventPart {
  type: 'raw'
  eventType: string
  title: string
  payload: Record<string, unknown>
  rawEventRef: RawEventRef
}

// ─── Interaction projection ──────────────────────────────────────────

export interface InteractionProjection {
  confirmationId: string
  question: string
  prompt?: string
  options: DecisionOption[]
  recommended?: string
  status: 'waiting' | 'submitted' | 'resolved'
}

export interface DecisionOption {
  value: string
  label: string
  description?: string
  consequence?: string
}

// ─── Shared value types ──────────────────────────────────────────────

export interface RawEventRef {
  eventId: string
  eventType: string
  seqNo?: number | null
}

export interface ArtifactRef {
  artifactId?: string
  title: string
  type?: string
  filePath?: string
}

// ─── Projector input contract ────────────────────────────────────────

export interface ProjectorInput {
  messages: ProjectorMessage[]
  runtimeEvents: ProjectorRuntimeEvent[]
  confirmations: ProjectorConfirmation[]
  streamingText: string
  activeSessionId: string | null
  running: boolean
}

export interface ProjectorMessage {
  id: string
  sessionId: string
  role: string
  content: string | null
  contentFormat: string
  status: string
  seqNo: number
  createdAt: string
  workflowNodeInstanceId: string | null
}

export interface ProjectorRuntimeEvent {
  id: string
  sessionId: string | null
  workItemId: string | null
  workflowInstanceId: string | null
  workflowNodeInstanceId: string | null
  eventType: string
  eventSource: string
  payloadJson: string | null
  seqNo?: number | null
  createdAt: string
}

export interface ProjectorConfirmation {
  id: string
  requestType: string
  status: string
  title: string
  content: string | null
  optionsJson: string | null
  interactionType: string | null
  agentSessionId: string | null
}
