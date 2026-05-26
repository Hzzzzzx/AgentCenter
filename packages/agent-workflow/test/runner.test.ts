import { describe, expect, test } from "bun:test"
import { createOpenCodeNativeRunner } from "../src/opencode/native-runner"
import { createOmoRunner, resolveOmoRunnerAvailability } from "../src/opencode/omo-runner"
import { selectOpenCodeRunner } from "../src/opencode/runner-selection"

const launchInput = {
  runId: "wf_test",
  nodeId: "planner",
  label: "Plan workflow",
  agent: "workflow_planner",
  prompt: "Create a plan.",
  command: "workflow:wf_test:planner",
}

describe("agent runners", () => {
  test("OpenCode Native runner emits resumable task instructions", () => {
    const runner = createOpenCodeNativeRunner()
    const plan = runner.launch(launchInput)

    expect(plan.runner).toBe("opencode-native")
    expect(plan.tool).toBe("task")
    expect(plan.args.subagent_type).toBe("workflow_planner")
    expect(plan.args.command).toBe("workflow:wf_test:planner")
    expect(plan.instructions.join("\n")).toContain("Call OpenCode task tool")
    expect(runner.parseCommand?.("workflow:wf_test:planner")).toEqual({ runId: "wf_test", nodeId: "planner" })
  })

  test("OMO availability is opt-in because the plugin cannot inspect another plugin's tool registry", () => {
    expect(resolveOmoRunnerAvailability().available).toBe(false)
    expect(resolveOmoRunnerAvailability({ enabled: true }).available).toBe(true)
  })

  test("auto runner falls back to native when OMO is not explicitly enabled", () => {
    const selection = selectOpenCodeRunner({ runner: "auto" })

    expect(selection.selected).toBe("opencode-native")
    expect(selection.fallbackFrom).toBe("omo")
    expect(selection.note).toContain("Falling back to OpenCode Native runner")
  })

  test("OMO runner emits the sync delegate-task shape when explicitly enabled", () => {
    const selection = selectOpenCodeRunner({ runner: "omo", omoTaskBridge: true })
    const plan = createOmoRunner().launch(launchInput)

    expect(selection.selected).toBe("omo")
    expect(plan.runner).toBe("omo")
    expect(plan.args.run_in_background).toBe(false)
    expect(plan.args.load_skills).toEqual([])
    expect(plan.instructions.join("\n")).toContain("Call OMO task bridge")
  })

  test("forced OMO without bridge still falls back to native", () => {
    const selection = selectOpenCodeRunner({ runner: "omo" })

    expect(selection.selected).toBe("opencode-native")
    expect(selection.fallbackFrom).toBe("omo")
    expect(selection.runner.kind).toBe("opencode-native")
  })
})
