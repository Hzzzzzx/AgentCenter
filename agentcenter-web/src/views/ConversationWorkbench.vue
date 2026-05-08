<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useSessionStore } from '../stores/sessions'
import { useWorkflowStore } from '../stores/workflows'
import { useRuntimeStore } from '../stores/runtime'
import { useWorkItemStore } from '../stores/workItems'
import MessageList from '../components/conversation/MessageList.vue'
import { runtimeResourceApi } from '../api/runtimeResources'
import { artifactApi } from '../api/artifacts'
import type { AgentSessionDto, ArtifactDto, WorkflowNodeInstanceDto, WorkflowNodeStatus } from '../api/types'

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

const props = defineProps<{
  workItemId?: string
  targetSessionId?: string | null
}>()

const emit = defineEmits<{
  back: []
  'open-artifact': [artifact: ArtifactDto]
}>()

const sessionStore = useSessionStore()
const workflowStore = useWorkflowStore()
const runtimeStore = useRuntimeStore()
const workItemStore = useWorkItemStore()

const inputText = ref('')
const inputRef = ref<HTMLInputElement | null>(null)
const messagesRef = ref<HTMLElement | null>(null)
const skillRefreshStatus = ref('')
const refreshingSkills = ref(false)
const loadingSession = ref(false)
const cancellingReply = ref(false)

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

const isConversationRunning = computed(() =>
  runtimeStore.busy || Boolean(runtimeStore.streamingText.trim())
)

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

onMounted(async () => {
  if (workflowStore.definitions.length === 0) {
    await workflowStore.loadDefinitions()
  }
  await ensureActiveSession()
})

watch([() => props.workItemId, () => props.targetSessionId], async () => {
  await ensureActiveSession()
})

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
  cancellingReply.value = true
  try {
    await sessionStore.cancelActiveSession()
    runtimeStore.markIdle()
    await sessionStore.selectSession(sessionStore.activeSession.id)
  } finally {
    cancellingReply.value = false
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

async function handleRetry() {
  const info = nodeStateInfo.value
  if (!info?.nodeId) return
  try {
    await workflowStore.retryNode(info.nodeId)
  } catch {
    // Error handled by store/UI state
  }
}

async function handleSkip() {
  const info = nodeStateInfo.value
  if (!info?.nodeId) return
  try {
    await workflowStore.skipNode(info.nodeId)
  } catch {
    // Error handled by store/UI state
  }
}

async function handleStop() {
  // Navigate back — workflow remains blocked at the failed node
  emit('back')
}

async function handleConfirmationAction() {
  const instance = currentWorkflowInstance.value
  if (!instance) return
  try {
    await workflowStore.continueWorkflow(instance.id)
  } catch {
    // Error handled by store/UI state
  }
}

async function openArtifactById(artifactId: string) {
  const workItem = selectedWorkItem.value
  if (!workItem) return
  const artifacts = await artifactApi.listByWorkItem(workItem.id)
  const artifact = artifacts.find((item) => item.id === artifactId)
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

      <div
        v-if="nodeStateInfo.type"
        class="node-state-area"
        :class="`node-state-area--${nodeStateInfo.type.toLowerCase()}`"
        role="status"
      >
        <!-- RUNNING -->
        <template v-if="nodeStateInfo.type === 'RUNNING'">
          <span class="node-state-area__indicator node-state-area__indicator--pulse">&#9679;</span>
          <span class="node-state-area__label">{{ nodeStateInfo.skillName || nodeStateInfo.nodeName }} 运行中</span>
        </template>

        <!-- WAITING_CONFIRMATION -->
        <template v-if="nodeStateInfo.type === 'WAITING_CONFIRMATION'">
          <span class="node-state-area__indicator">&#9208;</span>
          <span class="node-state-area__label">等待确认：{{ nodeStateInfo.confirmationType }}</span>
          <button class="node-state-area__action" @click="handleConfirmationAction">处理</button>
        </template>

        <!-- FAILED -->
        <template v-if="nodeStateInfo.type === 'FAILED'">
          <span class="node-state-area__indicator">&#10005;</span>
          <span class="node-state-area__label">{{ nodeStateInfo.errorMessage }}</span>
          <div class="node-state-area__actions">
            <button @click="handleRetry">重试</button>
            <button @click="handleSkip">跳过</button>
            <button @click="handleStop">停止推进</button>
          </div>
        </template>

        <!-- COMPLETED (node) -->
        <template v-if="nodeStateInfo.type === 'COMPLETED'">
          <span class="node-state-area__indicator">&#10003;</span>
          <span class="node-state-area__label">{{ nodeStateInfo.nodeName }} 输出完成</span>
          <button v-if="nodeStateInfo.artifactId" class="node-state-area__action" @click="openArtifactById(nodeStateInfo.artifactId)">
            查看产物
          </button>
        </template>

        <!-- WORKFLOW_COMPLETED -->
        <template v-if="nodeStateInfo.type === 'WORKFLOW_COMPLETED'">
          <span class="node-state-area__indicator">&#10003;</span>
          <span class="node-state-area__label node-state-area__label--prominent">任务已完成</span>
        </template>
      </div>

      <div ref="messagesRef" class="conversation-workbench__messages">
        <div v-if="loadingSession && sessionStore.messages.length === 0" class="conversation-workbench__loading">
          正在准备会话...
        </div>
        <template v-else>
          <MessageList
            :messages="sessionStore.messages"
            :streaming-text="runtimeStore.streamingText"
            :runtime-events="runtimeStore.events"
            @open-artifact="handleOpenArtifact"
          />
        </template>
      </div>

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

.node-state-area {
  margin: 10px 22px 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-card);
  font-size: 12px;
  line-height: 1.55;
  flex-wrap: wrap;
}

.node-state-area__indicator {
  flex-shrink: 0;
  font-size: 13px;
  line-height: 1;
}

.node-state-area__indicator--pulse {
  animation: node-state-pulse 1.8s ease-in-out infinite;
}

@keyframes node-state-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.node-state-area__label {
  color: var(--text-primary);
  font-weight: 700;
  overflow-wrap: anywhere;
}

.node-state-area__label--prominent {
  font-size: 14px;
  font-weight: 900;
}

.node-state-area__action,
.node-state-area__actions button {
  height: 28px;
  padding: 0 12px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-primary);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
  white-space: nowrap;
}

.node-state-area__action:hover,
.node-state-area__actions button:hover {
  border-color: var(--brand-border);
  background: var(--brand-soft);
  color: var(--brand-primary);
}

.node-state-area__actions {
  display: flex;
  gap: 6px;
  margin-left: auto;
}

/* State-specific colors */
.node-state-area--running {
  border-color: color-mix(in srgb, var(--accent-blue) 34%, var(--border-color));
  background: color-mix(in srgb, var(--accent-blue) 9%, var(--bg-card));
}

.node-state-area--running .node-state-area__indicator {
  color: var(--accent-blue);
}

.node-state-area--running .node-state-area__label {
  color: var(--accent-blue);
}

.node-state-area--waiting_confirmation {
  border-color: color-mix(in srgb, var(--warning) 34%, var(--border-color));
  background: color-mix(in srgb, var(--warning) 9%, var(--bg-card));
}

.node-state-area--waiting_confirmation .node-state-area__indicator {
  color: var(--warning);
}

.node-state-area--waiting_confirmation .node-state-area__label {
  color: var(--warning);
}

.node-state-area--failed {
  border-color: color-mix(in srgb, var(--error) 34%, var(--border-color));
  background: color-mix(in srgb, var(--error) 9%, var(--bg-card));
}

.node-state-area--failed .node-state-area__indicator {
  color: var(--error);
}

.node-state-area--failed .node-state-area__label {
  color: var(--text-secondary);
}

.node-state-area--completed {
  border-color: color-mix(in srgb, var(--success) 34%, var(--border-color));
  background: color-mix(in srgb, var(--success) 9%, var(--bg-card));
}

.node-state-area--completed .node-state-area__indicator {
  color: var(--success);
}

.node-state-area--completed .node-state-area__label {
  color: var(--success);
}

.node-state-area--workflow_completed {
  border-color: color-mix(in srgb, var(--success) 50%, var(--border-color));
  background: color-mix(in srgb, var(--success) 14%, var(--bg-card));
}

.node-state-area--workflow_completed .node-state-area__indicator {
  color: var(--success);
  font-size: 16px;
}

.node-state-area--workflow_completed .node-state-area__label {
  color: var(--success);
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

.conversation-workbench__input-area {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 48px;
  gap: 10px;
  flex-shrink: 0;
  padding: 14px 22px;
  border-top: 1px solid var(--border-color);
  background: var(--bg-card);
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
