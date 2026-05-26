import type { AgentLaunchInput, AgentLaunchPlan, AgentRunner } from "../core/runner"
import { parseWorkflowCommand } from "../core/scheduler"

export type OmoRunnerOptions = {
  enabled?: boolean
  taskToolName?: string
}

export type OmoRunnerAvailability = {
  available: boolean
  reason: string
}

export function resolveOmoRunnerAvailability(options: OmoRunnerOptions = {}): OmoRunnerAvailability {
  if (options.enabled) {
    return {
      available: true,
      reason: "OMO task bridge was explicitly enabled by plugin options.",
    }
  }
  return {
    available: false,
    reason: "OMO task bridge is not explicitly enabled, so the workflow runtime cannot safely assume OMO owns the task tool.",
  }
}

export function createOmoRunner(options: OmoRunnerOptions = {}): AgentRunner {
  return {
    kind: "omo",
    label: "OMO task bridge",
    capabilities: ["task_tool_sync", "task_resume", "task_hook_result", "manual_result"],
    parseCommand: parseWorkflowCommand,
    launch(input) {
      return createOmoLaunchPlan(input, options.taskToolName ?? "task")
    },
  }
}

function createOmoLaunchPlan(input: AgentLaunchInput, toolName: string): AgentLaunchPlan {
  return {
    runner: "omo",
    tool: toolName,
    handle: {
      runner: "omo",
      runId: input.runId,
      nodeId: input.nodeId,
      command: input.command,
      taskId: input.taskId,
    },
    args: {
      subagent_type: input.agent,
      description: input.label,
      command: input.command,
      task_id: input.taskId,
      prompt: input.prompt,
      run_in_background: false,
      load_skills: [],
    },
    instructions: [
      `Call OMO task bridge for node "${input.nodeId}".`,
      `   tool: ${toolName}`,
      `   subagent_type: ${input.agent}`,
      "   run_in_background: false",
      "   load_skills: []",
      `   description: ${input.label}`,
      `   command: ${input.command}`,
      input.taskId ? `   task_id: ${input.taskId}` : "   task_id: omit for first run",
      "   prompt: use the prompt returned by workflow_state_get for this node.",
      "   If this OMO bridge is unavailable, do not guess completion; use workflow_node_result or switch the plugin runner back to native.",
    ],
  }
}
