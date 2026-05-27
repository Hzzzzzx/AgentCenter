import { Bus } from "@/bus"
import { GlobalBus, type GlobalEvent as GlobalBusEvent } from "@/bus/global"
import { Command } from "@/command"
import { Config } from "@/config/config"
import * as InstanceState from "@/effect/instance-state"
import { File as OpenCodeFile } from "@/file"
import { Ripgrep } from "@/file/ripgrep"
import { InstanceStore } from "@/project/instance-store"
import { Vcs } from "@/project/vcs"
import { Permission } from "@/permission"
import { Question } from "@/question"
import { Session } from "@/session/session"
import { SessionShare } from "@/share/session"
import { Agent } from "@/agent/agent"
import { Format } from "@/format"
import { Global } from "@opencode-ai/core/global"
import { LSP } from "@/lsp/lsp"
import { MCP } from "@/mcp"
import { ModelsDev } from "@opencode-ai/core/models-dev"
import { Provider } from "@/provider/provider"
import { SessionCompaction } from "@/session/compaction"
import { MessageV2 } from "@/session/message-v2"
import { SessionPrompt } from "@/session/prompt"
import { SessionRevert } from "@/session/revert"
import { SessionRunState } from "@/session/run-state"
import { SessionStatus } from "@/session/status"
import { Todo } from "@/session/todo"
import { NamedError } from "@opencode-ai/core/util/error"
import { Cause, Effect, Queue, Scope } from "effect"
import * as Stream from "effect/Stream"
import * as Sse from "effect/unstable/encoding/Sse"
import { HttpServerRequest, HttpServerResponse } from "effect/unstable/http"
import { HttpApiBuilder, HttpApiError } from "effect/unstable/httpapi"
import path from "node:path"
import { pathToFileURL } from "node:url"
import { mapValues } from "remeda"
import { RootHttpApi } from "../api"
import { PermissionNotFoundError, QuestionNotFoundError, notFound } from "../errors"
import {
  AgentCenterFileQuery,
  AgentCenterCommandPayload,
  AgentCenterCompactPayload,
  AgentCenterFindFileQuery,
  AgentCenterFindTextQuery,
  AgentCenterForkPayload,
  AgentCenterOpenPayload,
  AgentCenterPermissionReplyPayload,
  AgentCenterPromptPayload,
  AgentCenterQuestionReplyPayload,
  AgentCenterRevertPayload,
  AgentCenterShellPayload,
  AgentCenterSessionUpdatePayload,
  MessageParams,
  OpenWorkItemParams,
  PartParams,
  PermissionParams,
  ProjectParams,
  QuestionParams,
  SessionParams,
} from "../groups/agentcenter"
import {
  WorkspaceControlRegistry,
  assertPathInScope,
  controlledSessionPermission,
  ensureRuntimeScope,
  loadWorkspaceControlConfig,
  openWorkItemWorkspace,
  resolveClientFileUrlInScope,
  resolveWorkspaceControlIdentity,
} from "@/workspace-control"

const registry = new WorkspaceControlRegistry()

const seedWorkItems = [
  { id: "WI-001", title: "体验受控 OpenCode 对话", status: "open" as const },
  { id: "WI-002", title: "验证用户工作目录隔离", status: "open" as const },
]

type PromptPartInput = SessionPrompt.PromptInput["parts"][number]
type PromptFilePartInput = Extract<PromptPartInput, { type: "file" }>
type CommandFilePartInput = NonNullable<SessionPrompt.CommandInput["parts"]>[number]

function toClientWorkItem(item: {
  workItemId: string
  title: string
  status: "open" | "running" | "blocked" | "done" | "archived"
}) {
  return {
    id: item.workItemId,
    title: item.title,
    status: item.status,
  }
}

function isVisibleWorkspacePath(input: string) {
  return !input.split(/[\\/]+/).includes(".opencode")
}

function inferCompactModel(info: Session.Info, messages: MessageV2.WithParts[]) {
  for (let index = messages.length - 1; index >= 0; index--) {
    const message = messages[index]?.info
    if (!message) continue
    if (message.role === "user") {
      return {
        providerID: message.model.providerID,
        modelID: message.model.modelID,
      }
    }
    return {
      providerID: message.providerID,
      modelID: message.modelID,
    }
  }
  if (!info.model) return undefined
  return {
    providerID: info.model.providerID,
    modelID: info.model.id,
  }
}

function toClientSession(info: Session.Info, binding: { workspaceId: string }): Session.Info {
  return {
    ...info,
    directory: binding.workspaceId,
    path: undefined,
  }
}

function toClientProject<T extends { worktree: string }>(project: T, binding: { workspaceId: string }) {
  return {
    ...project,
    worktree: binding.workspaceId,
  }
}

function eventData(data: unknown): Sse.Event {
  return {
    _tag: "Event",
    event: "message",
    id: undefined,
    data: JSON.stringify(data),
  }
}

function controlledEventResponse(binding: {
  sessionId: string
  projectId: string
  workspaceId: string
  allowedRoot: string
}) {
  const events = Stream.callback<GlobalBusEvent>((queue) => {
    const handler = (event: GlobalBusEvent) => Queue.offerUnsafe(queue, event)
    return Effect.acquireRelease(
      Effect.sync(() => GlobalBus.on("event", handler)),
      () => Effect.sync(() => GlobalBus.off("event", handler)),
    )
  })
  const heartbeat = Stream.tick("10 seconds").pipe(
    Stream.drop(1),
    Stream.map(() => ({ payload: { id: Bus.createID(), type: "server.heartbeat", properties: {} } })),
  )

  return HttpServerResponse.stream(
    Stream.make({ payload: { id: Bus.createID(), type: "server.connected", properties: {} } }).pipe(
      Stream.concat(
        events.pipe(
          Stream.filter((event) => isEventForBinding(event, binding)),
          Stream.merge(heartbeat, { haltStrategy: "left" }),
        ),
      ),
      Stream.map((event) => eventData(toClientEvent(event, binding))),
      Stream.pipeThroughChannel(Sse.encode()),
      Stream.encodeText,
    ),
    {
      contentType: "text/event-stream",
      headers: {
        "Cache-Control": "no-cache, no-transform",
        "X-Accel-Buffering": "no",
        "X-Content-Type-Options": "nosniff",
      },
    },
  )
}

function toClientEvent(
  event: { directory?: string; project?: string; workspace?: string; payload: unknown },
  binding: { projectId: string; workspaceId: string; allowedRoot: string },
) {
  return {
    directory: binding.workspaceId,
    project: binding.projectId,
    workspace: binding.workspaceId,
    payload: scrubAllowedRoot(event.payload, binding),
  }
}

function scrubAllowedRoot<T>(input: T, binding: { workspaceId: string; allowedRoot: string }): T {
  try {
    return JSON.parse(JSON.stringify(input).split(binding.allowedRoot).join(binding.workspaceId)) as T
  } catch {
    return input
  }
}

function isEventForBinding(
  event: GlobalBusEvent,
  binding: { sessionId: string; allowedRoot: string },
) {
  if (isGlobalEvent(event.payload)) return true
  const sessionID = eventSessionID(event.payload)
  if (sessionID) return sessionID === binding.sessionId
  return event.directory === binding.allowedRoot
}

function isGlobalEvent(payload: unknown) {
  if (!isRecord(payload)) return false
  return payload.type === "server.connected" || payload.type === "server.heartbeat" || payload.type === "global.disposed"
}

function eventSessionID(payload: unknown): string | undefined {
  if (!isRecord(payload)) return undefined
  const properties = payload.properties
  if (isRecord(properties)) {
    if (typeof properties.sessionID === "string") return properties.sessionID
    if (isRecord(properties.info) && typeof properties.info.sessionID === "string") return properties.info.sessionID
    if (isRecord(properties.part) && typeof properties.part.sessionID === "string") return properties.part.sessionID
  }
  if (!isRecord(payload.syncEvent)) return undefined
  if (typeof payload.syncEvent.aggregateID === "string") return payload.syncEvent.aggregateID
  if (!isRecord(payload.syncEvent.data)) return undefined
  if (typeof payload.syncEvent.data.sessionID === "string") return payload.syncEvent.data.sessionID
  if (isRecord(payload.syncEvent.data.info) && typeof payload.syncEvent.data.info.sessionID === "string") {
    return payload.syncEvent.data.info.sessionID
  }
  return undefined
}

function isRecord(input: unknown): input is Record<string, unknown> {
  return typeof input === "object" && input !== null
}

async function normalizeWorkspacePromptParts(
  parts: SessionPrompt.PromptInput["parts"],
  binding: { workspaceId: string; allowedRoot: string },
) {
  return Promise.all(parts.map((part) => normalizeWorkspacePromptPart(part, binding)))
}

async function normalizeWorkspacePromptPart(
  part: PromptPartInput,
  binding: { workspaceId: string; allowedRoot: string },
): Promise<PromptPartInput> {
  if (part.type !== "file") return part
  if (part.source?.type === "resource") return part
  const url = promptPartUrl(part.url)
  if (url.protocol !== "file:") return part
  const resolved = await resolveClientFileUrlInScope(binding, part.url)
  return {
    ...part,
    url: workspaceFileUrl(resolved.realpath, url),
    source: normalizeWorkspacePromptFileSource(part.source, resolved.realpath),
  }
}

function normalizeWorkspacePromptFileSource(
  source: PromptFilePartInput["source"],
  filepath: string,
): PromptFilePartInput["source"] {
  if (!source) return undefined
  if (source.type === "resource") return source
  return {
    ...source,
    path: filepath,
  }
}

async function normalizeWorkspaceCommandParts(
  parts: NonNullable<SessionPrompt.CommandInput["parts"]>,
  binding: { workspaceId: string; allowedRoot: string },
) {
  return Promise.all(parts.map((part) => normalizeWorkspaceCommandPart(part, binding)))
}

async function normalizeWorkspaceCommandPart(
  part: CommandFilePartInput,
  binding: { workspaceId: string; allowedRoot: string },
): Promise<CommandFilePartInput> {
  if (part.source?.type === "resource") return part
  const url = promptPartUrl(part.url)
  if (url.protocol !== "file:") return part
  const resolved = await resolveClientFileUrlInScope(binding, part.url)
  return {
    ...part,
    url: workspaceFileUrl(resolved.realpath, url),
    source: normalizeWorkspaceCommandFileSource(part.source, resolved.realpath),
  }
}

function normalizeWorkspaceCommandFileSource(
  source: CommandFilePartInput["source"],
  filepath: string,
): CommandFilePartInput["source"] {
  if (!source) return undefined
  if (source.type === "resource") return source
  return {
    ...source,
    path: filepath,
  }
}

function promptPartUrl(input: string) {
  try {
    return new URL(input)
  } catch {
    throw new Error(`Invalid prompt file URL: ${input}`)
  }
}

function workspaceFileUrl(filepath: string, source: URL) {
  const target = pathToFileURL(filepath)
  target.search = source.search
  target.hash = source.hash
  return target.toString()
}

export const agentCenterHandlers = HttpApiBuilder.group(RootHttpApi, "agentcenter", (handlers) =>
  Effect.gen(function* () {
    const store = yield* InstanceStore.Service
    const session = yield* Session.Service
    const shareSvc = yield* SessionShare.Service
    const prompt = yield* SessionPrompt.Service
    const revertSvc = yield* SessionRevert.Service
    const compactSvc = yield* SessionCompaction.Service
    const runState = yield* SessionRunState.Service
    const agentSvc = yield* Agent.Service
    const commandSvc = yield* Command.Service
    const configSvc = yield* Config.Service
    const formatSvc = yield* Format.Service
    const lspSvc = yield* LSP.Service
    const mcpSvc = yield* MCP.Service
    const providerSvc = yield* Provider.Service
    const vcs = yield* Vcs.Service
    const status = yield* SessionStatus.Service
    const todoSvc = yield* Todo.Service
    const questionSvc = yield* Question.Service
    const permissionSvc = yield* Permission.Service
    const fileSvc = yield* OpenCodeFile.Service
    const ripgrep = yield* Ripgrep.Service
    const bus = yield* Bus.Service
    const scope = yield* Scope.Scope

    function requestIdentity(request: HttpServerRequest.HttpServerRequest) {
      const config = loadWorkspaceControlConfig()
      return {
        config,
        identity: resolveWorkspaceControlIdentity({
          config,
          headers: request.headers as Record<string, string | undefined>,
        }),
      }
    }

    const me = Effect.fn("AgentCenterHttpApi.me")(function* () {
      const request = yield* HttpServerRequest.HttpServerRequest
      return requestIdentity(request).identity
    })

    const projects = Effect.fn("AgentCenterHttpApi.projects")(function* () {
      const request = yield* HttpServerRequest.HttpServerRequest
      const { config, identity } = requestIdentity(request)
      const projectId = config.devProjectId
      registry.upsertProject({ tenantId: identity.tenantId, projectId, name: "Demo Project" })
      for (const item of seedWorkItems) {
        registry.upsertWorkItem({
          tenantId: identity.tenantId,
          projectId,
          workItemId: item.id,
          title: item.title,
          status: item.status,
        })
      }
      return [
        {
          tenantId: identity.tenantId,
          projectId,
          name: "Demo Project",
          workItems: registry.listWorkItems({ tenantId: identity.tenantId, projectId }).map(toClientWorkItem),
        },
      ]
    })

    const workItems = Effect.fn("AgentCenterHttpApi.workItems")(function* (ctx: {
      params: typeof ProjectParams.Type
    }) {
      const request = yield* HttpServerRequest.HttpServerRequest
      const { identity } = requestIdentity(request)
      return registry.listWorkItems({ tenantId: identity.tenantId, projectId: ctx.params.projectID }).map(toClientWorkItem)
    })

    const openWorkItem = Effect.fn("AgentCenterHttpApi.openWorkItem")(function* (ctx: {
      params: typeof OpenWorkItemParams.Type
      payload?: typeof AgentCenterOpenPayload.Type
    }) {
      const request = yield* HttpServerRequest.HttpServerRequest
      const { config, identity } = requestIdentity(request)
      const initialScope = yield* Effect.promise(() =>
        ensureRuntimeScope(config, {
          tenantId: identity.tenantId,
          projectId: ctx.params.projectID,
          workItemId: ctx.params.workItemID,
          userId: identity.userId,
          sessionId: "ses_pending",
        }),
      ).pipe(Effect.mapError(() => new HttpApiError.BadRequest({})))

      const created = yield* store.provide(
        { directory: initialScope.allowedRoot },
        session.create({
          title: ctx.payload?.title ?? `AgentCenter ${ctx.params.workItemID}`,
          permission: controlledSessionPermission(),
        }),
      )

      const opened = yield* Effect.promise(() =>
        openWorkItemWorkspace({
          config,
          identity,
          registry,
          request: {
            projectId: ctx.params.projectID,
            projectName: ctx.params.projectID,
            workItemId: ctx.params.workItemID,
            workItemTitle: ctx.payload?.title,
            sessionId: created.id,
          },
        }),
      ).pipe(Effect.mapError(() => new HttpApiError.BadRequest({})))

      return {
        ...opened.client,
        session: {
          id: created.id,
          title: created.title,
          directoryLabel: opened.client.allowedRootLabel,
        },
        permission: opened.session.permission,
      }
    })

    const requireBinding = (sessionID: string) =>
      Effect.sync(() => registry.getSessionBinding(sessionID)).pipe(
        Effect.flatMap((binding) =>
          binding
            ? Effect.succeed(binding)
            : Effect.fail(notFound(`AgentCenter session scope not found: ${sessionID}`)),
        ),
      )

    const scopeInfo = Effect.fn("AgentCenterHttpApi.scope")(function* (ctx: { params: typeof SessionParams.Type }) {
      const request = yield* HttpServerRequest.HttpServerRequest
      const { config } = requestIdentity(request)
      const binding = yield* requireBinding(ctx.params.sessionID)
      return {
        tenantId: binding.tenantId,
        projectId: binding.projectId,
        workItemId: binding.workItemId,
        userId: binding.userId,
        sessionId: binding.sessionId,
        workspaceId: binding.workspaceId,
        allowedRootLabel: path.relative(config.runtimeRoot, binding.allowedRoot),
      }
    })

    const events = Effect.fn("AgentCenterHttpApi.event")(function* (ctx: {
      params: typeof SessionParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      return controlledEventResponse(binding)
    })

    const bootstrap = Effect.fn("AgentCenterHttpApi.bootstrap")(function* (ctx: {
      params: typeof SessionParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      const info = yield* store.provide({ directory: binding.allowedRoot }, session.get(ctx.params.sessionID)).pipe(
        Effect.mapError(() => notFound(`Session not found: ${ctx.params.sessionID}`)),
      )
      const messages = yield* store.provide(
        { directory: binding.allowedRoot },
        session.messages({ sessionID: ctx.params.sessionID }),
      ).pipe(Effect.mapError(() => notFound(`Session not found: ${ctx.params.sessionID}`)))
      const diff = yield* store.provide({ directory: binding.allowedRoot }, session.diff(ctx.params.sessionID))
      const todos = yield* store.provide({ directory: binding.allowedRoot }, todoSvc.get(ctx.params.sessionID))
      const questions = yield* store.provide({ directory: binding.allowedRoot }, questionSvc.list())
      const permissions = yield* store.provide({ directory: binding.allowedRoot }, permissionSvc.list())
      return scrubAllowedRoot(
        {
          session: toClientSession(info, binding),
          messages,
          status: yield* store.provide({ directory: binding.allowedRoot }, status.get(ctx.params.sessionID)),
          diff,
          todos,
          questions: questions.filter((item) => item.sessionID === ctx.params.sessionID),
          permissions: permissions.filter((item) => item.sessionID === ctx.params.sessionID),
        },
        binding,
      )
    })

    const messages = Effect.fn("AgentCenterHttpApi.messages")(function* (ctx: { params: typeof SessionParams.Type }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      const items = yield* store.provide(
        { directory: binding.allowedRoot },
        session.messages({ sessionID: ctx.params.sessionID }),
      ).pipe(Effect.mapError(() => notFound(`Session not found: ${ctx.params.sessionID}`)))
      return scrubAllowedRoot(items, binding)
    })

    const message = Effect.fn("AgentCenterHttpApi.message")(function* (ctx: { params: typeof MessageParams.Type }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      const item = yield* store.provide(
        { directory: binding.allowedRoot },
        MessageV2.get({ sessionID: ctx.params.sessionID, messageID: ctx.params.messageID }),
      ).pipe(Effect.mapError(() => notFound(`Message not found: ${ctx.params.messageID}`)))
      return scrubAllowedRoot(item, binding)
    })

    const update = Effect.fn("AgentCenterHttpApi.update")(function* (ctx: {
      params: typeof SessionParams.Type
      payload: typeof AgentCenterSessionUpdatePayload.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      const current = yield* store.provide({ directory: binding.allowedRoot }, session.get(ctx.params.sessionID)).pipe(
        Effect.mapError(() => notFound(`Session not found: ${ctx.params.sessionID}`)),
      )
      if (ctx.payload.title !== undefined) {
        yield* store.provide(
          { directory: binding.allowedRoot },
          session.setTitle({ sessionID: ctx.params.sessionID, title: ctx.payload.title }),
        )
      }
      if (ctx.payload.permission !== undefined) {
        yield* store.provide(
          { directory: binding.allowedRoot },
          session.setPermission({
            sessionID: ctx.params.sessionID,
            permission: Permission.merge(current.permission ?? [], ctx.payload.permission),
          }),
        )
      }
      if (ctx.payload.time?.archived !== undefined) {
        yield* store.provide(
          { directory: binding.allowedRoot },
          session.setArchived({ sessionID: ctx.params.sessionID, time: ctx.payload.time.archived }),
        )
      }
      const updated = yield* store.provide({ directory: binding.allowedRoot }, session.get(ctx.params.sessionID)).pipe(
        Effect.mapError(() => notFound(`Session not found: ${ctx.params.sessionID}`)),
      )
      return scrubAllowedRoot(toClientSession(updated, binding), binding)
    })

    const share = Effect.fn("AgentCenterHttpApi.share")(function* (ctx: { params: typeof SessionParams.Type }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      yield* store.provide({ directory: binding.allowedRoot }, session.get(ctx.params.sessionID)).pipe(
        Effect.mapError(() => notFound(`Session not found: ${ctx.params.sessionID}`)),
      )
      yield* store.provide({ directory: binding.allowedRoot }, shareSvc.share(ctx.params.sessionID)).pipe(
        Effect.mapError(() => new HttpApiError.InternalServerError({})),
      )
      const updated = yield* store.provide({ directory: binding.allowedRoot }, session.get(ctx.params.sessionID)).pipe(
        Effect.mapError(() => notFound(`Session not found: ${ctx.params.sessionID}`)),
      )
      return scrubAllowedRoot(toClientSession(updated, binding), binding)
    })

    const unshare = Effect.fn("AgentCenterHttpApi.unshare")(function* (ctx: { params: typeof SessionParams.Type }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      yield* store.provide({ directory: binding.allowedRoot }, session.get(ctx.params.sessionID)).pipe(
        Effect.mapError(() => notFound(`Session not found: ${ctx.params.sessionID}`)),
      )
      yield* store.provide({ directory: binding.allowedRoot }, shareSvc.unshare(ctx.params.sessionID)).pipe(
        Effect.mapError(() => new HttpApiError.InternalServerError({})),
      )
      const updated = yield* store.provide({ directory: binding.allowedRoot }, session.get(ctx.params.sessionID)).pipe(
        Effect.mapError(() => notFound(`Session not found: ${ctx.params.sessionID}`)),
      )
      return scrubAllowedRoot(toClientSession(updated, binding), binding)
    })

    const deleteMessage = Effect.fn("AgentCenterHttpApi.deleteMessage")(function* (ctx: {
      params: typeof MessageParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      yield* store.provide({ directory: binding.allowedRoot }, session.get(ctx.params.sessionID)).pipe(
        Effect.mapError(() => notFound(`Session not found: ${ctx.params.sessionID}`)),
      )
      yield* store.provide({ directory: binding.allowedRoot }, runState.assertNotBusy(ctx.params.sessionID)).pipe(
        Effect.mapError(() => new HttpApiError.BadRequest({})),
      )
      yield* store.provide(
        { directory: binding.allowedRoot },
        session.removeMessage({ sessionID: ctx.params.sessionID, messageID: ctx.params.messageID }),
      )
      return true
    })

    const deletePart = Effect.fn("AgentCenterHttpApi.deletePart")(function* (ctx: { params: typeof PartParams.Type }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      yield* store.provide({ directory: binding.allowedRoot }, session.get(ctx.params.sessionID)).pipe(
        Effect.mapError(() => notFound(`Session not found: ${ctx.params.sessionID}`)),
      )
      yield* store.provide(
        { directory: binding.allowedRoot },
        session.removePart({
          sessionID: ctx.params.sessionID,
          messageID: ctx.params.messageID,
          partID: ctx.params.partID,
        }),
      )
      return true
    })

    const updatePart = Effect.fn("AgentCenterHttpApi.updatePart")(function* (ctx: {
      params: typeof PartParams.Type
      payload: typeof MessageV2.Part.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      const payload = ctx.payload as MessageV2.Part
      if (
        payload.id !== ctx.params.partID ||
        payload.messageID !== ctx.params.messageID ||
        payload.sessionID !== ctx.params.sessionID
      ) {
        return yield* new HttpApiError.BadRequest({})
      }
      const updated = yield* store.provide({ directory: binding.allowedRoot }, session.updatePart(payload)).pipe(
        Effect.mapError(() => new HttpApiError.BadRequest({})),
      )
      return scrubAllowedRoot(updated, binding)
    })

    const children = Effect.fn("AgentCenterHttpApi.children")(function* (ctx: { params: typeof SessionParams.Type }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      yield* store.provide({ directory: binding.allowedRoot }, session.get(ctx.params.sessionID)).pipe(
        Effect.mapError(() => notFound(`Session not found: ${ctx.params.sessionID}`)),
      )
      const items = yield* store.provide({ directory: binding.allowedRoot }, session.children(ctx.params.sessionID))
      return scrubAllowedRoot(items.map((item) => toClientSession(item, binding)), binding)
    })

    const todo = Effect.fn("AgentCenterHttpApi.todo")(function* (ctx: { params: typeof SessionParams.Type }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      yield* store.provide({ directory: binding.allowedRoot }, session.get(ctx.params.sessionID)).pipe(
        Effect.mapError(() => notFound(`Session not found: ${ctx.params.sessionID}`)),
      )
      return scrubAllowedRoot(yield* store.provide({ directory: binding.allowedRoot }, todoSvc.get(ctx.params.sessionID)), binding)
    })

    const findText = Effect.fn("AgentCenterHttpApi.findText")(function* (ctx: {
      params: typeof SessionParams.Type
      query: typeof AgentCenterFindTextQuery.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      return yield* ripgrep
        .search({ cwd: binding.allowedRoot, pattern: ctx.query.pattern, limit: 10 })
        .pipe(
          Effect.map((result) => result.items.filter((item) => isVisibleWorkspacePath(item.path.text))),
          Effect.mapError(() => new HttpApiError.BadRequest({})),
        )
    })

    const findFile = Effect.fn("AgentCenterHttpApi.findFile")(function* (ctx: {
      params: typeof SessionParams.Type
      query: typeof AgentCenterFindFileQuery.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      return yield* store.provide(
        { directory: binding.allowedRoot },
        fileSvc.search({
          query: ctx.query.query,
          limit: ctx.query.limit ?? 10,
          dirs: ctx.query.dirs !== "false",
          type: ctx.query.type,
        }),
      ).pipe(
        Effect.map((items) => items.filter(isVisibleWorkspacePath)),
        Effect.mapError(() => new HttpApiError.BadRequest({})),
      )
    })

    const fileList = Effect.fn("AgentCenterHttpApi.fileList")(function* (ctx: {
      params: typeof SessionParams.Type
      query: typeof AgentCenterFileQuery.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      yield* Effect.tryPromise({
        try: () => assertPathInScope(binding, ctx.query.path ?? "", "existing"),
        catch: () => new HttpApiError.BadRequest({}),
      })
      const nodes = yield* store.provide(
        { directory: binding.allowedRoot },
        fileSvc.list(ctx.query.path ?? ""),
      ).pipe(Effect.mapError(() => new HttpApiError.BadRequest({})))
      return nodes
        .filter((node) => isVisibleWorkspacePath(node.path))
        .map((node) => ({
          name: node.name,
          path: node.path,
          type: node.type,
          ignored: node.ignored,
        }))
    })

    const fileContent = Effect.fn("AgentCenterHttpApi.fileContent")(function* (ctx: {
      params: typeof SessionParams.Type
      query: typeof AgentCenterFileQuery.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      yield* Effect.tryPromise({
        try: () => assertPathInScope(binding, ctx.query.path ?? "", "existing"),
        catch: () => new HttpApiError.BadRequest({}),
      })
      return yield* store.provide(
        { directory: binding.allowedRoot },
        fileSvc.read(ctx.query.path ?? ""),
      ).pipe(Effect.mapError(() => new HttpApiError.BadRequest({})))
    })

    const fileStatus = Effect.fn("AgentCenterHttpApi.fileStatus")(function* (ctx: {
      params: typeof SessionParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      return yield* store.provide({ directory: binding.allowedRoot }, fileSvc.status()).pipe(
        Effect.mapError(() => new HttpApiError.BadRequest({})),
      )
    })

    const sendPrompt = Effect.fn("AgentCenterHttpApi.prompt")(function* (ctx: {
      params: typeof SessionParams.Type
      payload: typeof AgentCenterPromptPayload.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      const { text, parts: inputParts, ...nativePayload } = ctx.payload
      const unsafeParts = inputParts ?? (text !== undefined ? [{ type: "text" as const, text }] : undefined)
      if (!unsafeParts) return yield* new HttpApiError.BadRequest({})
      const parts = yield* Effect.tryPromise({
        try: () => normalizeWorkspacePromptParts(unsafeParts, binding),
        catch: () => new HttpApiError.BadRequest({}),
      })
      yield* store.provide(
        { directory: binding.allowedRoot },
        prompt
          .prompt({
            ...nativePayload,
            sessionID: ctx.params.sessionID,
            parts,
          })
          .pipe(
            Effect.catchCause((cause) =>
              bus.publish(Session.Event.Error, {
                sessionID: ctx.params.sessionID,
                error: new NamedError.Unknown({ message: Cause.pretty(cause) }).toObject(),
              }),
            ),
            Effect.forkIn(scope),
          ),
      )
      return true
    })

    const command = Effect.fn("AgentCenterHttpApi.command")(function* (ctx: {
      params: typeof SessionParams.Type
      payload: typeof AgentCenterCommandPayload.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      const unsafeParts = ctx.payload.parts
      const parts = unsafeParts
        ? yield* Effect.tryPromise({
            try: () => normalizeWorkspaceCommandParts(unsafeParts, binding),
            catch: () => new HttpApiError.BadRequest({}),
          })
        : undefined
      return yield* store.provide(
        { directory: binding.allowedRoot },
        prompt
          .command({
            ...ctx.payload,
            parts,
            sessionID: ctx.params.sessionID,
          })
          .pipe(Effect.mapError(() => new HttpApiError.BadRequest({}))),
      )
    })

    const shell = Effect.fn("AgentCenterHttpApi.shell")(function* (ctx: {
      params: typeof SessionParams.Type
      payload: typeof AgentCenterShellPayload.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      return yield* store.provide(
        { directory: binding.allowedRoot },
        prompt
          .shell({
            ...ctx.payload,
            sessionID: ctx.params.sessionID,
          })
          .pipe(Effect.mapError(() => new HttpApiError.BadRequest({}))),
      )
    })

    const abort = Effect.fn("AgentCenterHttpApi.abort")(function* (ctx: { params: typeof SessionParams.Type }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      yield* store.provide({ directory: binding.allowedRoot }, prompt.cancel(ctx.params.sessionID))
      return true
    })

    const fork = Effect.fn("AgentCenterHttpApi.fork")(function* (ctx: {
      params: typeof SessionParams.Type
      payload?: typeof AgentCenterForkPayload.Type
    }) {
      const request = yield* HttpServerRequest.HttpServerRequest
      const { config } = requestIdentity(request)
      const binding = yield* requireBinding(ctx.params.sessionID)
      const forked = yield* store.provide(
        { directory: binding.allowedRoot },
        session
          .fork({
            sessionID: ctx.params.sessionID,
            messageID: ctx.payload?.messageID,
          })
          .pipe(Effect.mapError(() => new HttpApiError.BadRequest({}))),
      )
      const forkScope = yield* Effect.tryPromise({
        try: () =>
          ensureRuntimeScope(config, {
            tenantId: binding.tenantId,
            projectId: binding.projectId,
            workItemId: binding.workItemId,
            userId: binding.userId,
            sessionId: forked.id,
            resourceSnapshotId: binding.resourceSnapshotId,
          }),
        catch: () => new HttpApiError.BadRequest({}),
      })
      registry.bindSession(forkScope)
      const allowedRootLabel = path.relative(config.runtimeRoot, forkScope.allowedRoot)
      return {
        tenantId: forkScope.tenantId,
        projectId: forkScope.projectId,
        workItemId: forkScope.workItemId,
        userId: forkScope.userId,
        sessionId: forkScope.sessionId,
        workspaceId: forkScope.workspaceId,
        resourceSnapshotId: forkScope.resourceSnapshotId,
        allowedRootLabel,
        session: {
          id: forked.id,
          title: forked.title,
          directoryLabel: allowedRootLabel,
        },
        permission: forked.permission ?? controlledSessionPermission(),
      }
    })

    const compact = Effect.fn("AgentCenterHttpApi.compact")(function* (ctx: {
      params: typeof SessionParams.Type
      payload?: typeof AgentCenterCompactPayload.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      const directory = binding.allowedRoot
      const info = yield* store.provide({ directory }, session.get(ctx.params.sessionID)).pipe(
        Effect.mapError(() => notFound(`Session not found: ${ctx.params.sessionID}`)),
      )
      yield* store.provide({ directory }, revertSvc.cleanup(info)).pipe(
        Effect.mapError(() => new HttpApiError.BadRequest({})),
      )
      const messages = yield* store.provide({ directory }, session.messages({ sessionID: ctx.params.sessionID })).pipe(
        Effect.mapError(() => notFound(`Session not found: ${ctx.params.sessionID}`)),
      )
      const model = ctx.payload?.model ?? inferCompactModel(info, messages)
      if (!model) return yield* new HttpApiError.BadRequest({})

      const defaultAgent = yield* store.provide({ directory }, agentSvc.defaultAgent())
      const currentAgent = messages.findLast((message) => message.info.role === "user")?.info.agent ?? defaultAgent
      yield* store.provide(
        { directory },
        compactSvc
          .create({
            sessionID: ctx.params.sessionID,
            agent: currentAgent,
            model,
            auto: ctx.payload?.auto ?? false,
          })
          .pipe(Effect.mapError(() => new HttpApiError.BadRequest({}))),
      )
      yield* store.provide({ directory }, prompt.loop({ sessionID: ctx.params.sessionID })).pipe(
        Effect.mapError(() => new HttpApiError.BadRequest({})),
      )
      return true
    })

    const revert = Effect.fn("AgentCenterHttpApi.revert")(function* (ctx: {
      params: typeof SessionParams.Type
      payload: typeof AgentCenterRevertPayload.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      return yield* store.provide(
        { directory: binding.allowedRoot },
        revertSvc
          .revert({
            sessionID: ctx.params.sessionID,
            ...ctx.payload,
          })
          .pipe(Effect.mapError(() => new HttpApiError.BadRequest({}))),
      )
    })

    const unrevert = Effect.fn("AgentCenterHttpApi.unrevert")(function* (ctx: {
      params: typeof SessionParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      return yield* store.provide(
        { directory: binding.allowedRoot },
        revertSvc.unrevert({ sessionID: ctx.params.sessionID }).pipe(Effect.mapError(() => new HttpApiError.BadRequest({}))),
      )
    })

    const questions = Effect.fn("AgentCenterHttpApi.questions")(function* (ctx: {
      params: typeof SessionParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      const items = yield* store.provide({ directory: binding.allowedRoot }, questionSvc.list())
      return items.filter((item) => item.sessionID === ctx.params.sessionID)
    })

    const questionReply = Effect.fn("AgentCenterHttpApi.questionReply")(function* (ctx: {
      params: typeof QuestionParams.Type
      payload: typeof AgentCenterQuestionReplyPayload.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      yield* store.provide(
        { directory: binding.allowedRoot },
        questionSvc
          .reply({
            requestID: ctx.params.requestID,
            answers: ctx.payload.answers,
          })
          .pipe(
            Effect.catchTag("Question.NotFoundError", (error) =>
              Effect.fail(
                new QuestionNotFoundError({
                  requestID: String(error.requestID),
                  message: `Question request not found: ${error.requestID}`,
                }),
              ),
            ),
          ),
      )
      return true
    })

    const questionReject = Effect.fn("AgentCenterHttpApi.questionReject")(function* (ctx: {
      params: typeof QuestionParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      yield* store.provide(
        { directory: binding.allowedRoot },
        questionSvc.reject(ctx.params.requestID).pipe(
          Effect.catchTag("Question.NotFoundError", (error) =>
            Effect.fail(
              new QuestionNotFoundError({
                requestID: String(error.requestID),
                message: `Question request not found: ${error.requestID}`,
              }),
            ),
          ),
        ),
      )
      return true
    })

    const permissions = Effect.fn("AgentCenterHttpApi.permissions")(function* (ctx: {
      params: typeof SessionParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      const items = yield* store.provide({ directory: binding.allowedRoot }, permissionSvc.list())
      return items.filter((item) => item.sessionID === ctx.params.sessionID)
    })

    const permissionReply = Effect.fn("AgentCenterHttpApi.permissionReply")(function* (ctx: {
      params: typeof PermissionParams.Type
      payload: typeof AgentCenterPermissionReplyPayload.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      yield* store.provide(
        { directory: binding.allowedRoot },
        permissionSvc
          .reply({
            requestID: ctx.params.requestID,
            reply: ctx.payload.reply,
            message: ctx.payload.message,
          })
          .pipe(
            Effect.catchTag("Permission.NotFoundError", (error) =>
              Effect.fail(
                new PermissionNotFoundError({
                  requestID: String(error.requestID),
                  message: `Permission request not found: ${error.requestID}`,
                }),
              ),
            ),
          ),
      )
      return true
    })

    const nativeAgent = Effect.fn("AgentCenterHttpApi.nativeAgent")(function* (ctx: {
      params: typeof SessionParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      return yield* store.provide({ directory: binding.allowedRoot }, agentSvc.list())
    })

    const nativeCommand = Effect.fn("AgentCenterHttpApi.nativeCommand")(function* (ctx: {
      params: typeof SessionParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      return yield* store.provide({ directory: binding.allowedRoot }, commandSvc.list())
    })

    const nativeConfig = Effect.fn("AgentCenterHttpApi.nativeConfig")(function* (ctx: {
      params: typeof SessionParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      return yield* store.provide({ directory: binding.allowedRoot }, configSvc.get())
    })

    const nativeConfigProviders = Effect.fn("AgentCenterHttpApi.nativeConfigProviders")(function* (ctx: {
      params: typeof SessionParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      const providers = yield* store.provide({ directory: binding.allowedRoot }, providerSvc.list())
      return {
        providers: Object.values(providers).map(Provider.toPublicInfo),
        default: Provider.defaultModelIDs(providers),
      }
    })

    const nativeFormatter = Effect.fn("AgentCenterHttpApi.nativeFormatter")(function* (ctx: {
      params: typeof SessionParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      return yield* store.provide({ directory: binding.allowedRoot }, formatSvc.status())
    })

    const nativeLsp = Effect.fn("AgentCenterHttpApi.nativeLsp")(function* (ctx: {
      params: typeof SessionParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      return yield* store.provide({ directory: binding.allowedRoot }, lspSvc.status())
    })

    const nativeMcp = Effect.fn("AgentCenterHttpApi.nativeMcp")(function* (ctx: {
      params: typeof SessionParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      return yield* store.provide({ directory: binding.allowedRoot }, mcpSvc.status())
    })

    const nativePath = Effect.fn("AgentCenterHttpApi.nativePath")(function* (ctx: {
      params: typeof SessionParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      return {
        home: "",
        state: "",
        config: "",
        worktree: binding.workspaceId,
        directory: binding.workspaceId,
      }
    })

    const nativePermission = Effect.fn("AgentCenterHttpApi.nativePermission")(function* (ctx: {
      params: typeof SessionParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      const items = yield* store.provide({ directory: binding.allowedRoot }, permissionSvc.list())
      return items.filter((item) => item.sessionID === ctx.params.sessionID)
    })

    const nativeProject = Effect.fn("AgentCenterHttpApi.nativeProject")(function* (ctx: {
      params: typeof SessionParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      const instance = yield* store.provide({ directory: binding.allowedRoot }, InstanceState.context)
      return [toClientProject(instance.project, binding)]
    })

    const nativeProjectCurrent = Effect.fn("AgentCenterHttpApi.nativeProjectCurrent")(function* (ctx: {
      params: typeof SessionParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      return toClientProject((yield* store.provide({ directory: binding.allowedRoot }, InstanceState.context)).project, binding)
    })

    const nativeProvider = Effect.fn("AgentCenterHttpApi.nativeProvider")(function* (ctx: {
      params: typeof SessionParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      const config = yield* store.provide({ directory: binding.allowedRoot }, configSvc.get())
      const all = yield* ModelsDev.Service.use((service) => service.get())
      const disabled = new Set(config.disabled_providers ?? [])
      const enabled = config.enabled_providers ? new Set(config.enabled_providers) : undefined
      const filtered = Object.fromEntries(
        Object.entries(all).filter(([key]) => (enabled ? enabled.has(key) : true) && !disabled.has(key)),
      )
      const connected = yield* store.provide({ directory: binding.allowedRoot }, providerSvc.list())
      const providers = Object.assign(
        mapValues(filtered, (item) => Provider.fromModelsDevProvider(item)),
        connected,
      )
      return {
        all: Object.values(providers).map(Provider.toPublicInfo),
        default: Provider.defaultModelIDs(providers),
        connected: Object.keys(connected),
      }
    })

    const nativeQuestion = Effect.fn("AgentCenterHttpApi.nativeQuestion")(function* (ctx: {
      params: typeof SessionParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      const items = yield* store.provide({ directory: binding.allowedRoot }, questionSvc.list())
      return items.filter((item) => item.sessionID === ctx.params.sessionID)
    })

    const nativeVcs = Effect.fn("AgentCenterHttpApi.nativeVcs")(function* (ctx: {
      params: typeof SessionParams.Type
    }) {
      const binding = yield* requireBinding(ctx.params.sessionID)
      const [branch, default_branch] = yield* store.provide(
        { directory: binding.allowedRoot },
        Effect.all([vcs.branch(), vcs.defaultBranch()], { concurrency: "unbounded" }),
      )
      return { branch, default_branch }
    })

    return handlers
      .handle("me", me)
      .handle("projects", projects)
      .handle("workItems", workItems)
      .handle("openWorkItem", openWorkItem)
      .handle("scope", scopeInfo)
      .handle("event", events)
      .handle("bootstrap", bootstrap)
      .handle("messages", messages)
      .handle("message", message)
      .handle("update", update)
      .handle("share", share)
      .handle("unshare", unshare)
      .handle("deleteMessage", deleteMessage)
      .handle("deletePart", deletePart)
      .handle("updatePart", updatePart)
      .handle("children", children)
      .handle("todo", todo)
      .handle("findText", findText)
      .handle("findFile", findFile)
      .handle("fileList", fileList)
      .handle("fileContent", fileContent)
      .handle("fileStatus", fileStatus)
      .handle("prompt", sendPrompt)
      .handle("command", command)
      .handle("shell", shell)
      .handle("abort", abort)
      .handle("fork", fork)
      .handle("compact", compact)
      .handle("revert", revert)
      .handle("unrevert", unrevert)
      .handle("questions", questions)
      .handle("questionReply", questionReply)
      .handle("questionReject", questionReject)
      .handle("permissions", permissions)
      .handle("permissionReply", permissionReply)
      .handle("nativeAgent", nativeAgent)
      .handle("nativeCommand", nativeCommand)
      .handle("nativeConfig", nativeConfig)
      .handle("nativeConfigProviders", nativeConfigProviders)
      .handle("nativeFormatter", nativeFormatter)
      .handle("nativeLsp", nativeLsp)
      .handle("nativeMcp", nativeMcp)
      .handle("nativePath", nativePath)
      .handle("nativePermission", nativePermission)
      .handle("nativeProject", nativeProject)
      .handle("nativeProjectCurrent", nativeProjectCurrent)
      .handle("nativeProvider", nativeProvider)
      .handle("nativeQuestion", nativeQuestion)
      .handle("nativeVcs", nativeVcs)
  }),
)
