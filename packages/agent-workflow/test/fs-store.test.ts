import { mkdtemp, rm, writeFile } from "node:fs/promises"
import path from "node:path"
import { tmpdir } from "node:os"
import { describe, expect, test } from "bun:test"
import { createCodeChangeWorkflow } from "../src/core/definition"
import { createWorkflowRun } from "../src/core/state"
import { FsWorkflowStore, isSafeRunId } from "../src/store/fs-store"

describe("FsWorkflowStore", () => {
  test("rejects unsafe run ids before building file paths", async () => {
    const stateDir = await mkdtemp(path.join(tmpdir(), "agent-workflow-store-"))
    try {
      const store = new FsWorkflowStore(stateDir)

      expect(isSafeRunId("wf_safe-123")).toBe(true)
      expect(isSafeRunId("../outside")).toBe(false)
      expect(await store.getRun("../outside")).toBeUndefined()
      expect(await store.updateRun("../outside", () => {})).toBeUndefined()
    } finally {
      await rm(stateDir, { recursive: true, force: true })
    }
  })

  test("ignores unsafe json filenames when listing runs", async () => {
    const stateDir = await mkdtemp(path.join(tmpdir(), "agent-workflow-store-"))
    try {
      const store = new FsWorkflowStore(stateDir)
      const run = createWorkflowRun({
        sessionId: "session-store",
        task: "store smoke",
        definition: createCodeChangeWorkflow({ task: "store smoke" }),
      })

      await store.saveRun(run)
      await writeFile(path.join(stateDir, "runs", "unsafe.name.json"), "{}")

      const runs = await store.listRuns({ sessionId: "session-store" })
      expect(runs.map((item) => item.id)).toEqual([run.id])
    } finally {
      await rm(stateDir, { recursive: true, force: true })
    }
  })

  test("saves, loads, and lists workflow definitions", async () => {
    const stateDir = await mkdtemp(path.join(tmpdir(), "agent-workflow-store-"))
    try {
      const store = new FsWorkflowStore(stateDir)
      const definition = createCodeChangeWorkflow({ workflowId: "saved-code-change", task: "{{task}}" })

      await store.saveWorkflowDefinition(definition)

      expect((await store.getWorkflowDefinition("saved-code-change"))?.id).toBe("saved-code-change")
      expect((await store.listWorkflowDefinitions()).map((item) => item.id)).toEqual(["saved-code-change"])
      expect(await store.getWorkflowDefinition("../outside")).toBeUndefined()
    } finally {
      await rm(stateDir, { recursive: true, force: true })
    }
  })

})
