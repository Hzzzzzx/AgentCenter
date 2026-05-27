import type { ParentProps } from "solid-js"
import { createMemo, createResource, createSignal, onCleanup, onMount, Show } from "solid-js"
import { useNavigate } from "@solidjs/router"
import type { Todo } from "@opencode-ai/sdk/v2"
import { DataProvider, DialogProvider, FileComponentProvider, I18nProvider } from "@opencode-ai/ui/context"
import type { UiI18n } from "@opencode-ai/ui/context/i18n"
import { MarkedProvider } from "@opencode-ai/ui/context/marked"
import { File } from "@opencode-ai/ui/file"
import { Font } from "@opencode-ai/ui/font"
import { ThemeProvider } from "@opencode-ai/ui/theme/context"
import { QueryClient, QueryClientProvider } from "@tanstack/solid-query"
import { PromptInput } from "../../../app/src/components/prompt-input"
import { CommandProvider, useCommand } from "../../../app/src/context/command"
import { CommentsProvider } from "../../../app/src/context/comments"
import { FileProvider } from "../../../app/src/context/file"
import { GlobalSDKProvider } from "../../../app/src/context/global-sdk"
import { GlobalSyncProvider } from "../../../app/src/context/global-sync"
import { HighlightsProvider } from "../../../app/src/context/highlights"
import { LanguageProvider, useLanguage } from "../../../app/src/context/language"
import { LayoutProvider } from "../../../app/src/context/layout"
import { LocalProvider } from "../../../app/src/context/local"
import { ModelsProvider } from "../../../app/src/context/models"
import { NotificationProvider } from "../../../app/src/context/notification"
import { PermissionProvider } from "../../../app/src/context/permission"
import { type Platform, PlatformProvider } from "../../../app/src/context/platform"
import { PromptProvider, usePrompt } from "../../../app/src/context/prompt"
import { SDKProvider } from "../../../app/src/context/sdk"
import { ServerConnection, ServerProvider } from "../../../app/src/context/server"
import { SettingsProvider } from "../../../app/src/context/settings"
import { useSync } from "../../../app/src/context/sync"
import { TerminalProvider } from "../../../app/src/context/terminal"
import type { FileSelection } from "../../../app/src/context/file/types"
import { SessionTodoDock } from "../../../app/src/pages/session/composer/session-todo-dock"

export type NativePromptSession = {
  sessionId: string
  workspaceId: string
}

type NativePromptComposerProps = {
  opened?: NativePromptSession
  blocked?: boolean
  commandActions?: NativeSessionCommandActions
  onSubmit?: () => void
  onTransportEvent?: (message: string) => void
}

export type NativeSessionCommandActions = {
  canUndo?: () => boolean
  onUndo?: () => void | Promise<void>
  canRedo?: () => boolean
  onRedo?: () => void | Promise<void>
  canCompact?: () => boolean
  onCompact?: () => void | Promise<void>
  canFork?: () => boolean
  onFork?: () => void | Promise<void>
}

export type AddWorkspaceFileContextDetail = {
  sessionId: string
  path: string
  selection?: FileSelection
  comment?: string
  commentID?: string
  commentOrigin?: "review" | "file"
  preview?: string
}

export type UpdateWorkspaceFileContextCommentDetail = {
  sessionId: string
  path: string
  commentID: string
  comment: string
  preview?: string
}

export type RemoveWorkspaceFileContextCommentDetail = {
  sessionId: string
  path: string
  commentID: string
}

const addWorkspaceFileContextEvent = "agentcenter:add-workspace-file-context"
const updateWorkspaceFileContextCommentEvent = "agentcenter:update-workspace-file-context-comment"
const removeWorkspaceFileContextCommentEvent = "agentcenter:remove-workspace-file-context-comment"
const nativeFetch = window.fetch.bind(window)
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnReconnect: false,
      refetchOnMount: false,
      refetchOnWindowFocus: false,
    },
  },
})

export function NativePromptComposer(props: NativePromptComposerProps) {
  const active = createMemo(() => (props.opened && !props.blocked ? props.opened : undefined))
  return (
    <Show when={active()} keyed fallback={<div class="native-prompt-placeholder" />}>
      {(opened) => (
        <NativePromptProviders opened={opened} onTransportEvent={props.onTransportEvent}>
          <WorkspaceControlDirectory opened={opened}>
            <TerminalProvider>
              <FileProvider>
                <PromptProvider>
                  <WorkspaceControlPromptContextBridge opened={opened} />
                  <WorkspaceControlSessionCommandBridge actions={props.commandActions} />
                  <PromptProviderRoot onSubmit={props.onSubmit} />
                </PromptProvider>
              </FileProvider>
            </TerminalProvider>
          </WorkspaceControlDirectory>
        </NativePromptProviders>
      )}
    </Show>
  )
}

function WorkspaceControlSessionCommandBridge(props: { actions?: NativeSessionCommandActions }) {
  const command = useCommand()
  const language = useLanguage()
  const t = (key: string) => language.t(key as never)
  const run = (action: (() => void | Promise<void>) | undefined) => {
    if (!action) return
    void action()
  }

  command.register("agentcenter-session", () => [
    {
      id: "session.undo",
      title: t("command.session.undo"),
      description: t("command.session.undo.description"),
      category: t("command.category.session"),
      slash: "undo",
      disabled: !(props.actions?.canUndo?.() ?? false),
      onSelect: () => run(props.actions?.onUndo),
    },
    {
      id: "session.redo",
      title: t("command.session.redo"),
      description: t("command.session.redo.description"),
      category: t("command.category.session"),
      slash: "redo",
      disabled: !(props.actions?.canRedo?.() ?? false),
      onSelect: () => run(props.actions?.onRedo),
    },
    {
      id: "session.compact",
      title: t("command.session.compact"),
      description: t("command.session.compact.description"),
      category: t("command.category.session"),
      slash: "compact",
      disabled: !(props.actions?.canCompact?.() ?? false),
      onSelect: () => run(props.actions?.onCompact),
    },
    {
      id: "session.fork",
      title: t("command.session.fork"),
      description: t("command.session.fork.description"),
      category: t("command.category.session"),
      slash: "fork",
      disabled: !(props.actions?.canFork?.() ?? false),
      onSelect: () => run(props.actions?.onFork),
    },
  ])

  return null
}

export function addWorkspaceFileContext(detail: AddWorkspaceFileContextDetail) {
  window.dispatchEvent(new CustomEvent(addWorkspaceFileContextEvent, { detail }))
}

export function updateWorkspaceFileContextComment(detail: UpdateWorkspaceFileContextCommentDetail) {
  window.dispatchEvent(new CustomEvent(updateWorkspaceFileContextCommentEvent, { detail }))
}

export function removeWorkspaceFileContextComment(detail: RemoveWorkspaceFileContextCommentDetail) {
  window.dispatchEvent(new CustomEvent(removeWorkspaceFileContextCommentEvent, { detail }))
}

function PromptProviderRoot(props: { onSubmit?: () => void }) {
  return (
    <CommentsProvider>
      <PromptInput class="agentcenter-native-prompt" onSubmit={props.onSubmit} />
    </CommentsProvider>
  )
}

function WorkspaceControlPromptContextBridge(props: { opened: NativePromptSession }) {
  const prompt = usePrompt()
  const addHandler = (event: Event) => {
    if (!(event instanceof CustomEvent)) return
    if (!isAddWorkspaceFileContextDetail(event.detail)) return
    if (event.detail.sessionId !== props.opened.sessionId) return
    prompt.context.add({
      type: "file",
      path: event.detail.path,
      selection: event.detail.selection,
      comment: event.detail.comment,
      commentID: event.detail.commentID,
      commentOrigin: event.detail.commentOrigin,
      preview: event.detail.preview,
    })
  }
  const updateHandler = (event: Event) => {
    if (!(event instanceof CustomEvent)) return
    if (!isUpdateWorkspaceFileContextCommentDetail(event.detail)) return
    if (event.detail.sessionId !== props.opened.sessionId) return
    prompt.context.updateComment(event.detail.path, event.detail.commentID, {
      comment: event.detail.comment,
      preview: event.detail.preview,
    })
  }
  const removeHandler = (event: Event) => {
    if (!(event instanceof CustomEvent)) return
    if (!isRemoveWorkspaceFileContextCommentDetail(event.detail)) return
    if (event.detail.sessionId !== props.opened.sessionId) return
    prompt.context.removeComment(event.detail.path, event.detail.commentID)
  }

  onMount(() => {
    window.addEventListener(addWorkspaceFileContextEvent, addHandler)
    window.addEventListener(updateWorkspaceFileContextCommentEvent, updateHandler)
    window.addEventListener(removeWorkspaceFileContextCommentEvent, removeHandler)
  })
  onCleanup(() => {
    window.removeEventListener(addWorkspaceFileContextEvent, addHandler)
    window.removeEventListener(updateWorkspaceFileContextCommentEvent, updateHandler)
    window.removeEventListener(removeWorkspaceFileContextCommentEvent, removeHandler)
  })
  return null
}

function isAddWorkspaceFileContextDetail(input: unknown): input is AddWorkspaceFileContextDetail {
  if (typeof input !== "object" || input === null) return false
  const detail = input as Partial<AddWorkspaceFileContextDetail>
  return (
    typeof detail.sessionId === "string" &&
    typeof detail.path === "string" &&
    detail.path.length > 0 &&
    (detail.selection === undefined || isFileSelection(detail.selection)) &&
    (detail.comment === undefined || typeof detail.comment === "string") &&
    (detail.commentID === undefined || typeof detail.commentID === "string") &&
    (detail.commentOrigin === undefined || detail.commentOrigin === "review" || detail.commentOrigin === "file") &&
    (detail.preview === undefined || typeof detail.preview === "string")
  )
}

function isUpdateWorkspaceFileContextCommentDetail(input: unknown): input is UpdateWorkspaceFileContextCommentDetail {
  if (typeof input !== "object" || input === null) return false
  const detail = input as Partial<UpdateWorkspaceFileContextCommentDetail>
  return (
    typeof detail.sessionId === "string" &&
    typeof detail.path === "string" &&
    detail.path.length > 0 &&
    typeof detail.commentID === "string" &&
    detail.commentID.length > 0 &&
    typeof detail.comment === "string" &&
    (detail.preview === undefined || typeof detail.preview === "string")
  )
}

function isRemoveWorkspaceFileContextCommentDetail(input: unknown): input is RemoveWorkspaceFileContextCommentDetail {
  if (typeof input !== "object" || input === null) return false
  const detail = input as Partial<RemoveWorkspaceFileContextCommentDetail>
  return (
    typeof detail.sessionId === "string" &&
    typeof detail.path === "string" &&
    detail.path.length > 0 &&
    typeof detail.commentID === "string" &&
    detail.commentID.length > 0
  )
}

function isFileSelection(input: unknown): input is FileSelection {
  if (typeof input !== "object" || input === null) return false
  const selection = input as Partial<FileSelection>
  return (
    Number.isFinite(selection.startLine) &&
    Number.isFinite(selection.startChar) &&
    Number.isFinite(selection.endLine) &&
    Number.isFinite(selection.endChar)
  )
}

function NativePromptProviders(props: ParentProps<{ opened: NativePromptSession; onTransportEvent?: (message: string) => void }>) {
  const platform = createMemo(() => createPlatform(props.opened, props.onTransportEvent))
  const serverUrl = "http://agentcenter.workspace-control"
  const server: ServerConnection.Http = {
    type: "http",
    displayName: "AgentCenter workspace-control",
    http: { url: serverUrl },
  }

  return (
    <PlatformProvider value={platform()}>
      <Font />
      <ThemeProvider>
        <LanguageProvider locale="zh">
          <UiI18nBridge>
            <QueryClientProvider client={queryClient}>
              <DialogProvider>
                <MarkedProvider>
                  <FileComponentProvider component={File}>
                    <ServerProvider
                      defaultServer={ServerConnection.Key.make(serverUrl)}
                      servers={[server]}
                      disableHealthCheck
                    >
                      <GlobalSDKProvider>
                        <GlobalSyncProvider>
                          <SettingsProvider>
                            <PermissionProvider>
                              <LayoutProvider>
                                <NotificationProvider>
                                  <ModelsProvider>
                                    <CommandProvider>
                                      <HighlightsProvider>{props.children}</HighlightsProvider>
                                    </CommandProvider>
                                  </ModelsProvider>
                                </NotificationProvider>
                              </LayoutProvider>
                            </PermissionProvider>
                          </SettingsProvider>
                        </GlobalSyncProvider>
                      </GlobalSDKProvider>
                    </ServerProvider>
                  </FileComponentProvider>
                </MarkedProvider>
              </DialogProvider>
            </QueryClientProvider>
          </UiI18nBridge>
        </LanguageProvider>
      </ThemeProvider>
    </PlatformProvider>
  )
}

function WorkspaceControlDirectory(props: ParentProps<{ opened: NativePromptSession }>) {
  return (
    <SDKProvider directory={props.opened.workspaceId}>
      <WorkspaceControlData opened={props.opened}>{props.children}</WorkspaceControlData>
    </SDKProvider>
  )
}

function WorkspaceControlData(props: ParentProps<{ opened: NativePromptSession }>) {
  const sync = useSync()
  const navigate = useNavigate()
  const language = useLanguage()
  const [todoCollapsed, setTodoCollapsed] = createSignal(false)
  createResource(
    () => props.opened.sessionId,
    (id) => Promise.all([sync.session.sync(id), sync.session.todo(id)]),
  )
  const href = (sessionId: string) => `/${encodeURIComponent(workspaceRouteToken(props.opened.workspaceId))}/session/${encodeURIComponent(sessionId)}`
  const todos = createMemo((): Todo[] => sync.data.todo[props.opened.sessionId] ?? [])

  return (
    <DataProvider
      data={sync.data}
      directory={props.opened.workspaceId}
      onNavigateToSession={(sessionId) => navigate(href(sessionId))}
      onSessionHref={href}
    >
      <LocalProvider>
        <Show when={todos().length > 0}>
          <div class="agentcenter-native-todo-dock">
            <SessionTodoDock
              sessionID={props.opened.sessionId}
              todos={todos()}
              collapsed={todoCollapsed()}
              onToggle={() => setTodoCollapsed(!todoCollapsed())}
              collapseLabel={language.t("session.todo.collapse")}
              expandLabel={language.t("session.todo.expand")}
              dockProgress={1}
            />
          </div>
        </Show>
        {props.children}
      </LocalProvider>
    </DataProvider>
  )
}

function UiI18nBridge(props: ParentProps) {
  const language = useLanguage()
  const value: UiI18n = {
    locale: language.intl,
    t: (key, params) => language.t(key as never, params as never),
  }
  return <I18nProvider value={value}>{props.children}</I18nProvider>
}

function createPlatform(opened: NativePromptSession, onTransportEvent?: (message: string) => void): Platform {
  return {
    platform: "web",
    openLink(url) {
      window.open(url, "_blank", "noopener,noreferrer")
    },
    back() {
      window.history.back()
    },
    forward() {
      window.history.forward()
    },
    restart() {
      window.location.reload()
      return Promise.resolve()
    },
    notify() {
      return Promise.resolve()
    },
    fetch: createWorkspaceControlFetch(opened, onTransportEvent),
  }
}

function createWorkspaceControlFetch(opened: NativePromptSession, onTransportEvent?: (message: string) => void): typeof fetch {
  return async (input, init) => {
    const request = input instanceof Request ? input : new Request(input, init)
    const url = new URL(request.url, window.location.origin)
    const directoryCheck = validateDirectory(url, request, opened.workspaceId)
    if (directoryCheck) return directoryCheck

    if (request.method === "GET" && url.pathname === "/session") {
      return bootstrapSessionList(opened.sessionId)
    }

    if (request.method === "GET" && url.pathname === "/session/status") {
      return bootstrapStatusMap(opened.sessionId)
    }

    if (request.method === "GET" && url.pathname === "/global/event") {
      return nativeFetch(`/agentcenter/session/${encodeURIComponent(opened.sessionId)}/event`, {
        headers: request.headers,
        signal: request.signal,
      })
    }

    if (request.method === "GET" && (url.pathname === "/global/config" || url.pathname === "/global/health")) {
      return nativeFetch(`${url.pathname}${url.search}`, {
        headers: request.headers,
        signal: request.signal,
      })
    }

    const sessionPath = /^\/session\/([^/]+)\/([^/]+)$/.exec(url.pathname)
    const sessionMessagePath = /^\/session\/([^/]+)\/message\/([^/]+)$/.exec(url.pathname)
    const sessionPartPath = /^\/session\/([^/]+)\/message\/([^/]+)\/part\/([^/]+)$/.exec(url.pathname)
    const sessionRoot = /^\/session\/([^/]+)$/.exec(url.pathname)
    if (sessionRoot) {
      const [, sessionID] = sessionRoot
      if (sessionID !== opened.sessionId) return forbidden(`Session is outside workspace-control scope: ${sessionID}`)
      if (request.method === "GET") return bootstrapField(sessionID, "session")
      if (request.method === "PATCH") return forwardJson(request, `/agentcenter/session/${encodeURIComponent(sessionID)}`)
      return forbidden("Raw OpenCode session mutations must go through workspace-control")
    }

    if (sessionPartPath) {
      const [, sessionID, messageID, partID] = sessionPartPath
      if (sessionID !== opened.sessionId) return forbidden(`Session is outside workspace-control scope: ${sessionID}`)
      const path = `/agentcenter/session/${encodeURIComponent(sessionID)}/message/${encodeURIComponent(messageID)}/part/${encodeURIComponent(partID)}`
      if (request.method === "DELETE" || request.method === "PATCH") return forwardJson(request, path)
      return forbidden("Raw OpenCode part route is not exposed in workspace-control")
    }

    if (sessionMessagePath) {
      const [, sessionID, messageID] = sessionMessagePath
      if (sessionID !== opened.sessionId) return forbidden(`Session is outside workspace-control scope: ${sessionID}`)
      const path = `/agentcenter/session/${encodeURIComponent(sessionID)}/message/${encodeURIComponent(messageID)}`
      if (request.method === "GET") return nativeFetch(path)
      if (request.method === "DELETE") return forwardJson(request, path)
      return forbidden("Raw OpenCode message route is not exposed in workspace-control")
    }

    if (sessionPath) {
      const [, sessionID, action] = sessionPath
      if (sessionID !== opened.sessionId) return forbidden(`Session is outside workspace-control scope: ${sessionID}`)

      if (request.method === "GET" && action === "message") {
        return nativeFetch(`/agentcenter/session/${encodeURIComponent(sessionID)}/message`)
      }

      if (request.method === "GET" && action === "diff") {
        return bootstrapField(sessionID, "diff")
      }

      if (request.method === "GET" && action === "todo") {
        return nativeFetch(`/agentcenter/session/${encodeURIComponent(sessionID)}/todo`)
      }

      if (request.method === "GET" && action === "children") {
        return nativeFetch(`/agentcenter/session/${encodeURIComponent(sessionID)}/children`)
      }

      if (request.method === "POST" && action === "prompt_async") {
        const response = await forwardJson(request, `/agentcenter/session/${encodeURIComponent(sessionID)}/message`)
        if (!response.ok) return response
        onTransportEvent?.("OpenCode prompt accepted through workspace-control")
        return new Response(null, { status: 204, statusText: "No Content" })
      }

      if (request.method === "POST" && action === "command") {
        onTransportEvent?.("OpenCode command routed through workspace-control")
        return forwardJson(request, `/agentcenter/session/${encodeURIComponent(sessionID)}/command`)
      }

      if (request.method === "POST" && action === "shell") {
        onTransportEvent?.("OpenCode shell command routed through workspace-control")
        return forwardJson(request, `/agentcenter/session/${encodeURIComponent(sessionID)}/shell`)
      }

      if (request.method === "POST" && action === "abort") {
        onTransportEvent?.("OpenCode abort routed through workspace-control")
        return forwardJson(request, `/agentcenter/session/${encodeURIComponent(sessionID)}/abort`)
      }

      if (request.method === "POST" && action === "summarize") {
        onTransportEvent?.("OpenCode summarize routed through workspace-control")
        return forwardSummarize(request, `/agentcenter/session/${encodeURIComponent(sessionID)}/compact`)
      }

      if (action === "share") {
        const path = `/agentcenter/session/${encodeURIComponent(sessionID)}/share`
        if (request.method === "POST" || request.method === "DELETE") return forwardJson(request, path)
      }

      if (request.method === "POST" && (action === "revert" || action === "unrevert")) {
        return forwardJson(request, `/agentcenter/session/${encodeURIComponent(sessionID)}/${action}`)
      }

      return forbidden(`Raw OpenCode session route is not exposed in workspace-control: ${action}`)
    }

    if (request.method === "POST" && url.pathname === `/session/${opened.sessionId}/message`) {
      return forwardJson(request, `/agentcenter/session/${encodeURIComponent(opened.sessionId)}/message`)
    }

    if (request.method === "GET" && url.pathname === "/find/file") {
      return nativeFetch(rewriteUrl(url, `/agentcenter/session/${encodeURIComponent(opened.sessionId)}/find/file`, [
        "query",
        "dirs",
        "type",
        "limit",
      ]).toString())
    }

    if (request.method === "GET" && url.pathname === "/find") {
      return nativeFetch(rewriteUrl(url, `/agentcenter/session/${encodeURIComponent(opened.sessionId)}/find`, ["pattern"]).toString())
    }

    if (request.method === "GET" && url.pathname === "/file/content") {
      return nativeFetch(rewriteUrl(url, `/agentcenter/session/${encodeURIComponent(opened.sessionId)}/file/content`, ["path"]).toString())
    }

    if (request.method === "GET" && url.pathname === "/file/status") {
      return nativeFetch(`/agentcenter/session/${encodeURIComponent(opened.sessionId)}/file/status`)
    }

    if (request.method === "GET" && url.pathname === "/file") {
      return nativeFetch(rewriteUrl(url, `/agentcenter/session/${encodeURIComponent(opened.sessionId)}/file`, ["path"]).toString())
    }

    const nativeRead = controlledNativeReadPath(url)
    if (request.method === "GET" && nativeRead) {
      return nativeFetch(controlledSessionUrl(opened.sessionId, nativeRead).toString())
    }

    if (url.searchParams.has("directory") || request.headers.has("x-opencode-directory")) {
      return forbidden("Raw OpenCode directory route is not exposed in workspace-control")
    }

    return nativeFetch(request)
  }
}

function validateDirectory(url: URL, request: Request, workspaceId: string) {
  const raw = request.headers.get("x-opencode-directory") ?? url.searchParams.get("directory")
  if (!raw) return
  const requested = decodeURIComponentSafe(raw)
  if (requested === workspaceId) return
  return forbidden("Directory is outside workspace-control scope")
}

async function forwardJson(request: Request, path: string) {
  const headers = new Headers(request.headers)
  headers.delete("x-opencode-directory")
  return nativeFetch(path, {
    method: request.method,
    headers,
    body: await request.clone().text(),
    signal: request.signal,
  })
}

async function forwardSummarize(request: Request, path: string) {
  const payload = await request.clone().json().catch(() => undefined)
  const body = isSummarizePayload(payload)
    ? JSON.stringify({
        model: { providerID: payload.providerID, modelID: payload.modelID },
        auto: payload.auto,
      })
    : await request.clone().text()

  const headers = new Headers(request.headers)
  headers.delete("x-opencode-directory")
  return nativeFetch(path, {
    method: request.method,
    headers,
    body,
    signal: request.signal,
  })
}

function isSummarizePayload(input: unknown): input is { providerID: string; modelID: string; auto?: boolean } {
  if (typeof input !== "object" || input === null) return false
  const payload = input as Partial<{ providerID: string; modelID: string; auto: boolean }>
  return (
    typeof payload.providerID === "string" &&
    typeof payload.modelID === "string" &&
    (payload.auto === undefined || typeof payload.auto === "boolean")
  )
}

async function bootstrapField(sessionID: string, field: "session" | "diff") {
  const response = await nativeFetch(`/agentcenter/session/${encodeURIComponent(sessionID)}/bootstrap`)
  if (!response.ok) return response
  const data = await response.json() as { session?: unknown; diff?: unknown }
  return Response.json(data[field])
}

async function bootstrapSessionList(sessionID: string) {
  const response = await nativeFetch(`/agentcenter/session/${encodeURIComponent(sessionID)}/bootstrap`)
  if (!response.ok) return response
  const data = await response.json() as { session?: unknown }
  return Response.json(data.session ? [data.session] : [])
}

async function bootstrapStatusMap(sessionID: string) {
  const response = await nativeFetch(`/agentcenter/session/${encodeURIComponent(sessionID)}/bootstrap`)
  if (!response.ok) return response
  const data = await response.json() as { status?: unknown }
  return Response.json(data.status ? { [sessionID]: data.status } : {})
}

function rewriteUrl(source: URL, path: string, fields: string[]) {
  const target = new URL(path, window.location.origin)
  for (const field of fields) {
    const value = source.searchParams.get(field)
    if (value !== null) target.searchParams.set(field, value)
  }
  return target
}

function controlledNativeReadPath(url: URL) {
  if (url.pathname === "/agent") return "agent"
  if (url.pathname === "/command") return "command"
  if (url.pathname === "/config") return "config"
  if (url.pathname === "/config/providers") return "config/providers"
  if (url.pathname === "/formatter") return "formatter"
  if (url.pathname === "/lsp") return "lsp"
  if (url.pathname === "/mcp") return "mcp"
  if (url.pathname === "/path") return "path"
  if (url.pathname === "/permission") return "permission"
  if (url.pathname === "/project") return "project"
  if (url.pathname === "/project/current") return "project/current"
  if (url.pathname === "/provider") return "provider"
  if (url.pathname === "/question") return "question"
  if (url.pathname === "/vcs") return "vcs"
}

function controlledSessionUrl(sessionID: string, nativePath: string) {
  return new URL(`/agentcenter/session/${encodeURIComponent(sessionID)}/native/${nativePath}`, window.location.origin)
}

function forbidden(message: string) {
  return Response.json({ message }, { status: 403 })
}

function decodeURIComponentSafe(input: string) {
  try {
    return decodeURIComponent(input)
  } catch {
    return input
  }
}

function workspaceRouteToken(workspaceId: string) {
  return `w.${workspaceId}`
}
