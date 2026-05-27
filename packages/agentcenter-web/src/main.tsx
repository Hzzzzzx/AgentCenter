import "@opencode-ai/ui/styles/tailwind"
import "./styles.css"

import type { ParentProps } from "solid-js"
import { createMemo, For, onCleanup, onMount, Show } from "solid-js"
import { render } from "solid-js/web"
import { createStore } from "solid-js/store"
import { Route, Router, useNavigate, useParams } from "@solidjs/router"
import type {
  Agent as OpenCodeAgent,
  Message as OpenCodeMessage,
  Part as OpenCodePart,
  PermissionRule,
  PermissionRequest,
  Provider,
  QuestionAnswer,
  QuestionRequest,
  Session,
  SessionStatus,
  SnapshotFileDiff,
  Todo,
} from "@opencode-ai/sdk/v2"
import { DataProvider, DialogProvider, FileComponentProvider, I18nProvider } from "@opencode-ai/ui/context"
import type { NormalizedProviderListResponse } from "@opencode-ai/ui/context"
import type { UiI18n, UiI18nParams } from "@opencode-ai/ui/context/i18n"
import { MarkedProvider } from "@opencode-ai/ui/context/marked"
import { File } from "@opencode-ai/ui/file"
import { Icon } from "@opencode-ai/ui/icon"
import { SessionTurn } from "@opencode-ai/ui/session-turn"
import { dict as zh } from "@opencode-ai/ui/i18n/zh"
import { ControlledFilePanel, openWorkspaceFile } from "./opencode-session/controlled-file-panel"
import { NativePromptComposer } from "./opencode-session/native-prompt-composer"
import { SessionPermissionDock } from "./opencode-session/session-permission-dock"
import { SessionQuestionDock } from "./opencode-session/session-question-dock"

type Identity = { tenantId: string; userId: string }
type WorkItemStatus = "open" | "running" | "blocked" | "done" | "archived"
type WorkItem = { id: string; title: string; status: WorkItemStatus }
type Project = { tenantId: string; projectId: string; name: string; workItems: WorkItem[] }

type OpenedSession = {
  tenantId: string
  projectId: string
  workItemId: string
  userId: string
  sessionId: string
  workspaceId: string
  allowedRootLabel: string
  session: { id: string; title: string; directoryLabel: string }
  permission: PermissionRule[]
}

type MessageWithParts = { info: OpenCodeMessage; parts: OpenCodePart[] }
type Toast = { tone: "info" | "error" | "success"; text: string }
type ServerEvent = {
  payload?: {
    type?: string
    properties?: Record<string, unknown>
    syncEvent?: { type?: string; data?: Record<string, unknown> }
  }
}
type OpenCodeEvent = { type: string; properties?: Record<string, unknown> }
type PendingPartDelta = { messageID: string; field: string; value: string }
type PendingPartsByMessage = Record<string, Record<string, OpenCodePart>>
type SessionBootstrap = {
  session: Session
  messages: MessageWithParts[]
  status: SessionStatus
  diff: SnapshotFileDiff[]
  todos: Todo[]
  questions: QuestionRequest[]
  permissions: PermissionRequest[]
}
type SessionScope = {
  tenantId: string
  projectId: string
  workItemId: string
  userId: string
  sessionId: string
  workspaceId: string
  allowedRootLabel: string
}

type State = {
  loading: boolean
  sending: boolean
  aborting: boolean
  identity?: Identity
  projects: Project[]
  selectedProjectId: string
  selectedWorkItemId: string
  opened?: OpenedSession
  messages: MessageWithParts[]
  sessionInfo: Record<string, Session>
  sessionStatus: Record<string, SessionStatus>
  sessionDiff: Record<string, SnapshotFileDiff[]>
  sessionTodo: Record<string, Todo[]>
  questions: Record<string, QuestionRequest[]>
  permissions: Record<string, PermissionRequest[]>
  agents: OpenCodeAgent[]
  providerContext?: NormalizedProviderListResponse
  permissionResponding: Record<string, boolean>
  commandBusy: Record<string, boolean>
  partTextAccumDelta: Record<string, string>
  pendingPartDelta: Record<string, PendingPartDelta>
  pendingParts: PendingPartsByMessage
  input: string
  eventStatus: string
  toast?: Toast
}

const idleStatus: SessionStatus = { type: "idle" }
const emptyProvider = { all: new Map(), default: {}, connected: [] } satisfies NormalizedProviderListResponse
const skippedPartTypes = new Set<OpenCodePart["type"]>(["patch", "step-start", "step-finish"])

const [state, setState] = createStore<State>({
  loading: true,
  sending: false,
  aborting: false,
  projects: [],
  selectedProjectId: "",
  selectedWorkItemId: "",
  messages: [],
  sessionInfo: {},
  sessionStatus: {},
  sessionDiff: {},
  sessionTodo: {},
  questions: {},
  permissions: {},
  agents: [],
  permissionResponding: {},
  commandBusy: {},
  partTextAccumDelta: {},
  pendingPartDelta: {},
  pendingParts: {},
  input: "",
  eventStatus: "未连接",
})

const app = document.querySelector<HTMLDivElement>("#app")
if (!app) throw new Error("#app not found")

let eventSource: EventSource | undefined

const uiI18n: UiI18n = {
  locale: () => "zh-CN",
  t: (key, params) => resolveTemplate(uiMessages[key] ?? String(key), params),
}
const uiMessages: Record<string, string> = zh

render(
  () => (
    <Router>
      <Route path="/:dir/session/:id" component={AgentCenterApp} />
      <Route path="*" component={AgentCenterApp} />
    </Router>
  ),
  app,
)

function AgentCenterApp() {
  const params = useParams()
  const navigate = useNavigate()
  onMount(() => void boot({ sessionId: params.id, workspaceId: params.dir, navigate }))
  onCleanup(() => {
    eventSource?.close()
  })

  const messages = createMemo(() => state.messages.map((message) => message.info))
  const provider = createMemo(() => state.providerContext ?? buildProviderContext(messages()))
  const parts = createMemo(() => {
    const result: Record<string, OpenCodePart[]> = {}
    for (const message of state.messages) result[message.info.id] = message.parts
    return result
  })
  const sessionData = createMemo(() => {
    const sessionRecord = state.opened ? (state.sessionInfo[state.opened.sessionId] ?? toOpenCodeSession(state.opened)) : undefined
    return {
      provider: provider(),
      session: sessionRecord ? [sessionRecord] : ([] as Session[]),
      session_status: state.sessionStatus,
      session_diff: state.sessionDiff,
      agent: state.agents,
      message: state.opened ? { [state.opened.sessionId]: messages() } : {},
      part: parts(),
      part_text_accum_delta: state.partTextAccumDelta,
    }
  })

  return (
    <OpenCodeProviders data={sessionData()} directory={state.opened?.workspaceId ?? ""}>
      <div class="ac-shell">
        <header class="topbar">
          <div class="brand">
            <span class="brand-mark">AC</span>
            <div>
              <strong>AgentCenter</strong>
              <small>OpenCode Native Runtime</small>
            </div>
          </div>
          <div class="runtime-pill">
            <span>{state.eventStatus}</span>
            <span class={`session-state session-state--${currentSessionStatus().type}`}>{sessionStatusLabel(currentSessionStatus())}</span>
            <strong>{state.identity?.tenantId ?? "local-tenant"}</strong>
            <em>{state.identity?.userId ?? "local-user"}</em>
          </div>
        </header>

        <aside class="project-pane">
          <div class="pane-heading">
            <span>中心工作空间</span>
            <strong>Project / Work Item</strong>
          </div>
          <Show when={!state.loading} fallback={<SkeletonList />}>
            <ProjectList />
          </Show>
        </aside>

        <main class="chat-pane">
          <ChatHeader />
          <section class="messages" id="message-scroll">
            <MessageList messages={messages()} />
          </section>
          <Composer />
        </main>

        <aside class="scope-pane">
          <div class="pane-heading">
            <span>运行边界</span>
            <strong>workspace-control</strong>
          </div>
          <ScopePanel />
        </aside>

        <Show when={state.toast}>
          {(toast) => <div class={`toast toast--${toast().tone}`}>{toast().text}</div>}
        </Show>
      </div>
    </OpenCodeProviders>
  )
}

function OpenCodeProviders(props: ParentProps<{ data: Parameters<typeof DataProvider>[0]["data"]; directory: string }>) {
  return (
    <I18nProvider value={uiI18n}>
      <DialogProvider>
        <MarkedProvider>
          <FileComponentProvider component={File}>
            <DataProvider data={props.data} directory={props.directory}>
              {props.children}
            </DataProvider>
          </FileComponentProvider>
        </MarkedProvider>
      </DialogProvider>
    </I18nProvider>
  )
}

async function boot(route?: {
  sessionId?: string
  workspaceId?: string
  navigate?: ReturnType<typeof useNavigate>
}) {
  try {
    const [identity, projects] = await Promise.all([api<Identity>("/agentcenter/me"), api<Project[]>("/agentcenter/project")])
    setState("identity", identity)
    setState("projects", projects)
    const restored = route?.sessionId
      ? await restoreRoutedSession(route.sessionId, route.workspaceId ? workspaceIdFromRoute(route.workspaceId) : undefined, route.navigate).catch((error) => {
          setState("toast", { tone: "error", text: `恢复会话失败：${errorText(error)}` })
          return false
        })
      : false
    if (!restored) {
      setState("selectedProjectId", projects[0]?.projectId ?? "")
      setState("selectedWorkItemId", projects[0]?.workItems[0]?.id ?? "")
      setState("toast", { tone: "success", text: "已连接 workspace-control" })
    }
  } catch (error) {
    setState("toast", {
      tone: "error",
      text: `连接失败：${errorText(error)}。请确认 OpenCode server 已启动。`,
    })
  } finally {
    setState("loading", false)
  }
}

async function restoreRoutedSession(
  sessionId: string,
  routeWorkspaceId?: string,
  navigate?: ReturnType<typeof useNavigate>,
) {
  const [scope, bootstrap] = await Promise.all([
    api<SessionScope>(`/agentcenter/session/${encodeURIComponent(sessionId)}/scope`),
    api<SessionBootstrap>(`/agentcenter/session/${encodeURIComponent(sessionId)}/bootstrap`),
  ])
  const opened: OpenedSession = {
    ...scope,
    session: {
      id: bootstrap.session.id,
      title: bootstrap.session.title,
      directoryLabel: scope.allowedRootLabel,
    },
    permission: bootstrap.session.permission ?? [],
  }
  setState("selectedProjectId", opened.projectId)
  setState("selectedWorkItemId", opened.workItemId)
  setState("opened", opened)
  applyBootstrap(sessionId, bootstrap)
  void loadNativeRenderContext(sessionId).catch((error) => {
    setState("toast", { tone: "error", text: `同步 OpenCode 原生上下文失败：${errorText(error)}` })
  })
  startEvents()
  if (routeWorkspaceId !== opened.workspaceId) navigateToNativeSession(opened, navigate)
  return true
}

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { "content-type": "application/json", ...init?.headers },
  })
  if (!response.ok) throw new Error((await response.text().catch(() => "")) || `${response.status} ${response.statusText}`)
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

function selectedProject() {
  return state.projects.find((project) => project.projectId === state.selectedProjectId) ?? state.projects[0]
}

function selectedWorkItem() {
  const project = selectedProject()
  return project?.workItems.find((item) => item.id === state.selectedWorkItemId) ?? project?.workItems[0]
}

async function openSelectedWorkItem(navigate?: ReturnType<typeof useNavigate>) {
  const project = selectedProject()
  const workItem = selectedWorkItem()
  if (!project || !workItem) return

  setState("sending", true)
  setState("messages", [])
  setState("sessionInfo", {})
  setState("sessionStatus", {})
  setState("sessionDiff", {})
  setState("sessionTodo", {})
  setState("questions", {})
  setState("permissions", {})
  setState("agents", [])
  setState("providerContext", undefined)
  setState("permissionResponding", {})
  setState("commandBusy", {})
  setState("partTextAccumDelta", {})
  setState("pendingPartDelta", {})
  setState("pendingParts", {})
  setState("opened", undefined)
  setState("toast", { tone: "info", text: "正在打开受控工作目录..." })

  try {
    const opened = await api<OpenedSession>(
      `/agentcenter/project/${encodeURIComponent(project.projectId)}/work-item/${encodeURIComponent(workItem.id)}/open`,
      { method: "POST", body: JSON.stringify({ title: workItem.title }) },
    )
    navigateToNativeSession(opened, navigate)
    setState("opened", opened)
    setState("toast", { tone: "success", text: `已打开 ${workItem.id} 的受控会话` })
    await loadBootstrap()
    startEvents()
    return opened
  } catch (error) {
    setState("toast", { tone: "error", text: `打开失败：${errorText(error)}` })
  } finally {
    setState("sending", false)
  }
}

function navigateToNativeSession(opened: OpenedSession, navigate?: ReturnType<typeof useNavigate>) {
  const href = `/${encodeURIComponent(workspaceRouteToken(opened.workspaceId))}/session/${encodeURIComponent(opened.sessionId)}`
  if (navigate) {
    navigate(href, { replace: true })
    return
  }
  window.history.replaceState(window.history.state, "", href)
}

async function loadBootstrap() {
  if (!state.opened) return
  const bootstrap = await api<SessionBootstrap>(`/agentcenter/session/${encodeURIComponent(state.opened.sessionId)}/bootstrap`)
  applyBootstrap(state.opened.sessionId, bootstrap)
  void loadNativeRenderContext(state.opened.sessionId).catch((error) => {
    setState("toast", { tone: "error", text: `同步 OpenCode 原生上下文失败：${errorText(error)}` })
  })
  scrollMessagesToBottom()
}

async function sendPrompt() {
  if (!state.opened || state.sending) return
  const text = state.input.trim()
  if (!text) return

  setState("sending", true)
  setState("input", "")
  setState("toast", { tone: "info", text: "消息已发送，等待 OpenCode 实时事件..." })

  try {
    await api<boolean>(`/agentcenter/session/${encodeURIComponent(state.opened.sessionId)}/message`, {
      method: "POST",
      body: JSON.stringify({ parts: [{ type: "text", text }] }),
    })
  } catch (error) {
    setState("toast", { tone: "error", text: `发送失败：${errorText(error)}` })
  } finally {
    setState("sending", false)
  }
}

async function abortSession() {
  if (!state.opened || state.aborting || currentSessionStatus().type === "idle") return
  setState("aborting", true)
  try {
    await api<boolean>(`/agentcenter/session/${encodeURIComponent(state.opened.sessionId)}/abort`, { method: "POST" })
    setState("toast", { tone: "info", text: "已请求停止当前 OpenCode 运行" })
  } catch (error) {
    setState("toast", { tone: "error", text: `停止失败：${errorText(error)}` })
  } finally {
    setState("aborting", false)
  }
}

async function forkSession(messageID?: string, navigate?: ReturnType<typeof useNavigate>) {
  if (!state.opened || state.commandBusy.fork) return
  setCommandBusy("fork", true)
  setState("toast", { tone: "info", text: "正在 fork 当前受控会话..." })
  try {
    const forked = await api<OpenedSession>(`/agentcenter/session/${encodeURIComponent(state.opened.sessionId)}/fork`, {
      method: "POST",
      body: JSON.stringify(messageID ? { messageID } : {}),
    })
    clearCurrentConversation()
    navigateToNativeSession(forked, navigate)
    setState("opened", forked)
    setState("toast", { tone: "success", text: "已 fork 到新的受控会话" })
    await loadBootstrap()
    startEvents()
  } catch (error) {
    setState("toast", { tone: "error", text: `fork 失败：${errorText(error)}` })
  } finally {
    setCommandBusy("fork", false)
  }
}

async function compactSession() {
  if (!state.opened || state.commandBusy.compact || !hasUserMessages()) return
  setCommandBusy("compact", true)
  setState("toast", { tone: "info", text: "正在请求 OpenCode 压缩上下文..." })
  try {
    await api<boolean>(`/agentcenter/session/${encodeURIComponent(state.opened.sessionId)}/compact`, {
      method: "POST",
      body: JSON.stringify({}),
    })
    setState("toast", { tone: "success", text: "已请求上下文压缩" })
    await loadBootstrap()
  } catch (error) {
    setState("toast", { tone: "error", text: `压缩失败：${errorText(error)}` })
  } finally {
    setCommandBusy("compact", false)
  }
}

async function unrevertSession() {
  if (!state.opened || state.commandBusy.unrevert || !canUnrevert()) return
  setCommandBusy("unrevert", true)
  try {
    const restored = await api<Session>(`/agentcenter/session/${encodeURIComponent(state.opened.sessionId)}/unrevert`, {
      method: "POST",
    })
    setState("sessionInfo", restored.id, restored)
    setState("toast", { tone: "success", text: "已恢复回退前状态" })
    await loadBootstrap()
  } catch (error) {
    setState("toast", { tone: "error", text: `恢复失败：${errorText(error)}` })
  } finally {
    setCommandBusy("unrevert", false)
  }
}

async function revertMessage(input: { sessionID: string; messageID: string }) {
  if (!state.opened || input.sessionID !== state.opened.sessionId) return
  try {
    const reverted = await api<Session>(`/agentcenter/session/${encodeURIComponent(input.sessionID)}/revert`, {
      method: "POST",
      body: JSON.stringify({ messageID: input.messageID }),
    })
    setState("sessionInfo", reverted.id, reverted)
    setState("toast", { tone: "info", text: "已请求回退到这条消息" })
    await loadBootstrap()
  } catch (error) {
    setState("toast", { tone: "error", text: `回退失败：${errorText(error)}` })
  }
}

async function replyQuestion(requestID: string, answers: QuestionAnswer[]) {
  if (!state.opened) return
  await api<boolean>(
    `/agentcenter/session/${encodeURIComponent(state.opened.sessionId)}/question/${encodeURIComponent(requestID)}/reply`,
    { method: "POST", body: JSON.stringify({ answers }) },
  )
  removeQuestion(state.opened.sessionId, requestID)
}

async function rejectQuestion(requestID: string) {
  if (!state.opened) return
  await api<boolean>(
    `/agentcenter/session/${encodeURIComponent(state.opened.sessionId)}/question/${encodeURIComponent(requestID)}/reject`,
    { method: "POST" },
  )
  removeQuestion(state.opened.sessionId, requestID)
}

async function respondPermission(requestID: string, reply: "once" | "always" | "reject") {
  if (!state.opened || state.permissionResponding[requestID]) return
  setState("permissionResponding", requestID, true)
  try {
    await api<boolean>(
      `/agentcenter/session/${encodeURIComponent(state.opened.sessionId)}/permission/${encodeURIComponent(requestID)}/reply`,
      { method: "POST", body: JSON.stringify({ reply }) },
    )
    removePermission(state.opened.sessionId, requestID)
  } catch (error) {
    setState("toast", { tone: "error", text: `权限响应失败：${errorText(error)}` })
  } finally {
    setState("permissionResponding", requestID, false)
  }
}

function startEvents() {
  if (!state.opened) return
  eventSource?.close()
  setState("eventStatus", "连接中")
  eventSource = new EventSource(`/agentcenter/session/${encodeURIComponent(state.opened.sessionId)}/event`)
  eventSource.onopen = () => setState("eventStatus", "实时连接")
  eventSource.onerror = () => setState("eventStatus", "事件重连中")
  eventSource.onmessage = (message) => {
    try {
      handleServerEvent(JSON.parse(message.data) as ServerEvent)
    } catch {
      // Ignore malformed events during local server restarts.
    }
  }
}

function handleServerEvent(event: ServerEvent) {
  const payload = normalizeOpenCodeEvent(event)
  if (!payload) return
  if (payload.type === "global.disposed" || payload.type === "server.connected") {
    void loadBootstrap().catch((error) => {
      setState("toast", { tone: "error", text: `同步会话快照失败：${errorText(error)}` })
    })
    return
  }
  const sessionId = sessionIdFromEvent(payload)
  if (!sessionId || sessionId !== state.opened?.sessionId) return
  if (applySessionEvent(payload)) {
    scrollMessagesToBottom()
  }
}

function normalizeOpenCodeEvent(event: ServerEvent): OpenCodeEvent | undefined {
  const payload = event.payload
  if (!payload) return undefined
  if (payload.syncEvent?.type) return { type: payload.syncEvent.type, properties: payload.syncEvent.data }
  if (!payload.type) return undefined
  return { type: payload.type, properties: payload.properties }
}

function sessionIdFromEvent(event: OpenCodeEvent) {
  const properties = event.properties
  if (typeof properties?.sessionID === "string") return properties.sessionID
  if (isRecord(properties?.info) && typeof properties.info.sessionID === "string") return properties.info.sessionID
  if (isRecord(properties?.part) && typeof properties.part.sessionID === "string") return properties.part.sessionID
  if (typeof properties?.messageID === "string") {
    const message = state.messages.find((item) => item.info.id === properties.messageID)
    if (message) return message.info.sessionID
  }
  return undefined
}

function applySessionEvent(event: OpenCodeEvent) {
  switch (event.type) {
    case "session.updated":
      return applySessionUpdated(event.properties)
    case "session.deleted":
      return applySessionDeleted(event.properties)
    case "session.status":
      return applySessionStatus(event.properties)
    case "session.idle":
      return applySessionIdle(event.properties)
    case "session.diff":
      return applySessionDiff(event.properties)
    case "todo.updated":
      return applyTodoUpdated(event.properties)
    case "message.updated":
      return applyMessageUpdated(event.properties)
    case "message.removed":
      return applyMessageRemoved(event.properties)
    case "message.part.updated":
      return applyPartUpdated(event.properties)
    case "message.part.removed":
      return applyPartRemoved(event.properties)
    case "message.part.delta":
      return applyPartDelta(event.properties)
    case "question.asked":
      return applyQuestionAsked(event.properties)
    case "question.replied":
    case "question.rejected":
      return applyQuestionResolved(event.properties)
    case "permission.asked":
      return applyPermissionAsked(event.properties)
    case "permission.replied":
      return applyPermissionResolved(event.properties)
    default:
      return false
  }
}

function applySessionUpdated(properties: Record<string, unknown> | undefined) {
  const info = readSessionInfo(properties?.info)
  if (!info || info.id !== state.opened?.sessionId) return false
  setState("sessionInfo", info.id, info)
  if (state.opened) {
    setState("opened", {
      ...state.opened,
      session: {
        ...state.opened.session,
        title: info.title,
      },
      permission: info.permission ?? state.opened.permission,
    })
  }
  return true
}

function applySessionDeleted(properties: Record<string, unknown> | undefined) {
  const info = readSessionInfo(properties?.info)
  if (!info || info.id !== state.opened?.sessionId) return false
  clearCurrentConversation()
  setState("opened", undefined)
  setState("toast", { tone: "info", text: "当前 OpenCode 会话已删除" })
  return true
}

function applySessionStatus(properties: Record<string, unknown> | undefined) {
  if (typeof properties?.sessionID !== "string" || !isSessionStatus(properties.status)) return false
  setState("sessionStatus", properties.sessionID, properties.status)
  setState("toast", {
    tone: properties.status.type === "idle" ? "success" : "info",
    text: properties.status.type === "idle" ? "OpenCode 已空闲" : "OpenCode 正在思考...",
  })
  return true
}

function applySessionIdle(properties: Record<string, unknown> | undefined) {
  if (typeof properties?.sessionID !== "string") return false
  setState("sessionStatus", properties.sessionID, idleStatus)
  setState("toast", { tone: "success", text: "OpenCode 已空闲" })
  return true
}

function applySessionDiff(properties: Record<string, unknown> | undefined) {
  if (typeof properties?.sessionID !== "string" || !Array.isArray(properties.diff)) return false
  setState("sessionDiff", properties.sessionID, properties.diff.filter(isSnapshotFileDiff))
  return true
}

function applyTodoUpdated(properties: Record<string, unknown> | undefined) {
  if (typeof properties?.sessionID !== "string" || !Array.isArray(properties.todos)) return false
  if (properties.sessionID !== state.opened?.sessionId) return false
  setState("sessionTodo", properties.sessionID, properties.todos.filter(isTodo))
  return true
}

function applyMessageUpdated(properties: Record<string, unknown> | undefined) {
  const info = readMessageInfo(properties?.info)
  if (!info || info.sessionID !== state.opened?.sessionId) return false
  const pending = pendingPartsForMessage(info.id)
  setState("messages", (messages) => upsertMessage(messages, info, pending))
  clearPendingParts(info.id)
  return true
}

function applyMessageRemoved(properties: Record<string, unknown> | undefined) {
  if (typeof properties?.sessionID !== "string" || typeof properties.messageID !== "string") return false
  if (properties.sessionID !== state.opened?.sessionId) return false
  const removedPartIds = state.messages.find((message) => message.info.id === properties.messageID)?.parts.map((part) => part.id) ?? []
  setState("messages", (messages) => messages.filter((message) => message.info.id !== properties.messageID))
  clearPendingParts(properties.messageID)
  clearPartBuffers(removedPartIds)
  return true
}

function applyPartUpdated(properties: Record<string, unknown> | undefined) {
  const part = readPartInfo(properties?.part)
  if (!part || part.sessionID !== state.opened?.sessionId) return false
  if (skippedPartTypes.has(part.type)) {
    clearPartTextAccum(part.id)
    clearPendingPartDelta(part.id)
    removePendingPart(part.messageID, part.id)
    return true
  }
  const pending = state.pendingPartDelta[part.id]
  const nextPart = pending?.messageID === part.messageID ? mergePartDelta(part, pending.field, pending.value) : part
  clearPartTextAccum(part.id)
  clearPendingPartDelta(part.id)
  if (!hasMessage(part.messageID)) {
    queuePendingPart(nextPart)
    return true
  }
  removePendingPart(part.messageID, part.id)
  setState("messages", (messages) => upsertPart(messages, nextPart))
  return true
}

function applyPartRemoved(properties: Record<string, unknown> | undefined) {
  if (typeof properties?.messageID !== "string" || typeof properties.partID !== "string") return false
  clearPartBuffers([properties.partID])
  removePendingPart(properties.messageID, properties.partID)
  setState("messages", (messages) =>
    messages.map((message) =>
      message.info.id === properties.messageID
        ? { ...message, parts: message.parts.filter((part) => part.id !== properties.partID) }
        : message,
    ),
  )
  return true
}

function applyPartDelta(properties: Record<string, unknown> | undefined) {
  const messageID = properties?.messageID
  const partID = properties?.partID
  const field = properties?.field
  const delta = properties?.delta
  if (typeof messageID !== "string" || typeof partID !== "string" || typeof field !== "string" || typeof delta !== "string") return false

  let nextAccum: string | undefined
  let found = false
  setState("messages", (messages) =>
    messages.map((message) => {
      if (message.info.id !== messageID) return message
      return {
        ...message,
        parts: message.parts.map((part) => {
          if (part.id !== partID) return part
          found = true
          const current = (part as Record<string, unknown>)[field]
          nextAccum = `${typeof current === "string" ? current : ""}${delta}`
          return mergePartDelta(part, field, delta)
        }),
      }
    }),
  )
  if (found && nextAccum !== undefined) {
    setPartTextAccum(partID, nextAccum)
    return true
  }
  const pendingPart = state.pendingParts[messageID]?.[partID]
  if (pendingPart) {
    queuePendingPart(mergePartDelta(pendingPart, field, delta))
    return true
  }
  queuePendingPartDelta(partID, { messageID, field, value: delta })
  return true
}

function applyQuestionAsked(properties: Record<string, unknown> | undefined) {
  const request = readQuestionRequest(properties)
  if (!request || request.sessionID !== state.opened?.sessionId) return false
  setState("questions", request.sessionID, (questions = []) => upsertById(questions, request))
  setState("toast", { tone: "info", text: "OpenCode 需要你选择一个答案" })
  return true
}

function applyQuestionResolved(properties: Record<string, unknown> | undefined) {
  if (typeof properties?.sessionID !== "string" || typeof properties.requestID !== "string") return false
  removeQuestion(properties.sessionID, properties.requestID)
  return true
}

function applyPermissionAsked(properties: Record<string, unknown> | undefined) {
  const request = readPermissionRequest(properties)
  if (!request || request.sessionID !== state.opened?.sessionId) return false
  setState("permissions", request.sessionID, (permissions = []) => upsertById(permissions, request))
  setState("toast", { tone: "info", text: "OpenCode 需要权限确认" })
  return true
}

function applyPermissionResolved(properties: Record<string, unknown> | undefined) {
  if (typeof properties?.sessionID !== "string" || typeof properties.requestID !== "string") return false
  removePermission(properties.sessionID, properties.requestID)
  return true
}

function applyBootstrap(sessionID: string, bootstrap: SessionBootstrap) {
  setState("sessionInfo", sessionID, bootstrap.session)
  setState(
    "messages",
    bootstrap.messages
      .map((message) => ({
        info: message.info,
        parts: message.parts.filter((part) => !skippedPartTypes.has(part.type)).sort(comparePart),
      }))
      .sort((a, b) => a.info.id.localeCompare(b.info.id)),
  )
  setState("sessionStatus", sessionID, bootstrap.status)
  setState("sessionDiff", sessionID, bootstrap.diff)
  setState("sessionTodo", sessionID, bootstrap.todos)
  setState("questions", sessionID, bootstrap.questions)
  setState("permissions", sessionID, bootstrap.permissions)
  setState("partTextAccumDelta", {})
  setState("pendingPartDelta", {})
  setState("pendingParts", {})
}

async function loadNativeRenderContext(sessionID: string) {
  const [providers, agents] = await Promise.all([
    api<NativeProviderResponse>(`/agentcenter/session/${encodeURIComponent(sessionID)}/native/provider`),
    api<OpenCodeAgent[]>(`/agentcenter/session/${encodeURIComponent(sessionID)}/native/agent`),
  ])
  setState("providerContext", normalizeProviderContext(providers))
  setState("agents", agents)
}

type NativeProviderResponse = {
  all: Provider[]
  default: Record<string, string>
  connected: string[]
}

function normalizeProviderContext(input: NativeProviderResponse): NormalizedProviderListResponse {
  return {
    all: new Map(input.all.map((provider) => [provider.id, provider])),
    default: input.default,
    connected: input.connected,
  }
}

function buildProviderContext(messages: OpenCodeMessage[]): NormalizedProviderListResponse {
  if (messages.length === 0) return emptyProvider
  const all = new Map<string, Provider>()
  const defaults: Record<string, string> = {}
  const connected: string[] = []

  for (const message of messages) {
    const model = messageModelRef(message)
    if (!model) continue
    const existing = all.get(model.providerID)
    if (existing) {
      existing.models[model.modelID] ??= minimalModel(model.providerID, model.modelID)
      defaults[model.providerID] ??= model.modelID
      continue
    }
    all.set(model.providerID, {
      id: model.providerID,
      name: model.providerID,
      source: "api",
      env: [],
      options: {},
      models: { [model.modelID]: minimalModel(model.providerID, model.modelID) },
    })
    defaults[model.providerID] = model.modelID
    connected.push(model.providerID)
  }

  if (all.size === 0) return emptyProvider
  return { all, default: defaults, connected }
}

function messageModelRef(message: OpenCodeMessage) {
  if (message.role === "assistant") return { providerID: message.providerID, modelID: message.modelID }
  return message.model
}

function minimalModel(providerID: string, modelID: string): Provider["models"][string] {
  return {
    id: modelID,
    providerID,
    api: { id: modelID, url: "", npm: "" },
    name: modelID,
    family: "",
    capabilities: {
      temperature: false,
      reasoning: false,
      attachment: false,
      toolcall: false,
      input: { text: true, audio: false, image: false, video: false, pdf: false },
      output: { text: true, audio: false, image: false, video: false, pdf: false },
      interleaved: false,
    },
    cost: { input: 0, output: 0, cache: { read: 0, write: 0 } },
    limit: { context: 0, output: 0 },
    status: "active",
    options: {},
    headers: {},
    release_date: "",
    variants: {},
  }
}

function upsertMessage(messages: MessageWithParts[], info: OpenCodeMessage, parts: OpenCodePart[] = []) {
  const existing = messages.find((message) => message.info.id === info.id)
  const next = existing
    ? messages.map((message) =>
        message.info.id === info.id ? { ...message, info, parts: mergeParts(message.parts, parts) } : message,
      )
    : [...messages, { info, parts: mergeParts([], parts) }]
  return next.sort((a, b) => a.info.id.localeCompare(b.info.id))
}

function upsertById<T extends { id: string }>(items: T[], item: T) {
  const next = items.some((current) => current.id === item.id)
    ? items.map((current) => (current.id === item.id ? item : current))
    : [...items, item]
  return next.sort((a, b) => a.id.localeCompare(b.id))
}

function removeQuestion(sessionID: string, requestID: string) {
  setState("questions", sessionID, (questions = []) => questions.filter((question) => question.id !== requestID))
}

function removePermission(sessionID: string, requestID: string) {
  setState("permissions", sessionID, (permissions = []) =>
    permissions.filter((permission) => permission.id !== requestID),
  )
}

function upsertPart(messages: MessageWithParts[], part: OpenCodePart) {
  return messages.map((message) => {
    if (message.info.id !== part.messageID) return message
    return { ...message, parts: mergeParts(message.parts, [part]) }
  })
}

function mergeParts(current: OpenCodePart[], incoming: OpenCodePart[]) {
  if (incoming.length === 0) return [...current].sort(comparePart)
  const byId = new Map(current.map((part) => [part.id, part]))
  for (const part of incoming) {
    if (!skippedPartTypes.has(part.type)) byId.set(part.id, part)
  }
  return Array.from(byId.values()).sort(comparePart)
}

function comparePart(a: OpenCodePart, b: OpenCodePart) {
  return a.id.localeCompare(b.id)
}

function mergePartDelta(part: OpenCodePart, field: string, delta: string): OpenCodePart {
  const current = (part as Record<string, unknown>)[field]
  return { ...part, [field]: `${typeof current === "string" ? current : ""}${delta}` } as OpenCodePart
}

function setPartTextAccum(partID: string, value: string) {
  setState("partTextAccumDelta", { ...state.partTextAccumDelta, [partID]: value })
}

function clearPartTextAccum(partID: string) {
  const next = { ...state.partTextAccumDelta }
  delete next[partID]
  setState("partTextAccumDelta", next)
}

function queuePendingPartDelta(partID: string, delta: PendingPartDelta) {
  const existing = state.pendingPartDelta[partID]
  setState("pendingPartDelta", {
    ...state.pendingPartDelta,
    [partID]: existing && existing.messageID === delta.messageID && existing.field === delta.field
      ? { ...delta, value: existing.value + delta.value }
      : delta,
  })
}

function clearPendingPartDelta(partID: string) {
  const next = { ...state.pendingPartDelta }
  delete next[partID]
  setState("pendingPartDelta", next)
}

function hasMessage(messageID: string) {
  return state.messages.some((message) => message.info.id === messageID)
}

function pendingPartsForMessage(messageID: string) {
  return Object.values(state.pendingParts[messageID] ?? {})
}

function queuePendingPart(part: OpenCodePart) {
  const group = state.pendingParts[part.messageID] ?? {}
  setState("pendingParts", {
    ...state.pendingParts,
    [part.messageID]: {
      ...group,
      [part.id]: part,
    },
  })
}

function clearPendingParts(messageID: string) {
  if (!state.pendingParts[messageID]) return
  const next = { ...state.pendingParts }
  delete next[messageID]
  setState("pendingParts", next)
}

function removePendingPart(messageID: string, partID: string) {
  const group = state.pendingParts[messageID]
  if (!group?.[partID]) return
  const nextGroup = { ...group }
  delete nextGroup[partID]
  const next = { ...state.pendingParts }
  if (Object.keys(nextGroup).length === 0) delete next[messageID]
  else next[messageID] = nextGroup
  setState("pendingParts", next)
}

function clearPartBuffers(partIDs: string[]) {
  if (partIDs.length === 0) return
  const accum = { ...state.partTextAccumDelta }
  const pending = { ...state.pendingPartDelta }
  const pendingParts = { ...state.pendingParts }
  for (const partID of partIDs) {
    delete accum[partID]
    delete pending[partID]
    for (const [messageID, parts] of Object.entries(pendingParts)) {
      if (!parts[partID]) continue
      const nextParts = { ...parts }
      delete nextParts[partID]
      if (Object.keys(nextParts).length === 0) delete pendingParts[messageID]
      else pendingParts[messageID] = nextParts
    }
  }
  setState("partTextAccumDelta", accum)
  setState("pendingPartDelta", pending)
  setState("pendingParts", pendingParts)
}

function clearCurrentConversation() {
  setState("messages", [])
  setState("sessionStatus", {})
  setState("sessionDiff", {})
  setState("sessionTodo", {})
  setState("questions", {})
  setState("permissions", {})
  setState("permissionResponding", {})
  setState("partTextAccumDelta", {})
  setState("pendingPartDelta", {})
  setState("pendingParts", {})
}

function setCommandBusy(name: string, busy: boolean) {
  setState("commandBusy", name, busy)
}

function readMessageInfo(input: unknown): OpenCodeMessage | undefined {
  if (!isRecord(input) || typeof input.id !== "string" || typeof input.sessionID !== "string") return undefined
  if (input.role !== "user" && input.role !== "assistant") return undefined
  return input as OpenCodeMessage
}

function readPartInfo(input: unknown): OpenCodePart | undefined {
  if (!isRecord(input) || typeof input.id !== "string" || typeof input.sessionID !== "string") return undefined
  if (typeof input.messageID !== "string" || typeof input.type !== "string") return undefined
  return input as OpenCodePart
}

function readSessionInfo(input: unknown): Session | undefined {
  if (!isRecord(input) || typeof input.id !== "string" || typeof input.title !== "string") return undefined
  if (typeof input.directory !== "string" || !isRecord(input.time)) return undefined
  return input as Session
}

function readQuestionRequest(input: unknown): QuestionRequest | undefined {
  if (!isRecord(input) || typeof input.id !== "string" || typeof input.sessionID !== "string") return undefined
  if (!Array.isArray(input.questions)) return undefined
  return input as QuestionRequest
}

function readPermissionRequest(input: unknown): PermissionRequest | undefined {
  if (!isRecord(input) || typeof input.id !== "string" || typeof input.sessionID !== "string") return undefined
  if (typeof input.permission !== "string" || !Array.isArray(input.patterns)) return undefined
  return input as PermissionRequest
}

function isSessionStatus(input: unknown): input is SessionStatus {
  if (!isRecord(input)) return false
  if (input.type === "idle" || input.type === "busy") return true
  return input.type === "retry" && typeof input.attempt === "number" && typeof input.message === "string" && typeof input.next === "number"
}

function isSnapshotFileDiff(input: unknown): input is SnapshotFileDiff {
  if (!isRecord(input)) return false
  return typeof input.additions === "number" && typeof input.deletions === "number"
}

function isTodo(input: unknown): input is Todo {
  if (!isRecord(input)) return false
  return typeof input.content === "string" && typeof input.status === "string" && typeof input.priority === "string"
}

function ProjectList() {
  return (
    <Show when={state.projects.length} fallback={<div class="empty-panel">没有从中心服务拿到项目。先确认 OpenCode server 和 /agentcenter API 是否可用。</div>}>
      <For each={state.projects}>
        {(project) => (
          <section class={`project-card ${project.projectId === state.selectedProjectId ? "project-card--active" : ""}`}>
            <button
              class="project-row"
              type="button"
              onClick={() => {
                setState("selectedProjectId", project.projectId)
                setState("selectedWorkItemId", project.workItems[0]?.id ?? "")
              }}
            >
              <span>{project.name}</span>
              <strong>{project.projectId}</strong>
            </button>
            <div class="workitem-list">
              <For each={project.workItems}>
                {(item) => (
                  <button
                    class={`workitem-row ${item.id === state.selectedWorkItemId ? "workitem-row--active" : ""}`}
                    type="button"
                    onClick={() => {
                      setState("selectedProjectId", project.projectId)
                      setState("selectedWorkItemId", item.id)
                    }}
                  >
                    <span>{item.id}</span>
                    <strong>{item.title}</strong>
                    <em>{statusLabel(item.status)}</em>
                  </button>
                )}
              </For>
            </div>
          </section>
        )}
      </For>
    </Show>
  )
}

function ChatHeader() {
  const navigate = useNavigate()
  const workItem = createMemo(() => selectedWorkItem())
  return (
    <div class="chat-header">
      <div>
        <span>{selectedProject()?.name ?? "未选择项目"}</span>
        <h1>{workItem() ? `${workItem()!.id} · ${workItem()!.title}` : "选择一个工作项"}</h1>
      </div>
      <div class="header-actions">
        <button class="command-button" type="button" disabled={!state.opened || !hasMessages() || state.commandBusy.fork} onClick={() => void forkSession(undefined, navigate)}>
          <Icon name="fork" size="small" />
          <span>{state.commandBusy.fork ? "Fork 中" : "Fork"}</span>
        </button>
        <button class="command-button" type="button" disabled={!state.opened || !hasUserMessages() || state.commandBusy.compact} onClick={() => void compactSession()}>
          <Icon name="brain" size="small" />
          <span>{state.commandBusy.compact ? "压缩中" : "压缩"}</span>
        </button>
        <Show when={canUnrevert()}>
          <button class="command-button" type="button" disabled={state.commandBusy.unrevert} onClick={() => void unrevertSession()}>
            <Icon name="reset" size="small" />
            <span>{state.commandBusy.unrevert ? "恢复中" : "恢复"}</span>
          </button>
        </Show>
        <button class="primary-button" type="button" disabled={state.sending || !workItem()} onClick={() => void openSelectedWorkItem(navigate)}>
          {state.opened ? "重新打开会话" : "打开受控会话"}
        </button>
      </div>
    </div>
  )
}

function MessageList(props: { messages: OpenCodeMessage[] }) {
  const navigate = useNavigate()
  const userMessages = createMemo(() => props.messages.filter((message) => message.role === "user"))
  return (
    <>
      <Show when={state.opened} fallback={<Welcome title="先打开一个受控会话">打开后，AgentCenter 会在中心 runtime root 下创建租户 / 项目 / 工作项 / 用户目录，再把对话交给 OpenCode 原生 session 处理。</Welcome>}>
        <Show when={userMessages().length} fallback={<Welcome title="会话已准备好">在底部输入消息。消息区直接复用 OpenCode 原版 SessionTurn / message-part 组件展示。</Welcome>}>
          <div class="oc-turns" onClick={openMessageFileInPanel}>
            <For each={userMessages()}>
              {(message) => (
                <SessionTurn
                  sessionID={state.opened!.sessionId}
                  messageID={message.id}
                  messages={props.messages}
                  actions={{ fork: (input) => void forkSession(input.messageID, navigate), revert: (input) => void revertMessage(input) }}
                  active={activeTurnId() === message.id}
                  status={turnStatus(message.id)}
                  showReasoningSummaries={false}
                  classes={{ root: "ac-session-turn", content: "ac-session-turn-content", container: "ac-session-turn-container" }}
                />
              )}
            </For>
          </div>
        </Show>
      </Show>
    </>
  )
}

function openMessageFileInPanel(event: MouseEvent) {
  if (!state.opened) return
  const file = fileTargetFromMessage(event.target)
  if (!file) return

  openWorkspaceFile({
    sessionId: state.opened.sessionId,
    path: file.path,
    mode: file.mode,
  })
  setState("toast", { tone: "info", text: `已在右侧打开：${file.path}` })
}

function fileTargetFromMessage(target: EventTarget | null): { path: string; mode: "diff" | "text" } | undefined {
  if (!(target instanceof Element)) return

  const diffPath = target.closest('[data-slot="session-turn-diff-path"]')
  if (diffPath?.textContent) {
    const path = cleanMessageFilePath(diffPath.textContent)
    if (path) return { path, mode: "diff" }
  }

  const toolFile = target.closest('[data-slot="apply-patch-file-info"]')
  if (toolFile?.textContent) {
    const path = cleanMessageFilePath(toolFile.textContent)
    if (path) return { path, mode: "text" }
  }

  const attachment = target.closest('[data-slot="user-message-attachment"][title]')
  if (attachment instanceof HTMLElement) {
    const path = cleanMessageFilePath(attachment.title)
    if (path) return { path, mode: "text" }
  }

  const inlineReference = target.closest('[data-highlight="file"]')
  if (inlineReference?.textContent) {
    const path = cleanMessageFilePath(inlineReference.textContent.replace(/^@/, ""))
    if (path) return { path, mode: "text" }
  }
}

function cleanMessageFilePath(input: string) {
  return input
    .replace(/[\u202A-\u202E]/g, "")
    .replace(/\s*\n\s*/g, "")
    .trim()
}

function Welcome(props: ParentProps<{ title: string }>) {
  return (
    <div class="welcome">
      <h2>{props.title}</h2>
      <p>{props.children}</p>
    </div>
  )
}

function Composer() {
  const params = useParams()
  const navigate = useNavigate()
  const blocked = () => !!currentQuestion() || !!currentPermission()
  const routedOpened = createMemo(() => {
    const opened = state.opened
    if (!opened) return
    if (params.id !== opened.sessionId) return
    if (workspaceIdFromRoute(params.dir ?? "") !== opened.workspaceId) return
    return opened
  })
  return (
    <div class="composer-region" data-component="session-prompt-dock">
      <ConversationDocks />
      <Show when={state.opened} fallback={<div class="native-prompt-empty">{composerPlaceholder()}</div>}>
        <Show when={routedOpened()} fallback={<div class="native-prompt-empty">正在同步 OpenCode 路由...</div>}>
          {(opened) => (
            <NativePromptComposer
              opened={{
                sessionId: opened().sessionId,
                workspaceId: opened().workspaceId,
              }}
              blocked={blocked()}
              commandActions={{
                canUndo: canUndoSession,
                onUndo: undoSession,
                canRedo: canRedoSession,
                onRedo: redoSession,
                canCompact: () => hasUserMessages() && !state.commandBusy.compact,
                onCompact: compactSession,
                canFork: () => hasMessages() && !state.commandBusy.fork,
                onFork: () => forkSession(undefined, navigate),
              }}
              onSubmit={() => setState("toast", { tone: "info", text: "消息已提交给 OpenCode 原生 PromptInput" })}
              onTransportEvent={(message) => setState("toast", { tone: "info", text: message })}
            />
          )}
        </Show>
      </Show>
    </div>
  )
}

function ConversationDocks() {
  return (
    <>
      <Show when={currentQuestion()}>
        {(question) => (
          <SessionQuestionDock
            request={question()}
            onSubmit={() => setState("toast", { tone: "info", text: "正在提交 OpenCode 问题回答..." })}
            onReply={(answers) => replyQuestion(question().id, answers)}
            onReject={() => rejectQuestion(question().id)}
            onError={(error) => setState("toast", { tone: "error", text: `问题响应失败：${errorText(error)}` })}
          />
        )}
      </Show>
      <Show when={!currentQuestion() && currentPermission()}>
        {(permission) => (
          <SessionPermissionDock
            request={permission()}
            responding={!!state.permissionResponding[permission().id]}
            onDecide={(response) => void respondPermission(permission().id, response)}
          />
        )}
      </Show>
    </>
  )
}

function composerPlaceholder() {
  if (!state.opened) return "先打开受控会话"
  if (currentQuestion()) return "请先回答 OpenCode 的问题"
  if (currentPermission()) return "请先处理 OpenCode 权限请求"
  return "给 OpenCode 发一条消息..."
}

function ScopePanel() {
  return (
    <Show when={state.opened} fallback={<div class="scope-card"><span>尚未打开会话</span><p>这里会显示产品级 scope，而不是让浏览器自己选择文件系统目录。</p></div>}>
      {(opened) => (
        <>
          <ScopeCard label="Tenant" value={opened().tenantId} />
          <ScopeCard label="Project" value={opened().projectId} />
          <ScopeCard label="Work Item" value={opened().workItemId} />
          <ScopeCard label="User Workspace" value={opened().userId} note={opened().allowedRootLabel} />
          <ScopeCard label="Workspace Token" value={opened().workspaceId} />
          <ScopeCard label="Session" value={opened().sessionId} note={opened().session.title} />
          <ScopeCard label="Default Permission" value={opened().permission.map((item) => `${item.permission}:${item.action}`).join(", ")} />
          <ControlledFilePanel
            sessionID={opened().sessionId}
            diffs={currentDiffs()}
            onToast={(toast) => setState("toast", toast)}
          />
        </>
      )}
    </Show>
  )
}

function ScopeCard(props: { label: string; value: string; note?: string }) {
  return (
    <div class="scope-card">
      <span>{props.label}</span>
      <strong>{props.value}</strong>
      <Show when={props.note}>
        {(note) => <p>{note()}</p>}
      </Show>
    </div>
  )
}

function SkeletonList() {
  return <>{Array.from({ length: 5 }).map(() => <div class="skeleton" />)}</>
}

function activeTurnId() {
  const assistant = state.messages
    .map((message) => message.info)
    .findLast((message) => message.role === "assistant" && typeof message.time.completed !== "number")
  if (assistant?.role === "assistant") return assistant.parentID
  if (currentSessionStatus().type !== "idle") {
    return state.messages
      .map((message) => message.info)
      .findLast((message) => message.role === "user")?.id
  }
  return undefined
}

function turnStatus(userMessageId: string): SessionStatus {
  return activeTurnId() === userMessageId ? currentSessionStatus() : idleStatus
}

function currentSessionStatus(): SessionStatus {
  const sessionID = state.opened?.sessionId
  if (!sessionID) return idleStatus
  return state.sessionStatus[sessionID] ?? idleStatus
}

function currentQuestion() {
  const sessionID = state.opened?.sessionId
  if (!sessionID) return undefined
  return state.questions[sessionID]?.[0]
}

function currentPermission() {
  const sessionID = state.opened?.sessionId
  if (!sessionID) return undefined
  return state.permissions[sessionID]?.[0]
}

function currentDiffs() {
  const sessionID = state.opened?.sessionId
  if (!sessionID) return []
  return state.sessionDiff[sessionID] ?? []
}

function currentSessionInfo() {
  if (!state.opened) return undefined
  return state.sessionInfo[state.opened.sessionId] ?? toOpenCodeSession(state.opened)
}

function hasMessages() {
  return state.messages.length > 0
}

function hasUserMessages() {
  return state.messages.some((message) => message.info.role === "user")
}

function canUnrevert() {
  return !!currentSessionInfo()?.revert?.messageID
}

function userMessageInfos() {
  return state.messages.map((message) => message.info).filter((message) => message.role === "user")
}

function canUndoSession() {
  const revertMessageID = currentSessionInfo()?.revert?.messageID
  return userMessageInfos().some((message) => !revertMessageID || message.id < revertMessageID)
}

function canRedoSession() {
  return canUnrevert()
}

async function undoSession() {
  if (!state.opened || !canUndoSession()) return
  if (currentSessionStatus().type !== "idle") await abortSession()

  const revertMessageID = currentSessionInfo()?.revert?.messageID
  const message = userMessageInfos().findLast((item) => !revertMessageID || item.id < revertMessageID)
  if (!message) return
  await revertMessage({ sessionID: state.opened.sessionId, messageID: message.id })
}

async function redoSession() {
  if (!state.opened || !canRedoSession()) return
  const revertMessageID = currentSessionInfo()?.revert?.messageID
  if (!revertMessageID) return

  const next = userMessageInfos().find((message) => message.id > revertMessageID)
  if (!next) {
    await unrevertSession()
    return
  }
  await revertMessage({ sessionID: state.opened.sessionId, messageID: next.id })
}

function toOpenCodeSession(opened: OpenedSession): Session {
  const now = Date.now()
  return {
    id: opened.sessionId,
    slug: opened.sessionId,
    projectID: opened.projectId,
    directory: opened.workspaceId,
    title: opened.session.title,
    version: "agentcenter",
    time: { created: now, updated: now },
    permission: opened.permission,
  }
}

function statusLabel(status: WorkItemStatus) {
  const labels: Record<WorkItemStatus, string> = { open: "待处理", running: "运行中", blocked: "阻塞", done: "完成", archived: "归档" }
  return labels[status]
}

function sessionStatusLabel(status: SessionStatus) {
  if (status.type === "busy") return "thinking"
  if (status.type === "retry") return `busy · retry ${status.attempt}`
  return "idle"
}

function isRecord(input: unknown): input is Record<string, unknown> {
  return typeof input === "object" && input !== null
}

function resolveTemplate(text: string, params?: UiI18nParams) {
  if (!params) return text
  return text.replace(/{{\s*([^}]+?)\s*}}/g, (_, rawKey) => {
    const key = String(rawKey)
    const value = params[key]
    return value === undefined ? "" : String(value)
  })
}

function errorText(error: unknown) {
  if (error instanceof Error) return error.message
  if (typeof error === "string") return error
  if (isRecord(error)) {
    const data = error.data
    if (isRecord(data) && typeof data.message === "string") return data.message
    if (typeof error.message === "string") return error.message
    return JSON.stringify(error)
  }
  return String(error)
}

function workspaceRouteToken(workspaceId: string) {
  return `w.${workspaceId}`
}

function workspaceIdFromRoute(routeToken: string) {
  return routeToken.startsWith("w.") ? routeToken.slice(2) : routeToken
}

function scrollMessagesToBottom() {
  requestAnimationFrame(() => {
    const scroller = document.querySelector<HTMLElement>("#message-scroll")
    if (scroller) scroller.scrollTop = scroller.scrollHeight
  })
}
