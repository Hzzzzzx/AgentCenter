import { getReadyNodes, renderRunSummary, type ReadyWorkflowNode } from "../core/scheduler"
import type { AgentRunner } from "../core/runner"
import type { WorkflowRun } from "../core/state"
import { createOpenCodeNativeRunner } from "./native-runner"
import type { OpenCodeRunnerSelection } from "./runner-selection"

const defaultRunner = createOpenCodeNativeRunner()

export function renderRunCreated(run: WorkflowRun, selection?: OpenCodeRunnerSelection) {
  return [
    "Workflow run created.",
    "",
    renderRunnerSelection(selection),
    "",
    renderRunSummary(run),
    "",
    renderTaskInstructions(getReadyNodes(run), selection?.runner),
  ].join("\n")
}

export function renderTaskInstructions(nodes: ReadyWorkflowNode[], runner: AgentRunner = defaultRunner) {
  if (!nodes.length) return "No ready nodes. Call workflow_state_get after user input or task completion."
  return [
    "Next action for the main Agent:",
    `Runner: ${runner.kind} (${runner.label})`,
    ...nodes.flatMap((node, index) => [
      "",
      `${index + 1}. ${runner.launch(node).instructions.join("\n")}`,
    ]),
  ].join("\n")
}

export function renderWorkflowInteractionInstructions(run: WorkflowRun) {
  const waiting = Object.values(run.nodes).filter((node) => node.status === "waiting" && node.wait)
  if (!waiting.length) return ""
  const questionPayload = {
    questions: waiting.map((node) => ({
      header: `Workflow ${node.nodeId}`.slice(0, 30),
      question: node.wait?.question ?? `Workflow node ${node.nodeId} needs your decision.`,
      options: questionOptions(node.wait?.choices ?? []),
      multiple: false,
    })),
  }
  return [
    "Native OpenCode question required before continuing waiting workflow nodes.",
    "Do not ask the user in plain text and do not call workflow_answer before the question tool returns.",
    "",
    "Call the built-in question tool with:",
    "```json",
    JSON.stringify(questionPayload, null, 2),
    "```",
    "",
    "After the question tool returns, call workflow_answer once per waiting node using the selected label:",
    ...waiting.map(
      (node, index) =>
        `- node ${index + 1}: workflow_answer run_id=${run.id} node_id=${node.nodeId} answer=<selected label> source=question`,
    ),
    "Use source=manual only when the user already answered in normal chat and explicitly asked you to record that answer.",
  ].join("\n")
}

export function renderRuns(runs: WorkflowRun[]) {
  if (!runs.length) return "No workflow runs found for this session."
  return runs
    .map((run) => {
      const waiting = Object.values(run.nodes).find((node) => node.status === "waiting")
      return [
        `- ${run.id}: ${run.status}`,
        `  task: ${run.task}`,
        waiting?.wait ? `  waiting: ${waiting.nodeId} -> ${waiting.wait.question}` : undefined,
      ]
        .filter((line): line is string => Boolean(line))
        .join("\n")
    })
    .join("\n")
}

export function renderWorkflowCapsule(runs: WorkflowRun[]) {
  if (!runs.length) return "No active workflow runs."
  return [
    "Current workflow capsule:",
    ...runs.slice(0, 5).flatMap((run) => {
      const ready = getReadyNodes(run)
      const waiting = Object.values(run.nodes).filter((node) => node.status === "waiting" && node.wait)
      const taskIds = Object.values(run.nodes)
        .filter((node) => node.taskId)
        .map((node) => `${node.nodeId}=${node.taskId}`)
      const runners = Object.values(run.nodes)
        .filter((node) => node.runner)
        .map((node) => `${node.nodeId}=${node.runner}`)
      const lastEvents = run.events.slice(-3).map((event) => event.type).join(", ") || "none"
      return [
        `- ${run.id}: ${run.status}; ready=[${ready.map((node) => node.nodeId).join(", ") || "none"}]; waiting=[${
          waiting.map((node) => node.nodeId).join(", ") || "none"
        }]`,
        `  taskIds: ${taskIds.join(", ") || "none"}`,
        `  runners: ${runners.join(", ") || "none"}`,
        `  lastEvents: ${lastEvents}`,
        ...waiting.map((node) => `  native question required ${node.nodeId}: ${node.wait?.question.replaceAll("\n", " ")}`),
      ]
    }),
  ].join("\n")
}

function questionOptions(choices: string[]) {
  if (choices.length) {
    return choices.map((choice) => ({
      label: choice,
      description: `Choose ${choice} for this workflow decision.`,
    }))
  }
  return [
    { label: "Continue", description: "Continue this workflow node with the current plan." },
    { label: "Adjust", description: "Do not continue yet; provide a changed direction." },
    { label: "Cancel", description: "Cancel or stop this workflow path." },
  ]
}

function renderRunnerSelection(selection: OpenCodeRunnerSelection | undefined) {
  if (!selection) return "Runner: opencode-native (OpenCode native task tool)"
  return [
    `Runner: ${selection.runner.kind} (${selection.runner.label})`,
    `Requested runner: ${selection.requested}`,
    selection.fallbackFrom ? `Fallback: ${selection.fallbackFrom} -> ${selection.selected}` : undefined,
    selection.note ? `Runner note: ${selection.note}` : undefined,
  ]
    .filter((line): line is string => Boolean(line))
    .join("\n")
}
