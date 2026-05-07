<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import AppShell from './components/shell/AppShell.vue'
import HomeOverview from './views/HomeOverview.vue'
import BoardView from './views/BoardView.vue'
import WorkflowConfig from './views/WorkflowConfig.vue'
import ConversationWorkbench from './views/ConversationWorkbench.vue'
import RuntimeResources from './views/RuntimeResources.vue'
import ProjectContextSettings from './views/ProjectContextSettings.vue'
import SkillManagement from './views/SkillManagement.vue'
import McpManagement from './views/McpManagement.vue'
import { confirmationApi } from './api/confirmations'
import { workItemApi } from './api/workItems'
import { useSessionStore } from './stores/sessions'
import { useConfirmationStore } from './stores/confirmations'
import { useNotificationStore } from './stores/notifications'
import { useWorkflowStore } from './stores/workflows'
import { useWorkItemStore } from './stores/workItems'
import type { AgentSessionDto, StartWorkflowResponse } from './api/types'
import type { ProjectContextOptions, ProjectContextSelection } from './types/projectContext'

const activeView = ref('home')
const selectedWorkItemId = ref<string | undefined>(undefined)
const targetSessionId = ref<string | null>(null)
const conversationReturnView = ref('home')
const settingsTab = ref<string>('skills')
const projectContextOptions: ProjectContextOptions = {
  projects: ['AgentCenter', 'TianYuan', '平台接入'],
  spaces: ['研发中台', '平台工程', '安全治理'],
  iterations: ['Sprint 14', 'Sprint 15', '长期演进'],
}
const projectContext = ref<ProjectContextSelection>({
  project: 'AgentCenter',
  space: '研发中台',
  iteration: 'Sprint 14',
})
const sessionStore = useSessionStore()
const workflowStore = useWorkflowStore()
const confirmationStore = useConfirmationStore()
const notificationStore = useNotificationStore()
const workItemStore = useWorkItemStore()
const refreshTimerIds = new Set<number>()

onMounted(async () => {
  await refreshWorkItemState()
})

onUnmounted(() => {
  for (const timerId of refreshTimerIds) {
    window.clearTimeout(timerId)
  }
  refreshTimerIds.clear()
})

async function refreshWorkItemState() {
  await workItemStore.loadItems()
  for (const item of workItemStore.items) {
    if (item.workflowSummary) {
      workflowStore.upsertInstance({
        id: item.workflowSummary.instanceId,
        workItemId: item.id,
        workflowDefinitionId: '',
        status: item.workflowSummary.status,
        currentNodeInstanceId: item.workflowSummary.currentNodeInstanceId,
        nodes: item.workflowSummary.nodes.map((n) => ({
          id: n.id,
          nodeDefinitionId: '',
          status: n.status,
          inputArtifactId: null,
          outputArtifactId: null,
          agentSessionId: null,
          startedAt: null,
          completedAt: null,
          errorMessage: null,
        })),
        startedAt: null,
        completedAt: null,
      })
    }
  }
  await confirmationStore.loadPending()
}

async function refreshOneWorkItemState(workItemId: string) {
  const item = await workItemStore.refreshItem(workItemId)
  if (item.workflowSummary) {
    workflowStore.upsertInstance({
      id: item.workflowSummary.instanceId,
      workItemId: item.id,
      workflowDefinitionId: '',
      status: item.workflowSummary.status,
      currentNodeInstanceId: item.workflowSummary.currentNodeInstanceId,
      nodes: item.workflowSummary.nodes.map((n) => ({
        id: n.id,
        nodeDefinitionId: '',
        status: n.status,
        inputArtifactId: null,
        outputArtifactId: null,
        agentSessionId: null,
        startedAt: null,
        completedAt: null,
        errorMessage: null,
      })),
      startedAt: null,
      completedAt: null,
    })
  }
}

function queueWorkflowRefresh(workItemId?: string | null) {
  const delays = [600, 1500, 3000, 5000, 8000, 12000]
  for (const delay of delays) {
    const timerId = window.setTimeout(async () => {
      refreshTimerIds.delete(timerId)
      if (workItemId) {
        await refreshOneWorkItemState(workItemId)
        await confirmationStore.loadPending()
      } else {
        await confirmationStore.loadPending()
      }
    }, delay)
    refreshTimerIds.add(timerId)
  }
}

function handleSelectWorkItem(id: string) {
  selectedWorkItemId.value = id
  targetSessionId.value = null
  // Stay on home — do NOT navigate to conversation
}

const selectedWorkItem = computed(() =>
  workItemStore.items.find(item => item.id === selectedWorkItemId.value) ?? null
)

async function handleStartWorkflow(workItemId: string, response?: StartWorkflowResponse) {
  if (!response) {
    response = await workItemApi.startWorkflow(workItemId, { mode: 'AUTO' })
  }
  selectedWorkItemId.value = workItemId
  targetSessionId.value = response.session?.id ?? null
  if (response.workflowInstance) {
    workflowStore.setActiveInstance(response.workflowInstance)
    workflowStore.upsertInstance(response.workflowInstance)
  }
  if (response.session) {
    sessionStore.upsertSession(response.session)
  }
  await refreshOneWorkItemState(workItemId)
  await confirmationStore.loadPending()
  queueWorkflowRefresh(workItemId)
}

async function handleConfirmationsChanged(workItemId?: string | null) {
  if (workItemId) {
    await refreshOneWorkItemState(workItemId)
  }
  await confirmationStore.loadPending()
  queueWorkflowRefresh(workItemId)
}

function handleEnterWorkItemConversation(id: string) {
  rememberConversationReturnView()
  selectedWorkItemId.value = id
  const session = sessionStore.sessions.find((item) => item.workItemId === id)
  targetSessionId.value = session?.id ?? null
  activeView.value = 'conversation'
}

async function handleConfirmation(confirmationId: string) {
  try {
    rememberConversationReturnView()
    const result = await confirmationApi.enterSession(confirmationId)
    if (result.agentSessionId) {
      targetSessionId.value = result.agentSessionId
    }
    if (result.workItemId) {
      selectedWorkItemId.value = result.workItemId
    }
    activeView.value = 'conversation'
  } catch (e) {
    console.error('Failed to enter confirmation session:', e)
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'error',
      title: '进入确认会话失败',
      message: e instanceof Error ? e.message : '请稍后重试',
      durationMs: 5200,
    })
  }
}

function handleSelectSession(session: AgentSessionDto) {
  rememberConversationReturnView()
  targetSessionId.value = session.id
  selectedWorkItemId.value = session.workItemId || undefined
  activeView.value = 'conversation'
}

async function handleCreateGeneralSession() {
  rememberConversationReturnView()
  const session = await sessionStore.createSession({
    sessionType: 'GENERAL',
    title: '通用会话',
    runtimeType: 'OPENCODE',
  })
  targetSessionId.value = session.id
  selectedWorkItemId.value = undefined
  activeView.value = 'conversation'
}

function handleNavigateSettings(tab: string) {
  settingsTab.value = tab
  activeView.value = 'settings'
}

function rememberConversationReturnView() {
  if (activeView.value !== 'conversation') {
    conversationReturnView.value = activeView.value
  }
}

function handleConversationBack() {
  activeView.value = conversationReturnView.value || 'home'
}
</script>

<template>
  <AppShell
    v-model:activeView="activeView"
    :selected-work-item="selectedWorkItem"
    :project-context="projectContext"
    @handle-confirmation="handleConfirmation"
    @select-session="handleSelectSession"
    @create-general-session="handleCreateGeneralSession"
    @navigate-settings="handleNavigateSettings"
    @start-workflow="handleStartWorkflow"
    @enter-work-item-conversation="handleEnterWorkItemConversation"
    @confirmations-changed="handleConfirmationsChanged"
  >
    <template #center>
      <HomeOverview
        v-if="activeView === 'home'"
        @select-work-item="handleSelectWorkItem"
        @start-workflow="handleStartWorkflow"
      />
      <BoardView
        v-else-if="activeView === 'board'"
        @select-work-item="handleSelectWorkItem"
      />
      <WorkflowConfig v-else-if="activeView === 'workflow'" />
      <RuntimeResources v-else-if="activeView === 'resources'" />
      <ProjectContextSettings
        v-else-if="activeView === 'settings' && settingsTab === 'project'"
        v-model="projectContext"
        :options="projectContextOptions"
      />
      <SkillManagement v-else-if="activeView === 'settings' && settingsTab === 'skills'" />
      <McpManagement v-else-if="activeView === 'settings' && settingsTab === 'mcps'" />
      <ConversationWorkbench
        v-else-if="activeView === 'conversation'"
        :work-item-id="selectedWorkItemId"
        :target-session-id="targetSessionId"
        @back="handleConversationBack"
      />
    </template>
  </AppShell>
</template>
