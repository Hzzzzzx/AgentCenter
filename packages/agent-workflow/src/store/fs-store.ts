import { mkdir, readFile, readdir, rename, writeFile } from "node:fs/promises"
import path from "node:path"
import type { WorkflowDefinition } from "../core/definition"
import type { WorkflowRun } from "../core/state"

export class FsWorkflowStore {
  private updates = new Map<string, Promise<WorkflowRun | undefined>>()

  constructor(private root: string) {}

  async saveRun(run: WorkflowRun) {
    await mkdir(this.runsDir(), { recursive: true })
    if (!isSafeRunId(run.id)) throw new Error(`Invalid workflow run id: ${run.id}`)
    run.version = (run.version ?? 0) + 1
    run.updatedAt = Date.now()
    await writeFile(this.tmpRunFile(run.id), `${JSON.stringify(run, null, 2)}\n`)
    await rename(this.tmpRunFile(run.id), this.runFile(run.id))
  }

  async updateRun(id: string, update: (run: WorkflowRun) => WorkflowRun | void | Promise<WorkflowRun | void>) {
    if (!isSafeRunId(id)) return
    const previous = this.updates.get(id) ?? Promise.resolve(undefined)
    const next = previous.then(async () => {
      const run = await this.getRun(id)
      if (!run) return
      const updated = (await update(run)) ?? run
      await this.saveRun(updated)
      return updated
    })
    this.updates.set(id, next.catch(() => undefined))
    return next.finally(() => {
      if (this.updates.get(id) === next) this.updates.delete(id)
    })
  }

  async getRun(id: string) {
    if (!isSafeRunId(id)) return
    const text = await readFile(this.runFile(id), "utf8").catch(() => undefined)
    if (!text) return
    return normalizeRun(JSON.parse(text) as WorkflowRun)
  }

  async listRuns(input?: { sessionId?: string }) {
    await mkdir(this.runsDir(), { recursive: true })
    const files = await readdir(this.runsDir()).catch(() => [])
    const runs = await Promise.all(
      files
        .filter((file) => file.endsWith(".json") && isSafeRunId(file.slice(0, -5)))
        .map(async (file) => {
          const text = await readFile(path.join(this.runsDir(), file), "utf8").catch(() => undefined)
          const value = text ? (JSON.parse(text) as unknown) : undefined
          return isWorkflowRun(value) ? normalizeRun(value) : undefined
        }),
    )
    return runs
      .filter((run): run is WorkflowRun => Boolean(run))
      .filter((run) => !input?.sessionId || run.sessionId === input.sessionId)
      .sort((a, b) => b.updatedAt - a.updatedAt)
  }

  async saveWorkflowDefinition(definition: WorkflowDefinition) {
    await mkdir(this.definitionsDir(), { recursive: true })
    if (!isSafeRunId(definition.id)) throw new Error(`Invalid workflow definition id: ${definition.id}`)
    await writeFile(this.tmpDefinitionFile(definition.id), `${JSON.stringify(definition, null, 2)}\n`)
    await rename(this.tmpDefinitionFile(definition.id), this.definitionFile(definition.id))
  }

  async getWorkflowDefinition(id: string) {
    if (!isSafeRunId(id)) return
    const text = await readFile(this.definitionFile(id), "utf8").catch(() => undefined)
    if (!text) return
    return JSON.parse(text) as WorkflowDefinition
  }

  async listWorkflowDefinitions() {
    await mkdir(this.definitionsDir(), { recursive: true })
    const files = await readdir(this.definitionsDir()).catch(() => [])
    const definitions = await Promise.all(
      files
        .filter((file) => file.endsWith(".json") && isSafeRunId(file.slice(0, -5)))
        .map(async (file) => {
          const text = await readFile(path.join(this.definitionsDir(), file), "utf8").catch(() => undefined)
          return text ? (JSON.parse(text) as WorkflowDefinition) : undefined
        }),
    )
    return definitions
      .filter((definition): definition is WorkflowDefinition => Boolean(definition?.id))
      .sort((a, b) => a.id.localeCompare(b.id))
  }

  directory() {
    return this.root
  }

  private runsDir() {
    return path.join(this.root, "runs")
  }

  private definitionsDir() {
    return path.join(this.root, "definitions")
  }

  private runFile(id: string) {
    return path.join(this.runsDir(), `${id}.json`)
  }

  private tmpRunFile(id: string) {
    return path.join(this.runsDir(), `${id}.${process.pid}.tmp`)
  }

  private definitionFile(id: string) {
    return path.join(this.definitionsDir(), `${id}.json`)
  }

  private tmpDefinitionFile(id: string) {
    return path.join(this.definitionsDir(), `${id}.${process.pid}.tmp`)
  }

}

export function isSafeRunId(id: string) {
  return /^[A-Za-z0-9_-]+$/.test(id)
}

function isWorkflowRun(value: unknown): value is WorkflowRun {
  return !!value && typeof value === "object" && "id" in value && "nodes" in value && "definition" in value
}

function normalizeRun(run: WorkflowRun) {
  run.version = run.version ?? 0
  run.deviations = run.deviations ?? []
  return run
}
