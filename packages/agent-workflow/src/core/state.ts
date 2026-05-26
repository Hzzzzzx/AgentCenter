import type { WorkflowDefinition } from "./definition"

export type WorkflowRunStatus = "running" | "waiting" | "failed" | "succeeded" | "cancelled"
export type WorkflowNodeStatus = "pending" | "running" | "waiting" | "succeeded" | "failed" | "skipped"
export type WorkflowAnswerSource = "question" | "manual"

export type WorkflowWait = {
  id: string
  reason: "user" | "permission" | "review" | "external"
  question: string
  choices: string[]
  requestedAt: number
  deviationId?: string
  answer?: string
  answerSource?: WorkflowAnswerSource
  answeredAt?: number
}

export type WorkflowEvidence = {
  title: string
  body: string
  createdAt: number
}

export type WorkflowArtifact = {
  id: string
  nodeId: string
  kind: "text" | "diff" | "test" | "report" | "log" | "external"
  title: string
  body: string
  createdAt: number
}

export type WorkflowEvent = {
  id: string
  type: string
  message: string
  createdAt: number
  data?: Record<string, unknown>
}

export type WorkflowDeviationRisk = "low" | "medium" | "high"
export type WorkflowDeviationAction = "continue" | "insert_step" | "skip_node" | "detach" | "change_plan"
export type WorkflowDeviationStatus = "recorded" | "waiting_approval" | "approved" | "rejected"

export type WorkflowDeviation = {
  id: string
  nodeId?: string
  action: WorkflowDeviationAction
  risk: WorkflowDeviationRisk
  reason: string
  proposal: string
  status: WorkflowDeviationStatus
  createdAt: number
  decidedAt?: number
  decision?: string
}

export type WorkflowNodeRun = {
  nodeId: string
  status: WorkflowNodeStatus
  attempts: number
  runner?: string
  taskCallId?: string
  taskId?: string
  input?: unknown
  output?: unknown
  summary?: string
  error?: string
  wait?: WorkflowWait
  lastWait?: WorkflowWait
  evidence: WorkflowEvidence[]
  updatedAt: number
}

export type WorkflowRun = {
  id: string
  version: number
  workflowId: string
  sessionId: string
  title: string
  task: string
  definition: WorkflowDefinition
  status: WorkflowRunStatus
  nodes: Record<string, WorkflowNodeRun>
  artifacts: WorkflowArtifact[]
  deviations: WorkflowDeviation[]
  events: WorkflowEvent[]
  createdAt: number
  updatedAt: number
}

export type WorkflowNodeResult =
  | {
      status: "succeeded"
      summary: string
      output?: unknown
      artifacts?: WorkflowArtifact[]
    }
  | {
      status: "skipped"
      summary: string
      output?: unknown
    }
  | {
      status: "failed"
      summary: string
      error?: string
      output?: unknown
    }
  | {
      status: "needs_input"
      summary: string
      question: string
      choices?: string[]
      output?: unknown
    }

export function createWorkflowRun(input: {
  id?: string
  sessionId: string
  title?: string
  task: string
  definition: WorkflowDefinition
}): WorkflowRun {
  const now = Date.now()
  const id = input.id ?? createId("wf")
  return {
    id,
    version: 0,
    workflowId: input.definition.id,
    sessionId: input.sessionId,
    title: input.title?.trim() || input.definition.name,
    task: input.task,
    definition: input.definition,
    status: "running",
    nodes: Object.fromEntries(
      input.definition.nodes.map((node) => [
        node.id,
        {
          nodeId: node.id,
          status: "pending" as const,
          attempts: 0,
          evidence: [],
          updatedAt: now,
        },
      ]),
    ),
    artifacts: [],
    deviations: [],
    events: [
      {
        id: createId("evt"),
        type: "run.created",
        message: `Workflow run ${id} created`,
        createdAt: now,
      },
    ],
    createdAt: now,
    updatedAt: now,
  }
}

export function createId(prefix: string) {
  const random = Math.random().toString(36).slice(2, 8)
  return `${prefix}_${Date.now().toString(36)}_${random}`
}

export function pushEvent(run: WorkflowRun, input: { type: string; message: string; data?: Record<string, unknown> }) {
  run.events.push({
    id: createId("evt"),
    type: input.type,
    message: input.message,
    createdAt: Date.now(),
    data: input.data,
  })
  run.updatedAt = Date.now()
}
