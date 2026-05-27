import { afterEach, describe, expect, test } from "bun:test"
import fs from "node:fs/promises"
import os from "node:os"
import path from "node:path"
import { pathToFileURL } from "node:url"
import {
  WorkspaceControlError,
  WorkspaceControlRegistry,
  assertPathInScope,
  ensureRuntimeScope,
  loadWorkspaceControlConfig,
  openWorkItemWorkspace,
  resolveClientFileUrlInScope,
  resolveClientPathInScope,
  resolveWorkspaceControlIdentity,
} from "../../src/workspace-control"

const roots: string[] = []

async function tmpRoot() {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "workspace-control-"))
  roots.push(root)
  return root
}

afterEach(async () => {
  await Promise.all(roots.splice(0).map((root) => fs.rm(root, { recursive: true, force: true })))
})

describe("workspace-control runtime scope", () => {
  test("loads development defaults and header identity", () => {
    const config = loadWorkspaceControlConfig({ AGENTCENTER_RUNTIME_ROOT: "runtime-workspaces" }, "/tmp/ac")
    const identity = resolveWorkspaceControlIdentity({
      config,
      headers: {
        "x-agentcenter-user-id": "user-a",
        "x-agentcenter-tenant-id": "tenant-a",
      },
    })

    expect(config.runtimeRoot).toBe(path.join("/tmp/ac", "runtime-workspaces"))
    expect(config.devProjectId).toBe("demo-project")
    expect(identity).toEqual({ tenantId: "tenant-a", userId: "user-a" })
  })

  test("creates the target directory layout under runtime root", async () => {
    const runtimeRoot = await tmpRoot()
    const config = loadWorkspaceControlConfig(
      {
        AGENTCENTER_RUNTIME_ROOT: runtimeRoot,
        AGENTCENTER_DEV_TENANT_ID: "tenant-a",
        AGENTCENTER_DEV_PROJECT_ID: "project-a",
        AGENTCENTER_DEV_USER_ID: "user-a",
      },
      "/ignored",
    )

    const scope = await ensureRuntimeScope(config, {
      tenantId: config.devTenantId,
      projectId: config.devProjectId,
      workItemId: "WI-001",
      userId: config.devUserId,
      sessionId: "ses_001",
      resourceSnapshotId: "snap-001",
    })

    const realRoot = await fs.realpath(runtimeRoot)
    expect(scope.allowedRoot).toBe(
      path.join(
        realRoot,
        "tenants",
        "tenant-a",
        "projects",
        "project-a",
        "work-items",
        "WI-001",
        "users",
        "user-a",
        "workspace",
      ),
    )
    expect((await fs.stat(scope.layout.projectSkillsRoot)).isDirectory()).toBe(true)
    expect((await fs.stat(scope.layout.projectMcpRoot)).isDirectory()).toBe(true)
    expect((await fs.stat(scope.layout.opencodeRoot)).isDirectory()).toBe(true)
  })

  test("rejects path traversal in product ids", async () => {
    const runtimeRoot = await tmpRoot()
    const config = loadWorkspaceControlConfig({ AGENTCENTER_RUNTIME_ROOT: runtimeRoot }, "/ignored")

    await expect(
      ensureRuntimeScope(config, {
        tenantId: "local-tenant",
        projectId: "../outside",
        workItemId: "WI-001",
        userId: "local-user",
        sessionId: "ses_001",
      }),
    ).rejects.toMatchObject({ code: "invalid_segment" })
  })

  test("allows paths inside allowedRoot and rejects sibling escapes", async () => {
    const runtimeRoot = await tmpRoot()
    const config = loadWorkspaceControlConfig({ AGENTCENTER_RUNTIME_ROOT: runtimeRoot }, "/ignored")
    const scope = await ensureRuntimeScope(config, {
      tenantId: "local-tenant",
      projectId: "demo-project",
      workItemId: "WI-001",
      userId: "local-user",
      sessionId: "ses_001",
    })

    const inside = path.join(scope.allowedRoot, "src", "index.ts")
    await fs.mkdir(path.dirname(inside), { recursive: true })
    await fs.writeFile(inside, "export {}\n")

    await expect(assertPathInScope(scope, "src/index.ts")).resolves.toMatchObject({ realpath: inside })
    await expect(assertPathInScope(scope, path.join(scope.layout.workItemRoot, "state", "secret.txt"), "new-file")).rejects.toMatchObject({
      code: "path_escape",
    })
  })

  test("resolves opaque client workspace paths back to the allowed root", async () => {
    const runtimeRoot = await tmpRoot()
    const outside = await tmpRoot()
    const config = loadWorkspaceControlConfig({ AGENTCENTER_RUNTIME_ROOT: runtimeRoot }, "/ignored")
    const scope = await ensureRuntimeScope(config, {
      tenantId: "local-tenant",
      projectId: "demo-project",
      workItemId: "WI-001",
      userId: "local-user",
      sessionId: "ses_001",
    })

    const inside = path.join(scope.allowedRoot, "src", "index.ts")
    await fs.mkdir(path.dirname(inside), { recursive: true })
    await fs.writeFile(inside, "export {}\n")
    const secret = path.join(outside, "secret.txt")
    await fs.writeFile(secret, "secret\n")

    await expect(resolveClientPathInScope(scope, `${scope.workspaceId}/src/index.ts`)).resolves.toMatchObject({
      realpath: inside,
    })
    await expect(resolveClientFileUrlInScope(scope, `file://${scope.workspaceId}/src/index.ts?start=1&end=1`)).resolves.toMatchObject({
      realpath: inside,
    })
    await expect(resolveClientFileUrlInScope(scope, `file:///${scope.workspaceId}/src/index.ts`)).resolves.toMatchObject({
      realpath: inside,
    })
    await expect(resolveClientFileUrlInScope(scope, pathToFileURL(secret).toString())).rejects.toMatchObject({
      code: "path_escape",
    })
  })

  test("rejects symlink escapes after realpath resolution", async () => {
    const runtimeRoot = await tmpRoot()
    const outside = await tmpRoot()
    const config = loadWorkspaceControlConfig({ AGENTCENTER_RUNTIME_ROOT: runtimeRoot }, "/ignored")
    const scope = await ensureRuntimeScope(config, {
      tenantId: "local-tenant",
      projectId: "demo-project",
      workItemId: "WI-001",
      userId: "local-user",
      sessionId: "ses_001",
    })

    await fs.writeFile(path.join(outside, "secret.txt"), "secret")
    await fs.symlink(path.join(outside, "secret.txt"), path.join(scope.allowedRoot, "secret-link.txt"))

    await expect(assertPathInScope(scope, "secret-link.txt")).rejects.toBeInstanceOf(WorkspaceControlError)
    await expect(assertPathInScope(scope, "secret-link.txt")).rejects.toMatchObject({ code: "path_escape" })
  })

  test("binds sessions to runtime scopes in the first-stage registry", async () => {
    const runtimeRoot = await tmpRoot()
    const config = loadWorkspaceControlConfig({ AGENTCENTER_RUNTIME_ROOT: runtimeRoot }, "/ignored")
    const scope = await ensureRuntimeScope(config, {
      tenantId: "local-tenant",
      projectId: "demo-project",
      workItemId: "WI-001",
      userId: "local-user",
      sessionId: "ses_001",
    })
    const registry = new WorkspaceControlRegistry()

    registry.upsertProject({ tenantId: scope.tenantId, projectId: scope.projectId, name: "Demo" })
    registry.upsertWorkItem({
      tenantId: scope.tenantId,
      projectId: scope.projectId,
      workItemId: scope.workItemId,
      title: "Demo work item",
      status: "open",
    })
    const binding = registry.bindSession(scope, 1000)

    expect(binding).toMatchObject({
      tenantId: "local-tenant",
      projectId: "demo-project",
      workItemId: "WI-001",
      userId: "local-user",
      sessionId: "ses_001",
      workspaceId: scope.workspaceId,
      allowedRoot: scope.allowedRoot,
      createdAt: 1000,
      updatedAt: 1000,
    })
    expect(registry.listWorkItems({ tenantId: "local-tenant", projectId: "demo-project" })).toHaveLength(1)
    expect(registry.getSessionBinding("ses_001")).toEqual(binding)
  })

  test("opens work items as isolated user workspaces with shared project resources", async () => {
    const runtimeRoot = await tmpRoot()
    const config = loadWorkspaceControlConfig({ AGENTCENTER_RUNTIME_ROOT: runtimeRoot }, "/ignored")
    const registry = new WorkspaceControlRegistry()

    const first = await openWorkItemWorkspace({
      config,
      registry,
      identity: { tenantId: "tenant-a", userId: "alice" },
      request: {
        projectId: "project-a",
        projectName: "Project A",
        workItemId: "WI-001",
        workItemTitle: "Design workspace control",
        sessionId: "ses_alice",
      },
    })
    const second = await openWorkItemWorkspace({
      config,
      registry,
      identity: { tenantId: "tenant-a", userId: "bob" },
      request: {
        projectId: "project-a",
        workItemId: "WI-001",
        sessionId: "ses_bob",
      },
    })

    expect(first.scope.layout.projectResourcesRoot).toBe(second.scope.layout.projectResourcesRoot)
    expect(first.scope.layout.workItemStateRoot).toBe(second.scope.layout.workItemStateRoot)
    expect(first.scope.allowedRoot).not.toBe(second.scope.allowedRoot)
    expect(first.session).toEqual({
      directory: first.scope.allowedRoot,
      permission: [{ permission: "external_directory", pattern: "*", action: "deny" }],
    })
    expect(first.client).toEqual({
      tenantId: "tenant-a",
      projectId: "project-a",
      workItemId: "WI-001",
      userId: "alice",
      sessionId: "ses_alice",
      workspaceId: first.scope.workspaceId,
      allowedRootLabel: path.join(
        "tenants",
        "tenant-a",
        "projects",
        "project-a",
        "work-items",
        "WI-001",
        "users",
        "alice",
        "workspace",
      ),
    })
    expect(registry.getSessionBinding("ses_alice")?.allowedRoot).toBe(first.scope.allowedRoot)
    expect(registry.getSessionBinding("ses_bob")?.allowedRoot).toBe(second.scope.allowedRoot)
    expect(registry.getSessionBinding("ses_alice")?.workspaceId).toBe(first.scope.workspaceId)
    expect(first.scope.workspaceId).not.toContain(first.scope.allowedRoot)
  })
})
