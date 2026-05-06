import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import App from './App.vue'

// Mock stores that trigger API calls on mount
vi.mock('./stores/sessions', () => ({
  useSessionStore: vi.fn(() => ({
    sessions: [],
    activeSession: null,
    messages: [],
    loading: false,
    loadSessions: vi.fn(),
    selectSession: vi.fn(),
    createSession: vi.fn(),
    sendMessage: vi.fn(),
  }))
}))

vi.mock('./stores/confirmations', () => ({
  useConfirmationStore: vi.fn(() => ({
    pendingConfirmations: [],
    currentConfirmation: null,
    loading: false,
    loadPending: vi.fn(),
    selectConfirmation: vi.fn(),
    resolveConfirmation: vi.fn(),
    rejectConfirmation: vi.fn(),
  }))
}))

vi.mock('./stores/workItems', () => ({
  useWorkItemStore: vi.fn(() => ({
    items: [],
    selectedItem: null,
    loading: false,
    loadItems: vi.fn(),
    selectItem: vi.fn(),
    createItem: vi.fn(),
  }))
}))

vi.mock('./stores/workflows', () => ({
  useWorkflowStore: vi.fn(() => ({
    definitions: [],
    activeWorkflowInstance: null,
    loading: false,
    loadDefinitions: vi.fn(),
    loadInstance: vi.fn(),
    continueWorkflow: vi.fn(),
    retryNode: vi.fn(),
    skipNode: vi.fn(),
  }))
}))

describe('App.vue', () => {
  it('renders without errors', () => {
    const wrapper = mount(App, {
      global: {
        plugins: [createPinia()],
      },
    })
    expect(wrapper.find('.app-shell').exists()).toBe(true)
  })
})
