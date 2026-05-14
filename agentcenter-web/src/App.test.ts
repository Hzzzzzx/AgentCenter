import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import App from './App.vue'
import type { WorkItemDto } from './api/types'

const mocks = vi.hoisted(() => {
  const sessionStore = {
    sessions: [],
    activeSession: null,
    messages: [],
    loading: false,
    loadSessions: vi.fn(),
    selectSession: vi.fn(),
    createSession: vi.fn(),
    sendMessage: vi.fn(),
    upsertSession: vi.fn(),
  }
  const confirmationStore = {
    pendingConfirmations: [],
    currentConfirmation: null,
    loading: false,
    loadPending: vi.fn().mockResolvedValue(undefined),
    selectConfirmation: vi.fn(),
    resolveConfirmation: vi.fn(),
    rejectConfirmation: vi.fn(),
  }
  const workItemStore = {
    items: [] as unknown[],
    selectedItem: null,
    loading: false,
    setScope: vi.fn(),
    loadItems: vi.fn().mockResolvedValue(undefined),
    loadOverview: vi.fn().mockResolvedValue(undefined),
    refreshItem: vi.fn().mockResolvedValue({ workflowSummary: null }),
    selectItem: vi.fn(),
    createItem: vi.fn(),
  }
  const workflowStore = {
    definitions: [],
    activeWorkflowInstance: null,
    loading: false,
    instancesByWorkItemId: {},
    loadDefinitions: vi.fn(),
    loadInstance: vi.fn(),
    continueWorkflow: vi.fn(),
    retryNode: vi.fn(),
    skipNode: vi.fn(),
    setActiveInstance: vi.fn(),
    upsertInstance: vi.fn(),
  }
  const runtimeSettingsStore = {
    activeProjectDataProviderId: 'fixture-alpha',
    activeProjectName: null,
    activeExternalProjectId: null,
    activeExternalSpaceId: null,
    activeExternalIterationId: null,
    batchStartWorkflowLimit: 5,
    initFromStorage: vi.fn(),
    loadProjectDataProviders: vi.fn().mockResolvedValue(undefined),
    setProjectDataScope: vi.fn().mockResolvedValue(undefined),
  }
  return { sessionStore, confirmationStore, workItemStore, workflowStore, runtimeSettingsStore }
})

vi.mock('./api/projectDataProviders', () => ({
  projectDataProviderApi: {
    settings: vi.fn().mockResolvedValue({
      providers: [
        { id: 'fixture-alpha', name: '测试源 A', description: 'fixture', active: true },
      ],
      activeProviderId: 'fixture-alpha',
      activeProjectContextId: null,
      activeProjectSpaceId: null,
      activeProjectIterationId: null,
      activeProjectName: null,
    }),
    sync: vi.fn().mockResolvedValue({
      providerId: 'fixture-alpha',
      contexts: [
        {
          id: 'ctx-agentcenter',
          externalProjectId: 'alpha-project-agentcenter',
          project: 'AgentCenter',
          externalCloudeReqProjectId: 'alpha-cloudereq-rd',
          cloudeReqProject: 'CloudeReq 研发项目',
          externalSpaceId: 'alpha-space-rd',
          space: '研发中台',
          externalIterationId: 'alpha-sprint-14',
          iteration: 'Sprint 14',
          active: true,
        },
        {
          id: 'ctx-platform',
          externalProjectId: 'alpha-project-platform',
          project: '平台接入',
          externalCloudeReqProjectId: 'alpha-cloudereq-delivery',
          cloudeReqProject: 'CloudeReq 交付空间',
          externalSpaceId: 'alpha-space-platform',
          space: '平台工程',
          externalIterationId: 'alpha-sprint-15',
          iteration: 'Sprint 15',
          active: false,
        },
      ],
      options: {
        cloudeReqProjects: ['CloudeReq 研发项目', 'CloudeReq 交付空间'],
        spaces: ['研发中台', '平台工程'],
        iterations: ['Sprint 14', 'Sprint 15'],
      },
      workItems: [],
      syncedAt: '2026-05-12T00:00:00Z',
    }),
    snapshot: vi.fn(),
    setActive: vi.fn(),
    setActiveScope: vi.fn(),
    syncHistory: vi.fn(),
  },
}))

// Mock stores that trigger API calls on mount
vi.mock('./stores/sessions', () => ({
  useSessionStore: vi.fn(() => mocks.sessionStore)
}))

vi.mock('./stores/confirmations', () => ({
  useConfirmationStore: vi.fn(() => mocks.confirmationStore)
}))

vi.mock('./stores/workItems', () => ({
  useWorkItemStore: vi.fn(() => mocks.workItemStore)
}))

vi.mock('./stores/workflows', () => ({
  useWorkflowStore: vi.fn(() => mocks.workflowStore)
}))

vi.mock('./stores/runtimeSettings', () => ({
  useRuntimeSettingsStore: vi.fn(() => mocks.runtimeSettingsStore)
}))

describe('App.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.workItemStore.items = []
    mocks.workItemStore.setScope.mockClear()
    mocks.workItemStore.loadItems.mockResolvedValue(undefined)
    mocks.workItemStore.loadOverview.mockResolvedValue(undefined)
    mocks.workItemStore.refreshItem.mockResolvedValue({ workflowSummary: null })
    mocks.confirmationStore.loadPending.mockResolvedValue(undefined)
    mocks.runtimeSettingsStore.setProjectDataScope.mockResolvedValue(undefined)
    mocks.workflowStore.instancesByWorkItemId = {}
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders without errors', () => {
    const wrapper = mount(App, {
      global: {
        plugins: [createPinia()],
      },
    })
    expect(wrapper.find('.app-shell').exists()).toBe(true)
  })

  it('updates the title context from project settings', async () => {
    const wrapper = mount(App, {
      global: {
        plugins: [createPinia()],
      },
    })
    await flushPromises()

    await wrapper.find('.left-sidebar__settings').trigger('click')
    const projectMenuItem = wrapper
      .findAll('.left-sidebar__settings-menu-item')
      .find(item => item.text().includes('项目管理'))
    expect(projectMenuItem).toBeTruthy()

    await projectMenuItem!.trigger('click')
    await flushPromises()

    expect(wrapper.find('.project-context').exists()).toBe(true)
    const projectInput = wrapper.find('.project-context input[aria-label="自定义项目名称"]')
    await projectInput.setValue('平台接入')
    await flushPromises()

    expect(wrapper.find('.title-bar__context-project').text()).toBe('AgentCenter')
    await wrapper.find('.project-context__save').trigger('click')
    await flushPromises()

    expect(wrapper.find('.title-bar__context-project').text()).toBe('平台接入')
    expect(mocks.runtimeSettingsStore.setProjectDataScope).toHaveBeenCalledWith({
      providerId: 'fixture-alpha',
      projectName: '平台接入',
      projectId: 'fixture-alpha:alpha-project-platform',
      spaceId: 'alpha-space-platform',
      iterationId: 'alpha-sprint-15',
      externalProjectId: 'alpha-project-platform',
      externalSpaceId: 'alpha-space-platform',
      externalIterationId: 'alpha-sprint-15',
    })

    const iterationSelect = wrapper.find('.title-bar__iteration')
    expect((iterationSelect.element as HTMLSelectElement).value).toBe('Sprint 15')
    await flushPromises()
    expect(mocks.workItemStore.setScope).toHaveBeenLastCalledWith({
      providerId: 'fixture-alpha',
      projectId: 'fixture-alpha:alpha-project-platform',
      spaceId: 'alpha-space-platform',
      iterationId: 'alpha-sprint-15',
    })
  })

  it('refreshes only the affected work item after workflow actions', async () => {
    vi.useFakeTimers()
    const item = {
      id: 'work-1',
      code: 'US1204',
      type: 'US',
      title: '消息中心订阅设置',
      description: null,
      status: 'TODO',
      priority: 'HIGH',
      projectId: null,
      spaceId: null,
      iterationId: null,
      assigneeUserId: null,
      currentWorkflowInstanceId: 'wf-1',
      workflowSummary: {
        instanceId: 'wf-1',
        status: 'RUNNING',
        currentNodeInstanceId: 'node-2',
        nodes: [{ id: 'node-2', definitionName: '方案设计', skillName: 'hld-design', status: 'RUNNING' }],
        stages: [{ id: 'node-2', stageKey: 'solution_design', name: '方案设计', skillName: 'hld-design', status: 'RUNNING', dynamicNodeCount: 0, recoveryCount: 0, pendingConfirmationCount: 0, latestSummary: null }],
      },
      createdAt: '2026-05-07T00:00:00Z',
      updatedAt: '2026-05-07T00:00:00Z',
    }
    mocks.workItemStore.refreshItem.mockResolvedValue(item)

    const wrapper = mount(App, {
      global: {
        plugins: [createPinia()],
      },
    })
    await flushPromises()
    const initialLoadCalls = mocks.workItemStore.loadItems.mock.calls.length

    await wrapper.findComponent({ name: 'HomeOverview' }).vm.$emit('start-workflow', 'work-1', {
      workflowInstance: null,
      session: null,
      artifacts: [],
      events: [],
      confirmation: null,
    })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(600)
    await flushPromises()

    expect(mocks.workItemStore.refreshItem).toHaveBeenCalledWith('work-1')
    expect(mocks.workItemStore.loadItems).toHaveBeenCalledTimes(initialLoadCalls)
    wrapper.unmount()
  })

  it('keeps refreshing a running workflow after the startup refresh window', async () => {
    vi.useFakeTimers()
    let refreshCount = 0
    const runningItem = makeWorkflowItem('RUNNING', 'RUNNING')
    const completedItem = makeWorkflowItem('COMPLETED', 'COMPLETED')
    mocks.workItemStore.refreshItem.mockImplementation(async () => {
      refreshCount += 1
      const item = refreshCount < 9 ? runningItem : completedItem
      mocks.workItemStore.items = [item]
      return item
    })

    const wrapper = mount(App, {
      global: {
        plugins: [createPinia()],
      },
    })
    await flushPromises()

    await wrapper.findComponent({ name: 'HomeOverview' }).vm.$emit('start-workflow', 'work-1', {
      workflowInstance: null,
      session: null,
      artifacts: [],
      events: [],
      confirmation: null,
    })
    await flushPromises()

    await vi.advanceTimersByTimeAsync(12000)
    await flushPromises()
    const callsAfterStartupWindow = mocks.workItemStore.refreshItem.mock.calls.length

    await vi.advanceTimersByTimeAsync(3000)
    await flushPromises()
    expect(mocks.workItemStore.refreshItem.mock.calls.length).toBeGreaterThan(callsAfterStartupWindow)
    const callsAfterFirstWatch = mocks.workItemStore.refreshItem.mock.calls.length

    await vi.advanceTimersByTimeAsync(15000)
    await flushPromises()
    expect(mocks.workItemStore.refreshItem.mock.calls.length).toBeGreaterThan(callsAfterFirstWatch)
    const callsAfterCompleted = mocks.workItemStore.refreshItem.mock.calls.length

    await vi.advanceTimersByTimeAsync(30000)
    await flushPromises()
    expect(mocks.workItemStore.refreshItem.mock.calls.length).toBe(callsAfterCompleted)

    wrapper.unmount()
  })
})

function makeWorkflowItem(workflowStatus: 'RUNNING' | 'COMPLETED', nodeStatus: 'RUNNING' | 'COMPLETED'): WorkItemDto {
  return {
    id: 'work-1',
    code: 'US1204',
    type: 'US',
    title: '消息中心订阅设置',
    description: null,
    status: workflowStatus === 'COMPLETED' ? 'DONE' : 'IN_PROGRESS',
    priority: 'HIGH',
    providerId: null,
    externalWorkItemId: null,
    projectId: null,
    spaceId: null,
    iterationId: null,
    projectContextId: null,
    projectSpaceId: null,
    projectIterationId: null,
    assigneeUserId: null,
    currentWorkflowInstanceId: 'wf-1',
    workflowSummary: {
      instanceId: 'wf-1',
      status: workflowStatus,
      currentNodeInstanceId: 'node-2',
      currentStageKey: 'solution_design',
      nodes: [{ id: 'node-2', definitionName: '方案设计', skillName: 'hld-design', status: nodeStatus, errorMessage: null }],
      stages: [{ id: 'node-2', stageKey: 'solution_design', name: '方案设计', skillName: 'hld-design', status: nodeStatus, dynamicNodeCount: 0, recoveryCount: 0, pendingConfirmationCount: 0, latestSummary: null, errorMessage: null }],
    },
    createdAt: '2026-05-07T00:00:00Z',
    updatedAt: '2026-05-07T00:00:00Z',
  }
}
