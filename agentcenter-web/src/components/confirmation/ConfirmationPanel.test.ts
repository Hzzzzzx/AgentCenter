import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ConfirmationPanel from './ConfirmationPanel.vue'
import { useConfirmationStore } from '../../stores/confirmations'
import type { ConfirmationRequestDto } from '../../api/types'

vi.mock('../../api/confirmations', () => ({
  confirmationApi: {
    list: vi.fn().mockResolvedValue([]),
  },
}))

const mockConfirmations: ConfirmationRequestDto[] = [
  {
    id: 'conf-1',
    requestType: 'APPROVAL',
    status: 'PENDING',
    workItemId: null,
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
  })
}

describe('ConfirmationPanel.vue', () => {
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

  it('emits handle when action button clicked', async () => {
    const wrapper = mountConfirmationPanel()
    const store = useConfirmationStore()
    store.pendingConfirmations = mockConfirmations
    store.loading = false
    await wrapper.vm.$nextTick()

    const actionButtons = wrapper.findAll('.confirmation-card__action')
    await actionButtons[0].trigger('click')
    expect(wrapper.emitted('handle')).toBeTruthy()
    expect(wrapper.emitted('handle')![0]).toEqual(['conf-1'])
  })
})
