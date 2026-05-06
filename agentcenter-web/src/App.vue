<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import AppShell from './components/shell/AppShell.vue'
import HomeOverview from './views/HomeOverview.vue'
import BoardView from './views/BoardView.vue'
import WorkflowConfig from './views/WorkflowConfig.vue'
import ConversationWorkbench from './views/ConversationWorkbench.vue'
import RuntimeResources from './views/RuntimeResources.vue'
import SkillManagement from './views/SkillManagement.vue'
import McpManagement from './views/McpManagement.vue'
import { confirmationApi } from './api/confirmations'
import { workItemApi } from './api/workItems'
import { useSessionStore } from './stores/sessions'
import { useConfirmationStore } from './stores/confirmations'
import { useWorkflowStore } from './stores/workflows'
import { useWorkItemStore } from './stores/workItems'
import type { AgentSessionDto, StartWorkflowResponse } from './api/types'

const activeView = ref('home')
const selectedWorkItemId = ref<string | undefined>(undefined)
const targetSessionId = ref<string | null>(null)
const settingsTab = ref<string>('skills')
const sessionStore = useSessionStore()
const workflowStore = useWorkflowStore()
const confirmationStore = useConfirmationStore()
const workItemStore = useWorkItemStore()

onMounted(async () => {
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
})

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
  await workItemStore.loadItems()
  await confirmationStore.loadPending()
}

function handleEnterWorkItemConversation(id: string) {
  selectedWorkItemId.value = id
  const session = sessionStore.sessions.find((item) => item.workItemId === id)
  targetSessionId.value = session?.id ?? null
  activeView.value = 'conversation'
}

async function handleConfirmation(confirmationId: string) {
  try {
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
  }
}

function handleSelectSession(session: AgentSessionDto) {
  targetSessionId.value = session.id
  selectedWorkItemId.value = session.workItemId || undefined
  activeView.value = 'conversation'
}

async function handleCreateGeneralSession() {
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
</script>

<template>
  <AppShell
    v-model:activeView="activeView"
    :selected-work-item="selectedWorkItem"
    @handle-confirmation="handleConfirmation"
    @select-session="handleSelectSession"
    @create-general-session="handleCreateGeneralSession"
    @navigate-settings="handleNavigateSettings"
    @start-workflow="handleStartWorkflow"
    @enter-work-item-conversation="handleEnterWorkItemConversation"
  >
    <template #center>
      <HomeOverview
        v-if="activeView === 'home'"
        @select-work-item="handleSelectWorkItem"
        @start-workflow="handleStartWorkflow"
      />
      <BoardView v-else-if="activeView === 'board'" />
      <WorkflowConfig v-else-if="activeView === 'workflow'" />
      <RuntimeResources v-else-if="activeView === 'resources'" />
      <SkillManagement v-else-if="activeView === 'settings' && settingsTab === 'skills'" />
      <McpManagement v-else-if="activeView === 'settings' && settingsTab === 'mcps'" />
      <ConversationWorkbench
        v-else-if="activeView === 'conversation'"
        :work-item-id="selectedWorkItemId"
        :target-session-id="targetSessionId"
      />
    </template>
  </AppShell>
</template>
