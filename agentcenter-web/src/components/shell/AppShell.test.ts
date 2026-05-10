import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import AppShell from './AppShell.vue'
import type { ArtifactDto, WorkItemDto } from '../../api/types'
import { useWorkflowStore } from '../../stores/workflows'
import { useWorkItemWorkflowProjectionStore } from '../../stores/workItemWorkflowProjection'

vi.mock('../../stores/confirmations', () => ({
  useConfirmationStore: vi.fn(() => ({
    pendingConfirmations: [{ id: 'conf-1' }],
    currentConfirmation: null,
    loading: false,
    loadPending: vi.fn(),
    selectConfirmation: vi.fn(),
    resolveConfirmation: vi.fn(),
    rejectConfirmation: vi.fn(),
  })),
}))

vi.mock('../../stores/sessions', () => ({
  useSessionStore: vi.fn(() => ({
    sessions: [],
    activeSession: null,
    messages: [],
    loading: false,
    loadSessions: vi.fn(),
    selectSession: vi.fn(),
    createSession: vi.fn(),
    sendMessage: vi.fn(),
    replaceMessages: vi.fn(),
  })),
}))

vi.mock('../../api/workItems', () => ({
  workItemApi: {
    list: vi.fn().mockResolvedValue([]),
    getById: vi.fn(),
    create: vi.fn(),
  },
}))

vi.mock('../../api/workflows', () => ({
  workflowApi: {
    listDefinitions: vi.fn().mockResolvedValue([]),
    updateDefinition: vi.fn(),
    getInstance: vi.fn(),
    continueWorkflow: vi.fn(),
    retryNode: vi.fn(),
    skipNode: vi.fn(),
  },
}))

describe('AppShell.vue', () => {
  const selectedArtifact: ArtifactDto = {
    id: 'artifact-1',
    workItemId: 'wi-1',
    workflowInstanceId: 'wf-1',
    workflowNodeInstanceId: 'node-1',
    artifactType: 'MARKDOWN',
    title: 'FE0001-HLD.md',
    content: '# HLD',
    createdAt: '2026-01-01T10:00:00Z',
  }
  const selectedWorkItem: WorkItemDto = {
    id: 'wi-1',
    code: 'FE0001',
    type: 'FE',
    title: '登录页重构',
    description: '使用新的设计规范重构登录页面',
    status: 'TODO',
    priority: 'HIGH',
    projectId: null,
    spaceId: null,
    iterationId: null,
    assigneeUserId: null,
    currentWorkflowInstanceId: null,
    workflowSummary: null,
    createdAt: '2026-01-01T10:00:00Z',
    updatedAt: '2026-01-01T10:00:00Z',
  }

  function mountShell(props = {}) {
    return mount(AppShell, {
      props: {
        activeView: 'home',
        ...props,
      },
      slots: {
        center: '<div class="slot-content">Test Content</div>',
      },
      global: {
        plugins: [createPinia()],
      },
    })
  }

  it('renders all sub-components', () => {
    const wrapper = mountShell()

    expect(wrapper.find('.title-bar').exists()).toBe(true)
    expect(wrapper.find('.title-bar__name').text()).toContain('AI DevOps')
    expect(wrapper.find('.title-bar__context').text()).toContain('AgentCenter')
    expect(wrapper.find('.left-sidebar').exists()).toBe(true)
    expect(wrapper.find('.center-workbench').exists()).toBe(true)
    expect(wrapper.find('.slot-content').exists()).toBe(true)
    expect(wrapper.find('.right-panel').exists()).toBe(true)
    expect(wrapper.find('.status-bar').exists()).toBe(true)
    expect(wrapper.find('.status-bar__indicator--normal').exists()).toBe(true)
  })

  it('shows project context and iteration selector in the title bar', () => {
    const wrapper = mountShell()

    expect(wrapper.find('.title-bar__context-project').text()).toBe('AgentCenter')
    expect(wrapper.find('.title-bar__iteration').exists()).toBe(true)
  })

  it('does not render title bar notification or settings shortcuts', () => {
    const wrapper = mountShell()

    expect(wrapper.find('[aria-label="通知"]').exists()).toBe(false)
    expect(wrapper.find('.app-shell__titlebar [aria-label="设置"]').exists()).toBe(false)
  })

  it('navigates when clicking nav items', async () => {
    const wrapper = mountShell()
    const navItems = wrapper.findAll('.left-sidebar__nav-item')
    expect(navItems.length).toBe(3)

    await navItems[1].trigger('click')
    expect(wrapper.emitted('update:activeView')).toBeTruthy()
    expect(wrapper.emitted('update:activeView')![0]).toEqual(['board'])
  })

  it('collapses left sidebar on toggle', async () => {
    const wrapper = mountShell()
    expect(wrapper.find('.left-sidebar').classes()).not.toContain('left-sidebar--collapsed')

    await wrapper.find('.left-sidebar__collapse').trigger('click')
    expect(wrapper.find('.left-sidebar--collapsed').exists()).toBe(true)
  })

  it('collapses right panel on toggle', async () => {
    const wrapper = mountShell()
    expect(wrapper.find('.right-panel').classes()).not.toContain('right-panel--collapsed')

    await wrapper.find('.right-panel__toggle').trigger('click')
    expect(wrapper.find('.right-panel--collapsed').exists()).toBe(true)
    expect(wrapper.find('.right-panel__toggle-badge').exists()).toBe(false)
    expect(wrapper.find('.right-panel__rail-badge').text()).toBe('1')
  })

  it('clears expanded layout when collapsing right panel', async () => {
    const wrapper = mountShell({ selectedArtifact })

    await wrapper.find('.right-panel__expand').trigger('click')
    expect(wrapper.find('.app-shell').classes()).toContain('app-shell--right-expanded')

    await wrapper.find('.right-panel__toggle').trigger('click')

    expect(wrapper.find('.right-panel--collapsed').exists()).toBe(true)
    expect(wrapper.find('.app-shell').classes()).not.toContain('app-shell--right-expanded')
    expect(wrapper.find('.app-shell__center').attributes('style')).toBeUndefined()
  })

  it('opens confirmations from collapsed rail shortcut', async () => {
    const wrapper = mountShell()

    await wrapper.find('.right-panel__toggle').trigger('click')
    await wrapper.find('.right-panel__rail-action').trigger('click')

    expect(wrapper.find('.right-panel--collapsed').exists()).toBe(false)
    expect(wrapper.find('.right-panel__tab--active').text()).toContain('待确认')
  })

  it('shows starting state in the right panel details action', async () => {
    const wrapper = mountShell()
    const workflowProjectionStore = useWorkItemWorkflowProjectionStore()

    workflowProjectionStore.startingIds = new Set(['wi-1'])
    await wrapper.setProps({ selectedWorkItem })

    const btn = wrapper.find('.detail__btn--primary')
    expect(btn.text()).toBe('启动中')
    expect(btn.attributes('disabled')).toBeDefined()
    expect(wrapper.find('.detail__node--active').text()).toContain('开始')
  })

  it('uses cached workflow instance to switch right panel action after launch', async () => {
    const wrapper = mountShell()
    const workflowStore = useWorkflowStore()

    await wrapper.setProps({ selectedWorkItem })
    workflowStore.upsertInstance({
      id: 'wf-1',
      workItemId: 'wi-1',
      workflowDefinitionId: 'definition-1',
      status: 'RUNNING',
      currentNodeInstanceId: 'node-1',
      nodes: [{
        id: 'node-1',
        nodeDefinitionId: 'node-def-1',
        status: 'RUNNING',
        inputArtifactId: null,
        outputArtifactId: null,
        agentSessionId: null,
        startedAt: null,
        completedAt: null,
        errorMessage: null,
        skillName: 'prd-design',
      }],
      startedAt: null,
      completedAt: null,
    })
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.detail__btn--primary').exists()).toBe(false)
    const btn = wrapper.find('.detail__btn--secondary')
    expect(btn.text()).toBe('进入会话')
    expect(wrapper.find('.detail__node--active').text()).toContain('prd-design')
  })

  it('displays correct navigation labels', () => {
    const wrapper = mountShell()
    const labels = wrapper
      .findAll('.left-sidebar__nav-item')
      .map(item => item.text().trim())
    expect(labels).toEqual(['首页', '任务看板', '任务编排'])
  })

  it('navigates to project management from the settings menu', async () => {
    const wrapper = mountShell()

    await wrapper.find('.left-sidebar__settings').trigger('click')
    const projectMenuItem = wrapper
      .findAll('.left-sidebar__settings-menu-item')
      .find(item => item.text().includes('项目管理'))

    expect(projectMenuItem).toBeTruthy()
    await projectMenuItem!.trigger('click')

    expect(wrapper.emitted('navigate-settings')).toBeTruthy()
    expect(wrapper.emitted('navigate-settings')![0]).toEqual(['project'])
  })

  it('has correct grid layout structure', () => {
    const wrapper = mountShell()
    const shell = wrapper.find('.app-shell')
    expect(shell.exists()).toBe(true)
    expect(wrapper.find('.app-shell__titlebar').exists()).toBe(true)
    expect(wrapper.find('.app-shell__sidebar-left').exists()).toBe(true)
    expect(wrapper.find('.app-shell__center').exists()).toBe(true)
    expect(wrapper.find('.app-shell__right-panel').exists()).toBe(true)
    expect(wrapper.find('.app-shell__statusbar').exists()).toBe(true)
  })
})
