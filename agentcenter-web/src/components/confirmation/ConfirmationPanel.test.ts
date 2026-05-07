import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ConfirmationPanel from './ConfirmationPanel.vue'
import { useConfirmationStore } from '../../stores/confirmations'
import type { ConfirmationRequestDto } from '../../api/types'

vi.mock('../../api/confirmations', () => ({
  confirmationApi: {
    list: vi.fn().mockResolvedValue([]),
    resolve: vi.fn().mockResolvedValue({ id: 'conf-1', status: 'RESOLVED' }),
    reject: vi.fn().mockResolvedValue({ id: 'conf-1', status: 'REJECTED' }),
    enterSession: vi.fn().mockResolvedValue({ id: 'conf-1', status: 'IN_CONVERSATION' }),
  },
}))

vi.mock('../../api/workItems', () => ({
  workItemApi: {
    list: vi.fn().mockResolvedValue([]),
    getById: vi.fn().mockResolvedValue({
      id: 'wi-1',
      code: 'TASK-001',
      type: 'TASK',
      title: 'Test task',
      description: null,
      status: 'TODO',
      priority: 'MEDIUM',
      projectId: null,
      spaceId: null,
      iterationId: null,
      assigneeUserId: null,
      currentWorkflowInstanceId: null,
      workflowSummary: null,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    }),
    create: vi.fn(),
  },
}))

const mockConfirmations: ConfirmationRequestDto[] = [
  {
    id: 'conf-1',
    requestType: 'APPROVAL',
    status: 'PENDING',
    workItemId: 'wi-1',
    workflowInstanceId: null,
    workflowNodeInstanceId: null,
    agentSessionId: null,
    skillName: 'code-review',
    title: 'Approve deployment',
    content: 'Please review and approve',
    contextSummary: null,
    optionsJson: null,
    priority: 'HIGH',
    createdAt: '2026-01-01T10:00:00Z',
  },
  {
    id: 'conf-2',
    requestType: 'CONFIRM',
    status: 'PENDING',
    workItemId: null,
    workflowInstanceId: null,
    workflowNodeInstanceId: null,
    agentSessionId: null,
    skillName: 'test-runner',
    title: 'Confirm test results',
    content: null,
    contextSummary: null,
    optionsJson: null,
    priority: 'MEDIUM',
    createdAt: '2026-01-01T11:00:00Z',
  },
]

function mountConfirmationPanel() {
  const pinia = createPinia()
  setActivePinia(pinia)
  return mount(ConfirmationPanel, {
    global: { plugins: [pinia] },
    attachTo: document.body,
  })
}

describe('ConfirmationPanel.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
  })

  it('renders confirmation cards', async () => {
    const wrapper = mountConfirmationPanel()
    const store = useConfirmationStore()
    store.pendingConfirmations = mockConfirmations
    store.loading = false
    await wrapper.vm.$nextTick()

    const cards = wrapper.findAll('.confirmation-card')
    expect(cards.length).toBe(2)
    expect(cards[0].text()).toContain('Approve deployment')
    expect(cards[1].text()).toContain('Confirm test results')
  })

  it('shows empty state when no confirmations', async () => {
    const wrapper = mountConfirmationPanel()
    const store = useConfirmationStore()
    store.pendingConfirmations = []
    store.loading = false
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.confirmation-panel__empty').exists()).toBe(true)
    expect(wrapper.find('.confirmation-panel__empty').text()).toBe('无待确认事项')
  })

  it('shows loading state', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const { confirmationApi } = await import('../../api/confirmations')
    vi.mocked(confirmationApi.list).mockReturnValue(new Promise(() => {}))
    const wrapper = mount(ConfirmationPanel, { global: { plugins: [pinia] } })
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.confirmation-panel__loading').exists()).toBe(true)
  })

  it('emits handle when entering session from处理 dialog', async () => {
    const wrapper = mountConfirmationPanel()
    const store = useConfirmationStore()
    store.pendingConfirmations = [mockConfirmations[0]]
    store.loading = false
    await wrapper.vm.$nextTick()

    await wrapper.find('.confirmation-card__action').trigger('click')
    await wrapper.vm.$nextTick()
    const enterBtn = [...document.body.querySelectorAll<HTMLButtonElement>('.confirmation-card__action')]
      .find((btn) => btn.textContent?.includes('进入会话'))
    expect(enterBtn).toBeTruthy()
    await enterBtn!.click()
    expect(wrapper.emitted('handle')).toBeTruthy()
    expect(wrapper.emitted('handle')![0]).toEqual(['conf-1'])
  })

  it('refreshes work items and pending confirmations after approval', async () => {
    const { confirmationApi } = await import('../../api/confirmations')
    const { workItemApi } = await import('../../api/workItems')
    const wrapper = mountConfirmationPanel()
    const store = useConfirmationStore()
    store.pendingConfirmations = [mockConfirmations[0]]
    store.loading = false
    vi.clearAllMocks()
    await wrapper.vm.$nextTick()

    await wrapper.find('.confirmation-card__action').trigger('click')
    await wrapper.vm.$nextTick()
    document.body.querySelector<HTMLButtonElement>('.confirmation-card__action--approve')!.click()
    await flushPromises()
    await wrapper.vm.$nextTick()
    await flushPromises()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('conf-1', { actionType: 'APPROVE' })
    expect(confirmationApi.list).toHaveBeenCalledWith('PENDING')
    expect(workItemApi.getById).toHaveBeenCalledWith('wi-1')
    expect(workItemApi.list).not.toHaveBeenCalled()
    expect(wrapper.emitted('changed')).toBeTruthy()
    expect(wrapper.emitted('changed')![0]).toEqual(['wi-1'])
  })
})
