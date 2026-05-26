import { describe, expect, test } from "bun:test"
import { workflowAgents } from "../src/opencode/agents"

describe("workflow agents", () => {
  test("allow native question tool for autonomous node interactions", () => {
    for (const [name, agent] of Object.entries(workflowAgents())) {
      expect(agent.permission.question, `${name} should expose native question`).toBe("allow")
    }
  })
})
