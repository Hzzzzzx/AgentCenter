import { describe, expect, test } from "bun:test"
import {
  createCodeChangeWorkflow,
  createWorkflowDraft,
  materializeWorkflowDefinition,
  parseWorkflowDefinitionJson,
  validateWorkflowDefinition,
} from "../src/core/definition"

describe("workflow definitions", () => {
  test("keeps the default risk review autonomous instead of mandatory human gate", () => {
    const workflow = createCodeChangeWorkflow({ task: "update one README sentence" })
    const riskReview = workflow.nodes.find((node) => node.id === "risk_review")

    expect(riskReview?.strategy?.mode).toBe("single")
    expect(riskReview?.prompt).toContain("Default to passing low-risk")
    expect(riskReview?.prompt).toContain("native question tool")
    expect(riskReview?.prompt).not.toContain("workflow_needs_input")
  })

  test("creates a reusable DAG draft from natural language", () => {
    const draft = createWorkflowDraft({
      workflowId: "safe-code-change",
      name: "Safe Code Change",
      description:
        "先分析架构影响，再实现；同时准备测试验证方案；高风险改动必须先问我，最后 review。",
    })

    expect(draft.id).toBe("safe-code-change")
    expect(draft.nodes.map((node) => node.id)).toContain("architecture_review")
    expect(draft.nodes.map((node) => node.id)).toContain("risk_gate")
    expect(draft.nodes.map((node) => node.id)).toContain("test_planner")
    expect(draft.nodes.find((node) => node.id === "coder")?.strategy?.mode).toBe("static_parallel_branch")
    expect(validateWorkflowDefinition(draft).valid).toBe(true)
  })

  test("does not turn ordinary risk review wording into a mandatory gate", () => {
    const draft = createWorkflowDraft({
      workflowId: "autonomous-risk-review",
      description: "先分析风险和影响，再实现，最后 review。",
    })

    expect(draft.nodes.map((node) => node.id)).not.toContain("risk_gate")
    expect(validateWorkflowDefinition(draft).valid).toBe(true)
  })

  test("validates unsafe ids, unknown dependencies, unsupported agents, and cycles", () => {
    const definition = parseWorkflowDefinitionJson(
      JSON.stringify({
        id: "../bad",
        version: "0.1.0",
        name: "Bad Workflow",
        description: "bad",
        activation: { mode: "explicit_or_confirmed", applicability: [], nonApplicability: [] },
        nodes: [
          { id: "a", type: "agent", agent: "workflow_planner", prompt: "A", dependsOn: ["b"] },
          { id: "b", type: "agent", agent: "unknown_agent", prompt: "B", dependsOn: ["a", "missing"] },
        ],
        edges: [],
      }),
    )

    const validation = validateWorkflowDefinition(definition)
    expect(validation.valid).toBe(false)
    expect(validation.errors.join("\n")).toContain("Workflow id is unsafe")
    expect(validation.errors.join("\n")).toContain("unsupported agent")
    expect(validation.errors.join("\n")).toContain("unknown node missing")
    expect(validation.errors.join("\n")).toContain("dependency cycle")
  })

  test("materializes saved workflow templates with the current task", () => {
    const draft = createWorkflowDraft({
      workflowId: "template-test",
      description: "实现并测试",
    })

    const materialized = materializeWorkflowDefinition(draft, "add login validation")

    expect(materialized.nodes.some((node) => node.prompt.includes("{{task}}"))).toBe(false)
    expect(materialized.nodes.some((node) => node.prompt.includes("add login validation"))).toBe(true)
    expect(draft.nodes.some((node) => node.prompt.includes("{{task}}"))).toBe(true)
  })
})
