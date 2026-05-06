import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ConfirmationCard from './ConfirmationCard.vue'
import { useNotificationStore } from '../../stores/notifications'
import type { ConfirmationRequestDto } from '../../api/types'

vi.mock('../../api/confirmations', () => ({
  confirmationApi: {
    list: vi.fn().mockResolvedValue([]),
    resolve: vi.fn().mockResolvedValue({ id: 'conf-1', status: 'RESOLVED' }),
    reject: vi.fn().mockResolvedValue({ id: 'conf-1', status: 'REJECTED' }),
  },
}))

const pendingConfirmation: ConfirmationRequestDto = {
  id: 'conf-1',
  requestType: 'APPROVAL',
  status: 'PENDING',
  workItemId: null,
  workflowInstanceId: null,
  workflowNodeInstanceId: null,
  agentSessionId: null,
  skillName: 'deploy',
  title: 'Approve deployment',
  content: 'Deploy to prod?',
  contextSummary: null,
  optionsJson: null,
  priority: 'HIGH',
  createdAt: '2026-01-01T10:00:00Z',
}

const resolvedConfirmation: ConfirmationRequestDto = {
  ...pendingConfirmation,
  id: 'conf-2',
  status: 'RESOLVED',
}

function mountCard(confirmation: ConfirmationRequestDto) {
  const pinia = createPinia()
  setActivePinia(pinia)
  return mount(ConfirmationCard, {
    props: { confirmation },
    global: { plugins: [pinia] },
  })
}

describe('ConfirmationCard.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows 通过 and 拒绝 buttons for PENDING confirmation', async () => {
    const wrapper = mountCard(pendingConfirmation)

    const approveBtn = wrapper.find('.confirmation-card__action--approve')
    const rejectBtn = wrapper.find('.confirmation-card__action--reject')

    expect(approveBtn.exists()).toBe(true)
    expect(approveBtn.text()).toBe('通过')
    expect(rejectBtn.exists()).toBe(true)
    expect(rejectBtn.text()).toBe('拒绝')
  })

  it('hides 通过/拒绝 for non-PENDING confirmation', async () => {
    const wrapper = mountCard(resolvedConfirmation)

    expect(wrapper.find('.confirmation-card__action--approve').exists()).toBe(false)
    expect(wrapper.find('.confirmation-card__action--reject').exists()).toBe(false)
  })

  it('clicking 通过 calls resolveConfirmation and emits resolved', async () => {
    const { confirmationApi } = await import('../../api/confirmations')
    const wrapper = mountCard(pendingConfirmation)
    const notificationStore = useNotificationStore()

    await wrapper.find('.confirmation-card__action--approve').trigger('click')
    await vi.dynamicImportSettled()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('conf-1', { actionType: 'APPROVE' })
    expect(wrapper.emitted('resolved')).toBeTruthy()
    expect(wrapper.emitted('resolved')![0]).toEqual(['conf-1'])
    expect(notificationStore.rightPanelNotifications[0].title).toBe('确认已通过')
  })

  it('clicking 拒绝 calls rejectConfirmation and emits rejected', async () => {
    const { confirmationApi } = await import('../../api/confirmations')
    const wrapper = mountCard(pendingConfirmation)
    const notificationStore = useNotificationStore()

    await wrapper.find('.confirmation-card__action--reject').trigger('click')
    await vi.dynamicImportSettled()

    expect(confirmationApi.reject).toHaveBeenCalledWith('conf-1', { comment: undefined })
    expect(wrapper.emitted('rejected')).toBeTruthy()
    expect(wrapper.emitted('rejected')![0]).toEqual(['conf-1'])
    expect(notificationStore.rightPanelNotifications[0].title).toBe('已拒绝确认')
  })

  it('shows error notification when approve fails', async () => {
    const { confirmationApi } = await import('../../api/confirmations')
    vi.mocked(confirmationApi.resolve).mockRejectedValueOnce(new Error('Network error'))
    const wrapper = mountCard(pendingConfirmation)
    const notificationStore = useNotificationStore()

    await wrapper.find('.confirmation-card__action--approve').trigger('click')
    await vi.dynamicImportSettled()

    expect(wrapper.emitted('resolved')).toBeFalsy()
    expect(notificationStore.rightPanelNotifications[0].title).toBe('通过失败')
    expect(notificationStore.rightPanelNotifications[0].message).toBe('Network error')
  })

  it('clicking 处理 emits handle with id', async () => {
    const wrapper = mountCard(pendingConfirmation)

    const allButtons = wrapper.findAll('.confirmation-card__action')
    const handleBtn = allButtons.find((btn) => btn.text() === '处理')
    expect(handleBtn).toBeTruthy()

    await handleBtn!.trigger('click')

    expect(wrapper.emitted('handle')).toBeTruthy()
    expect(wrapper.emitted('handle')![0]).toEqual(['conf-1'])
  })
})
