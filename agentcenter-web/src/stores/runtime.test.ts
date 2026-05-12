import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useRuntimeStore } from './runtime'
import { useConfirmationStore } from './confirmations'
import { useWorkflowStore } from './workflows'
import { useSessionStore } from './sessions'
import type { AgentMessageDto, RuntimeEventDto } from '../api/types'

let capturedOnEvent: ((event: RuntimeEventDto) => void) | null = null
let capturedOnError: (() => void) | null = null
let capturedOnEvents: Array<(event: RuntimeEventDto) => void> = []

vi.mock('../api/events', () => ({
  eventApi: {
    streamSessionEvents: vi.fn().mockImplementation((
      _sessionId: string,
      onEvent: (event: RuntimeEventDto) => void,
      onError?: () => void,
    ) => {
      capturedOnEvent = onEvent
      capturedOnError = onError ?? null
      capturedOnEvents.push(onEvent)
      return { close: vi.fn() }
    }),
  },
}))

vi.mock('../api/confirmations', () => ({
  confirmationApi: {
    list: vi.fn().mockResolvedValue([]),
  },
}))

vi.mock('../api/workflows', () => ({
  workflowApi: {
    listDefinitions: vi.fn().mockResolvedValue([]),
    getInstance: vi.fn().mockResolvedValue({ id: 'inst-1', workItemId: null, workflowDefinitionId: 'wd-1', status: 'RUNNING', nodes: [], startedAt: null, completedAt: null }),
  },
}))

vi.mock('../api/workItems', () => ({
  workItemApi: {
    list: vi.fn().mockResolvedValue([]),
    getById: vi.fn().mockResolvedValue({
      id: 'work-1',
      code: 'FE1236',
      type: 'FE',
      title: '移动端适配优化',
      description: null,
      status: 'IN_PROGRESS',
      priority: 'MEDIUM',
      projectId: null,
      spaceId: null,
      iterationId: null,
      assigneeUserId: null,
      currentWorkflowInstanceId: 'inst-42',
      workflowSummary: null,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    }),
    create: vi.fn(),
  },
}))

vi.mock('../api/sessions', () => ({
  sessionApi: {
    list: vi.fn().mockResolvedValue([]),
    getById: vi.fn().mockResolvedValue({ id: 'sess-1', messages: [] }),
    getMessages: vi.fn().mockResolvedValue([]),
  },
}))

function makeEvent(overrides: Partial<RuntimeEventDto> & { eventType: RuntimeEventDto['eventType'] }): RuntimeEventDto {
  return {
    id: 'evt-1',
    sessionId: 'sess-1',
    workItemId: null,
    workflowInstanceId: null,
    workflowNodeInstanceId: null,
    eventSource: 'RUNTIME',
    payloadJson: null,
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

describe('useRuntimeStore — SSE event handlers', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    capturedOnEvent = null
    capturedOnError = null
    capturedOnEvents = []
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('SKILL_COMPLETED refreshes workflow store when workflowInstanceId present', async () => {
    const { workflowApi } = await import('../api/workflows')
    const { workItemApi } = await import('../api/workItems')
    const runtimeStore = useRuntimeStore()

    runtimeStore.connectSSE('sess-1')
    expect(capturedOnEvent).toBeTruthy()
    runtimeStore.markBusy()

    capturedOnEvent!(makeEvent({
      eventType: 'SKILL_COMPLETED',
      workflowInstanceId: 'inst-42',
      workItemId: 'work-1',
    }))

    await vi.dynamicImportSettled()

    expect(workflowApi.getInstance).toHaveBeenCalledWith('inst-42')
    expect(workItemApi.getById).toHaveBeenCalledWith('work-1')
    expect(runtimeStore.busy).toBe(false)
    const workflowStore = useWorkflowStore()
    expect(workflowStore.activeWorkflowInstance?.id).toBe('inst-1')
  })

  it('SKILL_COMPLETED without workflowInstanceId does not call refreshInstance', async () => {
    const { workflowApi } = await import('../api/workflows')
    const runtimeStore = useRuntimeStore()

    runtimeStore.connectSSE('sess-1')

    capturedOnEvent!(makeEvent({
      eventType: 'SKILL_COMPLETED',
      workflowInstanceId: null,
    }))

    await vi.dynamicImportSettled()

    expect(workflowApi.getInstance).not.toHaveBeenCalled()
  })

  it('CONFIRMATION_CREATED adds confirmation to pending list', () => {
    const runtimeStore = useRuntimeStore()

    runtimeStore.connectSSE('sess-1')

    const payload = {
      id: 'conf-new',
      requestType: 'APPROVAL',
      status: 'PENDING',
      workItemId: null,
      workflowInstanceId: null,
      workflowNodeInstanceId: null,
      agentSessionId: null,
      skillName: 'deploy',
      title: 'Deploy to production?',
      content: 'Please approve',
      contextSummary: null,
      optionsJson: null,
      priority: 'HIGH',
      createdAt: '2026-01-01T12:00:00Z',
    }

    capturedOnEvent!(makeEvent({
      eventType: 'CONFIRMATION_CREATED',
      payloadJson: JSON.stringify(payload),
    }))

    const confirmationStore = useConfirmationStore()
    expect(confirmationStore.pendingConfirmations).toHaveLength(1)
    expect(confirmationStore.pendingConfirmations[0].id).toBe('conf-new')
    expect(confirmationStore.pendingConfirmations[0].title).toBe('Deploy to production?')
  })

  it('CONFIRMATION_CREATED deduplicates by id', () => {
    const runtimeStore = useRuntimeStore()

    runtimeStore.connectSSE('sess-1')

    const payload = {
      id: 'conf-dup',
      requestType: 'CONFIRM',
      status: 'PENDING',
      workItemId: null,
      workflowInstanceId: null,
      workflowNodeInstanceId: null,
      agentSessionId: null,
      skillName: null,
      title: 'Dup test',
      content: null,
      contextSummary: null,
      optionsJson: null,
      priority: 'MEDIUM',
      createdAt: '2026-01-01T12:00:00Z',
    }

    const event = makeEvent({
      eventType: 'CONFIRMATION_CREATED',
      payloadJson: JSON.stringify(payload),
    })

    capturedOnEvent!(event)
    capturedOnEvent!(event)

    const confirmationStore = useConfirmationStore()
    expect(confirmationStore.pendingConfirmations).toHaveLength(1)
  })

  it('CONFIRMATION_CREATED with empty payloadJson triggers loadPending', async () => {
    const { confirmationApi } = await import('../api/confirmations')
    const runtimeStore = useRuntimeStore()

    runtimeStore.connectSSE('sess-1')

    capturedOnEvent!(makeEvent({
      eventType: 'CONFIRMATION_CREATED',
      payloadJson: null,
    }))

    await vi.dynamicImportSettled()

    expect(confirmationApi.list).toHaveBeenCalledWith('PENDING')
  })

  it('CONFIRMATION_CREATED with partial OpenCode question payload refreshes pending confirmations', async () => {
    const { confirmationApi } = await import('../api/confirmations')
    const runtimeStore = useRuntimeStore()

    runtimeStore.connectSSE('sess-1')

    capturedOnEvent!(makeEvent({
      eventType: 'CONFIRMATION_CREATED',
      payloadJson: JSON.stringify({
        confirmationId: 'question-session-tool',
        requestType: 'DECISION',
        interactionType: 'OPENCODE_QUESTION',
        title: '选择 FE/US 拆分方式',
        question: '请选择本次授权测试后的拆分路线。',
        options: '[{"id":"FE","label":"FE 优先"}]',
      }),
    }))

    await vi.dynamicImportSettled()

    expect(confirmationApi.list).toHaveBeenCalledWith('PENDING')
  })

  it('PERMISSION_REQUIRED refreshes pending confirmations', async () => {
    const { confirmationApi } = await import('../api/confirmations')
    const runtimeStore = useRuntimeStore()

    runtimeStore.connectSSE('sess-1')

    capturedOnEvent!(makeEvent({
      eventType: 'PERMISSION_REQUIRED',
      workflowInstanceId: 'inst-42',
      payloadJson: JSON.stringify({ permissionId: 'perm-1', title: 'Allow read?' }),
    }))

    await vi.dynamicImportSettled()

    expect(confirmationApi.list).toHaveBeenCalledWith('PENDING')
  })

  it('CONFIRMATION_CREATED does not refresh workflow (that is CONFIRMATION_RESOLVED)', async () => {
    const { workflowApi } = await import('../api/workflows')
    const runtimeStore = useRuntimeStore()

    runtimeStore.connectSSE('sess-1')

    const payload = {
      id: 'conf-wf',
      requestType: 'APPROVAL',
      status: 'PENDING',
      workItemId: null,
      workflowInstanceId: 'inst-99',
      workflowNodeInstanceId: null,
      agentSessionId: null,
      skillName: null,
      title: 'WF Confirm',
      content: null,
      contextSummary: null,
      optionsJson: null,
      priority: 'HIGH',
      createdAt: '2026-01-01T12:00:00Z',
    }

    capturedOnEvent!(makeEvent({
      eventType: 'CONFIRMATION_CREATED',
      workflowInstanceId: 'inst-99',
      payloadJson: JSON.stringify(payload),
    }))

    await vi.dynamicImportSettled()

    expect(workflowApi.getInstance).not.toHaveBeenCalled()
  })

  it('CONFIRMATION_RESOLVED removes from pending and refreshes workflow', async () => {
    const { workflowApi } = await import('../api/workflows')
    const runtimeStore = useRuntimeStore()

    runtimeStore.connectSSE('sess-1')

    const confirmationStore = useConfirmationStore()
    ;(confirmationStore as any).pendingConfirmations = [
      { id: 'conf-resolve', requestType: 'APPROVAL', status: 'PENDING', title: 'Test' } as any,
    ]

    capturedOnEvent!(makeEvent({
      eventType: 'CONFIRMATION_RESOLVED',
      workflowInstanceId: 'inst-100',
      payloadJson: JSON.stringify({ confirmationId: 'conf-resolve', actionType: 'APPROVE', requestType: 'APPROVAL' }),
    }))

    await vi.dynamicImportSettled()

    expect(workflowApi.getInstance).toHaveBeenCalledWith('inst-100')
    expect(confirmationStore.pendingConfirmations).toHaveLength(0)
  })

  it('deduplicates ASSISTANT_DELTA events by runtime event id', () => {
    vi.useFakeTimers()
    const runtimeStore = useRuntimeStore()

    runtimeStore.connectSSE('sess-1')
    capturedOnEvent!(makeEvent({
      eventType: 'ASSISTANT_DELTA',
      id: 'evt-delta-1',
      payloadJson: JSON.stringify({ label: '你好，AgentCenter' }),
    }))

    vi.advanceTimersByTime(40)
    expect(runtimeStore.streamingText).toBe('你好，AgentCenter')

    capturedOnEvent!(makeEvent({
      eventType: 'ASSISTANT_DELTA',
      id: 'evt-delta-1',
      payloadJson: JSON.stringify({ label: '你好，AgentCenter' }),
    }))

    vi.advanceTimersByTime(40)
    expect(runtimeStore.streamingText).toBe('你好，AgentCenter')
  })

  it('ignores late SSE events from a previous session after switching sessions', () => {
    vi.useFakeTimers()
    const runtimeStore = useRuntimeStore()

    runtimeStore.connectSSE('sess-1')
    const previousSessionCallback = capturedOnEvent
    runtimeStore.connectSSE('sess-2')

    previousSessionCallback!(makeEvent({
      eventType: 'ASSISTANT_DELTA',
      id: 'evt-late-sess-1',
      sessionId: 'sess-1',
      payloadJson: JSON.stringify({ delta: '旧会话晚到文本' }),
    }))

    vi.advanceTimersByTime(40)
    expect(runtimeStore.events).toHaveLength(0)
    expect(runtimeStore.streamingText).toBe('')
    expect(runtimeStore.busy).toBe(false)

    capturedOnEvent!(makeEvent({
      eventType: 'ASSISTANT_DELTA',
      id: 'evt-current-sess-2',
      sessionId: 'sess-2',
      payloadJson: JSON.stringify({ delta: '当前会话文本' }),
    }))

    vi.advanceTimersByTime(40)
    expect(runtimeStore.streamingText).toBe('当前会话文本')
    expect(capturedOnEvents).toHaveLength(2)
  })

  it('ignores SSE events without the subscribed session id', () => {
    vi.useFakeTimers()
    const runtimeStore = useRuntimeStore()

    runtimeStore.connectSSE('sess-1')
    capturedOnEvent!(makeEvent({
      eventType: 'ASSISTANT_DELTA',
      id: 'evt-missing-session',
      sessionId: null,
      payloadJson: JSON.stringify({ delta: '不应显示的文本' }),
    }))

    vi.advanceTimersByTime(40)
    expect(runtimeStore.events).toHaveLength(0)
    expect(runtimeStore.streamingText).toBe('')
    expect(runtimeStore.busy).toBe(false)
  })

  it('records a visible runtime connection error when the browser SSE stream fails', () => {
    const runtimeStore = useRuntimeStore()

    runtimeStore.connectSSE('sess-1')
    runtimeStore.markBusy()
    expect(capturedOnError).toBeTruthy()

    capturedOnError!()

    expect(runtimeStore.connected).toBe(false)
    expect(runtimeStore.busy).toBe(false)
    expect(runtimeStore.events).toHaveLength(1)
    expect(runtimeStore.events[0].eventType).toBe('ERROR')
    expect(runtimeStore.events[0].payloadJson).toContain('browser.sse.error')
  })

  it('syncs persisted assistant message when assistant completed event arrives', async () => {
    vi.useFakeTimers()
    const { sessionApi } = await import('../api/sessions')
    const sessionStore = useSessionStore()
    const runtimeStore = useRuntimeStore()
    const finalAssistant: AgentMessageDto = {
      id: 'msg-assistant-final',
      sessionId: 'sess-1',
      role: 'ASSISTANT',
      content: '最终回复内容',
      contentFormat: 'MARKDOWN',
      status: 'COMPLETED',
      seqNo: 2,
      createdAt: '2026-01-01T00:00:02Z',
      workflowNodeInstanceId: null,
    }
    await sessionStore.selectSession('sess-1')
    vi.mocked(sessionApi.getMessages).mockResolvedValueOnce([
      {
        id: 'msg-user',
        sessionId: 'sess-1',
        role: 'USER',
        content: '开始',
        contentFormat: 'TEXT',
        status: 'COMPLETED',
        seqNo: 1,
        createdAt: '2026-01-01T00:00:01Z',
        workflowNodeInstanceId: null,
      },
      finalAssistant,
    ])

    runtimeStore.connectSSE('sess-1')
    capturedOnEvent!(makeEvent({
      eventType: 'ASSISTANT_DELTA',
      id: 'evt-delta-final',
      payloadJson: JSON.stringify({ delta: '最终回复内容' }),
    }))
    capturedOnEvent!(makeEvent({
      eventType: 'ASSISTANT_COMPLETED',
      id: 'evt-assistant-completed',
      payloadJson: '{}',
    }))

    await vi.advanceTimersByTimeAsync(0)
    await vi.dynamicImportSettled()

    expect(sessionStore.messages.at(-1)?.content).toBe('最终回复内容')
    expect(runtimeStore.streamingText).toBe('')
  })

  it('does not replay historical assistant deltas when a final assistant message exists', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-05-07T00:00:00Z'))
    const sessionStore = useSessionStore()
    const runtimeStore = useRuntimeStore()
    const messages: AgentMessageDto[] = [
      {
        id: 'msg-user',
        sessionId: 'sess-1',
        role: 'USER',
        content: '请总结',
        contentFormat: 'TEXT',
        status: 'COMPLETED',
        seqNo: 1,
        createdAt: '2026-05-06T23:50:00Z',
        workflowNodeInstanceId: null,
      },
      {
        id: 'msg-assistant',
        sessionId: 'sess-1',
        role: 'ASSISTANT',
        content: '已经总结完成。',
        contentFormat: 'MARKDOWN',
        status: 'COMPLETED',
        seqNo: 2,
        createdAt: '2026-05-06T23:51:00Z',
        workflowNodeInstanceId: null,
      },
    ]
    sessionStore.messages = messages

    runtimeStore.connectSSE('sess-1')
    capturedOnEvent!(makeEvent({
      eventType: 'ASSISTANT_DELTA',
      id: 'evt-old-delta',
      payloadJson: JSON.stringify({ label: '历史流式文本' }),
      createdAt: '2026-05-06T23:51:00Z',
    }))

    vi.advanceTimersByTime(40)
    expect(runtimeStore.streamingText).toBe('')
  })

  it('keeps ASSISTANT_DELTA out of the retained runtime event timeline', () => {
    vi.useFakeTimers()
    const runtimeStore = useRuntimeStore()

    runtimeStore.connectSSE('sess-1')
    capturedOnEvent!(makeEvent({
      eventType: 'ASSISTANT_DELTA',
      id: 'evt-delta-timeline',
      payloadJson: JSON.stringify({ delta: '轻量流式文本' }),
    }))

    vi.advanceTimersByTime(40)

    expect(runtimeStore.events).toHaveLength(0)
    expect(runtimeStore.streamingText).toBe('轻量流式文本')
  })

  it('caps retained runtime events to a bounded recent window', () => {
    const runtimeStore = useRuntimeStore()

    runtimeStore.connectSSE('sess-1')
    for (let i = 0; i < 305; i += 1) {
      capturedOnEvent!(makeEvent({
        id: `evt-status-${i}`,
        eventType: 'STATUS',
        payloadJson: JSON.stringify({ status: 'running', label: `事件 ${i}` }),
        createdAt: `2026-01-01T00:00:${String(i % 60).padStart(2, '0')}Z`,
      }))
    }

    expect(runtimeStore.events).toHaveLength(300)
    expect(runtimeStore.events[0].id).toBe('evt-status-5')
    expect(runtimeStore.events.at(-1)?.id).toBe('evt-status-304')
  })

  it('PROCESS_TRACE with workflowInstanceId does not refresh workflow store for every trace event', async () => {
    const { workflowApi } = await import('../api/workflows')
    const { workItemApi } = await import('../api/workItems')
    const runtimeStore = useRuntimeStore()

    runtimeStore.connectSSE('sess-1')
    expect(capturedOnEvent).toBeTruthy()

    capturedOnEvent!(makeEvent({
      eventType: 'PROCESS_TRACE',
      workflowInstanceId: 'inst-42',
      workItemId: 'work-1',
    }))

    await vi.dynamicImportSettled()

    expect(workflowApi.getInstance).not.toHaveBeenCalled()
    expect(workItemApi.getById).not.toHaveBeenCalled()
  })

  it('PROCESS_TRACE without workflowInstanceId does not call refreshInstance', async () => {
    const { workflowApi } = await import('../api/workflows')
    const runtimeStore = useRuntimeStore()

    runtimeStore.connectSSE('sess-1')

    capturedOnEvent!(makeEvent({
      eventType: 'PROCESS_TRACE',
      workflowInstanceId: null,
    }))

    await vi.dynamicImportSettled()

    expect(workflowApi.getInstance).not.toHaveBeenCalled()
  })

  it('PROCESS_TRACE does not modify streamingText or busy state', () => {
    const runtimeStore = useRuntimeStore()

    runtimeStore.connectSSE('sess-1')
    runtimeStore.markIdle()

    capturedOnEvent!(makeEvent({
      eventType: 'PROCESS_TRACE',
      workflowInstanceId: null,
    }))

    expect(runtimeStore.streamingText).toBe('')
    expect(runtimeStore.busy).toBe(false)
  })
})
