import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import AppShell from './AppShell.vue'

vi.mock('../../stores/confirmations', () => ({
  useConfirmationStore: vi.fn(() => ({
    pendingConfirmations: [],
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

describe('AppShell.vue', () => {
  function mountShell() {
    return mount(AppShell, {
      props: {
        activeView: 'home',
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
    expect(wrapper.find('.left-sidebar').exists()).toBe(true)
    expect(wrapper.find('.center-workbench').exists()).toBe(true)
    expect(wrapper.find('.slot-content').exists()).toBe(true)
    expect(wrapper.find('.right-panel').exists()).toBe(true)
    expect(wrapper.find('.status-bar').exists()).toBe(true)
    expect(wrapper.find('.status-bar__indicator--normal').exists()).toBe(true)
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
  })

  it('displays correct navigation labels', () => {
    const wrapper = mountShell()
    const labels = wrapper
      .findAll('.left-sidebar__nav-item')
      .map(item => item.text().trim())
    expect(labels).toEqual(['首页', '看板', '工作流'])
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
