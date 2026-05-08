<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useSessionStore } from '../stores/sessions'
import { useWorkflowStore } from '../stores/workflows'
import { useRuntimeStore } from '../stores/runtime'
import { useWorkItemStore } from '../stores/workItems'
import MessageList from '../components/conversation/MessageList.vue'
import { runtimeResourceApi } from '../api/runtimeResources'
import { artifactApi } from '../api/artifacts'
import type { AgentSessionDto, ArtifactDto, WorkflowNodeInstanceDto, WorkflowSummaryNodeDto, WorkflowSummaryStageDto } from '../api/types'

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

const workflowFailureNotice = computed(() => {
  const failedNode = currentWorkflowInstance.value?.nodes.find((node) =>
    node.status === 'FAILED' && Boolean(node.errorMessage?.trim())
  ) ?? currentWorkflowInstance.value?.nodes.find((node) => node.status === 'FAILED')
  if (failedNode) {
    return {
      title: `${workflowNodeLabel(failedNode)} 执行失败`,
      detail: failedNode.errorMessage?.trim() || '工作流节点执行失败，暂无详细错误原因。',
    }
  }

  const summary = selectedWorkItem.value?.workflowSummary
  const failedStage = summary?.stages?.find((stage) =>
    stage.status === 'FAILED' && Boolean(stage.errorMessage?.trim())
  ) ?? summary?.stages?.find((stage) => stage.status === 'FAILED')
  if (failedStage) {
    return {
      title: `${summaryNodeLabel(failedStage)} 执行失败`,
      detail: failedStage.errorMessage?.trim() || '工作流节点执行失败，暂无详细错误原因。',
    }
  }

  const failedSummaryNode = summary?.nodes.find((node) =>
    node.status === 'FAILED' && Boolean(node.errorMessage?.trim())
  ) ?? summary?.nodes.find((node) => node.status === 'FAILED')
  if (failedSummaryNode) {
    return {
      title: `${summaryNodeLabel(failedSummaryNode)} 执行失败`,
      detail: failedSummaryNode.errorMessage?.trim() || '工作流节点执行失败，暂无详细错误原因。',
    }
  }

  return null
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

function workflowNodeLabel(node: WorkflowNodeInstanceDto): string {
  const definition = workflowStore.definitions
    .flatMap((item) => item.nodes)
    .find((item) => item.id === node.nodeDefinitionId)
  return definition?.name ?? node.skillName ?? '工作流节点'
}

function summaryNodeLabel(node: WorkflowSummaryNodeDto | WorkflowSummaryStageDto): string {
  if ('name' in node) {
    return node.name ?? node.skillName ?? '工作流节点'
  }
  return node.definitionName ?? node.skillName ?? '工作流节点'
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

      <div v-if="workflowFailureNotice" class="conversation-workbench__failure" role="alert">
        <strong>{{ workflowFailureNotice.title }}</strong>
        <span>{{ workflowFailureNotice.detail }}</span>
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

.conversation-workbench__failure {
  margin: 10px 22px 0;
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid color-mix(in srgb, var(--error) 34%, var(--border-color));
  border-radius: 8px;
  background: color-mix(in srgb, var(--error) 9%, var(--bg-card));
  color: var(--text-primary);
  font-size: 12px;
  line-height: 1.55;
}

.conversation-workbench__failure strong {
  color: var(--error);
  font-size: 13px;
  font-weight: 900;
}

.conversation-workbench__failure span {
  overflow-wrap: anywhere;
  color: var(--text-secondary);
  font-weight: 700;
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
