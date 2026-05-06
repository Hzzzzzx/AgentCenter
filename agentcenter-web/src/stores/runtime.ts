import { defineStore } from 'pinia'
import { ref } from 'vue'
import { eventApi } from '../api/events'
import type { RuntimeEventDto } from '../api/types'
import { useSessionStore } from './sessions'

export const useRuntimeStore = defineStore('runtime', () => {
  const events = ref<RuntimeEventDto[]>([])
  const connected = ref(false)
  const activeSessionId = ref<string | null>(null)
  const activeSse = ref<EventSource | null>(null)
  const streamingText = ref('')

  function connectSSE(sessionId: string) {
    disconnectSSE()
    activeSessionId.value = sessionId
    connected.value = true

    activeSse.value = eventApi.streamSessionEvents(sessionId, (event: RuntimeEventDto) => {
      events.value.push(event)
      applyRuntimeEvent(event)
    })
  }

  function disconnectSSE() {
    if (activeSse.value) {
      activeSse.value.close()
      activeSse.value = null
    }
    connected.value = false
    streamingText.value = ''
  }

  function clearEvents() {
    events.value = []
  }

  function applyRuntimeEvent(event: RuntimeEventDto) {
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
          flushStreamingText()
          reloadActiveMessages()
        }
      } catch { /* ignore parse errors */ }
    }
  }

  function flushStreamingText() {
    if (streamingText.value.trim()) {
      const sessionStore = useSessionStore()
      sessionStore.appendStreamingMessage(streamingText.value)
      streamingText.value = ''
    }
  }

  async function reloadActiveMessages() {
    const sessionStore = useSessionStore()
    const sessionId = activeSessionId.value
    if (!sessionId || sessionStore.activeSession?.id !== sessionId) {
      return
    }
    try {
      await sessionStore.selectSession(sessionId)
    } catch { /* keep the streamed view if the snapshot reload fails */ }
  }

  return {
    events,
    connected,
    activeSessionId,
    activeSse,
    streamingText,
    connectSSE,
    disconnectSSE,
    clearEvents,
  }
})
