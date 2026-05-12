import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { projectDataProviderApi } from '../api/projectDataProviders'
import type { ProjectDataProviderDto, StartWorkflowRequest } from '../api/types'

const STORAGE_KEY = 'agentcenter.runtimeSettings'

type StoredRuntimeSettings = {
  autoRunWorkflow?: boolean
  promptDebugPanelEnabled?: boolean
}

export const useRuntimeSettingsStore = defineStore('runtimeSettings', () => {
  const autoRunWorkflow = ref(false)
  const promptDebugPanelEnabled = ref(false)
  const projectDataProviders = ref<ProjectDataProviderDto[]>([])
  const activeProjectDataProviderId = ref('')
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

  async function loadProjectDataProviders() {
    projectDataProviderLoading.value = true
    try {
      const settings = await projectDataProviderApi.settings()
      projectDataProviders.value = settings.providers
      activeProjectDataProviderId.value = settings.activeProviderId
    } finally {
      projectDataProviderLoading.value = false
    }
  }

  async function setProjectDataProvider(providerId: string) {
    if (!providerId || providerId === activeProjectDataProviderId.value) return
    projectDataProviderLoading.value = true
    try {
      const settings = await projectDataProviderApi.setActive({ providerId })
      projectDataProviders.value = settings.providers
      activeProjectDataProviderId.value = settings.activeProviderId
    } finally {
      projectDataProviderLoading.value = false
    }
  }

  function initFromStorage() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) return
      const stored = JSON.parse(raw) as StoredRuntimeSettings
      autoRunWorkflow.value = stored.autoRunWorkflow === true
      promptDebugPanelEnabled.value = stored.promptDebugPanelEnabled === true
    } catch {
      // Storage is optional; keep the safe default.
    }
  }

  function persist() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        autoRunWorkflow: autoRunWorkflow.value,
        promptDebugPanelEnabled: promptDebugPanelEnabled.value,
      }))
    } catch {
      // Storage is optional; the in-memory setting still works for this session.
    }
  }

  return {
    autoRunWorkflow,
    promptDebugPanelEnabled,
    projectDataProviders,
    activeProjectDataProviderId,
    projectDataProviderLoading,
    workflowRunMode,
    setAutoRunWorkflow,
    setPromptDebugPanelEnabled,
    loadProjectDataProviders,
    setProjectDataProvider,
    initFromStorage,
  }
})
