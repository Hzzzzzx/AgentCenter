import { defineStore } from 'pinia'
import { ref } from 'vue'
import { sessionApi } from '../api/sessions'
import type { AgentSessionDto, AgentMessageDto } from '../api/types'

export const useSessionStore = defineStore('sessions', () => {
  const sessions = ref<AgentSessionDto[]>([])
  const activeSession = ref<AgentSessionDto | null>(null)
  const messages = ref<AgentMessageDto[]>([])
  const loading = ref(false)

  async function loadSessions() {
    loading.value = true
    try {
      sessions.value = await sessionApi.list()
    } finally {
      loading.value = false
    }
  }

  async function selectSession(id: string) {
    activeSession.value = await sessionApi.getById(id)
    messages.value = await sessionApi.getMessages(id)
  }

  async function createSession(data: { sessionType: string; title?: string; workItemId?: string; workflowInstanceId?: string; runtimeType?: string }) {
    const session = await sessionApi.create(data)
    upsertSession(session)
    activeSession.value = session
    messages.value = []
    return session
  }

  function upsertSession(session: AgentSessionDto) {
    sessions.value = [session, ...sessions.value.filter((item) => item.id !== session.id)]
  }

  async function sendMessage(content: string) {
    if (!activeSession.value) return
    const message = await sessionApi.sendMessage(activeSession.value.id, { content })
    messages.value.push(message)
    return message
  }

  function replaceMessages(nextMessages: AgentMessageDto[]) {
    messages.value = nextMessages
  }

  function appendStreamingMessage(content: string) {
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg && lastMsg.role === 'ASSISTANT' && lastMsg.status === 'STREAMING') {
      messages.value[messages.value.length - 1] = {
        ...lastMsg,
        content: (lastMsg.content || '') + content,
      }
      return
    }
    messages.value.push({
      id: `streaming_${Date.now()}`,
      sessionId: activeSession.value?.id || '',
      role: 'ASSISTANT',
      content,
      contentFormat: 'TEXT',
      status: 'STREAMING',
      seqNo: (messages.value.length + 1),
      createdAt: new Date().toISOString(),
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
    upsertSession,
    replaceMessages,
    appendStreamingMessage,
  }
})
