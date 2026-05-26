import type { WorkflowRun } from "../core/state"

export class MemoryWorkflowStore {
  private runs = new Map<string, WorkflowRun>()

  async saveRun(run: WorkflowRun) {
    run.version = (run.version ?? 0) + 1
    run.updatedAt = Date.now()
    this.runs.set(run.id, structuredClone(run))
  }

  async updateRun(id: string, update: (run: WorkflowRun) => WorkflowRun | void | Promise<WorkflowRun | void>) {
    const run = await this.getRun(id)
    if (!run) return
    const updated = (await update(run)) ?? run
    await this.saveRun(updated)
    return updated
  }

  async getRun(id: string) {
    const run = this.runs.get(id)
    if (!run) return
    return structuredClone(run)
  }

  async listRuns(input?: { sessionId?: string }) {
    return Array.from(this.runs.values())
      .filter((run) => !input?.sessionId || run.sessionId === input.sessionId)
      .sort((a, b) => b.updatedAt - a.updatedAt)
      .map((run) => structuredClone(run))
  }
}
