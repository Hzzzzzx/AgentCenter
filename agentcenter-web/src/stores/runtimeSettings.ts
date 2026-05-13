import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { projectDataProviderApi } from '../api/projectDataProviders'
import type {
  ProjectDataProviderDto,
  ProjectDataProviderSettingsDto,
  StartWorkflowRequest,
  UpdateProjectDataScopeRequest,
} from '../api/types'

const STORAGE_KEY = 'agentcenter.runtimeSettings'
const DEFAULT_BATCH_START_WORKFLOW_LIMIT = 5
const MIN_BATCH_START_WORKFLOW_LIMIT = 1
const MAX_BATCH_START_WORKFLOW_LIMIT = 20

type StoredRuntimeSettings = {
  autoRunWorkflow?: boolean
  promptDebugPanelEnabled?: boolean
  batchStartWorkflowLimit?: number
}

export const useRuntimeSettingsStore = defineStore('runtimeSettings', () => {
  const autoRunWorkflow = ref(false)
  const promptDebugPanelEnabled = ref(false)
  const batchStartWorkflowLimit = ref(DEFAULT_BATCH_START_WORKFLOW_LIMIT)
  const projectDataProviders = ref<ProjectDataProviderDto[]>([])
  const activeProjectDataProviderId = ref('')
  const activeExternalProjectId = ref<string | null>(null)
  const activeExternalSpaceId = ref<string | null>(null)
  const activeExternalIterationId = ref<string | null>(null)
  const projectDataProviderLoading = ref(false)

  const workflowRunMode = computed<NonNullable<StartWorkflowRequest['mode']>>(() =>
    autoRunWorkflow.value ? 'AUTO' : 'MANUAL_CONFIRM'
  )

  function setAutoRunWorkflow(value: boolean) {
    autoRunWorkflow.value = value
    persist()
  }

  function setPromptDebugPanelEnabled(value: boolean) {
    promptDebugPanelEnabled.value = value
    persist()
  }

  function setBatchStartWorkflowLimit(value: number) {
    batchStartWorkflowLimit.value = normalizeBatchStartWorkflowLimit(value)
    persist()
  }

  async function loadProjectDataProviders() {
    projectDataProviderLoading.value = true
    try {
      const settings = await projectDataProviderApi.settings()
      applyProjectDataProviderSettings(settings)
    } finally {
      projectDataProviderLoading.value = false
    }
  }

  async function setProjectDataProvider(providerId: string) {
    if (!providerId || providerId === activeProjectDataProviderId.value) return
    projectDataProviderLoading.value = true
    try {
      const settings = await projectDataProviderApi.setActive({ providerId })
      applyProjectDataProviderSettings(settings)
    } finally {
      projectDataProviderLoading.value = false
    }
  }

  async function setProjectDataScope(scope: UpdateProjectDataScopeRequest) {
    projectDataProviderLoading.value = true
    try {
      const settings = await projectDataProviderApi.setActiveScope(scope)
      applyProjectDataProviderSettings(settings)
    } finally {
      projectDataProviderLoading.value = false
    }
  }

  function applyProjectDataProviderSettings(settings: ProjectDataProviderSettingsDto) {
    projectDataProviders.value = settings.providers
    activeProjectDataProviderId.value = settings.activeProviderId
    activeExternalProjectId.value = settings.activeExternalProjectId ?? null
    activeExternalSpaceId.value = settings.activeExternalSpaceId ?? null
    activeExternalIterationId.value = settings.activeExternalIterationId ?? null
  }

  function initFromStorage() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) return
      const stored = JSON.parse(raw) as StoredRuntimeSettings
      autoRunWorkflow.value = stored.autoRunWorkflow === true
      promptDebugPanelEnabled.value = stored.promptDebugPanelEnabled === true
      batchStartWorkflowLimit.value = normalizeBatchStartWorkflowLimit(stored.batchStartWorkflowLimit)
    } catch {
      // Storage is optional; keep the safe default.
    }
  }

  function normalizeBatchStartWorkflowLimit(value: unknown): number {
    const numeric = typeof value === 'number' ? value : Number(value)
    if (!Number.isFinite(numeric)) return DEFAULT_BATCH_START_WORKFLOW_LIMIT
    const integer = Math.trunc(numeric)
    if (integer < MIN_BATCH_START_WORKFLOW_LIMIT) return MIN_BATCH_START_WORKFLOW_LIMIT
    if (integer > MAX_BATCH_START_WORKFLOW_LIMIT) return MAX_BATCH_START_WORKFLOW_LIMIT
    return integer
  }

  function persist() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        autoRunWorkflow: autoRunWorkflow.value,
        promptDebugPanelEnabled: promptDebugPanelEnabled.value,
        batchStartWorkflowLimit: batchStartWorkflowLimit.value,
      }))
    } catch {
      // Storage is optional; the in-memory setting still works for this session.
    }
  }

  return {
    autoRunWorkflow,
    promptDebugPanelEnabled,
    batchStartWorkflowLimit,
    projectDataProviders,
    activeProjectDataProviderId,
    activeExternalProjectId,
    activeExternalSpaceId,
    activeExternalIterationId,
    projectDataProviderLoading,
    workflowRunMode,
    setAutoRunWorkflow,
    setPromptDebugPanelEnabled,
    setBatchStartWorkflowLimit,
    loadProjectDataProviders,
    setProjectDataProvider,
    setProjectDataScope,
    applyProjectDataProviderSettings,
    initFromStorage,
  }
})
