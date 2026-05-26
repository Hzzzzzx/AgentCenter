import { mkdtemp, rm } from "node:fs/promises"
import path from "node:path"
import { tmpdir } from "node:os"
import { describe, expect, test } from "bun:test"
import { createCodeChangeWorkflow, createWorkflowDraft } from "../src/core/definition"
import { inspectWorkflowRun, renderWorkflowDebug } from "../src/core/inspect"
import { recordNodeResult } from "../src/core/scheduler"
import { createWorkflowRun } from "../src/core/state"
import { closeWorkflowObserveServersForTests, ensureWorkflowObserveServer } from "../src/opencode/observe-server"
import { FsWorkflowStore } from "../src/store/fs-store"

describe("workflow observe server", () => {
  test("serves a live run dashboard and JSON state", async () => {
    const stateDir = await mkdtemp(path.join(tmpdir(), "agent-workflow-observe-"))
    try {
      const store = new FsWorkflowStore(stateDir)
      const definition = createWorkflowDraft({
        workflowId: "observable-flow",
        name: "Observable Flow",
        description: "先分析影响，再实现 <script>，最后测试。",
      })
      const run = createWorkflowRun({
        sessionId: "session-observe",
        task: "escape <script> in dashboard",
        definition,
      })
      await store.saveWorkflowDefinition(definition)
      await store.saveRun(run)

      const started = await ensureWorkflowObserveServer(store)
      const html = await fetch(`${started.baseUrl}/runs/${run.id}`).then((response) => response.text())
      expect(html).toContain("节点流程")
      expect(html).toContain("诊断说明")
      expect(html).toContain("开发调试 JSON")
      expect(html).toContain(`/api/runs/${run.id}`)

      const runPayload = (await fetch(`${started.baseUrl}/api/runs/${run.id}`).then((response) => response.json())) as {
        run?: { id?: string }
        inspection?: { readyNodeIds?: string[] }
      }
      expect(runPayload.run?.id).toBe(run.id)
      expect(runPayload.inspection?.readyNodeIds).toEqual(["planner"])

      const definitionPayload = (await fetch(`${started.baseUrl}/api/definitions/observable-flow`).then((response) =>
        response.json(),
      )) as {
        definition?: { id?: string }
        inspection?: unknown[]
      }
      expect(definitionPayload.definition?.id).toBe("observable-flow")
      expect(definitionPayload.inspection?.length).toBe(definition.nodes.length)
    } finally {
      await closeWorkflowObserveServersForTests()
      await rm(stateDir, { recursive: true, force: true })
    }
  })

  test("explains ready and blocked workflow nodes", () => {
    const definition = createCodeChangeWorkflow({ task: "small code change" })
    const run = createWorkflowRun({ sessionId: "session-inspect", task: "small code change", definition })

    const inspection = inspectWorkflowRun(run)
    expect(inspection.readyNodeIds).toEqual(["planner"])
    expect(inspection.nodes.find((node) => node.nodeId === "coder")?.blockedReasons).toContain("waiting for risk_review (pending)")

    const debug = renderWorkflowDebug(run, "coder")
    expect(debug).toContain("Workflow debug:")
    expect(debug).toContain("waiting for risk_review (pending)")
  })

  test("shows node waiting diagnostics after a needs-input result", () => {
    const definition = createCodeChangeWorkflow({ task: "needs approval" })
    const run = createWorkflowRun({ sessionId: "session-wait", task: "needs approval", definition })

    recordNodeResult(run, {
      nodeId: "planner",
      result: {
        status: "needs_input",
        summary: "Need a decision",
        question: "Continue with this workflow?",
        choices: ["yes", "no"],
      },
    })

    const debug = renderWorkflowDebug(run, "planner")
    expect(debug).toContain("Waiting: planner")
    expect(debug).toContain("waiting for user: Continue with this workflow?")
  })
})
