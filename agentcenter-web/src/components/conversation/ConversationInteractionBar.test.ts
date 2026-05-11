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
    expect(wrapper.text()).toContain('需要你确认')
    expect(wrapper.text()).toContain('2')
    expect(wrapper.text()).not.toContain('FE1002 · 确认项浮层交互回归')
    expect(wrapper.text()).toContain('问题 1')
    expect(wrapper.text()).toContain('问题 2')
    expect(wrapper.text()).toContain('确认继续下一步')
    expect(wrapper.findAll('.interaction-bar__tab')).toHaveLength(2)
    expect(wrapper.find('.interaction-bar__tab').text()).toBe('问题 1')
    expect(wrapper.find('.interaction-bar__body .interaction-bar__tabs').exists()).toBe(false)
  })

  it('hides tabs when there is only one interaction', () => {
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction({
            id: 'confirm-single',
            requestType: 'DECISION',
            title: '选择实现路线',
            optionsJson: '["UI 优先","严格校验"]',
          }),
        ],
      },
    })

    expect(wrapper.text()).toContain('需要你确认选择')
    expect(wrapper.find('.interaction-bar__tabs').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('问题 1')
  })

  it('submits a selected decision option with structured payload', async () => {
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
      payload: { choice: '跳过', choiceId: '跳过', choiceLabel: '跳过' },
      comment: '跳过',
    })
    expect(wrapper.emitted('resolved')?.[0]).toEqual(['confirm-2'])
  })

  it('submits a selected decision option when legacy options use value and label fields', async () => {
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction({
            id: 'confirm-value-options',
            requestType: 'DECISION',
            title: '选择实现路线',
            optionsJson: JSON.stringify([
              { value: 'FAST', label: '快速验证', description: '只保留最小测试链路' },
              { value: 'STRICT', label: '严格校验' },
            ]),
          }),
        ],
      },
    })

    expect(wrapper.text()).not.toContain('[object Object]')
    await wrapper.findAll('.interaction-bar__option')[1].trigger('click')
    await wrapper.find('.interaction-bar__primary').trigger('click')
    await flushPromises()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('confirm-value-options', {
      actionType: 'CHOOSE',
      payload: { choice: 'STRICT', choiceId: 'STRICT', choiceLabel: '严格校验' },
      comment: '严格校验',
    })
    expect(wrapper.emitted('resolved')?.[0]).toEqual(['confirm-value-options'])
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

    await wrapper.find('.interaction-bar__textarea').setValue('请补充异常分支')
    await wrapper.find('.interaction-bar__primary').trigger('click')
    await flushPromises()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('confirm-3', {
      actionType: 'SUPPLEMENT',
      payload: { input: '请补充异常分支' },
      comment: '请补充异常分支',
    })
    expect(wrapper.emitted('resolved')?.[0]).toEqual(['confirm-3'])
  })

  it('keeps multi-question prompts visible while editing answers', async () => {
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction({
            id: 'confirm-questions',
            requestType: 'INPUT_REQUIRED',
            title: '需要你回答 2 个问题',
            content: '请按问题分别补充答案',
            interactionSchemaJson: JSON.stringify({
              question: '请按问题分别补充答案',
              fields: [
                {
                  id: 'q0',
                  label: '问题 1',
                  type: 'textarea',
                  required: true,
                  placeholder: '请确认目标用户是谁？',
                },
                {
                  id: 'q1',
                  label: '问题 2',
                  type: 'textarea',
                  required: true,
                  placeholder: '请说明验收标准是什么？',
                },
              ],
            }),
          }),
        ],
      },
    })

    const textareas = wrapper.findAll('textarea')
    expect(wrapper.text()).toContain('请确认目标用户是谁？')
    expect(wrapper.text()).toContain('请说明验收标准是什么？')
    expect(textareas[0].attributes('placeholder')).toBe('输入你的回答...')

    await textareas[0].setValue('企业项目经理')

    expect(wrapper.text()).toContain('请确认目标用户是谁？')
    expect(wrapper.text()).toContain('请说明验收标准是什么？')
  })

  it('shows the target file for Windows permission requests', () => {
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction({
            id: 'confirm-permission',
            requestType: 'PERMISSION',
            title: 'Allow edit?',
            content: 'OpenCode permission request',
            interactionContextJson: JSON.stringify({
              permission: 'edit',
              tool: { name: 'Edit file' },
              args: {
                filePath: 'C:\\Users\\alice\\workspace\\demo\\.opencode\\skills\\planner\\SKILL.md',
              },
            }),
          }),
        ],
      },
    })

    expect(wrapper.text()).toContain('需要你授权')
    expect(wrapper.text()).toContain('允许 Agent 编辑文件：C:/.../.opencode/skills/planner/SKILL.md？')
  })

  it('renders multi-select checkboxes for DECISION with selection: multi', async () => {
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction({
            id: 'confirm-multi',
            requestType: 'DECISION',
            title: '选择多个方案',
            interactionSchemaJson: JSON.stringify({
              selection: 'multi',
              options: [
                { id: 'opt-1', label: '方案A' },
                { id: 'opt-2', label: '方案B' },
                { id: 'opt-3', label: '方案C' },
              ],
            }),
          }),
        ],
      },
    })

    const options = wrapper.findAll('.interaction-bar__option')
    expect(options).toHaveLength(3)

    await options[0].trigger('click')
    await options[2].trigger('click')
    await wrapper.find('.interaction-bar__primary').trigger('click')
    await flushPromises()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('confirm-multi', {
      actionType: 'CHOOSE',
      payload: {
        choiceIds: ['opt-1', 'opt-3'],
        choiceLabels: ['方案A', '方案C'],
        choices: ['方案A', '方案C'],
      },
      comment: JSON.stringify(['方案A', '方案C']),
    })
  })

  it('renders custom input for DECISION with allowCustom: true', async () => {
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction({
            id: 'confirm-custom',
            requestType: 'DECISION',
            title: '选择方案或自定义',
            interactionSchemaJson: JSON.stringify({
              selection: 'single',
              options: [
                { id: 'opt-1', label: '方案A' },
              ],
              allowCustom: true,
            }),
          }),
        ],
      },
    })

    const customInputEl = wrapper.find('input[placeholder="自定义输入..."]')
    expect(customInputEl.exists()).toBe(true)
    expect(wrapper.findAll('.interaction-bar__option')).toHaveLength(1)

    // Custom input with no preset selected → submits custom text
    await customInputEl.setValue('自定义方案X')
    await wrapper.find('.interaction-bar__primary').trigger('click')
    await flushPromises()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('confirm-custom', {
      actionType: 'CHOOSE',
      payload: { choice: '自定义方案X', customChoice: '自定义方案X' },
      comment: '自定义方案X',
    })
  })

  it('submits custom text for single-select DECISION with allowCustom when typing without selecting preset', async () => {
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction({
            id: 'confirm-custom',
            requestType: 'DECISION',
            title: '选择方案或自定义',
            interactionSchemaJson: JSON.stringify({
              selection: 'single',
              options: [
                { id: 'opt-1', label: '方案A' },
                { id: 'opt-2', label: '方案B' },
              ],
              allowCustom: true,
            }),
          }),
        ],
      },
    })

    // Type custom text without clicking any preset option
    const customInputEl = wrapper.find('input[placeholder="自定义输入..."]')
    await customInputEl.setValue('完全自定义的方案文本')
    await wrapper.find('.interaction-bar__primary').trigger('click')
    await flushPromises()

    // Should submit the custom text, not the first preset option
    expect(confirmationApi.resolve).toHaveBeenCalledWith('confirm-custom', {
      actionType: 'CHOOSE',
      payload: { choice: '完全自定义的方案文本', customChoice: '完全自定义的方案文本' },
      comment: '完全自定义的方案文本',
    })
  })

  it('renders multi-field form for INPUT_REQUIRED with fields array', async () => {
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction({
            id: 'confirm-fields',
            requestType: 'INPUT_REQUIRED',
            title: '补充设计信息',
            interactionSchemaJson: JSON.stringify({
              fields: [
                { id: 'name', label: '模块名称', type: 'text', required: true, placeholder: '输入模块名' },
                { id: 'desc', label: '描述', type: 'textarea', required: false, placeholder: '可选描述' },
                { id: 'count', label: '数量', type: 'number', required: true },
              ],
            }),
          }),
        ],
      },
    })

    expect(wrapper.find('.interaction-bar__fields').exists()).toBe(true)
    expect(wrapper.findAll('.interaction-bar__field')).toHaveLength(3)
    expect(wrapper.find('label[for="field-name"]').text()).toContain('模块名称')
    expect(wrapper.find('label[for="field-name"] .interaction-bar__field-required').exists()).toBe(true)

    const nameInput = wrapper.find('#field-name')
    const descTextarea = wrapper.find('#field-desc')
    const countInput = wrapper.find('#field-count')

    expect(nameInput.exists()).toBe(true)
    expect(descTextarea.exists()).toBe(true)
    expect(countInput.exists()).toBe(true)

    await nameInput.setValue('AuthService')
    await descTextarea.setValue('认证服务模块')
    await countInput.setValue('3')
    await wrapper.find('.interaction-bar__primary').trigger('click')
    await flushPromises()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('confirm-fields', {
      actionType: 'SUPPLEMENT',
      payload: {
        input: 'AuthService\n认证服务模块\n3',
        fields: { name: 'AuthService', desc: '认证服务模块', count: '3' },
      },
      comment: undefined,
    })
  })

  it('disables submit when required fields are empty', async () => {
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction({
            id: 'confirm-fields',
            requestType: 'INPUT_REQUIRED',
            title: '补充信息',
            interactionSchemaJson: JSON.stringify({
              fields: [
                { id: 'name', label: '名称', type: 'text', required: true },
              ],
            }),
          }),
        ],
      },
    })

    expect(wrapper.find('.interaction-bar__primary').attributes('disabled')).toBeDefined()

    await wrapper.find('#field-name').setValue('filled')
    expect(wrapper.find('.interaction-bar__primary').attributes('disabled')).toBeUndefined()
  })
})
