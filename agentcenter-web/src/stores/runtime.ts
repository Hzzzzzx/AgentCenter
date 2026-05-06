import { defineStore } from 'pinia'
import { ref } from 'vue'
import { websocketUrl } from '../api/client'
import { eventApi } from '../api/events'
import type { AgentMessageDto, RuntimeEventDto } from '../api/types'
import { useSessionStore } from './sessions'

type OutboundWebSocketEvent = {
  type: string
  requestId?: string
  payload?: Record<string, unknown>
}

type InboundWebSocketEvent = {
  type: string
  payload?: unknown
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isRuntimeEvent(value: unknown): value is RuntimeEventDto {
  return isRecord(value) && typeof value.eventType === 'string' && typeof value.eventSource === 'string'
}

function isRuntimeEventsPayload(value: unknown): value is { events: RuntimeEventDto[] } {
  return isRecord(value) && Array.isArray(value.events) && value.events.every(isRuntimeEvent)
}

function isAgentMessage(value: unknown): value is AgentMessageDto {
  return isRecord(value) && typeof value.id === 'string' && typeof value.role === 'string'
}

function isSessionMessagesPayload(value: unknown): value is { messages: AgentMessageDto[] } {
  return isRecord(value) && Array.isArray(value.messages) && value.messages.every(isAgentMessage)
}

export const useRuntimeStore = defineStore('runtime', () => {
  const events = ref<RuntimeEventDto[]>([])
  const connected = ref(false)
  const activeSocket = ref<WebSocket | null>(null)
  const activeSessionId = ref<string | null>(null)
  const outboundQueue = ref<OutboundWebSocketEvent[]>([])
  const activeSse = ref<EventSource | null>(null)
  const streamingText = ref('')

  function connectSessionEvents(sessionId: string) {
    if (activeSessionId.value === sessionId && activeSocket.value?.readyState === WebSocket.OPEN) {
      return
    }

    disconnect()
    activeSessionId.value = sessionId
    const socket = new WebSocket(websocketUrl(`/ws/agent-sessions/${sessionId}`))
    activeSocket.value = socket

    socket.onopen = () => {
      connected.value = true
      flushQueue()
    }
    socket.onmessage = (event) => {
      handleInboundEvent(event.data)
    }
    socket.onerror = () => {
      connected.value = false
    }
    socket.onclose = () => {
      connected.value = false
      if (activeSocket.value === socket) {
        activeSocket.value = null
      }
    }
  }

  function sendUserMessage(content: string, contentFormat = 'TEXT') {
    sendOrQueue({
      type: 'user.message',
      requestId: `req_${Date.now()}`,
      payload: { content, contentFormat },
    })
  }

  function disconnect() {
    if (activeSocket.value) {
      activeSocket.value.close()
      activeSocket.value = null
    }
    connected.value = false
  }

  function connectSSE(sessionId: string) {
    disconnectSSE()
    activeSessionId.value = sessionId

    activeSse.value = eventApi.streamSessionEvents(sessionId, (event: RuntimeEventDto) => {
      events.value.push(event)

      if (event.eventType === 'ASSISTANT_DELTA') {
        try {
          const payload = event.payloadJson ? JSON.parse(event.payloadJson) : {}
          const text = payload.delta || payload.text || payload.label || ''
          if (text) {
            streamingText.value += text
          }
        } catch { /* ignore parse errors */ }
      }

      if (event.eventType === 'STATUS') {
        try {
          const payload = event.payloadJson ? JSON.parse(event.payloadJson) : {}
          const status = payload.status || payload.label || ''
          if (status === 'waiting_user' || status === 'idle') {
            if (streamingText.value.trim()) {
              const sessionStore = useSessionStore()
              sessionStore.appendStreamingMessage(streamingText.value)
              streamingText.value = ''
            }
          }
        } catch { /* ignore parse errors */ }
      }
    })
  }

  function disconnectSSE() {
    if (activeSse.value) {
      activeSse.value.close()
      activeSse.value = null
    }
    streamingText.value = ''
  }

  function clearEvents() {
    events.value = []
  }

  function sendOrQueue(event: OutboundWebSocketEvent) {
    if (activeSocket.value?.readyState === WebSocket.OPEN) {
      activeSocket.value.send(JSON.stringify(event))
      return
    }
    outboundQueue.value.push(event)
  }

  function flushQueue() {
    const queued = [...outboundQueue.value]
    outboundQueue.value = []
    queued.forEach(sendOrQueue)
  }

  function handleInboundEvent(raw: string) {
    let event: InboundWebSocketEvent
    try {
      event = JSON.parse(raw)
    } catch {
      return
    }

    const sessionStore = useSessionStore()

    if (event.type === 'runtime.event' && isRuntimeEvent(event.payload)) {
      events.value.push(event.payload)
      return
    }
    if (event.type === 'runtime.events' && isRuntimeEventsPayload(event.payload)) {
      events.value = event.payload.events
      return
    }
    if (event.type === 'session.messages' && isSessionMessagesPayload(event.payload)) {
      sessionStore.replaceMessages(event.payload.messages)
    }
  }

  return {
    events,
    connected,
    activeSocket,
    activeSessionId,
    activeSse,
    streamingText,
    connectSessionEvents,
    connectSSE,
    disconnectSSE,
    sendUserMessage,
    disconnect,
    clearEvents,
  }
})
