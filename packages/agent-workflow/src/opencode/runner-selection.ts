import type { AgentRunner, AgentRunnerKind, AgentRunnerMode } from "../core/runner"
import { createOmoRunner, resolveOmoRunnerAvailability } from "./omo-runner"
import { createOpenCodeNativeRunner } from "./native-runner"

export type OpenCodeRunnerOptions = {
  runner?: AgentRunnerMode
  omoTaskBridge?: boolean
  omoTaskTool?: string
}

export type OpenCodeRunnerSelection = {
  requested: AgentRunnerMode
  selected: AgentRunnerKind
  runner: AgentRunner
  fallbackFrom?: AgentRunnerKind
  note?: string
}

export function selectOpenCodeRunner(options: OpenCodeRunnerOptions = {}): OpenCodeRunnerSelection {
  const requested = options.runner ?? "native"
  if (requested === "native") {
    return {
      requested,
      selected: "opencode-native",
      runner: createOpenCodeNativeRunner(),
    }
  }

  const availability = resolveOmoRunnerAvailability({
    enabled: options.omoTaskBridge,
    taskToolName: options.omoTaskTool,
  })
  if (availability.available) {
    return {
      requested,
      selected: "omo",
      runner: createOmoRunner({ taskToolName: options.omoTaskTool }),
      note: availability.reason,
    }
  }

  return {
    requested,
    selected: "opencode-native",
    runner: createOpenCodeNativeRunner(),
    fallbackFrom: "omo",
    note: `${requested === "omo" ? "OMO runner requested" : "Auto runner requested"}: ${availability.reason} Falling back to OpenCode Native runner.`,
  }
}

export function readRunnerMode(value: unknown): AgentRunnerMode | undefined {
  if (value === "native" || value === "opencode-native") return "native"
  if (value === "omo") return "omo"
  if (value === "auto") return "auto"
}
