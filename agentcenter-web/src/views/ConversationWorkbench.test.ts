import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import ConversationWorkbench from './ConversationWorkbench.vue'
import { sessionApi } from '../api/sessions'
import { useRuntimeStore } from '../stores/runtime'

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
  }

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
  }

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
  }

  return { runningWorkItem, runningSession, runningWorkflow, workflowDefinition, pendingConfirmation }
})

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
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('shows pause action while the workflow node is running', async () => {
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

  it('cancels the active session and releases the input when pause is clicked', async () => {
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

  it('shows current interactions above the input composer', async () => {
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
    expect(interactionBar.text()).toContain('当前需要交互')
    expect(interactionBar.text()).toContain('确认继续下一步')

    const composer = wrapper.find('.conversation-workbench__composer')
    expect(composer.find('.interaction-bar').exists()).toBe(true)
    expect(composer.find('.conversation-workbench__input-area').exists()).toBe(true)
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
    expect(document.body.textContent).toContain('System Prompt')
    expect(document.body.textContent).toContain('请生成 PRD')
    expect(document.body.textContent).toContain('Prompt、回复与事件')
    expect(document.body.textContent).toContain('发送给 Runtime 的 prompt_async 请求')
    expect(document.body.textContent).toContain('Agent 完整回复')
    expect(document.body.textContent).toContain('这是 Agent 的完整回复')
    expect(document.body.textContent).toContain('开始执行 Skill')
    expect(document.body.textContent).toContain('运行时开始调用 Skill')
    expect(document.body.textContent).toContain('正在流式回复')
    expect(document.body.textContent).toContain('界面展示')
    expect(document.body.textContent).toContain('复制此段')
  })
})
