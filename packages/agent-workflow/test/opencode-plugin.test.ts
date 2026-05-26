import { mkdtemp, rm } from "node:fs/promises"
import path from "node:path"
import { tmpdir } from "node:os"
import { describe, expect, test } from "bun:test"
import { closeWorkflowObserveServersForTests } from "../src/opencode/observe-server"
import AgentWorkflowPlugin from "../src/opencode/plugin"

describe("OpenCode workflow plugin", () => {
  test("defines, saves, lists, shows, and runs a reusable workflow definition", async () => {
    const stateDir = await mkdtemp(path.join(tmpdir(), "agent-workflow-plugin-"))
    try {
      const hooks = await AgentWorkflowPlugin(
        {
          worktree: process.cwd(),
          directory: process.cwd(),
          project: { id: "project-definition" } as never,
          client: {} as never,
          serverUrl: new URL("http://localhost:4096"),
          experimental_workspace: { register() {} },
          $: undefined as never,
        },
        { stateDir },
      )
      const context = {
        sessionID: "session-definition",
        messageID: "message-definition",
        agent: "build",
        directory: process.cwd(),
        worktree: process.cwd(),
        abort: new AbortController().signal,
        metadata() {},
        async ask() {},
      }

      const draft = String(
        await hooks.tool?.workflow_define.execute(
          {
            workflow_id: "safe-code-change",
            name: "Safe Code Change",
            description: "先做架构影响分析，高风险先问我，再实现；同时准备测试方案，最后 review。",
          },
          context,
        ),
      )
      expect(draft).toContain("Workflow definition draft:")
      expect(draft).toContain("risk_gate")
      expect(draft).toContain("test_planner")
      const definitionJson = /```json\n([\s\S]*?)\n```/.exec(draft)?.[1]
      expect(definitionJson).toBeTruthy()

      const validation = await hooks.tool?.workflow_validate.execute({ definition_json: definitionJson }, context)
      expect(validation).toContain("Workflow definition valid.")

      const savePrompt = await hooks.tool?.workflow_save.execute({ definition_json: definitionJson }, context)
      expect(savePrompt).toContain("Native OpenCode question required")
      expect(savePrompt).toContain("confirmed_by_question=true")

      const saved = await hooks.tool?.workflow_save.execute({ definition_json: definitionJson, confirmed_by_question: true }, context)
      expect(saved).toContain("Workflow definition saved.")

      const list = await hooks.tool?.workflow_list.execute({}, context)
      expect(list).toContain("safe-code-change")

      const shown = await hooks.tool?.workflow_show.execute({ workflow_id: "safe-code-change" }, context)
      expect(shown).toContain("Definition JSON:")

      const route = await hooks.tool?.workflow_route.execute({ task: "safe code change with tests" }, context)
      expect(route).toContain("safe-code-change")
      expect(route).toContain("built-in question tool")

      const created = await hooks.tool?.workflow_run.execute(
        { workflow_id: "safe-code-change", task: "add a small validation check" },
        context,
      )
      expect(created?.output).toContain("Workflow run:")
      expect(created?.output).toContain("architecture_review")
      expect(created?.output).not.toContain("{{task}}")
      const runId = /Workflow run: (\S+)/.exec(String(created?.output))?.[1]
      expect(runId).toBeTruthy()

      const debug = await hooks.tool?.workflow_debug.execute({ run_id: runId, node_id: "coder" }, context)
      expect(debug).toContain("Workflow debug:")
      expect(debug).toContain("waiting for")

      const observedRun = String(await hooks.tool?.workflow_observe.execute({ run_id: runId }, context))
      expect(observedRun).toContain("Workflow observe dashboard ready.")
      expect(observedRun).toContain(`/runs/${runId}`)
      const runApi = /API: (http:\/\/127\.0\.0\.1:\d+\/api\/runs\/\S+)/.exec(observedRun)?.[1]
      expect(runApi).toBeTruthy()
      const runPayload = (await fetch(runApi!).then((response) => response.json())) as {
        run?: { id?: string }
        inspection?: { readyNodeIds?: string[] }
      }
      expect(runPayload.run?.id).toBe(runId)
      expect(runPayload.inspection?.readyNodeIds).toEqual(["planner"])

      const observedDefinition = String(await hooks.tool?.workflow_observe.execute({ workflow_id: "safe-code-change" }, context))
      expect(observedDefinition).toContain("Workflow observe dashboard ready.")
      expect(observedDefinition).toContain("/definitions/safe-code-change")
      const definitionApi = /API: (http:\/\/127\.0\.0\.1:\d+\/api\/definitions\/\S+)/.exec(observedDefinition)?.[1]
      expect(definitionApi).toBeTruthy()
      const definitionPayload = (await fetch(definitionApi!).then((response) => response.json())) as {
        definition?: { id?: string }
        inspection?: unknown[]
      }
      expect(definitionPayload.definition?.id).toBe("safe-code-change")
      expect(definitionPayload.inspection?.length).toBeGreaterThan(0)
    } finally {
      await closeWorkflowObserveServersForTests()
      await rm(stateDir, { recursive: true, force: true })
    }
  })

  test("serializes parallel branch result writes before join/reduce", async () => {
    const stateDir = await mkdtemp(path.join(tmpdir(), "agent-workflow-plugin-"))
    try {
      const hooks = await AgentWorkflowPlugin(
        {
          worktree: process.cwd(),
          directory: process.cwd(),
          project: { id: "project-parallel" } as never,
          client: {} as never,
          serverUrl: new URL("http://localhost:4096"),
          experimental_workspace: { register() {} },
          $: undefined as never,
        },
        { stateDir, runner: "auto" },
      )
      const context = {
        sessionID: "session-parallel",
        messageID: "message-parallel",
        agent: "build",
        directory: process.cwd(),
        worktree: process.cwd(),
        abort: new AbortController().signal,
        metadata() {},
        async ask() {},
      }

      const created = await hooks.tool?.workflow_run.execute({ task: "parallel smoke workflow" }, context)
      const runId = /Workflow run: (\S+)/.exec(String(created?.output))?.[1]
      expect(runId).toBeTruthy()

      await hooks.tool?.workflow_node_result.execute(
        { run_id: runId, node_id: "planner", status: "succeeded", summary: "plan ok" },
        context,
      )
      await Promise.all(
        ["code_scout", "test_scout", "history_scout"].map((nodeId) =>
          hooks.tool?.workflow_node_result.execute(
            { run_id: runId, node_id: nodeId, status: "succeeded", summary: `${nodeId} ok` },
            context,
          ),
        ),
      )

      const state = await hooks.tool?.workflow_state_get.execute({ run_id: runId }, context)
      expect(state).toContain("scout_join: succeeded")
      expect(state).toContain("synthesizer -> call task")
    } finally {
      await closeWorkflowObserveServersForTests()
      await rm(stateDir, { recursive: true, force: true })
    }
  })

  test("closes the V1 hook, wait/resume, and skip loop", async () => {
    const stateDir = await mkdtemp(path.join(tmpdir(), "agent-workflow-plugin-"))
    try {
      const hooks = await AgentWorkflowPlugin(
        {
          worktree: process.cwd(),
          directory: process.cwd(),
          project: { id: "project-smoke" } as never,
          client: {} as never,
          serverUrl: new URL("http://localhost:4096"),
          experimental_workspace: { register() {} },
          $: undefined as never,
        },
        { stateDir, runner: "auto" },
      )
      const context = {
        sessionID: "session-smoke",
        messageID: "message-smoke",
        agent: "build",
        directory: process.cwd(),
        worktree: process.cwd(),
        abort: new AbortController().signal,
        metadata() {},
        async ask() {},
      }

      const route = await hooks.tool?.workflow_route.execute({ task: "implement a small fix" }, context)
      expect(route).toContain("workflow may fit")
      expect(route).toContain("do not start workflow unless")
      expect(route).toContain("built-in question tool")

      const created = await hooks.tool?.workflow_run.execute({ task: "smoke workflow" }, context)
      expect(created?.output).toContain("planner -> call task")
      expect(created?.output).toContain("Runner: opencode-native")
      expect(created?.output).toContain("Fallback: omo -> opencode-native")
      const runId = /Workflow run: (\S+)/.exec(String(created?.output))?.[1]
      expect(runId).toBeTruthy()

      await hooks["tool.execute.before"]?.(
        { tool: "task", sessionID: context.sessionID, callID: "call-planner" },
        { args: { command: `workflow:${runId}:planner` } },
      )
      await hooks["tool.execute.after"]?.(
        { tool: "task", sessionID: context.sessionID, callID: "call-planner", args: { command: `workflow:${runId}:planner` } },
        {
          title: "task",
          output:
            'task_id: ses_planner\n\n<task_result><workflow_result>{"summary":"planner done","artifacts":[]}</workflow_result></task_result>',
          metadata: {},
        },
      )

      const afterPlanner = await hooks.tool?.workflow_state_get.execute({ run_id: runId }, context)
      expect(afterPlanner).toContain("code_scout -> call task")
      expect(afterPlanner).toContain("test_scout -> call task")
      expect(afterPlanner).toContain("history_scout -> call task")
      expect(afterPlanner).toContain("taskId=ses_planner")

      await hooks["tool.execute.before"]?.(
        { tool: "task", sessionID: context.sessionID, callID: "call-scout" },
        { args: { command: `workflow:${runId}:code_scout` } },
      )
      await hooks["tool.execute.after"]?.(
        { tool: "task", sessionID: context.sessionID, callID: "call-scout", args: { command: `workflow:${runId}:code_scout` } },
        {
          title: "task",
          output:
            '<workflow_needs_input>{"summary":"needs decision","question":"Continue after scout?","choices":["yes","no"]}</workflow_needs_input>',
          metadata: {},
        },
      )

      const waiting = await hooks.tool?.workflow_state_get.execute({ run_id: runId }, context)
      expect(waiting).toContain("Status: running")
      expect(waiting).toContain("Continue after scout?")
      expect(waiting).toContain("Native OpenCode question required")
      expect(waiting).toContain("Call the built-in question tool")
      expect(waiting).toContain("test_scout -> call task")

      const resumed = await hooks.tool?.workflow_answer.execute(
        { run_id: runId, node_id: "code_scout", answer: "yes", source: "question" },
        context,
      )
      expect(resumed).toContain("code_scout -> call task")

      const deviation = await hooks.tool?.workflow_deviation_request.execute(
        {
          run_id: runId,
          node_id: "code_scout",
          action: "insert_step",
          risk: "medium",
          reason: "The scout found a missing dependency check.",
          proposal: "Pause and ask before changing the workflow shape.",
        },
        context,
      )
      expect(deviation).toContain("Recent deviations:")
      expect(deviation).toContain("waiting_approval")
      expect(deviation).toContain("Native OpenCode question required")

      const deviationAnswered = await hooks.tool?.workflow_answer.execute(
        { run_id: runId, node_id: "code_scout", answer: "approve", source: "question" },
        context,
      )
      expect(deviationAnswered).toContain("approved")

      await hooks.tool?.workflow_node_result.execute(
        { run_id: runId, node_id: "code_scout", status: "skipped", summary: "scout no longer needed" },
        context,
      )
      await hooks.tool?.workflow_node_result.execute(
        { run_id: runId, node_id: "test_scout", status: "skipped", summary: "validation scout no longer needed" },
        context,
      )
      await hooks.tool?.workflow_node_result.execute(
        { run_id: runId, node_id: "history_scout", status: "skipped", summary: "context scout no longer needed" },
        context,
      )
      await hooks.tool?.workflow_node_result.execute(
        { run_id: runId, node_id: "synthesizer", status: "skipped", summary: "merge skipped" },
        context,
      )
      await hooks.tool?.workflow_node_result.execute(
        { run_id: runId, node_id: "risk_review", status: "skipped", summary: "no gate needed" },
        context,
      )
      await hooks.tool?.workflow_node_result.execute(
        { run_id: runId, node_id: "coder", status: "skipped", summary: "no code changes" },
        context,
      )
      await hooks.tool?.workflow_node_result.execute(
        { run_id: runId, node_id: "tester", status: "skipped", summary: "no tests needed" },
        context,
      )
      const final = await hooks.tool?.workflow_node_result.execute(
        { run_id: runId, node_id: "reviewer", status: "skipped", summary: "review skipped" },
        context,
      )

      expect(final).toContain("Status: succeeded")
      expect(final).toContain("reviewer: skipped")

      const systemOutput = { system: [] as string[] }
      await hooks["experimental.chat.system.transform"]?.({ sessionID: context.sessionID }, systemOutput)
      expect(systemOutput.system.join("\n")).toContain("Agent Workflow runner: opencode-native.")
      expect(systemOutput.system.join("\n")).toContain("Falling back to OpenCode Native runner.")
      expect(systemOutput.system.join("\n")).toContain("Do not route ordinary tasks into workflow by default.")
      expect(systemOutput.system.join("\n")).toContain("Current workflow capsule:")
    } finally {
      await closeWorkflowObserveServersForTests()
      await rm(stateDir, { recursive: true, force: true })
    }
  })
})
