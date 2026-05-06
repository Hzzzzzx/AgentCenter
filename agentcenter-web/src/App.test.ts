import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import App from './App.vue'

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
    loadItems: vi.fn().mockResolvedValue(undefined),
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
  return { sessionStore, confirmationStore, workItemStore, workflowStore }
})

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

describe('App.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.workItemStore.items = []
    mocks.workItemStore.loadItems.mockResolvedValue(undefined)
    mocks.workItemStore.refreshItem.mockResolvedValue({ workflowSummary: null })
    mocks.confirmationStore.loadPending.mockResolvedValue(undefined)
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
})
