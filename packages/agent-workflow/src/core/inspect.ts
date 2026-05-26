import type { WorkflowDefinition, WorkflowNode } from "./definition"
import { advanceRun, getReadyNodes } from "./scheduler"
import type { WorkflowRun } from "./state"

export type WorkflowNodeInspection = {
  nodeId: string
  label: string
  type: string
  status: string
  agent?: string
  ready: boolean
  blockedReasons: string[]
  dependencies: { nodeId: string; status: string; satisfied: boolean }[]
  taskId?: string
  runner?: string
  waitQuestion?: string
  summary?: string
}

export type WorkflowRunInspection = {
  runId: string
  status: string
  readyNodeIds: string[]
  waitingNodeIds: string[]
  failedNodeIds: string[]
  nodes: WorkflowNodeInspection[]
}

export function inspectWorkflowRun(run: WorkflowRun): WorkflowRunInspection {
  advanceRun(run)
  const ready = new Set(getReadyNodes(run).map((node) => node.nodeId))
  const nodes = run.definition.nodes.map((node) => inspectNode(run, node, ready))
  return {
    runId: run.id,
    status: run.status,
    readyNodeIds: nodes.filter((node) => node.ready).map((node) => node.nodeId),
    waitingNodeIds: nodes.filter((node) => node.status === "waiting").map((node) => node.nodeId),
    failedNodeIds: nodes.filter((node) => node.status === "failed").map((node) => node.nodeId),
    nodes,
  }
}

export function inspectWorkflowDefinition(definition: WorkflowDefinition) {
  const ids = new Set(definition.nodes.map((node) => node.id))
  return definition.nodes.map((node) => ({
    nodeId: node.id,
    label: node.label,
    type: node.type,
    agent: node.agent,
    dependencies: node.dependsOn ?? [],
    downstream: definition.nodes.filter((candidate) => candidate.dependsOn?.includes(node.id)).map((candidate) => candidate.id),
    missingDependencies: (node.dependsOn ?? []).filter((dependency) => !ids.has(dependency)),
  }))
}

export function renderWorkflowDebug(run: WorkflowRun, nodeId?: string) {
  const inspection = inspectWorkflowRun(run)
  const nodes = nodeId ? inspection.nodes.filter((node) => node.nodeId === nodeId) : inspection.nodes
  if (nodeId && !nodes.length) return `Workflow node not found: ${nodeId}`
  return [
    `Workflow debug: ${inspection.runId}`,
    `Status: ${inspection.status}`,
    `Ready: ${inspection.readyNodeIds.join(", ") || "none"}`,
    `Waiting: ${inspection.waitingNodeIds.join(", ") || "none"}`,
    `Failed: ${inspection.failedNodeIds.join(", ") || "none"}`,
    "",
    "Nodes:",
    ...nodes.map(renderNodeDebug),
  ].join("\n")
}

function inspectNode(run: WorkflowRun, node: WorkflowNode, ready: Set<string>): WorkflowNodeInspection {
  const state = run.nodes[node.id]
  const dependencies = (node.dependsOn ?? []).map((dependency) => {
    const status = run.nodes[dependency]?.status ?? "missing"
    return {
      nodeId: dependency,
      status,
      satisfied: status === "succeeded" || status === "skipped",
    }
  })
  return {
    nodeId: node.id,
    label: node.label,
    type: node.type,
    status: state?.status ?? "unknown",
    agent: node.agent,
    ready: ready.has(node.id),
    blockedReasons: blockedReasons(run, node, dependencies, ready.has(node.id)),
    dependencies,
    taskId: state?.taskId,
    runner: state?.runner,
    waitQuestion: state?.wait?.question,
    summary: state?.summary,
  }
}

function blockedReasons(
  run: WorkflowRun,
  node: WorkflowNode,
  dependencies: { nodeId: string; status: string; satisfied: boolean }[],
  ready: boolean,
) {
  const state = run.nodes[node.id]
  if (ready) return []
  if (run.status === "cancelled") return ["run is cancelled"]
  if (run.status === "failed" && state?.status !== "failed") return ["run has failed"]
  if (run.status === "succeeded") return ["run already succeeded"]
  if (!state) return ["node state is missing"]
  if (state.status === "waiting") return [`waiting for user: ${state.wait?.question ?? "no question recorded"}`]
  if (state.status === "running") return ["node is already running"]
  if (state.status === "succeeded" || state.status === "skipped") return [`node already ${state.status}`]
  if (state.status === "failed") return [`node failed: ${state.error ?? state.summary ?? "unknown error"}`]
  const blockedDependencies = dependencies.filter((dependency) => !dependency.satisfied)
  if (blockedDependencies.length) {
    return blockedDependencies.map((dependency) => `waiting for ${dependency.nodeId} (${dependency.status})`)
  }
  if (node.type !== "agent") return [`${node.type} nodes are handled by the scheduler`]
  return ["not ready for an unknown scheduler reason"]
}

function renderNodeDebug(node: WorkflowNodeInspection) {
  return [
    `- ${node.nodeId}: ${node.status}${node.ready ? " ready" : ""}`,
    `  type: ${node.type}${node.agent ? `; agent: ${node.agent}` : ""}`,
    `  dependencies: ${
      node.dependencies.map((dependency) => `${dependency.nodeId}=${dependency.status}`).join(", ") || "none"
    }`,
    `  blocked: ${node.blockedReasons.join("; ") || "none"}`,
    node.taskId ? `  taskId: ${node.taskId}` : undefined,
    node.runner ? `  runner: ${node.runner}` : undefined,
    node.waitQuestion ? `  waiting question: ${node.waitQuestion.replaceAll("\n", " ")}` : undefined,
    node.summary ? `  summary: ${node.summary.replaceAll("\n", " ")}` : undefined,
  ]
    .filter((line): line is string => Boolean(line))
    .join("\n")
}
