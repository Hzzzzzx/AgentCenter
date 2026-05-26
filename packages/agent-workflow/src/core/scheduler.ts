import type { WorkflowNode } from "./definition"
import {
  createId,
  pushEvent,
  type WorkflowDeviationAction,
  type WorkflowDeviationRisk,
  type WorkflowAnswerSource,
  type WorkflowArtifact,
  type WorkflowNodeResult,
  type WorkflowRun,
} from "./state"

export type ReadyWorkflowNode = {
  runId: string
  nodeId: string
  label: string
  agent: string
  prompt: string
  command: string
  taskId?: string
}

export function getReadyNodes(run: WorkflowRun): ReadyWorkflowNode[] {
  advanceRun(run)
  if (run.status === "failed" || run.status === "succeeded" || run.status === "cancelled") return []
  return run.definition.nodes
    .filter((node) => node.type === "agent")
    .filter((node) => run.nodes[node.id]?.status === "pending")
    .filter((node) => dependenciesSucceeded(run, node))
    .map((node) => ({
      runId: run.id,
      nodeId: node.id,
      label: node.label,
      agent: requireAgent(node),
      prompt: buildNodePrompt(run, node),
      command: workflowCommand(run.id, node.id),
      taskId: run.nodes[node.id]?.taskId,
    }))
}

export function advanceRun(run: WorkflowRun) {
  const joined = settleJoinNodes(run)
  recomputeRunStatus(run)
  return joined
}

export function startNode(run: WorkflowRun, input: { nodeId: string; callId?: string; taskId?: string; runner?: string }) {
  const node = requireNodeRun(run, input.nodeId)
  if (node.status === "running") return
  node.status = "running"
  node.attempts += 1
  node.runner = input.runner ?? node.runner
  node.taskCallId = input.callId
  node.taskId = input.taskId ?? node.taskId
  node.updatedAt = Date.now()
  run.status = "running"
  pushEvent(run, {
    type: "node.started",
    message: `Node ${input.nodeId} started`,
    data: { nodeId: input.nodeId, callId: input.callId },
  })
}

export function recordNodeResult(
  run: WorkflowRun,
  input: { nodeId: string; result: WorkflowNodeResult; taskId?: string; callId?: string },
) {
  const node = requireNodeRun(run, input.nodeId)
  if (isStaleNodeResult(node, input)) {
    pushEvent(run, {
      type: "node.result_ignored",
      message: `Ignored stale result for node ${input.nodeId}`,
      data: { nodeId: input.nodeId, callId: input.callId, taskId: input.taskId, status: node.status },
    })
    advanceRun(run)
    return false
  }
  const now = Date.now()
  node.taskId = input.taskId ?? node.taskId
  node.summary = input.result.summary
  node.output = input.result.output
  node.updatedAt = now

  if (input.result.status === "needs_input") {
    node.status = "waiting"
    node.wait = {
      id: createId("wait"),
      reason: "user",
      question: input.result.question,
      choices: input.result.choices ?? [],
      requestedAt: now,
    }
    pushEvent(run, {
      type: "node.waiting",
      message: `Node ${input.nodeId} is waiting for user input`,
      data: { nodeId: input.nodeId, question: input.result.question },
    })
    advanceRun(run)
    return true
  }

  if (input.result.status === "failed") {
    node.error = input.result.error ?? input.result.summary
    if (shouldRetryNode(run, input)) {
      node.status = "pending"
      node.summary = input.result.summary
      node.output = input.result.output
      pushEvent(run, {
        type: "node.retry",
        message: `Node ${input.nodeId} failed and will retry`,
        data: { nodeId: input.nodeId, attempts: node.attempts, error: node.error },
      })
      advanceRun(run)
      return true
    }
    node.status = "failed"
    pushEvent(run, {
      type: "node.failed",
      message: `Node ${input.nodeId} failed`,
      data: { nodeId: input.nodeId, error: node.error },
    })
    advanceRun(run)
    return true
  }

  if (input.result.status === "skipped") {
    node.status = "skipped"
    node.evidence.push({
      title: `${input.nodeId} skipped`,
      body: input.result.summary,
      createdAt: now,
    })
    pushEvent(run, {
      type: "node.skipped",
      message: `Node ${input.nodeId} skipped`,
      data: { nodeId: input.nodeId },
    })
    advanceRun(run)
    return true
  }

  node.status = "succeeded"
  node.evidence.push({
    title: `${input.nodeId} result`,
    body: input.result.summary,
    createdAt: now,
  })
  if (input.result.artifacts) run.artifacts.push(...input.result.artifacts)
  pushEvent(run, {
    type: "node.succeeded",
    message: `Node ${input.nodeId} succeeded`,
    data: { nodeId: input.nodeId },
  })
  advanceRun(run)
  return true
}

export function answerNodeWait(run: WorkflowRun, input: { nodeId: string; answer: string; source: WorkflowAnswerSource }) {
  const node = requireNodeRun(run, input.nodeId)
  if (node.status !== "waiting" || !node.wait) {
    throw new Error(`Node ${input.nodeId} is not waiting for input`)
  }
  if (!input.answer.trim()) {
    throw new Error(`Workflow answer for node ${input.nodeId} cannot be empty`)
  }

  node.wait.answer = input.answer
  node.wait.answerSource = input.source
  node.wait.answeredAt = Date.now()
  if (node.wait.deviationId) {
    const deviation = run.deviations.find((item) => item.id === node.wait?.deviationId)
    if (deviation) {
      deviation.status = input.answer.toLowerCase().includes("reject") ? "rejected" : "approved"
      deviation.decision = input.answer
      deviation.decidedAt = Date.now()
    }
  }
  node.input = {
    previousInput: node.input,
    userAnswer: input.answer,
    question: node.wait.question,
  }
  node.lastWait = node.wait
  node.wait = undefined
  node.status = "pending"
  node.updatedAt = Date.now()
  pushEvent(run, {
    type: "node.answer",
    message: `Node ${input.nodeId} received user answer`,
    data: { nodeId: input.nodeId },
  })
  advanceRun(run)
}

export function requestWorkflowDeviation(
  run: WorkflowRun,
  input: {
    nodeId?: string
    action: WorkflowDeviationAction
    risk: WorkflowDeviationRisk
    reason: string
    proposal: string
    requiresUserApproval?: boolean
  },
) {
  const now = Date.now()
  const requiresApproval = input.requiresUserApproval ?? input.risk !== "low"
  const deviation = {
    id: createId("dev"),
    nodeId: input.nodeId,
    action: input.action,
    risk: input.risk,
    reason: input.reason,
    proposal: input.proposal,
    status: requiresApproval ? ("waiting_approval" as const) : ("recorded" as const),
    createdAt: now,
  }
  run.deviations.push(deviation)
  pushEvent(run, {
    type: requiresApproval ? "deviation.waiting_approval" : "deviation.recorded",
    message: `Deviation ${deviation.id} requested for ${input.action}`,
    data: { deviationId: deviation.id, nodeId: input.nodeId, risk: input.risk },
  })

  if (!requiresApproval) {
    advanceRun(run)
    return deviation
  }

  const node = input.nodeId ? requireNodeRun(run, input.nodeId) : firstBlockableNode(run)
  node.status = "waiting"
  node.wait = {
    id: createId("wait"),
    reason: "review",
    deviationId: deviation.id,
    question: [
      `Workflow deviation requested: ${input.action}`,
      `Risk: ${input.risk}`,
      `Reason: ${input.reason}`,
      `Proposal: ${input.proposal}`,
      "Approve this deviation?",
    ].join("\n"),
    choices: ["approve", "reject"],
    requestedAt: now,
  }
  node.updatedAt = now
  advanceRun(run)
  return deviation
}

export function cancelRun(run: WorkflowRun) {
  run.status = "cancelled"
  run.updatedAt = Date.now()
  pushEvent(run, { type: "run.cancelled", message: `Workflow run ${run.id} cancelled` })
}

export function workflowCommand(runId: string, nodeId: string) {
  return `workflow:${runId}:${nodeId}`
}

export function parseWorkflowCommand(value: unknown) {
  if (typeof value !== "string") return
  const match = /^workflow:([^:]+):([^:]+)$/.exec(value)
  if (!match) return
  return { runId: match[1], nodeId: match[2] }
}

export function parseWorkflowTaskOutput(text: string): WorkflowNodeResult {
  const needsInput = parseTaggedJson(text, "workflow_needs_input")
  if (isRecord(needsInput) && typeof needsInput.question === "string") {
    return {
      status: "needs_input",
      summary: readString(needsInput.summary, "Node requested user input"),
      question: needsInput.question,
      choices: readStringArray(needsInput.choices),
      output: needsInput,
    }
  }

  const failed = parseTaggedJson(text, "workflow_failed")
  if (isRecord(failed)) {
    return {
      status: "failed",
      summary: readString(failed.summary, "Node failed"),
      error: readString(failed.error, undefined),
      output: failed,
    }
  }

  const result = parseTaggedJson(text, "workflow_result")
  if (isRecord(result)) {
    return {
      status: "succeeded",
      summary: readString(result.summary, "Node completed"),
      output: result,
      artifacts: readArtifacts(result.artifacts),
    }
  }

  return {
    status: "failed",
    summary: "Node did not return a workflow result block",
    error: text.trim().slice(0, 1200) || "Missing workflow result block",
    output: { text },
  }
}

export function parseTaskIdFromTaskOutput(text: string) {
  const direct = /^task_id:\s*([^\s(]+)/m.exec(text)
  if (direct?.[1]) return direct[1]

  const metadata = /<task_metadata>[\s\S]*?task_id:\s*([^\s<]+)[\s\S]*?<\/task_metadata>/i.exec(text)
  if (metadata?.[1]) return metadata[1]
}

export function renderRunSummary(run: WorkflowRun) {
  advanceRun(run)
  const ready = getReadyNodes(run)
  const waiting = Object.values(run.nodes).filter((node) => node.status === "waiting" && node.wait)
  const deviations = run.deviations.slice(-5)
  const lines = [
    `Workflow run: ${run.id}`,
    `Status: ${run.status}`,
    `Task: ${run.task}`,
    "",
    "Nodes:",
    ...run.definition.nodes.map((node) => {
      const state = run.nodes[node.id]
      const suffix = state?.wait ? ` (${state.wait.question})` : ""
      const task = state?.taskId ? ` taskId=${state.taskId}` : ""
      return `- ${node.id}: ${state?.status ?? "unknown"}${task}${suffix}`
    }),
    "",
    "Ready nodes:",
    ...(ready.length
      ? ready.flatMap((node) => [
          `- ${node.nodeId} -> call task with subagent_type="${node.agent}" and command="${node.command}"`,
          `  prompt: ${node.prompt.slice(0, 260).replaceAll("\n", " ")}...`,
        ])
      : ["- none"]),
    "",
    "Waiting:",
    ...(waiting.length
      ? waiting.map((node) => `- ${node.nodeId}: ${node.wait?.question.replaceAll("\n", " ")}`)
      : ["- none"]),
    "",
    "Recent deviations:",
    ...(deviations.length
      ? deviations.map((deviation) => `- ${deviation.id}: ${deviation.status} ${deviation.action} risk=${deviation.risk}`)
      : ["- none"]),
  ]
  return lines.join("\n")
}

function dependenciesSucceeded(run: WorkflowRun, node: WorkflowNode) {
  return (node.dependsOn ?? []).every((id) => {
    const status = run.nodes[id]?.status
    return status === "succeeded" || status === "skipped"
  })
}

function requireAgent(node: WorkflowNode) {
  if (node.agent) return node.agent
  throw new Error(`Workflow node ${node.id} has no agent`)
}

function requireNodeRun(run: WorkflowRun, nodeId: string) {
  const node = run.nodes[nodeId]
  if (!node) throw new Error(`Unknown workflow node: ${nodeId}`)
  return node
}

function isStaleNodeResult(
  node: ReturnType<typeof requireNodeRun>,
  input: { callId?: string; taskId?: string },
) {
  if (!input.callId && !input.taskId) return false
  if (node.status !== "running") return true
  if (input.callId && node.taskCallId && input.callId !== node.taskCallId) return true
  if (input.taskId && node.taskId && input.taskId !== node.taskId) return true
  return false
}

function shouldRetryNode(run: WorkflowRun, input: { nodeId: string; callId?: string; taskId?: string }) {
  if (!input.callId && !input.taskId) return false
  const node = requireNodeRun(run, input.nodeId)
  const definition = run.definition.nodes.find((item) => item.id === input.nodeId)
  return node.attempts > 0 && node.attempts <= (definition?.retry?.max ?? 0)
}

function firstBlockableNode(run: WorkflowRun) {
  const node = Object.values(run.nodes).find((item) => item.status === "running" || item.status === "pending")
  if (node) return node
  throw new Error("No workflow node can hold the deviation approval")
}

function settleJoinNodes(run: WorkflowRun) {
  if (run.status === "failed" || run.status === "succeeded" || run.status === "cancelled") return false
  const readyJoins = run.definition.nodes.filter(
    (node) => node.type === "join" && run.nodes[node.id]?.status === "pending" && dependenciesSucceeded(run, node),
  )
  readyJoins.forEach((node) => {
    const state = requireNodeRun(run, node.id)
    state.status = "succeeded"
    state.summary = (node.dependsOn ?? [])
      .map((id) => {
        const dependency = run.nodes[id]
        return `${id}: ${dependency?.status ?? "unknown"} - ${dependency?.summary ?? "n/a"}`
      })
      .join("\n")
    state.output = { joined: node.dependsOn ?? [], summary: state.summary }
    state.updatedAt = Date.now()
    pushEvent(run, {
      type: "node.joined",
      message: `Join node ${node.id} completed`,
      data: { nodeId: node.id, dependencies: node.dependsOn ?? [] },
    })
  })
  return readyJoins.length > 0
}

function recomputeRunStatus(run: WorkflowRun) {
  if (run.status === "cancelled") return
  const states = Object.values(run.nodes).map((node) => node.status)
  if (states.some((state) => state === "failed")) {
    run.status = "failed"
    return
  }
  if (states.every((state) => state === "succeeded" || state === "skipped")) {
    if (run.status === "succeeded") return
    run.status = "succeeded"
    pushEvent(run, { type: "run.succeeded", message: `Workflow run ${run.id} succeeded` })
    return
  }
  if (states.some((state) => state === "running")) {
    run.status = "running"
    return
  }
  if (hasReadyAgentNode(run)) {
    run.status = "running"
    return
  }
  if (states.some((state) => state === "waiting")) {
    run.status = "waiting"
    return
  }
  run.status = "running"
}

function hasReadyAgentNode(run: WorkflowRun) {
  return run.definition.nodes.some(
    (node) => node.type === "agent" && run.nodes[node.id]?.status === "pending" && dependenciesSucceeded(run, node),
  )
}

function buildNodePrompt(run: WorkflowRun, node: WorkflowNode) {
  const upstream = (node.dependsOn ?? [])
    .map((id) => {
      const state = run.nodes[id]
      if (!state) return undefined
      return [`## Upstream node: ${id}`, `status: ${state.status}`, `summary: ${state.summary ?? "n/a"}`].join("\n")
    })
    .filter((value): value is string => Boolean(value))
    .join("\n\n")
  const answered = run.nodes[node.id]?.wait?.answer
    ? `\n\n## User answer for this node\n${run.nodes[node.id]?.wait?.answer}`
    : ""

  return [
    `You are running workflow node "${node.id}" for workflow run "${run.id}".`,
    `Original user task: ${run.task}`,
    "",
    "You are still a normal OpenCode subagent inside this workflow node.",
    "If you need user input while executing this node, call the native question tool yourself and continue this same node after the user answers.",
    "Do not ask for generic approval when the task is low risk, scoped, and you can proceed from available context.",
    "",
    "You must finish with exactly one of these tagged JSON blocks:",
    '<workflow_result>{"summary":"what you completed","artifacts":[]}</workflow_result>',
    '<workflow_needs_input>{"summary":"why the workflow parent must ask instead","question":"question for user","choices":["choice A","choice B"]}</workflow_needs_input>',
    '<workflow_failed>{"summary":"what failed","error":"actionable error"}</workflow_failed>',
    "",
    "Prefer native question for OpenCode runs. Use workflow_needs_input only as a compatibility fallback when you cannot continue this node through the native question tool.",
    "",
    "Node task:",
    node.prompt,
    "",
    `Output contract: ${node.outputContract ?? "Concise structured result with evidence."}`,
    upstream ? `\n${upstream}` : "",
    answered,
  ].join("\n")
}

function parseTaggedJson(text: string, tag: string) {
  const match = new RegExp(`<${tag}>\\s*([\\s\\S]*?)\\s*</${tag}>`, "i").exec(text)
  if (!match) return
  try {
    return JSON.parse(match[1] ?? "{}") as unknown
  } catch {
    return
  }
}

function readString(value: unknown, fallback: string | undefined) {
  if (typeof value === "string" && value.trim()) return value
  return fallback ?? ""
}

function readStringArray(value: unknown) {
  if (!Array.isArray(value)) return []
  return value.filter((item): item is string => typeof item === "string" && Boolean(item.trim()))
}

function readArtifacts(value: unknown): WorkflowArtifact[] {
  if (!Array.isArray(value)) return []
  return value.flatMap((item) => {
    if (!isRecord(item)) return []
    const nodeId = readString(item.nodeId, "unknown")
    const title = readString(item.title, "Artifact")
    const body = readString(item.body, "")
    if (!body) return []
    return [
      {
        id: createId("art"),
        nodeId,
        kind: readArtifactKind(item.kind),
        title,
        body,
        createdAt: Date.now(),
      },
    ]
  })
}

function readArtifactKind(value: unknown): WorkflowArtifact["kind"] {
  if (value === "diff" || value === "test" || value === "report" || value === "log" || value === "external") {
    return value
  }
  return "text"
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === "object" && !Array.isArray(value)
}
