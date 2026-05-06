<script setup lang="ts">
import { ref } from 'vue'
import AppShell from './components/shell/AppShell.vue'
import HomeOverview from './views/HomeOverview.vue'
import BoardView from './views/BoardView.vue'
import WorkflowConfig from './views/WorkflowConfig.vue'
import ConversationWorkbench from './views/ConversationWorkbench.vue'
import RuntimeResources from './views/RuntimeResources.vue'
import { confirmationApi } from './api/confirmations'
import { useSessionStore } from './stores/sessions'
import { useConfirmationStore } from './stores/confirmations'
import { useWorkflowStore } from './stores/workflows'
import type { AgentSessionDto, StartWorkflowResponse } from './api/types'

const activeView = ref('home')
const selectedWorkItemId = ref<string | undefined>(undefined)
const targetSessionId = ref<string | null>(null)
const sessionStore = useSessionStore()
const workflowStore = useWorkflowStore()
const confirmationStore = useConfirmationStore()

function handleSelectWorkItem(id: string) {
  selectedWorkItemId.value = id
  targetSessionId.value = null
  activeView.value = 'conversation'
}

async function handleStartWorkflow(workItemId: string, response: StartWorkflowResponse) {
  selectedWorkItemId.value = workItemId
  targetSessionId.value = response.session?.id ?? null
  workflowStore.setActiveInstance(response.workflowInstance)
  if (response.session) {
    sessionStore.upsertSession(response.session)
  }
  await confirmationStore.loadPending()
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
</script>

<template>
  <AppShell
    v-model:activeView="activeView"
    @handle-confirmation="handleConfirmation"
    @select-session="handleSelectSession"
    @create-general-session="handleCreateGeneralSession"
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
      <ConversationWorkbench
        v-else-if="activeView === 'conversation'"
        :work-item-id="selectedWorkItemId"
        :target-session-id="targetSessionId"
      />
    </template>
  </AppShell>
</template>
