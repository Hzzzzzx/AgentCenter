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
    expect(document.body.textContent).toContain('退回')
    expect(document.body.textContent).toContain('进入会话')
  })

  it('hides pending actions in dialog for non-PENDING confirmation', async () => {
    const wrapper = mountCard(resolvedConfirmation)

    await openDialog(wrapper)
    expect(document.body.textContent).not.toContain('通过')
    expect(document.body.textContent).not.toContain('退回')
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

  it('submits DECISION confirmation with selected option', async () => {
    const { confirmationApi } = await import('../../api/confirmations')
    const wrapper = mountCard({
      ...pendingConfirmation,
      requestType: 'DECISION',
      optionsJson: '["低风险方案","低成本方案","完整方案"]',
    })

    await openDialog(wrapper)
    const option = document.body.querySelector<HTMLInputElement>('input[value="低成本方案"]')
    expect(option).toBeTruthy()
    option!.checked = true
    option!.dispatchEvent(new Event('change'))
    await wrapper.vm.$nextTick()
    await document.body.querySelector<HTMLButtonElement>('.confirmation-card__action--approve')!.click()
    await vi.dynamicImportSettled()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('conf-1', {
      actionType: 'CHOOSE',
      payload: { choice: '低成本方案' },
      comment: '低成本方案',
    })
    expect(wrapper.emitted('resolved')![0]).toEqual(['conf-1'])
  })

  it('submits INPUT_REQUIRED confirmation with supplemental text', async () => {
    const { confirmationApi } = await import('../../api/confirmations')
    const wrapper = mountCard({
      ...pendingConfirmation,
      requestType: 'INPUT_REQUIRED',
      content: '请补充目标用户范围',
    })

    await openDialog(wrapper)
    const textarea = document.body.querySelector<HTMLTextAreaElement>('.confirmation-dialog__textarea')
    expect(textarea).toBeTruthy()
    textarea!.value = '先覆盖企业管理员和运营人员'
    textarea!.dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()
    await document.body.querySelector<HTMLButtonElement>('.confirmation-card__action--approve')!.click()
    await vi.dynamicImportSettled()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('conf-1', {
      actionType: 'SUPPLEMENT',
      payload: { input: '先覆盖企业管理员和运营人员' },
      comment: '先覆盖企业管理员和运营人员',
    })
    expect(wrapper.emitted('resolved')![0]).toEqual(['conf-1'])
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
