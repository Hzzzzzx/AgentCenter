export type AgentRunnerKind = "opencode-native" | "omo" | (string & {})
export type AgentRunnerMode = "native" | "omo" | "auto"

export type AgentRunnerCapability =
  | "task_tool_sync"
  | "task_tool_background"
  | "task_resume"
  | "task_hook_result"
  | "manual_result"

export type AgentTaskHandle = {
  runner: AgentRunnerKind
  runId: string
  nodeId: string
  command?: string
  taskId?: string
}

export type AgentLaunchInput = {
  runId: string
  nodeId: string
  label: string
  agent: string
  prompt: string
  command: string
  taskId?: string
}

export type AgentLaunchPlan = {
  runner: AgentRunnerKind
  tool: string
  handle: AgentTaskHandle
  args: Record<string, string | boolean | string[] | undefined>
  instructions: string[]
  note?: string
}

export type AgentRunner = {
  kind: AgentRunnerKind
  label: string
  capabilities: AgentRunnerCapability[]
  launch: (input: AgentLaunchInput) => AgentLaunchPlan
  parseCommand?: (value: unknown) => { runId: string; nodeId: string } | undefined
}
