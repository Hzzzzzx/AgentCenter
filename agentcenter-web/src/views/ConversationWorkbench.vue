<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useSessionStore } from '../stores/sessions'
import { useWorkflowStore } from '../stores/workflows'
import { useRuntimeStore } from '../stores/runtime'
import { useWorkItemStore } from '../stores/workItems'
import { useConfirmationStore } from '../stores/confirmations'
import MessageList from '../components/conversation/MessageList.vue'
import ConversationInteractionBar from '../components/conversation/ConversationInteractionBar.vue'
import { runtimeResourceApi } from '../api/runtimeResources'
import { artifactApi } from '../api/artifacts'
import type {
  AgentSessionDto,
  ArtifactDto,
  RuntimeEventDto,
  WorkflowNodeInstanceDto,
  WorkflowNodeStatus,
} from '../api/types'

interface NodeStateInfo {
  type: 'RUNNING' | 'WAITING_CONFIRMATION' | 'FAILED' | 'COMPLETED' | 'WORKFLOW_COMPLETED' | null
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

const PROMPT_DEBUG_ENABLED = true
const PROMPT_DEBUG_EDGE_GAP = 16

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
const workItemStore = useWorkItemStore()
const confirmationStore = useConfirmationStore()

const inputText = ref('')
const inputRef = ref<HTMLInputElement | null>(null)
const messagesRef = ref<HTMLElement | null>(null)
const skillRefreshStatus = ref('')
const refreshingSkills = ref(false)
const loadingSession = ref(false)
const cancellingReply = ref(false)
const pausedRunningNodeId = ref<string | null>(null)
const promptDebugOpen = ref(false)
const promptDebugPosition = ref({ x: 0, y: 0 })
const promptDebugHasCustomPosition = ref(false)
const promptDebugDragging = ref(false)
const promptDebugDragOffset = ref({ x: 0, y: 0 })
const promptDebugSize = ref({ width: 0, height: 0 })

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
  const instance = workflowStore.activeWorkflowInstance
  if (!instance) return null
  if (props.workItemId && instance.workItemId !== props.workItemId) return null
  if (sessionStore.activeSession?.workflowInstanceId && instance.id !== sessionStore.activeSession.workflowInstanceId) return null
  return instance
})

const nodeStateInfo = computed<NodeStateInfo>(() => {
  const instance = currentWorkflowInstance.value
  if (!instance) return { type: null }

  // Workflow completed
  if (instance.status === 'COMPLETED') {
    return { type: 'WORKFLOW_COMPLETED' }
  }

  // Find the active node: prefer RUNNING > WAITING_CONFIRMATION > FAILED > latest COMPLETED
  const activeStatuses: WorkflowNodeStatus[] = ['RUNNING', 'WAITING_CONFIRMATION', 'FAILED']
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

const isConversationRunning = computed(() =>
  runtimeStore.busy
  || Boolean(runtimeStore.streamingText.trim())
  || (
    nodeStateInfo.value.type === 'RUNNING'
    && nodeStateInfo.value.nodeId !== pausedRunningNodeId.value
  )
)

const currentInteractions = computed(() => {
  const session = sessionStore.activeSession
  const workItemId = props.workItemId ?? session?.workItemId ?? null
  const workflowInstanceId = session?.workflowInstanceId ?? currentWorkflowInstance.value?.id ?? null
  const nodeId = nodeStateInfo.value.nodeId ?? null

  return confirmationStore.pendingConfirmations.filter((item) => {
    if (item.status !== 'PENDING' && item.status !== 'IN_CONVERSATION') return false
    return Boolean(
      (session?.id && item.agentSessionId === session.id)
      || (workItemId && item.workItemId === workItemId)
      || (workflowInstanceId && item.workflowInstanceId === workflowInstanceId)
      || (nodeId && item.workflowNodeInstanceId === nodeId)
    )
  })
})

const promptDebugItems = computed<PromptDebugItem[]>(() =>
  runtimeStore.events
    .filter((event) => event.eventType === 'PROCESS_TRACE')
    .map((event) => ({ event, payload: parsePromptDebugPayload(event.payloadJson) }))
    .filter((item) => item.payload.kind === 'prompt_debug')
    .sort((a, b) => timestamp(b.event.createdAt) - timestamp(a.event.createdAt))
)

const latestPromptDebug = computed(() => promptDebugItems.value[0] ?? null)
const promptDebugAvailable = computed(() => PROMPT_DEBUG_ENABLED && latestPromptDebug.value !== null)
const promptDebugFloatingStyle = computed(() =>
  promptDebugHasCustomPosition.value
    ? { transform: `translate(${promptDebugPosition.value.x}px, ${promptDebugPosition.value.y}px)` }
    : {}
)

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
  loadingSession.value = true
  try {
    if (workItemStore.items.length === 0) {
      await workItemStore.loadItems()
    }
    await sessionStore.loadSessions()

    let session: AgentSessionDto | undefined

    if (props.targetSessionId) {
      session = sessionStore.sessions.find((item) => item.id === props.targetSessionId)
    }

    if (!session && props.workItemId) {
      session = sessionStore.sessions.find(
        (item) => item.workItemId === props.workItemId && item.status === 'ACTIVE'
      )
    }

    if (!session && props.workItemId) {
      const item = selectedWorkItem.value
      session = await sessionStore.createSession({
        sessionType: 'WORK_ITEM',
        title: item ? `${item.code} · ${item.title}` : '任务会话',
        workItemId: props.workItemId,
        runtimeType: 'OPENCODE',
      })
    }

    if (!session) {
      return null
    }

    await sessionStore.selectSession(session.id)
    runtimeStore.connectSSE(session.id)

    if (session.workflowInstanceId) {
      await workflowStore.loadInstance(session.workflowInstanceId)
    }
    if (props.workItemId) {
      await workItemStore.refreshItem(props.workItemId)
    }

    await nextTick()
    inputRef.value?.focus()
    return session
  } finally {
    loadingSession.value = false
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
watch(() => runtimeStore.streamingText, scrollToBottom)
watch(() => runtimeStore.events.length, scrollToBottom)

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || isConversationRunning.value) return

  const session = await ensureActiveSession()
  if (!session) return

  runtimeStore.markBusy()
  await sessionStore.sendMessage(text)
  inputText.value = ''
}

async function handleCancelReply() {
  if (!sessionStore.activeSession || cancellingReply.value) return
  const sessionId = sessionStore.activeSession.id
  const runningNodeId = nodeStateInfo.value.type === 'RUNNING'
    ? nodeStateInfo.value.nodeId ?? null
    : null
  cancellingReply.value = true
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
      await workItemStore.refreshItem(sessionStore.activeSession.workItemId)
    }
  } finally {
    cancellingReply.value = false
  }
}

async function handleInteractionChanged() {
  await confirmationStore.loadPending()
  if (sessionStore.activeSession?.workflowInstanceId) {
    await workflowStore.refreshInstance(sessionStore.activeSession.workflowInstanceId)
  }
  const workItemId = props.workItemId ?? sessionStore.activeSession?.workItemId
  if (workItemId) {
    await workItemStore.refreshItem(workItemId)
  }
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

async function handleOpenArtifact(title: string) {
  const workItem = selectedWorkItem.value
  if (!workItem) return
  const artifacts = await artifactApi.listByWorkItem(workItem.id)
  const artifact = artifacts.find((item) => item.title === title)
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

function startPromptDebugDrag(event: PointerEvent) {
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
            <button
              type="button"
              class="prompt-debug-float__toggle"
              :aria-label="promptDebugOpen ? '收起 Prompt Debug' : '展开 Prompt Debug'"
              @pointerdown.stop
              @click="promptDebugOpen = !promptDebugOpen"
            >
              {{ promptDebugOpen ? '收起' : '展开' }}
            </button>
          </div>

          <div v-if="promptDebugOpen" class="prompt-debug-panel">
            <dl class="prompt-debug-panel__meta">
              <div>
                <dt>Agent</dt>
                <dd>{{ latestPromptDebug.payload.agent || '未提供' }}</dd>
              </div>
              <div>
                <dt>Runtime Session</dt>
                <dd>{{ latestPromptDebug.payload.runtimeSessionId || '未提供' }}</dd>
              </div>
              <div>
                <dt>工作目录</dt>
                <dd>{{ latestPromptDebug.payload.workingDirectory || '未提供' }}</dd>
              </div>
            </dl>

            <section class="prompt-debug-panel__section">
              <h3>System Prompt</h3>
              <pre>{{ latestPromptDebug.payload.systemPrompt || '无显式 system prompt' }}</pre>
            </section>

            <section class="prompt-debug-panel__section">
              <h3>User Prompt / Parts Text</h3>
              <pre>{{ latestPromptDebug.payload.userPrompt || '无' }}</pre>
            </section>

            <details
              v-if="promptDebugHttpBodyJson"
              class="prompt-debug-panel__details"
              open
            >
              <summary>OpenCode prompt_async body</summary>
              <pre>{{ promptDebugHttpBodyJson }}</pre>
            </details>

            <details
              v-if="promptDebugRequestJson"
              class="prompt-debug-panel__details"
            >
              <summary>AgentCenter transport payload</summary>
              <pre>{{ promptDebugRequestJson }}</pre>
            </details>
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
            :streaming-text="runtimeStore.streamingText"
            :runtime-events="runtimeStore.events"
            :active-node-id="nodeStateInfo.nodeId ?? null"
            :active-node-state="nodeStateInfo.type"
            :active-session-id="sessionStore.activeSession?.id ?? null"
            :running="isConversationRunning"
            @open-artifact="handleOpenArtifact"
          />
        </template>
      </div>

      <div class="conversation-workbench__composer">
        <ConversationInteractionBar
          :interactions="currentInteractions"
          @resolved="handleInteractionChanged"
          @rejected="handleInteractionChanged"
          @open="emit('show-confirmation', $event)"
        />

        <form class="conversation-workbench__input-area" @submit.prevent="handleSend">
          <input
            ref="inputRef"
            v-model="inputText"
            class="conversation-workbench__input"
            type="text"
            :placeholder="isConversationRunning ? '对话运行中，可点击右侧按钮暂停...' : '输入指令或问题...'"
            :disabled="isConversationRunning || loadingSession"
          />
          <button
            class="conversation-workbench__send"
            :class="{ 'conversation-workbench__send--pause': isConversationRunning }"
            :disabled="isConversationRunning ? cancellingReply : (!inputText.trim() || loadingSession)"
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
  padding: 18px 22px;
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
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
}

.conversation-workbench__heading > div {
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
  color: var(--text-primary);
  font-size: 20px;
  font-weight: 900;
  line-height: 1.2;
}

.conversation-workbench__header p {
  margin-top: 6px;
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 650;
}

.conversation-workbench__header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
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
  overflow-y: auto;
  padding: 0 22px;
  display: flex;
  flex-direction: column;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.conversation-workbench__notice {
  margin: 10px 22px 0;
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
  padding: 14px 22px;
  border-top: 1px solid var(--border-color);
  background: var(--bg-card);
}

.prompt-debug-float {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 9999;
  width: min(560px, calc(100vw - 32px));
  max-height: min(64vh, 680px);
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

.prompt-debug-float:not(.prompt-debug-float--open) {
  width: min(420px, calc(100vw - 32px));
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
  max-height: calc(min(64vh, 680px) - 50px);
  overflow: auto;
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

.prompt-debug-panel__section,
.prompt-debug-panel__details {
  padding: 12px 14px;
  border-bottom: 1px solid var(--border-color);
}

.prompt-debug-panel__section h3 {
  margin: 0 0 8px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 900;
}

.prompt-debug-panel__details summary {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 900;
  cursor: pointer;
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
