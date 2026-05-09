import { describe, expect, it, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ConversationInteractionBar from './ConversationInteractionBar.vue'
import { confirmationApi } from '../../api/confirmations'
import type { ConfirmationRequestDto } from '../../api/types'

vi.mock('../../api/confirmations', () => ({
  confirmationApi: {
    list: vi.fn().mockResolvedValue([]),
    getById: vi.fn(),
    enterSession: vi.fn(),
    resolve: vi.fn().mockResolvedValue({}),
    reject: vi.fn(),
  },
}))

function makeInteraction(overrides: Partial<ConfirmationRequestDto> = {}): ConfirmationRequestDto {
  return {
    id: 'confirm-1',
    requestType: 'APPROVAL',
    status: 'PENDING',
    workItemId: 'work-1',
    workItemCode: 'FE1002',
    workItemType: 'FE',
    workItemTitle: '确认项浮层交互回归',
    workflowInstanceId: 'wf-1',
    workflowNodeInstanceId: 'node-1',
    agentSessionId: 'session-1',
    skillName: 'lld-design',
    title: '确认继续下一步',
    content: '请确认是否继续推进工作流。',
    contextSummary: '详细设计产物已生成，等待确认。',
    optionsJson: null,
    priority: 'MEDIUM',
    createdAt: '2026-05-09T00:00:00Z',
    ...overrides,
  }
}

describe('ConversationInteractionBar.vue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders the current interaction drawer above the composer', () => {
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction(),
          makeInteraction({
            id: 'confirm-2',
            requestType: 'DECISION',
            title: '选择处理方式',
            optionsJson: '["重试","跳过","转人工"]',
          }),
        ],
      },
    })

    expect(wrapper.find('.interaction-bar').exists()).toBe(true)
    expect(wrapper.text()).toContain('当前需要交互')
    expect(wrapper.text()).toContain('2')
    expect(wrapper.text()).toContain('FE1002 · 确认项浮层交互回归')
    expect(wrapper.text()).toContain('确认继续下一步')
    expect(wrapper.text()).toContain('选择处理方式')
    expect(wrapper.find('.interaction-bar__tab strong').text()).toContain('FE1002')
  })

  it('submits a selected decision option', async () => {
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction({
            id: 'confirm-2',
            requestType: 'DECISION',
            title: '选择处理方式',
            optionsJson: '["重试","跳过","转人工"]',
          }),
        ],
      },
    })

    await wrapper.findAll('.interaction-bar__option')[1].trigger('click')
    await wrapper.find('.interaction-bar__primary').trigger('click')
    await flushPromises()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('confirm-2', {
      actionType: 'CHOOSE',
      payload: { choice: '跳过' },
      comment: '跳过',
    })
    expect(wrapper.emitted('resolved')?.[0]).toEqual(['confirm-2'])
  })

  it('submits custom input for input-required interactions', async () => {
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction({
            id: 'confirm-3',
            requestType: 'INPUT_REQUIRED',
            title: '补充说明',
            contextSummary: '需要补充验收口径。',
          }),
        ],
      },
    })

    await wrapper.find('.interaction-bar__input').setValue('请补充异常分支')
    await wrapper.find('.interaction-bar__primary').trigger('click')
    await flushPromises()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('confirm-3', {
      actionType: 'SUPPLEMENT',
      payload: { input: '请补充异常分支' },
      comment: '请补充异常分支',
    })
    expect(wrapper.emitted('resolved')?.[0]).toEqual(['confirm-3'])
  })
})
