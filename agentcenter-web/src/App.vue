<script setup lang="ts">
import { ref, computed, reactive, onMounted, onUnmounted, watch } from 'vue'
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
import { projectDataProviderApi } from './api/projectDataProviders'
import { useSessionStore } from './stores/sessions'
import { useConfirmationStore } from './stores/confirmations'
import { useNotificationStore } from './stores/notifications'
import { useWorkflowStore } from './stores/workflows'
import { useWorkItemStore } from './stores/workItems'
import { useRuntimeSettingsStore } from './stores/runtimeSettings'
import { useWorkItemWorkflowProjectionStore } from './stores/workItemWorkflowProjection'
import { DEFAULT_PROJECT_ID } from './constants/projects'
import type { AgentSessionDto, ArtifactDto, ProjectDataSnapshotDto, StartWorkflowResponse, WorkItemDto, WorkItemType } from './api/types'
import type { ProjectContextOptions, ProjectContextSelection } from './types/projectContext'

type BatchStartWorkflowsPayload = {
  workItemType: WorkItemType
  workItemIds: string[]
}

const activeView = ref('home')
const selectedWorkItemId = ref<string | undefined>(undefined)
const targetSessionId = ref<string | null>(null)
const selectedArtifact = ref<ArtifactDto | null>(null)
const conversationReturnView = ref('home')
const settingsTab = ref<string>('skills')
const appShellRef = ref<InstanceType<typeof AppShell> | null>(null)
const runtimeSettingsStore = useRuntimeSettingsStore()
const emptyProjectContext: ProjectContextSelection = {
  id: '',
  project: '',
  cloudeReqProject: '',
  space: '',
  iteration: '',
}
const projectContextOptions = reactive<ProjectContextOptions>({
  cloudeReqProjects: [],
  spaces: [],
  iterations: [],
})
const projectContexts = ref<ProjectContextSelection[]>([])
const activeProjectContextId = ref(projectContexts.value[0]?.id ?? '')
const projectContextSyncing = ref(false)
const projectContextSaving = ref(false)
const projectContext = computed<ProjectContextSelection>({
  get: () => (
    projectContexts.value.find((item) => item.id === activeProjectContextId.value)
    ?? projectContexts.value[0]
    ?? pendingProjectContext.value
    ?? emptyProjectContext
  ),
  set: (value) => {
    if (!value.id) return
    const existingIndex = projectContexts.value.findIndex((item) => item.id === value.id)
    if (existingIndex >= 0) {
      projectContexts.value.splice(existingIndex, 1, value)
    } else {
      projectContexts.value.push(value)
    }
    activeProjectContextId.value = value.id
  },
})
const pendingProjectContext = computed<ProjectContextSelection | null>(() => {
  const externalProjectId = runtimeSettingsStore.activeExternalProjectId
  const externalSpaceId = runtimeSettingsStore.activeExternalSpaceId
  const externalIterationId = runtimeSettingsStore.activeExternalIterationId
  if (!externalProjectId && !externalSpaceId && !externalIterationId) return null
  return {
    id: '',
    externalProjectId,
    project: runtimeSettingsStore.activeProjectName || externalProjectId || '',
    externalCloudeReqProjectId: externalProjectId,
    cloudeReqProject: externalProjectId || '',
    externalSpaceId,
    space: externalSpaceId || '',
    externalIterationId,
    iteration: externalIterationId || '',
  }
})
const activeProjectId = computed(() => scopeProjectIdFor(projectContext.value) || DEFAULT_PROJECT_ID)
const activeIterationOptions = computed(() => {
  const context = projectContext.value
  const scopedIterations = projectContexts.value
    .filter((item) => item.project === context.project && item.space === context.space)
    .map((item) => item.iteration)
  return unique(scopedIterations).length > 0 ? unique(scopedIterations) : projectContextOptions.iterations
})
const shellProjectContextOptions = computed<ProjectContextOptions>(() => ({
  cloudeReqProjects: projectContextOptions.cloudeReqProjects,
  spaces: projectContextOptions.spaces,
  iterations: activeIterationOptions.value,
}))
const sessionStore = useSessionStore()
const workflowStore = useWorkflowStore()
const confirmationStore = useConfirmationStore()
const notificationStore = useNotificationStore()
const workItemStore = useWorkItemStore()
const workflowProjectionStore = useWorkItemWorkflowProjectionStore()
const refreshTimerIds = new Set<number>()
const workflowWatchTimerByWorkItemId = new Map<string, number>()
const workflowWatchAttemptsByWorkItemId = new Map<string, number>()
const workflowRefreshDelaysMs = [600, 1500, 3000, 5000, 8000, 12000]
const workflowWatchDelayMs = 15000
const workflowWatchIntervalMs = 15000
const workflowWatchMaxAttempts = 80

onMounted(async () => {
  runtimeSettingsStore.initFromStorage()
  try {
    await runtimeSettingsStore.loadProjectDataProviders()
    await loadProjectDataSnapshot(true)
  } catch (e) {
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'error',
      title: '项目数据加载失败',
      message: e instanceof Error ? e.message : '请检查 Bridge 项目数据同步接口',
      durationMs: 5200,
    })
  }
  await refreshWorkItemState()
})

onUnmounted(() => {
  for (const timerId of refreshTimerIds) {
    window.clearTimeout(timerId)
  }
  refreshTimerIds.clear()
  workflowWatchTimerByWorkItemId.clear()
  workflowWatchAttemptsByWorkItemId.clear()
})

async function refreshWorkItemState() {
  workItemStore.setScope(scopeForProjectContext())
  await workItemStore.loadItems()
  await workItemStore.loadOverview()
  workflowProjectionStore.syncWorkItemsFromList()
  await confirmationStore.loadPending()
}

async function refreshOneWorkItemState(workItemId: string): Promise<WorkItemDto> {
  workItemStore.setScope(scopeForProjectContext())
  const item = await workflowProjectionStore.syncWorkItem(workItemId)
  await workItemStore.loadOverview()
  return item
}

function scopeForProjectContext() {
  const context = projectContext.value
  return {
    providerId: runtimeSettingsStore.activeProjectDataProviderId || null,
    projectId: scopeProjectIdFor(context),
    spaceId: firstNonBlank(context.externalSpaceId, context.space),
    iterationId: firstNonBlank(context.externalIterationId, context.iteration),
  }
}

function scopeProjectIdFor(context: ProjectContextSelection) {
  const providerId = runtimeSettingsStore.activeProjectDataProviderId
  const externalProjectId = firstNonBlank(context.externalProjectId, context.project)
  if (!providerId || !externalProjectId) return externalProjectId
  return `${providerId}:${externalProjectId}`
}

function firstNonBlank(...values: Array<string | null | undefined>) {
  return values.find((value) => value && value.trim().length > 0)?.trim() ?? null
}

function unique(values: string[]) {
  return Array.from(new Set(values.filter(Boolean)))
}

function queueWorkflowRefresh(workItemId?: string | null) {
  for (const delay of workflowRefreshDelaysMs) {
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
  if (workItemId) {
    startWorkflowWatch(workItemId)
  }
}

function startWorkflowWatch(workItemId: string) {
  clearWorkflowWatch(workItemId)
  workflowWatchAttemptsByWorkItemId.set(workItemId, 0)
  scheduleWorkflowWatch(workItemId, workflowWatchDelayMs)
}

function scheduleWorkflowWatch(workItemId: string, delayMs: number) {
  const timerId = window.setTimeout(async () => {
    refreshTimerIds.delete(timerId)
    workflowWatchTimerByWorkItemId.delete(workItemId)

    const attempts = workflowWatchAttemptsByWorkItemId.get(workItemId) ?? 0
    const shouldContinue = await refreshWorkflowAndShouldContinue(workItemId)
    if (!shouldContinue) {
      workflowWatchAttemptsByWorkItemId.delete(workItemId)
      return
    }

    const nextAttempts = attempts + 1
    if (nextAttempts >= workflowWatchMaxAttempts) {
      workflowWatchAttemptsByWorkItemId.delete(workItemId)
      return
    }

    workflowWatchAttemptsByWorkItemId.set(workItemId, nextAttempts)
    scheduleWorkflowWatch(workItemId, workflowWatchIntervalMs)
  }, delayMs)

  workflowWatchTimerByWorkItemId.set(workItemId, timerId)
  refreshTimerIds.add(timerId)
}

function clearWorkflowWatch(workItemId: string) {
  const timerId = workflowWatchTimerByWorkItemId.get(workItemId)
  if (timerId !== undefined) {
    window.clearTimeout(timerId)
    refreshTimerIds.delete(timerId)
  }
  workflowWatchTimerByWorkItemId.delete(workItemId)
}

async function refreshWorkflowAndShouldContinue(workItemId: string): Promise<boolean> {
  try {
    const item = await refreshOneWorkItemState(workItemId)
    await confirmationStore.loadPending()
    return shouldContinueWorkflowWatch(item)
  } catch (error) {
    console.error('Failed to refresh running workflow state:', error)
    return true
  }
}

function shouldContinueWorkflowWatch(item: WorkItemDto): boolean {
  const projection = workflowProjectionStore.projectionFor(item)
  const workflowStatus = projection.workflowInstance?.status ?? item.workflowSummary?.status ?? null
  if (workflowStatus === 'RUNNING') {
    return true
  }
  return projection.commandState === 'RUNNING'
    || projection.commandState === 'STARTING'
    || projection.nodes.some((node) => node.status === 'RUNNING')
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

async function handleStartWorkflows(payload: BatchStartWorkflowsPayload) {
  selectedArtifact.value = null
  try {
    const result = await workflowProjectionStore.startWorkflowsBatch(payload.workItemType, payload.workItemIds)
    const startedWorkItemIds: string[] = []
    for (const item of result.results) {
      if (item.response?.workflowInstance) {
        workflowStore.upsertInstance(item.response.workflowInstance)
        startedWorkItemIds.push(item.workItemId)
      }
      if (item.response?.session) {
        sessionStore.upsertSession(item.response.session)
      }
    }
    await workItemStore.loadOverview()
    await confirmationStore.loadPending()
    startedWorkItemIds.forEach((workItemId) => queueWorkflowRefresh(workItemId))
    notificationStore.push({
      anchor: 'right-panel',
      tone: result.failedCount > 0 ? 'warning' : 'success',
      title: `${payload.workItemType} 批量启动完成`,
      message: `已启动 ${result.startedCount} 个，跳过 ${result.skippedCount} 个，失败 ${result.failedCount} 个。`,
      durationMs: 5200,
    })
  } catch (e) {
    console.error('Failed to batch start workflows:', e)
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'error',
      title: '批量启动失败',
      message: e instanceof Error ? e.message : '请稍后重试',
      durationMs: 5200,
    })
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

function handleSyncProjectContextData() {
  if (projectContextSyncing.value) return
  projectContextSyncing.value = true
  loadProjectDataSnapshot(true)
    .then(refreshWorkItemState)
    .then(() => {
      notificationStore.push({
        anchor: 'right-panel',
        tone: 'success',
        title: '同步完成',
        message: '已按当前同步源刷新项目、空间、迭代、工作项与首页节点统计。',
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

async function loadProjectDataSnapshot(sync: boolean) {
  const snapshot = sync
    ? await projectDataProviderApi.sync()
    : await projectDataProviderApi.snapshot()
  if (sync) {
    await runtimeSettingsStore.loadProjectDataProviders()
  }
  applyProjectDataSnapshot(snapshot)
}

function applyProjectDataSnapshot(snapshot: ProjectDataSnapshotDto) {
  projectContextOptions.cloudeReqProjects = snapshot.options.cloudeReqProjects
  projectContextOptions.spaces = snapshot.options.spaces
  projectContextOptions.iterations = snapshot.options.iterations

  projectContexts.value = snapshot.contexts.map((context) => ({
    id: context.id,
    externalProjectId: context.externalProjectId,
    project: context.externalProjectId === runtimeSettingsStore.activeExternalProjectId
      ? runtimeSettingsStore.activeProjectName || context.project
      : context.project,
    externalCloudeReqProjectId: context.externalCloudeReqProjectId,
    cloudeReqProject: context.cloudeReqProject,
    externalSpaceId: context.externalSpaceId,
    space: context.space,
    externalIterationId: context.externalIterationId,
    iteration: context.iteration,
    iterationStatus: context.iterationStatus,
    iterationStartAt: context.iterationStartAt,
    iterationEndAt: context.iterationEndAt,
    active: context.active,
    extraJson: context.extraJson,
  }))
  activeProjectContextId.value = projectContexts.value.find(matchesSavedProjectScope)?.id
    ?? projectContexts.value.find((context) => context.active)?.id
    ?? projectContexts.value.find((context) => context.id === activeProjectContextId.value)?.id
    ?? projectContexts.value[0]?.id
    ?? ''
}

function matchesSavedProjectScope(context: ProjectContextSelection) {
  const externalProjectId = runtimeSettingsStore.activeExternalProjectId
  if (!externalProjectId || context.externalProjectId !== externalProjectId) return false
  const externalSpaceId = runtimeSettingsStore.activeExternalSpaceId
  if (externalSpaceId && context.externalSpaceId !== externalSpaceId) return false
  const externalIterationId = runtimeSettingsStore.activeExternalIterationId
  if (externalIterationId && context.externalIterationId !== externalIterationId) return false
  return true
}

async function handleSaveProjectContext(context: ProjectContextSelection) {
  if (projectContextSaving.value) return
  projectContextSaving.value = true
  try {
    await runtimeSettingsStore.setProjectDataScope({
      providerId: runtimeSettingsStore.activeProjectDataProviderId || null,
      projectName: firstNonBlank(context.project),
      projectId: scopeProjectIdFor(context),
      spaceId: firstNonBlank(context.externalSpaceId, context.space),
      iterationId: firstNonBlank(context.externalIterationId, context.iteration),
      externalProjectId: firstNonBlank(context.externalProjectId, context.project),
      externalSpaceId: firstNonBlank(context.externalSpaceId, context.space),
      externalIterationId: firstNonBlank(context.externalIterationId, context.iteration),
    })
    if (context.id) {
      projectContext.value = context
    }
    await refreshWorkItemState()
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'success',
      title: '项目选择已保存',
      message: '已按企业同步源的稳定 ID 更新当前项目、空间和迭代。',
      durationMs: 3200,
    })
  } catch (e) {
    notificationStore.push({
      anchor: 'right-panel',
      tone: 'error',
      title: '保存项目选择失败',
      message: e instanceof Error ? e.message : '请先同步企业项目数据后重试',
      durationMs: 5200,
    })
  } finally {
    projectContextSaving.value = false
  }
}

function handleTitleProjectContextUpdate(value: ProjectContextSelection) {
  const matched = projectContexts.value.find((context) =>
    context.project === value.project
    && context.space === value.space
    && context.iteration === value.iteration
  )
  if (matched) {
    void handleSaveProjectContext(matched)
  }
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

watch(
  () => runtimeSettingsStore.activeProjectDataProviderId,
  async (nextProviderId, previousProviderId) => {
    if (!nextProviderId || !previousProviderId || nextProviderId === previousProviderId) return
    projectContextSyncing.value = true
    try {
      await loadProjectDataSnapshot(true)
      await refreshWorkItemState()
      notificationStore.push({
        anchor: 'right-panel',
        tone: 'success',
        title: '同步源已切换',
        message: '项目、空间、迭代和 FE/US 等工作项数据已按新的同步源刷新。',
        durationMs: 3600,
      })
    } catch (e) {
      notificationStore.push({
        anchor: 'right-panel',
        tone: 'error',
        title: '同步源切换失败',
        message: e instanceof Error ? e.message : '请稍后重试',
        durationMs: 5200,
      })
    } finally {
      projectContextSyncing.value = false
    }
  }
)

</script>

<template>
  <AppShell
    ref="appShellRef"
    v-model:activeView="activeView"
    :selected-work-item="selectedWorkItem"
    :selected-artifact="selectedArtifact"
    :project-context="projectContext"
    :project-context-options="shellProjectContextOptions"
    @handle-confirmation="handleConfirmation"
    @select-session="handleSelectSession"
    @create-general-session="handleCreateGeneralSession"
    @navigate-settings="handleNavigateSettings"
    @start-workflow="handleStartWorkflow"
    @enter-work-item-conversation="handleEnterWorkItemConversation"
    @confirmations-changed="handleConfirmationsChanged"
    @update-project-context="handleTitleProjectContextUpdate"
    @close-artifact="selectedArtifact = null"
  >
    <template #center>
      <HomeOverview
        v-if="activeView === 'home'"
        @select-work-item="handleSelectWorkItem"
        @start-workflow="handleStartWorkflow"
        @start-workflows="handleStartWorkflows"
        @enter-conversation="handleEnterWorkItemConversation"
      />
      <BoardView
        v-else-if="activeView === 'board'"
        @select-work-item="handleSelectWorkItem"
      />
      <WorkflowConfig v-else-if="activeView === 'workflow'" :project-id="activeProjectId" />
      <RuntimeResources v-else-if="activeView === 'resources'" :project-id="activeProjectId" />
      <ProjectContextSettings
        v-else-if="activeView === 'settings' && settingsTab === 'project'"
        v-model="projectContext"
        :contexts="projectContexts"
        :active-context-id="activeProjectContextId"
        :options="projectContextOptions"
        :syncing="projectContextSyncing"
        :saving="projectContextSaving"
        @update:active-context-id="activeProjectContextId = $event"
        @save-selection="handleSaveProjectContext"
        @sync-data="handleSyncProjectContextData"
      />
      <SkillManagement v-else-if="activeView === 'settings' && settingsTab === 'skills'" :project-id="activeProjectId" />
      <McpManagement v-else-if="activeView === 'settings' && settingsTab === 'mcps'" :project-id="activeProjectId" />
      <RuntimeSettings v-else-if="activeView === 'settings' && settingsTab === 'runtime'" />
      <ConversationWorkbench
        v-else-if="activeView === 'conversation'"
        :work-item-id="selectedWorkItemId"
        :target-session-id="targetSessionId"
        :project-id="activeProjectId"
        :selected-artifact-id="selectedArtifact?.id ?? null"
        @back="handleConversationBack"
        @open-artifact="selectedArtifact = $event"
        @show-confirmation="handleShowConfirmation"
      />
    </template>
  </AppShell>
</template>
