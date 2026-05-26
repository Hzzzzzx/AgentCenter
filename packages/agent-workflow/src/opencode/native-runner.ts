import type { AgentLaunchInput, AgentLaunchPlan, AgentRunner } from "../core/runner"
import { parseWorkflowCommand } from "../core/scheduler"

export function createOpenCodeNativeRunner(): AgentRunner {
  return {
    kind: "opencode-native",
    label: "OpenCode native task tool",
    capabilities: ["task_tool_sync", "task_resume", "task_hook_result", "manual_result"],
    parseCommand: parseWorkflowCommand,
    launch(input) {
      return createNativeLaunchPlan(input)
    },
  }
}

function createNativeLaunchPlan(input: AgentLaunchInput): AgentLaunchPlan {
  return {
    runner: "opencode-native",
    tool: "task",
    handle: {
      runner: "opencode-native",
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
    },
    instructions: [
      `Call OpenCode task tool for node "${input.nodeId}".`,
      `   subagent_type: ${input.agent}`,
      `   description: ${input.label}`,
      `   command: ${input.command}`,
      input.taskId ? `   task_id: ${input.taskId}` : "   task_id: omit for first run",
      "   prompt: use the prompt returned by workflow_state_get for this node.",
    ],
  }
}
