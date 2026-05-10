<script setup lang="ts">
import { ref, computed, reactive, onMounted, onUnmounted } from 'vue'
import AppShell from './components/shell/AppShell.vue'
import HomeOverview from './views/HomeOverview.vue'
import BoardView from './views/BoardView.vue'
import WorkflowConfig from './views/WorkflowConfig.vue'
import ConversationWorkbench from './views/ConversationWorkbench.vue'
import RuntimeResources from './views/RuntimeResources.vue'
import ProjectContextSettings from './views/ProjectContextSettings.vue'
import SkillManagement from './views/SkillManagement.vue'
import McpManagement from './views/McpManagement.vue'
import RuntimeSettings from './views/RuntimeSettings.vue'
import { confirmationApi } from './api/confirmations'
import { useSessionStore } from './stores/sessions'
import { useConfirmationStore } from './stores/confirmations'
import { useNotificationStore } from './stores/notifications'
import { useWorkflowStore } from './stores/workflows'
import { useWorkItemStore } from './stores/workItems'
import { useRuntimeSettingsStore } from './stores/runtimeSettings'
import { useWorkItemWorkflowProjectionStore } from './stores/workItemWorkflowProjection'
import type { AgentSessionDto, ArtifactDto, StartWorkflowResponse } from './api/types'
import type { ProjectContextOptions, ProjectContextSelection } from './types/projectContext'

const activeView = ref('home')
const selectedWorkItemId = ref<string | undefined>(undefined)
const targetSessionId = ref<string | null>(null)
const selectedArtifact = ref<ArtifactDto | null>(null)
const conversationReturnView = ref('home')
const settingsTab = ref<string>('skills')
const appShellRef = ref<InstanceType<typeof AppShell> | null>(null)
const projectContextOptions = reactive<ProjectContextOptions>({
  cloudeReqProjects: ['CloudeReq 需求平台', 'CloudeReq 研发项目', 'CloudeReq 交付空间'],
  spaces: ['研发中台', '平台工程', '安全治理'],
  iterations: ['Sprint 14', 'Sprint 15', '长期演进'],
})
const projectContexts = ref<ProjectContextSelection[]>([
  {
    id: 'ctx-agentcenter',
    project: 'AgentCenter',
    cloudeReqProject: 'CloudeReq 研发项目',
    space: '研发中台',
    iteration: 'Sprint 14',
  },
  {
    id: 'ctx-platform',
    project: '平台接入',
    cloudeReqProject: 'CloudeReq 交付空间',
    space: '平台工程',
    iteration: 'Sprint 15',
  },
])
const activeProjectContextId = ref(projectContexts.value[0]?.id ?? '')
const projectContextSyncing = ref(false)
const projectContext = computed<ProjectContextSelection>({
  get: () => (
    projectContexts.value.find((item) => item.id === activeProjectContextId.value)
    ?? projectContexts.value[0]
  ),
  set: (value) => {
    const existingIndex = projectContexts.value.findIndex((item) => item.id === value.id)
    if (existingIndex >= 0) {
      projectContexts.value.splice(existingIndex, 1, value)
    } else {
      projectContexts.value.push(value)
    }
    activeProjectContextId.value = value.id
  },
})
const sessionStore = useSessionStore()
const workflowStore = useWorkflowStore()
const confirmationStore = useConfirmationStore()
const notificationStore = useNotificationStore()
const workItemStore = useWorkItemStore()
const runtimeSettingsStore = useRuntimeSettingsStore()
const workflowProjectionStore = useWorkItemWorkflowProjectionStore()
const refreshTimerIds = new Set<number>()

onMounted(async () => {
  runtimeSettingsStore.initFromStorage()
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
  await workItemStore.loadOverview()
  workflowProjectionStore.syncWorkItemsFromList()
  await confirmationStore.loadPending()
}

async function refreshOneWorkItemState(workItemId: string) {
  await workflowProjectionStore.syncWorkItem(workItemId)
  await workItemStore.loadOverview()
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
  selectedArtifact.value = null
  targetSessionId.value = null
  // Stay on home — do NOT navigate to conversation
}

const selectedWorkItem = computed(() =>
  workItemStore.items.find(item => item.id === selectedWorkItemId.value) ?? null
)

async function handleStartWorkflow(workItemId: string, response?: StartWorkflowResponse) {
  selectedArtifact.value = null
  try {
    response = await workflowProjectionStore.startWorkflow(workItemId, response)
    selectedWorkItemId.value = workItemId
    targetSessionId.value = response.session?.id ?? null
    if (response.workflowInstance) {
      workflowStore.setActiveInstance(response.workflowInstance)
      workflowStore.upsertInstance(response.workflowInstance)
    }
    if (response.session) {
      sessionStore.upsertSession(response.session)
    }
    await workItemStore.loadOverview()
    await confirmationStore.loadPending()
    queueWorkflowRefresh(workItemId)
  } catch (e) {
    console.error('Failed to start workflow:', e)
  }
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
  selectedArtifact.value = null
  targetSessionId.value = null
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
  selectedArtifact.value = null
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

function handleProjectContextsUpdate(nextContexts: ProjectContextSelection[]) {
  projectContexts.value = nextContexts
  if (!nextContexts.some((item) => item.id === activeProjectContextId.value)) {
    activeProjectContextId.value = nextContexts[0]?.id ?? ''
  }
}

function handleSyncProjectContextData() {
  if (projectContextSyncing.value) return
  projectContextSyncing.value = true
  refreshWorkItemState()
    .then(() => {
      notificationStore.push({
        anchor: 'right-panel',
        tone: 'success',
        title: '同步完成',
        message: '已从数据库刷新工作项、待确认与首页节点统计。',
        durationMs: 3600,
      })
    })
    .catch((e) => {
      notificationStore.push({
        anchor: 'right-panel',
        tone: 'error',
        title: '同步失败',
        message: e instanceof Error ? e.message : '请稍后重试',
        durationMs: 5200,
      })
    })
    .finally(() => {
      projectContextSyncing.value = false
    })
}

function rememberConversationReturnView() {
  if (activeView.value !== 'conversation') {
    conversationReturnView.value = activeView.value
  }
}

function handleConversationBack() {
  activeView.value = conversationReturnView.value || 'home'
}

async function handleShowConfirmation(confirmationId: string) {
  try {
    await confirmationStore.selectConfirmation(confirmationId)
    appShellRef.value?.expandRightPanel()
  } catch (e) {
    console.error('Failed to select confirmation:', e)
  }
}
</script>

<template>
  <AppShell
    ref="appShellRef"
    v-model:activeView="activeView"
    :selected-work-item="selectedWorkItem"
    :selected-artifact="selectedArtifact"
    :project-context="projectContext"
    :project-context-options="projectContextOptions"
    @handle-confirmation="handleConfirmation"
    @select-session="handleSelectSession"
    @create-general-session="handleCreateGeneralSession"
    @navigate-settings="handleNavigateSettings"
    @start-workflow="handleStartWorkflow"
    @enter-work-item-conversation="handleEnterWorkItemConversation"
    @confirmations-changed="handleConfirmationsChanged"
    @update-project-context="projectContext = $event"
    @close-artifact="selectedArtifact = null"
  >
    <template #center>
      <HomeOverview
        v-if="activeView === 'home'"
        @select-work-item="handleSelectWorkItem"
        @start-workflow="handleStartWorkflow"
        @enter-conversation="handleEnterWorkItemConversation"
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
        :contexts="projectContexts"
        :active-context-id="activeProjectContextId"
        :options="projectContextOptions"
        :syncing="projectContextSyncing"
        @update:contexts="handleProjectContextsUpdate"
        @update:active-context-id="activeProjectContextId = $event"
        @sync-data="handleSyncProjectContextData"
      />
      <SkillManagement v-else-if="activeView === 'settings' && settingsTab === 'skills'" />
      <McpManagement v-else-if="activeView === 'settings' && settingsTab === 'mcps'" />
      <RuntimeSettings v-else-if="activeView === 'settings' && settingsTab === 'runtime'" />
      <ConversationWorkbench
        v-else-if="activeView === 'conversation'"
        :work-item-id="selectedWorkItemId"
        :target-session-id="targetSessionId"
        @back="handleConversationBack"
        @open-artifact="selectedArtifact = $event"
        @show-confirmation="handleShowConfirmation"
      />
    </template>
  </AppShell>
</template>
