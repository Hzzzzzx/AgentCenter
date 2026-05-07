import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
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
    attachTo: document.body,
  })
}

describe('ConfirmationCard.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  async function openDialog(wrapper: ReturnType<typeof mountCard>) {
    await wrapper.find('.confirmation-card__action').trigger('click')
    await wrapper.vm.$nextTick()
  }

  it('keeps only 处理 action in the card and moves approval controls into dialog', async () => {
    const wrapper = mountCard(pendingConfirmation)

    const cardActions = wrapper.findAll('.confirmation-card > .confirmation-card__actions .confirmation-card__action')
    expect(cardActions).toHaveLength(1)
    expect(cardActions[0].text()).toBe('处理')

    await openDialog(wrapper)
    expect(document.body.textContent).toContain('通过')
    expect(document.body.textContent).toContain('拒绝')
    expect(document.body.textContent).toContain('进入会话')
  })

  it('hides 通过/拒绝 in dialog for non-PENDING confirmation', async () => {
    const wrapper = mountCard(resolvedConfirmation)

    await openDialog(wrapper)
    expect(document.body.textContent).not.toContain('通过')
    expect(document.body.textContent).not.toContain('拒绝')
    expect(document.body.textContent).toContain('进入会话')
  })

  it('clicking 通过 calls resolveConfirmation and emits resolved', async () => {
    const { confirmationApi } = await import('../../api/confirmations')
    const wrapper = mountCard(pendingConfirmation)
    const notificationStore = useNotificationStore()

    await openDialog(wrapper)
    await document.body.querySelector<HTMLButtonElement>('.confirmation-card__action--approve')!.click()
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

    await openDialog(wrapper)
    await document.body.querySelector<HTMLButtonElement>('.confirmation-card__action--reject')!.click()
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

    await openDialog(wrapper)
    await document.body.querySelector<HTMLButtonElement>('.confirmation-card__action--approve')!.click()
    await vi.dynamicImportSettled()

    expect(wrapper.emitted('resolved')).toBeFalsy()
    expect(notificationStore.rightPanelNotifications[0].title).toBe('通过失败')
    expect(notificationStore.rightPanelNotifications[0].message).toBe('Network error')
  })

  it('clicking 进入会话 emits handle with id', async () => {
    const wrapper = mountCard(pendingConfirmation)

    await openDialog(wrapper)
    const enterBtn = [...document.body.querySelectorAll<HTMLButtonElement>('.confirmation-card__action')]
      .find((btn) => btn.textContent?.includes('进入会话'))
    expect(enterBtn).toBeTruthy()
    await enterBtn!.click()

    expect(wrapper.emitted('handle')).toBeTruthy()
    expect(wrapper.emitted('handle')![0]).toEqual(['conf-1'])
  })
})
