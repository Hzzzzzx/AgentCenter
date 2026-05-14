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
      payload: { choice: '低成本方案', choiceId: '低成本方案', choiceLabel: '低成本方案' },
      comment: '低成本方案',
    })
    expect(wrapper.emitted('resolved')![0]).toEqual(['conf-1'])
  })

  it('submits DECISION confirmation when options use value and label fields', async () => {
    const { confirmationApi } = await import('../../api/confirmations')
    const wrapper = mountCard({
      ...pendingConfirmation,
      requestType: 'DECISION',
      optionsJson: JSON.stringify([
        { value: 'FAST', label: '快速验证' },
        { value: 'STRICT', label: '严格校验' },
      ]),
    })

    await openDialog(wrapper)
    expect(document.body.textContent).not.toContain('[object Object]')
    const option = document.body.querySelector<HTMLInputElement>('input[value="STRICT"]')
    expect(option).toBeTruthy()
    option!.checked = true
    option!.dispatchEvent(new Event('change'))
    await wrapper.vm.$nextTick()
    await document.body.querySelector<HTMLButtonElement>('.confirmation-card__action--approve')!.click()
    await vi.dynamicImportSettled()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('conf-1', {
      actionType: 'CHOOSE',
      payload: { choice: 'STRICT', choiceId: 'STRICT', choiceLabel: '严格校验' },
      comment: '严格校验',
    })
  })

  it('uses option actionType for decision submissions', async () => {
    const { confirmationApi } = await import('../../api/confirmations')
    const wrapper = mountCard({
      ...pendingConfirmation,
      requestType: 'DECISION',
      interactionType: 'WORKFLOW_ADVANCE',
      interactionSchemaJson: JSON.stringify({
        selection: 'single',
        options: [
          { id: 'next-node', label: '进入下一阶段', actionType: 'ADVANCE' },
          { id: 'revise-current', label: '继续完善当前阶段', actionType: 'SUPPLEMENT' },
        ],
      }),
    })

    await openDialog(wrapper)
    const option = document.body.querySelector<HTMLInputElement>('input[value="next-node"]')
    expect(option).toBeTruthy()
    option!.checked = true
    option!.dispatchEvent(new Event('change'))
    await wrapper.vm.$nextTick()
    await document.body.querySelector<HTMLButtonElement>('.confirmation-card__action--approve')!.click()
    await vi.dynamicImportSettled()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('conf-1', {
      actionType: 'ADVANCE',
      payload: { choice: 'next-node', choiceId: 'next-node', choiceLabel: '进入下一阶段' },
      comment: '进入下一阶段',
    })
  })

  it('submits DECISION confirmation with multi-select and custom choice', async () => {
    const { confirmationApi } = await import('../../api/confirmations')
    const wrapper = mountCard({
      ...pendingConfirmation,
      requestType: 'DECISION',
      interactionSchemaJson: JSON.stringify({
        selection: 'multi',
        allowCustom: true,
        options: [
          { id: 'FAST', label: '快速验证', description: '先跑关键链路' },
          { id: 'FULL', label: '完整回归', description: '覆盖全部用例' },
        ],
      }),
    })

    await openDialog(wrapper)
    expect(document.body.textContent).toContain('先跑关键链路')
    const fast = document.body.querySelector<HTMLInputElement>('input[value="FAST"]')!
    fast.checked = true
    fast.dispatchEvent(new Event('change'))
    const custom = document.body.querySelector<HTMLInputElement>('input[placeholder="输入自定义处理方式..."]')!
    custom.value = '再补一轮冒烟'
    custom.dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()

    await document.body.querySelector<HTMLButtonElement>('.confirmation-card__action--approve')!.click()
    await vi.dynamicImportSettled()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('conf-1', {
      actionType: 'CHOOSE',
      payload: {
        choiceIds: ['FAST'],
        choiceLabels: ['快速验证'],
        choices: ['快速验证', '再补一轮冒烟'],
        customChoice: '再补一轮冒烟',
      },
      comment: '快速验证，再补一轮冒烟',
    })
  })

  it('renders ARTIFACT_REVIEW approval options with review note', async () => {
    const { confirmationApi } = await import('../../api/confirmations')
    const wrapper = mountCard({
      ...pendingConfirmation,
      requestType: 'APPROVAL',
      interactionType: 'ARTIFACT_REVIEW',
      title: '审阅 LLD 草稿',
      optionsJson: JSON.stringify([
        { id: 'PASS', label: '通过', description: '进入实现' },
        { id: 'REVISE', label: '需要调整', description: '补充风险和验证' },
      ]),
    })

    await openDialog(wrapper)
    expect(document.body.textContent).toContain('审阅产物')
    expect(document.body.textContent).toContain('补充风险和验证')
    const revise = document.body.querySelector<HTMLInputElement>('input[value="REVISE"]')!
    revise.checked = true
    revise.dispatchEvent(new Event('change'))
    const note = document.body.querySelector<HTMLTextAreaElement>('.confirmation-dialog__review-note')!
    note.value = '补上失败回滚策略'
    note.dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()

    await document.body.querySelector<HTMLButtonElement>('.confirmation-card__action--approve')!.click()
    await vi.dynamicImportSettled()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('conf-1', {
      actionType: 'REJECT',
      payload: {
        choice: 'REVISE',
        choiceId: 'REVISE',
        choiceLabel: '需要调整',
        remark: '补上失败回滚策略',
      },
      comment: '需要调整\n补上失败回滚策略',
    })
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

  it('renders and submits INPUT_REQUIRED confirmation with structured fields', async () => {
    const { confirmationApi } = await import('../../api/confirmations')
    const wrapper = mountCard({
      ...pendingConfirmation,
      requestType: 'INPUT_REQUIRED',
      title: '补充 PRD 关键问题',
      content: '请一次性补充目标用户、范围边界和验收标准。',
      interactionSchemaJson: JSON.stringify({
        id: 'PRD-MULTI-QUESTION',
        type: 'INPUT',
        title: '补充 PRD 关键问题',
        question: '请一次性补充目标用户、范围边界和验收标准。',
        fields: [
          { id: 'PRD-AUDIENCE', label: '目标用户', type: 'textarea', required: true },
          { id: 'PRD-SCOPE', label: '范围边界', type: 'textarea', required: true },
          { id: 'PRD-ACCEPTANCE', label: '验收标准', type: 'textarea', required: true },
        ],
      }),
    })

    await openDialog(wrapper)
    expect(document.body.textContent).toContain('目标用户')
    expect(document.body.textContent).toContain('范围边界')
    expect(document.body.textContent).toContain('验收标准')
    expect(document.body.querySelectorAll('.confirmation-dialog__field-tab')).toHaveLength(3)
    expect(document.body.querySelector('#confirmation-field-PRD-SCOPE')).toBeNull()

    const submitButton = document.body.querySelector<HTMLButtonElement>('.confirmation-card__action--approve')!
    expect(submitButton.disabled).toBe(true)

    const audience = document.body.querySelector<HTMLTextAreaElement>('#confirmation-field-PRD-AUDIENCE')!
    audience.value = '企业管理员'
    audience.dispatchEvent(new Event('input'))

    const tabs = [...document.body.querySelectorAll<HTMLButtonElement>('.confirmation-dialog__field-tab')]
    await tabs[1].click()
    await wrapper.vm.$nextTick()
    const scope = document.body.querySelector<HTMLTextAreaElement>('#confirmation-field-PRD-SCOPE')!
    scope.value = '只覆盖 PRD 阶段，不做 HLD'
    scope.dispatchEvent(new Event('input'))

    await tabs[2].click()
    await wrapper.vm.$nextTick()
    const acceptance = document.body.querySelector<HTMLTextAreaElement>('#confirmation-field-PRD-ACCEPTANCE')!
    acceptance.value = '页面出现三个必填输入框'
    acceptance.dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()

    expect(submitButton.disabled).toBe(false)
    await submitButton.click()
    await vi.dynamicImportSettled()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('conf-1', {
      actionType: 'SUPPLEMENT',
      payload: {
        input: '企业管理员\n只覆盖 PRD 阶段，不做 HLD\n页面出现三个必填输入框',
        fields: {
          'PRD-AUDIENCE': '企业管理员',
          'PRD-SCOPE': '只覆盖 PRD 阶段，不做 HLD',
          'PRD-ACCEPTANCE': '页面出现三个必填输入框',
        },
      },
      comment: '企业管理员\n只覆盖 PRD 阶段，不做 HLD\n页面出现三个必填输入框',
    })
    expect(wrapper.emitted('resolved')![0]).toEqual(['conf-1'])
  })

  it('renders and submits INPUT_REQUIRED select and checkbox fields', async () => {
    const { confirmationApi } = await import('../../api/confirmations')
    const wrapper = mountCard({
      ...pendingConfirmation,
      requestType: 'INPUT_REQUIRED',
      title: '补充发布参数',
      interactionSchemaJson: JSON.stringify({
        title: '补充发布参数',
        fields: [
          {
            id: 'priority',
            label: '优先级',
            type: 'select',
            required: true,
            allowCustom: true,
            options: [
              { value: 'low', label: '低' },
              { value: 'high', label: '高' },
            ],
          },
          { id: 'accepted', label: '我确认风险', type: 'checkbox', required: true },
        ],
      }),
    })

    await openDialog(wrapper)
    const submitButton = document.body.querySelector<HTMLButtonElement>('.confirmation-card__action--approve')!
    expect(submitButton.disabled).toBe(true)
    expect(document.body.querySelector('#confirmation-field-priority')?.tagName).toBe('DIV')
    expect(document.body.querySelector('select#confirmation-field-priority')).toBeNull()

    const priorityOptions = [...document.body.querySelectorAll<HTMLButtonElement>('.confirmation-dialog__field-menu-option')]
    await priorityOptions[1].click()

    const tabs = [...document.body.querySelectorAll<HTMLButtonElement>('.confirmation-dialog__field-tab')]
    await tabs[1].click()
    await wrapper.vm.$nextTick()
    const accepted = document.body.querySelector<HTMLInputElement>('#confirmation-field-accepted')!
    accepted.checked = true
    accepted.dispatchEvent(new Event('change'))
    await wrapper.vm.$nextTick()

    expect(submitButton.disabled).toBe(false)
    await submitButton.click()
    await vi.dynamicImportSettled()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('conf-1', {
      actionType: 'SUPPLEMENT',
      payload: {
        input: '高\n是',
        fields: { priority: 'high', accepted: 'true' },
      },
      comment: '高\n是',
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

    expect(confirmationApi.resolve).toHaveBeenCalledWith('conf-1', { actionType: 'REJECT' })
    expect(wrapper.emitted('rejected')).toBeTruthy()
    expect(wrapper.emitted('rejected')![0]).toEqual(['conf-1'])
    expect(notificationStore.rightPanelNotifications[0].title).toBe('交互已拒绝')
  })

  it('offers skip and reject for EXCEPTION confirmations', async () => {
    const { confirmationApi } = await import('../../api/confirmations')
    const wrapper = mountCard({
      ...pendingConfirmation,
      requestType: 'EXCEPTION',
      title: '执行异常',
      contextSummary: '当前节点执行失败。',
    })

    await openDialog(wrapper)
    expect(document.body.textContent).toContain('跳过')
    expect(document.body.textContent).toContain('拒绝')

    const reject = [...document.body.querySelectorAll<HTMLButtonElement>('.confirmation-card__action')]
      .find((button) => button.textContent?.includes('拒绝'))!
    await reject.click()
    await vi.dynamicImportSettled()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('conf-1', {
      actionType: 'REJECT',
    })
    expect(wrapper.emitted('rejected')![0]).toEqual(['conf-1'])
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
