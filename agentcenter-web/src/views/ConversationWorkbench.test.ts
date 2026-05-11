import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import ConversationWorkbench from './ConversationWorkbench.vue'
import MessageList from '../components/conversation/MessageList.vue'
import { sessionApi } from '../api/sessions'
import { workflowApi } from '../api/workflows'
import { artifactApi } from '../api/artifacts'
import { confirmationApi } from '../api/confirmations'
import { eventApi } from '../api/events'
import { useRuntimeStore } from '../stores/runtime'
import { useRuntimeSettingsStore } from '../stores/runtimeSettings'
import { useWorkflowStore } from '../stores/workflows'
import type { AgentMessageDto, AgentSessionDto, ArtifactDto, ConfirmationRequestDto, WorkflowInstanceDto } from '../api/types'

const mocks = vi.hoisted(() => {
  const runningWorkItem = {
    id: 'work-1',
    code: 'FE0003',
    type: 'FE',
    title: '社区共享工具柜借还流程',
    description: null,
    status: 'BACKLOG',
    priority: 'MEDIUM',
    projectId: null,
    spaceId: null,
    iterationId: null,
    assigneeUserId: null,
    currentWorkflowInstanceId: 'wf-1',
    workflowSummary: {
      instanceId: 'wf-1',
      status: 'RUNNING',
      currentNodeInstanceId: 'node-1',
      nodes: [
        {
          id: 'node-1',
          definitionName: '详细设计 (LLD)',
          skillName: 'lld-design',
          status: 'RUNNING',
          errorMessage: null,
        },
      ],
      stages: [
        {
          id: 'node-1',
          stageKey: 'detail_design',
          name: '详细设计 (LLD)',
          skillName: 'lld-design',
          status: 'RUNNING',
          dynamicNodeCount: 0,
          recoveryCount: 0,
          pendingConfirmationCount: 0,
          latestSummary: '详细设计 (LLD)',
          errorMessage: null,
        },
      ],
    },
    createdAt: '2026-05-08T10:00:00Z',
    updatedAt: '2026-05-08T10:00:00Z',
  }

  const runningSession = {
    id: 'session-1',
    sessionType: 'WORK_ITEM',
    title: 'FE0003 · 社区共享工具柜借还流程',
    workItemId: 'work-1',
    workflowInstanceId: 'wf-1',
    runtimeType: 'OPENCODE',
    status: 'ACTIVE',
    createdAt: '2026-05-08T10:00:00Z',
  } satisfies AgentSessionDto

  const runningWorkflow = {
    id: 'wf-1',
    workItemId: 'work-1',
    workflowDefinitionId: 'wf-def-1',
    status: 'RUNNING',
    currentNodeInstanceId: 'node-1',
    nodes: [
      {
        id: 'node-1',
        nodeDefinitionId: 'node-def-1',
        status: 'RUNNING',
        inputArtifactId: null,
        outputArtifactId: null,
        agentSessionId: 'session-1',
        startedAt: null,
        completedAt: null,
        errorMessage: null,
      },
    ],
    startedAt: null,
    completedAt: null,
  } satisfies WorkflowInstanceDto

  const workflowDefinition = {
    id: 'wf-def-1',
    workItemType: 'FE',
    name: 'FE 标准工作流',
    versionNo: 1,
    status: 'ENABLED',
    isDefault: true,
    nodes: [
      {
        id: 'node-def-1',
        nodeKey: 'lld',
        name: '详细设计 (LLD)',
        orderNo: 1,
        skillName: 'lld-design',
        inputPolicy: 'WORK_ITEM_ONLY',
        outputArtifactType: 'MARKDOWN',
        requiredConfirmation: false,
      },
    ],
  }

  const pendingConfirmation = {
    id: 'confirm-1',
    requestType: 'APPROVAL',
    status: 'PENDING',
    workItemId: 'work-1',
    workItemCode: 'FE0003',
    workItemType: 'FE',
    workItemTitle: '社区共享工具柜借还流程',
    workflowInstanceId: 'wf-1',
    workflowNodeInstanceId: 'node-1',
    workflowNodeName: '详细设计 (LLD)',
    agentSessionId: 'session-1',
    skillName: 'lld-design',
    title: '确认继续下一步',
    content: '请确认详细设计产物。',
    contextSummary: '详细设计产物已生成，等待确认。',
    optionsJson: null,
    priority: 'MEDIUM',
    createdAt: '2026-05-08T10:01:00Z',
  } satisfies ConfirmationRequestDto

  const capturedArtifact = {
    id: 'art-1',
    workItemId: 'work-1',
    workflowInstanceId: 'wf-1',
    workflowNodeInstanceId: 'node-1',
    artifactType: 'MARKDOWN',
    title: 'FE0003 详细设计.md',
    content: '# 详细设计',
    createdAt: '2026-05-08T10:03:00Z',
  } satisfies ArtifactDto

  return { runningWorkItem, runningSession, runningWorkflow, workflowDefinition, pendingConfirmation, capturedArtifact }
})

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((res) => {
    resolve = res
  })
  return { promise, resolve }
}

vi.mock('../api/workItems', () => ({
  workItemApi: {
    list: vi.fn().mockResolvedValue([mocks.runningWorkItem]),
    getById: vi.fn().mockResolvedValue(mocks.runningWorkItem),
  },
}))

vi.mock('../api/workflows', () => ({
  workflowApi: {
    listDefinitions: vi.fn().mockResolvedValue([mocks.workflowDefinition]),
    getInstance: vi.fn().mockResolvedValue(mocks.runningWorkflow),
  },
}))

vi.mock('../api/sessions', () => ({
  sessionApi: {
    list: vi.fn().mockResolvedValue([mocks.runningSession]),
    getById: vi.fn().mockResolvedValue(mocks.runningSession),
    getMessages: vi.fn().mockResolvedValue([]),
    sendMessage: vi.fn().mockResolvedValue({
      id: 'msg-user-1',
      sessionId: 'session-1',
      role: 'USER',
      content: '补充信息',
      contentFormat: 'TEXT',
      status: 'COMPLETED',
      seqNo: 1,
      createdAt: '2026-05-08T10:02:00Z',
      workflowNodeInstanceId: 'node-1',
    }),
    create: vi.fn(),
    cancel: vi.fn().mockResolvedValue(undefined),
  },
}))

vi.mock('../api/events', () => ({
  eventApi: {
    streamSessionEvents: vi.fn().mockReturnValue({ close: vi.fn() }),
  },
}))

vi.mock('../api/runtimeResources', () => ({
  runtimeResourceApi: {
    refreshSkills: vi.fn(),
  },
}))

vi.mock('../api/artifacts', () => ({
  artifactApi: {
    get: vi.fn(),
    listByWorkItem: vi.fn().mockResolvedValue([]),
  },
}))

vi.mock('../api/confirmations', () => ({
  confirmationApi: {
    list: vi.fn((status?: string) => Promise.resolve(status === 'PENDING' ? [mocks.pendingConfirmation] : [])),
    getById: vi.fn(),
    enterSession: vi.fn(),
    resolve: vi.fn().mockResolvedValue({}),
    reject: vi.fn(),
  },
}))

describe('ConversationWorkbench.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(sessionApi.list).mockResolvedValue([mocks.runningSession])
    vi.mocked(sessionApi.getById).mockResolvedValue(mocks.runningSession)
    vi.mocked(sessionApi.getMessages).mockResolvedValue([])
    vi.mocked(artifactApi.get).mockResolvedValue(mocks.capturedArtifact)
    vi.mocked(artifactApi.listByWorkItem).mockResolvedValue([])
    vi.mocked(sessionApi.sendMessage).mockResolvedValue({
      id: 'msg-user-1',
      sessionId: 'session-1',
      role: 'USER',
      content: '补充信息',
      contentFormat: 'TEXT',
      status: 'COMPLETED',
      seqNo: 1,
      createdAt: '2026-05-08T10:02:00Z',
      workflowNodeInstanceId: 'node-1',
    })
    vi.mocked(workflowApi.getInstance).mockResolvedValue(mocks.runningWorkflow)
    vi.mocked(confirmationApi.list).mockImplementation((status?: string) =>
      Promise.resolve(status === 'PENDING' ? [mocks.pendingConfirmation] : [])
    )
    vi.mocked(confirmationApi.resolve).mockResolvedValue(mocks.pendingConfirmation)
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('shows pause action while the workflow node is running', async () => {
    vi.mocked(confirmationApi.list).mockResolvedValue([])
    const pinia = createPinia()
    setActivePinia(pinia)
    const wrapper = mount(ConversationWorkbench, {
      props: {
        workItemId: 'work-1',
        targetSessionId: 'session-1',
      },
      global: {
        plugins: [pinia],
      },
    })

    await flushPromises()

    const sendButton = wrapper.find('.conversation-workbench__send')
    expect(sendButton.classes()).toContain('conversation-workbench__send--pause')
    expect(sendButton.attributes('aria-label')).toBe('暂停当前回复')
    expect(wrapper.find('.node-state-area--running').exists()).toBe(false)
  })

  it('does not reconnect stale SSE after switching target sessions', async () => {
    const oldSession = {
      ...mocks.runningSession,
      id: 'session-old',
      sessionType: 'GENERAL',
      title: '旧会话',
      workItemId: null,
      workflowInstanceId: null,
    } satisfies AgentSessionDto
    const currentSession = {
      ...mocks.runningSession,
      id: 'session-current',
      sessionType: 'GENERAL',
      title: '当前会话',
      workItemId: null,
      workflowInstanceId: null,
    } satisfies AgentSessionDto
    const oldMessages = deferred<AgentMessageDto[]>()

    vi.mocked(sessionApi.list).mockResolvedValue([oldSession, currentSession])
    vi.mocked(sessionApi.getById).mockImplementation((id: string) =>
      Promise.resolve(id === 'session-old' ? oldSession : currentSession)
    )
    vi.mocked(sessionApi.getMessages).mockImplementation((id: string) =>
      id === 'session-old' ? oldMessages.promise : Promise.resolve([])
    )

    const pinia = createPinia()
    setActivePinia(pinia)
    const wrapper = mount(ConversationWorkbench, {
      props: {
        targetSessionId: 'session-old',
      },
      global: {
        plugins: [pinia],
      },
    })

    await vi.waitFor(() => {
      expect(sessionApi.getMessages).toHaveBeenCalledWith('session-old')
    })

    await wrapper.setProps({ targetSessionId: 'session-current' })
    await flushPromises()

    oldMessages.resolve([])
    await flushPromises()

    const runtimeStore = useRuntimeStore()
    expect(runtimeStore.activeSessionId).toBe('session-current')
    expect(vi.mocked(eventApi.streamSessionEvents).mock.calls.map(([sessionId]) => sessionId)).toEqual(['session-current'])
  })

  it('opens captured artifacts by artifactId from conversation evidence', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const wrapper = mount(ConversationWorkbench, {
      props: {
        workItemId: 'work-1',
        targetSessionId: 'session-1',
      },
      global: {
        plugins: [pinia],
      },
    })

    await flushPromises()
    wrapper.findComponent(MessageList).vm.$emit('open-artifact', { artifactId: 'art-1' })
    await flushPromises()

    expect(artifactApi.get).toHaveBeenCalledWith('art-1')
    expect(wrapper.emitted('open-artifact')?.[0]).toEqual([mocks.capturedArtifact])
  })

  it('cancels the active session and releases the input when pause is clicked', async () => {
    vi.mocked(confirmationApi.list).mockResolvedValue([])
    const pinia = createPinia()
    setActivePinia(pinia)
    const wrapper = mount(ConversationWorkbench, {
      props: {
        workItemId: 'work-1',
        targetSessionId: 'session-1',
      },
      global: {
        plugins: [pinia],
      },
    })

    await flushPromises()

    const runtimeStore = useRuntimeStore()
    runtimeStore.streamingText = '正在回复'

    await wrapper.find('.conversation-workbench__send').trigger('click')
    await flushPromises()

    expect(sessionApi.cancel).toHaveBeenCalledWith('session-1')
    expect(runtimeStore.streamingText).toBe('')

    const sendButton = wrapper.find('.conversation-workbench__send')
    expect(sendButton.classes()).not.toContain('conversation-workbench__send--pause')
    expect(sendButton.attributes('aria-label')).toBe('发送消息')
    expect(wrapper.find('.conversation-workbench__input').attributes('disabled')).toBeUndefined()
  })

  it('continues a paused node with a lightweight runtime message', async () => {
    vi.mocked(confirmationApi.list).mockResolvedValue([])

    const pinia = createPinia()
    setActivePinia(pinia)
    const wrapper = mount(ConversationWorkbench, {
      props: {
        workItemId: 'work-1',
        targetSessionId: 'session-1',
      },
      global: {
        plugins: [pinia],
      },
    })

    await flushPromises()

    const runtimeStore = useRuntimeStore()
    runtimeStore.lastNodeState = 'IN_PROGRESS'
    runtimeStore.streamingText = '正在回复'
    await nextTick()

    await wrapper.find('.conversation-workbench__send').trigger('click')
    await flushPromises()

    const continueButton = wrapper.findAll('.wf-control__btn')
      .find((button) => button.text() === '继续当前')
    if (!continueButton) {
      throw new Error('Continue current button not found')
    }

    await continueButton.trigger('click')
    await flushPromises()

    expect(sessionApi.sendMessage).toHaveBeenLastCalledWith(
      'session-1',
      expect.objectContaining({
        content: expect.stringContaining('不要重新开始节点'),
        workflowUserAction: 'CONTINUE_CURRENT',
        workflowNodeInstanceId: 'node-1',
      }),
    )
    expect(vi.mocked(sessionApi.sendMessage).mock.calls.at(-1)?.[1].content).not.toBe('[CONTINUE_CURRENT]')
  })

  it('routes typed input after pause as lightweight continue current action', async () => {
    vi.mocked(confirmationApi.list).mockResolvedValue([])

    const pinia = createPinia()
    setActivePinia(pinia)
    const wrapper = mount(ConversationWorkbench, {
      props: {
        workItemId: 'work-1',
        targetSessionId: 'session-1',
      },
      global: {
        plugins: [pinia],
      },
    })

    await flushPromises()

    const runtimeStore = useRuntimeStore()
    runtimeStore.lastNodeState = 'IN_PROGRESS'
    runtimeStore.streamingText = '正在回复'
    await nextTick()

    await wrapper.find('.conversation-workbench__send').trigger('click')
    await flushPromises()

    const input = wrapper.find<HTMLInputElement>('.conversation-workbench__input')
    await input.setValue('继续完善剩余内容')
    await wrapper.find('form.conversation-workbench__input-area').trigger('submit')
    await flushPromises()

    expect(sessionApi.sendMessage).toHaveBeenLastCalledWith(
      'session-1',
      expect.objectContaining({
        content: '继续完善剩余内容',
        workflowUserAction: 'CONTINUE_CURRENT',
        workflowNodeInstanceId: 'node-1',
      }),
    )
  })

  it('uses the composer slot for current interactions instead of the text input', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const wrapper = mount(ConversationWorkbench, {
      props: {
        workItemId: 'work-1',
        targetSessionId: 'session-1',
      },
      global: {
        plugins: [pinia],
      },
    })

    await flushPromises()

    const interactionBar = wrapper.find('.interaction-bar')
    expect(interactionBar.exists()).toBe(true)
    expect(interactionBar.text()).toContain('需要你确认')
    expect(interactionBar.text()).toContain('确认继续下一步')

    const composer = wrapper.find('.conversation-workbench__composer')
    expect(composer.find('.interaction-bar').exists()).toBe(true)
    expect(composer.find('.conversation-workbench__input-area').exists()).toBe(false)
  })

  it('does not show interactions that belong to another session on the same work item', async () => {
    const otherSession = {
      ...mocks.runningSession,
      id: 'session-2',
    }
    vi.mocked(sessionApi.list).mockResolvedValueOnce([mocks.runningSession, otherSession])
    vi.mocked(sessionApi.getById).mockResolvedValueOnce(otherSession)

    const pinia = createPinia()
    setActivePinia(pinia)
    const wrapper = mount(ConversationWorkbench, {
      props: {
        workItemId: 'work-1',
        targetSessionId: 'session-2',
      },
      global: {
        plugins: [pinia],
      },
    })

    await flushPromises()

    expect(wrapper.find('.interaction-bar').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('确认继续下一步')

    const sendButton = wrapper.find('.conversation-workbench__send')
    expect(sendButton.classes()).not.toContain('conversation-workbench__send--pause')
    expect(sendButton.attributes('aria-label')).toBe('发送消息')
  })

  it('does not leak workflow interactions into a general session', async () => {
    const generalSession: AgentSessionDto = {
      id: 'general-session',
      sessionType: 'GENERAL',
      title: '通用会话',
      workItemId: null,
      workflowInstanceId: null,
      runtimeType: 'OPENCODE',
      status: 'ACTIVE',
      createdAt: '2026-05-08T10:00:00Z',
    }
    const workflowConfirmation: ConfirmationRequestDto = {
      ...mocks.pendingConfirmation,
      agentSessionId: null,
    }
    vi.mocked(sessionApi.list).mockResolvedValueOnce([generalSession, mocks.runningSession])
    vi.mocked(sessionApi.getById).mockResolvedValueOnce(generalSession)
    vi.mocked(confirmationApi.list).mockImplementation((status?: string) =>
      Promise.resolve(status === 'PENDING' ? [workflowConfirmation] : [])
    )

    const pinia = createPinia()
    setActivePinia(pinia)
    useWorkflowStore().setActiveInstance(mocks.runningWorkflow)
    const wrapper = mount(ConversationWorkbench, {
      props: {
        targetSessionId: 'general-session',
      },
      global: {
        plugins: [pinia],
      },
    })

    await flushPromises()

    expect(wrapper.find('.interaction-bar').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('确认继续下一步')
    expect(wrapper.find('.conversation-workbench__send').attributes('aria-label')).toBe('发送消息')
  })

  it('submits exception recovery from the composer with SUPPLEMENT', async () => {
    const failedWorkflow: WorkflowInstanceDto = {
      ...mocks.runningWorkflow,
      status: 'FAILED',
      nodes: [
        {
          ...mocks.runningWorkflow.nodes[0],
          status: 'FAILED',
          errorMessage: 'Agent Runtime 超时，没有返回可用输出',
        },
      ],
    }
    const exceptionConfirmation: ConfirmationRequestDto = {
      ...mocks.pendingConfirmation,
      id: 'exception-1',
      requestType: 'EXCEPTION',
      title: '节点执行异常',
      content: 'Agent Runtime 超时，没有返回可用输出',
      optionsJson: JSON.stringify([
        { value: 'RETRY', label: '重试当前节点' },
        { value: 'SUPPLEMENT', label: '补充信息后继续' },
        { value: 'SKIP', label: '跳过该节点继续' },
      ]),
    }
    vi.mocked(workflowApi.getInstance).mockResolvedValue(failedWorkflow)
    vi.mocked(confirmationApi.list).mockImplementation((status?: string) =>
      Promise.resolve(status === 'PENDING' ? [exceptionConfirmation] : [])
    )
    vi.mocked(confirmationApi.resolve).mockResolvedValue({
      ...exceptionConfirmation,
      status: 'RESOLVED',
    })

    const pinia = createPinia()
    setActivePinia(pinia)
    const wrapper = mount(ConversationWorkbench, {
      props: {
        workItemId: 'work-1',
        targetSessionId: 'session-1',
      },
      global: {
        plugins: [pinia],
      },
    })

    await flushPromises()

    expect(wrapper.find('.conversation-workbench__input-area').exists()).toBe(false)
    expect(wrapper.find('.interaction-bar').exists()).toBe(true)

    const textarea = wrapper.find<HTMLTextAreaElement>('.interaction-bar__textarea')
    await textarea.setValue('请继续，但先限制在只读检查范围内')
    await wrapper.find('.interaction-bar__primary').trigger('click')
    await flushPromises()

    expect(confirmationApi.resolve).toHaveBeenCalledWith(
      exceptionConfirmation.id,
      expect.objectContaining({
        actionType: 'SUPPLEMENT',
        comment: '请继续，但先限制在只读检查范围内',
        payload: { input: '请继续，但先限制在只读检查范围内' },
      }),
    )
    expect(sessionApi.sendMessage).not.toHaveBeenCalled()
  })

  it('does not show the workflow advance control while an interaction is active', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const wrapper = mount(ConversationWorkbench, {
      props: {
        workItemId: 'work-1',
        targetSessionId: 'session-1',
      },
      global: {
        plugins: [pinia],
      },
    })

    await flushPromises()

    const runtimeStore = useRuntimeStore()
    runtimeStore.lastNodeState = 'READY_TO_ADVANCE'
    await nextTick()

    expect(wrapper.find('.interaction-bar').exists()).toBe(true)
    expect(wrapper.find('.wf-control').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('进入下一步')
  })

  it('resolves permission interactions with APPROVE instead of CHOOSE', async () => {
    const permissionConfirmation: ConfirmationRequestDto = {
      ...(mocks.pendingConfirmation as ConfirmationRequestDto),
      id: 'perm-session-1-write',
      requestType: 'PERMISSION',
      title: '允许写入文件？',
      content: 'OpenCode permission request',
      optionsJson: JSON.stringify([
        { value: 'APPROVE', label: '允许' },
        { value: 'REJECT', label: '拒绝' },
      ]),
    }
    vi.mocked(confirmationApi.list).mockImplementation((status?: string) =>
      Promise.resolve(status === 'PENDING' ? [permissionConfirmation] : [])
    )

    const pinia = createPinia()
    setActivePinia(pinia)
    const wrapper = mount(ConversationWorkbench, {
      props: {
        workItemId: 'work-1',
        targetSessionId: 'session-1',
      },
      global: {
        plugins: [pinia],
      },
    })

    await flushPromises()

    wrapper.findComponent(MessageList).vm.$emit('resolve-confirmation', permissionConfirmation.id, 'APPROVE', {
      requestType: 'PERMISSION',
    })
    await flushPromises()

    expect(confirmationApi.resolve).toHaveBeenCalledWith(
      permissionConfirmation.id,
      expect.objectContaining({
        actionType: 'APPROVE',
        payload: { choice: 'APPROVE' },
        comment: 'APPROVE',
      }),
    )
  })

  it('shows prompt debug replies, annotated events, and copy actions', async () => {
    vi.mocked(sessionApi.getMessages).mockResolvedValueOnce([
      {
        id: 'msg-1',
        sessionId: 'session-1',
        role: 'ASSISTANT',
        content: '这是 Agent 的完整回复',
        contentFormat: 'MARKDOWN',
        status: 'COMPLETED',
        seqNo: 2,
        createdAt: '2026-05-08T10:02:00Z',
        workflowNodeInstanceId: 'node-1',
      },
    ])

    const pinia = createPinia()
    setActivePinia(pinia)
    useRuntimeSettingsStore().setPromptDebugPanelEnabled(true)
    mount(ConversationWorkbench, {
      props: {
        workItemId: 'work-1',
        targetSessionId: 'session-1',
      },
      global: {
        plugins: [pinia],
      },
    })

    await flushPromises()

    const runtimeStore = useRuntimeStore()
    runtimeStore.events = [
      {
        id: 'event-prompt',
        sessionId: 'session-1',
        workItemId: 'work-1',
        workflowInstanceId: 'wf-1',
        workflowNodeInstanceId: 'node-1',
        eventType: 'PROCESS_TRACE',
        eventSource: 'runtime',
        payloadJson: JSON.stringify({
          kind: 'prompt_debug',
          agent: 'build',
          userPrompt: '请生成 PRD',
          systemPrompt: '系统提示词',
        }),
        createdAt: '2026-05-08T10:01:00Z',
      },
      {
        id: 'event-skill',
        sessionId: 'session-1',
        workItemId: 'work-1',
        workflowInstanceId: 'wf-1',
        workflowNodeInstanceId: 'node-1',
        eventType: 'SKILL_STARTED',
        eventSource: 'runtime',
        payloadJson: JSON.stringify({ skillName: 'prd-design', summary: '开始执行 PRD Skill' }),
        createdAt: '2026-05-08T10:01:30Z',
      },
      {
        id: 'event-delta',
        sessionId: 'session-1',
        workItemId: 'work-1',
        workflowInstanceId: 'wf-1',
        workflowNodeInstanceId: 'node-1',
        eventType: 'ASSISTANT_DELTA',
        eventSource: 'runtime',
        payloadJson: JSON.stringify({ delta: '增量片段' }),
        createdAt: '2026-05-08T10:01:45Z',
      },
    ]
    runtimeStore.streamingText = '正在流式输出'
    await nextTick()

    const actionButtons = Array.from(document.body.querySelectorAll<HTMLButtonElement>('.prompt-debug-float__toggle'))
    const fullscreenButton = actionButtons.find((button) => button.textContent?.includes('全屏'))
    expect(fullscreenButton).toBeDefined()
    fullscreenButton?.click()
    await nextTick()

    expect(document.body.querySelector('.prompt-debug-float--fullscreen')).not.toBeNull()
    expect(document.body.textContent).toContain('还原')
    expect(document.body.textContent).toContain('系统提示词')
    expect(document.body.textContent).toContain('请生成 PRD')
    expect(document.body.textContent).toContain('本轮回合时间线')
    expect(document.body.textContent).toContain('输入 / 运行')
    expect(document.body.textContent).toContain('Agent 完整回复')
    expect(document.body.textContent).toContain('这是 Agent 的完整回复')
    expect(document.body.textContent).toContain('开始执行 Skill')
    expect(document.body.textContent).toContain('运行时开始调用 Skill')
    expect(document.body.textContent).toContain('Agent 增量回复')
    expect(document.body.textContent).toContain('增量片段')
    expect(document.body.textContent).toContain('正在流式回复')
    expect(document.body.textContent).toContain('界面展示')
    expect(document.body.textContent).toContain('复制此段')
  })
})
