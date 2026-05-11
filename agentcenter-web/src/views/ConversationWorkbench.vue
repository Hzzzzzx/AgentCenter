<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useSessionStore } from '../stores/sessions'
import { useWorkflowStore } from '../stores/workflows'
import { useRuntimeStore } from '../stores/runtime'
import { useWorkItemStore } from '../stores/workItems'
import { useConfirmationStore } from '../stores/confirmations'
import { useRuntimeSettingsStore } from '../stores/runtimeSettings'
import { useWorkItemWorkflowProjectionStore } from '../stores/workItemWorkflowProjection'
import { useNotificationStore } from '../stores/notifications'
import MessageList from '../components/conversation/MessageList.vue'
import WorkflowNodeControlBar from '../components/conversation/WorkflowNodeControlBar.vue'
import { runtimeResourceApi } from '../api/runtimeResources'
import { artifactApi } from '../api/artifacts'
import type {
  AgentMessageDto,
  AgentSessionDto,
  AgentStateStatus,
  ArtifactDto,
  ConfirmationActionType,
  ConfirmationRequestType,
  RuntimeEventDto,
  WorkflowNodeInstanceDto,
  WorkflowNodeStatus,
} from '../api/types'

interface NodeStateInfo {
  type: 'RUNNING' | 'READY' | 'WAITING_CONFIRMATION' | 'FAILED' | 'COMPLETED' | 'WORKFLOW_COMPLETED' | null
  nodeId?: string
  nodeName?: string
  skillName?: string
  errorMessage?: string
  confirmationType?: string
  artifactId?: string
  artifactTitle?: string
}

interface PromptDebugPayload {
  kind?: string
  title?: string
  summary?: string
  agent?: string
  runtimeSessionId?: string
  baseUrl?: string
  workingDirectory?: string
  systemPrompt?: string
  userPrompt?: string
  requestPayload?: unknown
  opencodePromptAsyncBody?: unknown
}

interface PromptDebugItem {
  event: RuntimeEventDto
  payload: PromptDebugPayload
}

interface PromptDebugTimelineItem {
  id: string
  createdAt: string
  kind: 'assistant-message' | 'runtime-event' | 'streaming' | 'prompt-input'
  side: 'agent' | 'input'
  title: string
  badge: string
  note: string
  uiDisplay: string
  preview: string
  content: string
  copyText: string
}

interface ArtifactOpenRef {
  artifactId?: string
  title?: string
}

const PROMPT_DEBUG_ENABLED = true
const PROMPT_DEBUG_EDGE_GAP = 16
const PROMPT_DEBUG_RUNTIME_EVENT_LIMIT = 24
const CONTINUE_CURRENT_MESSAGE = '请继续当前节点未完成的回答，不要重新开始节点，也不要重复发送或复述工作流节点提示词。'

const props = defineProps<{
  workItemId?: string
  targetSessionId?: string | null
}>()

const emit = defineEmits<{
  back: []
  'open-artifact': [artifact: ArtifactDto]
  'show-confirmation': [confirmationId: string]
}>()

const sessionStore = useSessionStore()
const workflowStore = useWorkflowStore()
const runtimeStore = useRuntimeStore()
const runtimeSettingsStore = useRuntimeSettingsStore()
const workItemStore = useWorkItemStore()
const confirmationStore = useConfirmationStore()
const notificationStore = useNotificationStore()
const workflowProjectionStore = useWorkItemWorkflowProjectionStore()

const inputText = ref('')
const inputRef = ref<HTMLInputElement | null>(null)
const messagesRef = ref<HTMLElement | null>(null)
const skillRefreshStatus = ref('')
const refreshingSkills = ref(false)
const loadingSession = ref(false)
const cancellingSessionId = ref<string | null>(null)
const pausedRunningNodeId = ref<string | null>(null)
const promptDebugOpen = ref(false)
const promptDebugFullscreen = ref(false)
const promptDebugPosition = ref({ x: 0, y: 0 })
const promptDebugHasCustomPosition = ref(false)
const promptDebugDragging = ref(false)
const promptDebugDragOffset = ref({ x: 0, y: 0 })
const promptDebugSize = ref({ width: 0, height: 0 })
const promptDebugCopiedId = ref<string | null>(null)
let ensureActiveSessionSeq = 0

const currentWorkflowNodeInstanceId = computed(() => {
  return nodeStateInfo.value.nodeId ?? null
})

const activeWorkflowNode = computed<WorkflowNodeInstanceDto | null>(() => {
  const instance = currentWorkflowInstance.value
  if (!instance) return null
  const activeStatuses: WorkflowNodeStatus[] = ['RUNNING', 'READY', 'WAITING_CONFIRMATION', 'FAILED']
  const activeNode =
    activeStatuses
      .flatMap((status) => instance.nodes.filter((n) => n.status === status))
      .sort((a, b) => (a.sequenceNo ?? 0) - (b.sequenceNo ?? 0))[0] ??
    [...instance.nodes]
      .filter((n) => n.status === 'COMPLETED')
      .sort((a, b) => (b.sequenceNo ?? 0) - (a.sequenceNo ?? 0))[0]
  return activeNode ?? null
})

const workflowNodeState = computed<AgentStateStatus | null>(() => {
  return (runtimeStore.lastNodeState as AgentStateStatus)
    ?? (activeWorkflowNode.value?.agentState as AgentStateStatus)
    ?? null
})

const workflowNodeStateReason = computed<string | null>(() => {
  return runtimeStore.lastNodeStateReason
    ?? activeWorkflowNode.value?.agentStateReason
    ?? null
})

const inputPlaceholder = computed(() => {
  if (isConversationRunning.value) return '对话运行中，可点击右侧按钮暂停...'
  if (composerRecoveryInteraction.value?.requestType === 'EXCEPTION') return '补充异常处理信息后继续当前节点...'
  if (composerRecoveryInteraction.value?.requestType === 'INPUT_REQUIRED') return '补充输入后返回给 Agent...'
  if (isWorkflowCompleted.value) return '流程已完成，可查看产物或输入新的补充指令...'
  const state = workflowNodeState.value
  if (state === 'NEEDS_USER_INPUT') return '补充输入后返回给 Agent...'
  if (state === 'IN_PROGRESS') return '输入补充信息或指令...'
  if (state === 'READY_TO_ADVANCE') return '节点已完成，点击"进入下一步"继续...'
  if (state === 'BLOCKED') return '节点已阻塞，处理异常或跳过...'
  return '输入指令或问题...'
})

const selectedWorkItem = computed(() => {
  if (!props.workItemId) return null
  return workItemStore.items.find((item) => item.id === props.workItemId) ?? null
})

const activeTitle = computed(() => {
  if (selectedWorkItem.value) {
    return `${selectedWorkItem.value.code} · ${selectedWorkItem.value.title}`
  }
  return sessionStore.activeSession?.title || '对话工作台 · AI 智能中枢'
})

const currentWorkflowInstance = computed(() => {
  const activeInstance = workflowStore.activeWorkflowInstance
  const sessionWorkflowInstanceId = sessionStore.activeSession?.workflowInstanceId ?? null
  if (!props.workItemId && !sessionWorkflowInstanceId) return null

  const workItemInstance = props.workItemId
    ? workflowStore.instancesByWorkItemId[props.workItemId] ?? null
    : null
  const instance = sessionWorkflowInstanceId && activeInstance?.id === sessionWorkflowInstanceId
    ? activeInstance
    : props.workItemId
      ? workItemInstance ?? activeInstance
      : activeInstance

  if (!instance) return null
  if (props.workItemId && instance.workItemId !== props.workItemId) return null
  if (sessionStore.activeSession?.workflowInstanceId && instance.id !== sessionStore.activeSession.workflowInstanceId) return null
  return instance
})

const isWorkflowCompleted = computed(() =>
  currentWorkflowInstance.value?.status === 'COMPLETED'
)

const nodeStateInfo = computed<NodeStateInfo>(() => {
  const instance = currentWorkflowInstance.value
  if (!instance) return { type: null }

  // Workflow completed
  if (instance.status === 'COMPLETED') {
    return { type: 'WORKFLOW_COMPLETED' }
  }

  // Find the active node: prefer RUNNING > READY > WAITING_CONFIRMATION > FAILED > latest COMPLETED
  const activeStatuses: WorkflowNodeStatus[] = ['RUNNING', 'READY', 'WAITING_CONFIRMATION', 'FAILED']
  const activeNode =
    activeStatuses
      .flatMap((status) => instance.nodes.filter((n) => n.status === status))
      .sort((a, b) => (a.sequenceNo ?? 0) - (b.sequenceNo ?? 0))[0] ??
    [...instance.nodes]
      .filter((n) => n.status === 'COMPLETED')
      .sort((a, b) => (b.sequenceNo ?? 0) - (a.sequenceNo ?? 0))[0]

  if (!activeNode) return { type: null }

  const label = workflowNodeLabel(activeNode)

  switch (activeNode.status) {
    case 'RUNNING':
      return {
        type: 'RUNNING',
        nodeId: activeNode.id,
        nodeName: label,
        skillName: activeNode.skillName ?? undefined,
      }
    case 'READY':
      return {
        type: 'READY',
        nodeId: activeNode.id,
        nodeName: label,
        skillName: activeNode.skillName ?? undefined,
      }
    case 'WAITING_CONFIRMATION':
      return {
        type: 'WAITING_CONFIRMATION',
        nodeId: activeNode.id,
        nodeName: label,
        skillName: activeNode.skillName ?? undefined,
        confirmationType: activeNode.reason ?? '确认',
      }
    case 'FAILED':
      return {
        type: 'FAILED',
        nodeId: activeNode.id,
        nodeName: label,
        skillName: activeNode.skillName ?? undefined,
        errorMessage: activeNode.errorMessage?.trim() || '工作流节点执行失败，暂无详细错误原因。',
      }
    case 'COMPLETED':
      return {
        type: 'COMPLETED',
        nodeId: activeNode.id,
        nodeName: label,
        skillName: activeNode.skillName ?? undefined,
        artifactId: activeNode.outputArtifactId ?? undefined,
      }
    default:
      return { type: null }
  }
})

const isPausedCurrentNode = computed(() =>
  nodeStateInfo.value.type === 'RUNNING'
  && Boolean(nodeStateInfo.value.nodeId)
  && nodeStateInfo.value.nodeId === pausedRunningNodeId.value
)

const isCancellingCurrentReply = computed(() =>
  Boolean(
    sessionStore.activeSession?.id
    && cancellingSessionId.value === sessionStore.activeSession.id
  )
)

const isActiveWorkflowNodeOwnedByCurrentSession = computed(() =>
  Boolean(
    sessionStore.activeSession?.id
    && activeWorkflowNode.value?.agentSessionId
    && activeWorkflowNode.value.agentSessionId === sessionStore.activeSession.id
  )
)

const hasActiveSessionRuntimeOutput = computed(() =>
  runtimeStore.activeSessionId === sessionStore.activeSession?.id
  && (
    runtimeStore.busy
    || Boolean(runtimeStore.streamingText.trim())
  )
)

const currentRuntimeEvents = computed(() => {
  const sessionId = sessionStore.activeSession?.id
  if (!sessionId) return []
  return runtimeStore.events.filter((event) => event.sessionId === sessionId)
})

const currentStreamingText = computed(() =>
  runtimeStore.activeSessionId === sessionStore.activeSession?.id
    ? runtimeStore.streamingText
    : ''
)

const isConversationRunning = computed(() =>
  !composerRecoveryInteraction.value
  && nodeStateInfo.value.type !== 'FAILED'
  && (
    hasActiveSessionRuntimeOutput.value
    || (
      nodeStateInfo.value.type === 'RUNNING'
      && isActiveWorkflowNodeOwnedByCurrentSession.value
      && !isPausedCurrentNode.value
    )
  )
)

function userFacingAgentReason(reason: string | null): string {
  if (!reason) return ''
  if (reason.includes('multiple interactions')) return '已完成多轮交互并生成详细设计'
  if (reason.includes('one decision')) return '已根据用户选择生成方案设计'
  if (reason.includes('without user interaction')) return '已根据工作项信息生成需求产物'
  if (reason.includes('implementation constraint')) return '等待补充实现约束'
  if (reason.includes('implementation route')) return '等待选择实现路线'
  if (reason.includes('draft review')) return '等待审阅设计草稿'
  if (reason.includes('HLD path decision')) return '等待选择 HLD 方案'
  return reason
}

const agentStateLabel = computed<{ dot: string; text: string; reason: string } | null>(() => {
  if (isWorkflowCompleted.value) {
    const completedNodes = currentWorkflowInstance.value?.nodes.filter(node => node.status === 'COMPLETED').length ?? 0
    return {
      dot: '✓',
      text: '流程已完成',
      reason: completedNodes > 0 ? `已完成 ${completedNodes} 个节点并生成产物` : '',
    }
  }
  const state = workflowNodeState.value
  if (!state) return null
  const map: Record<AgentStateStatus, { dot: string; text: string }> = {
    IN_PROGRESS: { dot: '🟡', text: '执行中' },
    NEEDS_USER_INPUT: { dot: '🟠', text: '等待用户输入' },
    READY_TO_ADVANCE: { dot: '🟢', text: '就绪推进' },
    BLOCKED: { dot: '🔴', text: '已阻塞' },
  }
  const entry = map[state]
  if (!entry) return null
  return { ...entry, reason: userFacingAgentReason(workflowNodeStateReason.value) }
})

const currentInteractions = computed(() => {
  const session = sessionStore.activeSession
  const workflowInstanceId = session?.workflowInstanceId ?? currentWorkflowInstance.value?.id ?? null
  const nodeId = nodeStateInfo.value.nodeId ?? null

  return confirmationStore.pendingConfirmations.filter((item) => {
    if (item.status !== 'PENDING' && item.status !== 'IN_CONVERSATION') return false
    if (!session?.id) return false
    if (item.agentSessionId) return item.agentSessionId === session.id

    return Boolean(
      (workflowInstanceId && item.workflowInstanceId === workflowInstanceId)
      || (nodeId && item.workflowNodeInstanceId === nodeId)
    )
  })
})

function canSubmitInteractionFromComposer(item: { requestType: ConfirmationRequestType }): boolean {
  return item.requestType === 'EXCEPTION' || item.requestType === 'INPUT_REQUIRED'
}

const composerRecoveryInteraction = computed(() =>
  currentInteractions.value.find(canSubmitInteractionFromComposer) ?? null
)

const shouldShowWorkflowNodeControl = computed(() =>
  !isWorkflowCompleted.value
  && nodeStateInfo.value.type !== 'WAITING_CONFIRMATION'
  && workflowNodeState.value !== 'READY_TO_ADVANCE'
  && !isConversationRunning.value
  && currentInteractions.value.length === 0
)

const promptDebugItems = computed<PromptDebugItem[]>(() =>
  currentRuntimeEvents.value
    .filter((event) => event.eventType === 'PROCESS_TRACE')
    .map((event) => ({ event, payload: parsePromptDebugPayload(event.payloadJson) }))
    .filter((item) => item.payload.kind === 'prompt_debug')
    .sort((a, b) => timestamp(b.event.createdAt) - timestamp(a.event.createdAt))
)

const latestPromptDebug = computed(() => promptDebugItems.value[0] ?? null)
const promptDebugAvailable = computed(() =>
  PROMPT_DEBUG_ENABLED
  && runtimeSettingsStore.promptDebugPanelEnabled
  && latestPromptDebug.value !== null
)
const promptDebugFloatingStyle = computed(() => {
  if (promptDebugFullscreen.value) return {}
  if (promptDebugHasCustomPosition.value) {
    return { transform: `translate(${promptDebugPosition.value.x}px, ${promptDebugPosition.value.y}px)` }
  }
  return {}
})

const promptDebugRequestJson = computed(() =>
  latestPromptDebug.value?.payload.requestPayload !== undefined
    ? formatDebugValue(latestPromptDebug.value.payload.requestPayload)
    : ''
)

const promptDebugHttpBodyJson = computed(() =>
  latestPromptDebug.value?.payload.opencodePromptAsyncBody !== undefined
    ? formatDebugValue(latestPromptDebug.value.payload.opencodePromptAsyncBody)
    : ''
)

const promptDebugTimelineItems = computed<PromptDebugTimelineItem[]>(() => {
  const roundStart = timestamp(latestPromptDebug.value?.event.createdAt)
  const assistantMessages = sessionStore.messages
    .filter((message) => message.role.toUpperCase() === 'ASSISTANT')
    .filter((message) => timestamp(message.createdAt) >= roundStart)
    .map(messageToPromptDebugItem)

  const runtimeEvents = currentRuntimeEvents.value
    .filter((event) => timestamp(event.createdAt) >= roundStart)
    .filter((event) => {
      const payload = parseRuntimePayload(event.payloadJson)
      return isPromptDebugTimelineEvent(event, payload)
    })
    .sort((a, b) => timestamp(a.createdAt) - timestamp(b.createdAt))
    .slice(-PROMPT_DEBUG_RUNTIME_EVENT_LIMIT)
    .map(eventToPromptDebugItem)

  const streamingText = currentStreamingText.value.trim()
  const streamingItem: PromptDebugTimelineItem[] = streamingText
    ? [{
        id: 'streaming-assistant',
        createdAt: new Date().toISOString(),
        kind: 'streaming',
        side: 'agent',
        title: '正在流式回复',
        badge: 'ASSISTANT_DELTA',
        note: 'Agent 正在增量输出，还没有落成完整消息。',
        uiDisplay: '显示为对话区最底部的实时流式回复。',
        preview: summarizeDebugContent(streamingText),
        content: streamingText,
        copyText: buildPromptDebugCopyText({
          title: '正在流式回复',
          badge: 'ASSISTANT_DELTA',
          note: 'Agent 正在增量输出，还没有落成完整消息。',
          uiDisplay: '显示为对话区最底部的实时流式回复。',
          content: streamingText,
        }),
      }]
    : []

  return [...promptDebugInputItems.value, ...assistantMessages, ...runtimeEvents, ...streamingItem]
    .sort((a, b) => {
      const timeDiff = timestamp(a.createdAt) - timestamp(b.createdAt)
      if (timeDiff !== 0) return timeDiff
      return a.id.localeCompare(b.id)
    })
})

const promptDebugInputItems = computed<PromptDebugTimelineItem[]>(() => {
  const item = latestPromptDebug.value
  if (!item) return []
  const createdAt = item.event.createdAt
  const payload = item.payload
  const rows: PromptDebugTimelineItem[] = []

  rows.push(makePromptDebugInputItem({
    id: 'prompt-input:01-system',
    createdAt,
    title: '系统提示词',
    badge: 'SYSTEM_PROMPT',
    note: '本轮 prompt_async 请求里的系统侧上下文，用来约束 Runtime 工作目录、agent 和加载方式。',
    uiDisplay: '只显示在调试看板右侧时间线，不进入对话正文。',
    content: payload.systemPrompt || '无显式 system prompt',
  }))

  rows.push(makePromptDebugInputItem({
    id: 'prompt-input:02-user',
    createdAt,
    title: '用户输入 / Parts Text',
    badge: 'USER_PARTS',
    note: '本轮发送给 Agent 的用户侧文本，通常包含工作流节点输入、用户补充和 Skill 调用入口。',
    uiDisplay: '对应本轮运行请求的输入点，默认折叠为摘要。',
    content: payload.userPrompt || '无',
  }))

  if (promptDebugHttpBodyJson.value) {
    rows.push(makePromptDebugInputItem({
      id: 'prompt-input:03-http-body',
      createdAt,
      title: 'OpenCode prompt_async body',
      badge: 'PROMPT_ASYNC',
      note: 'Bridge 实际提交给 OpenCode Runtime 的 HTTP body。',
      uiDisplay: '用于核对 agent、parts 和 runtime 请求结构。',
      content: promptDebugHttpBodyJson.value,
    }))
  }

  if (promptDebugRequestJson.value) {
    rows.push(makePromptDebugInputItem({
      id: 'prompt-input:04-transport',
      createdAt,
      title: 'AgentCenter transport payload',
      badge: 'TRANSPORT',
      note: 'AgentCenter 内部传输载荷，包含 baseUrl、workingDirectory 等桥接上下文。',
      uiDisplay: '用于排查 Bridge 到 Runtime 的参数传递。',
      content: promptDebugRequestJson.value,
    }))
  }

  return rows
})

onMounted(async () => {
  if (workflowStore.definitions.length === 0) {
    await workflowStore.loadDefinitions()
  }
  await ensureActiveSession()
  await confirmationStore.loadPending()
})

watch([() => props.workItemId, () => props.targetSessionId], async () => {
  await ensureActiveSession()
  await confirmationStore.loadPending()
})

watch(
  () => [sessionStore.activeSession?.id ?? null, nodeStateInfo.value.nodeId ?? null, nodeStateInfo.value.type] as const,
  ([sessionId, nodeId, nodeType]) => {
    if (!sessionId || nodeType !== 'RUNNING' || nodeId !== pausedRunningNodeId.value) {
      pausedRunningNodeId.value = null
    }
  }
)

async function ensureActiveSession(): Promise<AgentSessionDto | null> {
  const seq = ++ensureActiveSessionSeq
  const requestedWorkItemId = props.workItemId
  const requestedTargetSessionId = props.targetSessionId ?? null
  const isCurrentRequest = () =>
    seq === ensureActiveSessionSeq
    && props.workItemId === requestedWorkItemId
    && (props.targetSessionId ?? null) === requestedTargetSessionId

  loadingSession.value = true
  try {
    if (workItemStore.items.length === 0) {
      await workItemStore.loadItems()
      if (!isCurrentRequest()) return null
    }

    let selectedItem = requestedWorkItemId
      ? workItemStore.items.find((item) => item.id === requestedWorkItemId) ?? null
      : null

    if (requestedWorkItemId) {
      await workflowProjectionStore.syncWorkItem(requestedWorkItemId)
      if (!isCurrentRequest()) return null
      selectedItem = workItemStore.items.find((item) => item.id === requestedWorkItemId) ?? selectedItem
      const workflowInstanceId = selectedItem?.currentWorkflowInstanceId
      if (workflowInstanceId) {
        await workflowStore.loadInstance(workflowInstanceId)
        if (!isCurrentRequest()) return null
      }
    }
    await sessionStore.loadSessions()
    if (!isCurrentRequest()) return null

    let session: AgentSessionDto | undefined

    if (requestedTargetSessionId) {
      session = sessionStore.sessions.find((item) => item.id === requestedTargetSessionId)
    }

    if (!session) {
      const workflowSessionId = activeWorkflowNode.value?.agentSessionId
      if (workflowSessionId) {
        session = sessionStore.sessions.find((item) => item.id === workflowSessionId)
        if (!session) {
          await sessionStore.selectSession(workflowSessionId)
          if (!isCurrentRequest()) return null
          if (sessionStore.activeSession?.id !== workflowSessionId) return null
          session = sessionStore.activeSession ?? undefined
        }
      }
    }

    if (!session && requestedWorkItemId) {
      const workflowInstanceId = currentWorkflowInstance.value?.id ?? selectedItem?.currentWorkflowInstanceId
      if (workflowInstanceId) {
        session = sessionStore.sessions.find(
          (item) => item.workflowInstanceId === workflowInstanceId && item.status === 'ACTIVE'
        )
      }
    }

    if (!session && requestedWorkItemId && !currentWorkflowInstance.value && !selectedItem?.currentWorkflowInstanceId) {
      session = sessionStore.sessions.find(
        (item) => item.workItemId === requestedWorkItemId && item.status === 'ACTIVE'
      )
    }

    if (!session && requestedWorkItemId && !currentWorkflowInstance.value && !selectedItem?.currentWorkflowInstanceId) {
      session = await sessionStore.createSession({
        sessionType: 'WORK_ITEM',
        title: selectedItem ? `${selectedItem.code} · ${selectedItem.title}` : '任务会话',
        workItemId: requestedWorkItemId,
        runtimeType: 'OPENCODE',
      })
      if (!isCurrentRequest()) return null
    }

    if (!session) {
      return null
    }

    await sessionStore.selectSession(session.id)
    if (!isCurrentRequest() || sessionStore.activeSession?.id !== session.id) return null
    runtimeStore.connectSSE(session.id)

    if (session.workflowInstanceId) {
      await workflowStore.loadInstance(session.workflowInstanceId)
      if (!isCurrentRequest()) return null
    }
    if (requestedWorkItemId) {
      await workflowProjectionStore.syncWorkItem(requestedWorkItemId)
      if (!isCurrentRequest()) return null
    }

    await nextTick()
    if (!isCurrentRequest()) return null
    inputRef.value?.focus()
    return session
  } finally {
    if (seq === ensureActiveSessionSeq) {
      loadingSession.value = false
    }
  }
}

onUnmounted(() => {
  stopPromptDebugDrag()
  runtimeStore.disconnectSSE()
})

function scrollToBottom() {
  nextTick(() => {
    const el = messagesRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

watch(() => sessionStore.messages.length, scrollToBottom)
watch(() => currentStreamingText.value, scrollToBottom)
watch(() => currentRuntimeEvents.value.length, scrollToBottom)
watch(
  () => runtimeSettingsStore.promptDebugPanelEnabled,
  (enabled) => {
    if (enabled) return
    promptDebugOpen.value = false
    promptDebugFullscreen.value = false
    stopPromptDebugDrag()
  }
)

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || isConversationRunning.value) return
  const recoveryInteraction = composerRecoveryInteraction.value
  if (recoveryInteraction) {
    await resolveComposerRecoveryInteraction(recoveryInteraction.id, text)
    return
  }

  const blockingInteraction = currentInteractions.value[0]
  if (blockingInteraction) {
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'warning',
      title: '请先处理当前交互',
      message: blockingInteraction.requestType === 'PERMISSION'
        ? 'Runtime 正在等待权限授权，发送普通消息不会解除权限等待。'
        : '当前节点正在等待你的确认或补充，请先处理交互卡片。',
      durationMs: 5200,
    })
    return
  }

  if (isPausedCurrentNode.value && currentWorkflowNodeInstanceId.value) {
    await sendContinueCurrentMessage(currentWorkflowNodeInstanceId.value, text)
    return
  }

  const session = await ensureActiveSession()
  if (!session) return

  runtimeStore.markBusy()
  await sessionStore.sendMessage(text)
  inputText.value = ''
}

async function resolveComposerRecoveryInteraction(confirmationId: string, text: string) {
  runtimeStore.markBusy()
  try {
    await confirmationStore.resolveConfirmation(confirmationId, {
      actionType: 'SUPPLEMENT',
      comment: text,
      payload: { input: text },
    }, { remove: true })
    inputText.value = ''
    await handleInteractionChanged(confirmationId)
  } catch (error) {
    runtimeStore.markIdle()
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'error',
      title: '补充提交失败',
      message: error instanceof Error ? error.message : '异常补充提交失败，请稍后重试。',
      durationMs: 5200,
    })
  }
}

async function handleCancelReply() {
  if (!sessionStore.activeSession || isCancellingCurrentReply.value) return
  const sessionId = sessionStore.activeSession.id
  const runningNodeId = nodeStateInfo.value.type === 'RUNNING'
    ? nodeStateInfo.value.nodeId ?? null
    : null
  cancellingSessionId.value = sessionId
  try {
    await sessionStore.cancelActiveSession()
    pausedRunningNodeId.value = runningNodeId
    runtimeStore.resetStreamingOutput()
    runtimeStore.markIdle()
    await sessionStore.selectSession(sessionId)
    if (sessionStore.activeSession.workflowInstanceId) {
      await workflowStore.refreshInstance(sessionStore.activeSession.workflowInstanceId)
    }
    if (sessionStore.activeSession.workItemId) {
      await workflowProjectionStore.syncWorkItem(sessionStore.activeSession.workItemId)
    }
  } finally {
    cancellingSessionId.value = null
  }
}

async function handleWorkflowAction(action: string, nodeInstanceId: string) {
  if (!sessionStore.activeSession) return
  if (action === 'CONTINUE_CURRENT') {
    await sendContinueCurrentMessage(nodeInstanceId, CONTINUE_CURRENT_MESSAGE)
    return
  }
  runtimeStore.markBusy()
  await sessionStore.sendMessage({
    content: `[${action}]`,
    workflowUserAction: action,
    workflowNodeInstanceId: nodeInstanceId,
  })
}

async function sendContinueCurrentMessage(nodeInstanceId: string, content: string) {
  runtimeStore.markBusy()
  await sessionStore.sendMessage({
    content,
    workflowUserAction: 'CONTINUE_CURRENT',
    workflowNodeInstanceId: nodeInstanceId,
  })
  inputText.value = ''
  pausedRunningNodeId.value = null
}

async function handleInteractionChanged(_confirmationId?: string) {
  await confirmationStore.loadPending()
  if (sessionStore.activeSession?.workflowInstanceId) {
    await workflowStore.refreshInstance(sessionStore.activeSession.workflowInstanceId)
  }
  const workItemId = props.workItemId ?? sessionStore.activeSession?.workItemId
  if (workItemId) {
    await workflowProjectionStore.syncWorkItem(workItemId)
  }
}

async function handleResolveConfirmation(confirmationId: string, value: string, meta?: { requestType?: string; interactionType?: string }) {
  const actionType = actionTypeForInteraction(meta?.requestType, value, meta?.interactionType)
  try {
    await confirmationStore.resolveConfirmation(confirmationId, {
      actionType,
      payload: { choice: value },
      comment: value,
    })
    await handleInteractionChanged()
  } catch (error) {
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'error',
      title: '交互提交失败',
      message: error instanceof Error ? error.message : '权限或确认提交失败，请稍后重试。',
      durationMs: 5200,
    })
  }
}

function actionTypeForInteraction(
  requestType: string | undefined,
  value: string,
  interactionType?: string,
): ConfirmationActionType {
  const normalized = normalizeConfirmationAction(value)
  const typedRequest = requestType as ConfirmationRequestType | undefined

  if (typedRequest === 'PERMISSION' || typedRequest === 'APPROVAL' || typedRequest === 'CONFIRM') {
    return normalized === 'REJECT' ? 'REJECT' : 'APPROVE'
  }
  if (typedRequest === 'EXCEPTION') {
    return normalized === 'SKIP' || normalized === 'REJECT' || normalized === 'SUPPLEMENT'
      ? normalized
      : 'RETRY'
  }
  if (typedRequest === 'INPUT_REQUIRED') {
    return normalized === 'REJECT' ? 'REJECT' : 'SUPPLEMENT'
  }
  if (typedRequest === 'DECISION') {
    if (interactionType === 'WORKFLOW_ADVANCE') {
      return normalized ?? 'CHOOSE'
    }
    return normalized === 'REJECT' ? 'REJECT' : 'CHOOSE'
  }

  return normalized ?? 'CHOOSE'
}

function normalizeConfirmationAction(value: string): ConfirmationActionType | undefined {
  const normalized = value.trim().toUpperCase()
  const validActions: ConfirmationActionType[] = ['ENTER_SESSION', 'APPROVE', 'REJECT', 'SUPPLEMENT', 'CHOOSE', 'RETRY', 'SKIP', 'ADVANCE']
  return validActions.includes(normalized as ConfirmationActionType)
    ? normalized as ConfirmationActionType
    : undefined
}

async function refreshSkills() {
  refreshingSkills.value = true
  skillRefreshStatus.value = ''
  try {
    const result = await runtimeResourceApi.refreshSkills()
    skillRefreshStatus.value = `已刷新 ${result.skillCount} 个 Skill`
  } catch (error) {
    skillRefreshStatus.value = error instanceof Error ? error.message : '刷新 Skill 失败'
  } finally {
    refreshingSkills.value = false
  }
}

async function handleOpenArtifact(ref: ArtifactOpenRef | string) {
  const target = typeof ref === 'string' ? { title: ref } : ref
  if (target.artifactId) {
    try {
      emit('open-artifact', await artifactApi.get(target.artifactId))
      return
    } catch (error) {
      console.debug('Failed to open artifact by id, falling back to title lookup', error)
    }
  }

  const workItem = selectedWorkItem.value
  if (!workItem || !target.title) return
  const artifacts = await artifactApi.listByWorkItem(workItem.id)
  const artifact = artifacts.find((item) => item.title === target.title)
  if (artifact) {
    emit('open-artifact', artifact)
  }
}

function workflowNodeLabel(node: WorkflowNodeInstanceDto): string {
  const definition = workflowStore.definitions
    .flatMap((item) => item.nodes)
    .find((item) => item.id === node.nodeDefinitionId)
  return definition?.name ?? node.skillName ?? '工作流节点'
}

function parsePromptDebugPayload(payloadJson: string | null): PromptDebugPayload {
  if (!payloadJson) return {}
  try {
    const parsed: unknown = JSON.parse(payloadJson)
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)
      ? parsed as PromptDebugPayload
      : {}
  } catch {
    return {}
  }
}

function formatDebugValue(value: unknown): string {
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function parseRuntimePayload(payloadJson: string | null): Record<string, unknown> {
  if (!payloadJson) return {}
  try {
    const parsed: unknown = JSON.parse(payloadJson)
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : {}
  } catch {
    return { raw: payloadJson }
  }
}

function promptPayloadText(payload: Record<string, unknown>, keys: string[]): string {
  for (const key of keys) {
    const value = payload[key]
    if (typeof value === 'string' && value.trim()) return value.trim()
  }
  return ''
}

function promptEventTitle(event: RuntimeEventDto, payload: Record<string, unknown>): string {
  const title = promptPayloadText(payload, ['title', 'label', 'summary'])
  if (event.eventType === 'PROCESS_TRACE' && payload.kind === 'prompt_debug') return '发送给运行引擎的 prompt_async 请求'
  if (event.eventType === 'SKILL_STARTED') return `开始执行 Skill：${promptPayloadText(payload, ['skillName', 'label']) || '未命名'}`
  if (event.eventType === 'SKILL_COMPLETED') return `Skill 执行完成：${promptPayloadText(payload, ['skillName', 'label']) || '未命名'}`
  if (event.eventType === 'MCP_CALL') return `工具调用：${promptPayloadText(payload, ['toolName', 'command', 'label']) || 'MCP'}`
  if (event.eventType === 'CONFIRMATION_CREATED') return '触发用户交互'
  if (event.eventType === 'CONFIRMATION_RESOLVED') return '用户交互已处理'
  if (event.eventType === 'PERMISSION_REQUIRED') return '需要授权'
  if (event.eventType === 'ASSISTANT_DELTA') return 'Agent 增量回复'
  if (event.eventType === 'ASSISTANT_COMPLETED') return 'Agent 回复完成'
  if (event.eventType === 'ERROR') return '运行错误'
  return title || event.eventType
}

function promptEventNote(event: RuntimeEventDto, payload: Record<string, unknown>): string {
  if (event.eventType === 'PROCESS_TRACE' && payload.kind === 'prompt_debug') return '本轮输入给 Agent 的 prompt_async 请求，用来核对 Agent 收到了什么上下文。'
  if (event.eventType === 'ASSISTANT_DELTA') return 'Agent 的增量文本片段，通常会拼接进当前流式回复。'
  if (event.eventType === 'ASSISTANT_COMPLETED') return 'Agent 本轮回复结束信号，表示流式输出收束。'
  if (event.eventType === 'SKILL_STARTED') return '运行时开始调用 Skill。投影为 tool 类型 ExecutionStep，按 toolCallId 跟踪生命周期。'
  if (event.eventType === 'SKILL_COMPLETED') return 'Skill 调用结束。与 SKILL_STARTED 合并为同一个 ToolInvocationPart，payload 包含输出摘要。'
  if (event.eventType === 'MCP_CALL') return 'MCP 工具调用。投影为 ToolInvocationPart，挂在当前执行步骤下。'
  if (event.eventType === 'CONFIRMATION_CREATED') return 'Agent 触发用户交互。投影为 DecisionGatePart，挂在触发步骤下，同时出现在交互栏。'
  if (event.eventType === 'CONFIRMATION_RESOLVED') return '用户已处理交互项。投影为 decision StepPart 标记已解决，Agent 继续后续流程。'
  if (event.eventType === 'PROCESS_TRACE') {
    const kind = typeof payload.kind === 'string' ? payload.kind : 'trace'
    return `运行过程追踪事件，kind=${kind}，用于解释 Agent 内部状态或调试信息。`
  }
  if (event.eventType === 'ERROR') return '运行时错误事件，需要优先排查。'
  return '运行时事件，用来补充 Agent 回复之外的过程状态。'
}

function promptEventUiDisplay(event: RuntimeEventDto, payload: Record<string, unknown>): string {
  if (event.eventType === 'PROCESS_TRACE' && payload.kind === 'prompt_debug') return '显示在 Prompt Debug 的输入区，不进入对话正文。'
  if (event.eventType === 'ASSISTANT_DELTA') return '显示为对话区实时流式回复的一部分。'
  if (event.eventType === 'ASSISTANT_COMPLETED') return '通常不单独显示正文，只影响回复完成状态。'
  if (event.eventType === 'SKILL_STARTED' || event.eventType === 'SKILL_COMPLETED' || event.eventType === 'MCP_CALL' || payload.kind === 'tool_call') {
    return '投影为执行步骤中的 ToolInvocationPart，按 toolCallId 合并生命周期，挂在触发它的步骤下。'
  }
  if (event.eventType === 'CONFIRMATION_CREATED' || event.eventType === 'PERMISSION_REQUIRED') return '投影为 DecisionGatePart，挂在触发步骤下；同时出现在交互栏。'
  if (event.eventType === 'CONFIRMATION_RESOLVED') return '投影为 decision 类型 StepPart，标记交互已处理。'
  if (event.eventType === 'ERROR') return '投影为 ErrorPart，默认展开显示。'
  if (event.eventType === 'PROCESS_TRACE') return '投影为 StatusPart / ReasoningSummaryPart / 上下文步骤，根据 kind 决定折叠状态。'
  return '显示为普通运行事件。'
}

function promptEventContent(event: RuntimeEventDto, payload: Record<string, unknown>): string {
  if (event.eventType === 'PROCESS_TRACE' && payload.kind === 'prompt_debug') {
    return formatDebugValue({
      systemPrompt: payload.systemPrompt ?? null,
      userPrompt: payload.userPrompt ?? null,
      opencodePromptAsyncBody: payload.opencodePromptAsyncBody ?? null,
      requestPayload: payload.requestPayload ?? null,
    })
  }
  const summary = promptPayloadText(payload, ['summary', 'message', 'content', 'text', 'output', 'delta'])
  if (summary) return summary
  return formatDebugValue({
    eventType: event.eventType,
    eventSource: event.eventSource,
    payload,
  })
}

function buildPromptDebugCopyText(item: Pick<PromptDebugTimelineItem, 'title' | 'badge' | 'note' | 'uiDisplay' | 'content'>): string {
  return [
    `标题：${item.title}`,
    `类型：${item.badge}`,
    `作用：${item.note}`,
    `界面展示：${item.uiDisplay}`,
    '',
    item.content,
  ].join('\n')
}

function summarizeDebugContent(content: string): string {
  const compact = content
    .replace(/\s+/g, ' ')
    .trim()
  if (!compact) return '无内容'
  return compact.length > 120 ? `${compact.slice(0, 120)}...` : compact
}

function makePromptDebugInputItem(item: Omit<PromptDebugTimelineItem, 'kind' | 'side' | 'preview' | 'copyText'>): PromptDebugTimelineItem {
  return {
    ...item,
    kind: 'prompt-input',
    side: 'input',
    preview: summarizeDebugContent(item.content),
    copyText: buildPromptDebugCopyText(item),
  }
}

function isPromptDebugTimelineEvent(event: RuntimeEventDto, payload: Record<string, unknown>): boolean {
  if (event.eventType === 'PROCESS_TRACE' && payload.kind === 'prompt_debug') return false
  if (event.eventType === 'STATUS') return false
  if (event.eventType === 'PROCESS_TRACE') {
    return payload.kind === 'tool_call'
      || payload.kind === 'reasoning_summary'
      || payload.kind === 'permission'
      || payload.kind === 'error'
  }
  return [
    'SKILL_STARTED',
    'SKILL_COMPLETED',
    'MCP_CALL',
    'CONFIRMATION_CREATED',
    'CONFIRMATION_RESOLVED',
    'PERMISSION_REQUIRED',
    'ASSISTANT_DELTA',
    'ASSISTANT_COMPLETED',
    'ERROR',
  ].includes(event.eventType)
}

function promptEventPreview(event: RuntimeEventDto, payload: Record<string, unknown>, title: string, note: string, content: string): string {
  const summary = promptPayloadText(payload, ['summary', 'message', 'label', 'content', 'text', 'output', 'delta'])
  if (summary) return summarizeDebugContent(summary)
  if (event.eventType === 'SKILL_STARTED' || event.eventType === 'SKILL_COMPLETED') return title
  if (event.eventType === 'MCP_CALL') return title
  if (event.eventType === 'CONFIRMATION_CREATED' || event.eventType === 'PERMISSION_REQUIRED') return note
  if (event.eventType === 'ASSISTANT_DELTA' || event.eventType === 'ASSISTANT_COMPLETED') return summarizeDebugContent(content)
  return note
}

function messageToPromptDebugItem(message: AgentMessageDto): PromptDebugTimelineItem {
  const content = message.content?.trim() || '（空回复）'
  const title = message.status === 'FAILED' ? 'Agent 回复失败' : 'Agent 完整回复'
  const note = 'Agent 已写入会话消息表的完整回复，是用户在对话区主要阅读的内容。'
  const uiDisplay = message.contentFormat === 'MARKDOWN'
    ? '显示为对话区助手消息卡片，并按 Markdown 渲染。'
    : '显示为对话区助手消息卡片。'
  return {
    id: `message:${message.id}`,
    createdAt: message.createdAt,
    kind: 'assistant-message',
    side: 'agent',
    title,
    badge: `MESSAGE:${message.status}`,
    note,
    uiDisplay,
    preview: summarizeDebugContent(content),
    content,
    copyText: buildPromptDebugCopyText({ title, badge: `MESSAGE:${message.status}`, note, uiDisplay, content }),
  }
}

function eventToPromptDebugItem(event: RuntimeEventDto): PromptDebugTimelineItem {
  const payload = parseRuntimePayload(event.payloadJson)
  const title = promptEventTitle(event, payload)
  const note = promptEventNote(event, payload)
  const uiDisplay = promptEventUiDisplay(event, payload)
  const content = promptEventContent(event, payload)
  const preview = promptEventPreview(event, payload, title, note, content)
  return {
    id: `event:${event.id}`,
    createdAt: event.createdAt,
    kind: 'runtime-event',
    side: event.eventType === 'ASSISTANT_DELTA' || event.eventType === 'ASSISTANT_COMPLETED' ? 'agent' : 'input',
    title,
    badge: event.eventType,
    note,
    uiDisplay,
    preview,
    content,
    copyText: buildPromptDebugCopyText({ title, badge: event.eventType, note, uiDisplay, content }),
  }
}

async function copyPromptDebugText(id: string, text: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
  } else {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.setAttribute('readonly', 'true')
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
  }
  promptDebugCopiedId.value = id
  window.setTimeout(() => {
    if (promptDebugCopiedId.value === id) {
      promptDebugCopiedId.value = null
    }
  }, 1200)
}

function togglePromptDebugOpen() {
  if (promptDebugOpen.value) {
    promptDebugOpen.value = false
    promptDebugFullscreen.value = false
    return
  }
  promptDebugOpen.value = true
}

function togglePromptDebugFullscreen() {
  promptDebugOpen.value = true
  promptDebugFullscreen.value = !promptDebugFullscreen.value
  if (promptDebugFullscreen.value) {
    promptDebugDragging.value = false
  }
}

function startPromptDebugDrag(event: PointerEvent) {
  if (promptDebugFullscreen.value) return
  if (event.button !== 0) return
  const floatElement = (event.currentTarget as HTMLElement).closest('.prompt-debug-float') as HTMLElement | null
  const rect = floatElement?.getBoundingClientRect()
  if (!rect) return

  event.preventDefault()
  promptDebugHasCustomPosition.value = true
  promptDebugDragging.value = true
  promptDebugPosition.value = { x: rect.left, y: rect.top }
  promptDebugSize.value = { width: rect.width, height: rect.height }
  promptDebugDragOffset.value = {
    x: event.clientX - rect.left,
    y: event.clientY - rect.top,
  }
  window.addEventListener('pointermove', handlePromptDebugDrag)
  window.addEventListener('pointerup', stopPromptDebugDrag)
}

function handlePromptDebugDrag(event: PointerEvent) {
  if (!promptDebugDragging.value) return
  const maxX = Math.max(
    PROMPT_DEBUG_EDGE_GAP,
    window.innerWidth - promptDebugSize.value.width - PROMPT_DEBUG_EDGE_GAP
  )
  const maxY = Math.max(
    PROMPT_DEBUG_EDGE_GAP,
    window.innerHeight - promptDebugSize.value.height - PROMPT_DEBUG_EDGE_GAP
  )
  const nextX = event.clientX - promptDebugDragOffset.value.x
  const nextY = event.clientY - promptDebugDragOffset.value.y
  promptDebugPosition.value = {
    x: Math.min(Math.max(PROMPT_DEBUG_EDGE_GAP, nextX), maxX),
    y: Math.min(Math.max(PROMPT_DEBUG_EDGE_GAP, nextY), maxY),
  }
}

function stopPromptDebugDrag() {
  if (!promptDebugDragging.value) return
  promptDebugDragging.value = false
  window.removeEventListener('pointermove', handlePromptDebugDrag)
  window.removeEventListener('pointerup', stopPromptDebugDrag)
}

function timestamp(value: string | null | undefined): number {
  if (!value) return 0
  const parsed = Date.parse(value)
  return Number.isFinite(parsed) ? parsed : 0
}
</script>

<template>
  <div class="conversation-workbench">
    <section class="conversation-workbench__main" aria-label="对话工作台">
      <header class="conversation-workbench__header">
        <div class="conversation-workbench__heading">
          <button class="conversation-workbench__back" aria-label="返回上一级" title="返回上一级" @click="emit('back')">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path d="M15 18l-6-6 6-6" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
          <div>
            <h2>{{ activeTitle }}</h2>
          </div>
        </div>
        <div class="conversation-workbench__header-actions">
          <span
            v-if="agentStateLabel"
            class="conversation-workbench__agent-state"
          >
            {{ agentStateLabel.dot }} {{ agentStateLabel.text }}
            <small v-if="agentStateLabel.reason">{{ agentStateLabel.reason }}</small>
          </span>
          <span
            class="conversation-workbench__socket"
            :class="{ 'conversation-workbench__socket--online': runtimeStore.connected }"
          >
            {{ runtimeStore.connected ? '已连接' : '连接中' }}
          </span>
          <button
            class="conversation-workbench__refresh"
            :disabled="refreshingSkills"
            @click="refreshSkills"
          >
            {{ refreshingSkills ? '刷新中...' : '刷新 Skill' }}
          </button>
        </div>
      </header>

      <div v-if="skillRefreshStatus" class="conversation-workbench__notice">
        {{ skillRefreshStatus }}
      </div>

      <Teleport to="body">
        <aside
          v-if="promptDebugAvailable && latestPromptDebug"
          class="prompt-debug-float"
          :class="{
            'prompt-debug-float--open': promptDebugOpen,
            'prompt-debug-float--fullscreen': promptDebugFullscreen,
            'prompt-debug-float--dragging': promptDebugDragging,
            'prompt-debug-float--custom-position': promptDebugHasCustomPosition,
          }"
          :style="promptDebugFloatingStyle"
          aria-label="Prompt Debug 可拖拽浮窗"
        >
          <div
            class="prompt-debug-float__header"
            @pointerdown="startPromptDebugDrag"
          >
            <span class="prompt-debug-float__summary">
              <strong>Prompt Debug</strong>
              <em>{{ latestPromptDebug.payload.summary || '本轮发送给 OpenCode Runtime 的 prompt_async 请求' }}</em>
            </span>
            <div class="prompt-debug-float__actions">
              <button
                type="button"
                class="prompt-debug-float__toggle"
                :aria-label="promptDebugFullscreen ? '还原 Prompt Debug' : '全屏 Prompt Debug'"
                @pointerdown.stop
                @click="togglePromptDebugFullscreen"
              >
                {{ promptDebugFullscreen ? '还原' : '全屏' }}
              </button>
              <button
                type="button"
                class="prompt-debug-float__toggle"
                :aria-label="promptDebugOpen ? '收起 Prompt Debug' : '展开 Prompt Debug'"
                @pointerdown.stop
                @click="togglePromptDebugOpen"
              >
                {{ promptDebugOpen ? '收起' : '展开' }}
              </button>
            </div>
          </div>

          <div v-if="promptDebugOpen" class="prompt-debug-panel">
            <dl class="prompt-debug-panel__meta">
              <div>
                <dt>Agent</dt>
                <dd>{{ latestPromptDebug.payload.agent || '未提供' }}</dd>
              </div>
              <div>
                <dt>会话 ID</dt>
                <dd>{{ latestPromptDebug.payload.runtimeSessionId || '未提供' }}</dd>
              </div>
              <div>
                <dt>工作目录</dt>
                <dd>{{ latestPromptDebug.payload.workingDirectory || '未提供' }}</dd>
              </div>
            </dl>

            <section class="prompt-debug-panel__section prompt-debug-panel__timeline">
              <div class="prompt-debug-panel__section-head">
                <div>
                  <h3>本轮回合时间线</h3>
                  <p>左侧是 Agent 回复，右侧是用户输入、系统提示词、Skill 和运行事件。</p>
                </div>
                <span>{{ promptDebugTimelineItems.length }} 个节点</span>
              </div>
              <div v-if="promptDebugTimelineItems.length" class="prompt-debug-timeline">
                <details
                  v-for="item in promptDebugTimelineItems"
                  :key="item.id"
                  class="prompt-debug-timeline__item"
                  :class="{
                    'prompt-debug-timeline__item--agent': item.side === 'agent',
                    'prompt-debug-timeline__item--input': item.side === 'input',
                  }"
                >
                  <summary class="prompt-debug-timeline__summary">
                    <span class="prompt-debug-timeline__dot" aria-hidden="true"></span>
                    <div class="prompt-debug-timeline__card">
                      <div class="prompt-debug-timeline__head">
                        <div>
                          <span class="prompt-debug-timeline__badge">{{ item.badge }}</span>
                          <strong>{{ item.title }}</strong>
                        </div>
                        <span class="prompt-debug-timeline__side">
                          {{ item.side === 'agent' ? 'Agent 回复' : '输入 / 运行' }}
                        </span>
                      </div>
                      <p class="prompt-debug-timeline__preview">{{ item.preview }}</p>
                    </div>
                  </summary>
                  <div class="prompt-debug-timeline__detail">
                    <div class="prompt-debug-timeline__detail-head">
                      <dl class="prompt-debug-timeline__meta">
                        <div>
                          <dt>作用</dt>
                          <dd>{{ item.note }}</dd>
                        </div>
                        <div>
                          <dt>界面展示</dt>
                          <dd>{{ item.uiDisplay }}</dd>
                        </div>
                      </dl>
                      <button
                        type="button"
                        class="prompt-debug-panel__copy"
                        @click="copyPromptDebugText(item.id, item.copyText)"
                      >
                        {{ promptDebugCopiedId === item.id ? '已复制' : '复制此段' }}
                      </button>
                    </div>
                    <pre>{{ item.content }}</pre>
                  </div>
                </details>
              </div>
              <div v-else class="prompt-debug-panel__empty">暂无 Agent 回复或运行事件。</div>
            </section>
          </div>
        </aside>
      </Teleport>

      <div ref="messagesRef" class="conversation-workbench__messages">
        <div v-if="loadingSession && sessionStore.messages.length === 0" class="conversation-workbench__loading">
          正在准备会话...
        </div>
        <template v-else>
          <MessageList
            :messages="sessionStore.messages"
            :streaming-text="currentStreamingText"
            :runtime-events="currentRuntimeEvents"
            :active-node-id="nodeStateInfo.nodeId ?? null"
            :active-node-state="nodeStateInfo.type"
            :active-session-id="sessionStore.activeSession?.id ?? null"
            :confirmations="currentInteractions"
            :running="isConversationRunning"
            @open-artifact="handleOpenArtifact"
            @resolve-confirmation="handleResolveConfirmation"
            @interaction-changed="handleInteractionChanged"
            @open-confirmation="emit('show-confirmation', $event)"
          />
        </template>
      </div>

      <div class="conversation-workbench__composer">
        <WorkflowNodeControlBar
          v-if="shouldShowWorkflowNodeControl"
          :node-state="workflowNodeState"
          :node-instance-id="currentWorkflowNodeInstanceId"
          @action="handleWorkflowAction"
        />

        <form class="conversation-workbench__input-area" @submit.prevent="handleSend">
          <input
            ref="inputRef"
            v-model="inputText"
            class="conversation-workbench__input"
            type="text"
            :placeholder="inputPlaceholder"
            :disabled="isConversationRunning || loadingSession"
          />
          <button
            class="conversation-workbench__send"
            :class="{ 'conversation-workbench__send--pause': isConversationRunning }"
            :disabled="isConversationRunning ? isCancellingCurrentReply : (!inputText.trim() || loadingSession)"
            :type="isConversationRunning ? 'button' : 'submit'"
            :aria-label="isConversationRunning ? '暂停当前回复' : '发送消息'"
            :title="isConversationRunning ? '暂停当前回复' : '发送消息'"
            @click="isConversationRunning ? handleCancelReply() : undefined"
          >
            <svg v-if="isConversationRunning" width="22" height="22" viewBox="0 0 24 24" fill="none">
              <rect x="7" y="5" width="3.5" height="14" rx="1.2" fill="currentColor"/>
              <rect x="13.5" y="5" width="3.5" height="14" rx="1.2" fill="currentColor"/>
            </svg>
            <svg v-else width="22" height="22" viewBox="0 0 24 24" fill="none">
              <path d="M22 2L11 13" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M22 2L15 22L11 13L2 9L22 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
        </form>
      </div>
    </section>
  </div>
</template>

<style scoped>
.conversation-workbench {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: var(--bg-primary);
  padding: 14px;
  box-sizing: border-box;
}

.conversation-workbench__main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.conversation-workbench__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 82px;
  flex-shrink: 0;
  padding: 18px 22px;
  border-bottom: 1px solid var(--border-color);
}

.conversation-workbench__heading {
  display: flex;
  flex: 1 1 auto;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
}

.conversation-workbench__heading > div {
  flex: 1 1 auto;
  min-width: 0;
}

.conversation-workbench__back {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-card);
  color: var(--text-secondary);
  cursor: pointer;
}

.conversation-workbench__back:hover {
  border-color: var(--brand-border);
  background: var(--brand-soft);
  color: var(--brand-primary);
}

.conversation-workbench__header h2 {
  margin: 0;
  overflow: hidden;
  color: var(--text-primary);
  font-size: 20px;
  font-weight: 900;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-workbench__header p {
  margin-top: 6px;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-workbench__header-actions {
  display: flex;
  flex: 0 1 auto;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.conversation-workbench__agent-state {
  display: inline-flex;
  flex: 1 1 auto;
  align-items: center;
  gap: 4px;
  min-width: 0;
  max-width: 520px;
  min-height: 32px;
  padding: 0 10px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.conversation-workbench__agent-state small {
  min-width: 0;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 700;
  margin-left: 4px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-workbench__socket {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--bg-tertiary);
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 800;
}

.conversation-workbench__socket::before {
  content: '';
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--text-muted);
}

.conversation-workbench__socket--online {
  background: var(--success-soft);
  color: var(--success);
}

.conversation-workbench__socket--online::before {
  background: var(--success);
}

.conversation-workbench__refresh {
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.conversation-workbench__refresh:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.conversation-workbench__messages {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 0 12px;
  display: flex;
  flex-direction: column;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.conversation-workbench__notice {
  margin: 10px 12px 0;
  padding: 8px 10px;
  border: 1px solid var(--brand-border);
  border-radius: 8px;
  background: var(--brand-soft);
  color: var(--accent-blue);
  font-size: 12px;
  font-weight: 800;
}

.conversation-workbench__loading {
  display: grid;
  place-items: center;
  flex: 1;
  min-height: 200px;
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 750;
}

.conversation-workbench__composer {
  flex-shrink: 0;
  padding: 12px;
  border-top: 1px solid var(--border-color);
  background: var(--bg-card);
}

.prompt-debug-float {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 9999;
  width: min(720px, calc(100vw - 32px));
  max-height: min(78vh, 760px);
  overflow: hidden;
  border: 1px solid var(--brand-border);
  border-radius: 10px;
  background: var(--bg-card);
  color: var(--text-primary);
  box-shadow: 0 22px 55px rgba(15, 23, 42, 0.18);
  will-change: transform;
}

.prompt-debug-float--custom-position {
  top: 0;
  right: auto;
  left: 0;
}

.prompt-debug-float--fullscreen {
  inset: 0;
  width: 100vw;
  height: 100vh;
  max-height: 100vh;
  border-radius: 0;
}

.prompt-debug-float--fullscreen.prompt-debug-float--custom-position {
  transform: none;
}

.prompt-debug-float:not(.prompt-debug-float--open) {
  width: min(420px, calc(100vw - 32px));
}

.prompt-debug-float--open:not(.prompt-debug-float--fullscreen) {
  width: min(980px, calc(100vw - 32px));
}

.prompt-debug-float--dragging {
  user-select: none;
}

.prompt-debug-float__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-card);
  cursor: grab;
  touch-action: none;
}

.prompt-debug-float--dragging .prompt-debug-float__header {
  cursor: grabbing;
}

.prompt-debug-float--fullscreen .prompt-debug-float__header {
  cursor: default;
}

.prompt-debug-float__summary {
  appearance: none;
  display: block;
  min-width: 0;
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  text-align: left;
}

.prompt-debug-float__summary strong {
  display: block;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 900;
}

.prompt-debug-float__summary em {
  display: block;
  margin-top: 3px;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 700;
  font-style: normal;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.prompt-debug-float__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.prompt-debug-float__toggle {
  appearance: none;
  flex-shrink: 0;
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--border-color);
  border-radius: 7px;
  background: var(--bg-primary);
  color: var(--accent-blue);
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.prompt-debug-panel {
  max-height: calc(min(78vh, 760px) - 50px);
  overflow: auto;
}

.prompt-debug-float--fullscreen .prompt-debug-panel {
  height: calc(100vh - 50px);
  max-height: calc(100vh - 50px);
}

.prompt-debug-panel__meta {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 0;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border-color);
}

.prompt-debug-panel__meta dt {
  margin-bottom: 4px;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 850;
}

.prompt-debug-panel__meta dd {
  margin: 0;
  overflow: hidden;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 750;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.prompt-debug-panel__section {
  padding: 12px 14px;
  border-bottom: 1px solid var(--border-color);
}

.prompt-debug-panel__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.prompt-debug-panel__section-head > div {
  min-width: 0;
}

.prompt-debug-panel__section-head h3 {
  margin: 0;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 900;
}

.prompt-debug-panel__section-head p {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 700;
  line-height: 1.45;
}

.prompt-debug-panel__section-head > span {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 800;
}

.prompt-debug-panel__copy {
  flex: 0 0 auto;
  min-height: 26px;
  padding: 0 9px;
  border: 1px solid var(--border-color);
  border-radius: 7px;
  background: var(--bg-primary);
  color: var(--accent-blue);
  font-size: 11px;
  font-weight: 850;
  cursor: pointer;
}

.prompt-debug-panel__copy:hover {
  border-color: var(--brand-border);
  background: var(--brand-soft);
}

.prompt-debug-panel pre {
  max-height: 260px;
  overflow: auto;
  margin: 0;
  padding: 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.prompt-debug-panel__timeline {
  padding-bottom: 14px;
}

.prompt-debug-timeline {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px 0;
}

.prompt-debug-timeline::before {
  content: '';
  position: absolute;
  top: 4px;
  bottom: 4px;
  left: 50%;
  width: 1px;
  background: var(--border-color);
}

.prompt-debug-timeline__item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 28px minmax(0, 1fr);
  gap: 0;
  align-items: start;
  border: 0;
  background: transparent;
}

.prompt-debug-timeline__summary {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: minmax(0, 1fr) 28px minmax(0, 1fr);
  align-items: start;
  list-style: none;
  cursor: pointer;
}

.prompt-debug-timeline__summary::-webkit-details-marker {
  display: none;
}

.prompt-debug-timeline__dot {
  position: relative;
  z-index: 1;
  grid-column: 2;
  justify-self: center;
  width: 13px;
  height: 13px;
  margin-top: 13px;
  border: 2px solid var(--bg-card);
  border-radius: 999px;
  background: var(--accent-blue);
  box-shadow: 0 0 0 1px var(--brand-border);
}

.prompt-debug-timeline__card {
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-overlay);
  transition: border-color 0.16s ease, background-color 0.16s ease;
}

.prompt-debug-timeline__summary:hover .prompt-debug-timeline__card {
  border-color: var(--brand-border);
  background: var(--bg-card);
}

.prompt-debug-timeline__item--agent .prompt-debug-timeline__card {
  grid-column: 1;
}

.prompt-debug-timeline__item--input .prompt-debug-timeline__card {
  grid-column: 3;
}

.prompt-debug-timeline__item--agent .prompt-debug-timeline__dot {
  background: var(--success);
}

.prompt-debug-timeline__item--input .prompt-debug-timeline__dot {
  background: var(--accent-blue);
}

.prompt-debug-timeline__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
}

.prompt-debug-timeline__head > div {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.prompt-debug-timeline__head strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.prompt-debug-timeline__badge {
  flex: 0 0 auto;
  max-width: 160px;
  padding: 2px 7px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--brand-soft);
  color: var(--accent-blue);
  font-size: 10px;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.prompt-debug-timeline__side {
  flex: 0 0 auto;
  color: var(--text-muted);
  font-size: 10px;
  font-weight: 900;
}

.prompt-debug-timeline__preview {
  display: -webkit-box;
  margin: 8px 0 0;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 650;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.prompt-debug-timeline__detail {
  min-width: 0;
  margin-top: 8px;
  padding: 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-card);
}

.prompt-debug-timeline__item--agent .prompt-debug-timeline__detail {
  grid-column: 1;
}

.prompt-debug-timeline__item--input .prompt-debug-timeline__detail {
  grid-column: 3;
}

.prompt-debug-timeline__detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.prompt-debug-timeline__meta {
  display: grid;
  gap: 6px;
  grid-template-columns: minmax(0, 1fr);
  margin: 0;
}

.prompt-debug-timeline__meta div {
  min-width: 0;
}

.prompt-debug-timeline__meta dt {
  margin-bottom: 2px;
  color: var(--text-muted);
  font-size: 10px;
  font-weight: 900;
}

.prompt-debug-timeline__meta dd {
  margin: 0;
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 650;
  line-height: 1.45;
}

.prompt-debug-timeline__item pre {
  max-height: 260px;
}

.prompt-debug-panel__empty {
  padding: 14px;
  border: 1px dashed var(--border-color);
  border-radius: 8px;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 750;
  text-align: center;
}

@media (max-width: 760px) {
  .prompt-debug-timeline::before {
    left: 8px;
  }

  .prompt-debug-timeline__item,
  .prompt-debug-timeline__summary {
    grid-template-columns: 18px minmax(0, 1fr);
  }

  .prompt-debug-timeline__dot {
    grid-column: 1;
    justify-self: start;
  }

  .prompt-debug-timeline__item--agent .prompt-debug-timeline__card,
  .prompt-debug-timeline__item--input .prompt-debug-timeline__card,
  .prompt-debug-timeline__item--agent .prompt-debug-timeline__detail,
  .prompt-debug-timeline__item--input .prompt-debug-timeline__detail {
    grid-column: 2;
  }
}

.conversation-workbench__input-area {
  width: min(920px, 100%);
  margin: 0 auto;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 48px;
  gap: 10px;
}

.conversation-workbench__input {
  width: 100%;
  height: 46px;
  padding: 0 16px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-primary);
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 650;
  outline: none;
}

.conversation-workbench__input:focus {
  border-color: var(--accent-blue);
  background: var(--bg-secondary);
  box-shadow: 0 0 0 3px var(--glow-blue);
}

.conversation-workbench__input::placeholder {
  color: var(--text-muted);
}

.conversation-workbench__input:disabled {
  background: var(--bg-tertiary);
  color: var(--text-muted);
  cursor: not-allowed;
}

.conversation-workbench__send {
  display: grid;
  place-items: center;
  width: 48px;
  height: 46px;
  border: 0;
  border-radius: 10px;
  background: var(--brand-gradient);
  color: var(--on-brand);
  cursor: pointer;
}

.conversation-workbench__send--pause {
  background: var(--warning);
  color: #111827;
}

.conversation-workbench__send:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
