<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useSessionStore } from '../stores/sessions'
import { useWorkflowStore } from '../stores/workflows'
import { useRuntimeStore } from '../stores/runtime'
import { useWorkItemStore } from '../stores/workItems'
import MessageList from '../components/conversation/MessageList.vue'
import { runtimeResourceApi } from '../api/runtimeResources'
import type { AgentSessionDto } from '../api/types'

const props = defineProps<{
  workItemId?: string
  targetSessionId?: string | null
}>()

const sessionStore = useSessionStore()
const workflowStore = useWorkflowStore()
const runtimeStore = useRuntimeStore()
const workItemStore = useWorkItemStore()

const inputText = ref('')
const inputRef = ref<HTMLInputElement | null>(null)
const skillRefreshStatus = ref('')
const refreshingSkills = ref(false)
const loadingSession = ref(false)

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

const activeSubtitle = computed(() => {
  if (selectedWorkItem.value) {
    return `${selectedWorkItem.value.type} · ${selectedWorkItem.value.status} · ${selectedWorkItem.value.priority}`
  }
  return '通过对话驱动全流程'
})

const scenarioChips = [
  '需求转设计',
  '发布风险巡检',
  '故障跟进',
]

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
    runtimeStore.disconnect()
    runtimeStore.connectSSE(session.id)

    if (session.workflowInstanceId) {
      await workflowStore.loadInstance(session.workflowInstanceId)
    }

    await nextTick()
    inputRef.value?.focus()
    return session
  } finally {
    loadingSession.value = false
  }
}

function applyScenario(text: string) {
  const context = selectedWorkItem.value
    ? `请基于 ${selectedWorkItem.value.code}《${selectedWorkItem.value.title}》`
    : '请基于当前上下文'
  const prompts: Record<string, string> = {
    需求转设计: `${context}，把需求整理成一份结构化设计说明，并列出关键风险。`,
    发布风险巡检: `${context}，检查发布前风险、阻塞项和需要用户确认的问题。`,
    故障跟进: `${context}，整理故障定位路径、下一步操作和需要补充的信息。`,
  }
  inputText.value = prompts[text]
  nextTick(() => inputRef.value?.focus())
}

onUnmounted(() => {
  runtimeStore.disconnectSSE()
})

async function handleSend() {
  const text = inputText.value.trim()
  if (!text) return

  const session = await ensureActiveSession()
  if (!session) return

  await sessionStore.sendMessage(text)
  inputText.value = ''
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
</script>

<template>
  <div class="conversation-workbench">
    <section class="conversation-workbench__main" aria-label="对话工作台">
      <header class="conversation-workbench__header">
        <div>
          <h2>{{ activeTitle }}</h2>
          <p>{{ activeSubtitle }}</p>
        </div>
        <div class="conversation-workbench__header-actions">
          <span
            class="conversation-workbench__socket"
            :class="{ 'conversation-workbench__socket--online': runtimeStore.activeSse !== null }"
          >
            {{ runtimeStore.activeSse ? 'SSE 已连接' : '连接中' }}
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

      <div class="conversation-workbench__chips" aria-label="常用场景">
        <button
          v-for="chip in scenarioChips"
          :key="chip"
          class="conversation-workbench__chip"
          @click="applyScenario(chip)"
        >
          <span></span>
          {{ chip }}
        </button>
      </div>

      <div v-if="skillRefreshStatus" class="conversation-workbench__notice">
        {{ skillRefreshStatus }}
      </div>

      <div v-if="loadingSession && sessionStore.messages.length === 0" class="conversation-workbench__loading">
        正在准备会话...
      </div>
      <template v-else>
        <MessageList :messages="sessionStore.messages" />
        <div v-if="runtimeStore.streamingText" class="conversation-workbench__streaming">
          <span class="conversation-workbench__streaming-text">{{ runtimeStore.streamingText }}</span>
          <span class="conversation-workbench__streaming-cursor">▍</span>
        </div>
      </template>

      <form class="conversation-workbench__input-area" @submit.prevent="handleSend">
        <input
          ref="inputRef"
          v-model="inputText"
          class="conversation-workbench__input"
          type="text"
          placeholder="输入指令或问题..."
        />
        <button
          class="conversation-workbench__send"
          :disabled="!inputText.trim() || loadingSession"
          type="submit"
          aria-label="发送消息"
        >
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
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
}

.conversation-workbench__main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
  margin: 18px 22px;
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
  padding: 18px 22px;
  border-bottom: 1px solid var(--border-color);
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
  background: #94a3b8;
}

.conversation-workbench__socket--online {
  background: #ecfdf5;
  color: #047857;
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

.conversation-workbench__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 16px 22px;
  border-bottom: 1px solid var(--border-color);
}

.conversation-workbench__chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 850;
  cursor: pointer;
}

.conversation-workbench__chip:hover {
  border-color: var(--accent-blue);
  background: rgba(59, 130, 246, 0.06);
  color: var(--accent-blue);
}

.conversation-workbench__chip span {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: #10b981;
  box-shadow: 0 0 0 6px #dff8ee;
}

.conversation-workbench__notice {
  margin: 10px 22px 0;
  padding: 8px 10px;
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 8px;
  background: rgba(59, 130, 246, 0.06);
  color: var(--accent-blue);
  font-size: 12px;
  font-weight: 800;
}

.conversation-workbench__loading {
  display: grid;
  place-items: center;
  flex: 1;
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 750;
}

.conversation-workbench__input-area {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 48px;
  gap: 10px;
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
  color: #94a3b8;
}

.conversation-workbench__send {
  display: grid;
  place-items: center;
  width: 48px;
  height: 46px;
  border: 0;
  border-radius: 10px;
  background: linear-gradient(135deg, #3b82f6, #7c3aed);
  color: #ffffff;
  cursor: pointer;
}

.conversation-workbench__send:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.conversation-workbench__streaming {
  padding: 12px 22px;
  color: var(--text-primary);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.conversation-workbench__streaming-text {
  white-space: pre-wrap;
}

.conversation-workbench__streaming-cursor {
  animation: blink 0.8s step-end infinite;
  color: #3b82f6;
}

@keyframes blink {
  50% { opacity: 0; }
}
</style>
