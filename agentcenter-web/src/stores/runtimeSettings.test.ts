import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useRuntimeSettingsStore } from './runtimeSettings'
import { projectDataProviderApi } from '../api/projectDataProviders'

vi.mock('../api/projectDataProviders', () => ({
  projectDataProviderApi: {
    settings: vi.fn(),
    setActive: vi.fn(),
    setActiveScope: vi.fn(),
  },
}))

const providerSettings = {
  providers: [
    { id: 'fixture-alpha', name: '测试源 A', description: 'A', active: true },
    { id: 'fixture-beta', name: '测试源 B', description: 'B', active: false },
  ],
  activeProviderId: 'fixture-alpha',
  activeExternalProjectId: 'alpha-project-agentcenter',
  activeExternalSpaceId: 'alpha-space-rd',
  activeExternalIterationId: 'alpha-sprint-14',
}

describe('useRuntimeSettingsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads registered project data providers', async () => {
    vi.mocked(projectDataProviderApi.settings).mockResolvedValueOnce(providerSettings)

    const store = useRuntimeSettingsStore()
    await store.loadProjectDataProviders()

    expect(store.projectDataProviders).toEqual(providerSettings.providers)
    expect(store.activeProjectDataProviderId).toBe('fixture-alpha')
    expect(store.activeExternalProjectId).toBe('alpha-project-agentcenter')
  })

  it('switches the active project data provider', async () => {
    vi.mocked(projectDataProviderApi.setActive).mockResolvedValueOnce({
      providers: [
        { id: 'fixture-alpha', name: '测试源 A', description: 'A', active: false },
        { id: 'fixture-beta', name: '测试源 B', description: 'B', active: true },
      ],
      activeProviderId: 'fixture-beta',
      activeExternalProjectId: null,
      activeExternalSpaceId: null,
      activeExternalIterationId: null,
    })

    const store = useRuntimeSettingsStore()
    store.activeProjectDataProviderId = 'fixture-alpha'
    await store.setProjectDataProvider('fixture-beta')

    expect(projectDataProviderApi.setActive).toHaveBeenCalledWith({ providerId: 'fixture-beta' })
    expect(store.activeProjectDataProviderId).toBe('fixture-beta')
    expect(store.projectDataProviders[1].active).toBe(true)
  })

  it('saves the active project data scope', async () => {
    vi.mocked(projectDataProviderApi.setActiveScope).mockResolvedValueOnce({
      providers: providerSettings.providers,
      activeProviderId: 'fixture-alpha',
      activeExternalProjectId: 'alpha-project-platform',
      activeExternalSpaceId: 'alpha-space-platform',
      activeExternalIterationId: 'alpha-sprint-15',
    })

    const store = useRuntimeSettingsStore()
    await store.setProjectDataScope({
      providerId: 'fixture-alpha',
      externalProjectId: 'alpha-project-platform',
      externalSpaceId: 'alpha-space-platform',
      externalIterationId: 'alpha-sprint-15',
    })

    expect(projectDataProviderApi.setActiveScope).toHaveBeenCalledWith({
      providerId: 'fixture-alpha',
      externalProjectId: 'alpha-project-platform',
      externalSpaceId: 'alpha-space-platform',
      externalIterationId: 'alpha-sprint-15',
    })
    expect(store.activeExternalProjectId).toBe('alpha-project-platform')
    expect(store.activeExternalSpaceId).toBe('alpha-space-platform')
    expect(store.activeExternalIterationId).toBe('alpha-sprint-15')
  })

  it('normalizes the batch start workflow limit', () => {
    const store = useRuntimeSettingsStore()

    expect(store.batchStartWorkflowLimit).toBe(5)

    store.setBatchStartWorkflowLimit(0)
    expect(store.batchStartWorkflowLimit).toBe(1)

    store.setBatchStartWorkflowLimit(9.8)
    expect(store.batchStartWorkflowLimit).toBe(9)

    store.setBatchStartWorkflowLimit(100)
    expect(store.batchStartWorkflowLimit).toBe(20)
  })
})
