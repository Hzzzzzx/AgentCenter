import fs from "node:fs/promises"
import path from "node:path"
import { fileURLToPath } from "node:url"
import { WorkspaceControlError } from "./errors"

export type PathAccessMode = "existing" | "new-file" | "new-directory"

export type RuntimeScopeLike = {
  allowedRoot: string
}

export type RuntimeScopeClientPathLike = RuntimeScopeLike & {
  workspaceId: string
}

export type ScopedPath = {
  requestedPath: string
  absolutePath: string
  realpath: string
}

export function isPathInside(root: string, candidate: string): boolean {
  const relative = path.relative(root, candidate)
  return relative === "" || (!relative.startsWith("..") && !path.isAbsolute(relative))
}

export async function assertPathInScope(
  scope: RuntimeScopeLike,
  requestedPath: string,
  mode: PathAccessMode = "existing",
): Promise<ScopedPath> {
  const allowedRoot = await fs.realpath(scope.allowedRoot)
  const absolutePath = path.isAbsolute(requestedPath)
    ? path.resolve(requestedPath)
    : path.resolve(allowedRoot, requestedPath)
  const realpath = await resolveTargetRealpath(absolutePath, mode)

  if (!isPathInside(allowedRoot, realpath)) {
    throw new WorkspaceControlError("path_escape", `Path is outside runtime scope: ${requestedPath}`)
  }

  return {
    requestedPath,
    absolutePath,
    realpath,
  }
}

export async function resolveClientPathInScope(
  scope: RuntimeScopeClientPathLike,
  requestedPath: string,
  mode: PathAccessMode = "existing",
): Promise<ScopedPath> {
  return assertPathInScope(scope, clientVisiblePath(scope, requestedPath), mode)
}

export async function resolveClientFileUrlInScope(
  scope: RuntimeScopeClientPathLike,
  requestedUrl: string,
  mode: PathAccessMode = "existing",
): Promise<ScopedPath> {
  let url: URL
  try {
    url = new URL(requestedUrl)
  } catch {
    throw new WorkspaceControlError("invalid_path", `Invalid file URL: ${requestedUrl}`)
  }

  if (url.protocol !== "file:") {
    throw new WorkspaceControlError("invalid_path", `Unsupported file URL protocol: ${url.protocol}`)
  }

  if (url.hostname && url.hostname !== "localhost" && url.hostname !== scope.workspaceId) {
    throw new WorkspaceControlError("path_escape", `File URL host is outside runtime scope: ${url.hostname}`)
  }

  const requestedPath =
    url.hostname === scope.workspaceId ? decodeUrlPath(url.pathname.replace(/^\/+/, "")) : fileURLToPath(url)
  return resolveClientPathInScope(scope, requestedPath, mode)
}

function clientVisiblePath(scope: RuntimeScopeClientPathLike, requestedPath: string) {
  const normalized = requestedPath.replace(/\\/g, "/")
  const workspaceRoot = scope.workspaceId
  if (normalized === workspaceRoot) return ""
  if (normalized === `/${workspaceRoot}`) return ""
  if (normalized.startsWith(`${workspaceRoot}/`)) return normalized.slice(workspaceRoot.length + 1)
  if (normalized.startsWith(`/${workspaceRoot}/`)) return normalized.slice(workspaceRoot.length + 2)
  return requestedPath
}

function decodeUrlPath(input: string) {
  try {
    return decodeURIComponent(input)
  } catch {
    throw new WorkspaceControlError("invalid_path", `Invalid encoded file path: ${input}`)
  }
}

async function resolveTargetRealpath(target: string, mode: PathAccessMode): Promise<string> {
  if (mode === "existing") {
    try {
      return await fs.realpath(target)
    } catch {
      throw new WorkspaceControlError("missing_path", `Path does not exist: ${target}`)
    }
  }

  const parentRealpath = await nearestExistingParent(path.dirname(target))
  return path.resolve(parentRealpath.realpath, path.relative(parentRealpath.path, target))
}

async function nearestExistingParent(start: string): Promise<{ path: string; realpath: string }> {
  let current = path.resolve(start)
  while (true) {
    try {
      return {
        path: current,
        realpath: await fs.realpath(current),
      }
    } catch {
      const next = path.dirname(current)
      if (next === current) {
        throw new WorkspaceControlError("missing_path", `No existing parent for path: ${start}`)
      }
      current = next
    }
  }
}
