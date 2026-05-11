import { defineStore } from 'pinia'
import { ref } from 'vue'
import { sessionApi } from '../api/sessions'
import type { AgentSessionDto, AgentMessageDto, SendMessageRequest } from '../api/types'

export const useSessionStore = defineStore('sessions', () => {
  const sessions = ref<AgentSessionDto[]>([])
  const activeSession = ref<AgentSessionDto | null>(null)
  const messages = ref<AgentMessageDto[]>([])
  const loading = ref(false)
  let selectionSeq = 0

  async function loadSessions() {
    loading.value = true
    try {
      sessions.value = await sessionApi.list()
    } finally {
      loading.value = false
    }
  }

  async function selectSession(id: string) {
    const seq = ++selectionSeq
    const [session, nextMessages] = await Promise.all([
      sessionApi.getById(id),
      sessionApi.getMessages(id),
    ])
    if (seq !== selectionSeq) return
    activeSession.value = session
    messages.value = nextMessages.filter((message) => message.sessionId === id)
  }

  async function createSession(data: { sessionType: string; title?: string; workItemId?: string; workflowInstanceId?: string; runtimeType?: string }) {
    const seq = ++selectionSeq
    const session = await sessionApi.create(data)
    upsertSession(session)
    if (seq === selectionSeq) {
      activeSession.value = session
      messages.value = []
    }
    return session
  }

  function upsertSession(session: AgentSessionDto) {
    sessions.value = [session, ...sessions.value.filter((item) => item.id !== session.id)]
  }

  async function sendMessage(content: string): Promise<AgentMessageDto | undefined>
  async function sendMessage(data: SendMessageRequest): Promise<AgentMessageDto | undefined>
  async function sendMessage(contentOrData: string | SendMessageRequest): Promise<AgentMessageDto | undefined> {
    if (!activeSession.value) return
    const sessionId = activeSession.value.id
    const data: SendMessageRequest = typeof contentOrData === 'string'
      ? { content: contentOrData }
      : contentOrData
    const message = await sessionApi.sendMessage(sessionId, data)
    if (activeSession.value?.id === sessionId && message.sessionId === sessionId) {
      messages.value.push(message)
    }
    return message
  }

  async function cancelActiveSession() {
    if (!activeSession.value) return
    await sessionApi.cancel(activeSession.value.id)
  }

  function replaceMessages(nextMessages: AgentMessageDto[]) {
    const sessionId = activeSession.value?.id
    messages.value = sessionId
      ? nextMessages.filter((message) => message.sessionId === sessionId)
      : []
  }

  function appendStreamingMessage(content: string) {
    const sessionId = activeSession.value?.id
    if (!sessionId) return
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg && lastMsg.sessionId === sessionId && lastMsg.role === 'ASSISTANT' && lastMsg.status === 'STREAMING') {
      messages.value[messages.value.length - 1] = {
        ...lastMsg,
        content: (lastMsg.content || '') + content,
      }
      return
    }
    messages.value.push({
      id: `streaming_${Date.now()}`,
      sessionId,
      role: 'ASSISTANT',
      content,
      contentFormat: 'TEXT',
      status: 'STREAMING',
      seqNo: (messages.value.length + 1),
      createdAt: new Date().toISOString(),
      workflowNodeInstanceId: null,
    })
  }

  return {
    sessions,
    activeSession,
    messages,
    loading,
    loadSessions,
    selectSession,
    createSession,
    sendMessage,
    cancelActiveSession,
    upsertSession,
    replaceMessages,
    appendStreamingMessage,
  }
})
