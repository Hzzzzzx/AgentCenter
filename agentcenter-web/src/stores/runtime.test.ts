import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useRuntimeStore } from './runtime'
import { useConfirmationStore } from './confirmations'
import { useWorkflowStore } from './workflows'
import { useSessionStore } from './sessions'
import type { AgentMessageDto, RuntimeEventDto } from '../api/types'

let capturedOnEvent: ((event: RuntimeEventDto) => void) | null = null

vi.mock('../api/events', () => ({
  eventApi: {
    streamSessionEvents: vi.fn().mockImplementation((_sessionId: string, onEvent: (event: RuntimeEventDto) => void) => {
      capturedOnEvent = onEvent
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
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('SKILL_COMPLETED refreshes workflow store when workflowInstanceId present', async () => {
    const { workflowApi } = await import('../api/workflows')
    const runtimeStore = useRuntimeStore()

    runtimeStore.connectSSE('sess-1')
    expect(capturedOnEvent).toBeTruthy()

    capturedOnEvent!(makeEvent({
      eventType: 'SKILL_COMPLETED',
      workflowInstanceId: 'inst-42',
    }))

    await vi.dynamicImportSettled()

    expect(workflowApi.getInstance).toHaveBeenCalledWith('inst-42')
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

  it('CONFIRMATION_CREATED also refreshes workflow when workflowInstanceId present', async () => {
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

    expect(workflowApi.getInstance).toHaveBeenCalledWith('inst-99')
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

    vi.advanceTimersByTime(20)
    expect(runtimeStore.streamingText).toBe('你好，AgentCenter')

    capturedOnEvent!(makeEvent({
      eventType: 'ASSISTANT_DELTA',
      id: 'evt-delta-1',
      payloadJson: JSON.stringify({ label: '你好，AgentCenter' }),
    }))

    vi.advanceTimersByTime(40)
    expect(runtimeStore.streamingText).toBe('你好，AgentCenter')
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
})
