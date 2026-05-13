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
    expect(wrapper.text()).not.toContain('需要你确认')
    expect(wrapper.text()).not.toContain('FE1002 · 确认项浮层交互回归')
    expect(wrapper.text()).toContain('问题 1')
    expect(wrapper.text()).toContain('问题 2')
    expect(wrapper.text()).toContain('确认')
    expect(wrapper.text()).toContain('确认继续下一步')
    expect(wrapper.findAll('.interaction-bar__tab')).toHaveLength(3)
    expect(wrapper.find('.interaction-bar__tab').text()).toBe('问题 1')
    expect(wrapper.find('.interaction-bar__body .interaction-bar__tabs').exists()).toBe(false)
  })

  it('previews multiple pending interactions in the confirmation tab', async () => {
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction({
            id: 'confirm-1',
            requestType: 'DECISION',
            title: '选择路线',
            optionsJson: JSON.stringify([
              { value: 'MVP', label: 'MVP 路线' },
              { value: 'FULL', label: '完整路线' },
            ]),
          }),
          makeInteraction({
            id: 'confirm-2',
            requestType: 'INPUT_REQUIRED',
            title: '补充说明',
          }),
        ],
      },
    })

    const tabs = wrapper.findAll('.interaction-bar__tab')
    await tabs[2].trigger('click')

    expect(wrapper.find('.interaction-bar__review').exists()).toBe(true)
    expect(wrapper.text()).toContain('请确认本轮交互的待提交选择')
    expect(wrapper.text()).toContain('MVP 路线')
    expect(wrapper.text()).toContain('尚未填写')
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

    expect(wrapper.text()).not.toContain('需要你确认选择')
    expect(wrapper.find('.interaction-bar__tabs').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('问题 1')
  })

  it('stacks decision options vertically', () => {
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction({
            id: 'confirm-stack',
            requestType: 'DECISION',
            title: '选择实现路线',
            optionsJson: '["MVP","完整","风险优先"]',
          }),
        ],
      },
    })

    const options = wrapper.find('.interaction-bar__control--options')
    expect(options.exists()).toBe(true)
    expect(options.classes()).toContain('interaction-bar__control--options')
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
    expect(wrapper.emitted('submitting')?.[0]).toEqual(['confirm-2'])
    expect(wrapper.emitted('resolved')?.[0]).toEqual(['confirm-2'])
  })

  it('uses option actionType instead of guessing from option text', async () => {
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction({
            id: 'confirm-action-type',
            requestType: 'DECISION',
            interactionType: 'WORKFLOW_ADVANCE',
            title: '选择推进动作',
            interactionSchemaJson: JSON.stringify({
              selection: 'single',
              options: [
                { id: 'go-next', label: '继续下一步', actionType: 'ADVANCE' },
                { id: 'more-work', label: '继续完善', actionType: 'SUPPLEMENT' },
              ],
            }),
          }),
        ],
      },
    })

    await wrapper.findAll('.interaction-bar__option')[0].trigger('click')
    await wrapper.find('.interaction-bar__primary').trigger('click')
    await flushPromises()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('confirm-action-type', {
      actionType: 'ADVANCE',
      payload: { choice: 'go-next', choiceId: 'go-next', choiceLabel: '继续下一步' },
      comment: '继续下一步',
    })
  })

  it('announces submission before the resolve request completes', async () => {
    let resolveRequest!: (value: ConfirmationRequestDto) => void
    vi.mocked(confirmationApi.resolve).mockReturnValueOnce(new Promise((resolve) => {
      resolveRequest = resolve
    }))
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction({
            id: 'confirm-submit',
            requestType: 'DECISION',
            title: '选择处理方式',
            optionsJson: '["重试","跳过"]',
          }),
        ],
      },
    })

    await wrapper.find('.interaction-bar__primary').trigger('click')

    expect(wrapper.emitted('submitting')?.[0]).toEqual(['confirm-submit'])
    expect(wrapper.emitted('resolved')).toBeFalsy()

    resolveRequest(makeInteraction({ id: 'confirm-submit' }))
    await flushPromises()

    expect(wrapper.emitted('resolved')?.[0]).toEqual(['confirm-submit'])
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

    expect(wrapper.text()).not.toContain('需要你授权')
    expect(wrapper.text()).toContain('允许 Agent 编辑文件：C:/.../.opencode/skills/planner/SKILL.md？')
    expect(wrapper.text()).toContain('允许一次')
    expect(wrapper.text()).toContain('本次会话允许同类请求')
    expect(wrapper.text()).toContain('拒绝')
  })

  it('shows OpenCode external directory permission scope', () => {
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction({
            id: 'confirm-external-dir',
            requestType: 'PERMISSION',
            title: 'OpenCode 请求访问外部目录',
            content: 'OpenCode permission request',
            interactionContextJson: JSON.stringify({
              permission: 'external_directory',
              filePath: '/Users/hzz/workspace/AgentCenter/agentcenter-bridge/src/test/resources/opencode-permission-fixture/src/protected',
              always: '/Users/hzz/workspace/AgentCenter/agentcenter-bridge/src/test/resources/opencode-permission-fixture/src/protected/*',
            }),
          }),
        ],
      },
    })

    expect(wrapper.text()).toContain('允许 Agent 访问外部目录：.../resources/opencode-permission-fixture/src/protected？')
    expect(wrapper.text()).toContain('这是 OpenCode 原生工具授权')
    expect(wrapper.text()).toContain('同类请求范围：/Users/hzz/workspace/AgentCenter/agentcenter-bridge/src/test/resources/opencode-permission-fixture/src/protected/*')
  })

  it('submits always for OpenCode permission requests', async () => {
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction({
            id: 'confirm-permission-always',
            requestType: 'PERMISSION',
            title: 'Allow command?',
            content: 'OpenCode permission request',
          }),
        ],
      },
    })

    const alwaysButton = wrapper.findAll('button').find(button => button.text() === '本次会话允许同类请求')
    expect(alwaysButton).toBeTruthy()
    await alwaysButton!.trigger('click')
    await flushPromises()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('confirm-permission-always', {
      actionType: 'APPROVE',
      payload: { reply: 'always' },
      comment: 'always',
    })
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
      comment: '方案A，方案C',
    })
  })

  it('offers explicit reject for exception interactions', async () => {
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction({
            id: 'confirm-exception-reject',
            requestType: 'EXCEPTION',
            title: '执行异常',
            contextSummary: '当前节点执行失败。',
          }),
        ],
      },
    })

    const rejectButton = wrapper.findAll('button').find(button => button.text() === '拒绝')
    expect(rejectButton).toBeTruthy()
    await rejectButton!.trigger('click')
    await flushPromises()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('confirm-exception-reject', {
      actionType: 'REJECT',
    })
    expect(wrapper.emitted('rejected')?.[0]).toEqual(['confirm-exception-reject'])
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

  it('submits custom text for DECISION with allowCustom and no preset options', async () => {
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction({
            id: 'confirm-custom-only',
            requestType: 'DECISION',
            title: '补充自定义方案',
            interactionSchemaJson: JSON.stringify({
              selection: 'single',
              allowCustom: true,
            }),
          }),
        ],
      },
    })

    const primary = wrapper.find('.interaction-bar__primary')
    expect(primary.attributes('disabled')).toBeDefined()

    await wrapper.find('input[placeholder="自定义输入..."]').setValue('请 Agent 先列出 3 个方案')
    expect(primary.attributes('disabled')).toBeUndefined()
    await primary.trigger('click')
    await flushPromises()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('confirm-custom-only', {
      actionType: 'CHOOSE',
      payload: { choice: '请 Agent 先列出 3 个方案', customChoice: '请 Agent 先列出 3 个方案' },
      comment: '请 Agent 先列出 3 个方案',
    })
  })

  it('renders artifact review options instead of plain approval copy', async () => {
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction({
            id: 'confirm-review',
            requestType: 'APPROVAL',
            interactionType: 'ARTIFACT_REVIEW',
            title: '审阅 LLD 草稿',
            optionsJson: JSON.stringify([
              { id: 'PASS', label: '通过' },
              { id: 'REVISE', label: '需要调整' },
            ]),
          }),
        ],
      },
    })

    expect(wrapper.findAll('.interaction-bar__option')).toHaveLength(2)
    expect(wrapper.text()).toContain('通过')
    expect(wrapper.text()).toContain('需要调整')
    expect(wrapper.find('.interaction-bar__review-note').exists()).toBe(true)

    await wrapper.findAll('.interaction-bar__option')[1].trigger('click')
    await wrapper.find('.interaction-bar__review-note').setValue('补上失败回滚策略')
    await wrapper.find('.interaction-bar__primary').trigger('click')
    await flushPromises()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('confirm-review', {
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
      comment: 'AuthService\n认证服务模块\n3',
    })
  })

  it('renders select and checkbox fields for INPUT_REQUIRED', async () => {
    const wrapper = mount(ConversationInteractionBar, {
      props: {
        interactions: [
          makeInteraction({
            id: 'confirm-field-controls',
            requestType: 'INPUT_REQUIRED',
            title: '补充发布参数',
            interactionSchemaJson: JSON.stringify({
              fields: [
                {
                  id: 'priority',
                  label: '优先级',
                  type: 'select',
                  required: true,
                  options: [
                    { value: 'low', label: '低' },
                    { value: 'high', label: '高' },
                  ],
                },
                { id: 'accepted', label: '我确认风险', type: 'checkbox', required: true },
              ],
            }),
          }),
        ],
      },
    })

    const primary = wrapper.find('.interaction-bar__primary')
    expect(primary.attributes('disabled')).toBeDefined()

    await wrapper.find('#field-priority').setValue('high')
    expect(primary.attributes('disabled')).toBeDefined()

    await wrapper.find('#field-accepted').setValue(true)
    expect(primary.attributes('disabled')).toBeUndefined()
    await primary.trigger('click')
    await flushPromises()

    expect(confirmationApi.resolve).toHaveBeenCalledWith('confirm-field-controls', {
      actionType: 'SUPPLEMENT',
      payload: {
        input: '高\n是',
        fields: { priority: 'high', accepted: 'true' },
      },
      comment: '高\n是',
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
