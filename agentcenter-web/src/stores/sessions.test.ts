import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useSessionStore } from './sessions'
import { sessionApi } from '../api/sessions'
import type { AgentMessageDto, AgentSessionDto } from '../api/types'

vi.mock('../api/sessions', () => ({
  sessionApi: {
    list: vi.fn(),
    getById: vi.fn(),
    getMessages: vi.fn(),
    create: vi.fn(),
    sendMessage: vi.fn(),
    cancel: vi.fn(),
  },
}))

function makeSession(id: string): AgentSessionDto {
  return {
    id,
    sessionType: 'GENERAL',
    title: id,
    workItemId: null,
    workflowInstanceId: null,
    runtimeType: 'OPENCODE',
    status: 'ACTIVE',
    createdAt: '2026-05-11T00:00:00Z',
  }
}

function makeMessage(sessionId: string, content: string): AgentMessageDto {
  return {
    id: `msg-${sessionId}`,
    sessionId,
    role: 'ASSISTANT',
    content,
    contentFormat: 'TEXT',
    status: 'COMPLETED',
    seqNo: 1,
    createdAt: '2026-05-11T00:00:00Z',
    workflowNodeInstanceId: null,
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((res) => {
    resolve = res
  })
  return { promise, resolve }
}

describe('useSessionStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('keeps stale selectSession responses from replacing the active conversation messages', async () => {
    const firstMessages = deferred<AgentMessageDto[]>()
    const secondMessages = deferred<AgentMessageDto[]>()
    vi.mocked(sessionApi.getById)
      .mockResolvedValueOnce(makeSession('session-old'))
      .mockResolvedValueOnce(makeSession('session-current'))
    vi.mocked(sessionApi.getMessages)
      .mockReturnValueOnce(firstMessages.promise)
      .mockReturnValueOnce(secondMessages.promise)

    const store = useSessionStore()
    const firstSelect = store.selectSession('session-old')
    const secondSelect = store.selectSession('session-current')

    secondMessages.resolve([makeMessage('session-current', 'current reply')])
    await secondSelect
    expect(store.activeSession?.id).toBe('session-current')
    expect(store.messages.map((message) => message.content)).toEqual(['current reply'])

    firstMessages.resolve([makeMessage('session-old', 'stale reply')])
    await firstSelect
    expect(store.activeSession?.id).toBe('session-current')
    expect(store.messages.map((message) => message.content)).toEqual(['current reply'])
  })

  it('does not append a sendMessage result after the user switches sessions', async () => {
    const sentMessage = deferred<AgentMessageDto>()
    vi.mocked(sessionApi.sendMessage).mockReturnValue(sentMessage.promise)

    const store = useSessionStore()
    store.activeSession = makeSession('session-old')
    const send = store.sendMessage('hello')
    store.activeSession = makeSession('session-current')

    sentMessage.resolve(makeMessage('session-old', 'old user message'))
    await send

    expect(store.activeSession?.id).toBe('session-current')
    expect(store.messages).toEqual([])
  })

  it('does not let a stale createSession response replace a later selected session', async () => {
    const createdSession = deferred<AgentSessionDto>()
    vi.mocked(sessionApi.create).mockReturnValue(createdSession.promise)
    vi.mocked(sessionApi.getById).mockResolvedValue(makeSession('session-current'))
    vi.mocked(sessionApi.getMessages).mockResolvedValue([makeMessage('session-current', 'current reply')])

    const store = useSessionStore()
    const create = store.createSession({ sessionType: 'GENERAL', title: '通用会话' })
    const select = store.selectSession('session-current')

    await select
    createdSession.resolve(makeSession('session-created'))
    await create

    expect(store.activeSession?.id).toBe('session-current')
    expect(store.messages.map((message) => message.content)).toEqual(['current reply'])
    expect(store.sessions.map((session) => session.id)).toContain('session-created')
  })

  it('replaceMessages keeps only messages for the active session', () => {
    const store = useSessionStore()
    store.activeSession = makeSession('session-current')

    store.replaceMessages([
      makeMessage('session-old', 'old reply'),
      makeMessage('session-current', 'current reply'),
    ])

    expect(store.messages.map((message) => message.content)).toEqual(['current reply'])
  })

  it('does not merge streaming text into another session streaming message', () => {
    const store = useSessionStore()
    store.activeSession = makeSession('session-current')
    store.messages = [
      {
        ...makeMessage('session-old', 'old streaming'),
        id: 'msg-old-streaming',
        status: 'STREAMING',
      },
    ]

    store.appendStreamingMessage('current streaming')

    expect(store.messages).toHaveLength(2)
    expect(store.messages[0].content).toBe('old streaming')
    expect(store.messages[1].sessionId).toBe('session-current')
    expect(store.messages[1].content).toBe('current streaming')
  })
})
