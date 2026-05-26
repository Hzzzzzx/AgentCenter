import { describe, expect, test } from "bun:test"
import { createCodeChangeWorkflow } from "../src/core/definition"
import {
  answerNodeWait,
  getReadyNodes,
  parseTaskIdFromTaskOutput,
  parseWorkflowTaskOutput,
  recordNodeResult,
  requestWorkflowDeviation,
  startNode,
  workflowCommand,
  parseWorkflowCommand,
} from "../src/core/scheduler"
import { createWorkflowRun } from "../src/core/state"

describe("workflow scheduler", () => {
  test("runs a linear DAG with waiting and resume", () => {
    const definition = createCodeChangeWorkflow({ task: "implement login check" })
    const run = createWorkflowRun({ sessionId: "session-1", task: "implement login check", definition })

    expect(getReadyNodes(run).map((node) => node.nodeId)).toEqual(["planner"])

    startNode(run, { nodeId: "planner", callId: "call-1" })
    recordNodeResult(run, { nodeId: "planner", result: { status: "succeeded", summary: "plan ok" } })
    expect(getReadyNodes(run).map((node) => node.nodeId).sort()).toEqual([
      "code_scout",
      "history_scout",
      "test_scout",
    ])

    recordNodeResult(run, {
      nodeId: "code_scout",
      result: {
        status: "needs_input",
        summary: "needs decision",
        question: "Allow fixture edits?",
        choices: ["allow", "deny"],
      },
    })
    expect(run.status).toBe("running")
    expect(getReadyNodes(run).map((node) => node.nodeId).sort()).toEqual(["history_scout", "test_scout"])

    answerNodeWait(run, { nodeId: "code_scout", answer: "allow", source: "question" })
    expect(run.status).toBe("running")
    expect(getReadyNodes(run).map((node) => node.nodeId).sort()).toEqual([
      "code_scout",
      "history_scout",
      "test_scout",
    ])

    recordNodeResult(run, { nodeId: "code_scout", result: { status: "succeeded", summary: "code scout ok" } })
    recordNodeResult(run, { nodeId: "test_scout", result: { status: "succeeded", summary: "test scout ok" } })
    recordNodeResult(run, { nodeId: "history_scout", result: { status: "skipped", summary: "no extra context" } })
    expect(run.nodes.scout_join?.status).toBe("succeeded")
    expect(getReadyNodes(run).map((node) => node.nodeId)).toEqual(["synthesizer"])

    recordNodeResult(run, { nodeId: "synthesizer", result: { status: "succeeded", summary: "brief ok" } })
    expect(getReadyNodes(run).map((node) => node.nodeId)).toEqual(["risk_review"])
    recordNodeResult(run, {
      nodeId: "risk_review",
      result: {
        status: "needs_input",
        summary: "needs decision",
        question: "Allow fixture edits?",
        choices: ["allow", "deny"],
      },
    })
    expect(run.status).toBe("waiting")
    expect(getReadyNodes(run)).toEqual([])

    answerNodeWait(run, { nodeId: "risk_review", answer: "allow", source: "question" })
    expect(run.status).toBe("running")
    expect(getReadyNodes(run).map((node) => node.nodeId)).toEqual(["risk_review"])
  })

  test("parses workflow command", () => {
    expect(parseWorkflowCommand(workflowCommand("run-1", "node-1"))).toEqual({
      runId: "run-1",
      nodeId: "node-1",
    })
    expect(parseWorkflowCommand("not-workflow")).toBeUndefined()
  })

  test("skipped nodes unblock downstream nodes", () => {
    const definition = createCodeChangeWorkflow({ task: "summarize README without editing" })
    const run = createWorkflowRun({ sessionId: "session-1", task: "summarize README without editing", definition })

    recordNodeResult(run, { nodeId: "planner", result: { status: "skipped", summary: "already planned" } })
    expect(getReadyNodes(run).map((node) => node.nodeId).sort()).toEqual([
      "code_scout",
      "history_scout",
      "test_scout",
    ])

    recordNodeResult(run, { nodeId: "code_scout", result: { status: "skipped", summary: "not needed" } })
    recordNodeResult(run, { nodeId: "test_scout", result: { status: "skipped", summary: "not needed" } })
    recordNodeResult(run, { nodeId: "history_scout", result: { status: "skipped", summary: "not needed" } })
    expect(run.nodes.scout_join?.status).toBe("succeeded")
    recordNodeResult(run, { nodeId: "synthesizer", result: { status: "skipped", summary: "nothing to merge" } })
    recordNodeResult(run, { nodeId: "risk_review", result: { status: "skipped", summary: "no risk gate needed" } })
    recordNodeResult(run, { nodeId: "coder", result: { status: "skipped", summary: "no code changes requested" } })
    recordNodeResult(run, { nodeId: "tester", result: { status: "skipped", summary: "no validation needed" } })
    recordNodeResult(run, { nodeId: "reviewer", result: { status: "skipped", summary: "final review not needed" } })

    expect(run.status).toBe("succeeded")
    expect(run.nodes.coder?.status).toBe("skipped")
  })

  test("records workflow deviation and asks user for medium risk approval", () => {
    const definition = createCodeChangeWorkflow({ task: "change implementation plan" })
    const run = createWorkflowRun({ sessionId: "session-1", task: "change implementation plan", definition })

    const deviation = requestWorkflowDeviation(run, {
      nodeId: "planner",
      action: "insert_step",
      risk: "medium",
      reason: "The original workflow needs one extra dependency check.",
      proposal: "Insert a dependency scout before implementation.",
    })

    expect(deviation.status).toBe("waiting_approval")
    expect(run.status).toBe("waiting")
    expect(run.nodes.planner?.wait?.deviationId).toBe(deviation.id)

    answerNodeWait(run, { nodeId: "planner", answer: "approve", source: "question" })
    expect(run.deviations[0]?.status).toBe("approved")
    expect(run.nodes.planner?.status).toBe("pending")
  })

  test("records run-level workflow deviation on a concrete node", () => {
    const definition = createCodeChangeWorkflow({ task: "change implementation plan" })
    const run = createWorkflowRun({ sessionId: "session-1", task: "change implementation plan", definition })

    const deviation = requestWorkflowDeviation(run, {
      action: "detach",
      risk: "high",
      reason: "The workflow no longer fits.",
      proposal: "Ask before leaving the workflow.",
    })

    expect(deviation.status).toBe("waiting_approval")
    expect(run.status).toBe("waiting")
    expect(run.nodes.planner?.status).toBe("waiting")
    expect(run.nodes.planner?.wait?.deviationId).toBe(deviation.id)
  })

  test("ignores stale task results that do not match the current call", () => {
    const definition = createCodeChangeWorkflow({ task: "stale result protection" })
    const run = createWorkflowRun({ sessionId: "session-1", task: "stale result protection", definition })

    startNode(run, { nodeId: "planner", callId: "call-current" })
    const accepted = recordNodeResult(run, {
      nodeId: "planner",
      callId: "call-old",
      result: { status: "succeeded", summary: "old success" },
    })

    expect(accepted).toBe(false)
    expect(run.nodes.planner?.status).toBe("running")
    expect(run.nodes.planner?.summary).toBeUndefined()
    expect(run.events.at(-1)?.type).toBe("node.result_ignored")
  })

  test("treats untagged task output as failed instead of succeeded", () => {
    const result = parseWorkflowTaskOutput("plain output without the required tagged JSON")

    expect(result.status).toBe("failed")
    expect(result.summary).toContain("did not return")
  })

  test("retries started nodes but lets manual failure fail pending nodes", () => {
    const definition = createCodeChangeWorkflow({ task: "retry behavior" })
    const run = createWorkflowRun({ sessionId: "session-1", task: "retry behavior", definition })

    startNode(run, { nodeId: "planner", callId: "call-1" })
    recordNodeResult(run, {
      nodeId: "planner",
      callId: "call-1",
      result: { status: "failed", summary: "first failure", error: "temporary" },
    })
    expect(run.nodes.planner?.status).toBe("pending")
    expect(run.status).toBe("running")

    recordNodeResult(run, {
      nodeId: "planner",
      result: { status: "failed", summary: "manual failure", error: "not retryable" },
    })
    expect(run.nodes.planner?.status).toBe("failed")
    expect(run.status).toBe("failed")
  })

  test("clears active wait after user answer while preserving last wait", () => {
    const definition = createCodeChangeWorkflow({ task: "wait cleanup" })
    const run = createWorkflowRun({ sessionId: "session-1", task: "wait cleanup", definition })

    recordNodeResult(run, {
      nodeId: "planner",
      result: { status: "needs_input", summary: "need input", question: "Continue?", choices: ["yes"] },
    })
    answerNodeWait(run, { nodeId: "planner", answer: "yes", source: "question" })

    expect(run.nodes.planner?.wait).toBeUndefined()
    expect(run.nodes.planner?.lastWait?.question).toBe("Continue?")
    expect(run.nodes.planner?.lastWait?.answerSource).toBe("question")
  })

  test("parses task id from task output variants", () => {
    expect(parseTaskIdFromTaskOutput("task_id: ses_abc123 (for resuming)\n\n<task_result>ok</task_result>")).toBe(
      "ses_abc123",
    )
    expect(
      parseTaskIdFromTaskOutput("<task_metadata>\nsession_id: ses_parent\ntask_id: ses_child\nsubagent: workflow\n</task_metadata>"),
    ).toBe("ses_child")
  })
})
